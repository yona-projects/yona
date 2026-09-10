---
id: twirl-thymeleaf-rendering-audit-2026-09-11
type: report
created: 2026-09-11
status: done
relates_to: [twirl-vs-thymeleaf-parsing-notes, p3-49, p3-50, p3-51, p3-52]
---

# Twirl ↔ Thymeleaf 렌더링 동치성 전수 감사 (2026-09-11)

## 배경

`docs/TEMPLATE_BACKLOG.md`(내용 이식 여부 검증)와는 별개로, "내용은 그대로 옮겼는데 Twirl과
Thymeleaf가 같은 소스를 서로 다르게 파싱/렌더링해서 실제 브라우저 동작이 달라지는" 클래스의
버그를 찾기 위한 감사. 사전 조사 결과는 [[TWIRL_VS_THYMELEAF_PARSING_NOTES]] 참고. 대상:
`src/main/resources/templates/**/*.html` 182개 파일 전체.

## 방법

각 항목을 (1) grep/스크립트로 전수 스캔 → (2) `git show origin/v1.6:app/views/...`로 legacy
원본과 직접 대조해 진짜 회귀인지 판단 → (3) 확정된 항목만 RED 테스트 작성 → 수정 → GREEN
확인 순서로 진행했다. 카테고리 B/C는 두 개의 fork 서브에이전트로 병렬 조사했다(스캔 결과는
이 문서에 그대로 반영, 조사 과정 자체는 메인 세션 컨텍스트에 남기지 않음).

## 카테고리별 결과

### A. `<script>`/`<style>` 안 중첩 pseudo-element/속성 프로세서 미처리

- **스캔**: HTML 주석을 먼저 제거한 뒤(주석 안 예시 마크업이 오탐을 일으킴 — 방법론 교훈,
  [[TWIRL_VS_THYMELEAF_PARSING_NOTES]] 참고) 182개 파일의 모든 `<script>`/`<style>` 블록
  내부에서 중첩 pseudo-element(`<th:block>` 등)와 안쪽 태그의 속성 프로세서를 스캔.
- **발견**: 중첩 pseudo-element 리터럴 **0건**(P3-49에서 이미 수정된 `issue/view.html` 건이
  재발하지 않았음을 재확인). 안쪽 태그 속성 프로세서 미처리 **3건** — `site/userList.html`
  1건(검토 결과 의도된 코드, 버그 아님 — P3-49 문서에 이미 기록), `site/layout.html`
  2건(`tplAttachedFile`/`tplDropFilesHere`의 `th:text` — 로케일 무관하게 한국어 고정,
  **실제 회귀**).
- **수정**: `site/layout.html` 2건 — `<script th:inline="text">` + `[[#{...}]]` 인라인
  토큰으로 교체. 회귀 테스트: `AttachmentJqueryTemplateI18nSpec`(신규 1건).

### B. `th:inline="javascript"` + 수동 따옴표 `"[[...]]"` 이중 이스케이프

- **스캔**: `th:inline="javascript"`가 붙은 46개 파일 전체에서 `"[[`/`'[['` 패턴(수동
  따옴표로 감싼 인라인 표현식)을 스캔.
- **발견**: **3개 파일, 총 8곳** — `pullrequest/view.html`(4곳: confirm/alert/리뷰
  등록·취소 POST URL 2개, **CRITICAL** — 이 스크립트 블록 하나가 watch 토글/담당자·라벨
  변경/리뷰 등록/승인/merge 버튼을 전부 담당해서 깨지면 PR 상세화면의 핵심 상호작용이 전부
  죽는다), `pullrequest/clone.html`(3곳: cloneUrl/cloneParam/실패 alert, **HIGH** — 포크
  완료 3초 후 `doClone()` 호출이 실행되지 않아 "복제 중입니다" 화면에서 영원히 멈춤),
  `common/commentDeleteModal.html`(1곳: 삭제 실패 alert, **LOW** — 문법은 안 깨지고 표시
  문구에 따옴표 문자가 겹쳐 보이는 경미한 결함).
- **수정**: 3개 파일 전부 — 수동 따옴표를 제거하고 인라인 표현식이 스스로 완전한 JS 문자열
  리터럴을 만들게 함(`"[[${x}]]"` → `[[${x}]]`, 필요하면 `+ '...'`로 이어붙임). RED→GREEN
  확인: `pullrequest/view.html`은 실제로 되돌려서(`git checkout`) RED를 직접 재현·확인한
  뒤 재적용, `pullrequest/clone.html`도 동일하게 RED 재현 확인. 회귀 테스트 3건 신규
  (`PullRequestViewInlineScriptSyntaxSpec`, `PullRequestCloneInlineScriptSyntaxSpec`,
  `CommentDeleteModalInlineScriptSyntaxSpec`).

### C. legacy가 로드하던 `<script src>`가 포팅본에서 누락

- **스캔**: 242개 legacy 뷰 전체의 1차 자산(third-party 라이브러리 제외) script-src 목록을
  뽑아 포팅본과 대조(27개 고유 파일, 51개 뷰 참조).
- **발견**: 9개 파일이 "어디서도 로드되지 않음" 후보로 나왔고, 그중 5개(`yobi.
  CodeCommentBlock.js`/`yobi.CodeCommentBox.js`/`yobi.Interval.js`/`yona.Sha1.js`/
  `yobi.issue.Sharer.js`)는 이미 다른 방식으로 재구현됐거나 원래 죽은 코드로 확인(오탐).
  실제 격차로 남는 4건: `yona.ReceiverList.js`(댓글 작성 중 알림 대상 실시간 미리보기),
  `yona.detectChange.js`(이슈 화면 변경 감지 폴링), `yobi.CommentForm.js`(댓글 작성 중
  이탈 경고 + localStorage 초안 자동저장), PR 코드 리뷰 diff 화면의 "새 인라인 댓글 작성
  UI" 실재 여부(스캔 단계에서 확정 못 함 — 가장 우선순위 높은 재조사 대상).
- **처리**: 4건 전부 프론트엔드 신규 기능 구현이 필요한 규모라 이번 라운드에선 구현하지
  않고 [[tickets/p3-52|P3-52]]로 정직하게 등록(TEMPLATE_BACKLOG류 기능 격차이지 Twirl/
  Thymeleaf 파싱 차이 자체는 아니라고 판단).

### D. `@Html(x)` vs `th:utext`/`th:text` 불일치 (보안/XSS)

- **스캔**: legacy `@Html(` 110곳 전수(`git grep`) + 현재 `th:utext` 44곳 전수를 서로
  대조. 각 `th:utext` 자리마다 그 값이 (1) 새니타이즈된 마크다운 렌더링 결과인지, (2)
  서버가 관리하는 신뢰된 i18n 메시지인지, (3) 서버가 직접 조립하는 안전한 마크업인지, 아니면
  (4) 사용자 입력을 새니타이즈 없이 그대로 담을 수 있는지 하나하나 확인.
  `markdownService.render()`/`.sanitize()`를 거치는 모든 자리(이슈/게시글/PR/댓글/위키
  본문 등 20여 곳)는 내부적으로 OWASP Java HTML Sanitizer 정책을 거치는 것을 코드로
  확인해 안전.
- **발견(실제 회귀, 1건)**: `index/partial_notifications.html`의 알림 메시지
  (`th:utext="${noti.message}"`)가 새니타이즈 없이 렌더링됨. legacy는 같은 자리에서
  `@Html(HtmlUtil.defaultSanitize(noti.getMessage...))`로 반드시 새니타이즈를 거쳤다.
  `NEW_COMMENT` 등 이벤트의 `newValue`는 댓글 작성자가 그대로 입력한 원문(`Comment.
  getContents()`)이 그대로 저장되므로, 악의적인 댓글 작성자가 `<script>`를 넣으면 그
  알림을 받는 다른 사용자의 "/"·"/notifications" 화면에서 그대로 실행되는 **저장형 XSS**
  였다.
- **수정**: `IndexController.mapNotificationsToView()`에서 알림 메시지를
  `markdownService.sanitize(msg.replace(줄바꿈 -> <br/>))`를 거치도록 복구(legacy와 동일
  로직). `MarkdownService`를 `IndexController` 생성자에 새로 주입. 회귀 테스트:
  `NotificationStreamXssSpec`(신규 2건 — `<script>` 태그가 걸러지는지, 정상 텍스트는
  유지되는지).
- **확인한 비-회귀 케이스**: `common/branchItem.html`(브랜치명), `common/commitMsg.html`
  (커밋 메시지), `site/mail.html`/`user/lostPassword.html`(`errorMessage`를 메시지 키로
  우선 해석하고 실패 시 원문 그대로 노출)는 legacy도 동일하게 새니타이즈 없이 렌더링하던
  자리라 "포팅 과정에서 새로 생긴" 회귀가 아님(legacy 자체의 사전 위험이라 이 감사 범위
  밖으로 분류).

### E. boolean 속성(checked/disabled/selected/readonly/multiple)

- 낮은 우선순위로 지정된 대로 시간 관계상 전수 대조는 하지 못했다. `th:checked` 등 101곳이
  템플릿 전체에 흩어져 있음을 확인만 했고, 개별 대조는 미착수 — [[tickets/p3-52|P3-52]]와
  별개로 향후 세션에서 이어서 진행 필요.

## 감사 도중 발견한 범위 밖(비-Twirl/Thymeleaf) 이슈 1건

**`spring.messages.fallback-to-system-locale`(Spring Boot 기본값 true)** — 카테고리 A의
i18n 회귀를 검증하는 테스트를 로케일을 바꿔가며 작성하던 중 발견. 요청 로케일 전용 메시지
파일이 없으면 root 기본값보다 먼저 **서버 JVM 시스템 로케일** 파일을 시도하는 Java
ResourceBundle 기본 동작 때문에, `Locale.ENGLISH`를 명시해도 이 샌드박스(JVM 기본 ko_KR)에서는
한국어 메시지가 나오는 것을 실측으로 확인(`messageSource.getMessage(key, null, Locale.ENGLISH)`
직접 호출로 재현). `fallback-to-system-locale: false`로 끄면 legacy(`application.langs`의
결정적 폴백)와 동등해지지만, **이 한 줄만으로 전체 스위트에서 53건(14개 스펙 파일)이 새로
실패**하는 것을 확인했다 — 그만큼 많은 기존 테스트가 이 버그의 부작용("로케일 미지정 요청은
항상 한국어로 보인다")에 암묵적으로 의존하고 있었다는 뜻이다. Twirl-vs-Thymeleaf 템플릿 파싱
차이가 아니라 Spring MessageSource 설정 문제라 이번 감사 범위 밖으로 판단해 **수정을 되돌리고
발견만 [[tickets/p3-51|P3-51]]로 기록**했다(이 세션 범위를 지키기 위한 판단 — "시작 시점
기준 실패 3건 외에 실패를 늘리지 않는다"는 원칙과 충돌하는 큰 변경이라 별도 세션에서 53건을
개별 검토하며 진행하는 게 안전하다고 판단).

## 요약 표

| 카테고리 | 스캔 대상 | 발견 | 실제 회귀 확인 | 수정 완료 | 남겨둔 것 |
|---|---|---|---|---|---|
| A (script/style 미처리) | 182개 파일 전체 | 3건(주석 제거 스캔 후) | 2건 | 2건 | 0 |
| B (th:inline 이중 이스케이프) | th:inline="javascript" 46개 파일 | 8곳/3개 파일 | 8곳/3개 파일 | 8곳/3개 파일 | 0 |
| C (script src 누락) | 242개 legacy 뷰 | 9개 후보 | 4건(5건은 이미 재구현/죽은 코드로 확인) | 0 | 4건 → [[tickets/p3-52\|P3-52]] |
| D (utext/escape 불일치) | legacy `@Html` 110곳 + 현재 `th:utext` 44곳 | 1건 | 1건(보안, 즉시 최우선 처리) | 1건 | 0 |
| E (boolean 속성) | 101곳(위치만 확인) | 미착수 | - | 0 | 전체 → 향후 세션 |
| (범위 밖 보너스) MessageSource 로케일 폴백 | - | 1건(실측 확인) | 1건(영향 큼, 53개 테스트) | 0(되돌림) | [[tickets/p3-51\|P3-51]] |

## 최종 테스트 스위트 상태

`./gradlew test -Dyona.it.db=h2` 전체 재실행: **6,554건 중 3건 실패** — 시작 시점 기준
실패 3건(`site/postList.html`의 SpEL 오류 2건, `PullRequestListTemplateEquivalenceSpec`의
위젯 마크업 불일치 1건)과 정확히 동일한 건들이며, 실패 메시지를 직접 대조해 새로 늘어난
실패가 없음을 확인했다. 이번 세션에서 신규 작성한 회귀 테스트는 총 7개 파일, 신규 테스트
케이스 8건 — 전부 GREEN.
