import java.util.zip.ZipFile

buildscript {
	repositories { mavenCentral() }
	// svnkit-dav의 클래스 한 곳을 빌드 때 고치는 데 쓴다(아래 patchedDavPathUtil)
	dependencies { classpath("org.ow2.asm:asm:9.7") }
}

plugins {
	kotlin("jvm") version "2.4.10"
	kotlin("plugin.spring") version "2.4.10"
	kotlin("plugin.jpa") version "2.4.10"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.yona"
version = "2.0.0-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// ---------------------------------------------------------------------------------------------
// Subversion WebDAV(D-26): SVNKit의 DAV 서블릿(`DAVServlet`)은 Maven Central에 war로만 배포된다(v1.6은 SCM-Manager가 jar로 다시
// 포장한 `sonia.svnkit:svnkit-dav`를 썼다). war의 `WEB-INF/classes`만 꺼내 jar로 묶어 클래스패스에 올린다.
// ---------------------------------------------------------------------------------------------

val svnDavWar by configurations.creating

/**
 * 한글이나 공백이 든 프로젝트 이름의 저장소에서 커밋이 늘 실패하는 svnkit-dav 1.10.11의 불일치를 고친다(A-148).
 * 요청 본문이나 `Destination` 헤더의 주소를 `java.net.URI.getPath()`로 디코딩한 뒤, 퍼센트 인코딩한 저장소 컨텍스트
 * (`DAVResourceURI.getContext()`, `DAVRepositoryManager.getResourceContext()`)와 비교하는 곳이 세 군데 있다.
 * ASCII 이름에서는 같지만 `한글 프로젝트`는 `%ED%95%9C...`와 `한글 프로젝트`가 달라 `Unusable URI`나 `wrong context`로 거부된다.
 * 컨텍스트도 디코딩해서 비교하도록 호출 한 줄(`SVNEncodingUtil.uriDecode`)을 끼워 넣는다. 응답 주소를 만드는 곳은 인코딩한 컨텍스트가 맞으므로 건드리지 않는다.
 */
val davContextPatches = listOf(
	// 클래스, 고칠 메서드, 결과를 디코딩할 호출의 소유 클래스 끝, 호출 이름
	listOf("org/tmatesoft/svn/core/internal/server/dav/DAVPathUtil", "simpleParseURI", "/DAVResourceURI", "getContext"),
	listOf("org/tmatesoft/svn/core/internal/server/dav/handlers/DAVMergeHandler", "execute", "/DAVRepositoryManager", "getResourceContext"),
	listOf("org/tmatesoft/svn/core/internal/server/dav/handlers/DAVCopyMoveHandler", "execute", "/DAVRepositoryManager", "getResourceContext")
)

val patchedDavClasses by tasks.registering {
	val outputDir = layout.buildDirectory.dir("svn-dav/patched")
	inputs.files(svnDavWar)
	outputs.dir(outputDir)
	doLast {
		ZipFile(svnDavWar.singleFile).use { zip ->
			for ((className, methodName, ownerSuffix, callName) in davContextPatches) {
				val reader = org.objectweb.asm.ClassReader(zip.getInputStream(zip.getEntry("WEB-INF/classes/$className.class")).readBytes())
				val writer = org.objectweb.asm.ClassWriter(reader, 0)
				var patched = 0
				reader.accept(object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, writer) {
					override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): org.objectweb.asm.MethodVisitor {
						val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
						if (name != methodName) return visitor
						return object : org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9, visitor) {
							override fun visitMethodInsn(opcode: Int, owner: String, mname: String, mdesc: String, isInterface: Boolean) {
								super.visitMethodInsn(opcode, owner, mname, mdesc, isInterface)
								if (owner.endsWith(ownerSuffix) && mname == callName) {
									patched++
									super.visitMethodInsn(
										org.objectweb.asm.Opcodes.INVOKESTATIC, "org/tmatesoft/svn/core/internal/util/SVNEncodingUtil",
										"uriDecode", "(Ljava/lang/String;)Ljava/lang/String;", false
									)
								}
							}
						}
					}
				}, 0)
				check(patched == 1) { "$className.$methodName 에서 $callName 호출을 하나 찾아야 한다(찾은 수: $patched). svnkit-dav 버전이 바뀌었는지 확인한다." }
				outputDir.get().file("$className.class").asFile.also { it.parentFile.mkdirs() }.writeBytes(writer.toByteArray())
			}
		}
	}
}

val svnDavClasses by tasks.registering(Jar::class) {
	description = "svnkit-dav war의 WEB-INF/classes를 jar로 묶는다. 컨텍스트 비교를 고친 클래스 세 개를 대신 넣는다."
	archiveFileName = "svn-dav-classes.jar".replace("svn-dav", "svnkit-dav")
	destinationDirectory = layout.buildDirectory.dir("svn-dav")
	dependsOn(patchedDavClasses)
	from(layout.buildDirectory.dir("svn-dav/patched"))
	from(zipTree(svnDavWar.elements.map { it.single().asFile })) {
		include("WEB-INF/classes/**")
		davContextPatches.forEach { exclude("${it[0].removePrefix("org/tmatesoft/svn/core/internal/server/dav/")}.class".let { n -> "**/internal/server/dav/$n" }) }
		eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(2).toTypedArray()) }
		includeEmptyDirs = false
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	// 받은 메일 수신(IMAP IDLE, UID 조회)에 쓰는 IMAP 전용 클래스. 스타터는 실행 시점에만 포함한다
	implementation("org.eclipse.angus:angus-mail")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// Git 저장소 처리(D-15). v1.6의 4.5.0을 Java 21에서 쓸 수 있는 7.x로 올렸다.
	implementation("org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.http.server:7.8.0.202609011348-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.archive:7.8.0.202609011348-r")

	// 서버 측 마크다운 렌더링: marked.js 실행(아래 Nashorn), 결과 정화(OWASP)와 HTML 후처리(jsoup)
	// v1.6이 쓰던 marked.js를 그대로 실행하는 JavaScript 엔진(JDK 15에서 빠진 Nashorn의 독립 배포판, 결정 D-17 되돌림 A-151)
	implementation("org.openjdk.nashorn:nashorn-core:15.4")
	// v1.6이 변환 실패 때 원문을 감싸는 데 쓴 `StringEscapeUtils.escapeHtml`(commons-lang 2.x)
	implementation("commons-lang:commons-lang:2.6")
	implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20190610.1")
	implementation("org.jsoup:jsoup:1.8.3")
	// Subversion WebDAV(D-26): DAV 서블릿은 javax 서블릿 API를 쓰므로 Jakarta 서블릿 위에 변환 어댑터를 둔다.
	svnDavWar("org.tmatesoft.svnkit:svnkit-dav:1.10.11@war")
	implementation(files(svnDavClasses))
	implementation("javax.servlet:javax.servlet-api:3.1.0")
	// Subversion 저장소 처리(D-13). v1.6의 1.9.3을 현재 배포판인 1.10.11로 올렸다.
	implementation("org.tmatesoft.svnkit:svnkit:1.10.11")
	// 받은 HTML 메일의 본문 정리(v1.6 `CreationViaEmail.postprocessForHTML`이 쓰던 라이브러리)
	implementation("com.googlecode.htmlcompressor:htmlcompressor:1.5.2")
	implementation("org.apache.tika:tika-core:3.3.3")
	// v1.6과 같은 엑셀(xls) 라이브러리. 이슈 목록 내보내기에 쓴다.
	implementation("net.sourceforge.jexcelapi:jxl:2.6.12")
	implementation("com.googlecode.juniversalchardet:juniversalchardet:1.0.3")
	implementation("com.github.ben-manes.caffeine:caffeine")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

	testImplementation("com.unboundid:unboundid-ldapsdk:7.0.3")
	// 받은 메일(IMAP) 테스트용 인메모리 메일 서버
	testImplementation("com.icegreen:greenmail:2.1.3")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// 렌더링된 HTML의 구조를 DOM으로 검증하는 데 쓴다(운영 코드는 쓰지 않는다).

	// v1.6이 패스워드 해시에 쓰던 Shiro와 결과가 같은지 교차 검증하기 위한 참조 구현이다(운영 코드는 쓰지 않는다).
	testImplementation("org.apache.shiro:shiro-core:1.13.0")

	// PostgreSQL과 MariaDB는 Testcontainers로 검증한다. 컨테이너는 재사용(reuse)해 실행 사이에도 유지한다.
	testImplementation("org.testcontainers:testcontainers:2.0.5")
	testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
	testImplementation("org.testcontainers:testcontainers-mariadb:2.0.5")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 통합 테스트는 설정이 다른 스프링 컨텍스트를 테스트 클래스마다 따로 캐시한다. 컨텍스트가 30개를 넘으면서 기본 힙(512MB)이 모자라
	// 컨텍스트 로딩이 연쇄 실패했으므로 힙을 넉넉히 잡는다.
	maxHeapSize = "3g"
	// 통합 테스트가 사용할 DB를 `-Dyona.it.db=h2|postgres|mariadb`로 고른다(기본값 h2).
	systemProperty("yona.it.db", System.getProperty("yona.it.db") ?: "h2")
	testLogging {
		events("failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
	}
}

// ---------------------------------------------------------------------------------------------
// LESS 컴파일: v1.6은 sbt-less가 빌드 시점에 `app/assets/stylesheets/*.less`로 CSS를 만들었다.
// 같은 방식으로 Less 1.7.5(`build-tools/less`)를 실행해 `/assets/stylesheets/*.css`로 제공한다.
// ---------------------------------------------------------------------------------------------

val lessToolDir = layout.projectDirectory.dir("build-tools/less")
val lessSourceDir = layout.projectDirectory.dir("app/assets/stylesheets")
val lessOutputDir = layout.buildDirectory.dir("generated/less")

/** v1.6에서 `_`로 시작하지 않는 최상위 LESS 파일이 CSS 하나로 컴파일된다(`build.sbt`의 includeFilter/excludeFilter). */
val lessEntries = listOf("yobi", "usermenu", "filetype")

/** 하위 폴더의 LESS도 sbt-less가 따로 CSS로 컴파일해 내려 준다(`/assets/stylesheets/less/font/icons.css` 등). 소스 맵의 `sources`는 그 파일이 있는 폴더 기준이다. */
val lessNestedEntries = listOf("less/font/colours", "less/font/fonts", "less/font/icons")

val installLess by tasks.registering(Exec::class) {
	description = "LESS 컴파일러(npm less)를 설치한다."
	workingDir = lessToolDir.asFile
	commandLine("npm", "ci", "--no-audit", "--no-fund")
	inputs.files(lessToolDir.file("package.json"), lessToolDir.file("package-lock.json"))
	outputs.dir(lessToolDir.dir("node_modules"))
}

val compileLess by tasks.registering {
	description = "LESS 소스를 /assets/stylesheets 아래의 CSS로 컴파일한다."
	dependsOn(installLess)
	inputs.dir(lessSourceDir)
	outputs.dir(lessOutputDir)
	doLast {
		val outDir = lessOutputDir.get().dir("static/assets/stylesheets").asFile
		outDir.mkdirs()
		val lessc = lessToolDir.file("node_modules/.bin/lessc").asFile
		(lessEntries + lessNestedEntries).forEach { entry ->
			val name = entry.substringAfterLast('/')
			val sourceDir = File(lessSourceDir.asFile, entry.substringBeforeLast('/', ""))
			val entryOut = File(outDir, entry.substringBeforeLast('/', "")).also { it.mkdirs() }
			// v1.6 sbt-less는 소스 맵(`name.css.map`)을 함께 만들고 CSS 끝에 `/*# sourceMappingURL=name.css.map */`를 붙인다
			val result = ProcessBuilder(
				lessc.absolutePath, "--source-map=${File(entryOut, "$name.css.map").absolutePath}", "--source-map-url=$name.css.map",
				"$name.less", File(entryOut, "$name.css").absolutePath
			)
				.directory(sourceDir)
				.redirectErrorStream(true)
				.start()
			val output = result.inputStream.bufferedReader().readText()
			check(result.waitFor() == 0) { "$name.less 컴파일 실패:\n$output" }
			// v1.6의 소스 맵은 `file`이 출력 파일 이름이고 `version` 바로 뒤에 온다(lessc는 절대 경로를 맨 끝에 쓴다)
			val map = File(entryOut, "$name.css.map")
			val raw = map.readText()
			val fileKey = Regex(",\"file\":\"[^\"]*\"\\}$").find(raw)
			if (fileKey != null) {
				map.writeText(raw.removeRange(fileKey.range.first, fileKey.range.last).replaceFirst("{\"version\":3,", "{\"version\":3,\"file\":\"$name.css\","))
			}
		}
		// v1.6는 LESS 원본도 `/assets/stylesheets/` 아래에서 그대로 내려 준다(소스 맵이 가리킨다)
		lessSourceDir.asFile.walkTopDown().filter { it.isFile && it.extension == "less" }.forEach {
			val target = File(outDir, it.relativeTo(lessSourceDir.asFile).path)
			target.parentFile.mkdirs()
			it.copyTo(target, overwrite = true)
		}
	}
}

tasks.named<ProcessResources>("processResources") {
	from(compileLess)
}

// 실행 중인 버전을 알 수 있게 빌드 정보(`META-INF/build-info.properties`)를 만든다. 업데이트 확인이 현재 버전으로 쓴다.
springBoot {
	buildInfo()
}
