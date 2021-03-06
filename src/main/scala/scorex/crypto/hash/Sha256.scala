package scorex.crypto.hash

import java.security.MessageDigest

/**
  * Hashing functions implementation with sha256 impl from Java SDK
  */
object Sha256 extends CryptographicHash {
  override val DigestSize = 32

  override def hash(input: Array[Byte]) = MessageDigest.getInstance("SHA-256").digest(input)
}