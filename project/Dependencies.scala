import sbt._

object Dependencies {

  import Versions._

  lazy val http4sEmberServer = "org.http4s"               %% "http4s-ember-server" % http4sVersion
  lazy val http4sEmberClient = "org.http4s"               %% "http4s-ember-client" % http4sVersion
  lazy val http4sDsl         = "org.http4s"               %% "http4s-dsl"          % http4sVersion
  lazy val http4sCirce       = "org.http4s"               %% "http4s-circe"        % http4sVersion
  // lazy val fs2Io             = "co.fs2"                   %% "fs2-io"              % fs2Version
  lazy val circeGeneric      = "io.circe"                 %% "circe-generic"       % circeVersion
  lazy val circeLiteral      = "io.circe"                 %% "circe-literal"       % circeVersion
  lazy val circeCore         = "io.circe"                 %% "circe-core"          % circeVersion
  lazy val circeParser       = "io.circe"                 %% "circe-parser"        % circeVersion
  lazy val ciris             = "is.cir"                   %% "ciris"               % cirisVersion
  lazy val cirisCirce        = "is.cir"                   %% "ciris-circe"         % cirisVersion
  lazy val munit             = "org.scalameta"            %% "munit"               % munitVersion
  lazy val munitCE3          = "org.typelevel"            %% "munit-cats-effect-3" % munitCE3Version
  lazy val scalaCheck        = "org.scalacheck"           %% "scalacheck"          % scalaCheckVersion
  lazy val slf4jApi          = "org.slf4j"                 % "slf4j-api"           % slf4jVersion
  lazy val slf4jSimple       = "org.slf4j"                 % "slf4j-simple"        % slf4jVersion
  lazy val http4sJwtAuth     = "dev.profunktor"           %% "http4s-jwt-auth"     % http4sJwtAuthVersion
  lazy val jwtCore           = "com.github.jwt-scala"     %% "jwt-core"            % jwtScalaVersion
  lazy val jwtCirce          = "com.github.jwt-scala"     %% "jwt-circe"           % jwtScalaVersion
  lazy val otpJava           = "com.github.bastiaanjansen" % "otp-java"            % otpJavaVersion
  lazy val zxing             = "com.google.zxing"          % "javase"              % zxingVersion
  lazy val sendGrid          = "com.sendgrid"              % "sendgrid-java"       % sendGridVersion
  lazy val pureConfig        = "com.github.pureconfig"    %% "pureconfig-core"     % pureConfigVersion
  lazy val pureConfigGeneric = "com.github.pureconfig"    %% "pureconfig-generic"  % pureConfigVersion
  lazy val jcdp              = "com.diogonunes"            % "JCDP"                % jcdpVersion // provides AnsiConsole for logging
  lazy val logback           = "ch.qos.logback"            % "logback-classic"     % logbackVersion % Runtime

  // https://github.com/typelevel/kind-projector
  lazy val kindProjectorPlugin    = compilerPlugin(
    compilerPlugin("org.typelevel" % "kind-projector" % kindProjectorVersion cross CrossVersion.full)
  )
  // https://github.com/oleg-py/better-monadic-for
  lazy val betterMonadicForPlugin = compilerPlugin(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion)
  )

  lazy val myLibraryDependencies = Seq(
    http4sEmberServer,
    http4sEmberClient,
    http4sDsl,
    http4sCirce,
    circeCore,
    circeParser,
    circeGeneric,
    circeLiteral,
    http4sJwtAuth,
    jwtCore,
    jwtCirce,
    ciris,
    cirisCirce,
    slf4jApi,
    slf4jSimple,
    otpJava,
    zxing,
    sendGrid,
    pureConfig,
    logback,
    kindProjectorPlugin,
    betterMonadicForPlugin
  ) ++ Seq(
    munit,
    munitCE3,
    scalaCheck
  ).map(_ % Test)
}
