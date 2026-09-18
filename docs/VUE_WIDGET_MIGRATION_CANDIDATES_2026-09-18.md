# Vue 컴포넌트 분리 후보 조사 (2026-09-18)

이미 17개 위젯(Dialog/Toast/Popover/LoginDialog/Switch/Dropdown/Typeahead/Pagination/
Attachments/ScrollElevator/PageSlide/ReviewForm/LabelEditor/HelpMarkdown 등)이
`components/vue-widgets`에서 만들어져 yona에 영구 배포됐다(commit `4c5ffcb3b`).
이 문서는 그 이후 아직 vanilla JS(jQuery 스타일/순수 DOM 조작)로 남아있는 인터랙티브
UI 중 Vue 컴포넌트로 뽑아낼 만한 후보를 조사한 결과다. 코드 수정 없이 조사만 했다.

## 우선순위 후보

| 순위 | 파일 | 규모 | 재사용 화면 | 비고 |
|---|---|---|---|---|
| 1 | `yona.Files.js` | 919줄 | 8곳: `board.Write`/`milestone.Write`/`user.Setting`/`code.SvnDiff`/`issue.View`/`board.View`/`code.Diff`/`issue.Write` | 드래그앤드롭 업로드/첨부 위젯. 미포팅 컴포넌트 중 최대 규모. 이미 포팅된 `yona.Attachments.js`가 이 위에 얹혀 있을 수 있어 겹치는 부분 확인 필요 |
| 2 | `yona.CodeCommentBox.js` + `yona.CodeCommentBlock.js` | 322+529=851줄 | 5곳: `pullrequest/view.html`, `code/compare.html`, `code/diff.html`, `common/commentForm.html`, `yona.code.Diff.js` | 코드/PR diff 줄별 인라인 댓글 팝업. 복잡도 최고(스레드 상태머신). diff 테이블 셀 위치에 포지셔닝이 강결합돼 있어 `ReviewForm`처럼 라이트 DOM 탈출구가 필요할 가능성 높음 |
| 3 | `yona.ui.TomSelect.js` | 374줄 | 7곳: `yona.issue.Sharer.js`/`yona.issue.View.js`/`yona.board.View.js`/`yona.project.New.js`/`yona.issue.Assginee.js`/`yona.issue.Write.js`/`yona.issue.List.js`(2026-09-18 재검증으로 정정 — `Subtask`/`TitleHeadAutoCompletion`은 TomSelect를 직접 호출하지 않아 목록에서 뺐고, `project.New`/`issue.List`를 추가함) | 서드파티 tom-select 래퍼. 재사용도 최고. 조상 `<form>` 제출과 얽혀 있어 로그인 위젯 때 겪은 "폼 참여" 함정 재현 가능성 |
| 4 | `yona.Tasklist.js` | 138줄 | 4곳: `board/view.html`, `common/commentUpdateForm.html`, `issue/view.html`, `site/layout.html` | 렌더링된 마크다운 안 체크박스 리스트 상호작용. 상태 단순, 이식 난이도 낮음 — **빠른 승리(quick win) 후보** |
| 5 | `yona.TitleHeadAutoCompletion.js` | 249줄 | 3곳: `board/create.html`, `issue/edit.html`, `issue/create.html` | 이슈/게시글 제목 자동완성 |
| 6 | `yona.ReceiverList.js` + `yona.WatcherList.js` | 94+50줄 | 1곳: `issue/view.html` | 참여자/지켜보는 사람 목록. 재사용도 낮아 우선순위 낮음 |
| 7 | `yona.ui.Calendar.js` | 94줄 | 1곳 | 날짜 선택기. 낮은 우선순위 |
| 8 | ~~`yona.ui.Mergely.js`~~ | 169줄 | 0곳(진짜 죽은 코드, 확인 후 파일 삭제함 — 2026-09-18) | v0.5.4 release notes에 "Remove mergely.js"로 명시된, 그보다도 훨씨 전에 없어진 side-by-side diff 뷰어 기능의 잔해. `$.fn.mergely` 플러그인도 저장소에 없어 애초에 실행 불가능했다 |
| 9 | `yona.ui.Tabs.js` | 46줄 | **살아있음**(2026-09-18 3차 재검증으로 정정 — 아래 참고) | 탭 클릭 시 마지막 선택 인덱스를 localStorage에 저장하는 로직뿐. 복원(`_restoreTab`)은 legacy부터 있던 버그로 의도적으로 no-op. 로직 자체가 얇아 Vue로 뽑을 실익은 낮음(우선순위는 낮게 유지) |

## 추천 착수 순서

1. **Tasklist** — 빠른 승리, 리스크 낮음
2. **TomSelect** — 재사용도 최고, 폼 참여 함정만 미리 대비
3. **Files** — 규모는 크지만 명확한 단일 컴포넌트
4. **CodeCommentBox/Block** — 가장 복잡, 마지막 순서 추천

## markdownEditor(공용 프래그먼트) 재검증 (2026-09-18, 실측으로 결과 정정)

**이전에 기록한 "Shadow DOM이 textarea를 캡슐화해서 소비처가 깨진다"는 판단은 최신 코드
기준으로 틀렸다.** 실서버에 로그인해 실제 렌더링된 DOM을 찍어보고 소비 파일 6개를 전부
읽어 재검증한 결과는 다음과 같다.

- `-vue` 구현(`components/vue-widgets/src/editor/element.ts`)은 `connectedCallback()`에서
  **host의 라이트 DOM 자식으로 실제 `<textarea name=... data-editor-mode=... markdown="true">`를
  직접 만들어 붙인다.** Shadow DOM 안이 아니다 — 이미 이 문제를 해결해 놓은 상태였다.
- 실제 vendor(`<yona-markdown-editor>`, 현재 공용 프래그먼트가 쓰는 것)가 만드는 textarea를
  실측해보니 속성이 `name/id/data-editor-mode/markdown/style`뿐이고, `-vue` 쪽도 정확히
  같은 속성을 만든다 — **둘이 동일한 DOM 계약**이라 바꿔 꽂아도 깨질 이유가 없다.
- `data-editor-mode`를 쓰는 실제 소비 파일은 6개(`yona.issue.View.js`, `yona.milestone.Write.js`,
  `yona.pullrequest.Write.js`, `yona.code.SvnDiff.js`, `yona.board.View.js`, `yona.code.Diff.js`)이고,
  이 중 5개는 `textarea[data-editor-mode="..."]`만 쓰는 단순 셀렉터라 두 구현 모두에서
  이미 정상 동작한다.
- 예외 하나(`yona.temporarySaveHandler.js:48`, `textarea.content[data-editor-mode='update-comment-body']`)는
  `.content` 클래스를 요구하는데, **이건 Vue와 무관한 별개의 기존 버그다.** v1.6 원본
  (`app/views/common/editor.scala.html:53`)의 textarea는 `class="editorSeries content comment nm"`을
  가졌는데, Kotlin/Spring 재작성 시 vendor/`-vue` 구현 둘 다 이 클래스를 안 붙인다 —
  즉 재작성 시점부터 이미 깨져 있던 것이고 vendor를 그대로 써도 마찬가지로 깨진다(임시저장
  초안 복구가 update-comment-body 케이스에서 항상 동작 안 함, 별도 이슈로 다뤄야 함).

**결론**: 공용 프래그먼트를 `-vue`로 바꿔도 확인한 6개 소비처는 다 정상 동작할 것으로
보인다. 남는 건 원래부터 깨져 있던 `.content` 클래스 셀렉터 버그(마크다운 에디터 교체와
무관, 별도 수정 필요)뿐이다. 다음 단계로는 (1) 실제로 프래그먼트를 `-vue`로 바꿔 e2e로
검증, (2) `.content` 클래스 누락 버그를 별도로 수정하는 것을 추천한다.

## 2차 재검증 (2026-09-18, "또 틀린 거 있나" 점검)

markdownEditor 외에 이 문서의 다른 항목들도 다시 훑어서 확인했다.

- **틀렸던 것**: `yona.ui.Tabs.js`(위 9번 항목, 수정 완료 — **하지만 이 정정 자체가 또 틀렸었다.
  아래 3차 재검증 참고**) `yona.ui.TomSelect.js`(위 3번 항목, 수정 완료) — 재사용 파일 목록에
  실제로는 TomSelect를 호출하지 않는 `Subtask`/`TitleHeadAutoCompletion`이 끼어 있었고, 실제
  소비 파일인 `project.New`/`issue.List`가 빠져 있었다.
- **맞았던 것**: `yona.Files.js`(919줄/8곳), `yona.CodeCommentBox.js`+`CodeCommentBlock.js`
  (322+529줄/5곳), `yona.Tasklist.js`(138줄/4곳) 줄수·재사용처는 `wc -l`/grep 재확인 결과
  전부 정확했다. `yona.ui.Mergely.js`도 실제로 죽은 코드임을 재확인(참조 0건, 이후 3차
  재검증에서 파일 삭제까지 진행). 이번 세션에 고친 `usermenu.css`의 `.sidenav .nav-tabs.nm`
  패딩도 다른 8곳의 `.nav-tabs.nm` 사용처(전부 `.sidenav` 조상이 없는 페이지 본문 탭)에는
  안 걸리는 걸 재확인해 부작용 없음을 확인했다.

## 3차 재검증 (2026-09-18, 사용자가 "Tabs.js는 우리가 포팅한 걸로 아는데" 지적)

**2차 재검증에서 "Tabs.js는 죽은 코드"라고 고친 것 자체가 틀렸다.** 근거로 든 "이 파일을
로드하는 `<script src>`가 템플릿에 없다"는 검사 방법이 잘못됐다 - 이 프로젝트는 일부
`common/yona.*.js` 소스를 별도 빌드 산출물인 `/javascripts/yona-lib.js`(개별 `<script>`
태그 없이 미니파이된 번들, `site/layout.html:667`에서 로드)로 합쳐서 배포하는데, 파일명
문자열만 grep해서는 이 번들 안에 로직이 들어있는지 알 수 없다.

실제로 `yona-lib.js`에 `localStorage.setItem("yonatab-"+r,n)`(미니파이된 형태)가 그대로
들어있어 `yona.ui.Tabs.js`의 `_init` 로직(탭 클릭 시 `localStorage.setItem("yonatab-" +
sContainerId, nIndex)`)이 살아서 매 페이지에서 실행되고 있음을 확인했다(관련 리팩터
커밋 `0eed3be55` "tab 카테고리를 vanilla JS로 전환" - jQuery `.tab()` 플러그인 제거
작업의 일부로 이미 vanilla로 옮겨져 있었다). 반면 `yona.ui.Mergely.js`는 같은 번들에
"mergely" 문자열이 0건이라 이 경로로도 죽은 코드임이 재확인됐다 - 그래서 Mergely만 삭제하고
Tabs는 그대로 뒀다.

**교훈**: 이 저장소에서 "어떤 JS 파일이 죽었는지" 판단할 때는 템플릿의 `<script src>`
grep만으로 부족하고, `yona-lib.js`(및 다른 번들 산출물) 안에 해당 로직의 distinctive한
문자열이 들어있는지까지 확인해야 한다.
