package scorex.crypto.authds.skiplist

import scorex.crypto.authds.skiplist.SLNode.{SLNodeKey, SLNodeValue}
import scorex.crypto.authds.storage.{KVStorage, StorageType}
import scorex.crypto.encode.Base58
import scorex.crypto.hash.{CommutativeHash, CryptographicHash}

import scala.annotation.tailrec
import scala.util.Random

class SkipList[HF <: CommutativeHash[_], ST <: StorageType](implicit storage: KVStorage[SLNodeKey, SLNodeValue, ST],
                                                            hf: HF) {

  private val TopNodeKey: SLNodeKey = Array(-128: Byte)

  //top left node
  var topNode: SLNode = storage.get(TopNodeKey).flatMap(bytes => SLNode.parseBytes(bytes).toOption) match {
    case Some(tn) => tn
    case None =>
      val topRight: SLNode = SLNode(MaxSLElement, None, None, 0, true)
      val topNode: SLNode = SLNode(MinSLElement, Some(topRight.nodeKey), None, 0, true)
      storage.set(topRight.nodeKey, topRight.bytes)
      storage.set(topNode.nodeKey, topNode.bytes)
      storage.set(TopNodeKey, topNode.bytes)
      storage.commit()
      topNode
  }


  private def leftAt(l: Int): Option[SLNode] = {
    require(l <= topNode.level)
    topNode.downUntil(_.level == l)
  }

  def rootHash: CryptographicHash#Digest = topNode.hash

  def contains(e: SLElement): Boolean = find(e).isDefined

  def elementProof(e: SLElement): Option[SLAuthData] = find(e).map { n =>
    SLAuthData(e.bytes, SLPath(topNode.hashTrack(e)))
  }

  // find bottom node with current element
  def find(e: SLElement): Option[SLNode] = {
    @tailrec
    def loop(node: SLNode): Option[SLNode] = {
      val prevNodeOpt = node.rightUntil(n => n.right.exists(rn => rn.el > e))
      require(prevNodeOpt.isDefined, "Non-infinite element should have right node")
      val prevNode = prevNodeOpt.get
      prevNode.down match {
        case Some(dn: SLNode) => loop(dn)
        case _ => if (prevNode.el == e) Some(node) else None
      }
    }
    loop(topNode)
  }

  def insert(e: SLElement): Boolean = if (contains(e)) {
    false
  } else {
    val eLevel = selectLevel(e)
    if (eLevel == topNode.level) newTopLevel()
    def insertOne(lev: Int, down: Option[SLNode]): Unit = if (lev <= eLevel) {
      val startNode = leftAt(lev).get //TODO get
      val prev = startNode.rightUntil(_.right.get.el > e).get //TODO get
      val newNode = SLNode(e, prev.right.map(_.nodeKey), down.map(_.nodeKey), lev, lev != eLevel)
      storage.set(newNode.nodeKey, newNode.bytes)
      val prevUpdated = prev.copy(rightKey = Some(newNode.nodeKey))
      storage.unset(prev.nodeKey)
      storage.set(prevUpdated.nodeKey, prevUpdated.bytes)
      if (lev < eLevel) insertOne(lev + 1, Some(newNode))
    }
    insertOne(0, None)
    true
  }

  private def newTopLevel(): Unit = {
    val prevNode = topNode
    val newLev = topNode.level + 1
    val topRight = topNode.rightUntil(_.right.isEmpty).get
    val newRight = SLNode(MaxSLElement, None, Some(topRight.nodeKey), newLev, true)
    topNode = SLNode(MinSLElement, Some(newRight.nodeKey), Some(prevNode.nodeKey), newLev, true)
    storage.set(newRight.nodeKey, newRight.bytes)
    storage.set(topNode.nodeKey, topNode.bytes)
    storage.set(TopNodeKey, topNode.bytes)
    storage.commit()
  }

  def delete(e: SLElement): Boolean = if (contains(e)) {
    tower() foreach { leftNode =>
      leftNode.rightUntil(n => n.right.exists(nr => nr.el == e)).foreach { n =>
        val newNode = n.copy(rightKey = n.right.flatMap(_.rightKey))
        storage.unset(n.nodeKey)
        storage.set(newNode.nodeKey, newNode.bytes)
        n.right.foreach(nr => storage.unset(nr.nodeKey))
      }
    }
    true
  } else {
    false
  }

  //select level where element e will be putted
  private def selectLevel(e: SLElement) = {
    val r = Random
    r.setSeed((BigInt(e.key) % Long.MaxValue).toLong) //TODO check
    @tailrec
    def loop(lev: Int = 0): Int = {
      if (lev == topNode.level || r.nextDouble() > 0.5) lev
      else loop(lev + 1)
    }
    loop()
  }

  /**
    * All nodes in a tower
    */
  @tailrec
  private def tower(n: SLNode = topNode, acc: Seq[SLNode] = Seq(topNode)): Seq[SLNode] = n.down match {
    case Some(downNode) => tower(downNode, downNode +: acc)
    case None => acc
  }

  override def toString: String = {
    def lev(n: SLNode, acc: Seq[SLNode] = Seq()): Seq[SLNode] = n.right match {
      case Some(rn) => lev(rn, n +: acc)
      case None => n +: acc
    }
    val levs = tower() map { leftNode =>
      leftNode.level + ": " + lev(leftNode).reverse.map(n => Base58.encode(n.el.key)).mkString(", ")
    }
    levs.mkString("\n")
  }
}

object SkipList {
  type SLKey = Array[Byte]
  type SLValue = Array[Byte]
}