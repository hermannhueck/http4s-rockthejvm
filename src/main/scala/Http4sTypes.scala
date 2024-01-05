import cats.data._
import cats.effect._
import fs2.Stream

object Http4sTypes {

  // Here we mimic the types of the Http4s library
  // providing simplified versions of the types in the library.

  object Uri {
    type Scheme    = String
    type Authority = String
    type Path      = String
    type Query     = String
    type Fragment  = String
  }

  final case class Uri(
      scheme: Option[Uri.Scheme],
      authority: Option[Uri.Authority],
      path: Uri.Path,
      query: Uri.Query,
      fragment: Option[Uri.Fragment]
  )

  type EntityBody[+F[_]] = Stream[F, Byte]

  sealed trait Entity[+F[_]] {
    def body: EntityBody[F]
    def length: Option[Long]
  }

  final class Method(val name: String) {}

  final case class Header(name: String, value: String)

  final class Headers(val headers: List[Header])

  case class Status(code: Int)

  final case class HttpVersion(major: Int, minor: Int)

  final class Request[+F[_]](
      val method: Method,
      val uri: Uri,
      val httpVersion: HttpVersion,
      val headers: Headers,
      val entity: Entity[F],
      val attributes: org.typelevel.vault.Vault
  )

  final class Response[+F[_]](
      val status: Status,
      val httpVersion: HttpVersion,
      val headers: Headers,
      val entity: Entity[F],
      val attributes: org.typelevel.vault.Vault
  )

  // ----- Middleware defined in package org.http4s -----

  type Http[F[_], G[_]] = Kleisli[F, Request[G], Response[G]]

  type HttpApp[F[_]]  = Http[F, F]
  // expands to Kleisli[F, Request[F], Response[F]]
  type HttpApp2[F[_]] = Kleisli[F, Request[F], Response[F]]
  implicitly[HttpApp2[IO] =:= HttpApp[IO]]

  type HttpRoutes[F[_]]  = Http[OptionT[F, *], F]
  // expands to Kleisli[OptionT[F, *], Request[F], Response[F]]
  type HttpRoutes2[F[_]] = Kleisli[OptionT[F, *], Request[F], Response[F]]
  implicitly[HttpRoutes2[IO] =:= HttpRoutes[IO]]

  type Callback[A] = Either[Throwable, A] => Unit

  final case class ContextRequest[F[_], A](context: A, req: Request[F])

  type AuthedRequest[F[_], T] = ContextRequest[F, T]

  type AuthedRoutes[T, F[_]] = Kleisli[OptionT[F, *], AuthedRequest[F, T], Response[F]]

  type ContextRoutes[T, F[_]] = Kleisli[OptionT[F, *], ContextRequest[F, T], Response[F]]

  // ----- Middleware defined in package org.http4s.server -----

  /** A middleware is a function of one [[cats.data.Kleisli]] to another, possibly of a different [[Request]] and
    * [[Response]] type. http4s comes with several middlewares for composing common functionality into services.
    *
    * @tparam F
    *   the effect type of the services
    * @tparam A
    *   the request type of the original service
    * @tparam B
    *   the response type of the original service
    * @tparam C
    *   the request type of the resulting service
    * @tparam D
    *   the response type of the resulting service
    */
  type Middleware[F[_], A, B, C, D] = Kleisli[F, A, B] => Kleisli[F, C, D]

  type ContextMiddleware[F[_], T] =
    Middleware[OptionT[F, *], ContextRequest[F, T], Response[F], Request[F], Response[F]]

  type AuthMiddleware[F[_], T] =
    Middleware[OptionT[F, *], AuthedRequest[F, T], Response[F], Request[F], Response[F]]
}
