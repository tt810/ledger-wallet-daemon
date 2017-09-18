import slick.codegen.SourceCodeGenerator

slickCodegenSettings
slickCodegenDatabaseUrl := "jdbc:h2:./test;MODE=mysql;DB_CLOSE_DELAY=-1"
slickCodegenDatabaseUser := "SA"
slickCodegenDatabasePassword := ""
slickCodegenDriver := slick.driver.H2Driver
slickCodegenJdbcDriver := "org.h2.Driver"
slickCodegenOutputPackage := "co.ledger.wallet.daemon.slick"
slickCodegenExcludedTables := Seq("schema_version")

lazy val versions = new {
  val slick = "3.2.0"
}

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % versions.slick,
  "com.typesafe.slick" %% "slick-hikaricp" % versions.slick,
  "com.zaxxer" % "HikariCP" % "2.6.1"
)

sourceGenerators in Compile += slickCodegen.taskValue