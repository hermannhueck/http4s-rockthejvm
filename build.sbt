val projectName        = "http4s-rockthejvm"
val projectDescription = "Http4s Tutorials by rockthejvm"

ThisBuild / fork                   := true
ThisBuild / turbo                  := true // default: false
ThisBuild / includePluginResolvers := true // default: false
Global / onChangedBuildSource      := ReloadOnSourceChanges

inThisBuild(
  Seq(
    version                  := Versions.projectVersion,
    scalaVersion             := Versions.scala2Version,
    publish / skip           := true,
    scalacOptions ++= ScalacOptions.defaultScalacOptions,
    semanticdbEnabled        := true,
    semanticdbVersion        := scalafixSemanticdb.revision,
    scalafixDependencies ++= Seq(
      "com.github.liancheng" %% "organize-imports" % Versions.scalafixOrganizeImportsVersion
    ),
    Test / parallelExecution := false,
    // run 100 tests for each property // -s = -minSuccessfulTests
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-s", "100"),
    initialCommands          :=
      s"""|
          |import scala.util.chaining._
          |import scala.concurrent.duration._
          |println()
          |""".stripMargin // initialize REPL
  )
)

import Dependencies._

lazy val root = (project in file("."))
  .aggregate(
    intro,
    marksu_client,
    auth,
    oauth,
    twofactorauth,
    loadbalancer,
    http4ssecurity
  )
  .settings(
    name                              := projectName,
    description                       := projectDescription,
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sDsl,
      kindProjectorPlugin,
      betterMonadicForPlugin
    )
  )

lazy val intro = (project in file("code/intro"))
  .settings(
    name                              := "intro",
    description                       := "Introductive Tutorial to http4s",
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sEmberServer,
      http4sDsl,
      http4sCirce,
      circeCore,
      circeParser,
      circeGeneric,
      circeLiteral,
      slf4jApi,
      slf4jSimple,
      kindProjectorPlugin,
      betterMonadicForPlugin
    )
  )

lazy val marksu_client = (project in file("code/marksu_client"))
  .settings(
    name                              := "marksu_client",
    description                       := "A simple http4s client for the Marksu API",
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sEmberClient,
      http4sDsl,
      slf4jApi,
      slf4jSimple,
      kindProjectorPlugin,
      betterMonadicForPlugin
    )
  )

lazy val auth = (project in file("code/auth"))
  .settings(
    name                              := "auth",
    description                       := "Different Authentication Methods with http4s: Basic, Digest, Sessions, JWT",
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sEmberServer,
      http4sDsl,
      circeCore,
      circeParser,
      http4sJwtAuth,
      jwtCore,
      jwtCirce,
      slf4jApi,
      slf4jSimple,
      kindProjectorPlugin,
      betterMonadicForPlugin
    )
  )

lazy val oauth = (project in file("code/oauth"))
  .settings(
    name                              := "oauth",
    description                       := "OAuth Tutorial with http4s",
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sEmberServer,
      http4sEmberClient,
      http4sDsl,
      http4sCirce,
      circeCore,
      circeParser,
      ciris,
      cirisCirce,
      slf4jApi,
      slf4jSimple,
      kindProjectorPlugin,
      betterMonadicForPlugin
    )
  )

lazy val twofactorauth = (project in file("code/twofactorauth"))
  .settings(
    name                              := "twofactorauth",
    description                       := "2-Factor Authentication with http4s",
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sEmberServer,
      http4sDsl,
      slf4jApi,
      slf4jSimple,
      otpJava,
      zxing,
      sendGrid,
      kindProjectorPlugin,
      betterMonadicForPlugin
    )
  )

lazy val loadbalancer = (project in file("code/loadbalancer"))
  .settings(
    name                              := "loadbalancer",
    description                       := "A simple load balancer using http4s",
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sEmberServer,
      http4sEmberClient,
      http4sDsl,
      pureConfig,
      pureConfigGeneric,
      logback,
      jcdp,
      kindProjectorPlugin,
      betterMonadicForPlugin
    ) ++ Seq(
      munit,
      munitCE3
    ).map(_ % Test),
    assembly / assemblyMergeStrategy  := {
      case "module-info.class" => MergeStrategy.discard
      case x                   => (assembly / assemblyMergeStrategy).value.apply(x)
    },
    assembly / mainClass              := Some("rockthejvm.loadbalancer.Main"),
    assembly / assemblyJarName        := "lb.jar"
  )

lazy val http4ssecurity = (project in file("code/http4ssecurity"))
  .settings(
    name                              := "http4ssecurity",
    description                       := "Scala Server Security with Http4s: CORS and CSRF",
    Compile / console / scalacOptions := ScalacOptions.consoleScalacOptions,
    libraryDependencies ++= Seq(
      http4sEmberServer,
      http4sDsl,
      slf4jApi,
      slf4jSimple,
      kindProjectorPlugin,
      betterMonadicForPlugin
    )
  )
