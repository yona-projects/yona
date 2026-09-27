# Turbo + Thymeleaf two-column PoC: compatibility gate result

## Verdict

**Stop before production cutover.** Official Turbo can extract real Thymeleaf HTML without an iframe, and its asset can be packaged reproducibly by Gradle. That does **not** establish issue-detail parity or justify replacing most client-side JavaScript.

The experiment follows the supplied `PLAN-turbo-thymeleaf-poc.md` stop condition 6: preserving the existing detail surface requires shared lifecycle and draft-identity changes beyond the narrow list/two-column adapter. The strongest observed safety problem is an unsubmitted comment from issue A being restored into issue B when both use `/issues?selected=…` URLs. This occurs even with fresh full-document navigation, independently of Turbo frame mounting.

This is a scope decision, **not a claim that Turbo is technically incapable** of supporting Yona. A local initialization hook is possible; it does not fix draft identity, private polling timers, or shared ready-only initializers.

- Base: fetched `origin/next`, `1d04fe7` (`fix: require arrays in legacy bulk creation requests`).
- Worktree: `/Users/senghyunjo/github/yona-turbo-poc`.
- Branch: `poc/turbo-thymeleaf`.
- Baseline characterization: `f3f7d9523`.
- Asset pipeline: `38bf6a306`.
- Compatibility probes: `d697b59ef`.
- No PR was opened or pushed. This branch is an experiment, not a production replacement ready for a draft implementation PR.

## What is implemented, and what is not

Implemented:

1. Opt-in browser characterization of the existing iframe path.
2. Locked official `@hotwired/turbo` 8.0.23, self-hosted through Gradle production resources.
3. Browser probes using real server-rendered issue HTML and the real Turbo runtime.
4. A regular Thymeleaf POST/CSRF gate with Turbo Drive and Turbo forms disabled.
5. A reproducible negative draft-isolation check for the proposed canonical URL shape.

**No production controller, template, legacy navigation, or widget behavior was changed.** A selected-controller draft was removed when the compatibility gate failed. There is no unused selected endpoint, pretend read-only detail implementation, feature-flagged alternate production architecture, JSON screen API, custom application `fetch()+innerHTML` swap, or custom History API replacement.

The probes use explicit **test-only HTTP seams**:

- Frame probe: request the real issue detail route; wrap its rendered detail and unchanged inline ready callbacks in a matching frame in the intercepted HTML response. Turbo, not test code, performs the live DOM replacement. This does not claim a server-rendered production frame boundary.
- Draft probe: alias two proposed `/issues?selected=N` URLs to the corresponding real issue detail responses. The real ready callbacks and real five-second autosave run on fresh documents. The aliases exist only in Playwright routing; they are not implemented Spring routes.

These seams isolate migration risks before enabling a broken surface. They are **not** substitutes for the plan's end-to-end acceptance suite.

## Observed behavior

### Unchanged baseline

The H2 application was started before production modifications. Existing project-create and issue CRUD tests passed: **11/11 including setup**.

The opt-in legacy characterization observed:

- `useTwoColumnMode` on/off persists across reload.
- A and B open in an iframe (the current branch can host it inside `yona-page-slide`'s shadow DOM).
- The parent URL becomes the normal `/issue/N` route, not a reconstructible selected list URL.
- One captured Back state had URL A but highlighted row B and iframe B.
- A completed characterization run showed Forward returning to B and reload replacing the list/iframe with B's full detail page.
- Preference-off navigation opens a normal full detail page.

History characterization is **not stable green**: subsequent runs hit `page.goForward: net::ERR_ABORTED` and a reload `Not attached to an active page` error during competing navigation. These are recorded as observed automation/navigation limitations, not automatically attributed to a Turbo regression. No application navigation code had changed. Do not report the successful observation run as history parity.

Source explains why the standalone two-column script is not the sole history owner:

- `service/yona.twoColumnMode.js:220-229` pushes/replaces history.
- `templates/site/layout.html:39-41` also replaces the parent's state when an authenticated detail document loads.
- `service/yona.issue.List.js:322-364` independently fetches on `popstate`, updates the PJAX container, and can fall back to document navigation.

### Actual Turbo transport and detail initialization

The final transport probe observed:

| Check | Result |
|---|---|
| Official self-hosted runtime | Turbo 8.0.23 |
| Browser detail request | One request with `Turbo-Frame: issue-detail` |
| Server response | `text/html;charset=UTF-8`, real Thymeleaf issue detail |
| Matching frame content | Actual issue header rendered |
| iframe/page-slide in probe | None |
| Full-page delete-dialog trigger | Opens its dialog |
| Same unchanged trigger after frame insertion | Does not open its dialog |

`DOMContentLoaded` has already fired when frame content arrives. Merely retaining the existing callbacks does not run their initialization. Fixing that timing alone would not resolve the following deeper problems.

### Cross-issue draft restoration: browser-proven

The draft probe:

1. Opens real issue A HTML at the test alias `/issues?selected=A`.
2. Enters an unsubmitted comment and fires the editor's existing keyup autosave path.
3. Waits for the real five-second save into localStorage.
4. Opens real issue B HTML at `/issues?selected=B` in a fresh document.
5. Asserts that B's comment textarea now contains A's draft.

Final captured run: A `#1`, B `#10`, shared key `/admin/e2e-git-mujerqcs85s7/issues`.

No duplicate client domain state is needed to fix this: the existing helper needs a stable issue identity. The current helper is not query-state-safe:

- `service/yona.temporarySaveHandler.js:17-55`: restore/save uses `location.pathname`; delayed save retains the textarea.
- `service/yona.temporarySaveHandler.js:60-62`: draft removal uses the same pathname.
- `templates/site/layout.html:428-442`: recovered-draft visibility and clear action also depend on this behavior.
- The served copy comes from `javascripts/yona-lib.js`, so editing only the readable source would not fix the running application.

### Additional source-backed lifecycle constraints

These are code findings, not claims that all resulting failures were exercised:

- `service/yona.detectChange.js:14-65`: recursively scheduled private `setTimeout`, no returned cleanup or exposed timer handle. **[INFERENCE]** Remounting A→B without changing this contract retains A's polling and notifications.
- `common/yona.Tasklist.js:8-138`: checkbox persistence and permission-related disabling live in an anonymous ready callback; no scoped mount function.
- `common/yona.SubComment.js:1-114`: anonymous ready-only setup for reply controls and timeline presentation.
- `templates/issue/view.html:550-843`: document/window listeners and detail initialization need scoped mounting and disposal.
- List and detail both use IDs such as `assignee` and `milestone`; a shared document needs list-local IDs adjusted.
- The existing editor is a self-mounting custom element (`site/layout.html:361-367`). **No editor-internal rewrite was shown to be necessary.**

The minimum prerequisite is a bounded shared lifecycle change, not React, Stimulus, a global state store, or a new framework:

1. Give the existing poller cleanup and ignore/abort in-flight results after disposal.
2. Give draft save/remove a stable issue-route key and cancel pending saves on unmount; update its shipped bundle and clear-draft consumers.
3. Expose scoped initialization for existing Tasklist/SubComment behavior without duplicating it or replaying global `DOMContentLoaded`.
4. Then extract the detail markup/init once and verify A→B→A, cache restoration, and widget behavior.

Those changes were deliberately **not** smuggled into this navigation-only experiment.

## Build and CSRF gates

Spring Boot's official MVC and Thymeleaf starters and the Thymeleaf security extras remain unchanged. No server-side Turbo starter was added. Spring's [HTML fragment documentation](https://docs.spring.io/spring-framework/reference/web/webmvc-view/mvc-fragments.html) supports this HTML-over-the-wire approach; `FragmentsRendering` is not needed for the initial full-document extraction path.

Build chain: `npmCi` → `copyTurbo` → `processResources` → `bootJar`.

- Exact package version and npm integrity lockfile.
- No CDN, pasted vendor JS, Vite, Gradle frontend plugin, or runtime Node dependency.
- Generated file: `build/generated/turbo/turbo.es2017-esm.js`.
- Packaged file: `BOOT-INF/classes/static/javascripts/turbo/turbo.es2017-esm.js`.
- Node 22+/npm 10+ on `PATH` is an intentional, documented **build** prerequisite, including `./gradlew test`.
- Exercised versions: Node 24.21.0, npm 11.19.0, Gradle 9.7.1.
- `./gradlew processResources bootJar` succeeded.
- Packaged bytes exactly matched the installed locked npm distribution: **203,701 bytes**, SHA-256 `b9d35d123a07614f55eaaf993f74d687a503ae41ba50ef835aafa18dbb265a13`.
- The asset is packaged but not loaded by any production page.

The CSRF probe loads Turbo, sets `Turbo.session.drive = false` and `Turbo.config.forms.mode = 'off'`, then proves:

- A POST without a CSRF token returns **403**.
- The real issue-create Thymeleaf form sends `_csrf` through native document navigation, receives **302**, and renders the created issue.
- The request is not a Turbo frame submission.

This establishes only the **forms-off** contract. It does not establish Turbo-driven mutation-form compatibility or authorize a site-wide rollout. No CSRF setting was weakened.

## Measurements and limits

| Metric | Observed result |
|---|---|
| Production custom JS delta | +0 / −0 lines |
| Legacy iframe/page-slide code removed | None; the replacement gate did not pass |
| Production manual History API delta | 0; existing calls retained |
| Application custom detail-swap fetch added | 0 |
| Baseline A→B selection window | 2 detail document requests; 143 browser request events total |
| Baseline event breakdown | 96 script, 24 stylesheet, 11 image, 6 fetch, 4 font, 2 document |
| Turbo transport probe | 1 matching-frame request for one selection; not an A→B parity measurement |
| Real upstream detail HTML | 94,694 decoded UTF-8 bytes in final probe |
| Test-framed HTML response | 94,267 decoded UTF-8 bytes in final probe |
| Added official runtime | 203,701 bytes uncompressed in jar |
| Query increase for selected list rendering | Not measured: no production selected list controller remains |
| Production direct selected URL / reload | Not implemented or claimed |
| Turbo Back/Forward / filters / pagination / mobile | Not accepted or claimed |
| Visual parity | Not achieved; diagnostic frame placement is not a two-column layout implementation |

The baseline total counts request events, including cached resources, existing analytics, and blob URLs—not 143 server round trips. The two baseline selections and one diagnostic frame selection are not a like-for-like performance comparison. HTML sizes are decoded payload sizes, **not compressed transfer-byte measurements**. No query-performance or broad JavaScript-reduction claim follows from them.

## Verification record

- Initial unchanged baseline: project creation + issue CRUD, **11 passed** including setup.
- Scoped run with project-create/home/members and all issue specs: **37 passed, 1 failed**. The failed test was the new opt-in legacy history characterization; the existing scenarios and the three compatibility probes passed.
- A prior scoped run needed the existing `secondUserLoginId` signup seed. After running that prerequisite, project-members and the three probes passed (**7/7 including setup**). This was a test prerequisite failure, not an application fix.
- Final compatibility-only run: **4/4 including setup**. Passing here means reproducing the two negative findings and passing the forms-off CSRF check—not passing the plan's replacement acceptance suite.
- Plain `./gradlew test`: Kotest initialization failed at `DockerClientProviderStrategy` before the suite could run.
- `./gradlew test -Dyona.it.db=h2`: also failed during container initialization; the H2 switch does not eliminate every spec's container dependency.
- With the active OrbStack socket explicitly supplied (`DOCKER_HOST=unix:///Users/senghyunjo/.orbstack/run/docker.sock ./gradlew test`), the full suite ran: **6,724 tests, 6,723 passed, 1 failed, 0 skipped**. Failure: untouched `LegacyIssueResponseIntegrationSpec`, “GET, PUT and state PATCH return mapped results and persisted events without user secrets”, line 142. The issue `attachments` array was empty and `.single()` threw `NoSuchElementException`. The case's cause was not isolated; do not claim a clean baseline or attribute it to Turbo. The full HTML report is under `build/reports/tests/test/index.html`.
- No WTR manifest/configuration was present in this checkout; the tracked frontend test runner is the existing Playwright suite. No new test framework was added.

## Reproduction

Use a disposable H2 instance and the branch's documented Node/npm prerequisites. Tests create projects/issues and do not target production data.

```sh
./gradlew bootRun --args='--spring.profiles.active=h2 --server.port=18080'
```

In a second terminal:

```sh
cd e2e
npm ci
npx playwright install chromium
YONA_BASE_URL=http://localhost:18080 npx playwright test specs/01-auth/auth.spec.ts --grep 'every required field'
YONA_BASE_URL=http://localhost:18080 npx playwright test specs/04-project/00-project-create.spec.ts specs/06-issue/issue-crud.spec.ts
YONA_TURBO_PROBE=1 YONA_BASE_URL=http://localhost:18080 npx playwright test turbo-lifecycle-probe.spec.ts
YONA_LEGACY_TWO_COLUMN=1 YONA_BASE_URL=http://localhost:18080 npx playwright test two-column-legacy.spec.ts
```

The legacy command may fail during history navigation; stdout and Playwright attachments retain observations collected before the failure. The three compatibility probes are opt-in and skipped in ordinary runs. JSON evidence and a frame screenshot are attached to the Playwright HTML report. No positive feature acceptance is hidden behind expected-failure markers.

## Draft PR body

**Suggested title:** `PoC: characterize Turbo Frames compatibility with issue detail`

> This is an architecture experiment against `next`, not an approved Turbo adoption or a completed iframe replacement.
>
> Thymeleaf remains the canonical renderer. There is no SPA migration, visual redesign, Tailwind migration, or broad Turbo rollout. Official Turbo is self-hosted through a minimal locked Gradle/npm resource pipeline.
>
> Real-browser probes show matching-frame HTML extraction works, and regular Thymeleaf POST forms retain CSRF enforcement with Turbo forms disabled. They also expose a cross-issue comment-draft collision at the proposed selected URLs and existing detail lifecycle constraints. The supplied plan's stop condition 6 was applied; legacy behavior was not removed and incomplete controller/template changes were not retained.
>
> The purpose is to provide concrete evidence for the subsequent frontend architecture discussion. Shared draft identity and mount/unmount contracts need an explicitly scoped prerequisite before a full two-column acceptance run. The PR may be revised or closed following maintainer feedback. This does not assert that Yona has decided to adopt Turbo.

## Copyable follow-up issue evidence

```text
### PoC result
- target: issue-list two-column mode on next 1d04fe7
- branch/commit: poc/turbo-thymeleaf; baseline f3f7d9523, build 38bf6a306, probes d697b59ef
- result: stop condition 6; transport works, unchanged detail lifecycle/draft identity is not safe
- custom JS delta: production +0/-0; official Turbo asset 203,701 uncompressed bytes
- iframe removed from target path: no; zero iframes only in diagnostic frame probe
- manual history code delta: 0; legacy retained
- server/template changes: none retained; probe wraps/aliases real Thymeleaf responses at a test-only HTTP seam
- request behavior: one Turbo-Frame request and HTML extraction in probe; no custom application detail swap
- direct URL/reload: selected production path not implemented; canonical-URL alias probe reproduces cross-issue draft restoration on fresh document loads
- back/forward: legacy URL/pane mismatch observed; intermittent navigation aborts recorded; Turbo parity not established
- E2E result: final 3 compatibility probes + setup pass; broader scoped run 37 pass/1 legacy history characterization failure
- full regression result: 6,723/6,724 passed, 1 untouched LegacyIssueResponseIntegrationSpec failure (empty attachments at line 142); active Docker socket required; not full green
- CSRF findings: forms-off native POST with _csrf succeeds; missing token returns 403; Turbo mutation forms remain unproven
- performance/query findings: probe framed HTML 94,267 decoded bytes; selected-list query increase unmeasured
- limitations: no selected controller/frame cutover, no mobile/filter/pagination acceptance, no production JS reduction claim
```

Comparison implications:

- **Current Thymeleaf + imperative JS:** Turbo can take over HTML transport; existing widget initialization, teardown, and draft identity do not disappear automatically.
- **React SPA / `yona-bun-temp`:** this experiment adds no JSON screen model or client domain store. It does not benchmark or inspect that separate fork, so it makes no comparative performance or implementation-cost claim.
- **Gitea/Forgejo-style server rendering:** server-owned HTML and progressive enhancement remain viable architectural directions. This experiment did not inspect or test those projects; the relevant local prerequisite is making Yona's widget lifecycle explicit.

References: [Turbo Frames](https://turbo.hotwired.dev/handbook/frames), [Turbo application lifecycle and caching](https://turbo.hotwired.dev/handbook/building), [Spring MVC HTML fragments](https://docs.spring.io/spring-framework/reference/web/webmvc-view/mvc-fragments.html). The final architecture issue itself is not created here.
