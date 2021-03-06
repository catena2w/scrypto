package scorex.crypto.signing

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.crypto.signatures.Curve25519


class SigningFunctionsSpecification extends PropSpec
with PropertyChecks
with GeneratorDrivenPropertyChecks
with Matchers {

  property("signed message should be verifiable with appropriate public key") {
    forAll { (seed1: Array[Byte], seed2: Array[Byte],
              message1: Array[Byte], message2: Array[Byte]) =>
      whenever(!seed1.sameElements(seed2) && !message1.sameElements(message2)) {
        val keyPair = Curve25519.createKeyPair(seed1)
        val keyPair2 = Curve25519.createKeyPair(seed2)

        val sig = Curve25519.sign(keyPair._1, message1)
        Curve25519.verify(sig, message1, keyPair._2) should be(true)

        Curve25519.verify(sig, message1, keyPair2._2) shouldNot be(true)

        Curve25519.verify(sig, message2, keyPair._2) shouldNot be(true)
      }
    }
  }
}