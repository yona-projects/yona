---
title: 표준 기능 리팩터링 중 동작이 달라지는 부분 (나중에 보고)
type: architecture
updated: 2026-11-10
---

# 동작이 달라지는 부분 (D-123 진행 중 모은 목록)

선생님 지시(2026-11-10): "동작이 달라지는 부분은 작업하지 말고 나중에 추가로 보고해줘." 표준 기능으로 바꾸면 동작이 달라지는 곳은 **바꾸지 않고** 여기에 모은다. 작업이 끝나면 한꺼번에 보고하고 승인받는다.

| # | 위치 | 표준 기능 후보 | 동작이 달라지는 점 | 상태 |
|---|---|---|---|---|
| 1 | `setInterval` 주기 조회 5곳(`CodeCommentBlock`, `code.Nohead`, `git.View`, `issue.View`, `Migration`) | 탭이 안 보일 때 멈추기(`visibilitychange`), 간격 늘리기 | 탭이 백그라운드일 때 서버 조회가 멈춘다 | 보류 |
| 2 | `keyCode === 229` 비교 3곳 | `isComposing`만 | Safari 등에서 한글 조합 중 Enter가 제출될 수 있다 | 유지(바꾸지 않음) |
| 3 | `esm/http.js`가 지원하지 않는 `$.ajax` 옵션: `xhr.abort()`, `async: false`, `jsonp`, `xhrFields` | - | 이 옵션을 쓰는 호출이 없음을 확인했다(쓰는 곳이 생기면 그때 지원) | 해당 없음 |
| 4 | `issue/view.html`의 관심 등록 주소 | - | 템플릿이 `&amp;`를 글자 그대로 JS 문자열에 넣어 `resource.id` 앞에 `amp;`가 붙은 주소로 요청한다(서버는 200을 돌려주지만 관심 등록이 실제로 바뀌는지 확인하지 않았다). v1.6부터 있던 모양이며 이번 리팩터링은 바꾸지 않았다 | 결함 아님: 서버가 `&amp;`가 붙은 주소도 정상 처리함(`WatchAndFavoriteTest` 117~119행) |
| 5 | `window.draftSavingTimeout`(`CommentForm`, `temporarySaveHandler`가 공유) | 공용 `debounce` | 두 파일이 같은 타이머 변수를 공유해 한쪽 호출이 다른 쪽 타이머를 지운다. 공용 `debounce`로 나누면 두 호출이 독립이 되어 저장 횟수가 달라진다 | 보류 |
| 6 | `esm/Files.js`의 붙여넣은 이미지 이름(`oFile.name = nSubmitId + ".png"`) | `new File([file], 새이름, {type})` | v1.6의 대입은 `File.name`이 읽기 전용이라 항상 무시되었다. 지금은 대입을 지워 같은 동작을 지킨다. 실제로 이름을 바꾸면 서버에 올라가는 파일 이름이 `image.png` 대신 제출 번호가 된다 | 결함 아님: 업로드 때 `Files.js` 110행이 `image.png`를 `제출번호.png`로 바꿔 보내므로 서버로 가는 이름은 정상 |
| 7 | 문서 준비 처리기(`$(() => ...)`)의 실행 순서 | - | 클래식 스크립트는 태그 위치에서 처리기를 등록했으나, ES 모듈은 문서 분석이 끝난 뒤 등록하므로 같은 페이지의 인라인 처리기보다 뒤에 돈다. 화면에서 순서에 기대는 곳이 있는지 시험과 화면 확인으로 보고 있다 | 감시 중 |
| 8 | 기반 3개 `common/yona.Common.js`, `yona.Csrf.js`, `yona.Htmx.js`를 ES 모듈로 옮기는 것 | ES 모듈 | `Common.js`는 `$yona`와 `yona` 전역을 만들어 모든 클래식 인라인 스크립트와 ES 모듈이 문서 분석 중에도 쓴다. `Csrf.js`는 `fetch`와 `XMLHttpRequest`를 가장 먼저 감싸야 하고 `Htmx.js`는 htmx 설정을 가장 먼저 적용해야 한다. 모듈은 지연 실행이라 이 순서가 깨져 동작이 달라질 수 있어 클래식으로 둔다 | 보류 |
| 9 | 템플릿의 인라인 스크립트 133블록 | `<script type="module">`과 `data-*` | 문서 분석 중에 바로 실행되는 인라인 스크립트가 많아 모듈로 바꾸면 실행 시점이 달라진다. 이번 리팩터링 범위 밖으로 두었다 | 보류 |
| 10 | 스크롤, 크기 변경 처리기 6곳(`code.Diff` 2, `issue.MassUpdate`, `code.SvnDiff` 2, `Elevator`는 이미 표준) | `IntersectionObserver`, `ResizeObserver`, CSS | 이 처리기들은 스크롤 위치와 요소 크기로 값을 계산해 화면(리뷰 목록 높이, 일괄 수정 막대, 미니맵)을 갱신한다. 관찰자로 바꾸면 갱신 시점과 횟수가 달라진다 | 보류 |
| 11 | 사용자 데이터를 문자열로 이어 붙인 HTML 약 10곳(`pullrequest.Reviewers`, `pullrequest.Meta`, `issue.Assginee`, `issue.Sharer`의 `'<div>' + result.name + '</div>'`, `ui/Select2.js`의 `itemObject.text`와 `text`, `issue.MassUpdate`의 `sCategory`, `code.Browser`의 이동 경로) | `<template>`, `textContent` | 이름과 분류 이름이 HTML로 해석된다. DOM 방식으로 바꾸면 글자로 보이게 되어 화면이 달라진다. 사용자 이름이나 라벨 이름에 `<`가 들어갈 수 있으면 **저장형 XSS 후보**이므로 별도로 확인해야 한다 | **확인 필요(보안 후보)** |
| 12 | 사용자 데이터가 없는 HTML 조각(`Pagination` 링크 4곳, `Dialog` 기본 단추, `Attachments` 목록 항목) | `<template>` | 같은 DOM이 나오지만 `.html(문자열)`로 넣는 구조를 바꿔야 해 얻는 것이 없고 위험만 있어 두었다 | 보류 |
| 13 | 날짜 계산: `Files.js`, `CommentAttachmentsUpdate.js`의 올리는 파일 식별 문자열, `moment(...).isValid()` 3곳(`issue.Write`, `issue.View`, `issue.List`), `moment().fromNow()`(`code.Browser`) | `Intl.DateTimeFormat`, `Intl.RelativeTimeFormat`, `Date` | 식별 문자열은 0을 채우지 않는 `초밀리초-연-월-일-시-분` 모양이라 형식을 바꾸면 서버에 가는 값이 달라진다. `moment`의 마감일 검사는 `moment`의 해석 규칙이라 `Date`로 바꾸면 받아들이는 입력이 달라진다. `fromNow`는 언어별 문구가 달라진다 | 보류 |
| 14 | `Usermenu.js`: 목록을 읽어 온 뒤 `afterUsermenuLoaded()`를 다시 부름 | 처리기를 한 번만 붙임 | v1.6부터 처리기가 두 번 붙어 즐겨찾기 별을 한 번 눌러도 요청이 두 번 가고, 두 번 토글되어 결과가 상쇄될 수 있다. v1.6 결함이므로 그대로 두고 `usermenu.test.mjs`에 고정했다 | **수정함**(처리기를 붙이기 전에 같은 이름표를 떼어 한 번만 붙임, `usermenu.test.mjs`) |

