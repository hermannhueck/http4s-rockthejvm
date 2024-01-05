package rockthejvm.loadbalancer.domain

import rockthejvm.loadbalancer.domain.Url
import pureconfig.ConfigReader
// import pureconfig.generic.derivation.default._ // for Scala 3 derivation
import pureconfig.generic.semiauto._

final case class Config(
    port: Int,
    host: String,
    backends: Urls,
    healthCheckInterval: HealthCheckInterval
) // derives ConfigReader[Config] // for Scala 3 derivation

object Config {

  implicit val urlReader: ConfigReader[Url] =
    ConfigReader[String].map(Url.apply)

  implicit val urlsReader: ConfigReader[Urls] =
    ConfigReader[Vector[Url]].map(Urls.apply)

  implicit val healthCheckReader: ConfigReader[HealthCheckInterval] =
    ConfigReader[Long].map(HealthCheckInterval.apply)

  // Scala 2 derivation
  implicit val configReader: ConfigReader[Config] =
    deriveReader[Config]
}
