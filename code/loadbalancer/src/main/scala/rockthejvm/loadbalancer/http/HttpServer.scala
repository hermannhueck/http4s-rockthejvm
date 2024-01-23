package rockthejvm.loadbalancer.http

import cats.effect.syntax.resource._
import cats.effect.{Async, Resource}
import com.comcast.ip4s._
import fs2.io.net.Network
import org.http4s._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.Logger
import rockthejvm.loadbalancer.domain.UrlsRef.{Backends, HealthChecks}
import rockthejvm.loadbalancer.domain._
import rockthejvm.loadbalancer.services.RoundRobin.{BackendsRoundRobin, HealthChecksRoundRobin}
import rockthejvm.loadbalancer.services.{
  AddRequestPathToBackendUrl,
  HealthCheckBackends,
  LoadBalancer,
  ParseUri,
  SendAndExpect,
  UpdateBackendsAndGet
}

object HttpServer {

  def start[F[+_]: Async: Network](
      backends: Backends[F],
      healthChecks: HealthChecks[F],
      port: Port,
      host: Host,
      healthCheckInterval: HealthCheckInterval,
      parseUri: ParseUri,
      updateBackendsAndGet: UpdateBackendsAndGet,
      backendsRoundRobin: BackendsRoundRobin[F],
      healthChecksRoundRobin: HealthChecksRoundRobin[F]
  ): F[Unit] =
    (
      for {
        client: Client[F]          <- EmberClientBuilder
                                        .default[F]
                                        .build: Resource[F, Client[F]]
        httpClient: HttpClient[F]   = HttpClient.of(client): HttpClient[F]
        loadBalancer: HttpRoutes[F] = LoadBalancer
                                        .from(
                                          backends,
                                          backendsRoundRobin,
                                          AddRequestPathToBackendUrl.Impl,
                                          parseUri,
                                          SendAndExpect.toBackend(httpClient, _: Request[F])
                                        )
        httpApp: HttpApp[F]         = Logger.httpApp(logHeaders = false, logBody = true)(
                                        loadBalancer.orNotFound
                                      ): HttpApp[F]
        _: Server                  <- EmberServerBuilder
                                        .default[F]
                                        .withHost(host)
                                        .withPort(port)
                                        .withHttpApp(httpApp)
                                        .build: Resource[F, Server]
        _: Unit                    <- HealthCheckBackends
                                        .periodically(
                                          healthChecks,
                                          backends,
                                          parseUri,
                                          updateBackendsAndGet,
                                          healthChecksRoundRobin,
                                          SendAndExpect.toHealthCheck(httpClient),
                                          healthCheckInterval
                                        )
                                        .toResource: Resource[F, Unit]
      } yield ()
    ).useForever
}
