
lazy val protocol = (project in file("protocol"))

lazy val cli = (project in file("cli")).dependsOn(protocol)

lazy val daemon = (project in file("daemon")).dependsOn(protocol)
