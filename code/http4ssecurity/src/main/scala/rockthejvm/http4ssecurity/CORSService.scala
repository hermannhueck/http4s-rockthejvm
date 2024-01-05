// See:
// - https://www.youtube.com/watch?v=z3F3iAE8U-U
// - https://blog.rockthejvm.com/security-in-http4s/
//

/*
   CORS = Cross Origin Resource Sharing
   ------------------------------------

   It’s a technique used by browsers to ensure secure requests and data transfers
   from any origin other than its own. Here’s how this would work in 3 simple steps.

   1. img.com needs to load images on its landing page but these images are hosted on imagebucket.com

   2. When someone visits img.com, his/her browser will send a request for images to imagebucket.com,
      this is called a cross-origin request.

   3. If imagebucket.com setup cross-origin resource sharing to include img.com,
      then the browser will proceed and load these requests, otherwise the request
      will be canceled and the images will fail to load.

   Imagine for a second that CORS didn’t exist, malicious sites could easily request
   and acquire information from any site by making cross-origin requests.
   Typically a server should contain a list of approved sites to which
   cross-origin resource sharing is approved, any requests made from sites
   outside this list should be denied.
 */

package rockthejvm.http4ssecurity

import cats._
import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s._
import org.http4s.server.middleware.CORS
import org.http4s.headers.Origin
import org.http4s.server.Server
import fs2.io.net.Network

object CORSService extends IOApp.Simple {

  def imageService[F[_]: Monad]: HttpApp[F] = {

    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes
      .of[F] { case GET -> Root / "image" / name =>
        Ok(s"Processing image: $name.")
      }
      .orNotFound
  }

  // allow all origins
  def corsServiceAllowAll[F[_]: Monad]: HttpApp[F] =
    CORS
      .policy
      .withAllowOriginAll(imageService)

  // allow only specific origins
  def corsServiceAllowHost[F[_]: Monad]: HttpApp[F] =
    CORS
      .policy
      .withAllowOriginHost(
        Set(
          // static website served from localhost:3000
          // that is where the image request is coming from.
          Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(3000)),
          Origin.Host(Uri.Scheme.https, Uri.RegName("localhost"), Some(3000))
        )
      )
      .apply(imageService)

  def serverResource[F[_]: Async: Network]: Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(corsServiceAllowHost)
      .build

  override def run: IO[Unit] =
    serverResource[IO].use { _ =>
      IO.println("----- CORSService started on port 8080. -----") *>
        IO.never
    }
}
