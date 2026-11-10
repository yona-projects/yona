---
title: 표준 기능으로 코드 리팩터링 계획
type: architecture
updated: 2026-11-10
---

# 표준 기능으로 코드 리팩터링 계획 (D-123, 선생님 지시 2026-11-10)

지시: **"가능하면 표준 기능으로 코드와 최대한 동일한 로직을 갖추도록 리팩터링하고, ES 모듈화를 하는 경우 템플릿에는 `<script type="module">`로 불러온다."** 원칙은 동작을 바꾸지 않는 것이다. 파일마다 특성 시험(옛 구현에서 먼저 통과)을 만든 뒤 바꾼다(S-10, pageslide, `jquery.form`, 소규모 lib와 같은 방식).

## 현황 (2026-11-10 정규식 측정)
| 패턴 | 곳 | 판단 |
|---|---|---|
| `keyCode` 비교 | 9곳 | 이미 `event.key`다. 남은 것은 한글 조합(IME) 보호용 `keyCode === 229`로, 표준 `isComposing`만으로 Safari와 일부 브라우저가 걸러지지 않아 일부러 둔다. **할 일 없음** |
| `$.ajax`, `$.get`, `$.post`, `$.getJSON` | 스크립트 56곳(30개 파일) + 템플릿 인라인 20곳 | `fetch`로 바꿀 수 있다. 콜백 모양(`success`, `error`, `dataType`)이 달라 공용 `yona.Http`(이전 화면 `http()`와 `$yona.sendForm`이 쓰는 방식) 하나를 만들고 파일마다 옮긴다 |
| 콜백 연결(`.done/.fail/.always`, `success:`) | 94곳(36개 파일) | 위 `$.ajax` 이전과 함께 `async/await`로 |
| 직접 만든 지연 호출(`clearTimeout`) | 9곳 | 일부는 지연 호출이 아니라 상태 타이머라(`window.draftSavingTimeout`가 두 파일에 공유됨) 의미를 확인하고 공용 `debounce`로 모을 수 있는 것만 모은다 |
| 주기 조회(`setInterval`) | 5곳 | 동작을 바꾸지 않는다(탭이 안 보일 때 멈추는 것은 동작 변경이라 별도 승인) |
| 스크롤, 크기 변경 처리기 | 6곳 | `IntersectionObserver`, `ResizeObserver`, CSS 후보. 동작이 같은 곳만 |
| 문자열 이어 붙인 HTML | 6곳 | `<template>`, `textContent` |
| 날짜 직접 계산 | 13곳 | `Intl.DateTimeFormat` 등. 출력 글자가 같아야 한다 |

## 단계
1. **(완료)** 소규모 lib 교체와 문법 현대화(D-122, A-438)
2. **(완료)** ES 모듈 뼈대와 모듈 3개(`esm/ui/Elevator.js`, `Favicon.js`, `FormValidator.js`). 템플릿은 진입점 `<script type="module">` 하나(`esm-skeleton.md`)
3. **(완료)** 공용 `esm/http.js`(fetch 래퍼, jQuery와 같은 요청과 결과를 내는지 30개 입력으로 비교)를 만들고 `$.ajax`, `$.get`, `$.post`, `$.getJSON` 76곳(44개 파일, 템플릿 인라인 포함)을 `yona.http.*`로 옮겼다. 콜백을 `await`로 바꾸는 일은 4단계에서 파일을 모듈로 옮길 때 함께 한다
4. **(진행 중)** 화면별 스크립트를 ES 모듈로 옮긴다. 묶음은 아래 표.

| 묶음 | 대상 | 상태 |
|---|---|---|
| 4-1 | `service/` 네임스페이스형 26개(`board.*`, `code.*`, `git.View`, `issue.MassUpdate`, `issue.Write`, `milestone.*`, `organization.Member/New/Setting`, `project.*`, `site.MassMail`, `user.*`) | 완료 |
| 4-2 | `service/` 네임스페이스형 11개(`git.Write`, `organization.Global/View`, `project.Global/Webhook/ChangeVCS`, `resetPassword`, `issue.LabelEditor/List/View`, `review.List`) | 완료 |
| 4-3 | `service/` 전역 함수형과 즉시 실행형 10개(`Migration`, `detectChange`, `issue.Assginee`, `issue.Sharer`, `pullrequest.Meta`, `pullrequest.Reviewers`, `review.CommentEdit`, `showSubtask`, `temporarySaveHandler`, `twoColumnMode`) | 완료(`service/` 폴더가 비었다). 전역 함수는 `register`로 같은 이름의 전역에 등록하고, `temporarySaveHandler`는 번들에서 빼 진입점 `esm/index.js`가 불러온다. 이전 화면은 28개 관찰값 차이 0건 |
| 4-4 | `common/` 34개(번들 3개가 불러오는 파일 포함) | 남음 |

(원래 문장) 화면별 스크립트를 한 파일씩 ES 모듈로 옮긴다. 시험이 없는 파일은 특성 시험을 먼저 만든다. 옮길 때 그 화면 템플릿의 `<script>` 태그는 `type="module"`로 바꾼다
5. 직접 만든 지연 호출, 스크롤 처리기, 문자열 HTML, 날짜 계산을 파일을 열 때 함께 정리한다

각 단계는 시험 통과와 실제 앱 확인 뒤에 커밋하고, 달라진 구조는 `decisions.md`에 적는다.

동작이 달라지는 곳은 건드리지 않고 `refactor-deviations.md`에 모아 마지막에 보고한다.
