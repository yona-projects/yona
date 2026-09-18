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
| 3 | `yona.ui.TomSelect.js` | 374줄 | 7곳: `issue.Write`/`issue.View`/`issue.Assginee`/`issue.Sharer`/`board.View`/`Subtask`/`TitleHeadAutoCompletion` | 서드파티 tom-select 래퍼. 재사용도 최고. 조상 `<form>` 제출과 얽혀 있어 로그인 위젯 때 겪은 "폼 참여" 함정 재현 가능성 |
| 4 | `yona.Tasklist.js` | 138줄 | 4곳: `board/view.html`, `common/commentUpdateForm.html`, `issue/view.html`, `site/layout.html` | 렌더링된 마크다운 안 체크박스 리스트 상호작용. 상태 단순, 이식 난이도 낮음 — **빠른 승리(quick win) 후보** |
| 5 | `yona.TitleHeadAutoCompletion.js` | 249줄 | 3곳: `board/create.html`, `issue/edit.html`, `issue/create.html` | 이슈/게시글 제목 자동완성 |
| 6 | `yona.ReceiverList.js` + `yona.WatcherList.js` | 94+50줄 | 1곳: `issue/view.html` | 참여자/지켜보는 사람 목록. 재사용도 낮아 우선순위 낮음 |
| 7 | `yona.ui.Calendar.js` | 94줄 | 1곳 | 날짜 선택기. 낮은 우선순위 |
| 8 | `yona.ui.Mergely.js` | 169줄 | 0곳(템플릿에서 참조 못 찾음) | 죽은 코드일 가능성 — 포팅 전에 실제 사용 여부 재확인 필요 |
| 9 | `yona.ui.Tabs.js` | 46줄 | `.nav-tabs` 마크업이 템플릿 31곳 | 로직 자체는 매우 얇음(부트스트랩 `data-toggle=tab`). 이번 세션에서 고친 사이드바 탭 버그가 이 패턴이지만, Vue로 뽑을 상태/복잡도는 거의 없어 "분리 대상"으론 낮은 순위 |

## 추천 착수 순서

1. **Tasklist** — 빠른 승리, 리스크 낮음
2. **TomSelect** — 재사용도 최고, 폼 참여 함정만 미리 대비
3. **Files** — 규모는 크지만 명확한 단일 컴포넌트
4. **CodeCommentBox/Block** — 가장 복잡, 마지막 순서 추천

## markdownEditor(공용 프래그먼트) 재확인

여전히 막혀 있다. 템플릿 2곳이 vendor `<yona-markdown-editor>`를 직접 참조하고, JS 6곳이
`textarea[data-editor-mode=...]`를 직접 querySelector한다(이전 메모의 "14곳 이상"과는
집계 방식이 달라 재검증 필요). 근본 원인은 동일: `<yona-markdown-editor-vue>`의 Shadow DOM이
실제 textarea를 캡슐화해서 외부 querySelector 소비처가 깨진다. review-form은 이 공용
프래그먼트와 무관한 자기 전용 인스턴스라 이미 포팅되어 있음(관련: `project_yona_widget_light_dom_escape_hatch`,
`project_yona_permanent_widget_deployment` 메모).
