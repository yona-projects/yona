import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "nforge4"
    val appVersion      = "1.0-SNAPSHOT"

    resolvers += (
      "jgit-repository" at "http://download.eclipse.org/jgit/maven"
    )

    val appDependencies = Seq(
      // Add your project dependencies here,
      "mysql" % "mysql-connector-java" % "5.1.18",
      // Core Library
      "org.eclipse.jgit" % "org.eclipse.jgit" % "2.0.0.201206130900-r",
      // Smart HTTP Servlet
      "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "2.0.0.201206130900-r"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = JAVA).settings(
      // Add your own project settings here      
    )

}
