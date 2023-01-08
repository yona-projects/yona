import com.typesafe.config._
import java.nio.file.Paths

name := """yona"""

version := "1.16.0"

libraryDependencies ++= Seq(
  // Add your project dependencies here,
  javaCore,
  javaJdbc,
  javaEbean,
  javaWs,
  cache,
  // PlayAuthenticat for social login
  // https://github.com/joscha/play-authenticate
  "com.feth" %% "play-authenticate" % "0.6.9",
  // OWASP Java HTML Sanitizer
  // https://www.owasp.org/index.php/OWASP_Java_HTML_Sanitizer_Project
  "com.googlecode.owasp-java-html-sanitizer" % "owasp-java-html-sanitizer" % "20190610.1",
  // Add your project dependencies here,
  "com.h2database" % "h2" % "1.3.176",
  // JDBC driver for mariadb
  "org.mariadb.jdbc" % "mariadb-java-client" % "1.5.5",
  // Core Library
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.0.201609210915-r",
  // Smart HTTP Servlet
  "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "4.5.0.201609210915-r",
  // JGit Large File Storage
  "org.eclipse.jgit" % "org.eclipse.jgit.lfs" % "4.5.0.201609210915-r",
  // JGit Archive Formats
  "org.eclipse.jgit" % "org.eclipse.jgit.archive" % "4.5.0.201609210915-r",
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
  "org.apache.commons" % "commons-email" % "1.2",
  "info.schleichardt" %% "play-2-mailplugin" % "0.9.1",
  "commons-lang" % "commons-lang" % "2.6",
  "org.apache.tika" % "tika-core" % "1.2",
  "commons-io" % "commons-io" % "2.4",
  "org.julienrf" %% "play-jsmessages" % "1.6.2",
  "commons-collections" % "commons-collections" % "3.2.1",
  "org.jsoup" % "jsoup" % "1.8.3",
  "com.googlecode.juniversalchardet" % "juniversalchardet" % "1.0.3",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "org.powermock" % "powermock-module-junit4" % "1.6.4" % "test",
  "org.powermock" % "powermock-api-mockito" % "1.6.4" % "test",
  "com.github.zafarkhaja" % "java-semver" % "0.7.2",
  "com.google.guava" % "guava" % "19.0",
  "com.googlecode.htmlcompressor" % "htmlcompressor" % "1.4",
  "org.springframework" % "spring-jdbc" % "4.1.5.RELEASE",
  "javax.xml.bind" % "jaxb-api" % "2.3.0",
  "com.github.mfornos" % "humanize-slim" % "1.2.2",
  "org.jsoup" % "jsoup" % "1.8.3"
)

val projectSettings = Seq(
  // Add your own project settings here
  resolvers += "maven central" at "https://mvnrepository.com",
  resolvers += "maven central2" at "https://repo1.maven.org/maven2/",
  resolvers += "maven central3" at "https://repo.maven.apache.org/maven2",
  resolvers += "jgit-repository" at "https://repo.eclipse.org/content/groups/releases/",
  resolvers += "java-semVer" at "https://oss.sonatype.org/content/repositories/snapshots/",
  resolvers += "scm-manager release repository" at "https://maven.scm-manager.org/nexus/content/repositories/releases/",
  resolvers += "tmatesoft release repository" at "https://maven.tmatesoft.com/content/repositories/releases",
  resolvers += "tmatesoft snapshot repository" at "https://maven.tmatesoft.com/content/repositories/snapshots",
  resolvers += "julienrf.github.com" at "http://julienrf.github.com/repo/",
  resolvers += "opencast-public" at "http://nexus.opencast.org/nexus/content/repositories/public",
  resolvers += "jfrog" at "http://repo.jfrog.org/artifactory/libs-releases/",
  TwirlKeys.templateImports in Compile += "models.enumeration._",
  TwirlKeys.templateImports in Compile += "scala.collection.JavaConversions._",
  TwirlKeys.templateImports in Compile += "play.core.j.PlayMagicForJava._",
  TwirlKeys.templateImports in Compile += "java.lang._",
  TwirlKeys.templateImports in Compile += "java.util._",
  includeFilter in (Assets, LessKeys.less) := "*.less",
  excludeFilter in (Assets, LessKeys.less) := "_*.less",
  javaOptions in test ++= Seq("-Xmx2g", "-Xms1g", "-Dfile.encoding=UTF-8"),
  scalacOptions ++= Seq("-feature")
)

publishArtifact in packageDoc := false

publishArtifact in packageSrc := false

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version)

buildInfoPackage := "yona"

mappings in Universal :=
    (mappings in Universal).value.filterNot { case (_, file) => file.startsWith("conf/") }

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
    |addJava "-DapplyEvolutions.default=true"
    |""".stripMargin

NativePackagerKeys.batScriptExtraDefines += """
    | set "APP_CLASSPATH=%APP_LIB_DIR%\*"
    | if NOT "%YONA_DATA%" == "" set "YONA_OPTS=-DapplyEvolutions.default=true -Duser.dir=%YONA_HOME% -Dyona.data=%YONA_DATA% -Dconfig.file=%YONA_DATA%\conf\application.conf -Dlogger.file=%YONA_DATA%\conf\application-logger.xml"
    |""".stripMargin

lazy val yobi = (project in file("."))
      .enablePlugins(PlayScala)
      .enablePlugins(SbtWeb)
      .enablePlugins(SbtTwirl)
      .settings(projectSettings: _*)
      .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
      .settings(de.johoop.findbugs4sbt.FindBugs.findbugsSettings: _*)
      .settings(findbugsExcludeFilters :=  Some(
          <FindBugsFilter>
            <!-- Exclude classes generated by PlayFramework. See docs/examples
                 at http://findbugs.sourceforge.net/manual/filter.html for the
                 filtering rules. -->
            <Match>
              <Class name="~views\.html\..*"/>
            </Match>
            <Match>
              <Class name="~Routes.*"/>
            </Match>
            <Match>
              <Class name="~controllers\.routes.*"/>
            </Match>
          </FindBugsFilter>
        )
      )


fork in run := true
