resolvers += "Flyway" at "https://flywaydb.org/repo"

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0")

// Database migration
addSbtPlugin("org.flywaydb" % "flyway-sbt" % "4.2.0")

// Slick code generation
// https://github.com/tototoshi/sbt-slick-codegen
addSbtPlugin("com.github.tototoshi" % "sbt-slick-codegen" % "1.2.0")

// Common library dependencies
libraryDependencies += "com.h2database" % "h2" % "1.4.192"