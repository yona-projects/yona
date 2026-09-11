# P3-46 항목8 전면 재작업 계획: CM6 기반 Web Component 마크다운 에디터

> 이 문서는 승인된 실행 계획(설계 결정 + 0~7단계 로드맵)을 담은 **정적 참고 문서**입니다.
> 실제 진행 상황·완료 로그·발견 사항은 [[p3-46]] 티켓 본문의 "8번 항목 방향 전환" 절 이하를
> 참고하세요 — 이 문서는 "무엇을 하기로 했는가"만 담고, "무엇을 했는가"는 적지 않습니다
> (내용이 바뀌면 이 문서를 갱신하되, 완료 로그는 절대 여기 섞지 않습니다).

## Context

yona(Spring Boot/Kotlin/Thymeleaf) 프로젝트의 P3-46(클라이언트 위젯 라이브러리 현대화) 항목8에서
마크다운 에디터를 EasyMDE(CodeMirror5)+Tribute.js로 교체하는 작업을 진행하던 중(1단계 셸 교체,
2단계 서버 미리보기 연동은 완료·검증됨), 3단계(멘션 연동)에서 CodeMirror5의 구조적 결함(숨겨진
`<textarea>`가 백스페이스/Delete 직후 순간적으로 비어 Tribute.js가 컨텍스트를 잃는 문제,
zurb/tribute#511)에 부딪혔다. 완화책을 시도했으나 "줄 중간 타이핑 시 문서 내용이 반복
중복·붕괴되는" 훨씬 심각한 회귀를 유발해 비활성화한 상태다.

이후 대안 조사(CKEditor5/Toast UI Editor/Milkdown/CodeMirror6 자체구축/전면 빌드파이프라인
도입/Web Component 분리)를 순차적으로 진행했고, CodeMirror6은 이 백스페이스 버그의 근본 원인
자체가 구조적으로 없다는 것과(`.cm-content`라는 실제 contentEditable을 직접 씀), Web Component로
격리하면 "빌드 도구 없이 자체호스팅"이라는 이 프로젝트의 원칙을 프로젝트 전체 차원에서 깨지
않고도(컴포넌트 자체만 별도로 빌드, 산출물만 vendoring) CM6를 도입할 수 있다는 결론에
도달했다. 사용자가 최종적으로 "어차피 바꾸는 거 제대로" — 즉 Shadow DOM + CSS 커스텀
프로퍼티/`::part()` 표준 테마 계약을 실제로 설계하는, yona의 향후 Web Component화 방향의 첫
사례로 이 작업을 진행하기로 결정했다.

**목표**: EasyMDE+CodeMirror5+Tribute.js를 전부 걷어내고, CodeMirror6 기반의 자체 제작 Custom
Element(`<yona-markdown-editor>`)로 교체한다. 컴포넌트는 별도 격리된 npm 프로젝트
(`yona-projects/components` 저장소, `editor/` 디렉터리)에서 TypeScript+esbuild로 빌드하고,
산출물(단일 번들 파일)만 기존 vendoring 관례대로 yona의 `static/javascripts/lib/`에 커밋한다 —
메인 Gradle 빌드/CI에는 어떤 흔적도 남기지 않는다.

## 확정된 설계 결정

- **소스 언어**: TypeScript (사용자 확정).
- **테스트 전략 전환(사용자 승인 완료)**: Shadow DOM 내부 동작(툴바 클릭, 멘션 드롭다운 등)은
  Jsoup/MockMvc로 검증 불가능하므로, 3단계 이후부터는 **Playwright가 사실상 1차 회귀
  방지선**이 된다. Jsoup 스펙은 서버가 내려주는 초기 마크업(속성값 등)만 보조적으로 검증한다.
- **DOM 구조**: light DOM에 진짜 `<textarea name="...">`를 유지(기존 폼 제출 코드가 `.val()`로
  직접 읽는 구조와의 호환 — Form-Associated Custom Elements는 이 프로젝트엔 이점이 없음을 이미
  확인함). Shadow DOM 안에 CM6 EditorView+툴바+미리보기 패널을 배치하는 하이브리드 구조.
- **CSS 테마 계약**: `::part()`(toolbar/button/button-{command}/separator/editor/preview/
  notice-label)와 CSS 커스텀 프로퍼티(`--yona-md-*`)로 노출. 컴포넌트 자체 기본값이 지금의
  yobi 룩과 동일하게 나오는 것이 1차 목표(동치성). `@font-face`로 등록된 `yobicon` 폰트는
  Shadow DOM 경계를 넘어 참조 가능하므로 재선언 불필요.
- **범위 밖(건드리지 않음)**: `MarkdownController.kt`/`MarkdownServiceImpl.kt`(서버 렌더링
  파이프라인, 그대로 재사용), `yona.TitleHeadAutoCompletion.js`(`[` 트리거 — 라벨 자동완성,
  일반 `<input>`용 별도 Tribute 인스턴스, CodeMirror와 무관해 이번 문제와도 무관. 계속 필요한
  기능이고 계속 동작함 — 단지 이번 마이그레이션 대상이 아닐 뿐, 향후 Tribute.js 전체를
  정리할지는 별도 논의로 미룸), `help/markdown.html` 프래그먼트, `editorMode` 값(이미 죽은
  값으로 확정, textarea `data-editor-mode` 패스스루만 유지).

## 단계별 실행 계획 (0~7단계, 각 단계=커밋 경계=롤백 단위)

### 0단계 — Shadow DOM 스파이크 (go/no-go 게이트, 최우선)

CM6 EditorView를 실제 Shadow DOM에 마운트했을 때 Chromium/Firefox/WebKit 3개 엔진 전부에서
스타일·캐럿·선택 영역·타이핑이 정상 동작하는지 최소 단위로 검증한다. 알려진 함정:
CM6의 `StyleModule.mount()`가 스타일을 어디에 주입하는지, `root` 옵션 유무에 따른
`Cannot read properties of null (reading anchorNode)` 재현 여부, `ShadowRoot.getSelection()`이
Chromium 전용이라 Firefox/WebKit에서 캐럿 계산이 깨질 가능성.

- 산출물: `editor/spike/`(메인 앱과 완전히 격리된 실험 코드, esbuild로 CM6만 번들한 최소
  HTML+JS).
- 검증: Playwright `chromium`/`firefox`/`webkit` 3개 프로젝트로 타이핑/캐럿이동/드래그선택/
  구문강조 색상 실측.
- **실패 시 즉시 중단하고 보고** — 폴백 경로(Plan B: light DOM 완전 전환 + BEM CSS 클래스,
  Plan C: 툴바/미리보기만 Shadow DOM 유지)를 함께 제시하고 사용자 재확인 후 진행.

### 1단계 — 빌드 인프라

`editor/`(package.json — `@codemirror/state`/`view`/`commands`/`lang-markdown`/`autocomplete`/
`language` + devDependency `esbuild`, `tsconfig.json`, esbuild 빌드 스크립트, README — Node
버전/빌드 절차/"메인 Gradle과 무관, 로컬 수동 빌드 후 산출물만 커밋" 원칙 명시, `.gitignore`).
최소 골격(빈 Custom Element 등록)까지만. 최종 배포 위치: yona 저장소의
`static/javascripts/lib/yona-markdown-editor/yona-markdown-editor.min.js`.

검증: `npm run build` 성공, 정적 HTML에 얹어 브라우저 콘솔 에러 없이 커스텀 엘리먼트 등록되는지
Playwright 스모크.

### 2단계 — 셸 (폼 통합 + 기본 편집)

`markdownEditor` 프래그먼트를 `<yona-markdown-editor>`로 교체(아직 툴바/미리보기/멘션 없음).
light DOM textarea ↔ Shadow DOM CM6 양방향 동기화(CM6 변경 시 textarea.value 갱신 +
input/keyup 재발행 — 임시저장 시스템 의존). `easymde.min.js`/`yobi.ui.MarkdownEditor.js` 삭제.

검증: `MarkdownEditorShellWidgetTemplateEquivalenceSpec`을 새 마크업 계약(yona-markdown-editor
리소스 로드, light DOM textarea의 name/id/data-editor-mode 계약 유지)에 맞게 갱신, TDD로
RED→GREEN. Playwright로 타이핑→폼 제출 시 서버가 실제 값을 받는지 실증.

### 3단계 — 툴바

9개 커맨드(bold/italic/heading/quote/checklist/unordered-list/ordered-list/link/image)를
`EditorState.changeByRange()` 기반으로 새로 구현. `::part()`/CSS 커스텀 프로퍼티 테마 계약 적용,
`yobi.css`에 대응 규칙 소수 추가.

검증: Playwright로 각 버튼 클릭→마크다운 텍스트 결과 확인, 기존 EasyMDE 재스킨판과 스크린샷
시각 대조.

### 4단계 — 미리보기

`render-url` 속성 기반 서버 렌더링(`POST /markdown/{owner}/{name}`) 재연동, 기존
`_previewRenderer()`의 300ms 디바운스+요청순번 레이스가드 로직 이식.

검증: `MarkdownEditorPreviewWidgetTemplateEquivalenceSpec` 갱신(서버 렌더링 URL 매핑은 Jsoup
검증 가능, 8-2단계의 16개 화면/중첩 케이스 재사용). Playwright로 실제 AJAX/디바운스/연타 시나리오.

### 5단계 — 멘션

`@codemirror/autocomplete`로 `@`/`:`/`#` 3트리거 재구현(`[` 트리거는 범위 밖 — 위 "범위 밖"
절 참고). `yobi.Mention.js`의 순수 로직(이모지 배열, 커스텀 정렬/하이라이트)은 CM API 무관이라
그대로 재사용, `menuItemTemplate`/`selectTemplate`만 `Completion` 객체 형식으로 어댑터 작성.
착수 전 `@codemirror/autocomplete` API(특히 `renderOption` 커스텀 렌더링 자유도)를 먼저 확인.
`yobi.Mention.js` 완전 삭제(로직은 컴포넌트로 흡수), `yona.TitleHeadAutoCompletion.js`는 무변경.

검증: Playwright로 3트리거 전부 타이핑→드롭다운→선택 실증 + 회귀 방지 차원에서 백스페이스
직후 Enter, 후보 0개 상태 Enter를 재현해 "문제가 없음"을 직접 확인(CM6에서 구조적으로
해소됐다는 주장을 실측으로 뒷받침).

### 6단계 — 마이그레이션 (16개 markdownEditor 호출부 + 8개 멘션 호출부)

2~5단계에서 이미 프래그먼트 자체가 새 태그를 쓰므로, 이 단계는 8개 화면의 `yobi.Mention(...)`
인라인 스크립트 제거 + `mention-url` 속성 추가가 핵심 — 대규모 작업 아님. 위험도 낮은 화면군
(멘션 없는 8곳)부터, 멘션 있는 8곳(issue/board/milestone/pullrequest 생성·수정) 순으로 2개
배치 전환.

검증: 3개 스펙 전체 재실행 + 관련 컨트롤러 스펙(Issue/Board/PullRequest/Milestone) 개별 재실행.
착수 전 `yona-lib.js`에 남아있는 구식 `yobi.Mention`/atjs 사본이 완전히 죽어있는지(전역 이름
충돌 없는지) 재확인.

### 7단계 — 전면 회귀 + 브라우저 실사용 검증

16개 화면 개별 Playwright 확인(8-1단계 "수동 확인 필요" 목록을 자동화로 전환). 발견되는 버그는
고치거나 P3-49/52 패턴처럼 별도 티켓 분리.

## 원칙 적용

- 매 단계 = 별도 TDD 서브에이전트 위임(0~1단계는 아키텍처 결정 비중이 커서 메인 세션이 직접
  챙기거나 좁게 위임).
- 3원칙 유지: Playwright 실증 필수(3단계 이후는 사실상 유일한 1차 수단), 범위 확장 시 즉시
  중단·보고(0단계 스파이크 실패, 5단계 API 제약 발견 등을 사전 지정된 보고 지점으로 명시),
  죽은 코드 임의 수정 금지(`yona.TitleHeadAutoCompletion.js`/`help/markdown.html` 불가침 명시).
- gradle 테스트는 매 단계 신규/갱신 스펙만 `--tests "FQCN"`으로 개별 실행. **`--rerun-tasks`는
  데몬 강제종료 등 캐시를 의심할 구체적 사유가 있을 때만 사용** — 평소엔 일반 실행 후 로그의
  "N actionable tasks: N executed"와 `build/test-results/test/*.xml` 타임스탬프로 "진짜
  재실행됐다"를 확인한다(`--rerun-tasks`를 습관적으로 쓰면 이 프로젝트의 방대한 테스트 소스가
  매번 전체 재컴파일돼 불필요하게 오래 걸림).
- 메인 세션은 각 단계 완료 후 diff 직접 검토 + 스펙 재실행으로 독립 검증한 뒤에만
  [[p3-46]] 티켓 갱신 + 커밋/푸시(yona 저장소 + components 저장소 각각 별도 커밋).

## 롤백 지점

| 단계 | 롤백 |
|---|---|
| 0 | 실험 코드, 앱에 영향 없음 |
| 1 | 빌드 인프라만 추가, EasyMDE 무변경 — 언제든 폐기 가능 |
| 2 | 이 커밋부터 EasyMDE 삭제 시작 — revert 시 8-2단계 상태로 복귀 |
| 3~5 | 기능별 독립 — 해당 커밋만 되돌리면 이전(더 적은 기능) 상태로 복귀 |
| 6~7 | 화면군 단위 부분 롤백 가능 |

## 주요 참고 파일

**yona 저장소** (`yona-projects/yona`):
- `src/main/resources/templates/site/layout.html` (`markdownEditor`/`markdown(project)` 프래그먼트, `::scripts`)
- `src/main/kotlin/.../MarkdownServiceImpl.kt`, `MarkdownController.kt` (무변경, 재사용)
- `src/test/kotlin/.../MarkdownEditorShellWidgetTemplateEquivalenceSpec.kt`, `MarkdownEditorPreviewWidgetTemplateEquivalenceSpec.kt`, `MentionAutocompleteWidgetTemplateEquivalenceSpec.kt` (갱신 대상)
- `docs/parity/tickets/p3-46.md` (진행 상황/완료 로그 기록 — 이 계획 문서와 역할 분리)

**components 저장소** (`yona-projects/components`, 별도 클론 필요: `git clone git@github.com:yona-projects/components.git ~/yona-convert/components` — 로컬 경로는 세션마다 다를 수 있으니 원격 저장소 기준으로 클론할 것):
- `editor/src/YonaMarkdownEditor.ts` (컴포넌트 본체)
- `editor/src/commands.ts`, `editor/src/toolbar.ts` (3단계 이후 추가)
- `editor/spike/` (0단계 검증 기록, 참고용)
