package rockthejvm.loadbalancer.services

import rockthejvm.loadbalancer.domain._
import rockthejvm.loadbalancer.domain.UrlsRef._
import rockthejvm.loadbalancer.services.RoundRobin.BackendsRoundRobin
import org.http4s.dsl.Http4sDsl
import org.http4s._
import cats.effect._
import cats._
import cats.syntax.functor._
import cats.syntax.flatMap._

object LoadBalancer {

  def from[F[_]: Async](
      backends: Backends[F],
      backendsRoundRobin: BackendsRoundRobin[F],
      addRequestPathToBackendUrl: AddRequestPathToBackendUrl,
      parseUri: ParseUri,
      sendAndExpectResponse: Request[F] => SendAndExpect[F, String]
  ): HttpRoutes[F] = {

    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] { request =>
      backendsRoundRobin(backends).flatMap { optUrl: Option[Url] =>
        optUrl.fold(Ok("All backends are inactive"): F[Response[F]]) { backendUrl: Url =>
          val url: String = addRequestPathToBackendUrl(backendUrl.value, request)
          (for {
            // uri: Uri            <- IO.fromEither(parseUri(url))
            uri: Uri            <- parseUri(url).fold(MonadThrow[F].raiseError, MonadThrow[F].pure)
            response: String    <- sendAndExpectResponse(request)(uri)
            result: Response[F] <- Ok(response)
          } yield result): F[Response[F]]
        }
      }
    }
  }
}
