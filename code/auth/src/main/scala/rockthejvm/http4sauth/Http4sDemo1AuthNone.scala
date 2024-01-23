/*
  See:
  - https://www.youtube.com/watch?v=DxZIuvSDvyA
  - https://blog.rockthejvm.com/scala-http4s-authentication/
 */

package rockthejvm.http4sauth

import cats._
import cats.effect._
import com.comcast.ip4s._
import fs2.io.net.Network
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server._

object Http4sDemo1AuthNone extends IOApp.Simple {

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
    server[IO](httpAppNoAuth).useForever

  // 1. ========== No Authentication ==========

  def httpAppNoAuth[F[_]: Monad]: HttpApp[F] =
    routesNoAuth.orNotFound

  def routesNoAuth[F[_]: Monad]: HttpRoutes[F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] { case GET -> Root / "welcome" / user =>
      Ok(s"Welcome, $user!")
    }
  }
}
