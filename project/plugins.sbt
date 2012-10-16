// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers ++= Seq(
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
//    "Play2war plugins release" at "http://repository-play-war.forge.cloudbees.com/release/"
)

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.0.4")

// addSbtPlugin("com.github.play2war" % "play2-war-plugin" % "0.6")
