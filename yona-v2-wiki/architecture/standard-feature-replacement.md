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
| S-04 | `yona.Csrf.js` | 없음 | 유지(XHR, fetch, 폼 제출에 토큰을 싣는 표준 기능이 없다) | 유지 |
| S-05 | `yona.ShortcutKey.js`와 `keyCode`, `which` 33곳(17개 파일) | `KeyboardEvent.key` | 가능. 한글 입력기에서 `keyCode` 229, `key`의 `Process`, `isComposing`을 함께 봐야 한다 | **완료**(2026-11-09, A-420, A안: 도우미 `isEnterKey` 등 + 호출 교체 + `ShortcutKey`를 `key` 기반으로) |
| S-06 | `yona.Files.js`(783줄) 업로드 | `fetch` | 부분. `fetch`는 업로드 진행률이 없어 XHR이 필요하다. `FormData` 없는 브라우저용 `$.ajaxForm` 대체 경로(`_uploadFileForm`)와 IE 확인(`trident`)은 지울 수 있다 | 미착수 |
| S-07 | `jquery.requestAs.js`(303줄, 호출 7곳) | `fetch` + `<dialog>` 확인창 | 가능, 중간 규모 | 미착수 |
| S-08 | `yona.ui.Dialog`, `Toast`, `Dropdown` | Bootstrap 5 컴포넌트 또는 `<dialog>` | 가능. 얇은 껍데기이고 호출은 2~3곳 | 미착수 |
| S-09 | `yona.ui.Calendar`(Pikaday 래퍼) | `<input type="date">` | 가능하나 화면이 달라진다(v1.6과 같은 모양을 지키려면 결정이 필요) | 결정 대기 |
| S-10 | `yona.ui.Typeahead`와 better-typeahead | `<datalist>` | 그대로는 어렵다(구성원 추가가 `render`, `updater`로 항목을 꾸미는데 `<datalist>`는 꾸밀 수 없다) | 결정 대기 |
| S-11 | `lib/filetype.js`(1,078줄) | 없음 | 유지(파일 이름 → 아이콘 표) | 유지 |
| S-12 | `yona.Common.js`의 `xssClean` | Sanitizer API | 아직 모든 브라우저 표준이 아니라 유지 | 유지 |
| S-13 | jQuery 유틸 `$.each`(60), `$.extend`(10), `$.proxy`(13), `$.inArray`, `$.map`, `$.grep`, `$.isPlainObject`(약 5), `.animate` 등(7) | `for...of`, `Object.assign`, `bind`, `includes`, `map`, `filter`, CSS 전환 | 쉬운 기계적 치환(약 100곳) | 미착수 |
| S-14 | jQuery DOM과 이벤트와 `.data()`와 `$.ajax`(약 1,300곳) | `classList`, `hidden`, `closest`, `addEventListener`, `dataset`, `fetch` | jQuery 전체 제거에 해당하는 큰 일. `.data()`는 값 자동 변환, `$.ajax`는 4xx 처리와 `X-Requested-With`, `Accept` 헤더 차이에 주의 | 별도 단계 |

사용 횟수(`Migration.js` 제외, 파일 수): `$.ajax` 계열 59(34), `$.each` 60(28), `.show/.hide/.toggle` 122(36), 클래스 조작 100(32), `.on/.off/.bind` 237(64), `.data` 217(36), 탐색(`closest`, `find` 등) 310(51), `keyCode/which` 33(17), `setInterval` 8(5), `alert/confirm` 직접 30(13).

## 확인 중 발견한 것
| 번호 | 내용 | 처리 |
|---|---|---|
| F-01 | `common/yona.Sha1.js`(js-sha1 0.6.0)를 `issue/view`와 `board/view`가 불러오지만 어디서도 부르지 않는다(본문 체크섬은 서버가 계산하고 v1.6도 같았다). 표준 `crypto.subtle.digest('SHA-1')`는 비동기이고 보안 연결에서만 동작해 그대로 대체되지는 않는다 | 삭제 후보(호출 없음) |
| F-02 | `yona.ui.Tabs.js`는 복원 조건이 `welLink.data("toggle" == "tab")`(항상 거짓)라 선택한 탭을 저장만 하고 복원하지 않는다 | v1.6 결함, `R-139` |
| F-03 | `yona.Files.js`의 붙여넣기 업로드가 Firefox를 제외하는 확인이 `indexOf("FireFox")`인데 실제 브라우저 문자열은 `Firefox`라 제외가 동작하지 않는다 | v1.6 결함으로 보임, `R-140` |

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

