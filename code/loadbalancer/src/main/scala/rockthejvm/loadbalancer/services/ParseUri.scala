package rockthejvm.loadbalancer.services

import rockthejvm.loadbalancer.errors.parsing.InvalidUri
import cats.syntax.either._
import org.http4s.Uri

trait ParseUri {
  def apply(uri: String): Either[InvalidUri, Uri]
}

object ParseUri {
  object Impl extends ParseUri {

    /** Either returns proper Uri or InvalidUri
      */
    override def apply(uri: String): Either[InvalidUri, Uri] =
      Uri
        .fromString(uri)
        .leftMap(_ => InvalidUri(uri))
  }
}
