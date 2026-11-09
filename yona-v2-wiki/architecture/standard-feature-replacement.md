---
title: 직접 만든 JS를 표준 브라우저 기능으로 바꾸는 검토
type: architecture
sources:
  - ../src/main/resources/static/assets/javascripts/common/
  - ../src/main/resources/static/assets/javascripts/service/
  - decisions.md
  - js-upgrade-plan.md
updated: 2026-11-09
---

# 직접 만든 JS를 표준 브라우저 기능으로 바꾸는 검토

선생님 요청(2026-11-09)으로 `common/`, `service/`, 팀 플러그인(`jquery.requestAs.js`, `filetype.js`, `bootstrap-better-typeahead.js`)이 표준 브라우저 기능으로 바뀔 수 있는지 코드를 읽고 세어 확인했다(`Migration.js` 제외). **이 문서는 판단을 적어 두는 곳이고, 한 항목씩 선생님과 함께 진행한다.** 진행하면 항목의 상태를 고치고 결정은 `decisions.md`에 적는다.

## 부품별 판단 (상태: 미착수는 아직 손대지 않음)
| 번호 | 직접 만든 것 | 표준 대체 | 판단 | 상태 |
|---|---|---|---|---|
| S-01 | `yona.Clipboard.js` | `navigator.clipboard.writeText` | 이미 표준을 쓰고 보안 연결이 아닐 때만 `execCommand` 대체 경로를 쓴다(그 경로는 남긴다). 이슈 보기의 메일 복사 단추가 쓰던 clipboard.js를 이 도우미로 바꿔 번들에서 뺐다. 모달 안에서는 대체 경로의 입력 칸을 모달에 붙여야 한다(`A-413`). 동작하지 않던 `data-clipboard-target`은 지웠고 `data-clipboard-text`는 `data-copy-text`로 이름을 바꿨다(`A-414`, `A-415`) | **완료**(2026-11-09, A-413, A-414, A-415) |
| S-02 | `yona.Interval.js`(196줄, 호출 1곳 `git.View`) | `setInterval` + `visibilitychange` | 가능. 지금은 사용자 입력이 멈추면 타이머를 멈춘다(탭이 가려지면 멈추는 표준 방식과 의미가 다름) | **완료**(2026-11-09, A-416, A안) |
| S-03 | `yona.KeyControl.js`(88줄, 탭 들여쓰기) | `textarea.setRangeText()` | 가능. 값을 통째로 바꿔 되돌리기 기록이 끊겼다(A-419에서 고침) | **완료**(2026-11-09, A-419: D-105로 한 번 닫았다가 서식 단추와의 되돌리기 불일치로 다시 열어 `execCommand('insertText')`로 바꿈, 실패하면 예전 방식) |
| S-04 | `yona.Csrf.js` | 없음 | 유지(XHR, fetch, 폼 제출에 토큰을 싣는 표준 기능이 없다). 이 파일이 `XMLHttpRequest`와 `fetch`를 전역으로 가로채 토큰을 싣기 때문에 네이티브 XHR도 토큰이 자동으로 실린다(A-421) | 유지 |
| S-05 | `yona.ShortcutKey.js`와 `keyCode`, `which` 33곳(17개 파일) | `KeyboardEvent.key` | 가능. 한글 입력기에서 `keyCode` 229, `key`의 `Process`, `isComposing`을 함께 봐야 한다 | **완료**(2026-11-09, A-420, A안: 도우미 `isEnterKey` 등 + 호출 교체 + `ShortcutKey`를 `key` 기반으로) |
| S-06 | `yona.Files.js`(783줄) 업로드 | `fetch` | 부분. `fetch`는 업로드 진행률이 없어 XHR이 필요하다. `FormData` 없는 브라우저용 `$.ajaxForm` 대체 경로(`_uploadFileForm`)와 IE 확인(`trident`)은 지울 수 있다 | **완료**(2026-11-09, A-421: 옛 폼 경로 삭제 + 업로드를 네이티브 XHR로. 목록 조회와 삭제는 S-14. Firefox 붙여넣기 업로드는 R-140 논의 사항으로 남김) |
| S-07 | `jquery.requestAs.js`(303줄, 호출 7곳) | `fetch` + `<dialog>` 확인창 | 가능, 중간 규모 | **완료**(2026-11-09, A-422, A′안: `yona.Request` 함수 하나 + 표준 DOM 이벤트. 선언형 연결 층까지 없애는 방식은 후속 논의) |
| S-08 | `yona.ui.Dialog`, `Toast`, `Dropdown` | Bootstrap 5 컴포넌트 또는 `<dialog>` | 가능. 얇은 껍데기이고 호출은 2~3곳 | **완료**(2026-11-09, A-423, A안: Toast 네이티브 재작성, Dropdown 휠 막기를 CSS로, Dialog 유지. Firefox 휠은 R-145 논의 사항으로 남김) |
| S-09 | `yona.ui.Calendar`(Pikaday 래퍼) | vanilla-calendar-pro 3.4.0(계약과 위치는 Pikaday와 같게, `D-107`, `A-424`) | 구현 완료, 모양은 달라짐(월과 연 선택이 선택 상자에서 목록으로, 크기와 색) | 완료(2026-11-09, 선생님 확인) |
| S-10 | `yona.ui.Typeahead`와 better-typeahead | **BS2 플러그인 없이 같은 API로 다시 씀**(`D-109`, `A-426`. `<datalist>`는 아바타와 값 바꾸기를 못 해 제외) | 완료. 동작은 옛 구현과 같게 시험으로 고정, 모양은 그대로(BS5 `dropdown-menu`) | **완료**(2026-11-09, D-109, A-426, 선생님 확인) |
| S-11 | `lib/filetype.js`(1,078줄) | 없음 | 유지(파일 이름 → 아이콘 표) | 유지 |
| S-12 | `yona.Common.js`의 `xssClean` | Sanitizer API(`Element.setHTML`) | **표준 우선으로 바꿈**(지원하는 브라우저는 표준, Safari 등은 옛 필터. 금지 요소 목록과 변이 XSS 요소를 더 걷어냄) | **완료**(2026-11-09, D-108, A-425) |
| S-13 | jQuery 정적 유틸 32곳(`$.each`, `$.extend`, `$.proxy`, `$.inArray`, `$.map` 2, `$.isEmptyObject`, `$.parseHTML` 2) | `forEach`, `Object.entries`, `Object.assign`, `bind`, `includes`, `map`, `Object.keys`, `<template>`, `DOMParser` | **완료**: 값 비교 시험을 먼저 두고 바꿨다. 다름이 있는 곳은 "S-13 동작 확인 목록"으로 나중에 확인(선생님 지시) | **완료**(2026-11-09, D-110, A-427, 동작 확인 대기) |
| S-14 | jQuery DOM과 이벤트와 `.data()`와 `$.ajax`(약 1,300곳) | `classList`, `hidden`, `closest`, `addEventListener`, `dataset`, `fetch` | jQuery 전체 제거에 해당하는 큰 일. `.data()`는 값 자동 변환, `$.ajax`는 4xx 처리와 `X-Requested-With`, `Accept` 헤더 차이에 주의 | 별도 단계 |

사용 횟수(`Migration.js` 제외, 파일 수): `$.ajax` 계열 59(34), `$.each` 60(28), `.show/.hide/.toggle` 122(36), 클래스 조작 100(32), `.on/.off/.bind` 237(64), `.data` 217(36), 탐색(`closest`, `find` 등) 310(51), `keyCode/which` 33(17), `setInterval` 8(5), `alert/confirm` 직접 30(13).

## 확인 중 발견한 것
| 번호 | 내용 | 처리 |
|---|---|---|
| F-01 | `common/yona.Sha1.js`(js-sha1 0.6.0)를 `issue/view`와 `board/view`가 불러오지만 어디서도 부르지 않는다(본문 체크섬은 서버가 계산하고 v1.6도 같았다). 표준 `crypto.subtle.digest('SHA-1')`는 비동기이고 보안 연결에서만 동작해 그대로 대체되지는 않는다 | **삭제함**(2026-11-09, D-111, A-428) |
| F-02 | `yona.ui.Tabs.js`는 복원 조건이 `welLink.data("toggle" == "tab")`(항상 거짓)라 선택한 탭을 저장만 하고 복원하지 않는다 | v1.6 결함, `R-139`. **고침**(D-111, A-428) |
| F-03 | `yona.Files.js`의 붙여넣기 업로드가 Firefox를 제외하는 확인이 `indexOf("FireFox")`인데 실제 브라우저 문자열은 `Firefox`라 제외가 동작하지 않는다 | v1.6 결함으로 보임, `R-140`. **낡은 제외 조건을 없애고 Firefox에서도 켬**(D-111, A-428, 실제 Firefox 붙여넣기 확인 완료) |

## 이미 한 것
- S-01의 전신: Flash 기반 zclip을 걷어내고 이 도우미를 만들었다(`D-91`, `A-394`, 2026-11-07).
- `canvas.toBlob` 폴리필 삭제(`A-412`), 지워지는 jQuery API의 표준 JS 대체(`D-103`, `A-410`)는 이 검토 이전에 끝났다.

## S-02 상세 (`yona.Interval.js`, 2026-11-09 코드 확인)
- **무엇인가**: `setInterval`을 감싼 타이머 관리자(196줄, Yobi 2013). `set(함수, 간격)`과 `clear(번호)`를 제공하고, **사용자 입력(scroll, mousedown, mousemove, keypress)이 60초 동안 없으면 등록된 타이머를 모두 멈추고, 입력이 다시 오면 재개한다.** `beforeunload`에서 정리한다.
- **쓰는 곳은 한 곳**: 풀 리퀘스트 보기(`pullrequest/view.html`이 로드, `yona.git.View.js`가 사용). 10초마다 서버에서 풀 리퀘스트 상태 조각(HTML)을 받아 달라졌을 때만 갈아 끼우고 충돌 여부에 따라 수락 단추를 보이거나 숨긴다. 타이머는 화면을 열 때 한 번 만들고 지우지 않는다.
- **표준과의 차이**: 표준 `visibilitychange`(탭이 가려지면 멈춤)와 의미가 다르다. 지금은 탭이 보이는 채로 사용자가 60초 이상 손을 떼면 멈추고, 가려진 탭이라도 입력이 있었다면 계속 돈다. 대체 방법은 (A) 같은 "60초 입력 없음" 규칙을 `git.View` 안의 20줄 정도로 옮기기(v1.6과 같은 동작), (B) `visibilitychange`로 바꾸기(동작이 달라짐: 보이는 채 방치된 탭도 계속 갱신), (C) 두 조건 함께(입력이 없거나 탭이 가려지면 멈춤).
- **v1.6부터의 결함(영향 없음)**: 재개할 때 새 타이머 번호를 만들어 옛 번호를 지우므로, 번호를 저장해 둔 호출자가 나중에 `clear(옛 번호)`를 부르면 새 타이머가 남는다. `git.View`는 `clear`를 처음 만들 때만(저장된 번호가 없을 때) 부르는 구조라 실제로는 드러나지 않는다.
- **결정이 필요한 것**: A, B, C 중 선택. 권장은 v1.6과 같은 동작을 지키는 A이다.

## S-05 조사 (`keyCode`, `which` → `KeyboardEvent.key`, 2026-11-09)
`keyCode`와 `which`는 비권장 속성이지만 모든 브라우저가 아직 지원한다. 앱 스크립트와 템플릿에서 실제 사용처를 다시 세었다(33곳은 vendor 포함 값이었다).

### 앱 스크립트 (고칠 수 있는 곳, 14개 파일 약 20곳)
| 분류 | 파일 | 이벤트 | 보는 값 |
|---|---|---|---|
| Enter | `yona.Pagination.js` 220 | keydown | `which === 13`, 페이지 번호 입력 후 이동 |
| Enter | `yona.user.View.js` 127 | keypress | `keyCode === 13`, 조회 일수 입력 후 이동 |
| Enter | `yona.site.MassMail.js` 160 | keypress | `keyCode == 13`, 프로젝트 라벨 추가 |
| Enter | `yona.issue.LabelEditor.js` 155 | keypress | `(keyCode \|\| which) === 13`, 제출 막기 |
| Enter | `yona.issue.Write.js` 27, `yona.board.Write.js` 27, `yona.milestone.Write.js` 28 | keydown | 제목 Enter → 본문으로 포커스 |
| Enter 제외 | `yona.git.Write.js` 90 | keyup | `keyCode !== 13`이면 "사용자가 입력했다" 표시 |
| Ctrl/Meta+Enter | `yona.SubComment.js` 18 | keypress | `(e.metaKey \|\| e.Control)`. `e.Control`은 없는 속성이라 Meta(Cmd)만 동작한다(v1.6 결함) |
| Esc | `yona.CommentForm.js` 123, `yona.SubComment.js` 24, `yona.Usermenu.js` 94, `migration-layout.html` 131 | keydown, keyup | `27` |
| Backspace | `yona.Usermenu.js` 84 | keyup | `which === 8` |
| 문자 | `yona.Usermenu.js` 196 | keypress | `which === 102(f) \|\| 12601(ㄹ)`, 사이드바 즐겨찾기 열기 |
| 단축키 표 | `yona.ShortcutKey.js` 152 | keydown | `keyCode` → 이름 표(A~Z, 0~9, 방향키, F1~F12, 기호 등). 실제 등록된 단축키는 단일 문자(N, L, E, U, A 등), LEFT, RIGHT, CTRL+ENTER, CTRL+A, CTRL+SHIFT+ENTER뿐이다 |
| 마우스 | `yona.CodeCommentBlock.js` 82, `yona.code.Diff.js` 418 | mousedown 계열 | `which === 1`(왼쪽 단추) → `button === 0` |
| 마우스 | `yona.code.Diff.js` 590 | mousemove | `buttons`가 있으면 `buttons !== 0`, 없으면 `which !== 0`. 지금 브라우저는 모두 `buttons`가 있어 뒤쪽 갈래는 죽은 코드 |
| 의도적 유지 | `yona.Mention.js` 216 | keydown | 조합 중 판단(`isComposing`, `keyCode === 229`). Safari 보호막이라 이미 두 값을 함께 본다 |

### vendor (건드리지 않음)
`bootstrap-typeahead.js`(3), `bootstrap-better-typeahead.js`(4), `select2.js`(6), `bootstrap-switch.js`(1), `jquery.pageslide.js`(1), `viewer.js`(1), `jquery.requestAs.js`(1), `vendor.js`(8), jQuery 자체(1). 라이브러리를 올리거나 지울 때 함께 사라진다.

### 쟁점
1. **한글 입력기**: 조합 중 Enter를 누르면 Chrome은 keydown의 `keyCode`가 229라 `=== 13` 검사가 이미 거짓이다. 그러나 Safari는 조합 중에도 `key === "Enter"`(`isComposing`은 참)로 보낸다. 그래서 `key === "Enter"`로만 바꾸면 Safari에서 조합 확정 Enter가 "이동"이나 "제출"로 잘못 처리된다. 바꿀 때는 `e.key === "Enter" && !e.isComposing && e.keyCode !== 229`처럼 조합 중을 함께 걸러야 한다. 이 조건은 Chrome과 Firefox의 지금 동작과 같고 Safari의 결함만 막는다.
2. **jQuery 이벤트**: jQuery 3.7.1 이벤트에는 `key`가 복사되지만 `isComposing`은 없어 `e.originalEvent.isComposing`을 읽어야 한다.
3. **`ShortcutKey`의 표**: `keyCode`는 글자 키에서 배열과 무관하게 라틴 글자를 주고 한글 입력기에서는 229라 단축키가 무시된다. `key`로 바꾸면 라틴 배열에서는 같은 글자(대소문자는 올림)이고, 한글 입력기에서는 `Process`나 한글 글자라 표에 없어 역시 무시된다. 그러나 `Shift`와 함께 쓰는 숫자와 기호는 `key`가 `!`, `?`처럼 달라진다(등록된 단축키에는 없다). 기호 키는 표에서 빼거나 `code`로 보조해야 한다.
4. **`keypress`**: 이 이벤트 자체가 비권장이다(`keypress`를 쓰는 곳 5곳: Usermenu, SubComment, MassMail, user.View, LabelEditor). `keydown`으로 바꾸면 Enter의 의미는 같지만 입력기 동작이 달라질 수 있어 따로 확인해야 한다.
5. **v1.6 결함**(그대로 따른다): `SubComment.js`의 `e.Control`.

### 선택지
- **A. 얇은 도우미 하나 + 호출 교체**: `$yona`에 `isEnter(e)`, `isEscape(e)`처럼 조합 중을 걸러 주는 함수를 두고 약 20곳을 바꾼다. 마우스 `which`는 `button`으로 바꾼다. `ShortcutKey`는 `key` 기반 표로 바꾼다. 권장.
- **B. 호출만 `e.key`로 바꾸고 도우미는 두지 않는다**: 코드는 짧지만 조합 중 보호를 곳곳에 중복한다.
- **C. 그대로 둔다**: 모든 브라우저가 아직 지원하고 비용이 없다. 다만 Safari의 조합 중 Enter 문제는 남는다.

## S-06 조사 (`yona.Files.js` 업로드, 2026-11-09)
`common/yona.Files.js`(782줄)는 첨부 업로더(버튼, 끌어놓기, 붙여넣기)이다. 업로드 경로가 둘이다.

### 두 경로
- **XHR2 경로**(`_uploadFileXHR`): `FormData`에 파일을 담아 `$.ajax`로 보내고 `xhr().upload`의 `progress` 이벤트로 진행률을 낸다.
- **폼 경로**(`_uploadFileForm`, 약 50줄): 숨은 `<form>`에 `<input type=file>`을 옮겨 담고 `jquery.form`의 `ajaxForm`으로 보낸다. `FormData`가 없는 옛 브라우저용이다.

### 어느 경로를 타는가
`bXHR2 = !!(ProgressEvent && FileReader) && !!FormData`이고, IE(`trident`)면 HTTPS일 때만 참이다. 지금 지원하는 브라우저(Bootstrap 5와 이 프로젝트의 현대 브라우저 전제)는 모두 참이므로 **폼 경로는 도달하지 않는 코드**다. IE 확인(`trident`)도 같다.

### 같이 사라지는 것들
| 위치 | 내용 |
|---|---|
| `_init` | `bXHR2` 계산과 `trident` 규칙, `htUploadOpts`(폼 경로만 씀) |
| `_uploadSingleFile` | `bXHR2 ? XHR : Form` 갈래 |
| `_initElement` | `bXHR2`가 거짓이면 `multiple` 속성을 지우는 줄 |
| `_uploadFile`, `_onChangeFile` | `HTMLInputElement(IE)`를 받는 갈래(`files \|\| element`) |
| `yona.user.Setting.js` 120, 160 | `bUseCrop = getEnv().bXHR2`가 항상 참이라 자르기 없는 갈래(파일 크기 1MB 확인과 폼 바로 제출)가 도달 불가 |
| `getEnv()` | `bXHR2`를 쓰는 곳은 `user.Setting` 하나뿐 |

### 남기는 것
- 진행률이 필요하므로 **XHR은 남긴다**(`fetch`는 업로드 진행률 이벤트가 없다).
- `$.ajax` 껍데기: `yona.Csrf.js`가 `XMLHttpRequest`와 `fetch`를 전역으로 가로채 토큰을 싣기 때문에(CSRF 때문에 `$.ajax`가 필요한 것은 아니다) 네이티브 `XMLHttpRequest`로도 바꿀 수 있다. 그러나 `$.ajax`는 `X-Requested-With`, `Accept`, JSON 해석, 오류 객체 모양(`status`, `statusText`)을 맞춰 주므로 jQuery 전체 제거(S-14) 때 함께 바꾸는 편이 안전하다.
- `bDroppable`(`File` 존재)과 `bPastable`(`document.onpaste` 존재 + `FireFox` 철자 조건). 붙여넣기 조건의 `"FireFox"` 철자 오류(R-140)는 그대로 항상 참이므로 이 조건을 지워도 동작이 같다. 단 Firefox 제외가 의도였는지 확인되지 않았으므로(R-140) 값은 그대로 참으로 둔다.
- `jquery.form`(`ajaxForm`)은 `$yona.sendForm`이 쓰므로 이번에 지우지 않는다.

### 선택지
- **A (권장)**: 폼 경로, IE 규칙, `bXHR2`와 `htUploadOpts`, `multiple` 제거 갈래, IE 입력 요소 갈래를 지우고 `user.Setting`의 자르기 없는 갈래를 정리한다. 약 100줄 줄어든다. 동작은 지원 브라우저에서 같다.
- **B**: A에 더해 `$.ajax`를 네이티브 `XMLHttpRequest`로 바꾼다. 헤더와 오류 모양을 맞춰야 하므로 S-14와 함께 하는 편이 낫다.
- **C**: 그대로 둔다. 비용은 없지만 도달하지 않는 코드가 남는다.

## S-07 조사 (`jquery.requestAs.js`, 2026-11-09)
`lib/jquery/jquery.requestAs.js`(303줄)는 Yobi 시절(2013) 팀이 만든 jQuery 플러그인이다. 단추나 링크를 누르면 `POST`, `PUT`, `DELETE` 요청을 보내고 끝나면 화면을 새로 고치거나 서버가 알려 준 곳으로 옮긴다. 삭제, 투표, 상태 바꾸기, 관리자 조치처럼 "눌러서 서버에 한 번 알리고 화면을 갱신"하는 곳이 모두 이것을 쓴다. 공식 라이브러리가 아니라 우리 코드다.

### 쓰는 곳
- **선언형(자동 연결)**: 문서가 준비되면 `$("[data-request-method]").requestAs()`가 모든 요소에 붙는다. 마크업 요소 **31개**(14개 템플릿): `post` 18, `delete` 10, `put` 1. 템플릿: `issue/view`, `code/partial-comment-thread`, `pullrequest/partial-state`, `milestone/view`, `project/webhooks`, `code/branches`, `code/tags`, `site/user-list`, `user/edit-emails`, `board/view`, `layout/layout`, `pullrequest/view`, `pullrequest/partial-search`, `layout/migration-layout`. 요소에 `data-request-method`(방식)와 `data-request-uri`(주소, 없으면 `href`)를 둔다.
- **코드에서 직접(7곳)**: `site/project-list.html`(프로젝트 삭제 확인 단추), `site/user-list.html`(계정 삭제, `fOnError`로 403이면 새로 고침), `yona.project.Delete.js`(`sMethod`, `sHref`, `fOnError`), `yona.Comment.js`(댓글 삭제 확인 단추), `yona.issue.View.js` 396(타임라인에 나중에 들어온 삭제 단추), `yona.code.Diff.js` 130과 `yona.git.View.js` 122(`data("requestAs").on("beforeRequest", …)`로 수락 단추를 비활성화하고 진행 표시를 켬). 확인창 단추 쪽은 `.data('requestUri', …).data('requestMethod', 'delete').requestAs()`처럼 jQuery 데이터를 채우고 부른다.

### 지켜야 하는 동작
1. 누르기(`click`)와 Enter(`keydown`)로 요청한다. `<a>`에 `GET`이면 아무것도 붙이지 않는다(보통 링크, C003). 요소에 `cursor: pointer`를 준다.
2. 요청: 방식은 `data-request-method`, 주소는 `data-request-uri` 또는 `href`, 응답은 문자열(`dataType: "text"`)로 받는다. 누르면 `preventDefault`와 `stopPropagation`을 해서 다른 클릭 처리기로 가지 않는다.
3. 사용자 정의 이벤트 `beforeRequest`(처리기가 `false`를 돌려주면 요청을 보내지 않는다), `load`(`false`면 기본 동작을 하지 않는다), `error`와 옵션 `fOnLoad`, `fOnError`.
4. 성공의 기본 동작: 상태가 204이고 `Location` 머리글이 있으면 그곳으로 이동하고, 아니면 화면을 새로 고친다.
5. 실패의 기본 동작: 상태 200이면 새로 고치고, 204이면 `Location`으로 이동하며, 그 밖의 오류는 **아무것도 하지 않는다**(사용자에게 알리지 않는다, v1.6 그대로).
6. 이 플러그인은 `keyCode !== 13`을 쓰는 곳이 남아 있다(S-05의 훑기는 `lib/`를 보지 않았다).

### 쟁점
- **클릭 처리 순서**: 지금은 요소마다 직접 걸어 `stopPropagation`이 조상의 처리기를 막는다. 문서 전체 위임(`document.addEventListener`)으로 바꾸면 조상의 처리기가 먼저 돈 뒤라 달라진다(`code/branches`, `code/tags`는 요청 전에 삭제 확인을 캡처 단계에서 걸어 두었다). 요소마다 직접 거는 방식을 유지하면 순서가 같다.
- **나중에 들어온 요소**: 지금은 `issue.View`가 타임라인을 다시 그린 뒤 `requestAs()`를 다시 불러 준다. 위임이면 필요 없지만 위 순서 문제가 따른다.
- **jQuery 데이터**: 확인창 쪽 5곳은 `.data()`에 값을 넣고 부른다(속성이 아니다). 네이티브로 바꾸면 옵션으로 넘기도록 호출처를 고쳐야 한다.
- 요청: `fetch`로 충분하다(진행률이 필요 없다). 토큰은 `yona.Csrf.js`가 `fetch`를 가로채 싣는다. `Location` 머리글은 같은 출처 응답이라 읽을 수 있고, `$.ajax`가 달던 `X-Requested-With`는 직접 단다.

### 선택지
- **A (권장)**: 같은 동작의 작은 네이티브 모듈(`yona.RequestAs`, 약 120줄)로 바꾼다. 선언형은 문서가 준비되면 요소마다 직접 걸고(순서 유지), 코드에서 직접 부르는 7곳은 옵션을 넘기는 호출로 바꾸며, `beforeRequest` 같은 이벤트는 모듈이 돌려주는 객체에 붙인다. 플러그인 파일을 지운다.
- **B**: htmx(D-96으로 들여온 것)의 `hx-post`, `hx-delete`로 바꾼다. 서버가 204와 `Location`을 주는 계약(`HX-Redirect`가 아님)을 바꿔야 하고 마크업 31곳도 바뀐다. v1.6 계약을 바꾸므로 권하지 않는다.
- **C**: 그대로 둔다. jQuery 4에서도 도는 것을 확인했고 비용이 없다. jQuery 전체 제거(S-14) 때 같이 정리한다.

### S-07 결정 (A-422)
선택지 A(플러그인을 새 모듈로 옮김)는 "플러그인 구조를 늘린다"는 지적으로 접고, 이벤트 장치와 인스턴스를 없앤 **A′**(함수 `send` + 표준 `CustomEvent`)로 구현했다. 선생님이 원래 생각하신 방식(단추에서 함수를 바로 불러 전송, 선언형 연결 층 제거)은 선언형 마크업 31곳을 바꾸게 되어 이번에는 하지 않았고 후속 논의로 남긴다.

## S-08 조사 (`yona.ui.Dialog`, `Toast`, `Dropdown`, 2026-11-09)
체크리스트의 "얇은 껍데기이고 호출은 2~3곳"은 틀렸다. 세 모듈은 서로 다른 일을 하고 호출처도 훨씬 많다.

| | 하는 일 | 쓰는 곳 | 줄 수 |
|---|---|---|---|
| `yona.ui.Dialog` | Bootstrap 모달(`#yobiDialog`)을 복제해 메시지와 설명, 단추 목록을 채우고 보여 주는 알림·확인 창 | `$yona.alert` 46곳, `$yona.confirm` 3곳, `$yona.ajaxConfirm` 1곳(모두 `yona.Common.js`를 거침) | 160 |
| `yona.ui.Toast` | 화면 구석에 잠깐 떴다 사라지는 알림 상자(`#yobiToasts`)를 쌓고 일정 시간 뒤 투명해진 다음 지운다 | `$yona.notify` 47곳 | 114 |
| `yona.ui.Dropdown` | `.btn-group[data-name]`의 목록(`li[data-value]`)에서 하나를 골라 라벨 표시, 숨은 입력 `name` 값, `active` 표시, 바뀐 뒤 콜백을 처리한다. 목록 끝에서 휠이 페이지로 넘어가지 않게 막는다 | 마크업 12곳(10개 템플릿, 이슈 상태·담당자·마일스톤·라벨 일괄 변경, 이슈 작성, 멤버 관리 등), 문서 준비 때 자동 연결(`scripts.html`), `yona.issue.MassUpdate.js` 5곳, `yona.issue.Write.js` 1곳 | 203 |

### 판정
- **Dialog: 유지.** 이미 프로젝트의 표준 부품인 Bootstrap 5 `Modal`을 감싼 것이다. `<dialog>`로 바꾸면 (1) 알림창이 다른 Bootstrap 모달 위에 뜰 때 Bootstrap의 포커스 가두기가 포커스를 뺏어 단추를 키보드로 못 누르는 충돌이 생기고(복사 도우미를 모달 안에서 시험할 때 본 것과 같은 종류), (2) `.modal` 스타일을 새로 써야 해 v1.6과 모양이 달라질 위험이 있다. 얻는 것은 Bootstrap 의존을 한 곳 줄이는 정도라 비용에 맞지 않는다.
- **Toast: 네이티브로 다시 쓰기(작음).** 표준 `Toast` 부품이 없고(Bootstrap 5의 `Toast`는 모양과 동작이 달라 v1.6 화면이 바뀐다) 이 모듈은 상자 만들기, 투명도 전환, `transitionend`에서 지우기뿐이다. 다만 지금은 `webkitTransitionEnd`(접두사 이벤트)와 jQuery(`bind`, `css`, `click`, `prepend`)를 쓴다. Chromium과 Firefox 모두 접두사 이벤트가 표준 `transitionend`의 별칭으로 아직 동작하는 것은 확인했다. 표준 `transitionend`와 DOM으로 바꾸고 사용하지 않는 템플릿 정리(`#tplYobiToast`의 `\n` 처리)를 한다. `push`, `clear` API는 그대로다.
- **Dropdown: 휠 처리만 CSS로.** 목록 끝에서 휠이 넘어가지 않게 하는 JS는 비표준 `mousewheel` 이벤트를 쓴다. **Firefox에는 이 이벤트가 없어**(`'onmousewheel' in div`가 거짓으로 확인) 지금도 Firefox에서는 막지 못한다. 표준 CSS `overscroll-behavior: contain`으로 바꾸면 두 브라우저에서 같아진다(Firefox에서 동작이 v1.6과 달라지는 개선). 라벨, 숨은 입력, `active`, 콜백은 `<select>`로 대체할 수 없다(항목에 아바타와 색 라벨 같은 HTML이 들어가고 `<select>`는 꾸밀 수 없다). 그대로 둔다.

### 선택지
- **A (권장)**: Dialog 유지, Toast 네이티브 재작성, Dropdown은 `mousewheel` 처리를 CSS로 교체. 작은 변경 두 건이다.
- **B**: Dialog도 `<dialog>`로 바꾼다. 위의 포커스 충돌과 스타일 위험 때문에 권하지 않는다.
- **C**: 모두 유지한다. 비용은 없지만 접두사 이벤트와 Firefox에서 동작하지 않는 휠 처리가 남는다.

## S-09 조사 (`yona.ui.Calendar`와 Pikaday, 2026-11-09)
체크리스트에는 `yona.ui.Calendar`(79줄) 하나만 적혀 있지만 달력 라이브러리 Pikaday는 **세 가지 방식**으로 쓰인다.

| 방식 | 곳 | 모양 |
|---|---|---|
| 입력란 + 달력 단추(팝업) | `yona.ui.Calendar`(`data-toggle="calendar"`): 이슈 작성, 수정, 보기의 목표 완료일, 이슈 검색 폼 4곳. 입력란 옆 `.btn-calendar` 단추를 누르면 달력이 뜬다. 검색 폼은 `silent`(처음 값을 넣지 않음) | 팝업 |
| **항상 보이는 달력(인라인)** | `yona.milestone.Write.js`: 마일스톤 만들기, 수정(`.date-picker` 영역에 달력을 붙이고 고른 날을 입력란에 채움) | 인라인 |
| 항상 보이는 달력(인라인) | `user/partial-token-form.html`: API 토큰 만료일(추가 항목 X-5, `minDate` 내일 이후) | 인라인 |

### 의존
- Pikaday 33KB(JS)와 CSS. 날짜 형식(`format: "YYYY-MM-DD"`)을 읽고 쓰는 데 **moment를 쓴다**.
- **moment-with-locales 391KB**: Pikaday의 형식 처리(마일스톤과 이슈 팝업) 외에 목표 완료일 검증(`moment(값).isValid()`, 이슈 작성·보기·목록 3곳), 코드 보기의 상대 시간(`fromNow()`, `yona.code.Browser.js` 1곳), `scripts.html`의 로케일 설정에 쓰인다. 5개 템플릿이 직접 불러온다.
- Pikaday의 월과 요일 이름은 기본값(영어)이고 앱에서 i18n 설정을 찾지 못했다(화면으로 확인하지 않았다).

### 표준 대체 후보: `<input type="date">`
- 값이 항상 `yyyy-MM-dd`라 저장 형식이 같고, 브라우저가 로케일에 맞는 달력과 검증을 제공한다. `showPicker()`로 달력 단추를 연결할 수 있고(Chrome 99, Firefox 101, Safari 16), `min` 속성으로 "내일 이후"도 된다. 검증용 moment가 필요 없어진다.
- **한계**: (1) 항상 보이는 달력(인라인)을 만들 수 없다. 마일스톤과 토큰 화면의 달력이 팝업으로 바뀌어 화면이 달라진다. (2) 모양이 브라우저마다 다르고 Pikaday와 다르다. (3) 자유 입력(예: `2014-5-3`, 빈칸, 잘못된 값 안내)이 칸 나눔 입력으로 바뀌어 v1.6의 입력 동작과 오류 안내가 달라진다. 그래서 체크리스트가 "결정 대기"였다.

### 선택지
- **A**: 모든 달력을 `<input type="date">`로 바꾼다. Pikaday와 moment 의존이 크게 줄지만 세 화면의 모양과 입력 동작이 v1.6과 달라진다(결정 필요).
- **B (권장, 화면 변화 없음)**: Pikaday 모양은 그대로 두고 **moment 의존만 걷어낸다.** Pikaday는 `parse`와 `toString`을 직접 줄 수 있으므로 `yyyy-MM-dd`를 표준 코드로 읽고 쓰게 하고, 검증은 표준 날짜 검사로, 상대 시간은 `Intl.RelativeTimeFormat`으로 바꾼다. 391KB가 빠지고 달력 모양과 입력 동작은 그대로다.
- **C**: 팝업 달력(이슈 4곳)만 `<input type="date">`와 `showPicker()`로 바꾸고 인라인 달력(마일스톤, 토큰)은 Pikaday를 유지한다. 일부 화면만 모양이 달라진다.
- **D**: 그대로 둔다.

### S-09 버전 확인 (2026-11-09)
- **저장소의 Pikaday는 2014년의 옛 사본이다.** 공식 릴리스와 줄 단위로 비교했더니(공백과 줄 끝 무시) 1.3.0과 22줄만 달랐고 그 차이는 모두 1.3.0보다 **오래된** 코드다(예: `reposition` 옵션과 필드 비우기 처리가 없고 `moment`를 `require.defined`로 부른다). Yona가 고친 흔적은 보이지 않는다. 가장 가까운 공식 릴리스는 1.2.0과 1.3.0 사이다.
- **새 버전은 있다: 1.8.2(2020-10-22).** 그 뒤로 릴리스는 없다(npm 수정일은 2022-06). 1.4.0부터 1.8.2 사이에 터치 기기 지원, 접근성(aria), 일 단위 아닌 주 선택과 사용자 지정 파서(`parse`), 키보드 입력 옵션, 메모리 누수 수정, 날짜 문자열 해석 수정, 지우기, 고유 ID, ISO 8601 주 번호가 들어갔고 **1.8.0에서 moment 선택 의존이 빠졌다**(`format`을 쓰려면 직접 `parse`와 `toString`을 주면 된다). 최신판도 사실상 정체된 프로젝트이므로 올려도 장기 해결은 아니다.
- 선택지 B에 "Pikaday를 1.8.2로 올리는 것"을 포함할 수 있다(공식 배포 파일 그대로 들이고 sha256 기준 목록에 올림). 올리면 모양이 조금 달라질 수 있어 전후 화면 비교가 필요하다.

### S-09 대체 후보 시험 (2026-11-09)
`e2e/datepicker.tmp/`(추적하지 않는 시험 폴더)에 세 사용 방식(팝업, 인라인, 내일 이후만 고르는 인라인)을 한 페이지로 만들고 앱의 실제 CSS를 입혀 후보별 어댑터로 Chromium과 Firefox에서 같은 12개 항목을 확인했다. Yona가 Pikaday에 가한 변경은 v1.6 저장소 이력에 **없다**(2014-07의 사본 교체가 마지막이며 1.3.0보다 오래된 공식 코드와 일치). 앱이 입히는 것은 CSS뿐이다(`_page.scss`의 `.pika-select`, `.pika-button` 보정, `_override.scss`의 `abbr[title]`, 폭 232px, content-box 전제).

| 후보 | 결과 |
|---|---|
| Pikaday 현재, 1.8.2(moment 없이) | 12항목 모두 통과. 월과 요일 이름은 **영어**(i18n 미설정) |
| **vanillajs-datepicker 1.3.4**(+BS5 CSS, `ko`) | **12항목 모두 통과, 두 브라우저 동일.** 한국어 표기, 인라인과 `minDate` 지원, 입력란 직접 입력은 `blur`에서 `setDate`로 반영(Pikaday 어댑터와 같은 방식). 모양은 Pikaday와 다르다(BS5 모양) |
| air-datepicker 3.6.0 | 팝업과 인라인 값 채우기는 동작. 팝업이 바깥 클릭과 Esc로 닫히지 않아 어댑터 보완 필요, 화면이 큼(CSS 20KB) |
| vanilla-calendar-pro 3.4.0 | 인라인과 `dateMin` 동작. 팝업이 선택 뒤 닫히지 않고, 입력란에 직접 쓴 날짜를 열 때 반영하지 않아 어댑터 보완 필요. 주 시작이 월요일(`firstWeekday` 설정 필요) |
| cally 0.9.2(웹 컴포넌트) | 팝업과 인라인 선택은 동작. `min`이 오늘 칸을 막는지와 입력란 값을 달력에 반영하는 부분이 시험 어댑터로는 확인되지 않음 |
| js-datepicker 5.18.4 | 인라인(`alwaysShow`)을 여러 개 만들면 달력이 같은 자리에 겹쳐 보여 시험 어댑터를 다시 짜야 한다 |

세 후보(air, vanilla-calendar-pro, cally)와 js-datepicker는 시험 어댑터의 한계일 수 있어 "실패"가 아니라 **미확정**이다.

### S-09 Yona 팀이 달력에 가한 변경과 마감일 자동 저장 확인 (2026-11-09)
- **라이브러리 JS는 수정이 없다.** `legacy-yona`(v1.6 이후 저장소)의 `pikaday.js`와 바이트 단위로 같다.
- **CSS 보정 두 건이 있다.** `9a1d3202e`(2013-04, Keesun Baik): `pikaday.css`의 월과 연도 단추 모양(패딩, 테두리, 배경색 등 29줄). `e29343c51`(2014-08, insanehong): `.pika-table abbr[title] { cursor: default; }`(현재 `_override.scss`). 대체 후보로 바꿀 때 같은 모양을 맞춰야 한다.
- **마감일 자동 저장**: 이 저장소의 이전 이력(커밋 `1ad370b82` 메시지)에 "마감일이 있는 이슈 보기를 열 때마다 `massUpdate`가 조용히 발생하던 기존 버그(`setDate()`가 `change`를 내보내는 방식이 원인)"가 적혀 있다. `pikaday.js:685`가 `setDate()`에서 `change`를 발생시키는 것은 사실이다. 그러나 **현재 v2 화면에서 마감일이 있는 이슈(#44)의 보기를 열어 2.5초 동안 본 결과 저장 요청(비 GET)은 나가지 않았다**(Playwright, 외부 통계 요청만 있었다). 즉 현재 코드에서는 재현되지 않는다. v1.6에서 재현되었는지는 v1.6 화면을 볼 수 없어 확인하지 못했다(결함으로 단정하지 않는다).
- 후보 비교에 "초기화 때 `change`가 나가지 않는가"를 확인 항목으로 추가한다(vanillajs-datepicker는 `setDate`가 입력란 `change`를 내보내지 않는 것이 기본이라 이 계약에서는 유리할 수 있으나 시험하지 않았다).

### S-09 Pikaday 패치 유무 정정 (2026-11-09)
`architecture/js-upgrade-plan.md`의 "Yona가 v1.6에서 고친 벤더 파일" 표는 Pikaday를 "마감일 추가, import 경로 이동"으로 적었다. 이는 v1.6 커밋 제목만 본 것이어서(A-406과 같은 방식의 오류) 공식 파일과 직접 대조했다.
- **`pikaday.js`는 공식 저장소 커밋 `c43be6c2c`(2014-05-26, Pikaday/Pikaday)의 파일과 공백을 무시하면 0줄 차이**다. 2014-07-02 "add duedate in issue" 커밋은 개발판 사본을 공식 사본으로 **통째로 바꾼 것**이고 기능 패치는 없다(공백만 다르고 바이트까지 같지는 않다). "마감일 추가"는 래퍼 `yona.ui.Calendar.js`와 템플릿에서 한 일이다.
- **`pikaday.css`는 공식과 다르다(공백 무시 75줄).** 공식 CSS를 Yona가 고쳐 쓴 것이며 `z-index: 9`, 폭 240px, 월과 연도 단추 모양(2013-04 `9a1d3202e`) 등이다. 즉 Yona 팀의 **패치는 CSS에만 있다.**
- 표의 Pikaday 행을 "JS 패치 없음(공식 2014-05-26 커밋과 같음), CSS는 Yona가 고쳐 유지"로 정정해야 한다.

### S-09 `yona.ui.Calendar` 계약 대조 시험 (2026-11-09, 선생님 요청: 계약과 Pikaday의 동작, 위치를 유지하는 활발한 대체)
**Pikaday의 기준 동작**(코드와 측정): 입력란의 왼쪽 아래에 틈 없이 붙고(`dx 0, dy 0`), 오른쪽이 넘치면 오른쪽 맞춤으로, 아래가 넘치면 입력란 위로 뒤집는다(`adjustPosition`). 입력란 클릭과 포커스로 열리고 바깥 클릭(blur)으로 닫히며 Esc는 없다. 날짜를 고르면 값이 채워지고 닫히며 입력란에 `change`가 1번 나간다. `setDate()`도 `change`를 1번 낸다. 입력란에 쓴 값은 `change`에서 읽는다. 보존할 Pikaday 저장소는 2025-08 보관(archived)되었다.

후보를 같은 항목으로 측정했다(`e2e/datepicker.tmp/contract.mjs`, Chromium과 Firefox 동일 결과, 어댑터는 `Calendar.js` 안에서 쓸 수 있는 수준의 짧은 코드만 허용):

| 후보(마지막 GitHub 갱신) | 위치(일반) | 오른쪽 뒤집기 | 아래 뒤집기 | 클릭과 포커스로 열림 | 바깥 클릭 | 선택 후 닫힘 | 비고 |
|---|---|---|---|---|---|---|---|
| Pikaday | 0, 0 | 예 | 예 | 예 | 닫힘 | 닫힘 | 기준 |
| **vanilla-calendar-pro 3.4.0**(2026-11) | 0, **4px** | 예(`positionToInput:'auto'`) | 예 | 예 | 닫힘 | 닫힘(`hide()` 호출 필요) | 틈 4px는 CSS `margin-top`을 0으로 두면 맞음. 전역 변수 `VanillaCalendarPro`를 주는 UMD(`index.js`)가 있어 모듈 없이 쓸 수 있음. 인라인(마일스톤, 토큰)도 지원 |
| vanillajs-datepicker 1.3.4(2024-03, 코드 2023-11) | 0, 0 | 예 | 예 | 예 | 닫힘 | 닫힘 | 위치는 가장 같다. 갱신이 3년 가까이 없다 |
| air-datepicker 3.6.0(2025-05) | 0, 12px | **아니오** | **아니오** | 예 | 닫힘 | 닫힘 | 항상 아래로만 열린다 |
| flatpickr 4.6.13(2024-08, 릴리스 2022-06) | 0, 2px | 예 | 예 | 예 | 닫힘 | 닫힘 | 입력란 직접 입력과 `change`가 기본으로 동작(어댑터 불필요). 릴리스가 멈춰 있다 |
| js-datepicker 5.18.4(2025-02) | 0, 0 | **아니오** | **아니오** | 예 | 닫힘 | 닫힘 | 뒤집기 없음 |

어댑터가 필요한 계약(모든 후보 공통, Pikaday가 기본으로 하던 것): (1) 선택과 `setDate()`가 입력란에 `change`를 내보내기, (2) 입력란에 직접 쓰고 `change`가 나면 달력에 반영하기, (3) `getDate()`가 `Date`를 돌려주기, (4) 초기에 입력란 값을 선택일로 삼기. Pikaday의 `setDate("")`는 값을 지우지 않는 버그가 있어(측정: 값이 그대로 남음) 후보들의 "지움"과 다르다.

## S-10 조사 (`yona.ui.Typeahead`와 BS2 typeahead 플러그인, 2026-11-09)

### 현재 구성
- `common/yona.ui.Typeahead.js`(148줄) 래퍼가 BS2 플러그인 `bootstrap-typeahead.js`(2.3.1, 335줄)와 `bootstrap-better-typeahead.js`(1.0.0, 154줄, Yobi가 고친 곳에 `yobi` 표지)를 jQuery로 부른다. 플러그인은 `scripts.html`이 모든 화면에 불러온다. BS5에는 typeahead가 없다.
- 래퍼는 `source`(함수 또는 서버 주소), `updater`, `render`, `minLength`(기본 0), `limit`(항목 수, 기본 10)를 받고, 서버가 `Content-Range: items 일부/전체`로 알려 주면 이미 전체를 받았을 때 다시 묻지 않고 캐시를 쓴다.

| 쓰는 곳 | 항목 | 특이 사항 |
|---|---|---|
| 프로젝트 구성원 추가(`yona.project.Member.js`, `#loginId`) | **HTML 문자열**(아바타 `<img class='mention_image'>`와 이름, 서버 `UserApiController`가 `info`로 준다) | `render`가 플러그인 내부(`this.options.item`, `this.$menu`)를 직접 만진다. `updater`가 표시 문자열을 로그인 ID로 바꾼다. minLength 1 |
| 조직 구성원 추가(`yona.organization.Member.js`) | 위와 같음 | 위와 같음(코드가 거의 복사본) |
| 전체 메일(`yona.site.MassMail.js`, `#input-project`) | 프로젝트 이름 글자 | 기본 `source`(서버 호출), 래퍼 옵션만 사용 |
| 이슈 라벨 분류(`yona.issue.LabelEditor.js`) | 글자 배열 | 래퍼 없이 `.typeahead()`를 부르고 `data("typeahead").source`에 배열을 넣는다(내부에 직접 접근). `better-typeahead` 덕에 빈 칸에서도 목록이 뜬다 |
템플릿 4곳의 `data-provider="typeahead"`는 BS2의 자동 연결용이다.

### 표준 `<datalist>`로 되는가
- **되는 곳**: 글자 목록인 전체 메일과 라벨 분류(2곳). 값을 입력란에 채우는 동작은 표준이다.
- **안 되는 곳**: 구성원 추가 2곳. `<datalist>`의 항목은 글자뿐이라 **아바타 이미지를 보여 줄 수 없고**, 표시 문자열을 다른 값(로그인 ID)으로 바꾸는 `updater`도 없다. 서버가 주는 `info`를 글자로 바꾸고 아바타를 포기하면 v1.6과 화면이 달라진다. 또 목록의 모양과 위치를 CSS로 정할 수 없고 브라우저마다 다르게 그려진다.
- 그래서 체크리스트의 "그대로는 어렵다"가 맞다. 2곳만 바꾸고 2곳은 플러그인을 남기면 두 방식이 섞인다.

### 선택지
- **A. 유지**: 플러그인은 `jquery4.test.mjs`가 jQuery 4 환경에서 돌려 확인했고 동작한다. 단점은 BS2용 jQuery 플러그인이 계속 남고, 구성원 두 파일이 플러그인 내부(`$menu`, `options.item`)에 기대는 것이다.
- **B. (권장 후보) 같은 API로 다시 쓴다**: 이미 `lib/text-expander/combobox-nav.js`(GitHub, 7KB, D-106에서 들임)가 있다. 입력란과 `role="listbox"` 목록을 연결해 위, 아래, Enter, Esc, 마우스 선택과 ARIA를 처리해 준다. 이를 바탕으로 `yona.ui.Typeahead`를 jQuery 플러그인 없이 다시 쓰면 항목은 DOM으로 직접 만들 수 있어 **아바타 HTML을 그대로 보여 주고**(`render`는 항목 하나를 만드는 함수로 단순해진다), BS2 플러그인 두 파일(489줄)과 `data-provider` 4곳, 라벨 편집기의 내부 접근이 사라진다. D-105, D-106의 GitHub 방식과 맞다. 구성원 두 파일과 라벨 편집기를 함께 고쳐야 한다(호출 4곳).
- **C. Tom Select 등**: 선택 위젯이라 입력란에 글자를 채우는 이 화면의 동작이 바뀐다.
- 정해야 할 것: v1.6의 입력 중 목록 모양(BS2 `.typeahead.dropdown-menu`), 목록을 여는 시점(minLength 0이면 초점만 가도 열림), Tab으로 선택하는지(BS2는 Tab과 Enter가 선택), 강조 표시(BS2 기본 `highlighter`가 검색어를 굵게)를 그대로 맞출지.

## S-13 조사 (jQuery 정적 유틸, 2026-11-09)

### 체크리스트 숫자 정정
체크리스트의 "`$.each` 60, `$.extend` 10, `$.proxy` 13, `.animate` 7"은 지금(앱 코드 `common/`, `service/`, 라이브러리와 번들 제외)과 다르다. 앞선 작업(S-05, S-06, S-07 등)으로 줄었고, `$.each(`와 `.each(`가 섞여 있었다. 다시 센 값:

| 구분 | 호출 수 | 판단 |
|---|---|---|
| **정적 유틸(S-13의 범위)**: `$.each` 10, `$.extend` 8, `$.proxy` 8, `$.inArray` 1, `$.map`과 `jQuery.map` 2, `$.isEmptyObject` 1, `$.parseHTML` 2 | **32곳, 파일 11개** | 아래에서 하나씩 본다 |
| 인스턴스 `$(...).each(...)` | 49곳, 파일 27개 | jQuery 집합을 순회하므로 S-14(DOM 전체 제거)에 속한다. `this`가 요소이고 `:contains` 같은 jQuery 선택자와 묶여 있다 |
| `$.ajax` 40, `$.get` 8, `$.post` 7, `$.getJSON` 1 | 56곳 | S-14(`fetch`). 4xx 처리, `X-Requested-With`, `Accept` 헤더 차이에 주의 |
| `$.event.trigger`(`CodeCommentBox`) 2, `$.fn.select2.defaults` 1 | 3곳 | S-14와 select2에 속한다 |
| `.animate` | **0곳** | 이미 없다(체크리스트가 옛 값) |

### 정적 유틸 32곳의 표준 대체와 동작 차이
| 함수(곳) | 대체 | 같은가 | 확인할 곳 |
|---|---|---|---|
| `$.proxy(fn, this, 인자)`(`Files.js` 8) | `fn.bind(this, 인자)` | 같다(고정 인자도 같은 순서). 다른 점은 jQuery가 묶인 함수에 붙이는 `guid`라서 원래 함수로 `.off(fn)`을 부르면 떼어지던 것인데, `Files.js`는 인자 없는 `.off()`로만 떼므로 영향이 없다 | 호출 8곳이 모두 `.on(...)`에 바로 넘기는 형태 |
| `$.inArray(v, 배열) !== -1`(`code.Browser.js` 1) | `배열.includes(v)` | 같다(엄격 비교) | |
| `$.map(users, fn)`(`Select2.js` 1), `jQuery.map(aMatch, fn)`(`SvnDiff.js` 1) | `배열.map(fn)` | jQuery `map`은 결과의 `null`, `undefined`를 버리고 배열을 평탄화한다. 두 곳 모두 값마다 객체나 숫자(`parseInt`)를 돌려주므로 같다. 정규식 결과 배열의 `undefined` 원소도 호출된다 | 각 콜백이 `null`을 돌려주지 않는지 |
| `$.isEmptyObject(error)`(`project.New.js` 1) | `Object.keys(error).length === 0` | `error`는 직접 만든 객체라 같다. `null`, `undefined`는 `isEmptyObject`가 참이지만 `Object.keys`는 오류라 쓰는 곳에 `error`가 항상 객체인지 확인 | 이 곳은 `var error = {}`로 시작 |
| `$.extend(a, b)`(8) | `Object.assign(a, b)` | **`undefined` 값을 건너뛰는 점이 다르다**: jQuery는 원본의 `undefined` 값을 복사하지 않고 `Object.assign`은 복사한다. 깊은 복사(`true`)는 8곳 모두 없다. 대상에 원래 있는 `undefined`는 둘 다 유지한다 | `Calendar.js`(내가 쓴 것), `Common.js`(`htOptions` 없을 수 있음, 둘 다 `null`, `undefined` 원본은 건너뜀), `Pagination.js` 2(변경하는 대상), `Select2.js` 3. `Select2.js:204`는 서버 JSON(`undefined` 없음), `:234`는 인자가 대부분 없음 |
| `$.each(배열, (i, v) => …)`(10) | `배열.forEach((v, i) => …)` 또는 `for…of` | **인자 순서가 바뀐다**(i, v → v, i). jQuery는 콜백의 `this`가 값이고 `return false`로 멈춘다. 객체는 `(키, 값)`이다 | 10곳 중 객체 순회 2곳(`Select2.js:135, 140`, `Object.entries`), `HTMLOptionsCollection` 2곳(`Array.from`), `jQuery 객체` 1곳(`OriginalMessage.js`, 반복 가능), 나머지 배열 5곳. `this`나 `return false`를 쓰는 곳이 있는지 |
| `$.parseHTML(html)`(`Common.js` 1, `pullrequest.Reviewers.js` 1) | 템플릿 조각은 `<template>`의 `content`, 전체 문서는 `DOMParser` | jQuery는 기본으로 `<script>`를 버린다(`keepScripts`가 거짓). `template`은 스크립트를 실행하지 않고 남기므로 지우는 단계가 필요하다. `Reviewers.js`는 문서 전체를 받아 `#review-area`만 쓰므로 `DOMParser`로 바로 읽는 쪽이 간단하다 | `<tr>` 같은 조각은 `<template>`이 문맥 없이 처리하므로 괜찮다 |

### 선택지
- **A. 32곳을 표준 JS로 바꾼다(권장)**: 위 표대로 하되 jQuery 4 준비(D-103) 때처럼 **원래 API와 표준 대체식이 같은 값을 내는지 값 비교 시험**으로 먼저 확인한다(특히 `$.extend`의 `undefined`, `$.each`의 객체 순회, `$.map`의 평탄화). 규모는 11개 파일, 한 묶음이다. 얻는 것은 jQuery 정적 유틸 의존이 사라지는 것이지 jQuery 제거는 아니다(S-14가 남는다).
- **B. 일부만**: 차이가 없는 `$.proxy`, `$.inArray`, `$.map` 3종(11곳)만 바꾸고 나머지는 S-14 때 함께 한다.
- **C. 유지**: S-14에서 한꺼번에 다룬다.
- 판단: `$.each`와 `$.extend`는 위험한 차이(인자 순서, `undefined`)가 있어 시험 없이 기계적으로 바꾸면 안 되지만, 시험을 먼저 두면 안전하다. A를 권한다.

### S-13 동작 확인 목록 (2026-11-09, 선생님 지시: 다름이 있는 곳은 나중에 동작 확인)
구현은 끝났고 자동 시험과 실제 앱 확인(Chromium, Firefox)을 했다. 아래는 jQuery와 표준 JS의 결과가 **다를 수 있는 곳**이다. 선생님이 직접 화면에서 확인하실 때 보시라고 모아 둔다.

| 곳 | 달라질 수 있는 점 | 지금까지 확인한 것 | 직접 볼 화면 |
|---|---|---|---|
| `Select2.js` `$.extend`(5곳), `$.map` | 원본의 `undefined`를 건너뛰던 것이 이제 복사된다 | 호출처의 원본은 서버 JSON(undefined 없음)이거나 옵션이 없다. 시험 + 이슈 보기 라벨 목록과 선택 | 이슈 보기 담당자, 라벨, 공유자 선택 상자(추가 확인: 공유자 원격 검색과 칩 생성을 Chromium과 Firefox로 확인) |
| `Select2.js` `$.each`(객체 2곳, 선택 변화 비교) | 인자 순서, 객체 순회 방식 | 키 순서와 값이 같음을 값 비교로 확인, 라벨을 고르는 실제 화면 확인 | 라벨 여러 개 선택과 해제 |
| `Common.js` `$.extend`(팝오버 옵션) | `content: undefined`를 넘기면 덮어쓴다 | 호출 5곳 확인(`user.Setting`만 값이 비어 있을 수 있고 부트스트랩이 빈 값은 같게 처리) | 프로필 설정, 가입, 비밀번호 재설정, 프로젝트 만들기의 오류 말풍선(추가 확인: 비밀번호 변경의 빈 입력 말풍선 3개를 두 브라우저로 확인) |
| `Common.js` `$.parseHTML` → `parseHtml` | 문서 소유자가 `<template>`의 빈 문서라 이미지 등을 읽는 동안 불러오지 않는다(jQuery도 같음) | `<tr>` 조각, script 제거, 글자 조각이 같음을 값 비교로 확인 | 템플릿으로 그리는 화면(첨부 목록, 알림) |
| `Pagination.js` `$.extend`(대상을 바꾸는 2곳) | undefined 복사 | 이슈 목록 쪽 넘김 링크가 그려지는 것 확인 | 목록 아래 이전, 다음 쪽 |
| `pullrequest.Reviewers.js` `$.parseHTML` → `DOMParser` | 문서 전체를 읽는 방식이 달라짐. `#review-area`가 없으면 아무것도 바꾸지 않는 점은 같게 함 | 새 시험(script 미실행, 밖은 가져오지 않음). **실제 화면 확인 완료(2026-11-09)**: e2e 데이터의 `acme/web`에 풀 리퀘스트(feature-a → master)를 직접 만들어 리뷰 요청(`POST .../reviewRequests`) 뒤 리뷰 영역이 새로 그려지고 대기 중인 리뷰어와 취소 단추가 생기며, 취소 뒤 다시 갱신되는 것을 Chromium과 Firefox로 확인 | 풀 리퀘스트에서 리뷰 요청과 취소 직후 영역 갱신 |
| `OriginalMessage.js` `$.each` → `Array.from(...).forEach` | 없음(인자 순서만) | 새 시험(구분선 숨김과 "..." 펼침) | 이메일로 온 댓글의 인용 숨기기(추가 확인: 실제 번들에서 구분선과 인용 숨김, 단추 생성을 두 브라우저로 확인, 이메일로 온 실제 댓글은 없어 주입한 표지로 확인) |
| 그 밖의 `$.proxy`(Files), `$.inArray`, `$.isEmptyObject`, `jQuery.map` | 값 비교로 같음 | 파일 첨부와 끌어 놓기, 프로젝트 만들기 검증, 코드 보기 | 이슈 작성의 파일 올리기(추가 확인: 코드 보기에서 README.md가 마크다운으로 그려짐을 두 브라우저로 확인) |

