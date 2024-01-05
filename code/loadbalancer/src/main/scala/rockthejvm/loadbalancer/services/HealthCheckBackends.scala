package rockthejvm.loadbalancer.services

import rockthejvm.loadbalancer.domain._
import rockthejvm.loadbalancer.domain.UrlsRef._
import rockthejvm.loadbalancer.http.ServerHealthStatus
import rockthejvm.loadbalancer.services.RoundRobin.HealthChecksRoundRobin

import cats.effect._
import scala.concurrent.duration.DurationLong
import cats.Id
import cats.MonadThrow
import cats.syntax.functor._
import cats.syntax.flatMap._
import org.http4s.Uri

object HealthCheckBackends {

  def periodically[F[_]: Async](
      healthChecks: HealthChecks[F],
      backends: Backends[F],
      parseUri: ParseUri,
      updateBackendsAndGet: UpdateBackendsAndGet,
      healthChecksRoundRobin: HealthChecksRoundRobin[F],
      sendAndExpectStatus: SendAndExpect[F, ServerHealthStatus],
      healthCheckInterval: HealthCheckInterval
  ): F[Unit] =
    checkHealthAndUpdateBackends(
      healthChecks,
      backends,
      parseUri,
      healthChecksRoundRobin,
      sendAndExpectStatus,
      updateBackendsAndGet
    )
      .flatMap(_ => Temporal[F].sleep(healthCheckInterval.value.seconds))
      .foreverM

  private[services] def checkHealthAndUpdateBackends[F[_]: Async](
      healthChecks: HealthChecks[F],
      backends: Backends[F],
      parseUri: ParseUri,
      healthChecksRoundRobin: HealthChecksRoundRobin[F],
      sendAndExpectStatus: SendAndExpect[F, ServerHealthStatus],
      updateBackendsAndGet: UpdateBackendsAndGet
  ): F[Urls] =
    for {
      currentUrl: Id[Url]        <- healthChecksRoundRobin(healthChecks)
      // uri: Uri                   <- IO.fromEither(parseUri(currentUrl.value))
      uri: Uri                   <- parseUri(currentUrl.value).fold(MonadThrow[F].raiseError, MonadThrow[F].pure)
      status: ServerHealthStatus <- sendAndExpectStatus(uri)
      updated: Urls              <- updateBackendsAndGet(backends, currentUrl, status)
    } yield updated
}
