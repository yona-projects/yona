---
title: 표준 기능 리팩터링 중 동작이 달라지는 부분 (나중에 보고)
type: architecture
updated: 2026-11-10
---

# 동작이 달라지는 부분 (D-123 진행 중 모은 목록)

선생님 지시(2026-11-10): "동작이 달라지는 부분은 작업하지 말고 나중에 추가로 보고해줘." 표준 기능으로 바꾸면 동작이 달라지는 곳은 **바꾸지 않고** 여기에 모은다. 작업이 끝나면 한꺼번에 보고하고 승인받는다.

| # | 위치 | 표준 기능 후보 | 동작이 달라지는 점 | 상태 |
|---|---|---|---|---|
| 1 | `setInterval` 주기 조회 5곳(`CodeCommentBlock`, `code.Nohead`, `git.View`, `issue.View`, `Migration`) | 탭이 안 보일 때 멈추기(`visibilitychange`), 간격 늘리기 | 탭이 백그라운드일 때 서버 조회가 멈춘다 | **수용**(D-124): 탭이 백그라운드여도 주기 조회는 계속 돈다. 바꾸지 않고 문서로만 남긴다 |
| 2 | `keyCode === 229` 비교 3곳 | `isComposing`만 | Safari 등에서 한글 조합 중 Enter가 제출될 수 있다 | 유지(바꾸지 않음) |
| 3 | `esm/http.js`가 지원하지 않는 `$.ajax` 옵션: `xhr.abort()`, `async: false`, `jsonp`, `xhrFields` | - | 이 옵션을 쓰는 호출이 없음을 확인했다(쓰는 곳이 생기면 그때 지원) | 해당 없음 |
| 4 | `issue/view.html`의 관심 등록 주소 | - | 템플릿이 `&amp;`를 글자 그대로 JS 문자열에 넣어 `resource.id` 앞에 `amp;`가 붙은 주소로 요청한다(서버는 200을 돌려주지만 관심 등록이 실제로 바뀌는지 확인하지 않았다). v1.6부터 있던 모양이며 이번 리팩터링은 바꾸지 않았다 | 결함 아님: 서버가 `&amp;`가 붙은 주소도 정상 처리함(`WatchAndFavoriteTest` 117~119행) |
| 5 | `window.draftSavingTimeout`(`CommentForm`, `temporarySaveHandler`가 공유) | 공용 `debounce` | 두 파일이 같은 타이머 변수를 공유해 한쪽 호출이 다른 쪽 타이머를 지운다. 공용 `debounce`로 나누면 두 호출이 독립이 되어 저장 횟수가 달라진다 | **해결**(D-124): 입력란마다 타이머와 키를 따로 쓰고(수정 댓글은 댓글 번호를 키에 붙임), 댓글 제출 때와 1시간마다 모든 초안 타이머를 취소하며, 마지막 저장 뒤 1시간이 지나도 제출되지 않은 초안은 5분마다 점검해 지운다(시간 기록은 `yona.draftTimes`). `draft.test.mjs` |
| 6 | `esm/Files.js`의 붙여넣은 이미지 이름(`oFile.name = nSubmitId + ".png"`) | `new File([file], 새이름, {type})` | v1.6의 대입은 `File.name`이 읽기 전용이라 항상 무시되었다. 지금은 대입을 지워 같은 동작을 지킨다. 실제로 이름을 바꾸면 서버에 올라가는 파일 이름이 `image.png` 대신 제출 번호가 된다 | 결함 아님: 업로드 때 `Files.js` 110행이 `image.png`를 `제출번호.png`로 바꿔 보내므로 서버로 가는 이름은 정상 |
| 7 | 문서 준비 처리기(`$(() => ...)`)의 실행 순서 | - | 클래식 스크립트는 태그 위치에서 처리기를 등록했으나, ES 모듈은 문서 분석이 끝난 뒤 등록하므로 같은 페이지의 인라인 처리기보다 뒤에 돈다. 화면에서 순서에 기대는 곳이 있는지 시험과 화면 확인으로 보고 있다 | **수용**(D-124) |
| 8 | 기반 3개 `common/yona.Common.js`, `yona.Csrf.js`, `yona.Htmx.js`를 ES 모듈로 옮기는 것 | ES 모듈 | `Common.js`는 `$yona`와 `yona` 전역을 만들어 모든 클래식 인라인 스크립트와 ES 모듈이 문서 분석 중에도 쓴다. `Csrf.js`는 `fetch`와 `XMLHttpRequest`를 가장 먼저 감싸야 하고 `Htmx.js`는 htmx 설정을 가장 먼저 적용해야 한다. 모듈은 지연 실행이라 이 순서가 깨져 동작이 달라질 수 있어 클래식으로 둔다 | **현행 유지**(D-124): 나중에 리팩터링. "소스는 ES 모듈, 결과물은 클래식 번들" 방식이 후보 |
| 9 | 템플릿의 인라인 스크립트 133블록 | `<script type="module">`과 `data-*` | 문서 분석 중에 바로 실행되는 인라인 스크립트가 많아 모듈로 바꾸면 실행 시점이 달라진다. 이번 리팩터링 범위 밖으로 두었다 | **현행 유지**(D-124): 나중에 리팩터링. 화면 단위로 나눠 `data-*`/JSON 블록으로 옮기는 안이 후보 |
| 10 | 스크롤, 크기 변경 처리기 6곳 | `IntersectionObserver`, CSS `sticky`, `ResizeObserver` | `issue.MassUpdate`는 `IntersectionObserver`로, `code.Diff` 코드 영역 가로 스크롤의 스레드 고정은 CSS `sticky`로 바꿨다(실제 커밋 화면에서 6개 지점의 위치가 같고 픽셀 차이 0.07%). `code.Diff`와 `code.SvnDiff`의 미니맵은 스크롤을 `requestAnimationFrame`으로 프레임당 한 번만 반영하고 `passive`로 등록하며, 문서 크기 변화는 `ResizeObserver`가, 창 높이만 바뀌는 경우는 `resize`가 받는다. 미니맵은 직접 만든 SVN 프로젝트(검증용 DB의 `admin/svnproj`)의 커밋 화면에서 Chromium과 Firefox 12건 모두 확인했다. `code.Diff`의 리뷰 목록 높이는 선생님 결정으로 **CSS로 바꿨다**(`.review-container`에 `max-height: calc(100vh - 20px)`와 세로 flex, `.review-list`가 남는 높이를 채우고 안쪽에서 스크롤). 달라진 점은 ① 맨 위에서 패널 위에 휠을 돌리면 페이지가 먼저 움직이지 않고 목록이 바로 안쪽에서 움직이는 것, ② 맨 아래에서 패널이 줄어들지 않고 위로 밀려 올라가는 것(약 37px)이다. 비교 영상과 스크린샷은 `review-list-height/`에 보관했다 | **해결**(CSS 방식 채택, 2026-11-10) |
| 11 | 사용자 데이터를 문자열로 이어 붙인 HTML 약 10곳(`pullrequest.Reviewers`, `pullrequest.Meta`, `issue.Assginee`, `issue.Sharer`의 `'<div>' + result.name + '</div>'`, `ui/Select2.js`의 `itemObject.text`와 `text`, `issue.MassUpdate`의 `sCategory`, `code.Browser`의 이동 경로) | `<template>`, `textContent` | 이름과 분류 이름이 HTML로 해석된다. DOM 방식으로 바꾸면 글자로 보이게 되어 화면이 달라진다. 사용자 이름이나 라벨 이름에 `<`가 들어갈 수 있으면 **저장형 XSS 후보**이므로 별도로 확인해야 한다 | **확인하고 고쳤다.** 저장형 XSS가 실제로 있었다: 분류 이름(`issue.MassUpdate`, 큰따옴표 없는 HTML이 이슈 체크 때 실행), 파일 이름과 주소 해시(`code.Browser`, 속성과 `<img>` 주입)를 이스케이프하거나 속성으로 넣게 바꿨고 `issue.Sharer`, `Assginee`, `pullrequest.Meta/Reviewers`, `Select2`의 이름도 이스케이프한다. 사용자 이름(가입, 프로필)은 서버가 태그를 지우고 라벨 이름은 선택 상자에서 이스케이프되어 원래 안전했다. `$yona.tmpl`은 값을 이스케이프하지 않는 v1.6의 단순 치환이라 사용자 데이터는 `$yona.escapeHtml`을 거친다 |
| 12 | 사용자 데이터가 없는 HTML 조각(`Pagination` 링크 4곳, `Dialog` 기본 단추, `Attachments` 목록 항목) | `<template>` | 같은 DOM이 나오지만 `.html(문자열)`로 넣는 구조를 바꿔야 해 얻는 것이 없고 위험만 있어 두었다 | **수용**(D-124): 바꾸지 않는다 |
| 13 | 날짜 계산: `moment(값).isValid()` 3곳(`issue.Write`, `issue.View`, `issue.List`)과 `moment().fromNow()`(`code.Browser`) | `esm/dates.js`(moment 2.31.0 규칙을 그대로 옮김) | 같은 결과를 내도록 구현해 **동작 차이를 없앴다.** 날짜 검증은 ISO 8601(확장, 기본, 주, 연중 일수)과 RFC 2822의 범위 검사를, 상대 시각은 임계값(44/45/22/26/11)과 달력 기준 개월 차이, en·ko·ja·ru·uz 문구를 옮겼다. `dates.test.mjs`가 실제 moment와 날짜 검증 약 1만 건, 상대 시각 약 39만 건(4개 시간대, 6개 언어, 임계값 경계와 DST, 월말)을 비교해 차이가 0건이다. 올리는 파일 식별 문자열(`Files.js`, `CommentAttachmentsUpdate.js`)은 바꾸지 않았다 | **해결**(moment는 어느 화면도 부르지 않음. 파일은 시험 기준으로 남김, 삭제는 확인 대기) |
| 14 | `Usermenu.js`: 목록을 읽어 온 뒤 `afterUsermenuLoaded()`를 다시 부름 | 처리기를 한 번만 붙임 | v1.6부터 처리기가 두 번 붙어 즐겨찾기 별을 한 번 눌러도 요청이 두 번 가고, 두 번 토글되어 결과가 상쇄될 수 있다. v1.6 결함이므로 그대로 두고 `usermenu.test.mjs`에 고정했다 | **수정함**(처리기를 붙이기 전에 같은 이름표를 떼어 한 번만 붙임, `usermenu.test.mjs`) |

## 화면에서 보이는 정도 (2026-11-10 측정)

| # | 선생님 화면에서 달라지는가 | 근거 |
|---|---|---|
| 1 | 켜져 있는 탭에서는 같음. 탭이 백그라운드일 때만 자동 갱신이 멈춘다 | 코드 검토 |
| 2 | 바꾸지 않았으므로 없음 | |
| 5 | 보이지 않음. 초안 저장 횟수만 달라진다 | 코드 검토 |
| 7 | 현재 없음. 56쪽 방문과 상호작용에서 오류 0건 | smoke 실행 |
| 8, 9 | 바꾸지 않았으므로 없음 | |
| 10 | 바꾸면 리뷰 목록 높이, 일괄 수정 막대, 미니맵 갱신 시점이 미세하게 달라질 수 있음 | 코드 검토 |
| 11 | 확인한 경로는 없음(위 참조) | **확인하고 고쳤다.** 저장형 XSS가 실제로 있었다: 분류 이름(`issue.MassUpdate`, 큰따옴표 없는 HTML이 이슈 체크 때 실행), 파일 이름과 주소 해시(`code.Browser`, 속성과 `<img>` 주입)를 이스케이프하거나 속성으로 넣게 바꿨고 `issue.Sharer`, `Assginee`, `pullrequest.Meta/Reviewers`, `Select2`의 이름도 이스케이프한다. 사용자 이름(가입, 프로필)은 서버가 태그를 지우고 라벨 이름은 선택 상자에서 이스케이프되어 원래 안전했다. `$yona.tmpl`은 값을 이스케이프하지 않는 v1.6의 단순 치환이라 사용자 데이터는 `$yona.escapeHtml`을 거친다 |
| 12 | 없음 | |
| 13 | 날짜 계산: `moment(값).isValid()` 3곳(`issue.Write`, `issue.View`, `issue.List`)과 `moment().fromNow()`(`code.Browser`) | `esm/dates.js`(moment 2.31.0 규칙을 그대로 옮김) | 같은 결과를 내도록 구현해 **동작 차이를 없앴다.** 날짜 검증은 ISO 8601(확장, 기본, 주, 연중 일수)과 RFC 2822의 범위 검사를, 상대 시각은 임계값(44/45/22/26/11)과 달력 기준 개월 차이, en·ko·ja·ru·uz 문구를 옮겼다. `dates.test.mjs`가 실제 moment와 날짜 검증 약 1만 건, 상대 시각 약 39만 건(4개 시간대, 6개 언어, 임계값 경계와 DST, 월말)을 비교해 차이가 0건이다. 올리는 파일 식별 문자열(`Files.js`, `CommentAttachmentsUpdate.js`)은 바꾸지 않았다 | **해결**(moment는 어느 화면도 부르지 않음. 파일은 시험 기준으로 남김, 삭제는 확인 대기) |
| 15 | `code.Diff`, `code.SvnDiff`의 `$(document.body).scrollTop()` 3곳 | `$(window).scrollTop()` | v1.6부터 표준 모드 문서에서 `body.scrollTop`이 항상 0이라 미니맵의 현재 위치 표시가 움직이지 않았고, 댓글 상자가 화면 밖인지 판단이 틀렸다(Chromium, Firefox 모두 확인) | **수정함**(`svndiff.test.mjs`) |
| 16 | `code.SvnDiff`, `code.Diff` 미니맵의 댓글 위치 링크 | `$yona.render`가 돌려주는 jQuery 요소를 요소로 넣음 | 처음 포팅 때부터 링크 문자열을 `[object Object]`로 이어 붙여 미니맵에 댓글 위치 표시가 하나도 나오지 않았다. 직접 만든 SVN 프로젝트에서 발견했고, 링크 9개(`<a href="#comment-1" style="top:77px; height:13px;">` 등)가 나오는 것을 두 브라우저에서 확인했다 | **수정함**(`svndiff.test.mjs`) |

