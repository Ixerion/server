name := "server"

version := "0.1"

scalaVersion := "2.13.7"

val http4sVersion = "0.21.22"
val catsVersion = "2.2.0"
val catsEffectVersion = "2.2.0"

libraryDependencies ++=Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
)