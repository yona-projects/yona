import java.io.File
import java.util.concurrent.TimeUnit

plugins {
	kotlin("jvm") version "2.4.10"
	kotlin("plugin.spring") version "2.4.10"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.4.10"
	jacoco
}

group = "com.github.yonaprojects"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven {
		url = uri("https://packages.scm-manager.org/repository/releases/")
	}
	// spring-security-saml2-service-provider가 의존하는 OpenSAML은 Maven Central에 미배포되어
	// (Shibboleth 프로젝트가 자체 저장소에서만 배포) 이 저장소가 필요하다.
	maven {
		url = uri("https://build.shibboleth.net/nexus/content/repositories/releases/")
	}
}

// io.spring.dependency-management가 자동으로 가져오는 건 Spring Boot의 BOM뿐이라, Spring AI의
// 버전을 관리하려면 그 BOM을 명시적으로 import해야 한다.
dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:2.0.1")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	// 구조화 JSON 로깅. Spring Boot BOM이 버전을 관리하지 않는 서드파티 라이브러리라 명시적으로
	// 버전을 고정한다.
	implementation("net.logstash.logback:logstash-logback-encoder:9.0")
	// 분산 트레이싱. Spring Boot 4.x는 트레이싱 자동구성을 spring-boot-starter-actuator에서 분리해
	// 이 스타터로 모듈화했다 — micrometer-tracing-bridge-otel/opentelemetry-exporter-otlp만
	// 추가하면 의존성은 갖춰지지만 자동구성 클래스 자체가 없어 Tracer 빈이 생성되지 않는다.
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
	// Swagger/OpenAPI UI 노출 — 기존 @RestController를 런타임에 자동 스캔해 /swagger-ui.html에
	// 대화형 API 문서를 제공한다. springdoc 3.x가 Spring Boot 4.x/Spring Framework 7.x 계열용
	// 메이저 버전이라 이걸 쓴다(2.x는 Spring Boot 3.x용).
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	// SAML2 SP 연동. spring-boot-starter-security의 BOM이 버전을 관리하므로 별도 버전 고정 불필요.
	implementation("org.springframework.security:spring-security-saml2-service-provider")
	// 로컬 로그인 2FA(P3-43) 중 WebAuthn(보안 키/생체인증) 지원. Spring Security 7.1.1이 자체
	// 내장(내부적으로 WebAuthn4J 사용) — 서드파티 불필요, 위 SAML2/OAuth2 Authorization Server와
	// 동일하게 BOM이 버전을 관리해 버전 고정 없이 추가.
	implementation("org.springframework.security:spring-security-webauthn")
	// 2FA 중 TOTP(RFC 6238) 지원. Spring Security엔 내장 기능이 없어 별도 라이브러리 필요 —
	// QR코드 PNG 생성까지 한 번에 해결되는 dev.samstevens.totp 채택(이 프로젝트엔 QR/zxing류
	// 의존성이 전혀 없었음). Spring Boot BOM이 버전을 관리하지 않는 서드파티라 명시적으로 고정.
	implementation("dev.samstevens.totp:totp:1.7.1")
	// yona 자신이 OAuth2 인가 서버(Authorization Server)를 자체 운영하기 위한 라이브러리
	// (Spring Security 7.0부터 본체에 병합돼 spring-boot-starter-security의 BOM이 버전을 관리).
	// PKCE(S256 강제 기본값)/RFC7591 Dynamic Client Registration을 기본 제공하고, RFC8707
	// (Resource Indicators) 오디언스 검증만 이 프로젝트가 직접 구현한다.
	implementation("org.springframework.security:spring-security-oauth2-authorization-server")
	// MCP 프로토콜(Streamable HTTP 전송, 도구 등록)을 처음부터 구현하지 않고 재사용한다.
	// spring-ai-bom(위 dependencyManagement)이 버전을 관리한다.
	implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")
	// IMAP IDLE 명령/UID 조회에 필요한 IMAPFolder/IMAPStore. spring-boot-starter-mail은 angus-mail을
	// runtimeOnly로만 끌어와 그 구현 클래스가 컴파일 시점엔 보이지 않으므로 명시적으로 추가한다.
	implementation("org.eclipse.angus:angus-mail")
	implementation("com.googlecode.htmlcompressor:htmlcompressor:1.5.2")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
	runtimeOnly("org.postgresql:postgresql")
	// MariaDB/PostgreSQL 외 지원 대상 DB(MySQL/SQL Server/CUBRID) 드라이버.
	runtimeOnly("com.mysql:mysql-connector-j:9.3.0")
	runtimeOnly("com.microsoft.sqlserver:mssql-jdbc:12.10.0.jre11")
	runtimeOnly("org.cubrid:cubrid-jdbc:11.3.2.0053")
	// yona가 기본값으로 제공하던 "설치 없이 바로 써보기" 임베디드 DB 대응(h2 프로파일).
	// 도커/별도 서버 설치 없이 파일 기반으로 즉시 실행 가능 — testRuntimeOnly가 아니라
	// runtimeOnly로 선언해야 테스트 실행 시에도(Gradle이 testRuntimeOnly가 runtimeOnly를
	// extend) AbstractIntegrationTest의 -Dyona.it.db=h2 옵션에서 드라이버를 찾을 수 있다.
	runtimeOnly("com.h2database:h2:2.4.240")
	// CUBRIDDialect는 hibernate-core가 아니라 hibernate-community-dialects에 있다(공식 유지보수
	// 대상은 아니지만 현재 Hibernate 7.x용으로 갱신돼 있음, org.hibernate.community.dialect.CUBRIDDialect).
	implementation("org.hibernate.orm:hibernate-community-dialects")

	// JGit
	implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.http.server:7.7.1.202607240634-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.lfs:7.7.1.202607240634-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.lfs.server:7.7.1.202607240634-r")

	// 윈도우 SSH 폴백(시스템 OpenSSH의 AuthorizedKeysCommand 훅을 쓸 수 없는 환경)을 위한 JVM
	// 내장 SSH 서버. 별도 포트(기본 2222)에서 이 애플리케이션 프로세스가 직접 SshServer를 띄운다
	// — 시스템 sshd/포트 22와는 무관.
	implementation("org.apache.sshd:sshd-core:2.15.0")

	// GPG 커밋 서명 실제 암호학적 검증(단순 "서명 존재" 확인이 아니라 BouncyCastle(bcpg/bcprov)로
	// 서명을 공개키에 대해 실제로 검증한다 — GpgSignatureVerifier.kt). org.eclipse.jgit.gpg.bc의
	// BouncyCastleGpgSignatureVerifier는 Repository/GpgConfig 기반 로컬 GPG 키링(~/.gnupg)
	// 조회에 결합돼 있어(자체 사용자 GPG 키를 DB에 등록하는 이 앱의 멀티테넌트 모델과 안 맞음)
	// 채택하지 않았다 — bcpg/bcprov API로 (1) 등록된 공개키 목록에서 서명자 키를 직접 찾고
	// (2) PGPSignature.verify()로 검증하는 흐름을 직접 구현했다.
	implementation("org.bouncycastle:bcpg-jdk18on:1.82")
	implementation("org.bouncycastle:bcprov-jdk18on:1.82")

	// SVNKit
	implementation("org.tmatesoft.svnkit:svnkit:1.10.11")
	implementation("sonia.svnkit:svnkit-dav:1.10.10-scm2-jakarta")

	// Mercurial 지원. search5/hg4j가 Maven Central에 발행돼(io.github.search5.hg4j:hg4j) 일반
	// 좌표 의존성으로 가져온다.
	implementation("io.github.search5.hg4j:hg4j:1.0.0")

	// juniversalchardet
	implementation("com.github.albfernandez:juniversalchardet:2.5.0")

	// Commonmark
	implementation("org.commonmark:commonmark:0.24.0")
	implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
	implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
	implementation("org.commonmark:commonmark-ext-autolink:0.24.0")
	implementation("org.commonmark:commonmark-ext-task-list-items:0.24.0")

	// JSoup
	implementation("org.jsoup:jsoup:1.21.1")

	// OWASP HTML Sanitizer (allowlist 기반 XSS 방지, yona Markdown.java와 동등 정책)
	implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260313.1")

	// Apache Commons Lang3
	implementation("org.apache.commons:commons-lang3:3.20.0")

	// JExcelAPI (Legacy Yona Excel support)
	implementation("net.sourceforge.jexcelapi:jxl:2.6.12")

	// Apache Tika — 확장자가 없는 해시 파일명(SHA-256 원문 저장 방식) 그대로 JDK
	// Files.probeContentType()에 넘기면 사실상 항상 감지 실패해 모든 첨부가
	// application/octet-stream으로 저장된다.
	implementation("org.apache.tika:tika-core:4.0.0")

	// renderedMarkdown 캐시는 Caffeine 등으로 대체하지 않고 Guava Cache/CacheBuilder를 그대로 쓴다.
	implementation("com.google.guava:guava:33.4.8-jre")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Kotest & MockK
	testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
	testImplementation("io.kotest:kotest-assertions-core:5.9.1")
	testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
	testImplementation("io.mockk:mockk:1.14.11")

	// Testcontainers — MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID 5개 DB를 전부 도커 컨테이너
	// 기준으로 검증하기 위해 버전을 1.21.4로 통일하고 mssqlserver/mysql 모듈을 추가했다.
	testImplementation("org.testcontainers:testcontainers:1.21.4")
	testImplementation("org.testcontainers:junit-jupiter:1.21.4")
	testImplementation("org.testcontainers:postgresql:1.21.4")
	testImplementation("org.testcontainers:mariadb:1.21.4")
	testImplementation("org.testcontainers:mysql:1.21.4")
	testImplementation("org.testcontainers:mssqlserver:1.21.4")
	// CUBRID 공식 Testcontainers 모듈(testcontainers.com Official Module, CUBRID사 직접 관리).
	testImplementation("org.cubrid:testcontainers-cubrid:0.1.0")

	// WebAuthn 등록/인증 세리모니는 실제 브라우저 없이는 curl로 재현 불가능하다 — WebAuthn4J가
	// 제공하는 가상 인증기(virtual authenticator) 테스트 유틸리티로 실제 attestation/assertion을
	// 프로그래밍적으로 만들어 서버 측 검증 로직(서명 검증 포함)까지 실제로 태우는 통합테스트에 쓴다.
	// spring-security-webauthn이 끌어오는 webauthn4j-core와 버전을 맞춰야 한다.
	testImplementation("com.webauthn4j:webauthn4j-test:0.31.9.RELEASE")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

// Docker가 없고 Podman만 있는 환경(예: macOS)에서는 표준 유닉스 소켓 탐색이 실패하므로,
// 명령어 존재 여부를 확인해 Podman의 로컬 API 소켓을 DOCKER_HOST로 지정한다.
fun resolveDockerHost(): String? {
	if (System.getenv("DOCKER_HOST") != null) return null
	fun commandExists(cmd: String) = runCatching {
		ProcessBuilder("which", cmd).start().waitFor() == 0
	}.getOrDefault(false)
	if (commandExists("docker")) return null
	if (!commandExists("podman")) return null
	return runCatching {
		val proc = ProcessBuilder("podman", "machine", "inspect", "--format", "{{.ConnectionInfo.PodmanSocket.Path}}")
			.redirectErrorStream(true).start()
		proc.waitFor(10, TimeUnit.SECONDS)
		val socketPath = proc.inputStream.bufferedReader().readText().trim()
		if (socketPath.isNotBlank() && File(socketPath).exists()) "unix://$socketPath" else null
	}.getOrNull()
}

tasks.withType<Test> {
	useJUnitPlatform()
	// Gradle의 테스트 워커 기본 힙(512m)은, 각각 @DynamicPropertySource로 고유한 프로퍼티를 써서
	// Spring TestContext 캐시가 재사용하지 못하는 별도 ApplicationContext를 만드는
	// @SpringBootTest 스펙들이 많아지면서 전체 스위트(`./gradlew test`, 포크 없이 전부) 실행 시
	// OutOfMemoryError로 이어진다 — 개별/배치 실행에서는 재현되지 않고 전체 스위트 단독 실행에서만
	// 나타난다. gradle.properties의 데몬 힙(2048m)과 동일한 값으로 테스트 워커 힙을 올려 해소한다
	// (운영 코드/성능에는 영향 없음, 테스트 실행 전용 설정).
	maxHeapSize = "2048m"
	systemProperty("spring.profiles.active", "test")
	systemProperty("testcontainers.host", "127.0.0.1")
	systemProperty("api.version", "1.44")
	// AbstractIntegrationTest의 DB 컨테이너 선택 스위치(mariadb|postgres|mysql|mssql|cubrid).
	// -Dyona.it.db=... 로 gradle CLI에 준 값을 포크된 테스트 JVM까지 그대로 전달한다.
	systemProperty("yona.it.db", System.getProperty("yona.it.db", "mariadb"))
	environment("DOCKER_API_VERSION", "1.44")
	environment("TESTCONTAINERS_RYUK_DISABLED", "true")
	environment("TESTCONTAINERS_CONTAINER_STARTUP_TIMEOUT", "120")
	environment("TESTCONTAINERS_HOST_OVERRIDE", "127.0.0.1")
	resolveDockerHost()?.let { environment("DOCKER_HOST", it) }
	finalizedBy(tasks.jacocoTestReport)
}

jacoco {
	toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
		csv.required.set(false)
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)
	violationRules {
		rule {
			// 회귀 감사 백로그(docs/PARITY_BACKLOG.md) 항목을 구현할 때마다
			// 해당 클래스 단위로 커버리지를 개별 확인한다. 전역 최소치는
			// 아직 레거시 코드가 많아 0으로 두고 리포트만 강제 생성한다.
			limit {
				minimum = "0.00".toBigDecimal()
			}
		}
	}
}

