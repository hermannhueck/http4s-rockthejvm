/*
  See:
  - https://www.youtube.com/watch?v=DxZIuvSDvyA
  - https://blog.rockthejvm.com/scala-http4s-authentication/
 */

package rockthejvm.http4sauth

import cats.effect._
import org.http4s._
import org.http4s.server._
import org.http4s.ember.server._
import com.comcast.ip4s._
import fs2.io.net.Network
import org.http4s.dsl.Http4sDsl
import cats._
import cats.data._
import org.http4s.headers.Authorization

object Http4sDemo2AuthBasic extends IOApp.Simple {

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  def server[F[_]: Async: Network](httpApp: HttpApp[F]): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build

  override val run: IO[Unit] =
    server[IO](httpAppBasicAuth).useForever

  // 2. ========== Basic Authentication ==========

  def httpAppBasicAuth[F[_]: Sync]: HttpApp[F] =
    userBasicAuthMiddleware
      .apply(authedRoutes)
      .orNotFound

  def authedRoutes[F[_]: Monad]: AuthedRoutes[User, F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    AuthedRoutes.of { case GET -> Root / "welcome" as user =>
      Ok(s"Welcome, ${user.name}!")
    }
  }

  // auth middleware
  def userBasicAuthMiddleware[F[_]: Sync]: AuthMiddleware[F, User] =
    AuthMiddleware(basicAuthMethod, onFailure)

  // Request[IO] => Either[String, User] is equivalent to
  // Kleisli[IO, Request[IO], Either[String, User]]
  def basicAuthMethod[F[_]: Sync]: Kleisli[F, Request[F], Either[String, User]] =
    Kleisli { request =>
      // authentication logic
      val authHeader = request.headers.get[Authorization]
      authHeader match {
        case Some(Authorization(BasicCredentials(username, _))) =>
          Sync[F].delay(Right(User(123L, username)))
        case Some(_)                                            =>
          Sync[F].delay(Left("Unauthorized: No basic credentials"))
        case None                                               =>
          Sync[F].delay(Left("Unauthorized: No auth header"))
      }
    }

  // auth failure handler
  def onFailure[F[_]: Applicative]: AuthedRoutes[String, F] =
    Kleisli { _: AuthedRequest[F, String] =>
      OptionT.pure[F](Response[F](status = Status.Unauthorized))
    }
}
