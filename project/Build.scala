import sbt._
import Keys._
import play.Project.javaCore
import play.Project.javaJdbc
import play.Project.javaEbean
import play.Project.templatesImport
import play.Project.lessEntryPoints

object ApplicationBuild extends Build {

  val appName         = "nforge4"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    javaCore,
    javaJdbc,
    javaEbean,
      // Add your project dependencies here,
      "mysql" % "mysql-connector-java" % "5.1.18",
      "postgresql" % "postgresql" % "9.1-901.jdbc4",
      // Core Library
      "org.eclipse.jgit" % "org.eclipse.jgit" % "2.0.0.201206130900-r",
      // Smart HTTP Servlet
      "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "2.0.0.201206130900-r",
      // svnkit
      "sonia.svnkit" % "svnkit" % "1.7.5-1",
      // svnkit-dav
      "sonia.svnkit" % "svnkit-dav" % "1.7.5-1",
      // javahl
      "org.tmatesoft.svnkit" % "svnkit-javahl" % "1.3.5",
      "net.sourceforge.jexcelapi" % "jxl" % "2.6.10",
    // shiro
      "org.apache.shiro" % "shiro-core" % "1.2.1",
      // commons-codec
      "commons-codec" % "commons-codec" % "1.2",
      // apache-mails
      "org.apache.commons" % "commons-email" % "1.2",
      "info.schleichardt" %% "play-2-mailplugin" % "0.8",
      "commons-lang" % "commons-lang" % "2.6",
      "org.apache.tika" % "tika-core" % "1.2",
      "commons-io" % "commons-io" % "2.4",
      "com.github.julienrf" %% "play-jsmessages" % "1.4.1"
  )

    val projectSettings = Seq(
      // Add your own project settings here
      resolvers += "jgit-repository" at "http://download.eclipse.org/jgit/maven",
      resolvers += "svnkit-repository" at "http://maven.tmatesoft.com/content/repositories/releases/",
      resolvers += "scm-manager release repository" at "http://maven.scm-manager.org/nexus/content/groups/public",
      resolvers += "julienrf.github.com" at "http://julienrf.github.com/repo/",
      templatesImport += "models.enumeration._",
      lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" ** "nforge.less"),
        //      jacoco.settings:_*,
      javaOptions in test ++= Seq("-Xmx512m", "-XX:MaxPermSize=512m"),
      javacOptions ++= Seq("-Xlint:all", "-Xlint:-path"),
      scalacOptions ++= Seq("-feature")
    )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    projectSettings: _*
  )

}
