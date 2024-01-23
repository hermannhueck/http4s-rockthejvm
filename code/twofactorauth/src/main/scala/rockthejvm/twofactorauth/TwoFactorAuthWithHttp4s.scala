// see:
// https://blog.rockthejvm.com/oauth-authentication-scala-http4s/
// https://www.youtube.com/watch?v=NZwnZhwVPrs

/*
  OAuth which stands for Open Authorization is an open standard framework
  that allows the user to permit a website or application to interact with another without giving up his or her password.

  1. When the user tries to log into app1, the user is redirected to an authorization server owned by app2.
  2. The authorization server provides the user with a prompt, asking the user to grant app1 access to app2 with a list of permissions.
  3. Once the prompt is accepted, the user is redirected back to app1 with a single-use authorization code.
  4. app1 will respond to app2 with the same authorization code, a client id, and a client secret.
  5. The authorization server on app2 will respond with a token id and an access token.
  6. app1 can now request the user’s information from app2’s API using the access token.
 */

package rockthejvm.twofactorauth

import cats.effect._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.typelevel.log4cats.slf4j.Slf4jLogger

object TwoFactorAuthWithHttp4s extends IOApp.Simple {

  import org.typelevel.log4cats.Logger
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val user         = UserDB("john", "johndoe@email.com")
  // val tokenService = new OtpInteractiveService(Generator.Hotp, user)
  val tokenService = new OtpInteractiveService(Generator.Totp, user)

  val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "login"        =>
        // create barcode image
        tokenService
          .otpService
          .makeBarCodeImg("<business@email.com>", "<business>")
          .attempt
          .flatMap {
            // failed bar code image creation
            case Left(ex) =>
              logger.error(s"QR Code Error: ${ex.getMessage}") *>
                Ok("Error occurred generating QR Code...")
            // successful barcode image creation
            case Right(_) =>
              // Log token
              tokenService
                .otpService
                .getToken
                .flatMap { token =>
                  logger.info(s"Token value: $token")
                }
                .handleErrorWith { ex =>
                  logger.error(s"GetToken Error: ${ex.getMessage}")
                } *>
                // send email
                tokenService.send2FA(user).flatMap {
                  // failed email
                  case Left(ex)   =>
                    logger.error(s"Response Error: $ex") *>
                      Ok("Oops, something went wrong, please try again later.")
                  // successful email
                  case Right(res) =>
                    logger.info(s"Response Body: ${res.getBody()}") *>
                      logger.info(s"Response Status Code: ${res.getStatusCode()}") *>
                      logger.info(s"Response Headers: ${res.getHeaders()}") *>
                      Ok("We sent you an email, please follow the steps to complete the signin process.")
                }
          }
      case GET -> Root / "code" / value =>
        // verify code
        tokenService
          .otpService
          .verifyCode(value)
          .flatMap { result =>
            if (result) {
              IO(user.incrementCounter) *>
                IO(user.isSecretUpdated = false) *>
                Ok(s"Code verification passed")
            } else Ok(s"Code verification failed")
          }
          .handleErrorWith { ex =>
            logger.error(s"Verification Error: ${ex.getMessage}") *>
              Ok("An error occured during verification")
          }
    }

  def server: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes.orNotFound)
      .build

  val run: IO[Unit] = {
    server.useForever
  }
}
