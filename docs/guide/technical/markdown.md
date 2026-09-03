# 마크다운 지원 방식

legacy Yona의 `docs/technical/markdown.md`는 아주 짧은 문서였다 — `markdown` HTML 속성을 쓰고
Play 템플릿에서 `@views.html.markdown()` 헬퍼를 호출하면 활성화된다는 설명뿐이었다. yona는
Play 템플릿 헬퍼가 없으므로 이 부분만 새로 정리했다. 클라이언트 쪽 관례(`markdown` 속성)
자체는 legacy와 동일하게 남아 있다.

## 클라이언트 측 (`yobi.Markdown.js`, 파일 위치·이름 legacy와 동일)

- 에디터로 쓸 엘리먼트에는 legacy와 동일하게 `markdown` 속성을 붙인다.

  ```html
  <textarea markdown></textarea>
  ```

  또는 렌더링 대상이면:

  ```html
  <div markdown>...</div>
  ```

- `yobi.Markdown.js`가 이 속성을 가진 엘리먼트를 찾아(`$(sQuery || "[markdown]")`) 처리한다.
- 마크다운→HTML 변환 자체는 **클라이언트에서 `marked.js`로 즉시 렌더링**한다
  (`_renderMarkdown()`이 `marked(sText, ...)` 호출 후 `$yobi.xssClean()`으로 정제).
- 다만 이슈/멘션 등의 **자동 링크 치환**은 클라이언트만으로는 할 수 없어서(서버의 도메인
  지식이 필요), `_render()`가 별도로 서버에 AJAX POST 요청을 보낸다 — 요청 대상 URL은
  `htOptions.sMarkdownRendererUrl`로 주입되며, 실제 값은 `MarkdownController`의
  `POST /markdown/{owner}/{projectName}` 엔드포인트다.

## 서버 측 (`MarkdownController`, `MarkdownServiceImpl`)

- `POST /markdown/{owner}/{projectName}` — JSON body `{"body": "...", "breaks": true|false}`를
  받아 렌더링된 HTML을 반환한다. `breaks`는 `readme-body` 클래스(README 렌더링)에서는
  `false`, 나머지(댓글 등)는 `true`로 클라이언트가 지정한다.
- 실제 마크다운 렌더링 엔진은 legacy의 Nashorn/Rhino JS 엔진(`lib/js-engine.jar`) 기반이 아니라
  **CommonMark Java**(`org.commonmark:commonmark` + `commonmark-ext-gfm-tables`/
  `commonmark-ext-gfm-strikethrough`/`commonmark-ext-autolink`)로 완전히 대체됐다.
- 새니타이징도 legacy의 커스텀 `Markdown.java` allowlist 대신 **OWASP Java HTML Sanitizer**로
  동일한 allowlist 정책을 재구현했다(`docs/PARITY_BACKLOG.md` P0-08).
