/*
  see:
    https://blog.rockthejvm.com/a-5-minute-akka-http-client/
    https://www.youtube.com/watch?v=Agze0Ule5_0

  This is same program using http4s instead of akka-http
 */
package rockthejvm.marksu_client

import cats.effect._
import fs2.io.net.Network
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._

object GetHtmlCode extends IOApp.Simple {

  val source =
    """
      |object SimpleApp {
      |  val aField = 2
      |
      |  def aMethod(x: Int) = x + 1
      |
      |  def main(args: Array[String]) = {
      |    println(aMethod(aField))
      |  }
      |}""".stripMargin

  val uri = uri"http://markup.su/api/highlighter"

  val urlForm = UrlForm(
    "language" -> "Scala",
    "theme"    -> "SunBurst",
    "source"   -> source
  )

  import org.typelevel.log4cats.LoggerFactory

  @annotation.nowarn("cat=unused-params")
  def createClient[F[_]: Async: Network: LoggerFactory]: Resource[F, Client[F]] =
    org.http4s.ember.client.EmberClientBuilder.default[F].build

  override val run: IO[Unit] = {

    val request = Request[IO](
      method = Method.POST,
      uri = uri
    ).withEntity(urlForm) // implicitly encodes the UrlForm into the request body
    // .withEntity(urlForm)(UrlForm.entityEncoder[IO]) // equivalent to the above

    import org.typelevel.log4cats.slf4j.Slf4jFactory
    implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

    createClient[IO]
      .use { client: Client[IO] =>
        for {
          result <- sendGetRequest(client, request)
        } yield printResult(result)
      }
  }

  def sendGetRequest[F[_]: Concurrent](
      client: Client[F],
      request: Request[F]
  ): F[Either[String, List[String]]] = {

    import cats.syntax.functor._ // for map

    client.run(request).use { response =>
      response.bodyText.compile.toList.map { body =>
        if (!response.status.isSuccess)
          Left(s"Request failed. Status: ${response.status.code} (${response.status.reason})")
        else
          Right(body)
      }
    }
  }

  def printResult(result: Either[String, List[String]]): Unit = {

    import scala.util.chaining._
    import hutil.stringformat._

    line80.green pipe println

    result
      .fold(_.red, _.mkString("\n"))
      .pipe(println)

    line80.green pipe println
    System.out.flush()
  }
}
