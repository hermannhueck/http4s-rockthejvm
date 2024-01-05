package rockthejvm.loadbalancer.domain

class UrlsTest extends munit.FunSuite {

  private def sequentialUrls(from: Int, to: Int): Urls = Urls {
    (from to to)
      .map(i => Url(s"url$i"))
      .toVector
  }

  test("Urls(url1, url2, ...).currentOpt must return Some(url1)") {
    val urls     = sequentialUrls(1, 5)
    val obtained = urls.currentOpt.map(_.value)
    val expected = Some("url1")

    assertEquals(obtained, expected)
  }

  test("Urls.empty.currentOpt must return None") {
    val obtained = Urls.empty.currentOpt.map(_.value)

    assertEquals(obtained, None)
  }

  test("Urls(url1, url2, ...).currentUnsafe must return url1") {
    val urls     = sequentialUrls(1, 5)
    val obtained = urls.currentUnsafe.value
    val expected = "url1"

    assertEquals(obtained, expected)
  }

  test("Urls.empty.currentUnsafe should throw NoSuchElementException (based on Vector implementation)") {
    intercept[NoSuchElementException] {
      Urls.empty.currentUnsafe
    }
  }

  test("Urls(url1, url2, ...).remove should drop url1") {
    val urls     = sequentialUrls(1, 5)
    val obtained = urls.remove(Url("url1"))
    val expected = sequentialUrls(2, 5)

    assertEquals(obtained, expected)
  }

  test("Urls(url2, url3, ...).add should append url1 to the end of the Vector") {
    val urls     = sequentialUrls(2, 5)
    val obtained = urls.add(Url("url1"))
    val expected = Urls(urls.values :+ Url("url1"))

    assertEquals(obtained, expected)
  }
}
