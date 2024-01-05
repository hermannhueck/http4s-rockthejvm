package rockthejvm.loadbalancer.services

import rockthejvm.loadbalancer.domain.{Url, Urls}
import rockthejvm.loadbalancer.domain.UrlsRef._
import cats.effect.IO

class RoundRobinTest extends munit.CatsEffectSuite {

  test("forBackends [Some, one url]") {
    val roundRobin = RoundRobin.forBackends[IO]
    val assertion  = for {
      ref        <- IO.ref(Urls(Vector(Url("localhost:8082"))))
      backends    = Backends(ref)
      assertion1 <- roundRobin(backends)
                      .map(_.exists(_.value == "localhost:8082"))
      assertion2 <- roundRobin(backends)
                      .map(_.exists(_.value == "localhost:8082"))
    } yield assertion1 && assertion2

    assertIOBoolean(assertion)
  }

  test("forBackends [Some, multiple urls]") {
    val roundRobin = RoundRobin.forBackends[IO]
    val urls       = Urls(Vector("localhost:8081", "localhost:8082").map(Url.apply))
    val assertion  = for {
      ref        <- IO.ref(urls)
      backends    = Backends(ref)
      assertion1 <- roundRobin(backends)
                      .map(urlOpt => urlOpt.exists(_.value == "localhost:8081"))
      assertion2 <- ref
                      .get
                      .map(urls => urls.values.map(_.value) == Vector("localhost:8082", "localhost:8081"))
      assertion3 <- roundRobin(backends)
                      .map(urlOpt => urlOpt.exists(url => url.value == "localhost:8082"))
      assertion4 <- ref
                      .get
                      .map(urls => urls.values.map(url => url.value) == Vector("localhost:8081", "localhost:8082"))
    } yield List(assertion1, assertion2, assertion3, assertion4).reduce(_ && _)

    assertIOBoolean(assertion)
  }

  test("forBackends [None]") {
    val roundRobin = RoundRobin.forBackends[IO]
    val assertion  = for {
      ref    <- IO.ref(Urls.empty)
      result <- roundRobin(Backends(ref))
    } yield result.isEmpty

    assertIOBoolean(assertion)
  }

  test("forHealthChecks [Some, one url]") {
    val roundRobin = RoundRobin.forHealthChecks[IO]
    val assertion  = for {
      ref    <- IO.ref(Urls(Vector(Url("localhost:8082"))))
      result <- roundRobin(HealthChecks(ref))
    } yield result.value == "localhost:8082"

    assertIOBoolean(assertion)
  }

  test("forHealthChecks [Some, multiple urls]") {
    val roundRobin = RoundRobin.forHealthChecks[IO]
    val urls       = Urls(Vector("localhost:8081", "localhost:8082").map(Url.apply))
    val assertion  = for {
      ref         <- IO.ref(urls)
      healthChecks = HealthChecks(ref)
      assertion1  <- roundRobin(healthChecks)
                       .map(urlId => urlId.value == "localhost:8081")
      assertion2  <- ref
                       .get
                       .map(urls => urls.values.map(url => url.value) == Vector("localhost:8082", "localhost:8081"))
      assertion3  <- roundRobin(healthChecks)
                       .map(_.value == "localhost:8082")
      assertion4  <- ref
                       .get
                       .map(urlId => urlId.values.map(_.value) == Vector("localhost:8081", "localhost:8082"))
    } yield List(assertion1, assertion2, assertion3, assertion4).reduce(_ && _)

    assertIOBoolean(assertion)
  }

  test("forHealthChecks [Exception, empty urls]") {
    val roundRobin = RoundRobin.forHealthChecks[IO]
    val urls       = Urls(Vector.empty)
    val assertion  = for {
      ref    <- IO.ref(urls)
      result <- roundRobin(HealthChecks(ref))
                  .as(false)
                  .handleError {
                    // thrown by Urls(...).currentUnsafe
                    case _: NoSuchElementException => true
                    case _                         => false
                  }
    } yield result

    assertIOBoolean(assertion)
  }

  test("forBackends [Some, with stateful Ref updates]") {
    val roundRobin = RoundRobin.forBackends[IO]
    val urls       = Urls(Vector("localhost:8081", "localhost:8082").map(Url.apply))
    val assertion  = for {
      ref        <- IO.ref(urls)
      backends    = Backends(ref)
      assertion1 <- roundRobin(backends) // 8082, 8081
                      .map(urlOpt => urlOpt.exists(url => url.value == "localhost:8081"))
      _          <- ref.getAndUpdate { urls =>
                      Urls(urls.values :+ Url("localhost:8083")) // 8082, 8081, 8083
                    }
      assertion2 <- roundRobin(backends) // 8081, 8083, 8082
                      .map(urlOpt => urlOpt.exists(url => url.value == "localhost:8082"))
      assertion3 <- ref
                      .get
                      .map { urls =>
                        println(urls)
                        urls
                          .values
                          .map(url => url.value) == Vector("localhost:8081", "localhost:8083", "localhost:8082")
                      }
      assertion4 <- roundRobin(backends)
                      .map(urlOpt => urlOpt.exists(url => url.value == "localhost:8081"))
      assertion5 <- roundRobin(backends)
                      .map(urlOpt => urlOpt.exists(url => url.value == "localhost:8083"))
    } yield List(assertion1, assertion2, assertion3, assertion4, assertion5).reduce(_ && _)

    assertIOBoolean(assertion)
  }
}
