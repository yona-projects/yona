---
title: 데이터 내보내기와 가져오기
type: subsystem
sources: [app/data/DataService.java, app/data/DefaultExchanger.java, app/data/exchangers 아래 47개 클래스, app/controllers/SiteApp.java:352-382, docs/ko/spec/export-and-import.md]
updated: 2026-10-25
---

# 데이터 내보내기와 가져오기

코드: `domain/site/DataTables`(47개 테이블 명세), `DataService`, `web/site/SiteDataController`.

## 파일 모양

최상위 JSON 객체에 테이블 이름(대문자, 사용자 테이블은 `N4USER`)마다 행의 배열이 있고, 시퀀스가 있는 테이블은 그 뒤에 `<이름>_SEQ`(다음 번호)가 이어진다. 필드는 테이블 이름 순서(문자열 비교)이다. 조인 테이블 9개(`COMMENT_THREAD_N4USER`, `ISSUE_COMMENT_VOTER`, `ISSUE_ISSUE_LABEL`, `ISSUE_VOTER`, `NOTIFICATION_EVENT_N4USER`, `PROJECT_LABEL`, `PULL_REQUEST_REVIEWERS`, `USER_ENROLLED_ORGANIZATION`, `USER_ENROLLED_PROJECT`)에는 시퀀스가 없다.

- 행의 키는 소문자 컬럼 이름이고 컬럼 순서는 `DataTables`의 목록과 같다. `ATTACHMENT.container_type`만 키가 `content_type`이다.
- 값: 정수와 `int`는 숫자, 문자열과 긴 문자열은 문자열, 시각은 에폭 밀리초(`ATTACHMENT.created_date`는 날짜의 밀리초), 불린은 `true/false`(DB NULL은 `false`). DB NULL은 JSON `null`이다. `ATTACHMENT.container_id`와 `PROJECT_TRANSFER.sender_id`는 DB는 정수이지만 JSON은 문자열이다.
- 엔티티에 있어도 v1.6 Exchanger가 다루지 않는 컬럼과 14개 테이블(즐겨찾기, 웹훅, 소셜 로그인 정보, 사용자 설정, 인증 요청 등)은 파일에 없다(R-56).

## 내보내기

한 연결로 테이블마다 `select`해 행을 바로 쓴다(`order by` 없음, 트랜잭션 격리 없음). 시퀀스 값은 MariaDB는 `AUTO_INCREMENT`, H2와 PostgreSQL은 가장 큰 번호에 1을 더한 값이다(A-92).

## 가져오기

1. 첫 토큰이 객체가 아니면 아무것도 하지 않는다.
2. 외래 키 검사를 끈다(H2 `SET REFERENTIAL_INTEGRITY FALSE`, PostgreSQL `session_replication_role = replica`(슈퍼유저 권한), MariaDB `FOREIGN_KEY_CHECKS = 0`와 `utf8mb4`). 끝나면 되돌린다.
3. 파일의 필드를 차례로 읽는다. 테이블 이름(대소문자 무시)이면 그 테이블을 `delete from`으로 비우고 행을 100개씩 배치로 넣는다. 테이블 단위 트랜잭션이며, 오류가 나면 그 테이블만 되돌리고 로그를 남기고 다음 테이블을 계속한다. 파일에 없는 테이블은 그대로이다. 모르는 필드는 건너뛴다.
4. `<이름>_SEQ`이면 다음 번호를 복원한다(H2 `alter column restart with`, PostgreSQL `setval`, MariaDB `auto_increment`).

값 바인딩(JSON `null`일 때): 정수는 `0`(널을 허용하는 28개 컬럼은 SQL NULL), 문자열과 긴 문자열은 NULL(문자열이 아닌 값도 NULL), 불린은 `false`, 시각은 `0`이거나 `null`이면 NULL. 행에 키가 없으면 그 테이블이 실패한다. 결과(`DataImportResult`)에 성공과 실패한 테이블 이름이 담기지만 화면은 `/`로 이동할 뿐이다(R-56).

기존 데이터를 교체하므로 가져오기 전에 반드시 내보내기로 백업한다. Git 저장소와 첨부 파일 본문은 다루지 않는다.
