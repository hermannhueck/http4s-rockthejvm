package rockthejvm.twofactorauth

sealed trait Generator extends Product with Serializable
object Generator {
  case object Hotp extends Generator
  case object Totp extends Generator
}
