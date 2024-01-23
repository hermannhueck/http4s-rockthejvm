package rockthejvm.twofactorauth

case class UserDB(username: String, email: String) {

  // scalafix:off
  private var counter: Long   = 5L
  private var encryptedSecret = ""
  var isSecretUpdated         = false
  // scalafix:on

  def getCounter       = counter
  def incrementCounter = counter += 1

  def getSecret                         = encryptedSecret
  def updateSecret(secretValue: String) = {
    encryptedSecret = secretValue
    isSecretUpdated = true
  }
}
