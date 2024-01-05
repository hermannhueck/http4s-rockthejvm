package rockthejvm.loadbalancer.services

import org.http4s._

class AddRequestPathToBackendUrlTest extends munit.FunSuite {

  val impl       = AddRequestPathToBackendUrl.Impl
  val backendUrl = "http://localhost:8082"

  test("add '/items/1 to backendUrl") {
    val obtained = impl(backendUrl = backendUrl, Request(uri = Uri.unsafeFromString("localhost:8080/items/1")))
    val expected = "http://localhost:8082/items/1"

    assertEquals(obtained, expected)
  }

  test("since request doesn't have path just return backendUrl") {
    val obtained = impl(backendUrl = backendUrl, Request(uri = Uri.unsafeFromString("localhost:8080")))
    val expected = backendUrl

    assertEquals(obtained, expected)
  }
}
