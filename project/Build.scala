import sbt._
import Keys._
import play.Project.javaCore
import play.Project.javaJdbc
import play.Project.javaEbean
import play.Project.templatesImport
import play.Project.lessEntryPoints

object ApplicationBuild extends Build {

  val appName         = "yobi"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    javaCore,
    javaJdbc,
    javaEbean,
      // Add your project dependencies here,
      // Core Library
      "org.eclipse.jgit" % "org.eclipse.jgit" % "3.1.0.201310021548-r",
      // Smart HTTP Servlet
      "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "3.1.0.201310021548-r",
      // svnkit
      "sonia.svnkit" % "svnkit" % "1.7.10-scm3",
      // svnkit-dav
      "sonia.svnkit" % "svnkit-dav" % "1.7.10-scm3",
      // javahl
      "sonia.svnkit" % "svnkit-javahl16" % "1.7.10-scm3",
      "net.sourceforge.jexcelapi" % "jxl" % "2.6.10",
    // shiro
      "org.apache.shiro" % "shiro-core" % "1.2.1",
      // commons-codec
      "commons-codec" % "commons-codec" % "1.2",
      // apache-mails
      "org.apache.commons" % "commons-email" % "1.2",
      "info.schleichardt" %% "play-2-mailplugin" % "0.9.1",
      "commons-lang" % "commons-lang" % "2.6",
      "org.apache.tika" % "tika-core" % "1.2",
      "commons-io" % "commons-io" % "2.4",
      "com.github.julienrf" %% "play-jsmessages" % "1.4.1",
      "commons-collections" % "commons-collections" % "3.2.1",
      "org.jsoup" % "jsoup" % "1.7.2",
      "com.googlecode.juniversalchardet" % "juniversalchardet" % "1.0.3"
  )

    val projectSettings = Seq(
      // Add your own project settings here
      resolvers += "jgit-repository" at "http://download.eclipse.org/jgit/maven",
      resolvers += "scm-manager release repository" at "http://maven.scm-manager.org/nexus/content/groups/public",
      resolvers += "tmatesoft release repository" at "http://maven.tmatesoft.com/content/repositories/releases",
      resolvers += "julienrf.github.com" at "http://julienrf.github.com/repo/",
      templatesImport += "models.enumeration._",
      lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" ** "yobi.less"),
        //      jacoco.settings:_*,
      javaOptions in test ++= Seq("-Xmx512m", "-XX:MaxPermSize=512m", "-Dfile.encoding=UTF-8"),
      javacOptions ++= Seq("-Xlint:all", "-Xlint:-path"),
      scalacOptions ++= Seq("-feature")
    )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    projectSettings: _*
  )

}
