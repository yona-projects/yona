---
title: 프로젝트 홈 (GET /:user/:project, PUT /:user/:project)
type: web
sources: [app/controllers/ProjectApp.java:85-150, app/views/project/home.scala.html, app/views/project/partial_readme.scala.html, app/views/milestone/partial_status.scala.html, app/views/common/markdown.scala.html, app/controllers/MarkdownApp.java, app/models/Milestone.java:92-279, app/models/Project.java:330-383, app/models/User.java:856-862, app/utils/Markdown.java:352-376]
updated: 2026-10-22
---

# 프로젝트 홈

## 접근

- `GET /:user/:project`: 읽기 권한(`@IsAllowed(READ)`)이 필요하다. 익명도 공개 프로젝트는 열 수 있다.
- 소유자와 이름은 대소문자를 구별하지 않고 찾는다.
- `tabId` 쿼리로 탭을 고른다(`readme`(기본), `history`, `dashboard`). 값이 비어 있으면 `readme`이다.
- 로그인한 사용자가 열면 최근 방문 프로젝트에 기록한다([../domain/recent-visits.md](../domain/recent-visits.md)).

## 문서 제목

모델의 `title`은 `이름 - Home |:| 설명`이다. v1.6 레이아웃이 `" |:| "`로 쪼개 **첫 조각만 `<title>`에** 쓰므로 문서 제목은 `이름 - Home`이고, 마지막 조각 `설명 - 소유자/이름`이 `og:description`, `twitter:description`이 된다.

## 화면 구성

| 영역 | 내용 |
|---|---|
| 설명 | 설명이 있으면 마크다운으로 렌더링(프로젝트 문맥 없음), 비어 있으면 안내 문구(`project.description.placeholder`). 수정 권한자에게만 수정 버튼이 있고 입력란에 현재 설명이 들어간다 |
| 복제 주소 | 코드 메뉴가 켜져 있을 때만. 로그인했으면 `사용자@`가 들어간 주소 |
| 탭 | README / 이력 / 대시보드 링크. 활성 탭에 `active` |
| README | 저장소 `HEAD`의 `README.md`, 없으면 `readme.md`. 없으면 안내 문구와, 커밋을 만들 수 있는 사용자에게 `/:user/:project/postform?readme=true` 링크. 있으면 파일 이름 헤더, 렌더링한 본문, 커밋을 만들 수 있는 사용자에게 편집 링크 |
| 새 이슈, 포크 | 이슈 메뉴가 켜져 있으면 새 이슈, 코드 메뉴가 켜져 있고 Git이면 포크 |
| 마일스톤 요약 | 마일스톤 메뉴가 켜져 있고 열린 마일스톤이 있으면 마감일이 가장 이른 것 하나 |
| 구성원 | 사이트 관리자 역할을 뺀 구성원, 이름 오름차순. 보는 사람의 언어가 영어이면 영문 이름 |
| 구성원 추가 링크 | 프로젝트 라벨 `UPDATE` 권한자(구성원) |
| 탈퇴 | `LEAVE` 권한자(소유자가 아닌 구성원)에게 버튼과 확인 대화상자. 호출 주소는 `/:user/:project/member/:id/delete` |

### README 선택 규칙

1. 저장소에서 README 파일을 읽을 수 없으면 README는 "없음"이다.
2. 있으면, 코드 메뉴가 꺼져 있고 README 게시글(`posting.readme = true`)이 있으면 **게시글 본문**을, 아니면 **파일 내용**을 보여 준다. (README 파일이 없으면 게시글이 있어도 안내 문구가 나온다. v1.6과 같다.)
3. 파일 내용은 상대 경로 이미지와 링크를 저장소 주소로 바꾼 뒤 프로젝트 문맥 파이프라인(이슈 링크, 자동 링크 포함)으로 렌더링한다([markdown.md](markdown.md), 결함 R-14, R-15, R-16).

### 마일스톤 요약 ([../domain/milestone-status.md](../domain/milestone-status.md))

제목 링크 `/:user/:project/milestone/:id`, 마감일이 있으면 `Due Date <날짜> (<남은 기간>)`이고 지연이면 `over` 클래스, 진행 막대 `width: <완료율>%`, `<닫힌 수> / <열린 수 + 닫힌 수>`.

## 설명 수정 (`PUT /:user/:project`)

로그인해야 하고(안내 flash) 수정 권한이 있어야 한다. 요청 본문은 JSON `{"overview": "..."}`이고 응답은 `{"overview": "저장한 값"}`이다. 문자열이 아닌 값이면 `null`을 저장한다(v1.6 `textValue()`와 같다).

## 마크다운 미리보기 (`POST /markdown/:user/:project`)

본문 JSON `{"body": "...", "breaks": true}`를 받아 렌더링한 HTML 조각을 `text/html;charset=UTF-8`로 돌려준다. `breaks` 값은 v1.6에서도 쓰이지 않는다. 프로젝트가 없으면 프로젝트 문맥 없이 렌더링한다(v1.6은 이 경우의 동작이 정의되어 있지 않다, A-19).

## 아직 채우지 않은 부분

- **이력 탭, 대시보드 탭**: 링크와 활성 표시만 있고 본문은 비어 있다. 이력은 커밋, 이슈, 게시글, 풀 리퀘스트가, 대시보드는 이슈 집계와 풀 리퀘스트 집계가 필요하므로 각 서브시스템을 옮길 때 채운다(로드맵 3, 5단계).
