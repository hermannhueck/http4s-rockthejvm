package rockthejvm.loadbalancer.services

import org.http4s.Request

trait AddRequestPathToBackendUrl {
  def apply[F[_]](backendUrl: String, request: Request[F]): String
}

object AddRequestPathToBackendUrl {
  object Impl extends AddRequestPathToBackendUrl {
    override def apply[F[_]](backendUrl: String, request: Request[F]): String = {
      val requestPath = request
        .uri
        .path
        .renderString
        .dropWhile(_ != '/')

      backendUrl concat requestPath
    }
  }
}
