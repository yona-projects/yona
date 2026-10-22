---
title: 서버 측 마크다운 렌더링
type: web
sources: [app/utils/Markdown.java:36-330, public/javascripts/lib/marked.js]
updated: 2026-10-22
---

# 서버 측 마크다운 렌더링

결정 근거는 [../decisions.md](../decisions.md) D-17이다.

## 구성

| 구성 요소 | 역할 |
|---|---|
| `MarkdownRenderer` (운영) | commonmark-java 0.31.0 + GFM 확장(표, 취소선, 자동 링크, 작업 목록, 제목 앵커)으로 변환, 줄바꿈은 `<br>`, v1.6 마크업 맞춤 후처리, OWASP 정화 |
| `MarkedReference` (테스트 전용) | GraalJS에서 v1.6의 `marked.js`를 같은 옵션(`gfm`, `tables`, `breaks`, `smartLists`)으로 실행하는 기준 구현 |
| `MarkdownDifferenceTest` | 20개 입력 모음을 양쪽에 넣어 속성 순서와 공백을 무시하고 비교한다. 다르면 `build/markdown-differences.txt`를 남기고 실패한다 |

## v1.6 마크업 맞춤

v1.6의 `public/javascripts/lib/marked.js`는 원본이 아니라 커스터마이즈된 판이다. 후처리로 다음을 재현한다.

- 제목: `id`에 `yb-header-` 접두어를 붙이고, 끝에 `<a href="#id" class="head-anchor">#</a>`를 덧붙인다.
- 작업 목록: `li`에 `task-list-item`, 체크박스에 `task-list-item-checkbox` 클래스를 주고 `disabled`는 붙이지 않는다.

## 정화 정책

v1.6 `Markdown.sanitizerPolicy`와 같다. OWASP `FORMATTING`, `IMAGES`, `STYLES`, `TABLES`, `BLOCKS`에 `video`, `source`, `input`, `iframe` 등을 더하고 프로토콜은 `http`, `https`, `mailto`, `file`, `zpl`만 허용한다.

## 비교 결과 (2026-10-22)

20개 입력(제목, 강조, 줄바꿈, 목록, 작업 목록, 표, 코드, 인용, 링크, 이미지, 원시 HTML, 자바스크립트 링크, 한글 제목 등) 모두 일치한다. 입력 모음이 아직 작으므로 새로운 마크다운 사용처를 이식할 때 입력을 더하고, 차이가 발견되면 이 문서에 기록해 선생님의 승인을 받는다.

## 아직 이식하지 않은 부분

- 프로젝트 문맥 후처리: `removeJavascriptInHref`(정화기가 막아 주지만 `#` 치환은 아직 없음), `checkReferrer`(`application.noreferrer`), `transformIssueLink`(이슈 링크를 `#번호.제목`과 상태 배지로), `AutoLinkRenderer`(`#이슈`, SHA, `@사용자`). 이슈와 저장소가 이식되는 3단계 이후에 추가한다.
- 렌더 캐시(`CacheStore.renderedMarkdown`), 5초 제한(`Thread.stop`). commonmark-java는 정규식 폭주가 없어 5초 제한은 필요하지 않다고 판단하지만, 확정은 선생님의 결정이 필요하다.
- `renderFileInReadme`, `renderFileInCodeBrowser`의 상대 경로 치환.
