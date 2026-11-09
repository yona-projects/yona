---
title: 마크다운 입력란 GitHub 방식 (현황 보고)
type: architecture
sources:
  - markdown-editor-trial.md
  - ../decisions.md
updated: 2026-11-09
---

# 마크다운 입력란 GitHub 방식 (현황 보고, 2026-11-09)

결정은 `D-105`(textarea와 서버 미리보기를 유지하고 웹 컴포넌트로 보강), `D-106`(서식 단추와 자동완성 방향)이다. 시험 근거는 `markdown-editor-trial.md`에 있다.

## 끝난 것
- **text-expander-element 재확인**: At.js 패치 네 건(IME, 2단 보기, Alt 키, 캐럿)을 Chromium과 Firefox로 확인했다. 모두 대체로 만족한다. 남은 것은 두 가지다.
  - Safari형 조합 중 Enter가 후보를 고르는 문제(원인: 후보 갱신 때마다 `combobox-nav`를 새로 만들어 조합 상태가 초기화됨). 캡처 단계 keydown 한 줄(`e.isComposing || e.keyCode === 229`이면 `stopPropagation`)로 막힌다.
  - Alt+Enter: `combobox-nav`가 Alt, Shift, Meta가 눌린 키를 무시해서 삽입되지 않는다(At.js는 삽입). 오류는 없다.
- **서식 단추 구현**(`b851619fd`): `markdown-toolbar-element` 2.2.3을 번들 없이 `type="module"`로 붙였다. 단추 열 개(제목, 굵게, 기울임, 인용, 코드, 링크, 글머리 기호 목록, 번호 목록, 멘션, 참조). 실제 앱에서 두 브라우저로 21개 항목을 확인했다.
  - 단추 모듈이 textarea에 남기는 `contenteditable="false"`를 At.js가 편집 가능 요소로 오인해 멘션이 열리지 않던 문제를 At.js 판별식 한 줄로 고쳤다.
  - `yona.Mention.js`가 키보드가 아닌 방법으로 들어온 `@`, `#`, `:`(input 이벤트의 data)에도 시작하도록 고쳤다.

## 한계
- 이 서식 단추 모듈에는 단축키(Ctrl+B 등)가 없다.
- 단추 모양은 아이콘 글꼴 없이 글자로 했다.
- 실제 한글 입력기로 text-expander를 쓰는 확인은 하지 않았다(선생님 확인 필요).
- 실제 2단 보기(`pageslide`) 화면에서 text-expander는 확인하지 않았다.

## 남은 일
1. 본문 멘션을 At.js에서 text-expander-element로 실제 교체한다(Safari 보호막 한 줄 포함).
2. Alt+Enter를 삽입으로 맞출지 정한다.
3. 단추 모양과 단축키.
