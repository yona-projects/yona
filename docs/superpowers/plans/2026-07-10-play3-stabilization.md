# Play 3 Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the packaged Play 3 application start reliably and remove the Git Smart HTTP, favorite, mention, and comment-paste regressions discovered in live verification.

**Architecture:** First establish the new functional/E2E harness from the companion test-rebuild plan. Each regression is then fixed through a failing real-contract test, the smallest production change, and a focused retest. The Git exception remains route-scoped, favorite changes converge on an explicit desired state, and the staged artifact is the final runtime authority.

**Tech Stack:** Java 21, Play Framework 3.0.11, sbt 1.12.13, Ebean 14.3, JGit 7.7, MariaDB 12.3.2, JUnit Jupiter, Testcontainers, Node 20, Playwright 1.61.1.

## Global Constraints

- Complete Tasks 1 and 2 of `2026-07-10-play3-test-rebuild.md` before the functional portions of this plan. Complete that plan's Task 5 steps 1-3 before running this plan's browser portions; run its Task 5 verification only after the fixes below land.
- Use Java 21 for every compile, test, stage, and staged-process run.
- Keep CSRF enabled globally. Only Smart HTTP Git RPC routes may receive `nocsrf`.
- Preserve Basic authentication and Yona project authorization for both Git upload and receive operations.
- Do not retain a source class in the `play` package that shadows a Play framework class.
- Test only against disposable MariaDB data and temporary repository/data directories.
- Use `smtp.mock = true`; automated tests must not contact an external SMTP service.
- Keep MCP read-only and preserve token/origin authorization behavior.

## Recommended Execution Sequence

1. Complete Test Rebuild Tasks 1 and 2 to obtain the JUnit/MariaDB harness.
2. Implement Test Rebuild Task 5 steps 1-3 only, so the staged Playwright runtime exists; write its non-regression specs but defer its full-suite command.
3. Execute Stabilization Tasks 1-4 test-first. The browser specs named in this plan are owned by this plan.
4. Complete Test Rebuild Tasks 3-5 and run their full verification after the fixes pass.
5. Run Stabilization Task 5 and Test Rebuild Task 6 as the release gate.

---

### Task 1: Restore staged application startup

**Files:**
- Modify: `conf/application.conf.default:84-105`
- Modify: `conf/application-logger.xml.default:1-62`
- Modify: `app/modules/YonaRuntime.java:53-67`
- Modify: `build.sbt:151-178`
- Delete: `app/play/Logger.java`
- Create: `e2e/specs/staged-startup.spec.ts`
- Modify: `e2e/package.json`

**Interfaces:**
- Consumes: `e2e/global-setup.ts` from the test-rebuild plan, which exports a running staged base URL through `YONA_E2E_BASE_URL`.
- Produces: a staged application that accepts `application.secret` as the compatibility source for `play.http.secret.key`, and a `test:stage` Playwright command.

- [ ] **Step 1: Write the failing staged-process smoke test**

```ts
import { expect, test } from "@playwright/test";

test("the staged Yona process starts and serves the home page", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator("body")).toContainText("Yona");
});
```

Add the focused command to `e2e/package.json`:

```json
{
  "scripts": {
    "test:stage": "playwright test specs/staged-startup.spec.ts"
  }
}
```

- [ ] **Step 2: Verify the current staged artifact fails**

Run:

```bash
sbt stage
npm --prefix e2e run test:stage
```

Expected: FAIL before the browser navigation completes. The staged log must show the missing Play secret or the `play.Logger.of(Class)` linkage error, not a database connectivity failure.

- [ ] **Step 3: Add the Play 3 secret compatibility mapping without rewriting user configuration**

Immediately after the existing `application.secret` entry in `conf/application.conf.default`, add this ordered mapping for new installations:

```hocon
# Play 3 reads this key. Keep application.secret for existing Yona installations.
play.http.secret.key = ${application.secret}
play.http.secret.key = ${?PLAY_HTTP_SECRET_KEY}
```

Then make `YonaRuntime.loadConfiguration` supply the same fallback for an existing external `application.conf` that contains only `application.secret`:

```java
private static Config withPlaySecretCompatibility(Config config) {
    if (config.hasPath("play.http.secret.key") || !config.hasPath("application.secret")) {
        return config;
    }
    return ConfigFactory.parseMap(Map.of(
            "play.http.secret.key", config.getString("application.secret")))
            .withFallback(config);
}
```

Apply the helper to both return paths:

```java
if (generatedConfiguration == null) {
    return new play.api.Configuration(withPlaySecretCompatibility(initialConfiguration.underlying()));
}
Config merged = generatedConfiguration.underlying().withFallback(initialConfiguration.underlying());
return new play.api.Configuration(withPlaySecretCompatibility(merged));
```

This keeps a configured environment secret as the highest-priority value and does not overwrite a user's ignored `conf/application.conf` file.

Add `import java.util.Map;` to `YonaRuntime.java` with the other `java.util` imports.

- [ ] **Step 4: Remove runtime class shadowing and support old external logger files safely**

Delete `app/play/Logger.java`. Play 3's own `play.Logger` exposes the static logging overloads already used by application sources and also provides the `of(Class<?>)` method required by Play Ebean.

Remove this line from `conf/application-logger.xml.default`:

```xml
<conversionRule conversionWord="coloredLevel" converterClass="play.api.Logger$ColoredLevel" />
```

Replace the console pattern with Logback's built-in converter:

```xml
<pattern>%highlight(%-5level) %logger{15} - %message%n%xException{5}</pattern>
```

Do not modify the ignored user-owned `conf/application-logger.xml`. Instead, update the Unix and Windows native-packager definitions in `build.sbt` so an external logger file containing `play.api.Logger$ColoredLevel` is not passed as `-Dlogger.file`; Play's built-in compatible logging is used for that startup and Yona subsequently creates a fresh compatible default file. A valid customized logger file continues to be passed unchanged.

- [ ] **Step 5: Verify compile, staging, and the staged HTTP process**

Run:

```bash
sbt clean compile
sbt stage
npm --prefix e2e run test:stage
```

Expected: all commands exit 0; the staged process reaches the home page and shuts down during Playwright teardown without a listener left on its dynamic port.

- [ ] **Step 6: Commit the startup repair**

```bash
git add conf/application.conf.default \
  conf/application-logger.xml.default app/modules/YonaRuntime.java build.sbt \
  app/play/Logger.java e2e/specs/staged-startup.spec.ts e2e/package.json
git commit -m "fix: start staged app on Play 3"
```

---

### Task 2: Restore Smart HTTP Git push without weakening other CSRF protection

**Files:**
- Modify: `conf/routes:298-300`
- Create: `test/functional/git/GitSmartHttpFunctionalTest.java`
- Modify: `test/support/TemporaryRepository.java`
- Modify: `test/support/HttpSessionClient.java`

**Interfaces:**
- Consumes: `TemporaryRepository.init(Path)`, `commit(String, String)`, `push(URI)`, and `cloneFrom(URI, Path)` from the test support plan.
- Produces: a route-scoped `nocsrf` Git RPC endpoint; all other POST routes retain the default CSRF filter.

- [ ] **Step 1: Write failing real-client Git tests**

```java
@Test
void authenticatedUserCanPushAndCloneOverSmartHttp() throws Exception {
    TestUser owner = data.signupAndLogin("git-owner");
    TestProject project = data.createProject(owner.client(), "git-http");
    TemporaryRepository source = TemporaryRepository.init(tempDir.resolve("source"));
    source.commit("README.md", "# smart http\n", "add readme");

    URI remote = app.gitRemote(owner.loginId(), project.name(), owner.loginId(), owner.password());
    source.push(remote);

    TemporaryRepository clone = TemporaryRepository.cloneFrom(remote, tempDir.resolve("clone"));
    assertThat(clone.read("README.md")).isEqualTo("# smart http\n");
}

@Test
void ordinaryPostWithoutCsrfTokenRemainsForbidden() {
    assertThat(anonymousClient.postForm("/-_-api/v1/favoriteProjects/1", Map.of()).statusCode())
            .isEqualTo(403);
}
```

Also add an unauthorized push test using a second user and assert that the native Git command exits non-zero.

- [ ] **Step 2: Run the focused test before changing routes**

Run:

```bash
sbt "testOnly functional.git.GitSmartHttpFunctionalTest"
```

Expected: the authenticated push fails with HTTP 403 and the ordinary POST assertion passes. The server log identifies the missing CSRF token on `git-receive-pack`.

- [ ] **Step 3: Scope the CSRF exemption to the Git RPC route**

Add the modifier directly above the existing POST Smart HTTP route and do not add it above any other route:

```routes
+ nocsrf
POST /:ownerName/:project/$service<git-upload-pack|git-receive-pack> controllers.GitApp.serviceRpc(ownerName:String, project:String, service:String)
```

Keep `GitApp.serviceRpc` annotated with `@With(BasicAuthAction.class)` and keep `GitApp.isAllowed` unchanged so read/write permissions remain enforced.

- [ ] **Step 4: Verify success and denial paths**

Run:

```bash
sbt "testOnly functional.git.GitSmartHttpFunctionalTest"
```

Expected: authenticated push and clone pass; unauthorized push fails; an unrelated POST without a CSRF token remains 403.

- [ ] **Step 5: Commit the Git protocol repair**

```bash
git add conf/routes test/functional/git/GitSmartHttpFunctionalTest.java \
  test/support/TemporaryRepository.java test/support/HttpSessionClient.java
git commit -m "fix: allow authenticated smart HTTP Git RPC"
```

---

### Task 3: Make project favorites explicit, idempotent, and safe for anonymous users

**Files:**
- Modify: `conf/routes:63-68`
- Modify: `app/controllers/api/UserApi.java:63-91`
- Modify: `app/models/User.java:989-1028`
- Modify: `public/javascripts/common/yona.Usermenu.js:9-22,103-140`
- Modify: `app/views/layout.scala.html:75-80`
- Modify: `app/views/layout_framed.scala.html:92-97`
- Modify: `app/views/migration/migrationPageLayout.scala.html:135-181`
- Create: `test/functional/project/FavoriteProjectFunctionalTest.java`
- Create: `e2e/specs/favorites.spec.ts`

**Interfaces:**
- Consumes: `PUT /-_-api/v1/favoriteProjects/:projectId` and `DELETE /-_-api/v1/favoriteProjects/:projectId`.
- Produces: `{"projectId": <id>, "favored": true|false}` for both operations; anonymous `GET /-_-api/v1/favoriteProjects` returns empty arrays.

- [ ] **Step 1: Write failing API and browser regression tests**

```java
@Test
void repeatedFavoriteCreateIsIdempotent() {
    TestUser owner = data.signupAndLogin("favorite-owner");
    long projectId = data.createProject(owner.client(), "favorite-api").id();
    assertThat(owner.client().putWithCsrf("/-_-api/v1/favoriteProjects/" + projectId).statusCode()).isEqualTo(200);
    assertThat(owner.client().putWithCsrf("/-_-api/v1/favoriteProjects/" + projectId).statusCode()).isEqualTo(200);
    assertThat(FavoriteProject.findByProjectId(owner.id(), projectId)).isNotNull();
}

@Test
void anonymousFavoriteListIsEmpty() {
    HttpResponse<String> response = anonymousClient.get("/-_-api/v1/favoriteProjects");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"projectIds\":[]");
}
```

```ts
test("one favorite click sends one request and persists after reload", async ({ page }) => {
  const requests: string[] = [];
  page.on("request", request => {
    if (request.url().includes("/-_-api/v1/favoriteProjects/")) requests.push(request.method());
  });
  const star = page.locator(".star-project[data-project-id]").first();
  await star.click();
  await expect(star.locator("i")).toHaveClass(/starred/);
  expect(requests).toEqual(["PUT"]);
  await page.reload();
  await expect(page.locator(".star-project[data-project-id]").first().locator("i")).toHaveClass(/starred/);
});
```

- [ ] **Step 2: Verify the old toggle behavior fails the new contracts**

Run:

```bash
sbt "testOnly functional.project.FavoriteProjectFunctionalTest"
npm --prefix e2e run test -- --grep "favorite click"
```

Expected: PUT/DELETE routes do not exist, anonymous GET may fail, or the browser observes duplicate POST behavior.

- [ ] **Step 3: Add desired-state favorite endpoints and model operations**

Replace the UI-facing toggle route with explicit methods:

```routes
GET    /-_-api/v1/favoriteProjects            controllers.api.UserApi.getFavoriteProjects
PUT    /-_-api/v1/favoriteProjects/:projectId controllers.api.UserApi.addFavoriteProject(projectId:String)
DELETE /-_-api/v1/favoriteProjects/:projectId controllers.api.UserApi.removeFavoriteProject(projectId:String)
```

Keep the old POST route only as a documented compatibility adapter until every in-repository caller uses PUT/DELETE.

In `User`, implement `ensureFavoriteProject(Long)` and `removeFavoriteProject(Long)` so repeated create/delete requests return the requested state. `ensureFavoriteProject` must catch a duplicate-key persistence failure, reload the existing row, and return `true` rather than surface a 500. Reject anonymous mutation with `unauthorized()` before accessing the user's collections.

Rename the JavaScript URL values in both layout templates to `UsermenuAddFavoriteProjectUrl`, `UsermenuRemoveFavoriteProjectUrl`, and `UsermenuGetFavoriteProjectsUrl`. Update the migration-page inline handler as well as `yona.Usermenu.js`; no in-repository UI may continue to issue the compatibility POST.

- [ ] **Step 4: Bind exactly one client handler and serialize a star request**

Replace direct repeated binding with namespaced unbind/rebind and an in-flight guard:

```javascript
$(".project-list > .star-project, .project-breadcrumb > .user-project-list")
  .off("click.toggleProjectFavorite")
  .on("click.toggleProjectFavorite", function (event) {
    event.stopPropagation();
    var $star = $(this);
    if ($star.data("favoritePending")) return;
    $star.data("favoritePending", true);
    var favored = !$star.find("i").hasClass("starred");
    $.ajax({
      url: UsermenuToggleFavoriteProjectUrl + $star.data("projectId"),
      type: favored ? "PUT" : "DELETE"
    }).done(function (data) {
      $star.find("i").toggleClass("starred", data.favored);
    }).always(function () {
      $star.removeData("favoritePending");
    });
  });
```

Keep the existing loaded-star rendering in the project-list templates; it is the server-side source of truth after a reload.

- [ ] **Step 5: Verify idempotency, concurrent safety, and UI persistence**

Run:

```bash
sbt "testOnly functional.project.FavoriteProjectFunctionalTest"
npm --prefix e2e run test -- --grep "favorite click"
```

Expected: duplicate create requests return 200, one `favorite_project` row exists, anonymous GET returns empty arrays, one browser click makes one PUT, and reload retains the highlighted star.

- [ ] **Step 6: Commit the favorite repair**

```bash
git add conf/routes app/controllers/api/UserApi.java app/models/User.java \
  public/javascripts/common/yona.Usermenu.js \
  test/functional/project/FavoriteProjectFunctionalTest.java e2e/specs/favorites.spec.ts
git commit -m "fix: make project favorites idempotent"
```

---

### Task 4: Fix mention boundaries and harmless non-image paste handling

**Files:**
- Modify: `app/utils/AutoLinkRenderer.java:52-63,151-164`
- Modify: `public/javascripts/common/yona.CommentAttachmentsUpdate.js:96-130`
- Create: `test/functional/markdown/MentionRenderingFunctionalTest.java`
- Create: `e2e/specs/comment-editor.spec.ts`

**Interfaces:**
- Consumes: `Markdown.render(String, Project, boolean)` and the issue comment editor textarea.
- Produces: valid user/project anchors followed by unchanged terminal punctuation; text-only clipboard events do not make an attachment request or raise a page error.

- [ ] **Step 1: Write failing mention and paste tests**

```java
@Test
void linksUserAndProjectWithoutAbsorbingTerminalPunctuation() {
    TestUser owner = data.signupAndLogin("mention-owner");
    TestProject project = data.createProject(owner.client(), "api.docs");
    ObjectNode body = Json.newObject()
            .put("body", "User @" + owner.loginId() + ". Project @" + owner.loginId() + "/api.docs,")
            .put("breaks", false);
    String html = owner.client().postJsonWithCsrf(
            "/markdown/" + owner.loginId() + "/" + project.name(), body).body();

    assertThat(html).contains("href=\"/" + owner.loginId() + "\"");
    assertThat(html).contains("href=\"/" + owner.loginId() + "/api.docs\"");
    assertThat(html).contains("</a>.").contains("</a>,");
}
```

```ts
test("text paste into a comment editor produces no page error", async ({ page }) => {
  const errors: Error[] = [];
  page.on("pageerror", error => errors.push(error));
  const editor = page.locator("form textarea").last();
  await editor.evaluate((element: HTMLTextAreaElement) => {
    const data = new DataTransfer();
    data.setData("text/plain", "plain clipboard text");
    element.dispatchEvent(new ClipboardEvent("paste", { bubbles: true, clipboardData: data }));
  });
  expect(errors).toEqual([]);
});
```

- [ ] **Step 2: Verify the regression tests fail**

Run:

```bash
sbt "testOnly functional.markdown.MentionRenderingFunctionalTest"
npm --prefix e2e run test -- --grep "text paste"
```

Expected: the rendered terminal punctuation is included in the mention candidate, and the browser reports a `selectionStart` error for a form without `.file-upload__input`.

- [ ] **Step 3: Narrow only the mention pattern**

Keep `PATH_PATTERN_STR` unchanged for issue/SHA parsing. Add a separate mention segment/path expression that permits a dot only between valid segment characters:

```java
private static final String MENTION_SEGMENT_PATTERN_STR = "[-a-zA-Z0-9_가-힣]+(?:\\.[-a-zA-Z0-9_가-힣]+)*";
private static final String MENTION_PATH_PATTERN_STR = MENTION_SEGMENT_PATTERN_STR
        + "(?:/" + MENTION_SEGMENT_PATTERN_STR + ")?";
private static final Pattern LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH_PATTERN =
        Pattern.compile("@(" + MENTION_PATH_PATTERN_STR + ")");
```

This preserves dots inside a login/project name while leaving a trailing dot, comma, exclamation mark, question mark, or closing parenthesis outside the match.

- [ ] **Step 4: Make the paste path conditional before reading attachment state**

Replace the current eager attachment lookup/caret calculation with this sequence:

```javascript
var clipboard = event.clipboardData || event.originalEvent.clipboardData;
if (!clipboard || !clipboard.items) return;

var imageItem = Array.prototype.find.call(clipboard.items, function (item) {
  return item.kind === "file" && item.type.indexOf("image") === 0;
});
if (!imageItem) return;

var $parentForm = $(this).closest("form");
var $attachmentInput = $parentForm.find(".file-upload__input");
if ($attachmentInput.length === 0) return;

var caretPos = this.selectionStart;
```

Use `$parentForm` for the upload card and textarea lookup in the AJAX callback. Remove `getCaretPos` because it couples a textarea caret to an optional attachment input.

- [ ] **Step 5: Verify rendering and browser behavior**

Run:

```bash
sbt "testOnly functional.markdown.MentionRenderingFunctionalTest"
npm --prefix e2e run test -- --grep "text paste"
```

Expected: both mention links render without terminal punctuation inside the anchor and the paste event produces no browser error or upload request.

- [ ] **Step 6: Commit the renderer/editor repair**

```bash
git add app/utils/AutoLinkRenderer.java public/javascripts/common/yona.CommentAttachmentsUpdate.js \
  test/functional/markdown/MentionRenderingFunctionalTest.java e2e/specs/comment-editor.spec.ts
git commit -m "fix: preserve mention punctuation and safe comment paste"
```

---

### Task 5: Run release-level regression verification and document the behavior

**Files:**
- Modify: `docs/technical/play-framework-porting.md`
- Modify: `docs/technical/mcp-server.md`
- Modify: `README.md` if it contains a test/run command section

**Interfaces:**
- Consumes: the new test commands and cleanup guarantees from the test-rebuild plan.
- Produces: documented local verification prerequisites and a release-ready evidence record.

- [ ] **Step 1: Add a focused release regression checklist to the documentation**

Document these exact behaviors:

```text
1. sbt test runs JUnit unit and MariaDB-backed functional tests.
2. sbt stage builds the production artifact.
3. npm --prefix e2e run test runs Playwright against that staged artifact.
4. Smart HTTP Git POST routes are nocsrf only because BasicAuthAction and Yona authorization still protect them.
5. MCP remains disabled unless mcp.enabled=true and accepts only authenticated read-only requests.
```

- [ ] **Step 2: Execute the full release gate from a clean process state**

Run:

```bash
docker info
sbt clean compile test stage
npm --prefix e2e ci
npx --prefix e2e playwright install chromium
npm --prefix e2e run test
git diff --check
```

Expected: every command exits 0. Confirm with `lsof` that the temporary MariaDB and staged application ports are closed after the suite completes.

- [ ] **Step 3: Commit the release verification documentation**

```bash
git add docs/technical/play-framework-porting.md docs/technical/mcp-server.md README.md
git commit -m "docs: document Play 3 regression verification"
```
