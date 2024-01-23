package rockthejvm.loadbalancer.services

import cats.effect._
import rockthejvm.loadbalancer.domain.UrlsRef._
import rockthejvm.loadbalancer.domain._
import rockthejvm.loadbalancer.http.HttpClient

class HealthCheckBackendsTest extends munit.CatsEffectSuite {

  test("add backend url to the Backends as soon as health check returns success") {

    val backends: Urls     = Urls(Vector("localhost:8082").map(Url.apply))
    val healthChecks: Urls = Urls(Vector("localhost:8081", "localhost:8082").map(Url.apply))

    val obtained: IO[Urls] =
      for {
        backends: Ref[IO, Urls]     <- IO.ref(backends)
        healthChecks: Ref[IO, Urls] <- IO.ref(healthChecks)
        result: Urls                <- HealthCheckBackends.checkHealthAndUpdateBackends(
                                         HealthChecks(healthChecks),
                                         Backends(backends),
                                         ParseUri.Impl,
                                         RoundRobin.forHealthChecks[IO],
                                         // positive health check leaves the backend Urls as is
                                         SendAndExpect.toHealthCheck(HttpClient.Hello),
                                         UpdateBackendsAndGet.Impl
                                       )
      } yield result

    val expected: Urls = Urls(Vector("localhost:8082", "localhost:8081").map(Url.apply))
    assertIO(obtained, expected)
  }

  test("remove backend url from the Backends as soon as health check returns failure") {

    val urls: Urls = Urls(Vector("localhost:8081", "localhost:8082").map(Url.apply))

    val obtained: IO[Urls] =
      for {
        backends: Ref[IO, Urls]     <- IO.ref(urls)
        healthChecks: Ref[IO, Urls] <- IO.ref(urls)
        result: Urls                <- HealthCheckBackends.checkHealthAndUpdateBackends(
                                         HealthChecks(healthChecks),
                                         Backends(backends),
                                         ParseUri.Impl,
                                         RoundRobin.forHealthChecks[IO],
                                         // negative health check removes the first Url from the backend Urls
                                         SendAndExpect.toHealthCheck(HttpClient.TestTimeoutFailure),
                                         UpdateBackendsAndGet.Impl
                                       )
      } yield result

    val expected: Urls = Urls(Vector("localhost:8082").map(Url.apply))
    assertIO(obtained, expected)
  }
}
