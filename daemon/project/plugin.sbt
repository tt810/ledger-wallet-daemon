resolvers += Resolver.url("21re-bintray-plugins", url("http://dl.bintray.com/21re/public"))(Resolver.ivyStylePatterns)

addSbtPlugin("de.21re" % "sbt-swagger-plugin" % "0.1-4")