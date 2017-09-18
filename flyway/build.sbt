
flywayLocations := Seq("classpath:db/migration")
//flywayUrl := "jdbc:mysql://localhost:3306/"
flywayUrl := "jdbc:h2:./test;MODE=mysql;DB_CLOSE_DELAY=-1"
flywayUser := "SA"
flywayPassword := ""
//flywaySchemas := Seq("daemon")

libraryDependencies ++= Seq(
  "org.flywaydb" % "flyway-core" % "4.2.0"
)