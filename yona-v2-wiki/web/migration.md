---
title: 이전(Migration) 화면
type: web
sources: [app/controllers/MigrationApp.java:50-450, app/views/migration/home.scala.html, app/views/migration/migrationPageLayout.scala.html, conf/routes]
updated: 2026-10-25
---

# 이전(Migration) 화면

코드: `web/migration/MigrationController`, `domain/migration/MigrationService`, `GithubTokenClient`. 템플릿은 `migration/home`. 화면은 AngularJS 앱(`yona.Migration.js`)이 그리고 GitHub에 쓰는 일도 브라우저가 맡는다. 서버는 화면, 토큰 교환, 옮길 데이터 JSON만 제공한다.

## 주소
| 주소 | 설명 |
|---|---|
| `GET /migration` | 설정 `yona.github.allow-migration`(기본 꺼짐)이 켜져야 열린다. 꺼져 있으면 403. `code`가 있으면 GitHub 토큰으로 바꿔 화면에 넘긴다 |
| `GET /migration/projects` | 출발 프로젝트 목록: 내가 `ORG_ADMIN`인 조직의 모든 프로젝트와 내가 구성원이면서 설정을 바꿀 수 있는 프로젝트, 소유자와 이름 순 |
| `GET /migration/:owner/projects/:name` | 요약(담당자, 구성원, 이슈, 게시글, 마일스톤 수) |
| `.../labels`, `.../issuelabel`, `.../milestones`, `.../issues`, `.../posts` | 라벨, 이슈와 라벨의 짝, 마일스톤, 이슈와 댓글, 게시글과 댓글 |

`issues`와 `posts`는 `withWikiCommit=true`이면 본문의 상대 링크와 첨부를 위키 커밋 경로로 바꾼다. 본문 앞에는 원 작성자 안내가 붙고 뒤에는 첨부 목록이 붙는다. 게시글도 GitHub API와 맞추려고 `issue` 키를 쓴다.

## 접근 (D-23)
v1.6은 로그인만 요구했다. v2는 프로젝트별 주소를 그 프로젝트의 매니저, 소속 조직의 관리자, 사이트 매니저에게만 연다(출발 프로젝트 목록과 같은 기준). 그 밖의 사용자는 403, 없는 프로젝트는 404이다.

## 관련
A-94(구현 판단), R-58(재현한 v1.6 결함).
