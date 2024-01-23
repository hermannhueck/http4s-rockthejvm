// See:
// - https://www.youtube.com/watch?v=z3F3iAE8U-U
// - https://blog.rockthejvm.com/security-in-http4s/
//

/*
   CSRF = Cross Site Request Forgery
   ---------------------------------
 */

package rockthejvm.http4ssecurity

import javax.crypto.SecretKey

import cats._
import cats.effect._
import cats.syntax.functor._
import com.comcast.ip4s._
import fs2.io.net.Network
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.CSRF

object CSRFService extends IOApp.Simple {

  def photoService[F[_]: Monad]: HttpApp[F] = {

    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root / "testing" =>
          Ok(s"Testing ...")
        case POST -> Root / "photos" =>
          Ok("Processing photo ...")
      }
      .orNotFound
  }

  val cookieName = "csrf-token"

  def fToken[F[_]: Sync]: F[SecretKey] =
    CSRF.generateSigningKey[F]()

  def csrfService[F[_]: Sync]: F[HttpApp[F]] =
    fToken
      .map { token =>
        def defaultOriginCheck: Request[F] => Boolean =
          request =>
            CSRF
              .defaultOriginCheck[F](request, "localhost", Uri.Scheme.http, None)

        def csrfBuilder: CSRF.CSRFBuilder[F, F] =
          CSRF[F, F](token, defaultOriginCheck)

        def csrf: CSRF[F, F] =
          csrfBuilder
            .withCookieName(cookieName)
            .withCookieDomain(Some("localhost"))
            .withCookiePath(Some("/"))
            .build

        def service: HttpApp[F] =
          csrf
            .validate()
            .apply(photoService)

        service: HttpApp[F]
      }

  def serverResource[F[_]: Async: Network]: Resource[F, Server] =
    Resource
      .eval(csrfService)
      .flatMap { service =>
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(service)
          .build
      }

  override def run: IO[Unit] =
    serverResource[IO].use { _ =>
      IO.println("----- CSRFService started on port 8080. -----") *>
        IO.never
    }
}
