---
title: 서버 측 마크다운 렌더링
type: web
sources: [app/utils/Markdown.java:36-330, public/javascripts/lib/marked.js]
updated: 2026-10-22
---

# 서버 측 마크다운 렌더링

결정 근거는 [../decisions.md](../decisions.md) D-17이다.

## 구성 (2026-10-27 갱신, A-154)

| 구성 요소 | 역할 |
|---|---|
| `MarkedEngine` | v1.6의 `public/javascripts/lib/marked.js`(소스 기준 판)를 Nashorn 독립 배포판(`org.openjdk.nashorn:nashorn-core:15.4`)에서 같은 옵션(`gfm`, `tables`, `breaks`, `headerIds`, `smartLists`)으로 실행한다. 엔진은 처음 쓸 때 만들고 호출을 직렬화한다. JDK 8 Nashorn의 ES5 `splice(시작)` 동작을 맞춘다 |
| `MarkdownRenderer` | `MarkedEngine`의 출력을 v1.6과 같은 정책의 OWASP 정화(20190610.1)에 통과시키고 캐시한다 |
| `HtmlDocuments` | jsoup 1.8.3 기본 출력 설정으로 읽는다(v1.6 `removeJavascriptInHref` 등과 같다) |
| `MarkedReference`, `MarkdownDifferenceTest` | 테스트 전용 GraalJS 비교. 이제 운영도 같은 파일을 쓰므로 엔진 간 차이만 드러낸다 |

이전의 commonmark-java 구성은 공식 v1.16.0과 160여 개 입력을 비교한 결과 강조 겹침(`***x***`), 줄바꿈, 제목 `id` 문자 처리, 표의 남는 칸이 달라 버렸다.

## v1.6 마크업

`marked.js`가 직접 만든다: 제목은 `id="yb-header-…"`에 `<a href="#id" class="head-anchor">#</a>`, 작업 목록은 `task-list-item` 클래스를 붙인다. 후처리나 변환 코드를 따로 두지 않는다.
(공식 v1.16.0 릴리스의 `marked.js`는 소스 기준보다 세 커밋 앞서지 않아 제목 앵커가 없고 슬러그를 두 번 불러 `id`가 `-1`, `-3`으로 매겨진다. 비교 도구에서는 릴리스의 `marked.js`를 소스 기준으로 바꿔 쓴다, A-153.)

## 렌더 결과 캐시

`RenderedMarkdownCache`는 v1.6 `CacheStore.renderedMarkdown`에 해당한다. 값은 deflate로 압축해 최대 10000건까지 보관하고 만료 시간은 없다. 키는 원문의 SHA-256이다(A-18, v1.6은 `hashCode()`라 충돌 시 결과가 뒤바뀔 수 있었다). 정화된 변환 결과만 캐시하고 `sanitize`는 캐시하지 않는다. 프로젝트 문맥 후처리(자동 링크)는 v1.6처럼 캐시 바깥에서 매번 수행한다. 테스트 5건과 렌더러 재사용 테스트 1건이 있다.

## 정화 정책

v1.6 `Markdown.sanitizerPolicy`와 같다. OWASP `FORMATTING`, `IMAGES`, `STYLES`, `TABLES`, `BLOCKS`에 `video`, `source`, `input`, `iframe` 등을 더하고 프로토콜은 `http`, `https`, `mailto`, `file`, `zpl`만 허용한다.

## 비교 결과 (2026-10-22, commonmark 시절)

20개 입력(제목, 강조, 줄바꿈, 목록, 작업 목록, 표, 코드, 인용, 링크, 이미지, 원시 HTML, 자바스크립트 링크, 한글 제목 등) 모두 일치한다. 입력 모음이 아직 작으므로 새로운 마크다운 사용처를 이식할 때 입력을 더하고, 차이가 발견되면 이 문서에 기록해 선생님의 승인을 받는다.

## 프로젝트 문맥 파이프라인 (`ProjectMarkdownRenderer`)

게시글, 코멘트, README, 메일처럼 어느 프로젝트의 글인지 알 수 있는 곳에서 쓴다. 처리 순서는 v1.6과 같다.

1. 마크다운 -> HTML(위 `MarkdownRenderer`) 
2. `LinkPostProcessor.removeJavascriptInHref`: 문자, 숫자, 밑줄, 콜론이 아닌 문자를 모두 떼고 소문자로 만든 `href`가 `javascript:`로 시작하면 `#`으로 바꾼다(줄바꿈 등으로 숨긴 것도 막는다)
3. `LinkPostProcessor.checkReferrer`: 설정 `yona.noreferrer`가 켜져 있을 때 다른 호스트로 가는 링크에 `rel="noreferrer"`
4. `IssueLinkTransformer`: `/`로 시작하거나(또는 호스트가 이 사이트이고 문구가 주소와 같은) 링크에 `noreferrer`를 붙이고, 주소가 `.../이슈/번호`이면 읽을 수 있는 이슈에 한해 문구를 `#번호.제목[#조각]`으로 바꾸고 `issueLink` 클래스와 `issue-state <상태>` 배지를 붙인다
5. OWASP 정화, 결과 캐시 (여기까지가 캐시 대상)
6. `AutoLinkRenderer`: 캐시 바깥에서 매번 수행

`renderFileInReadme`은 앞에 상대 경로 변환(`RelativeLinkRewriter.forReadme`), `renderFileInCodeBrowser`는 이미지 경로만 변환(`forCodeBrowser`)을 한다.

### 자동 링크 (`AutoLinkRenderer`)

`code`, `a` 요소 안의 글과 단어 문자(영문, 숫자, 밑줄)에 붙은 참조는 건드리지 않는다. 대상이 실제로 있을 때만 바꾼다. 아래 순서로 한 종류씩 HTML을 다시 해석하며 바꾼다.

| 순서 | 참조 | 변환 |
|---|---|---|
| 1 | `[@]소유자/프로젝트#번호`, `[@]소유자#번호`(소유자와 같은 이름의 프로젝트) | 이슈 링크. 문구 `접두어#번호.제목`(결함 R-15) + 상태 배지 |
| 2 | `#번호` | 이 프로젝트의 이슈 링크 |
| 3 | `소유자/프로젝트[@]SHA` | 커밋 링크, 문구 `접두어@짧은SHA` |
| 4 | `[@]SHA`(7~40자 소문자 16진수) | 이 프로젝트의 커밋 링크, 문구는 짧은 SHA(7자). 코드 메뉴가 꺼졌거나 Git이 아니면 바꾸지 않음 |
| 5 | `@이름` | 조직이면 `/organizations/이름` 링크(`org-link`), 사용자이면 `/로그인ID` 링크(`user-link`, 소개 팝오버에 아바타와 이름) |
| 5 | `@소유자/프로젝트` | 프로젝트 링크(`project-link`) |

사용자 링크의 표시 이름은 이름에서 `[`, `(` 앞까지이다. 언어가 비어 있으면 현재 사용자의 언어(없으면 `ko-KR`)로 판단하고 앞뒤 공백을 다듬는다. 영어 사용자에게는 영문 이름을 쓴다.

### 결과 캐시의 v1.6 동작과 결함

- 이미 보관된 결과가 있으면 **그 결과를 돌려주고**, 같은 원문을 다시 변환해 캐시를 새 결과로 바꾼다(`afterTouch`). 이슈 제목이 바뀌어도 같은 원문의 이슈 주소 링크는 한 번 늦게 반영된다(R-16).
- 프로젝트 문맥이 없는 `MarkdownRenderer.render`와 같은 캐시를 같은 키로 쓴다. 어느 쪽이 먼저 저장하느냐에 따라 같은 원문의 결과가 달라질 수 있다(R-16).
- 이슈 주소 링크 변환은 보는 사람의 권한과 이슈 제목에 의존하는데 캐시 대상 안에 있어 전역으로 공유된다(R-16).

이 동작은 v1.6을 그대로 따르라는 원칙에 따라 재현했다. 의도와 맞지 않다면 알려 주십시오.

### 아직 없는 부분

- 알림 메일의 마크다운(`NotificationMail`)은 알림 서브시스템을 옮길 때 이 렌더러를 쓴다.
