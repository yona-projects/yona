---
title: 이식 로드맵과 진행 상태
type: synthesis
sources: [../AGENTS.md, ../yona-v1.6-wiki/web/templates/*.md]
updated: 2026-10-22
---

# 이식 로드맵

선생님 지시(2026-10-22): **의존 방향 순으로 진행하고, 결정이 필요하면 먼저 묻는다.** 템플릿은 242개이며 하나씩 직접 이식한다. 단위마다 TDD, 위키, 커밋을 함께 한다.

## 진행 상태

| 단계 | 내용 | 상태 | 기록 |
|---|---|---|---|
| 0 | 골격, 스키마 61개 테이블 검증(H2, PostgreSQL, MariaDB), 초기 데이터 | 완료 | [domain/schema-mapping.md](domain/schema-mapping.md), [subsystems/initial-data.md](subsystems/initial-data.md) |
| 0 | 정적 자원, LESS 빌드, 다국어 메시지 | 완료 | [web/static-assets-and-i18n.md](web/static-assets-and-i18n.md) |
| 0 | 인증(해시, 로그인, 세션, remember-me, 토큰), `@AnonymousCheck` | 완료 | [subsystems/authentication.md](subsystems/authentication.md), [subsystems/access-control.md](subsystems/access-control.md) |
| 0 | 레이아웃, 로그인 화면, `/messages.js`, 오류 화면 | 완료 | [web/layout-and-login-page.md](web/layout-and-login-page.md), [web/error-pages.md](web/error-pages.md) |
| 1 | 홈 화면(소개, 환영 표, 기본 로그인 페이지) | 완료 | [web/home-page.md](web/home-page.md) |
| 2a | `AccessControl` 권한 규칙 | 완료 | [subsystems/access-control.md](subsystems/access-control.md) |
| 2b-1 | `ProjectService`(조회, 소속, 메뉴 개수) | 완료 | [domain/project-service.md](domain/project-service.md) |
| 2b-2 | 프로젝트 화면 기반: `projectLayout`, `projectMenu`, 프로젝트 헤더, 프로젝트용 오류 화면 | 완료 | [web/project-layout.md](web/project-layout.md) |
| 2b-3 | 컨트롤러 검사 애너테이션: `@IsAllowed`, `@IsCreatable`, `@GuestProhibit`, `@IsOnlyGitAvailable`, 프로젝트 존재 검사 | 완료 | [web/project-checks.md](web/project-checks.md) |
| 2b-4 | Git 저장소 서비스(JGit): 저장소 생성/삭제, 경로 설정 | 완료 | [subsystems/git-repository.md](subsystems/git-repository.md) |
| 2b-5a | 프로젝트 목록(`/projects`) | 완료 | [web/project-list.md](web/project-list.md) |
| 2b-5b | 프로젝트 생성(`/projectform`, `POST /projects`) | 완료 | [web/project-create.md](web/project-create.md) |
| 2b-5c-1 | 프로젝트 홈(README, 설명 수정, 구성원, 마일스톤 요약), 마크다운 미리보기 | 완료 | [web/project-home.md](web/project-home.md) |
| 2b-5c-2 | 마크다운 프로젝트 문맥 후처리(자동 링크, 이슈 링크, 캐시 동작) | 완료 | [web/markdown.md](web/markdown.md) |
| 2b-5c-3a | 첨부 파일 핵심(저장, 내려받기) | 완료 | [web/attachments.md](web/attachments.md) |
| 2b-5c-3b | 프로젝트 설정(로고 업로드, 이름 변경, 기본 브랜치) | 완료 | [web/project-setting.md](web/project-setting.md) |
| 2b-5c-3c | 구성원 관리 | 완료 | [web/project-members.md](web/project-members.md) |
| 2b-5c-3d | 프로젝트 삭제 | 완료 | [web/project-delete.md](web/project-delete.md) |
| 2b-5c-3e | 프로젝트 이전, 저장소 종류 변경 | 완료 | [web/project-transfer.md](web/project-transfer.md) |
| 2c | 조직(생성, 홈, 목록, 설정, 구성원, 삭제, 탈퇴)과 가입 신청(조직, 프로젝트) | 완료 | [web/organization.md](web/organization.md) |
| 2d-1 | 가입, 이메일 인증, 사용 여부 확인 | 완료 | [web/signup-and-password.md](web/signup-and-password.md) |
| 2d-2 | 비밀번호 재설정 | 완료 | [web/signup-and-password.md](web/signup-and-password.md) |
| 2d-3 | 계정 설정(프로필, 비밀번호, 이메일, 토큰), 아바타, 방문 목록 초기화, 첨부 올리기와 임시 첨부 정리 | 완료 | [web/account.md](web/account.md) |
| 2d-4a | 사용자 검색 API(자동 완성) | 완료 | [web/account.md](web/account.md) |
| 2d-4b | 사용자 페이지(`/:user`), 사용자 메뉴 탭, 내 파일 목록, 프로젝트 탈퇴(`/info/leave`) | 이슈(3단계)와 풀 리퀘스트(5단계) 뒤 | 이슈, 풀 리퀘스트 목록에 의존 |
| 3a | 이슈 라벨과 분류 | 완료 | [web/issue-labels.md](web/issue-labels.md) |
| 3b-0 | 공용 편집기 부품(편집기, 업로더, 마크다운 도움말), 멘션 후보, 첨부 목록, 폼 본문 크기 한도 | 완료 | [web/editor-components.md](web/editor-components.md) |
| 3b | 마일스톤 | 완료 | [web/milestones.md](web/milestones.md) |
| 3c-1 | 이슈 목록, 검색, 엑셀, 일괄 변경 막대(화면), 마일스톤 보기의 이슈 영역 | 완료 | [web/issue-list.md](web/issue-list.md) |
| 3c-2a | 이슈 등록 폼과 등록, 제목 머리말, 멘션, 타임라인 이벤트 기록, 폼 자동 완성 API | 완료 | [web/issue-create.md](web/issue-create.md) |
| 3c-2b | 이슈 보기(읽기), 타임라인, 다음 상태, 삭제 | 완료 | [web/issue-view.md](web/issue-view.md) |
| 3c-2c | 이슈 수정(프로젝트 이동, 초안 게시, 변경 이력) | 완료 | [web/issue-edit.md](web/issue-edit.md) |
| 3c-3 | 이슈 댓글, 타임라인(이벤트), 추천, 공유자, 하위 이슈, 일괄 변경 처리 | 대기 | |
| 3d | 게시판 | 대기 | |
| 4 | 알림 서브시스템(이벤트 생성, 메시지, 링크, 메일)과 로그인 사용자용 홈 알림 목록 | 대기 | |
| 5 | 코드 저장소 화면(Git), 포크, 풀 리퀘스트, 코드 리뷰, 웹훅 | 대기 | |
| 6 | 검색, 통계, 도움말, 사이트 관리, 데이터 내보내기/가져오기 | 대기 | |
| 7 | 소셜 로그인, LDAP, 이메일 수신 | 대기 | |
| 8 | Subversion(SVNKit) | 마지막 | |

## 단계별로 남겨 둔 연결 고리

앞 단계에서 일부러 비워 둔 곳이다. 해당 단계에서 채운다.

| 비워 둔 곳 | 채울 단계 |
|---|---|
| 홈의 알림 항목 표시와 More 버튼, `GET /notification` | 4 |
| `GET /user/sidebar`와 사이드바 목록, `GET /user/usermenuTabContentList` | 2b-5 이후 (프로젝트 목록 필요) |
| `ErrorViews`의 조직 변형 | 소셜 로그인 버튼 영역, 사이트 업데이트 알림 | 7, 6 |
| 최초 기동 시 관리자 비밀번호 설정 화면(`welcome/secret`) | 6 |
| `AccessControl`의 엔티티별 `asResource()` | 각 엔티티의 화면 단계 |
| Play 언어 쿠키(`PLAY_LANG`) | 2d |

## 규칙 요약

- v1.6을 그대로 따른다. 결함도 재현하고 [refactoring-backlog.md](refactoring-backlog.md)에 올린다.
- 기술 스택은 [architecture/technology-decisions.md](architecture/technology-decisions.md)의 결정을 따르고, 새 결정이 필요하면 선생님께 먼저 묻는다.
- 검증은 H2, PostgreSQL, MariaDB 세 DB에서 모두 통과해야 한다. 화면 캡처는 Playwright로만 하며 v1.6 화면은 캡처할 수 없으므로 새 화면만 캡처한다. v1.6과의 일치는 템플릿 소스 대조와 테스트로 확인한다.
