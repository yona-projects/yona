import com.typesafe.config._
import java.nio.file.Paths

name := """yobi"""

val CONFIG_DIRNAME = "conf"
val VERSION_CONF = "version.conf"
val pathToVersionConfig = Paths.get(basePath, CONFIG_DIRNAME, VERSION_CONF)
val versionConf = ConfigFactory.parseFile(pathToVersionConfig.toFile()).resolve()

version := versionConf.getString("app.version")

libraryDependencies ++= Seq(
  // Add your project dependencies here,
  javaCore,
  javaJdbc,
  javaEbean,
  // Add your project dependencies here,
  // Core Library
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.5.3.201412180710-r",
  // Smart HTTP Servlet
  "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "3.5.3.201412180710-r",
  // Symlink support for Java7
  "org.eclipse.jgit" % "org.eclipse.jgit.java7" % "3.5.3.201412180710-r",
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
  "org.julienrf" %% "play-jsmessages" % "1.6.2",
  "commons-collections" % "commons-collections" % "3.2.1",
  "org.jsoup" % "jsoup" % "1.7.2",
  "com.googlecode.juniversalchardet" % "juniversalchardet" % "1.0.3",
  "org.mockito" % "mockito-all" % "1.9.0" % "test",
  "com.github.zafarkhaja" % "java-semver" % "0.7.2"
)

val projectSettings = Seq(
  // Add your own project settings here
  resolvers += "jgit-repository" at "http://download.eclipse.org/jgit/maven",
  resolvers += "scm-manager release repository" at "http://maven.scm-manager.org/nexus/content/groups/public",
  resolvers += "tmatesoft release repository" at "http://maven.tmatesoft.com/content/repositories/releases",
  resolvers += "julienrf.github.com" at "http://julienrf.github.com/repo/",
  TwirlKeys.templateImports in Compile += "models.enumeration._",
  TwirlKeys.templateImports in Compile += "scala.collection.JavaConversions._",
  TwirlKeys.templateImports in Compile += "play.core.j.PlayMagicForJava._",
  TwirlKeys.templateImports in Compile += "java.lang._",
  TwirlKeys.templateImports in Compile += "java.util._",
  includeFilter in (Assets, LessKeys.less) := "*.less",
  excludeFilter in (Assets, LessKeys.less) := "_*.less",
  javaOptions in test ++= Seq("-Xmx2g", "-Xms1g", "-XX:MaxPermSize=1g", "-Dfile.encoding=UTF-8"),
  javacOptions ++= Seq("-Xlint:all", "-Xlint:-path"),
  scalacOptions ++= Seq("-feature")
)

scalaVersion := "2.10.4"

lazy val yobi = (project in file("."))
      .enablePlugins(PlayScala)
      .enablePlugins(SbtWeb)
      .enablePlugins(SbtTwirl)
      .settings(projectSettings: _*)
      .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
