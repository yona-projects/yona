import sbt._
import Keys._
import PlayProject._
import com.github.play2war.plugin._

object ApplicationBuild extends Build {

    val appName         = "nforge4"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
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
      "commons-lang" % "commons-lang" % "2.6",
      "org.apache.tika" % "tika-core" % "1.2",
      "commons-io" % "commons-io" % "2.4"
    )

    val projectSettings = Play2WarPlugin.play2WarSettings ++ Seq(
      // Add your own project settings here
      resolvers += "jgit-repository" at "http://download.eclipse.org/jgit/maven",
      resolvers += "svnkit-repository" at "http://maven.tmatesoft.com/content/repositories/releases/",
      resolvers += "scm-manager release repository" at "http://maven.scm-manager.org/nexus/content/groups/public",
      templatesImport += "models.enumeration._",
      lessEntryPoints <<= baseDirectory(_ / "app" / "assets" / "stylesheets" ** "*.less"),
      Play2WarKeys.servletVersion := "3.0"
      // Or Play2WarKeys.servletVersion := "2.5"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = JAVA).settings(projectSettings: _*)
}
