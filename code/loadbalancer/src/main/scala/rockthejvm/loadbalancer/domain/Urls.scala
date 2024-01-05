package rockthejvm.loadbalancer.domain

import scala.util.Try

final case class Urls(values: Vector[Url]) extends AnyVal {

  def currentOpt: Option[Url] =
    Try(currentUnsafe).toOption

  def currentUnsafe: Url =
    values.head

  def remove(url: Url): Urls =
    copy(values.filter(_ != url))

  def add(url: Url): Urls =
    if (values contains url) this
    else copy(values :+ url)
}

object Urls {
  def empty: Urls = Urls(Vector.empty)
}
