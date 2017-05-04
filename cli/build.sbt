
organization := "co.ledger"

name := "ledger-wallet-cli"

version := "1.0"

scalaVersion := "2.12.2"

resolvers += "clojars.org" at "http://clojars.org/repo"

fork in run := true
cancelable in Global := true

libraryDependencies += "com.lihaoyi" % "ammonite" % "0.8.4" % "test" cross CrossVersion.full

initialCommands in (Test, console) := """ammonite.Main().run()"""

libraryDependencies ++= Seq(
    "io.github.shogowada" %% "scala-json-rpc" % "0.9.0",
    "io.github.shogowada" %% "scala-json-rpc-upickle-json-serializer" % "0.9.0",
    "org.java-websocket" % "java-websocket" % "1.3.3"
)

libraryDependencies ++= Seq(
  "org.backuity.clist" %% "clist-core"   % "3.2.2",
  "org.backuity.clist" %% "clist-macros" % "3.2.2" % "provided"
)