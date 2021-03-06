package scorex.crypto.authds.merkle.versioned

import org.scalacheck.Gen
import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.crypto.authds.merkle.CommonTreeFunctionality
import scorex.crypto.authds.merkle.MerkleTree._
import scorex.crypto.encode.Base16

import scala.util.Try


class VersionedMerkleSpecification
  extends PropSpec
  with PropertyChecks
  with GeneratorDrivenPropertyChecks
  with Matchers
  with CommonTreeFunctionality {

  property("two appends of the same contents are commutative") {
    for (blocks <- List(7, 8, 9, 128)) {

      val (_, _, tempFile: String) = generateFile(blocks, "3")

      val vms1 = MvStoreVersionedMerklizedIndexedSeq.fromFile(tempFile, None, 1024, DefaultHashFunction)
      val vms2 = MvStoreVersionedMerklizedIndexedSeq.fromFile(tempFile, None, 1024, DefaultHashFunction)

      vms1.rootHash shouldBe vms2.rootHash

      forAll(Gen.listOf(Gen.alphaStr)) { strings: List[String] =>
        val upd = strings.map(_.getBytes).map(MerklizedSeqAppend.apply)

        vms1.update(Nil, upd)
        vms1.update(Nil, upd)

        vms2.update(Nil, upd)
        vms2.update(Nil, upd)

        vms1.rootHash shouldBe vms2.rootHash
      }
    }
  }

  property("check against manually calculated update sample") {
    val vms = helloWorldTree()

    val removals = Seq(MerklizedSeqRemoval(2), MerklizedSeqRemoval(1))

    val elem = Base16.decode("f38764ccc88c199a5633bf8186a5ec6a5f6c05493e0bd921a295414839eda757")
    val appends = Seq.fill(6)(elem).map(MerklizedSeqAppend)

    // update tree is consisting of 8 leafs
    // 2 of them are b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
    // 6 of them are c0e6e05e8a4861c94010b6a21f3194944a5fcb469ec4c319479d65cc555281f6

    //               328
    //       b76             423
    //   17a     d58     fd3     fd3
    // b94 c0e c0e b94 c0e c0e c0e c0e

    vms.update(removals, appends)

    vms.tree.getHash(0 -> 1).get shouldBe Base16.decode("c0e6e05e8a4861c94010b6a21f3194944a5fcb469ec4c319479d65cc555281f6")
    vms.tree.getHash(0 -> 2).get shouldBe Base16.decode("c0e6e05e8a4861c94010b6a21f3194944a5fcb469ec4c319479d65cc555281f6")
    vms.tree.getHash(0 -> 3).get shouldBe Base16.decode("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")

    vms.tree.getHash(1 -> 0).get shouldBe Base16.decode("17a3343050197538c0e7fd717380b47009d2fc8e40f6073a8995d3fb42a44fa4")
    vms.rootHash shouldBe Base16.decode("3287081835e28d07a13907ca35ceb02e2b2d03f41eaeb1b2f26023620da97247")
  }

  property("check against manually calculated addition") {
    val vms = helloWorldTree()

    val hw = vms.seq.get(0).get
    val appends = Seq.fill(4)(hw).map(MerklizedSeqAppend)
    vms.update(Seq(), appends)
    vms.rootHash shouldBe Base16.decode("87acff56319a5e7e50263a4874f44a7b21389d4a567f45da05e5fa544cb3dd49")
  }

  property("check against manually calculated removal - decreasing order") {
    val vms = helloWorldTree()
    val removals = Seq(MerklizedSeqRemoval(2), MerklizedSeqRemoval(1))
    vms.update(removals, Seq())

    vms.rootHash shouldBe Base16.decode("47a8c6f8b634e4d94a9da33e182c270fe3571f1a550d20fd93735583180c3c32")
  }

  property("check against manually calculated removal - increasing order") {
    val vms = helloWorldTree()
    val removals = Seq(MerklizedSeqRemoval(1), MerklizedSeqRemoval(2))
    Try(vms.update(removals, Seq())).isFailure shouldBe true
  }

  property("versioning - rollback to a bigger tree") {
    val vms = helloWorldTree()

    val versionToGoBack = vms.allVersions().last

    vms.tree.rootHash shouldBe Base16.decode("0ba8ad6fc2a7c94abcd2d4128720c5697cf147310ae82287270d56beaf8702f1")

    val removals = Seq(MerklizedSeqRemoval(2), MerklizedSeqRemoval(1))
    vms.update(removals, Seq())

    vms.rootHash shouldBe Base16.decode("47a8c6f8b634e4d94a9da33e182c270fe3571f1a550d20fd93735583180c3c32")

    vms.consistent shouldBe true

    val rollbackStatus = vms.rollbackTo(versionToGoBack)

    rollbackStatus.isSuccess shouldBe true

    vms.consistent shouldBe true

    vms.tree.rootHash shouldBe Base16.decode("0ba8ad6fc2a7c94abcd2d4128720c5697cf147310ae82287270d56beaf8702f1")
  }

  property("versioning - rollback to a smaller tree") {
    val vms = helloWorldTree()

    val versionToGoBack = vms.allVersions().last

    vms.tree.rootHash shouldBe Base16.decode("0ba8ad6fc2a7c94abcd2d4128720c5697cf147310ae82287270d56beaf8702f1")

    val appends = Seq.fill(4)(MerklizedSeqAppend("hello world".getBytes))
    vms.update(Seq(), appends)

    vms.rootHash shouldBe Base16.decode("87acff56319a5e7e50263a4874f44a7b21389d4a567f45da05e5fa544cb3dd49")

    vms.consistent shouldBe true

    val rollbackStatus = vms.rollbackTo(versionToGoBack)

    rollbackStatus.isSuccess shouldBe true

    vms.consistent shouldBe true

    vms.tree.rootHash shouldBe Base16.decode("0ba8ad6fc2a7c94abcd2d4128720c5697cf147310ae82287270d56beaf8702f1")
  }


  property("reversal after many appends") {
    val vms = helloWorldTree()

    val initialRoot = vms.rootHash

    val versionToGoBack = vms.allVersions().last

    forAll(Gen.listOf(Gen.alphaStr), minSuccessful(3), maxDiscarded(1)) { strings: List[String] =>
      val upd = strings.map(_.getBytes).map(MerklizedSeqAppend.apply)
      vms.update(Seq(), upd)
    }

    vms.consistent shouldBe true

    val rollbackStatus = vms.rollbackTo(versionToGoBack)

    rollbackStatus.isSuccess shouldBe true

    vms.consistent shouldBe true

    vms.tree.rootHash shouldBe initialRoot
  }

  property("reversal after many appends - fromFile") {
    for (blocks <- List(7, 128)) {

      val (_, _, tempFile: String) = generateFile(blocks, "3")

      val vms = MvStoreVersionedMerklizedIndexedSeq.fromFile(tempFile, None, 1024, DefaultHashFunction)

      val initialRoot = vms.rootHash

      val versionToGoBack = vms.allVersions().last

      forAll(Gen.listOf(Gen.alphaStr), minSuccessful(10), maxDiscarded(5)) { strings: List[String] =>
        val upd = strings.map(_.getBytes).map(MerklizedSeqAppend.apply)
        vms.update(Seq(), upd)
      }

      vms.consistent shouldBe true

      val rollbackStatus = vms.rollbackTo(versionToGoBack)

      rollbackStatus.isSuccess shouldBe true

      vms.consistent shouldBe true

      vms.tree.rootHash shouldBe initialRoot
    }
  }
}
