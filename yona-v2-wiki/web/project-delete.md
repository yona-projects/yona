---
title: 프로젝트 삭제
type: web
sources: [app/controllers/ProjectApp.java:331-356, app/views/project/delete.scala.html, app/models/Project.java:695-790, app/models/PullRequest.java:831-849, app/models/CommentThread.java:236-240]
updated: 2026-10-22
---

# 프로젝트 삭제

## 화면 (`GET /:user/:project/deleteform`)

프로젝트를 지울 수 있는 사용자(`DELETE` 권한, 관리자와 조직 관리자)만 본다. 경고 문구, "삭제에 동의합니다" 체크박스, 삭제 버튼(확인 대화상자를 연다)이 있고 스크립트 `project.Delete`가 삭제 주소(`/:user/:project/delete`)를 받는다. 설정 하위 메뉴에서 삭제가 활성이다.

## 삭제 (`DELETE /:user/:project/delete`)

1. 지우는 사용자의 이 프로젝트 즐겨찾기를 지운다.
2. `ProjectDeletionService`가 프로젝트와 딸린 데이터를 한 트랜잭션에서 지운다.
3. 저장소 디렉터리를 지운다(DB 트랜잭션 밖이므로 이후 단계가 실패해도 되돌릴 수 없다).
4. `X-Requested-With: XMLHttpRequest`이면 `204`와 `Location: /`, 아니면 `303` `/`.

프로젝트가 이미 없으면 `303` `/`.

### 삭제 범위 (`ProjectDeletionService`)

Ebean 연쇄 삭제 대신 외래 키 순서대로 일괄 삭제문을 실행한다.

1. 이 프로젝트와 **포크**(원본 연결이 이 프로젝트인 프로젝트)가 보내거나 받은 풀 리퀘스트: 그 풀 리퀘스트를 가리키는 이슈 이벤트(`ISSUE_REFERRED_FROM_PULL_REQUEST`, 보낸 사람이 기여자), 코멘트 스레드와 리뷰 코멘트, 커밋, 이벤트, 리뷰어. 포크는 지우지 않고 원본 연결만 끊는다.
2. 프로젝트의 코멘트 스레드(리뷰 코멘트, 코드 작성자 연결 포함)
3. 이슈와 코멘트(투표 포함), 이슈 이벤트, 라벨 연결, 투표, 공유, 즐겨찾기, 최근 본 기록. 다른 프로젝트 이슈의 부모 연결은 외래 키 규칙으로 끊긴다.
4. 게시글과 코멘트, 라벨 연결
5. 웹훅과 웹훅 스레드, 담당자, 이슈 라벨과 분류, 마일스톤
6. 프로젝트 라벨 연결, 구성원, 메뉴 설정, 푸시된 브랜치, 이전 요청, 알림 설정, 가입 신청, 즐겨찾기, 최근 방문, 방문 기록, 제목 머리말, 코드 코멘트
7. 프로젝트

DB가 자동으로 지워 주는(`CASCADE`) 테이블도 명시적으로 지워 DB 종류와 무관하게 같은 결과를 낸다. `ProjectDeletionService.DELETED_TABLES`는 v1.6 외래 키 그래프에서 프로젝트로부터 닿는 모든 테이블을 담아야 하며 테스트가 이를 구조적으로 확인한다.

### v1.6과 같은 동작(결함 포함)

프로젝트 로고 같은 프로젝트 자체의 첨부, 구독/구독 해제 기록, 멘션은 지우지 않는다. 이슈, 게시글, 댓글의 첨부와 알림 이벤트는 v1.6 `issue.delete()`, `posting.delete()`가 지우므로 같이 지운다(2026-10-26 정정, `ProjectDeletionServiceTest`). 지워진 프로젝트를 가리키는 첨부는 이후 접근하면 "프로젝트를 잃었다" 오류가 된다(R-18).

## 검증

`ProjectDeletionServiceTest` 5건(전체 삭제, 다른 프로젝트 보존, 포크, 부모 이슈, 테이블 목록 구조 확인), `ProjectDeleteTest` 8건.
