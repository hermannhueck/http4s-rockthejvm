package rockthejvm.loadbalancer.services

import rockthejvm.loadbalancer.domain._
import rockthejvm.loadbalancer.domain.UrlsRef.Backends
import rockthejvm.loadbalancer.http.ServerHealthStatus

trait UpdateBackendsAndGet {
  def apply[F[_]](backends: Backends[F], url: Url, status: ServerHealthStatus): F[Urls]
}

object UpdateBackendsAndGet {

  object Impl extends UpdateBackendsAndGet {
    override def apply[F[_]](backends: Backends[F], url: Url, status: ServerHealthStatus): F[Urls] =
      backends
        .urls
        .updateAndGet { urls =>
          status match {
            case ServerHealthStatus.Alive => urls.add(url)
            case ServerHealthStatus.Dead  => urls.remove(url)
          }
        }
  }
}
