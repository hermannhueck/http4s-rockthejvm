package rockthejvm.loadbalancer

import cats.effect._
import cats.syntax.apply._
import com.comcast.ip4s.{Host, Port}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax._
import pureconfig.ConfigSource
import rockthejvm.loadbalancer.domain.UrlsRef._
import rockthejvm.loadbalancer.domain._
import rockthejvm.loadbalancer.errors.config.InvalidConfig
import rockthejvm.loadbalancer.http.HttpServer
import rockthejvm.loadbalancer.services.{ParseUri, RoundRobin, UpdateBackendsAndGet}

object Main extends IOApp.Simple {

  implicit def logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private def hostAndPort(
      host: String,
      port: Int
  ): Either[InvalidConfig, (Host, Port)] =
    (
      Host.fromString(host),
      Port.fromInt(port)
    ).tupled.toRight(InvalidConfig)

  override def run: IO[Unit] =
    for {
      config: Config              <- IO(ConfigSource.default.loadOrThrow[Config])
      backendUrls: Urls            = config.backends
      backends: Ref[IO, Urls]     <- IO.ref(backendUrls)
      healthChecks: Ref[IO, Urls] <- IO.ref(backendUrls)
      (host: Host, port: Port)    <- IO.fromEither(hostAndPort(config.host, config.port))
      _: Unit                     <- info"Starting server on $host:$port"
      _: Unit                     <- HttpServer.start(
                                       Backends(backends),
                                       HealthChecks(healthChecks),
                                       port,
                                       host,
                                       config.healthCheckInterval,
                                       ParseUri.Impl,
                                       UpdateBackendsAndGet.Impl,
                                       RoundRobin.forBackends[IO],
                                       RoundRobin.forHealthChecks[IO]
                                     )
    } yield ()
}
