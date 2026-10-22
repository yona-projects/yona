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
| 2b-2 | 프로젝트 화면 기반: `projectLayout`, `projectMenu`, 프로젝트 헤더, 프로젝트용 오류 화면 | 다음 | |
| 2b-3 | 컨트롤러 검사 애너테이션: `@IsAllowed`, `@IsCreatable`, `@GuestProhibit`, `@IsOnlyGitAvailable`, 프로젝트 존재 검사, 이전 이름 리다이렉트 | 대기 | |
| 2b-4 | Git 저장소 서비스(JGit): 저장소 생성/삭제, 경로 설정 | 대기 | |
| 2b-5 | 프로젝트 목록(`/projects`), 생성(`/projectform`, `POST /projects`), 홈(`/:user/:project`), 설정, 멤버, 삭제 | 대기 | |
| 2c | 조직(Organization) 화면과 서비스 | 대기 | |
| 2d | 사용자 화면(가입, 프로필, 설정, 이메일, 비밀번호 재설정) | 대기 | |
| 3 | 이슈, 게시판, 마일스톤, 라벨, 첨부 | 대기 | |
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
| `ErrorViews`의 프로젝트/조직 변형 | 2b-2 |
| 소셜 로그인 버튼 영역, 사이트 업데이트 알림 | 7, 6 |
| 최초 기동 시 관리자 비밀번호 설정 화면(`welcome/secret`) | 6 |
| `AccessControl`의 엔티티별 `asResource()` | 각 엔티티의 화면 단계 |
| Play 언어 쿠키(`PLAY_LANG`) | 2d |

## 규칙 요약

- v1.6을 그대로 따른다. 결함도 재현하고 [refactoring-backlog.md](refactoring-backlog.md)에 올린다.
- 기술 스택은 [architecture/technology-decisions.md](architecture/technology-decisions.md)의 결정을 따르고, 새 결정이 필요하면 선생님께 먼저 묻는다.
- 검증은 H2, PostgreSQL, MariaDB 세 DB에서 모두 통과해야 한다. 화면 캡처는 Playwright로만 한다.
