---
title: v1.6 결함 수정 결과 (담당 W1: 게시판, 이전, 시작 안내, 검색, 공통 틀)
type: ui-contract-verification
updated: 2026-11-11
---

# W1 결과

선생님 지시(A-462)로 "v1.6 결함 유지"였던 43건을 분류하고 고쳤다. 근거는 `A-board-wiki-search-misc.md`와 `F-shell.md`이다.

| 분류 | 개수 |
|---|---|
| FIX (고침) | 14 (shell-F23은 이미 A-460에서 고쳐져 있어 시험 확인만 했다) |
| SKIP (그대로 둠) | 24 |
| ASK (확인을 받음) | 5 |

돌린 시험(모두 통과): `SiteSecretTest`(아래 주의 참조), `BoardWriteTest`, `BoardViewTest`, `AccessControlTest`, `TemplateContractRegressionTest`, `AttachmentUploadTest`, `AttachmentControllerTest`, `UserMenuTest`, `dev.yona.web.search.*`, JS `shortcutkey`, `usermenu`, `migration`.
주의: `SiteSecretTest`의 "정하면 기본 관리자 계정이 바뀌고 ..." 한 건은 이번 수정과 무관하게 실패한다(수정 전 소스에서도 같다. 기대한 비밀번호 해시 값이 argon2id 형식으로 바뀌었기 때문이다). 이 한 건을 빼고 모두 통과했다.

## 항목별 결과

| 항목 | 분류 | 한 일 또는 이유 | 커밋 | 시험 |
|---|---|---|---|---|
| board-F4 | FIX | v1.6은 글쓰기 화면은 조직 관리자와 그룹 구성원에게도 공지 선택을 보이나 저장과 수정은 프로젝트 직접 구성원만 받아 무시했다. 지금은 화면과 같은 판정(`isProjectResourceCreatable`)을 저장과 수정에도 쓴다 | 897f4ad | BoardWriteTest |
| board-F5 | FIX | v1.6은 제목 오류 때 본문과 제목을 비우거나 저장된 값으로 되돌렸다. 지금은 제출한 본문과 제목을 오류 화면에 돌려준다(글쓰기, 수정 모두) | 897f4ad | BoardWriteTest |
| board-F6 | SKIP | 쪽 이동 링크가 `pageNum` 속성 없이 href로만 동작하나 이동이 정상이고 사용자가 겪는 오동작이 없다 | | |
| board-F9 | SKIP | 구독 해제가 303을 따라가 안내 메시지가 버려지는 것은 동작에 영향이 없는 표시 누락이다 | | |
| board-F10 | SKIP | 게시판 메뉴가 꺼진 프로젝트에도 글쓰기 링크는 유효한 주소이고, N 키맵 부재는 단축키 안내일 뿐이다 | | |
| board-F11 | FIX | v1.6은 번호만으로 아무 프로젝트의 라벨이나 글에 붙였다. 지금은 글이 속한 프로젝트의 라벨이 아니면 400 `Wrong label id` | 897f4ad | BoardWriteTest |
| board-F12 | SKIP | 주소의 프로젝트와 글 번호를 대조하지 않으나 권한은 댓글의 실제 프로젝트로 판단해 권한 우회가 없다 | | |
| board-F13 | FIX | v1.6은 읽기 권한만 보고 댓글 수정 단추를 보여 눌러도 저장 단추가 없는 폼이 열렸다. 지금은 수정 권한이 있을 때만 단추를 보인다 | 71af05b | BoardViewTest |
| board-F14 | FIX | v1.6은 비공개 프로젝트에서 구성원이 아니게 된 작성자가 글 보기와 삭제는 막히는데 수정은 열려 있었다. 지금은 작성자 권한을 그 프로젝트를 볼 수 있는 사용자(공개 프로젝트, 구성원, 그룹 구성원)에게만 준다. 공개 프로젝트의 비구성원 작성자는 v1.6처럼 자기 글을 고치고 지운다 | 2fae11a | AccessControlTest |
| board-F16 | SKIP | tabindex 숫자가 불규칙하나 탭 이동 순서는 화면 순서와 같아 동작에 문제가 없다 | | |
| migration-F1 | SKIP | GitHub OAuth 앱 client id 고정은 Yona 공식 앱의 값이며 설정으로 바꾸게 하는 것은 새 기능이다 | | |
| migration-F2 | ASK | 아래 ASK 절 참조 | | |
| migration-F3 | FIX | v1.6은 조직 대상이면 작업 계정 토큰을 쓰려 했으나 그 토큰을 정하는 곳이 없어 빈 토큰(`token `)으로 GitHub를 호출했다. 지금은 늘 사용자 토큰을 쓴다 | c2100e3 | JS migration |
| migration-F4 | ASK | 아래 ASK 절 참조 | | |
| migration-F5 | SKIP | 세 번째 열이 다음 줄로 내려가는 배치이며 기능 오동작이 아니다 | | |
| migration-F6 | SKIP | 첨부 이전 미지원은 콘솔 기록만 남는 설계상 한계이며 v1.6의 예외보다 낫다 | | |
| migration-F8 | SKIP | 실패 시 오류 데이터는 화면에 보이며(관리자용 이전 도구) 형식만 거칠다. 서버 403 안내는 새 화면 설계가 필요해 별도로 다룬다 | | |
| welcome-F1 | FIX | v1.6은 `loginId`나 `password` 항목이 없으면 NPE로 500이었다. 지금은 빈 값과 같이 필수 오류 400 | 56cd0b8 | SiteSecretTest |
| welcome-F2 | SKIP | 초점 이동이 없는 것은 오동작이 아니다 | | |
| welcome-F3 | ASK | 아래 ASK 절 참조 | | |
| welcome-F4 | SKIP | 모바일 폭 비율은 v1.6과 같은 규칙이며 화면 모양 문제이다 | | |
| search-F2 | SKIP | 잘못된 요청에 400을 주는 것은 의도된 동작이다 | | |
| search-F3 | SKIP | 0건 안내 문구 형식이며 오류가 아니다 | | |
| search-F5 | SKIP | 0건에도 탭이 링크인 것은 의도된 동작이다 | | |
| search-F6 | FIX | v1.6은 `searchType=auto`로 들어온 결과의 쪽 링크가 auto 그대로여서 다음 쪽에서 다른 종류로 풀릴 수 있었다. 지금은 결과를 그리기 전에 주소의 종류를 실제로 보인 종류로 바꾸어(history.replaceState) 쪽 링크가 종류를 지킨다 | 392c511 | TemplateContractRegressionTest(소스 계약) |
| search-F7 | SKIP | 아바타 크기 속성 누락은 CSS가 크기를 정해 화면에 영향이 없다 | | |
| shell-F4 | FIX | v1.6은 인자 하나짜리 정렬 함수 때문에 등록한 `CTRL+SHIFT+ENTER`가 `SHIFT+CTRL+ENTER`로 바뀌어 실제 입력과 맞지 않았다. 지금은 보조키를 ALT, CTRL, SHIFT 순으로 두고 주 키를 뒤에 둔다 | a61a71d | JS shortcutkey |
| shell-F5 | SKIP | 도움말 키 안내와 실제 처리기 등록 조건의 불일치이며 동작 오류가 아니다 | | |
| shell-F6 | FIX | v1.6은 사이드바 고정과 복원에서 주소의 쿼리(필터, 쪽)를 잃었다. 지금은 `pathname + search`를 인코딩해 넘긴다(서버는 `path`를 그대로 받는다) | 0365c1c | TemplateContractRegressionTest(소스 계약) |
| shell-F7 | SKIP | 사용자 메뉴 목록 읽기 자체가 서버 오류인 상황이며 콘솔에 기록이 남는다. 빈 사이드바를 여는 것이 더 낫지 않다 | | |
| shell-F8 | SKIP | 새 탭으로 여는 것은 의도된 동작이다(결과 파일도 같은 판정) | | |
| shell-F9 | SKIP | 이전 화면에 사용자 메뉴 스크립트가 없는 것은 설계상 차이이다 | | |
| shell-F10 | ASK | 아래 ASK 절 참조 | | |
| shell-F11 | SKIP | 중복 id는 마크업상 결함이나 쓰는 쪽이 클래스나 범위 선택자라 사용자 영향이 없다. 바꾸면 스크립트 계약이 깨질 위험이 더 크다 | | |
| shell-F12 | FIX | v1.6은 화면 스크립트만 `maxFileSize`를 검사해 직접 요청은 크기와 상관없이 받았다. 지금은 서버에서도 한도를 넘으면 413 | 7c3ac02 | AttachmentUploadTest |
| shell-F13 | SKIP | v2는 이미 비공개 프로젝트의 이슈 제목 노출을 막았다(R-16). 공개 프로젝트의 마크다운 렌더링은 공개 정보이다 | | |
| shell-F15 | SKIP | 이름만 오해하기 쉬운 v1.6 동작이며 결함이 아니다 | | |
| shell-F17 | SKIP | 404 화면의 고정 링크는 설정과 맞물린 설계이며 오동작이 아니다 | | |
| shell-F20 | FIX | v1.6은 즐겨찾기 요청이 JSON이 아닌 본문(400, 500 HTML)으로 실패하면 `JSON.parse` 예외로 알림창 없이 아무 반응이 없었다. 지금은 JSON이면 `reason`, 아니면 본문 글자를 알림창에 보인다 | dcd7dbb | JS usermenu |
| shell-F23 | FIX | 익명이 `authorLoginId`로 남의 이름으로 올릴 수 있던 것을 사이트 관리자만 명의를 지정하게 막았다. 이 담당이 하기 전에 A-460으로 이미 고쳐져 있어 새 변경이 없다 | 64a4eb0 (A-460, 이전 작업) | AttachmentUploadTest |
| shell-F25 | SKIP | Ctrl+A와 Ctrl+Enter의 입력란 안팎 분기는 의도된 동작이다 | | |
| shell-F26 | FIX | v1.6은 `mailto:`도 바깥 링크로 보아 `target=_blank`를 붙여 빈 새 탭이 떴다. 지금은 `mailto:`와 `tel:`을 제외한다 | f4be6b6 | TemplateContractRegressionTest(소스 계약) |
| shell-F27 | ASK | 아래 ASK 절 참조 | | |

## 확인을 받을 항목 (ASK)

**migration-F2: 이전 화면의 "작업 계정" 경고 문구(`{0}` 자리가 빈 글자)**
- 상황: 경고 4종(`migration.noWorkerInAdmin`, `workerNeeded`, `bothNotAdmin`, `userUsedInstead`)이 `CONFIG.DEFAULT_WORKER`를 문구에 넣지만 그 값은 v1.6에도 없었고 지금도 없다. 그래서 "Admin에  유저가 없음"처럼 빈 자리가 보인다. migration-F3 수정으로 작업 계정 개념이 실제로는 쓰이지 않게 되었다.
- 선택지: (A) 작업 계정 경고를 화면에서 모두 없앤다(조직 관리자 경고만 남긴다). (B) 작업 계정 이름을 설정 값으로 받는다(새 기능). (C) 그대로 둔다.
- 권장: A. 이유: 값을 정하는 곳이 없어 경고가 항상 깨진 문장이고, 사용자 토큰만 쓰는 지금 동작과도 맞지 않는다. 다만 화면 요소를 없애는 일이라 선생님 확인이 필요하다.

**migration-F4: 이전을 시작하면 먼저 `yona-projects/yona-migration-log` 이슈를 GitHub에 만든다 (안내 없음)**
- 상황: 마일스톤, 이슈 이전을 누르면 사용자 토큰으로 외부 저장소(공식 Yona 프로젝트)에 이전 기록 이슈를 올린다. 사용자에게 알리는 문구가 없다.
- 선택지: (A) 이전 확인 창에 "이전 기록이 yona-projects/yona-migration-log에 남습니다" 안내를 더한다. (B) 이 기록 기능을 없앤다. (C) 그대로 둔다.
- 권장: A. 이유: 기능은 v1.6의 의도된 동작(공식 통계 수집)이므로 없애지 않고, 외부로 무엇이 나가는지만 알리면 된다. 문구 추가는 메시지 키 5개가 늘어 확인을 받는다.

**welcome-F3: 최초 관리자 비밀번호가 한 글자여도 통과한다**
- 상황: `p` 한 글자로 설정이 끝난다. 그러나 앱 전체에 비밀번호 길이나 강도 규칙이 없다(회원 가입, 변경도 같다).
- 선택지: (A) 앱 전체에 최소 길이 정책을 도입한다(새 정책). (B) 최초 관리자에만 최소 길이를 둔다. (C) 그대로 둔다.
- 권장: A를 별도 항목으로. 이유: 이 화면만 막으면 일관성이 없고, 정책 도입은 기능 추가이므로 `decisions.md`에 선택지를 두고 정해야 한다.

**shell-F10: 한국어 화면에 영어로 고정된 문구(`More`, `Sidebar` 툴팁, `Draft saved`, `set Default page failed` 등)**
- 상황: v1.6 템플릿과 스크립트에 하드코딩된 영어 문구이다. 메시지 키와 5개 언어 값이 필요하다.
- 선택지: (A) 모두 메시지 키로 바꾼다. (B) 사용자에게 자주 보이는 것(`More`, `Sidebar`)만 바꾼다. (C) 그대로 둔다.
- 권장: A. 이유: 한국어, 일본어 등 사용자가 영어를 보게 되는 명백한 번역 누락이다. 다만 문구 수가 많고 5개 파일과 `MessageParityTest`를 건드려 범위 확인을 받는다.

**shell-F27: `$yona.alert`와 `$yona.notify`가 HTML을 그대로 해석한다**
- 상황: 인자의 HTML이 그대로 렌더되어 사용자 값이 섞이면 스크립트가 실행될 수 있다(`escapeHtml`은 v2에서 추가되었으나 호출부가 쓰는지는 곳곳에 달려 있다).
- 선택지: (A) 두 함수가 기본으로 글자 처리하고, HTML이 필요한 호출부만 `{html: true}` 옵션을 쓰게 바꾼다(호출부 전수 조사 필요). (B) 호출부를 하나씩 점검해 사용자 값을 넘기는 곳만 `escapeHtml`로 감싼다. (C) 그대로 둔다.
- 권장: B. 이유: A는 API 계약(HTML을 넣는 기존 호출부 다수)을 바꿔 화면이 깨질 위험이 크고, B는 위험 지점만 줄이면서 계약을 지킨다. 호출부 점검 범위가 커서 확인을 받는다.

## v1.6과 달라진 점 (`differences-from-v16.md`로 옮길 한 줄 요약)

- welcome-F1: v1.6은 항목 누락에 500, 지금은 필수 오류 400.
- board-F4: v1.6은 조직 관리자에게 공지 선택을 보이나 저장 무시, 지금은 저장과 수정에 반영.
- board-F5: v1.6은 오류 화면에서 본문 소실, 지금은 제출한 값 유지.
- board-F11: v1.6은 다른 프로젝트 라벨이 붙음, 지금은 400.
- board-F13: v1.6은 읽기 권한만으로 댓글 수정 단추 노출, 지금은 수정 권한이 있을 때만.
- board-F14: v1.6은 비공개 프로젝트에서 구성원이 아니게 된 작성자의 수정이 열림, 지금은 막힘(작성자 권한을 볼 수 있는 사용자에게만, 이슈와 댓글 등 작성자 규칙 전반에 적용).
- migration-F3: v1.6은 조직 대상에 빈 토큰, 지금은 사용자 토큰.
- search-F6: v1.6은 auto 쪽 이동에서 종류가 바뀔 수 있음, 지금은 실제 종류로 주소를 고정.
- shell-F4: v1.6은 CTRL+SHIFT+ENTER 동작 안 함, 지금은 동작.
- shell-F6: v1.6은 사이드바 고정에서 쿼리 소실, 지금은 유지.
- shell-F12: v1.6은 서버에서 크기 미검사, 지금은 413.
- shell-F20: v1.6은 JSON이 아닌 실패 응답에 무반응, 지금은 본문 안내.
- shell-F26: v1.6은 mailto 링크가 빈 새 탭, 지금은 같은 창(메일 앱).
