
enablePlugins(JavaServerAppPackaging)

organization := "co.ledger"

name := "ledger-wallet-daemon"

version := "1.0"

scalaVersion := "2.12.2"


concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
parallelExecution in Test := false
parallelExecution in IntegrationTest := false
testForkedParallel in Test := false
testForkedParallel in IntegrationTest := false

val versions = new {
  val finatra = "2.12.0"
  val guice = "4.0"
  val logback = "1.1.7"
  val slick = "3.2.1"
}

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % versions.slick,
  "org.postgresql" % "postgresql" % "9.3-1100-jdbc4",
  "org.xerial" % "sqlite-jdbc" % "3.7.15-M1",
  "com.h2database" % "h2" % "1.3.166",
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "com.typesafe.slick" %% "slick-hikaricp" % versions.slick,
  "com.twitter" %% "finatra-http" % versions.finatra,
  "org.bitcoinj" % "bitcoinj-core" % "0.14.4",
  "io.github.andrebeat" %% "scala-pool" % "0.4.0",

  "com.twitter" %% "finatra-http" % versions.finatra % "test",
  "com.twitter" %% "finatra-jackson" % versions.finatra % "test",
  "com.twitter" %% "inject-server" % versions.finatra % "test",
  "com.twitter" %% "inject-app" % versions.finatra % "test",
  "com.twitter" %% "inject-core" % versions.finatra % "test",
  "com.twitter" %% "inject-modules" % versions.finatra % "test",
  "com.google.inject.extensions" % "guice-testlib" % versions.guice % "test",

  "com.twitter" %% "finatra-http" % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "finatra-jackson" % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-server" % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-app" % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-core" % versions.finatra % "test" classifier "tests",
  "com.twitter" %% "inject-modules" % versions.finatra % "test" classifier "tests",

  "org.mockito" % "mockito-core" % "1.9.5" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "org.scalatest" %% "scalatest" %  "3.0.0" % "test",
  "org.specs2" %% "specs2-mock" % "2.4.17" % "test"
)
//libraryDependencies += "com.jakehschwartz" %% "finatra-swagger" % versions.finatra
