// See:
// - https://blog.rockthejvm.com/load-balancer/
// - https://www.youtube.com/watch?v=V9t_inPRKMU&t=153s

package rockthejvm.loadbalancer.fast

import cats._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.effect._
import org.http4s._
import org.http4s.dsl._
import org.http4s.implicits._
import com.comcast.ip4s._
import org.http4s.server.Server
import org.http4s.client.Client
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import pureconfig.ConfigSource
import fs2.io.net.Network

object LoadBalancer extends IOApp.Simple {

  // use opaque types in Scala 3
  type Urls = Vector[Uri]

  object Urls {

    val roundRobin: Urls => Urls = {
      case Vector()       => Vector()
      case Vector(single) => Vector(single)
      case multiple       => multiple.tail :+ multiple.head
    }

    val first: Urls => Option[Uri] =
      _.headOption
  }

  // http post mytwitter.com/user/rockthejvm -> http post replica:8081/user/rockthejvm

  def balanceLoad[F[_]: Async](
      backends: Ref[F, Urls],                        // list of backends
      sendAndExpect: (Request[F], Uri) => F[String], // redirection of HTTP requests
      addPathToBackend: (Request[F], Uri) => F[Uri], // add the path to the URI of the backend replica
      updateFunction: Urls => Urls,                  // shuffle the backends
      extractor: Urls => Option[Uri]                 // extract the next backend to send the request to
  ): Resource[F, HttpRoutes[F]] = {

    val dsl = Http4sDsl[F]
    import dsl._

    // extract the first backend/replica
    // forward the request to the backend
    // shuffle the backends
    // return the response to the user
    def routes =
      HttpRoutes.of[F] { request =>
        backends                        // : Ref[F, Urls]
          .getAndUpdate(updateFunction) // : F[Urls]
          .map(extractor)               // : F[Option[Uri]]
          .flatMap { maybeUri: Option[Uri] =>
            maybeUri.fold(Ok("No backends available")) { backendUri: Uri =>
              for {
                uri: Uri            <- addPathToBackend(request, backendUri)
                response: String    <- sendAndExpect(request, uri)
                result: Response[F] <- Ok(response)
              } yield result // : F[Response[F]]
            }
          }
      }

    Resource.pure(routes)
  }

  def getSeedNodes[F[_]: ApplicativeThrow]: F[Urls] =
    ConfigSource
      // .default // reads from application.conf
      .file("src/main/resources/loadbalancer-application.conf")
      .at("backends")
      .load[Vector[String]]
      .map(vec => vec.map(Uri.unsafeFromString))
      .fold(
        error => ApplicativeThrow[F].raiseError(new Exception(s"Could not load seed nodes: $error")),
        urls => ApplicativeThrow[F].pure(urls)
      )

  def sendRequest[F[_]: Async](client: Client[F])(request: Request[F], uri: Uri): F[String] =
    client.expect[String](request.withUri(uri))

  def addRequestPathToBackend[F[_]: Applicative](request: Request[F], uri: Uri): F[Uri] =
    // 1. -----
    // Applicative[F].pure(uri.withPath(request.uri.path))
    // 2. -----
    // Applicative[F].pure {
    //   val path = request.uri.path
    //   uri.withPath(path)
    // }
    // 3. -----
    Applicative[F].pure {
      uri / request.uri.path.renderString.dropWhile(_ != '/')
    }

  override val run: IO[Unit] = {
    val serverResource: Resource[IO, Server] =
      for {
        seedNodes: Urls              <- Resource.eval(getSeedNodes[IO])
        backends: Ref[IO, Urls]      <- Resource.eval(Ref.of[IO, Urls](seedNodes))
        client: Client[IO]           <- EmberClientBuilder.default[IO].build
        loadBalancer: HttpRoutes[IO] <- balanceLoad(
                                          backends,
                                          sendRequest(client),
                                          addRequestPathToBackend[IO],
                                          Urls.roundRobin,
                                          Urls.first
                                        )
        server: Server               <- EmberServerBuilder
                                          .default[IO]
                                          .withHost(host"localhost")
                                          .withPort(port"8080")
                                          .withHttpApp(loadBalancer.orNotFound)
                                          .build
      } yield server // : Resource[IO, Server]

    serverResource.use(_ => IO.println("Load balancer started at http://localhost:8080") *> IO.never)
  }
}

object Replica extends IOApp {

  def routes[F[_]: Sync](port: Int): HttpRoutes[F] = {

    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] { request =>
      Ok(s"[replica:$port] Access to ${request.uri.path}")
    }
  }

  def maybeServer[F[_]: Async: Network](host: String, port: Int, app: HttpApp[F]): Option[Resource[F, Server]] =
    for {
      h <- Host.fromString(host)
      p <- Port.fromInt(port)
    } yield EmberServerBuilder
      .default[F]
      .withHost(h)
      .withPort(p)
      .withHttpApp(app)
      .build

  def run(args: List[String]): IO[ExitCode] = {

    val host = "localhost"
    val port = args(0).toInt

    maybeServer(host, port, routes[IO](port).orNotFound)
      .map(_.use(_ => IO.println("Replica started at $host:$port") *> IO.never))
      .getOrElse(IO.println("Invalid host/port"))
      .as(ExitCode.Success)
  }
}
