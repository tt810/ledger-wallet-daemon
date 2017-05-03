organization := "co.ledger"

name := "ledger-wallet-daemon"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "io.github.shogowada" %% "scala-json-rpc" % "0.9.0",
  "org.java-websocket" % "java-websocket" % "1.3.3"
)

libraryDependencies ++= Seq(
  "org.backuity.clist" %% "clist-core"   % "3.2.2",
  "org.backuity.clist" %% "clist-macros" % "3.2.2" % "provided"
)