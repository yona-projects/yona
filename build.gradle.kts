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

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
		lessEntries.forEach { name ->
			val result = ProcessBuilder(lessc.absolutePath, "$name.less", File(outDir, "$name.css").absolutePath)
				.directory(lessSourceDir.asFile)
				.redirectErrorStream(true)
				.start()
			val output = result.inputStream.bufferedReader().readText()
			check(result.waitFor() == 0) { "$name.less 컴파일 실패:\n$output" }
		}
	}
}

tasks.named<ProcessResources>("processResources") {
	from(compileLess)
}
