package rockthejvm.loadbalancer.services

import rockthejvm.loadbalancer.http.{HttpClient, ServerHealthStatus}

import org.http4s.client.UnexpectedStatus
import org.http4s.{Request, Uri}
import org.http4s.EntityDecoder

import cats.effect.{Async, Sync}
import cats.effect.implicits._

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.syntax._

import cats.syntax.option._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicativeError._

import scala.concurrent.duration.DurationInt

trait SendAndExpect[F[_], A] {
  def apply(uri: Uri)(implicit
      sync: Async[F],
      decoder: EntityDecoder[F, String]
  ): F[A]
}

object SendAndExpect {

  implicit def logger[F[_]: Sync]: Logger[F] =
    Slf4jLogger.getLogger[F]

  def toBackend[F[_]](httpClient: HttpClient[F], req: Request[F]): SendAndExpect[F, String] =
    new SendAndExpect[F, String] {
      override def apply(uri: Uri)(implicit
          sync: Async[F],
          decoder: EntityDecoder[F, String]
      ): F[String] =
        info"[LOAD-BALANCER] sending request to $uri" *>
          httpClient
            .sendAndReceive(uri, req.some)
            .handleErrorWith {
              case UnexpectedStatus(org.http4s.Status.NotFound, _, _) =>
                s"resource was not found"
                  .pure[F]
                  .flatTap(msg => warn"$msg")
              case _                                                  =>
                s"server with uri: $uri is dead"
                  .pure[F]
                  .flatTap(msg => warn"$msg")
            }
    }

  def toHealthCheck[F[_]](httpClient: HttpClient[F]): SendAndExpect[F, ServerHealthStatus] =
    new SendAndExpect[F, ServerHealthStatus] {
      override def apply(uri: Uri)(implicit
          sync: Async[F],
          decoder: EntityDecoder[F, String]
      ): F[ServerHealthStatus] =
        info"[HEALTH-CHECK] checking $uri health" *>
          httpClient
            .sendAndReceive(uri, none)
            .as(ServerHealthStatus.Alive.widen)
            .flatTap(_ => info"$uri is alive")
            .timeout(5.seconds)
            .handleErrorWith { _ =>
              warn"$uri is dead" *>
                ServerHealthStatus.Dead.widen.pure[F]
            }
    }

  // used for testing only

  import cats.effect.IO

  val BackendSuccessTest: SendAndExpect[IO, String] = new SendAndExpect[IO, String] {
    override def apply(uri: Uri)(implicit
        sync: Async[IO],
        decoder: EntityDecoder[IO, String]
    ): IO[String] =
      IO("Success")
  }
}
