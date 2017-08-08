
lazy val commonSettings = Seq(
  version := "0.1-SNAPSHOT",
  organization := "com.example",
  scalaVersion := "2.10.1",
  test in assembly := {}
)

lazy val protocol = (project in file("protocol"))

lazy val cli = (project in file("cli")).dependsOn(protocol).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("co.ledger.wallet.cli.LedgerWalletCli")
  )

lazy val daemon = (project in file("daemon")).dependsOn(protocol)
  .settings(commonSettings: _*)
  .settings(
    mainClass in assembly := Some("co.ledger.wallet.daemon.LedgerWalletDaemon")
  )


cancelable in Global := true