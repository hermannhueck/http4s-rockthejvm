package rockthejvm.loadbalancer.services

import scala.util.Try

import cats._
import cats.syntax.functor._
import rockthejvm.loadbalancer.domain.{Url, Urls, UrlsRef}

trait RoundRobin[F[_], G[_]] {
  def apply(ref: UrlsRef[F]): F[G[Url]]
}

object RoundRobin {

  type BackendsRoundRobin[F[_]]     = RoundRobin[F, Option]
  type HealthChecksRoundRobin[F[_]] = RoundRobin[F, Id]

  def forBackends[F[_]: Functor]: BackendsRoundRobin[F] = new BackendsRoundRobin[F] {
    override def apply(ref: UrlsRef[F]): F[Option[Url]] =
      ref
        .urls
        .getAndUpdate(next)
        .map(_.currentOpt)
  }

  def forHealthChecks[F[_]: Functor]: HealthChecksRoundRobin[F] = new HealthChecksRoundRobin[F] {
    override def apply(ref: UrlsRef[F]): F[Id[Url]] =
      ref
        .urls
        .getAndUpdate(next)
        .map(_.currentUnsafe)
  }

  private def next(urls: Urls): Urls =
    Try(Urls(urls.values.tail :+ urls.values.head))
      .getOrElse(Urls.empty)

  // used for testing only
  import cats.effect.IO

  val TestId: RoundRobin[IO, Id]            = _ => IO.pure(Url("localhost:8081"))
  val LocalHost8081: RoundRobin[IO, Option] = _ => IO.pure(Some(Url("localhost:8081")))
}
