---
title: 계약 조사 발견 사항 재현과 v1.6 대조 요약
type: ui-contract-verification
updated: 2026-11-11
---

# 계약 조사 발견 사항 재현과 v1.6 대조 요약

`ui-contract/`의 발견 사항 중 재현하지 않은 221건을 일곱 영역으로 나누어 v2(검증용 앱)와 공식 v1.16.0(JDK 8, H2)에 같은 요청과 같은 화면 동작을 보내 확인했다(2026-11-11). 영역별 결과 파일은 같은 폴더에 있다: [A 게시판 위키 검색 등](A-board-wiki-search-misc.md), [B 코드와 코드 주고받기](B-code-pullrequest.md), [C 이슈와 마일스톤](C-issue-milestone.md), [D 그룹과 사이트 관리](D-organization-site.md), [E 프로젝트](E-project.md), [F 공통 틀](F-shell.md), [G 사용자와 인증과 OAuth](G-user-oauth.md). 재현이 안 되면 데이터를 만들어서라도 했고, 재현하지 못한 것은 1건(`site-F15`)뿐이다. 각 발견 사항의 행에는 판정과 수정 상태가 `[검증: ...]`로 표시되어 있다.

## 판정별 개수 (221건)

| 판정 | 개수 | 뜻 |
|---|---|---|
| v1.6 결함 유지 | 139 | v1.6도 같다. 프로젝트 지침(v1.6을 그대로 따른다)에 따라 고치지 않았다 |
| 결함 아님 | 32 | 재현해 보니 의도된 동작이거나 발견 사항의 서술이 틀렸다 |
| v2 추가 기능의 결함 | 28 | v1.6에 없는 추가 개발 항목(2단계 인증, OAuth 서버, 위키 등)의 결함 |
| 문서 문제 | 11 | 위키 서술이 코드와 달랐다(코드가 맞다) |
| v2 신규 결함 | 10 | v1.6에는 없었고 v2 이식에서 새로 생겼다 |
| 재현 불가 | 1 | 데이터베이스 장애가 필요해 만들 수 없었다 |

## 이번에 고친 것 (31건, A-460)

audit-F4, board-F15, code-F5, code-F7, issue-F20, oauth-F1, oauth-F2, organization-F8, organization-F21, project-F3, project-F13, project-F16, pullrequest-F10, pullrequest-F13, pullrequest-F14, pullrequest-F15, search-F4, shell-F14, shell-F23, site-F3, site-F6, site-F8, site-F16, user-F7, usersec-F10, wiki-F1, wiki-F2, wiki-F3, wiki-F4, wiki-F5, wiki-F6

앞서 재현 단계에서 고친 것(A-457: select2 `val` 다섯 곳 등, A-458: 2단계 강제 화면, A-459: 서버 권한 검사 보강)은 이 표와 별도이다. 세 가지 모두 `next`와 `ui-improvements`에 같은 내용으로 들어 있다.

## 선생님 결정을 받아 처리한 것

- `shell-F14`: 어디서도 참조하지 않는 `template/yona.CodeTemplate.js`와 빈 `service/` 디렉터리를 지웠다(번들, 템플릿, 시험, 설정을 모두 검색해 확인). 이 때문에 빈 디렉터리를 훑던 JS 시험 세 개가 실패해 없는 디렉터리를 건너뛰도록 고쳤다.
- `shell-F23`: 사이트 관리자만 업로드 명의를 정한다. 그 밖에는 무시하고 본인 명의이며 로그인하지 않았으면 403이다.
- `code-F7`: Mercurial은 이름표(bookmark)를 브랜치로 보이는데 이름표가 없으면 목록이 비고 기본 브랜치도 없다고 보아 브랜치 화면이 404였다. 코드 화면은 이미 `default` 브랜치 끝을 쓰고 있었으므로(A-378) Git처럼 기본 위치를 `HEAD`라는 기본 브랜치로 보이게 했고, 파일 보기의 짧은 번호를 목록과 같은 12자로 맞췄다.
- `site-F6`: 업데이트 화면에 새 버전 알림의 상태와 "알림 켜기" 단추(`POST /sites/watchUpdate`)를 더했다.

## 고치지 않은 것

- **선택 대기 (v2 쪽 8건)**: `shell-F16`(컨텍스트 경로), `oauth-F3`, `oauth-F4`, `oauth-F5`, `oauth-F6`, `usersec-F12`, `usersec-F16`, `site-F19`. 권장안과 이유는 `decisions.md` A-461에 있다.
- **조치 없음**: `site-F17`(SAML은 이메일을 항상 확인됨으로 처리해 그 설정이 효과가 없다), `organization-F13`(익명을 로그인 화면으로 보내는 것을 시험이 의도로 고정하고 있다).
- **v1.6 결함 유지 139건**: 목록은 아래와 같고 영향이 큰 것부터 고치려면 선생님의 선택이 필요하다. `shell-F23`은 위에서 고쳤다.

### v1.6 결함 유지 목록

board-F4, board-F5, board-F6, board-F9, board-F10, board-F11, board-F12, board-F13, board-F14, board-F16, code-F2, code-F3, code-F4, code-F8, code-F9, code-F10, code-F11, code-F12, code-F14, code-F15, code-F17, issue-F2, issue-F3, issue-F7, issue-F8, issue-F9, issue-F11, issue-F12, issue-F13, issue-F15, issue-F16, issue-F18, issue-F19, issue-F21, issue-F22, issue-F23, issue-F24, issue-F25, migration-F1, migration-F2, migration-F3, migration-F4, migration-F5, migration-F6, migration-F8, milestone-F1, milestone-F3, milestone-F5, milestone-F6, milestone-F7, milestone-F8, milestone-F9, oauth-F7, organization-F2, organization-F3, organization-F4, organization-F5, organization-F6, organization-F7, organization-F9, organization-F10, organization-F12, organization-F16, organization-F17, organization-F18, organization-F19, project-F1, project-F6, project-F7, project-F8, project-F9, project-F10, project-F11, project-F12, project-F14, project-F17, project-F18, project-F19, project-F20, project-F21, project-F22, project-F23, project-F24, pullrequest-F5, pullrequest-F6, pullrequest-F9, pullrequest-F11, pullrequest-F16, pullrequest-F17, pullrequest-F18, search-F2, search-F3, search-F5, search-F6, search-F7, shell-F4, shell-F5, shell-F6, shell-F7, shell-F8, shell-F9, shell-F10, shell-F11, shell-F12, shell-F13, shell-F15, shell-F17, shell-F20, shell-F23, shell-F25, shell-F26, shell-F27, site-F4, site-F5, site-F7, site-F9, site-F10, site-F13, user-F1, user-F2, user-F3, user-F4, user-F6, user-F8, user-F9, user-F10, usersec-F2, usersec-F3, usersec-F4, usersec-F7, usersec-F8, usersec-F11, usersec-F13, usersec-F14, usersec-F15, welcome-F1, welcome-F2, welcome-F3, welcome-F4

### 결함 아님과 문서 문제

- 결함 아님: audit-F1, audit-F2, audit-F3, audit-F5, code-F13, issue-F17, milestone-F4, oauth-F8, oauth-F9, organization-F11, organization-F15, organization-F20, organization-F22, organization-F23, project-F5, pullrequest-F7, pullrequest-F8, pullrequest-F12, pullrequest-F19, pullrequest-F20, pullrequest-F21, shell-F18, shell-F22, shell-F24, site-F12, site-F14, site-F17, site-F18, site-F20, user-F11, wiki-F7, wiki-F8
- 문서 문제: code-F6, code-F16, issue-F10, migration-F7, organization-F14, organization-F24, project-F4, shell-F14, site-F1, site-F11, usersec-F9
