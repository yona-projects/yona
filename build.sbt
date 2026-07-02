import com.typesafe.config._
import java.nio.file.Paths

name := """yona"""

version := "1.16.0"

ThisBuild / scalaVersion := "2.13.18"

routesGenerator := InjectedRoutesGenerator

val ebeanRuntimeVersion = "14.3.0"
val jgitVersion = "7.7.0.202606012155-r"

libraryDependencies ++= Seq(
  // Add your project dependencies here,
  javaCore,
  javaJdbc,
  javaWs,
  caffeine,
  evolutions,
  guice,
  // OWASP Java HTML Sanitizer
  // https://www.owasp.org/index.php/OWASP_Java_HTML_Sanitizer_Project
  "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer" % "20190610.1",
  "org.commonmark" % "commonmark" % "0.27.0",
  "org.commonmark" % "commonmark-ext-autolink" % "0.27.0",
  "org.commonmark" % "commonmark-ext-gfm-strikethrough" % "0.27.0",
  "org.commonmark" % "commonmark-ext-gfm-tables" % "0.27.0",
  // Add your project dependencies here,
  "com.h2database" % "h2" % "1.3.176",
  // JDBC driver for mariadb
  "org.mariadb.jdbc" % "mariadb-java-client" % "1.5.5",
  // Core Library
  "org.eclipse.jgit" % "org.eclipse.jgit" % jgitVersion,
  // Smart HTTP Servlet
  "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % jgitVersion,
  // JGit Large File Storage
  "org.eclipse.jgit" % "org.eclipse.jgit.lfs" % jgitVersion,
  // JGit Archive Formats
  "org.eclipse.jgit" % "org.eclipse.jgit.archive" % jgitVersion,
  // svnkit
  "org.tmatesoft.svnkit" % "svnkit" % "1.9.3",
  // svnkit-dav
  "sonia.svnkit" % "svnkit-dav" % "1.8.15-scm1",
  // javahl
  "net.sourceforge.jexcelapi" % "jxl" % "2.6.10",
// shiro
  "org.apache.shiro" % "shiro-core" % "1.2.1",
  // commons-codec
  "commons-codec" % "commons-codec" % "1.2",
  // apache-mails
  "org.apache.commons" % "commons-email" % "1.5",
  "com.sun.mail" % "javax.mail" % "1.6.2",
  "commons-lang" % "commons-lang" % "2.6",
  "org.apache.commons" % "commons-lang3" % "3.18.0",
  "org.apache.tika" % "tika-core" % "1.2",
  "commons-io" % "commons-io" % "2.4",
  "org.julienrf" %% "play-jsmessages" % "7.0.0",
  "commons-collections" % "commons-collections" % "3.2.1",
  "org.jsoup" % "jsoup" % "1.8.3",
  "com.googlecode.juniversalchardet" % "juniversalchardet" % "1.0.3",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "org.powermock" % "powermock-module-junit4" % "1.6.4" % "test",
  "org.powermock" % "powermock-api-mockito" % "1.6.4" % "test",
  "com.github.zafarkhaja" % "java-semver" % "0.7.2",
  "com.google.guava" % "guava" % "19.0",
  "com.googlecode.htmlcompressor" % "htmlcompressor" % "1.4",
  "org.yaml" % "snakeyaml" % "1.17",
  "org.springframework" % "spring-jdbc" % "4.1.5.RELEASE",
  "javax.servlet" % "javax.servlet-api" % "4.0.1",
  "javax.xml.bind" % "jaxb-api" % "2.3.1",
  "jakarta.persistence" % "jakarta.persistence-api" % "3.1.0",
  "com.google.code.findbugs" % "jsr305" % "3.0.2",
  "com.github.mfornos" % "humanize-slim" % "1.2.2",
  "org.jsoup" % "jsoup" % "1.8.3"
)

val projectSettings = Seq(
  // Add your own project settings here
  resolvers += "maven central" at "https://repo1.maven.org/maven2/",
  resolvers += "jgit-repository" at "https://repo.eclipse.org/content/groups/releases/",
  resolvers += "java-semVer" at "https://oss.sonatype.org/content/repositories/snapshots/",
  resolvers += "scm-manager release repository" at "https://packages.scm-manager.org/repository/releases/",
  resolvers += "tmatesoft release repository" at "https://maven.tmatesoft.com/content/repositories/releases",
  resolvers += "tmatesoft snapshot repository" at "https://maven.tmatesoft.com/content/repositories/snapshots",
  Compile / TwirlKeys.templateImports += "models.enumeration._",
  Compile / TwirlKeys.templateImports += "play.core.j.PlayMagicForJava._",
  Compile / TwirlKeys.templateImports += "java.lang._",
  Compile / TwirlKeys.templateImports += "java.util._",
  Compile / TwirlKeys.templateImports += "utils.TwirlCompat._",
  Assets / LessKeys.less / includeFilter := "*.less",
  Assets / LessKeys.less / excludeFilter := "_*.less",
  javacOptions ++= Seq("--release", "21"),
  Test / javaOptions ++= Seq("-Xmx2g", "-Xms1g", "-Dfile.encoding=UTF-8"),
  scalacOptions ++= Seq("-feature")
)

excludeDependencies += ExclusionRule(organization = "javax.servlet", name = "servlet-api")

dependencyOverrides ++= Seq(
  "io.ebean" % "ebean" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-api" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-core" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-agent" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-ddl-generator" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-jackson-mapper" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-platform-all" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-platform-h2" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-platform-mariadb" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-platform-mysql" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-platform-postgres" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-platform-sqlserver" % ebeanRuntimeVersion,
  "io.ebean" % "ebean-querybean" % ebeanRuntimeVersion
)

Compile / doc / sources := Seq.empty

Compile / packageDoc / publishArtifact := false

Compile / packageSrc / publishArtifact := false

Compile / playEbeanModels := Seq("models.*")

buildInfoKeys := Seq[BuildInfoKey](name, version)

buildInfoPackage := "yona"

Universal / mappings :=
    (Universal / mappings).value.filterNot { case (_, file) => file.startsWith("conf/") }

Universal / javaOptions += "-J--add-exports=java.base/sun.security.x509=ALL-UNNAMED"

NativePackagerKeys.bashScriptExtraDefines += """# Added by build.sbt
    |[ -n "$YONA_HOME" ] && addJava "-Duser.dir=$YONA_HOME"
    |[ -z "$YONA_HOME" ] && YONA_HOME=$(cd "$(realpath "$(dirname "$(realpath "$0")")")/.."; pwd -P)
    |addJava "-Dyobi.home=$YONA_HOME"
    |
    |[ -z "$YONA_DATA" ] && YONA_DATA=$(cd "$(realpath "$(dirname "$(realpath "$0")")")/.."; pwd -P)
    |addJava "-Dyona.data=$YONA_DATA"
    |addJava "-Dapplication.home=$YONA_DATA"
    |
    |yobi_config_file="$YONA_DATA"/conf/application.conf
    |yobi_log_config_file="$YONA_DATA"/conf/application-logger.xml
    |[ -f "$yobi_config_file" ] && addJava "-Dconfig.file=$yobi_config_file"
    |[ -f "$yobi_log_config_file" ] && addJava "-Dlogger.file=$yobi_log_config_file"
    |
    |addJava "-Dplay.evolutions.db.default.autoApply=true"
    |""".stripMargin

NativePackagerKeys.batScriptExtraDefines += """
    | set "APP_CLASSPATH=%APP_LIB_DIR%\*"
    | if NOT "%YONA_DATA%" == "" set "YONA_OPTS=-Dplay.evolutions.db.default.autoApply=true -Duser.dir=%YONA_HOME% -Dyona.data=%YONA_DATA% -Dconfig.file=%YONA_DATA%\conf\application.conf -Dlogger.file=%YONA_DATA%\conf\application-logger.xml"
    |""".stripMargin

lazy val yobi = (project in file("."))
      .enablePlugins(PlayScala)
      .enablePlugins(SbtWeb)
      .enablePlugins(PlayJava, PlayEbean, BuildInfoPlugin)
      .settings(projectSettings: _*)


run / fork := true
