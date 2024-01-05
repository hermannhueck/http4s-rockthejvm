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

package rockthejvm.oauth

import io.circe._
import io.circe.parser._
import ciris._
import ciris.circe._
import java.nio.file.Paths
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.dsl._
import org.http4s.implicits._
import cats.effect._
import cats.implicits._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.{Accept, Authorization}
import cats.data.OptionT
import org.http4s.client.Client
import fs2.io.net.Network
import fs2.io.file.Files

final case class AppConfig(key: String, secret: Secret[String])
object AppConfig {

  implicit val appDecoder: Decoder[AppConfig] =
    Decoder.instance { cursor =>
      for {
        key    <- cursor.get[String]("key")
        secret <- cursor.get[String]("secret")
      } yield AppConfig(key, Secret(secret))
    }

  implicit val appConfigDecoder: ConfigDecoder[String, AppConfig] =
    circeConfigDecoder("AppConfig")
}

final case class ServerConfig(host: Host, port: Port)
object ServerConfig {

  implicit val serverDecoder: Decoder[ServerConfig] =
    Decoder.instance { cursor =>
      for {
        gotHost <- cursor.get[String]("host")
        gotPort <- cursor.get[Int]("port")
      } yield ServerConfig(
        Host.fromString(gotHost).getOrElse(host"0.0.0.0"),
        Port.fromInt(gotPort).getOrElse(port"8080")
      )
    }

  implicit val serverConfigDecoder: ConfigDecoder[String, ServerConfig] =
    circeConfigDecoder("ServerConfig")
}

final case class Config(appConfig: AppConfig, serverConfig: ServerConfig)

object GithubTokenQueryParamMatcher extends QueryParamDecoderMatcher[String]("code")

final case class GithubTokenResponse(access_token: String, token_type: String, scope: String)
object GithubTokenResponse {

  implicit val githubTokenResponseDecoder: Decoder[GithubTokenResponse] =
    Decoder.instance { cursor =>
      for {
        access_token <- cursor.get[String]("access_token")
        token_type   <- cursor.get[String]("token_type")
        scope        <- cursor.get[String]("scope")
      } yield GithubTokenResponse(access_token, token_type, scope)
    }
}

final case class GithubUserResponse(email: String, primary: Boolean, verified: Boolean)
object GithubUserResponse {

  implicit val githubUserResponseDecoder: Decoder[GithubUserResponse] =
    Decoder.instance { cursor =>
      for {
        email    <- cursor.get[String]("email")
        primary  <- cursor.get[Boolean]("primary")
        verified <- cursor.get[Boolean]("verified")
      } yield GithubUserResponse(email, primary, verified)
    }
}

object OAuthWithHttp4s extends IOApp.Simple {
  /*
    OAuth Flow:

    1. The user tries to log into my application. - Click a button or link
    2. The user is redirected to the OAuth provider’s (Github's) authorization page.
    3. The user is redirected back to my application's callback page with a single-use authorization code.
    4. My application will respond to the OAuth provider Github with the same authorization code, a client id, and a client secret.
    5. The OAuth provider Github responds with an OAuth access token and some data about the user.
    6. My application shows sonthing to the user depending on the token and data received from Github.
   */

  import org.typelevel.log4cats.LoggerFactory
  import org.typelevel.log4cats.slf4j.Slf4jFactory
  implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val appConfig: ConfigValue[Effect, AppConfig]       =
    file(Paths.get("src/main/resources/appConfig.json"))
      .as[AppConfig]
  val serverConfig: ConfigValue[Effect, ServerConfig] =
    file(Paths.get("src/main/resources/serverConfig.json"))
      .as[ServerConfig]
  val config: ConfigValue[Effect, Config]             =
    (appConfig, serverConfig).parMapN(Config)

  @annotation.nowarn("cat=unused-params")
  def emberClientResource[F[_]: Async: Network: LoggerFactory]: Resource[F, Client[F]] =
    EmberClientBuilder
      .default[F]
      .build

  def getJsonString[F[_]: Async: Network: LoggerFactory](request: Request[F]): F[String] =
    emberClientResource[F]
      .use { _.expect[String](request) }

  def fetchAccessToken[F[_]: Async: Network: LoggerFactory](code: String, config: AppConfig): F[Option[String]] = {

    // access https://github.com/login/oauth/access_token using a POST request
    val form    = UrlForm(
      "client_id"     -> config.key,
      "client_secret" -> config.secret.value,
      "code"          -> code
    )
    val request = Request[F](
      method = Method.POST,
      uri = uri"https://github.com/login/oauth/access_token",
      headers = Headers(Accept(MediaType.application.json))
    ).withEntity(form)

    // 1. contact Github's authorization server with the authorization code (we need to be an HTTP client)
    // 2. Github will respond with a json String containing the access token
    // 3. we need to decode the access token from the json String
    getJsonString(request)
      .map { jsonString =>
        decode[GithubTokenResponse](jsonString)
          .toOption
          .map(_.access_token)
      }
  }

  def fetchUserInfo[F[_]: Async: Network: LoggerFactory](token: String): F[Option[String]] = {
    // access https://api.github.com/user using a GET request
    val request = Request[F](
      method = Method.GET,
      uri = uri"https://api.github.com/user",
      headers = Headers(
        Accept(MediaType.application.json),
        Authorization(Credentials.Token(AuthScheme.Bearer, token))
      )
    )

    // 1. contact Github's API with the access token (we need to be an HTTP client)
    // 2. Github will respond with a json String containing the user info
    // 3. we need to decode the user info from the json String
    getJsonString(request)
      .map { jsonString =>
        decode[Json](jsonString)
          .toOption
          .map(_.spaces4)
      }
    // .map { jsonString =>
    //   decode[List[GithubUserResponse]](jsonString)
    //     .toOption
    //     .flatMap(_.find(_.primary))
    //     .map(_.email)
    // }
  }

  def getOAuthResult[F[_]: Async: Network: LoggerFactory](code: String, config: AppConfig): F[String] =
    (for {
      token <- OptionT(fetchAccessToken(code, config))
      user  <- OptionT(fetchUserInfo(token))
    } yield s"User \n\n$user\n\nsuccesfully logged in").value.map(_.getOrElse("Authentication failed"))

  def routes[F[_]: Async: Files: Network: LoggerFactory](config: AppConfig): HttpRoutes[F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {

      // /home
      case req @ GET -> Root / "home"                                     =>
        StaticFile
          .fromString("src/main/resources/html/index.html", Some(req))
          .getOrElseF(NotFound())

      // /callback?code=...
      case GET -> Root / "callback" :? GithubTokenQueryParamMatcher(code) =>
        getOAuthResult(code, config).flatMap(result => Ok(result))
    }
  }

  def server[F[_]: Async: Files: Network: LoggerFactory](config: Config) =
    EmberServerBuilder
      .default[F]
      .withHost(config.serverConfig.host)
      .withPort(config.serverConfig.port)
      .withHttpApp(routes(config.appConfig).orNotFound)
      .build

  val run: IO[Unit] = {
    config
      .load[IO]
      .flatMap { config =>
        server[IO](config).useForever
      }
  }
}
