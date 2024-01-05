package rockthejvm.loadbalancer.http

import org.http4s.client.Client // UnexpectedStatus
import org.http4s.{Request, Uri}
import org.http4s.EntityDecoder

// import scala.concurrent.duration.DurationInt

trait HttpClient[F[_]] {
  def sendAndReceive(uri: Uri, requestOpt: Option[Request[F]])(implicit
      decoder: EntityDecoder[F, String]
  ): F[String]
}

object HttpClient {

  def of[F[_]](client: Client[F]) /*(implicit decoder: EntityDecoder[F, String])*/: HttpClient[F] = new HttpClient[F] {
    override def sendAndReceive(uri: Uri, requestOpt: Option[Request[F]])(implicit
        decoder: EntityDecoder[F, String]
    ): F[String] =
      requestOpt match {
        case Some(request) => client.expect[String](request.withUri(uri))
        case None          => client.expect[String](uri)
      }
  }

  // used for testing only
  import scala.concurrent.duration._
  import org.http4s.client.UnexpectedStatus
  import cats.effect.IO

  def testClient(impl: IO[String]): HttpClient[IO] = new HttpClient[IO] {
    override def sendAndReceive(uri: Uri, requestOpt: Option[Request[IO]])(implicit
        decoder: EntityDecoder[IO, String]
    ): IO[String] = impl
  }

  val Hello: HttpClient[IO]                   = testClient(IO.pure("Hello"))
  val RuntimeException: HttpClient[IO]        = testClient(IO.raiseError(new RuntimeException("Server is dead")))
  val TestTimeoutFailure: HttpClient[IO]      = testClient(IO.sleep(6.seconds).as(""))
  val BackendResourceNotFound: HttpClient[IO] = testClient(
    IO.raiseError {
      UnexpectedStatus(
        org.http4s.Status.NotFound,
        org.http4s.Method.GET,
        Uri.unsafeFromString("localhost:8081")
      )
    }
  )
}
