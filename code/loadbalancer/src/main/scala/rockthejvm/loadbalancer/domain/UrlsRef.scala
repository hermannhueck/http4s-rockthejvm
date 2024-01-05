package rockthejvm.loadbalancer.domain

import cats.effect.Ref

sealed trait UrlsRef[F[_]] extends Product with Serializable {
  def urls: Ref[F, Urls]
}

object UrlsRef {
  case class Backends[F[_]](override val urls: Ref[F, Urls])     extends UrlsRef[F]
  case class HealthChecks[F[_]](override val urls: Ref[F, Urls]) extends UrlsRef[F]
}
