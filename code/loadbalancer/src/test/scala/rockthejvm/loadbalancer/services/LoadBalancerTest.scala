package rockthejvm.loadbalancer.services

import rockthejvm.loadbalancer.domain._
import rockthejvm.loadbalancer.domain.UrlsRef.Backends
import rockthejvm.loadbalancer.http.HttpClient
import org.http4s._
import cats.effect._

class LoadBalancerTest extends munit.CatsEffectSuite {

  test("All backends are inactive because Urls is empty") {

    val incomingRequest: Request[IO] = Request[IO]()
    val obtained: IO[String]         = (
      for {
        backends: Ref[IO, Urls]     <- IO.ref(Urls.empty)
        loadBalancer: HttpRoutes[IO] = LoadBalancer.from(
                                         Backends(backends),
                                         RoundRobin.forBackends,
                                         AddRequestPathToBackendUrl.Impl,
                                         ParseUri.Impl,
                                         _ => SendAndExpect.BackendSuccessTest
                                       )
        result: Response[IO]        <- loadBalancer.orNotFound.run(incomingRequest)
      } yield result.body.compile.toVector.map(bytes => new String(bytes.toArray))
    ).flatten

    assertIO(obtained, "All backends are inactive")
  }

  test("Success case") {

    val incomingRequest: Request[IO] = Request[IO](uri = Uri.unsafeFromString("localhost:8080/items/1"))
    val obtained: IO[String]         = (
      for {
        backends: Ref[IO, Urls]     <- IO.ref(Urls(Vector("localhost:8081", "localhost:8082").map(Url.apply)))
        loadBalancer: HttpRoutes[IO] = LoadBalancer.from(
                                         Backends(backends),
                                         RoundRobin.LocalHost8081,
                                         AddRequestPathToBackendUrl.Impl,
                                         ParseUri.Impl,
                                         _ => SendAndExpect.BackendSuccessTest
                                       )
        result: Response[IO]        <-
          loadBalancer.orNotFound.run(incomingRequest)
      } yield result.body.compile.toVector.map(bytes => new String(bytes.toArray))
    ).flatten

    assertIO(obtained, "Success")
  }

  test("Resource not found (404) case") {

    val incomingRequest: Request[IO] = Request[IO](uri = Uri.unsafeFromString("localhost:8080/items/1"))
    val obtained: IO[String]         = (
      for {
        backends: Ref[IO, Urls]     <- IO.ref(Urls(Vector("localhost:8081", "localhost:8082").map(Url.apply)))
        emptyRequest                 = Request[IO]()
        loadBalancer: HttpRoutes[IO] = LoadBalancer.from(
                                         Backends(backends),
                                         RoundRobin.forBackends,
                                         AddRequestPathToBackendUrl.Impl,
                                         ParseUri.Impl,
                                         _ => SendAndExpect.toBackend(HttpClient.BackendResourceNotFound, emptyRequest)
                                       )
        result: Response[IO]        <-
          loadBalancer.orNotFound.run(incomingRequest)
      } yield result.body.compile.toVector.map(bytes => new String(bytes.toArray))
    ).flatten

    assertIO(obtained, s"resource was not found")
  }
}
