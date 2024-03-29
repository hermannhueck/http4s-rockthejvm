package rockthejvm.loadbalancer.services

import cats.effect.IO
import munit.CatsEffectSuite
import rockthejvm.loadbalancer.domain.UrlsRef.Backends
import rockthejvm.loadbalancer.domain.{Url, Urls}
import rockthejvm.loadbalancer.http.ServerHealthStatus

class UpdateBackendsAndGetTest extends CatsEffectSuite {

  val updateBackendsAndGet = UpdateBackendsAndGet.Impl
  val localhost8083        = "localhost:8083"
  val initialUrls          = Vector("localhost:8081", "localhost:8082").map(Url.apply)

  test("Add the passed url to the Backends when the server status is Alive") {
    val urls     = Urls(initialUrls)
    val obtained =
      for {
        ref     <- IO.ref(urls)
        updated <- updateBackendsAndGet(Backends(ref), Url(localhost8083), ServerHealthStatus.Alive)
      } yield updated

    assertIO(obtained, Urls(initialUrls :+ Url(localhost8083)))
  }

  test("Add the passed url to the Backends when the server status is Dead") {
    val urls     = Urls(initialUrls :+ Url(localhost8083))
    val obtained =
      for {
        ref     <- IO.ref(urls)
        updated <- updateBackendsAndGet(Backends(ref), Url(localhost8083), ServerHealthStatus.Dead)
      } yield updated

    assertIO(obtained, Urls(initialUrls))
  }
}
