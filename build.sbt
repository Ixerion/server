name := "server"

version := "0.1"

scalaVersion := "2.13.7"

autoCompilerPlugins := true

val http4sVersion = "0.21.22"
val catsVersion = "2.2.0"
val catsEffectVersion = "2.2.0"
val circeVersion = "0.13.0"

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
  "com.evolutiongaming" %% "cats-helper" % "2.2.3",
  "com.evolutiongaming" %% "cats-helper-testkit" % "2.2.3" % Test,
  "org.scalatest" %% "scalatest" % "3.2.10" % Test,
  "org.scalamock" %% "scalamock" % "5.1.0" % Test,
  "com.github.pureconfig" %% "pureconfig" % "0.17.1",
)