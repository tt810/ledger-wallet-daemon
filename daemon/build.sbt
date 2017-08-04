organization := "co.ledger"

name := "ledger-wallet-daemon"

version := "1.0"

scalaVersion := "2.12.2"

fork in run := true
cancelable in Global := true

libraryDependencies ++= Seq(
  "org.backuity.clist" %% "clist-core" % "3.2.2",
  "org.backuity.clist" %% "clist-macros" % "3.2.2" % "provided",
  "io.github.shogowada" %% "scala-json-rpc" % "0.9.0",
  "io.github.shogowada" %% "scala-json-rpc-upickle-json-serializer" % "0.9.0",
  "com.typesafe.slick" %% "slick" % "3.2.0",
  "org.postgresql" % "postgresql" % "9.3-1100-jdbc4",
  "org.xerial" % "sqlite-jdbc" % "3.7.15-M1",
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.0",
  "org.java-websocket" % "Java-WebSocket" % "1.3.4",
  "io.github.shogowada" %% "scala-json-rpc-upickle-json-serializer" % "0.9.0"
)