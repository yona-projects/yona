// Comment to get more information during initialization
logLevel := Level.Warn

// Use the Play sbt plugin for Play projects
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.11")

addSbtPlugin("org.playframework" % "sbt-play-ebean" % "8.5.0")

addSbtPlugin("com.github.sbt" % "sbt-less" % "2.0.1")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")

dependencyOverrides += "org.webjars.npm" % "less" % "4.2.0"

excludeDependencies += ExclusionRule(organization = "org.webjars", name = "less-node")
