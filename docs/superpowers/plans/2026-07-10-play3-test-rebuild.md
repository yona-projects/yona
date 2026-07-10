# Play 3 Test Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the unusable Play 2-era test suite and create a Java 21-compatible unit, MariaDB-backed functional, and staged-browser smoke test system that protects Yona's core workflows.

**Architecture:** Rebuild the `test/` tree from zero with JUnit Jupiter and a single reusable Play/MariaDB support layer. Use real HTTP sessions and native Git in functional tests, then run a small Playwright suite against the packaged `stage` application. The browser setup owns temporary Docker, data, repository, and process resources and always tears them down.

**Tech Stack:** Java 21, Play Framework 3.0.11, sbt 1.12.13, JUnit Jupiter 5.14 through sbt-jupiter-interface 0.16.0, AssertJ 3.27.7, Mockito 5.23.0, Testcontainers 1.21.4, MariaDB 12.3.2, Node 20, Playwright 1.61.1.

## Global Constraints

- Delete all existing source files under `test/`; do not port Play 2 `FakeApplication`, `Context`, Fest, or PowerMock code.
- Remove `conf/test-data.yml` after confirming only legacy tests reference it; new tests create their own data.
- Keep `conf/initial-data.yml` because the application bootstraps its administrative defaults from it.
- Functional tests use MariaDB only; H2 is not an acceptance database for this port.
- Run functional tests serially because Yona retains static Play/Ebean compatibility state.
- Test data, repository directories, and `YONA_DATA` must be unique per test lifecycle and deleted during teardown.
- Browser tests run serially and target a staged executable, never `sbt run`.
- Email tests use `smtp.mock = true`; no credentials or remote SMTP endpoint are accepted in test configuration.
- MCP tests use a generated user token and test only the existing read-only API surface.

## Recommended Execution Sequence

1. Complete Tasks 1 and 2 before modifying a production regression.
2. Complete Task 5 steps 1-3 to make the staged browser runtime available, but defer Task 5 step 5 until the stabilization plan's browser regressions are fixed.
3. Complete the companion stabilization plan Tasks 1-4.
4. Complete Tasks 3 and 4, then Task 5's smoke verification and Task 6's CI gate.

---

### Task 1: Replace the legacy test runtime with a clean JUnit Jupiter baseline

**Files:**
- Modify: `project/plugins.sbt:1-14`
- Modify: `build.sbt:6-70`
- Delete: `test/**`
- Delete: `conf/test-data.yml`
- Create: `test/unit/framework/JupiterSmokeTest.java`
- Create: `test/resources/application.test.conf`

**Interfaces:**
- Produces: `sbt test` discovers `org.junit.jupiter.api.Test` classes through `jupiter-interface`.
- Produces: a test-only configuration with a non-production Play secret, mock SMTP, disabled update check, and no developer-specific database URL.

- [ ] **Step 1: Record the old-suite failure before removal**

Run:

```bash
sbt test
```

Expected: test compilation fails on removed Play 2 classes such as `play.test.FakeApplication`, `play.test.FakeRequest`, `play.mvc.Http.Context`, and Fest assertions. Save this output in the implementation notes, not in source control.

- [ ] **Step 2: Remove old test sources, fixtures, and test-only libraries**

Delete every file under `test/` and delete `conf/test-data.yml`. Remove these test dependencies from `build.sbt`:

```scala
"org.mockito" % "mockito-all" % "1.10.19" % "test",
"org.powermock" % "powermock-module-junit4" % "1.6.4" % "test",
"org.powermock" % "powermock-api-mockito" % "1.6.4" % "test",
```

- [ ] **Step 3: Add the JUnit Jupiter and integration-test dependencies**

Add the SBT plugin in `project/plugins.sbt`:

```scala
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.16.0")
```

Add version constants and dependencies in `build.sbt`:

```scala
val assertjVersion = "3.27.7"
val mockitoVersion = "5.23.0"
val testcontainersVersion = "1.21.4"

libraryDependencies ++= Seq(
  "com.github.sbt.junit" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
  "org.assertj" % "assertj-core" % assertjVersion % Test,
  "org.mockito" % "mockito-core" % mockitoVersion % Test,
  "org.testcontainers" % "junit-jupiter" % testcontainersVersion % Test,
  "org.testcontainers" % "mariadb" % testcontainersVersion % Test
)

Test / parallelExecution := false
```

The selected Jupiter 5.14 runner is stable with the existing Play 3/JUnit-oriented test APIs and runs on Java 21.

- [ ] **Step 4: Create a deterministic test configuration and smoke class**

Create `test/resources/application.test.conf`:

```hocon
application.secret = "yona-test-secret-not-for-production-0123456789"
play.http.secret.key = ${application.secret}
smtp.mock = true
application.update.check.use = false
notification.bymail.enabled = false
play.evolutions.db.default.autoApply = true
mcp.enabled = true
```

Create `test/unit/framework/JupiterSmokeTest.java`:

```java
package unit.framework;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JupiterSmokeTest {
    @Test
    void discoversAndRunsJupiterTests() {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("21");
    }
}
```

- [ ] **Step 5: Verify the new test runner**

Run:

```bash
sbt clean test
```

Expected: `unit.framework.JupiterSmokeTest` passes and no source imports a Play 2 test helper, Fest, or PowerMock.

- [ ] **Step 6: Commit the clean baseline**

```bash
git add -A test conf/test-data.yml build.sbt project/plugins.sbt
git commit -m "test: replace legacy Play 2 test suite baseline"
```

---

### Task 2: Build reusable MariaDB, Play server, HTTP session, and Git support

**Files:**
- Create: `test/support/YonaMariaDb.java`
- Create: `test/support/YonaApplicationFactory.java`
- Create: `test/support/YonaFunctionalTest.java`
- Create: `test/support/HttpSessionClient.java`
- Create: `test/support/ScopedSystemProperties.java`
- Create: `test/support/TestUser.java`
- Create: `test/support/TestProject.java`
- Create: `test/support/TestDataFactory.java`
- Create: `test/support/TemporaryRepository.java`
- Create: `test/functional/runtime/FunctionalHarnessTest.java`

**Interfaces:**
- `YonaMariaDb.jdbcUrl()`, `username()`, and `password()` return the running Testcontainers MariaDB connection details.
- `YonaApplicationFactory.start(YonaMariaDb, Path)` returns a started `RunningYona` containing `URI baseUri()`, `Application application()`, and `close()`.
- `RunningYona.gitRemote(String owner, String project, String loginId, String password)` returns a percent-encoded Smart HTTP URI.
- `HttpSessionClient` persists cookies and exposes `get`, `postForm`, `postFormWithCsrf`, `putWithCsrf`, `deleteWithCsrf`, and `postJsonWithCsrf`.
- `TestUser` is `record TestUser(long id, String loginId, String password, String email, HttpSessionClient client)` and `TestProject` is `record TestProject(long id, String owner, String name)`.
- `TestDataFactory.signupAndLogin(String)` and `createProject(HttpSessionClient, String)` use public HTTP routes rather than direct Ebean writes for user-facing setup.
- `TestDataFactory.issueSnapshot(HttpSessionClient, TestProject, long)` and `detectIssueChange(HttpSessionClient, TestProject, long, IssueSnapshot)` model the existing issue-change polling request; `IssueChange.asSnapshot()` supplies the next acknowledged baseline.
- `TemporaryRepository` executes native `git` with `init`, `commit`, `push`, `cloneFrom`, `read`, and `close` operations.

- [ ] **Step 1: Write a failing functional harness test**

```java
package functional.runtime;

import org.junit.jupiter.api.Test;
import support.YonaFunctionalTest;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionalHarnessTest extends YonaFunctionalTest {
    @Test
    void startsPlayAgainstDisposableMariaDb() {
        assertThat(client.get("/").statusCode()).isEqualTo(200);
        assertThat(client.get("/").body()).contains("Yona");
    }
}
```

- [ ] **Step 2: Implement the MariaDB lifecycle extension**

Use one `MariaDBContainer` for the serial test JVM and close it after the suite:

```java
public final class YonaMariaDb {
    private static final MariaDBContainer<?> CONTAINER = new MariaDBContainer<>("mariadb:12.3.2")
            .withDatabaseName("yona")
            .withUsername("yona")
            .withPassword("yonadan");

    public static synchronized void start() {
        if (!CONTAINER.isRunning()) CONTAINER.start();
    }

    public static String jdbcUrl() { return CONTAINER.getJdbcUrl() + "?useServerPrepStmts=true"; }
    public static String username() { return CONTAINER.getUsername(); }
    public static String password() { return CONTAINER.getPassword(); }
    public static synchronized void stop() { CONTAINER.stop(); }
}
```

- [ ] **Step 3: Implement a Yona-aware Play application factory**

Build through `YonaApplicationLoader` so the same production module bindings and runtime initialization are exercised:

```java
ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
ApplicationLoader.Context context = new ApplicationLoader.Context(
        new Environment(dataDir.toFile(), classLoader, Mode.TEST), configuration);
Application app = new YonaApplicationLoader().builder(context).build();
Helpers.start(app);
TestServer server = new TestServer(0, app);
Helpers.start(server);
```

Before constructing the loader, write the merged test configuration to `<temp-data>/conf/application.conf`, set `config.file`, `logger.file`, `yona.data`, and `application.home` through `ScopedSystemProperties`, and restore their prior values in `RunningYona.close()`. The generated map must override `db.default.driver`, `db.default.url`, `db.default.user`, `db.default.password`, `application.secret`, `play.http.secret.key`, `smtp.mock`, `mcp.enabled`, and `application.update.check.use`. `RunningYona.close()` must stop the server before the application and recursively delete its temporary data directory.

- [ ] **Step 4: Implement session-aware HTTP and native Git helpers**

`HttpSessionClient` uses `java.net.http.HttpClient` with a `CookieManager`, reads the CSRF token from either a hidden input or `csrf-token` meta tag, and sends both `Csrf-Token` and `X-CSRF-Token` headers for JavaScript-style requests. `TemporaryRepository` uses `ProcessBuilder` with `GIT_TERMINAL_PROMPT=0`, captures stdout/stderr, and throws an assertion-friendly exception on unexpected exit status.

- [ ] **Step 5: Verify the harness against real infrastructure**

Run:

```bash
docker info
sbt "testOnly functional.runtime.FunctionalHarnessTest"
```

Expected: Testcontainers starts `mariadb:12.3.2`, Play applies evolutions, the root page returns 200, and no container/process remains after the JVM exits.

- [ ] **Step 6: Commit the support layer**

```bash
git add test/support test/functional/runtime/FunctionalHarnessTest.java
git commit -m "test: add MariaDB-backed Play functional harness"
```

---

### Task 3: Cover authentication and the primary project collaboration workflow

**Files:**
- Create: `test/functional/auth/SignupLoginFunctionalTest.java`
- Create: `test/functional/project/ProjectIssueWorkflowFunctionalTest.java`
- Create: `test/functional/project/UserIssuesFunctionalTest.java`

**Interfaces:**
- Consumes: `TestDataFactory.signupAndLogin`, `createProject`, and `HttpSessionClient` CSRF form helpers.
- Produces: executable coverage for signup, login, project creation, issue creation, comment creation, milestone creation, board posting, and the My Issues filter.

- [ ] **Step 1: Write the end-to-end HTTP workflow test**

```java
@Test
void userCanCreateProjectIssueCommentMilestoneAndBoardPost() {
    TestUser user = data.signupAndLogin("workflow");
    TestProject project = data.createProject(user.client(), "workflow-project");

    long issueNumber = data.createIssue(user.client(), project, "Workflow issue", "Issue body");
    data.addIssueComment(user.client(), project, issueNumber, "Workflow comment");
    data.createMilestone(user.client(), project, "Workflow milestone");
    data.createBoardPost(user.client(), project, "Workflow post", "Board body");

    assertThat(user.client().get("/user/issues").body()).contains("Workflow issue");
}
```

- [ ] **Step 2: Add authentication and CSRF assertions**

```java
@Test
void signupThenFormLoginCreatesAuthenticatedSession() {
    TestUser user = data.signup("auth");
    HttpSessionClient session = data.login(user.loginId(), user.password());
    assertThat(session.get("/").body()).contains("/" + user.loginId());
}

@Test
void protectedFormPostWithoutCsrfIsRejected() {
    assertThat(anonymousClient.postForm("/users/signup", Map.of("loginId", "missing-token")).statusCode())
            .isEqualTo(403);
}
```

- [ ] **Step 3: Run the functional workflow tests**

Run:

```bash
sbt "testOnly functional.auth.SignupLoginFunctionalTest functional.project.ProjectIssueWorkflowFunctionalTest functional.project.UserIssuesFunctionalTest"
```

Expected: all workflow requests use a real session and CSRF token, and the no-token request is forbidden.

- [ ] **Step 4: Commit the core workflow coverage**

```bash
git add test/functional/auth test/functional/project
git commit -m "test: cover core Yona collaboration workflow"
```

---

### Task 4: Cover notifications, mock email, MCP, favorites, mentions, and Git contracts

**Files:**
- Create: `test/unit/mcp/McpJsonTest.java`
- Create: `test/functional/mcp/McpFunctionalTest.java`
- Create: `test/functional/notification/IssueChangeNotificationFunctionalTest.java`
- Create: `test/functional/mail/PasswordResetFunctionalTest.java`
- Create: `test/functional/project/FavoriteProjectFunctionalTest.java`
- Create: `test/functional/markdown/MentionRenderingFunctionalTest.java`
- Create: `test/functional/git/GitSmartHttpFunctionalTest.java`

**Interfaces:**
- Consumes: stabilization-plan endpoints and renderer behavior.
- Produces: durable regression tests for all previously verified MCP, mail, notification, Git, favorite, and mention behaviors.

- [ ] **Step 1: Add pure MCP JSON unit tests**

```java
package unit.mcp;

import mcp.McpJson;
import org.junit.jupiter.api.Test;
import play.libs.Json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpJsonTest {
    @Test
    void clampsOptionalIntegersToSchemaBounds() {
        assertThat(McpJson.optionalInt(Json.parse("{\"limit\":99}"), "limit", 20, 1, 50)).isEqualTo(50);
    }

    @Test
    void rejectsBlankRequiredText() {
        assertThatThrownBy(() -> McpJson.requiredText(Json.parse("{\"owner\":\" \"}"), "owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing required parameter: owner");
    }
}
```

- [ ] **Step 2: Add MCP HTTP contract tests**

Use a generated per-user token and JSON-RPC requests to assert all three failure boundaries and the read-only discovery surface:

```java
assertThat(anonymousClient.postJson("/mcp", initializePayload).statusCode()).isEqualTo(401);
assertThat(tokenClient.withHeader("Origin", "https://untrusted.example").postJson("/mcp", initializePayload).statusCode()).isEqualTo(403);
assertThat(tokenClient.postJson("/mcp", initializePayload).body()).contains("\"protocolVersion\"");
assertThat(tokenClient.postJson("/mcp", toolsListPayload).body()).contains("yona_list_projects");
```

Then cover a readable project/issue/post/milestone and an unreadable private project using `tools/call` and `resources/read` requests.

- [ ] **Step 3: Add notification and mock-mail behavior tests**

```java
@Test
void oneRemoteCommentIsReportedOnceThenAcknowledgedByUpdatedBaseline() {
    IssueSnapshot before = data.issueSnapshot(owner.client(), project, issueNumber);
    data.addIssueComment(commenter.client(), project, issueNumber, "remote comment");
    IssueChange first = data.detectIssueChange(owner.client(), project, issueNumber, before);
    IssueChange second = data.detectIssueChange(owner.client(), project, issueNumber, first.asSnapshot());

    assertThat(first.commentAuthorName()).isEqualTo(commenter.name());
    assertThat(second.hasChange()).isFalse();
}

@Test
void passwordResetCompletesInMockMailMode() {
    HttpResponse<String> response = user.client().postFormWithCsrf("/lostPassword", Map.of(
            "loginId", user.loginId(), "emailAddress", user.email()));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("Mail has been sent");
}
```

Assert the mock path by verifying the reset hash/state rather than trying to connect to a mail server.

- [ ] **Step 4: Add the stabilization regression tests from the companion plan**

Implement the favorite, mention, and Git test classes exactly as specified in `2026-07-10-play3-stabilization.md`. They must remain in these packages so focused `testOnly` commands are stable.

- [ ] **Step 5: Run the full unit and functional suite**

Run:

```bash
sbt test
```

Expected: unit and functional tests pass against Testcontainers MariaDB; no test class imports `org.junit.Test`, `org.fest`, `play.test.FakeApplication`, or PowerMock.

- [ ] **Step 6: Commit the contract coverage**

```bash
git add test/unit test/functional
git commit -m "test: cover Play 3 functional and MCP contracts"
```

---

### Task 5: Add staged Playwright smoke coverage

**Files:**
- Create: `.nvmrc`
- Modify: `.gitignore`
- Create: `e2e/package.json`
- Create: `e2e/package-lock.json`
- Create: `e2e/playwright.config.ts`
- Create: `e2e/global-setup.ts`
- Create: `e2e/global-teardown.ts`
- Create: `e2e/support/runtime.ts`
- Create: `e2e/support/yona.ts`
- Create: `e2e/support/git.ts`
- Create: `e2e/specs/auth-project-issue.spec.ts`
- Create: `e2e/specs/notifications.spec.ts`
- Create: `e2e/specs/git-code.spec.ts`

**Interfaces:**
- `global-setup.ts` writes `{ baseUrl, dataDir, containerName, appPid }` to `e2e/.runtime.json`.
- `runtime.ts` exports `runtime()` and `gotoYona(page, path)` for specs.
- `yona.ts` exports `signup`, `login`, `createProject`, `createIssue`, and `createComment` helpers using user-visible forms.
- `git.ts` exports `pushReadme(remoteUrl)` using native Git.

- [ ] **Step 1: Pin the Node and Playwright runtime**

Create `.nvmrc` with:

```text
20
```

Create `e2e/package.json`:

```json
{
  "name": "yona-e2e",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "playwright test",
    "test:stage": "playwright test specs/staged-startup.spec.ts"
  },
  "devDependencies": {
    "@playwright/test": "1.61.1"
  }
}
```

Add these generated directories to `.gitignore`:

```gitignore
e2e/node_modules/
e2e/playwright-report/
e2e/test-results/
e2e/.runtime.json
```

- [ ] **Step 2: Implement deterministic staged runtime setup and teardown**

`global-setup.ts` must:

1. Allocate unused loopback ports for MariaDB and Yona.
2. Start `mariadb:12.3.2` with database/user/password `yona`/`yona`/`yonadan` and wait for TCP readiness.
3. Copy `conf/application.conf.default` and `conf/application-logger.xml.default` into a temporary `YONA_DATA/conf` directory.
4. Append database, mock-mail, MCP, secret, and dynamic `application.port` overrides to the copied configuration.
5. Spawn `target/universal/stage/bin/yona -Dhttp.port=<port>` with `YONA_DATA=<temp-dir>`.
6. Poll `http://127.0.0.1:<port>/` until it returns 200, then write `e2e/.runtime.json`.

The teardown reads that file, sends `SIGTERM` then `SIGKILL` only if needed, runs `docker rm -f <container-name>`, and deletes the temporary data directory. Both setup and teardown must throw when a child-process command exits unexpectedly.

- [ ] **Step 3: Configure serial, diagnostic browser runs**

Create `e2e/playwright.config.ts`:

```ts
import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./specs",
  globalSetup: "./global-setup.ts",
  globalTeardown: "./global-teardown.ts",
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  reporter: [["list"], ["html", { open: "never" }]],
  use: { browserName: "chromium", trace: "retain-on-failure", screenshot: "only-on-failure" }
});
```

`runtime.ts` must read the generated runtime file at call time, so dynamically allocated ports are available to every test.

- [ ] **Step 4: Implement the harness-owned smoke journeys**

Create the three non-regression specs owned by this plan:

```text
auth-project-issue.spec.ts  signup -> login -> project -> issue
notifications.spec.ts       remote comment -> one reload notification only
git-code.spec.ts            native Git push -> code browser shows README
```

The companion stabilization plan owns `staged-startup.spec.ts`, `favorites.spec.ts`, and `comment-editor.spec.ts`. Every spec registers `page.on("pageerror", ...)` and fails with the captured errors after its interaction. Each creates unique login/project names based on `test.info().testId` so retries cannot reuse state.

- [ ] **Step 5: Run the staged browser suite**

Run:

```bash
sbt stage
npm --prefix e2e install
npx --prefix e2e playwright install chromium
npm --prefix e2e run test
```

Expected: the staged process, MariaDB container, and all five critical browser journeys pass. `e2e/.runtime.json` and generated report directories remain untracked.

- [ ] **Step 6: Commit the E2E system**

```bash
git add .nvmrc .gitignore e2e
git commit -m "test: add staged Playwright smoke coverage"
```

---

### Task 6: Modernize the existing CI job and make verification repeatable

**Files:**
- Modify: `.travis.yml`
- Modify: `README.md` if it contains build/test instructions
- Modify: `docs/technical/play-framework-porting.md`

**Interfaces:**
- Consumes: `sbt test`, `sbt stage`, and `npm --prefix e2e run test`.
- Produces: a Java 21/Docker/Node 20 verification job that exercises the same gate locally and in CI.

- [ ] **Step 1: Replace the Activator/Java 8 Travis job**

Use a Java 21 and Docker-enabled Travis definition:

```yaml
language: java
dist: jammy
jdk: openjdk21
services:
  - docker
cache:
  directories:
    - $HOME/.cache/coursier
    - e2e/node_modules
before_install:
  - nvm install 20
  - npm --prefix e2e ci
  - npx --prefix e2e playwright install --with-deps chromium
script:
  - sbt clean compile test stage
  - npm --prefix e2e run test
```

- [ ] **Step 2: Document the local preflight and release gate**

Add this exact local sequence to the Play 3 porting guide:

```bash
docker info
sbt clean compile test stage
npm --prefix e2e ci
npx --prefix e2e playwright install chromium
npm --prefix e2e run test
```

State that Docker, Java 21, Node 20, native Git, and network access to fetch the MariaDB/Chromium images are required.

- [ ] **Step 3: Verify the complete gate and cleanup**

Run the documented sequence, then run:

```bash
git diff --check
git status --short
```

Expected: test commands succeed, no temporary service remains listening, and only intentionally modified source/documentation files appear before commit.

- [ ] **Step 4: Commit CI modernization**

```bash
git add .travis.yml README.md docs/technical/play-framework-porting.md
git commit -m "ci: verify Yona with Java 21 and browser smoke tests"
```
