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
| 4 | `issue/view.html`의 관심 등록 주소 | - | 템플릿이 `&amp;`를 글자 그대로 JS 문자열에 넣어 `resource.id` 앞에 `amp;`가 붙은 주소로 요청한다(서버는 200을 돌려주지만 관심 등록이 실제로 바뀌는지 확인하지 않았다). v1.6부터 있던 모양이며 이번 리팩터링은 바꾸지 않았다 | 확인 필요 |
| 5 | `window.draftSavingTimeout`(`CommentForm`, `temporarySaveHandler`가 공유) | 공용 `debounce` | 두 파일이 같은 타이머 변수를 공유해 한쪽 호출이 다른 쪽 타이머를 지운다. 공용 `debounce`로 나누면 두 호출이 독립이 되어 저장 횟수가 달라진다 | 보류 |
