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
import cats.syntax.all._
import org.http4s.server.middleware.authentication.DigestAuth
import java.time.Instant
import pdi.jwt._
import dev.profunktor.auth.jwt._
import io.circe._
import io.circe.parser._
import dev.profunktor.auth.JwtAuthMiddleware

object Http4sDemo5AuthJWT extends IOApp.Simple {

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  @annotation.nowarn("cat=unused-params")
  def server[F[_]: Async: Network: LoggerFactory]: Resource[F, Server] =
    serviceRouter[F].flatMap { httpApp =>
      EmberServerBuilder
        .default[F]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(httpApp)
        .build
    }

  override val run: IO[Unit] =
    server[IO].useForever

  // 5. ========== JWT Authentication ==========

  // 1. User logs in with username and password
  // 2. Server replies with a Set-Cookie header containing the JWT
  // 3. User sends the JWT back to the server with every subsequent request using the header "Authorization: Bearer <token>"
  // 4. Server validates the JWT and grants access if it is valid

  def serviceRouter[F[_]: Async]: Resource[F, HttpApp[F]] =
    userDigestAuthMiddlewareResource[F].map { middleware =>
      Router(
        "/login"   -> middleware.apply(authedRoutes),        // login endpoint (unauthorized)
        "/guarded" -> jwtAuthMiddleware.apply(securedRoutes) // endpoints that require a valid JWT (authorized)
      ).orNotFound
    }

  // login endpoint
  def authedRoutes[F[_]: Monad]: AuthedRoutes[User, F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    AuthedRoutes.of { case GET -> Root / "welcome" as user =>
      val cookie =
        ResponseCookie("token", token)
      Ok(s"Welcome, $user!")
        .map(_.addCookie(cookie))
    }
  }

  // JWT logic
  // using library: "dev.profunktor" %% "http4s-jwt-auth" % http4sJwtAuthVersion

  def jwtAuthMiddleware[F[_]: MonadThrow]: AuthMiddleware[F, User] =
    JwtAuthMiddleware(JwtAuth.hmac(key, algorithm), authenticate)

  def securedRoutes[F[_]: Monad]: AuthedRoutes[User, F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    AuthedRoutes.of { case GET -> Root / "secret" as user =>
      Ok(s"This is the secret, $user!")
    }
  }

  final case class TokenPayload(username: String, permsLevel: String)
  object TokenPayload {

    implicit val tokenPayloadDecoder: Decoder[TokenPayload] =
      // Decoder.forProduct2("username", "permsLevel")(TokenPayload.apply)
      Decoder.instance(cursor =>
        for {
          username   <- cursor.get[String]("username")
          permsLevel <- cursor.get[String]("permsLevel")
        } yield TokenPayload(username, permsLevel)
      )

    // import io.circe.generic.semiauto._
    // implicit val tokenPayloadDecoder: Decoder[TokenPayload] = deriveDecoder
    // implicit val tokenPayloadEncoder: Encoder[TokenPayload] = deriveEncoder
  }

  def claim(username: String, permsLevel: String): JwtClaim =
    JwtClaim(
      content = s"""
                   |{
                   |   "user": "$username",
                   |   "level": "$permsLevel"
                   |}
                   |""".stripMargin,
      expiration = Some(Instant.now.plusSeconds(10 * 24 * 3600).getEpochSecond),
      issuedAt = Some(Instant.now.getEpochSecond)
    )

  val key       = "TOBECONFIGURED"
  val algorithm = JwtAlgorithm.HS256
  val token     =
    JwtCirce.encode(claim("alice", "basic"), key, algorithm)

  def authenticate[F[_]: MonadThrow]: JwtToken => JwtClaim => F[Option[User]] =
    (_: JwtToken) =>
      (claim: JwtClaim) =>
        decode[TokenPayload](claim.content)
          .fold(_ => none[User], payload => database.get(payload.username))
          .pure[F]

  def authenticateUnused[F[_]: MonadThrow]: JwtToken => JwtClaim => F[Option[User]] =
    (_: JwtToken) =>
      (claim: JwtClaim) =>
        decode[TokenPayload](claim.content) match {
          case Left(_)        =>
            none[User].pure[F]
          case Right(payload) =>
            database.get(payload.username).pure[F]
        }

  // database
  val database: Map[String, User] =
    Map(
      "alice" -> User(123L, "alice"),
      "bob"   -> User(456L, "bob")
    )

  def userDigestAuthMiddlewareResource[F[_]: Async]: Resource[F, AuthMiddleware[F, User]] =
    Resource.eval(userDigestAuthMiddleware[F])

  val realm = "http://localhost:8080"

  def userDigestAuthMiddleware[F[_]: Async]: F[AuthMiddleware[F, User]] =
    // apply is side-effecting, hence we use applyF
    DigestAuth.applyF[F, User](realm = realm, store = digestAuthStore)

  def digestAuthStore[F[_]: Sync]: DigestAuth.AuthStore[F, User] =
    DigestAuth.Md5HashedAuthStore(searchFunction[F])

  def searchFunction[F[_]: Sync]: String => F[Option[(User, String)]] = {
    // search user in the database
    // if found, return Some(user, password)
    // if not found, return None
    // case "alice" => (User(123L, "alice") -> "alicepw").some.pure[F]
    case "alice" =>
      for {
        user <- User(123L, "alice").pure[F] // search user in the database
        hash <- DigestAuth.Md5HashedAuthStore.precomputeHash(user.name, realm, "alicepw")
      } yield (user, hash).some
    case _       => none.pure[F]
  }
}
