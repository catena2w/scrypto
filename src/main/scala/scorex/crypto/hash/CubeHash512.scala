package scorex.crypto.hash

object CubeHash512 extends FRHash {
  override protected def hf: fr.cryptohash.Digest = new fr.cryptohash.CubeHash512
}