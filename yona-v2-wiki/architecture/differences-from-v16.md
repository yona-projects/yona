---
title: v1.6과 달라지는 아키텍처 결정
type: architecture
sources: [build.sbt, conf/application.conf]
updated: 2026-10-25
---

# v1.6과 달라지는 아키텍처 결정

기능과 계약은 v1.6과 같게 유지하고, 구현 기반만 아래처럼 바꾼다.

| 항목 | v1.6 | v2 | 이유 |
|---|---|---|---|
| 언어 | Java, Scala 템플릿 | Kotlin (패키지 `dev.yona`) | 프로젝트 요구사항 |
| 프레임워크 | Play 2.3.10 | Spring Boot 4.1.1 (Spring Framework 7) | 프로젝트 요구사항 |
| ORM | Ebean | JPA(Hibernate) | Spring 표준 영속성 계층 |
| 빌드 | sbt | Gradle Kotlin DSL (JDK 21 toolchain) | 프로젝트 요구사항 |
| 템플릿 | `*.scala.html` | Thymeleaf | Scala 템플릿 엔진 대체 |
| DB | MariaDB | H2(기본), PostgreSQL(컨테이너), 이후 MariaDB 등 | 다중 백엔드 요구사항 |
| 개발 인프라 | 없음 | `docker-compose.yml` (PostgreSQL 16, mailpit) | 메일 확인과 DB 구성 |

테이블 구조는 v1.6의 `conf/evolutions/default/*.sql`과 같게 유지한다.

## 구현하면서 확정된 차이

위 표 이후 각 단계에서 구조가 달라진 부분이다. 동작과 요청, 응답 계약은 v1.6과 같고 결정의 자세한 이유는 [../decisions.md](../decisions.md)에 있다.

| 항목 | v1.6 | v2 | 이유 |
|---|---|---|---|
| 풀 리퀘스트 병합 작업 위치 | 받는 프로젝트마다 병합용 복제 저장소(`repo/git-merging/<소유자>/<이름>.git`)를 만들고 대체 객체 디렉터리로 연결한다 | 받는 프로젝트의 저장소 안에서 보내는 브랜치를 `refs/yobi/pull/<번호>/head`로 가져와 JGit RECURSIVE 전략으로 작업 트리 없이 병합하고 결과를 `…/merged`에 둔다([../subsystems/pull-request-merge.md](../subsystems/pull-request-merge.md)) | JGit이 작업 트리 없이 병합할 수 있어 복제 저장소와 그 정리 작업이 필요 없다. 결과 커밋과 상태 흐름은 같다 |
| 비동기 작업(Akka 액터) | 병합, 푸시 훅 후처리, 메일 수신 등을 액터로 처리 | `BackgroundJobRunner`와 Spring 이벤트(`yona.background-async`) | Play 전용 액터 시스템 대체 |
| 알림 발행 | 모델 저장 시점에 `NotificationEvent`를 만든다 | 활동을 Spring 이벤트로 발행하고 리스너가 알림 이벤트를 저장한다 | 도메인 계층이 알림 계층에 의존하지 않게 한다 |
| 라우팅 | `conf/routes` 한 파일과 맨 아래 포괄 라우트(끝 슬래시 제거) | 컨트롤러별 매핑과 `GlobalErrorHandler`의 처리기 없음 규칙(A-103). 대조는 `RouteParityTest`가 맡는다 | Spring MVC의 매핑 방식. 포괄 매핑은 정적 자원을 가린다 |
| Subversion HTTP(WebDAV) | `sonia.svnkit:svnkit-dav` 서블릿을 Play 컨트롤러에서 파이프로 호출 | Maven Central의 `svnkit-dav` war에서 클래스를 꺼내 쓰고 Jakarta, javax 서블릿 어댑터와 필터(`SvnDavFilter`)로 처리한다(D-26, A-98) | 같은 jar를 공개 저장소에서 받을 수 없고 Spring 디스패처가 WebDAV 메서드를 거부한다 |
| 마크다운 렌더링 | marked.js(서버에서 실행) | commonmark-java와 확장, 결과 정화(D-17) | JVM 안에서 같은 결과를 내는 구현으로 대체. 차이는 비교 테스트로 확인한다 |
| 소셜 로그인, LDAP, 메일 수신 | play-authenticate, JNDI, JavaMail | `RestClient` 직접 구현, JNDI, Jakarta Mail(D-24) | Play 플러그인 대체. 전환 후보는 [../refactoring-backlog.md](../refactoring-backlog.md) T-1~T-3 |
| 설정 | `application.conf`(Typesafe Config) | `yona.*` 속성(`YonaProperties`)과 `<데이터 디렉터리>/config/secret.properties`(A-93) | Spring Boot 설정 방식 |

## 기존 v1.6 설치 위에서 쓰기 (2026-10-25 실제 점검)
v1.6의 `conf/evolutions/default/1~32.sql`의 `Ups`를 새 MariaDB 10.11에 적용한 데이터베이스(61개 테이블)에 v2를 붙여 확인했다.

- 기동: 오류 없이 올라오고 초기 데이터(역할 7개, 관리자)는 v1.6 `initial-data.yml`과 같다. 스키마 변화는 [decisions.md](../decisions.md) A-104의 두 가지(중복 인덱스 2개, `webhook_type` 폭)뿐이다.
- 읽기: v1.6 형태로 넣은 사용자(v1.6 해시 비밀번호로 로그인, 잠긴 사용자 거부), 프로젝트(공개, 비공개), 이슈(상태 정수, 담당자는 `assignee` 테이블을 거친다), 댓글, 라벨, 마일스톤, 게시글, 추천, 이벤트, 첨부, 감시, 즐겨찾기가 화면에 맞게 나온다.
- 쓰기: v2가 쓴 이슈, 댓글, 마일스톤, 게시글이 v1.6과 같은 형태이다(번호가 이어지고 `last_issue_number`가 늘며 상태는 정수 1=열림, 2=닫힘, 마일스톤 마감은 `23:59:59`).
- 브랜치와 태그(2026-10-29, D-30): v1.6은 태그와 원격 참조를 "브랜치"로 함께 보여 주었으나 v2는 브랜치 목록(브랜치 화면, 풀 리퀘스트 출발·도착 목록)에 `refs/heads`만 둔다. 코드 화면과 커밋 이력의 선택 목록은 "브랜치", "태그" 구역으로 나누고 원격 참조는 뺀다. 코드, 커밋, 브랜치 화면의 탭에 "태그"를 더하고 전용 태그 화면과 삭제 경로를 추가했다([../web/tags.md](../web/tags.md)). v1.6 대비 의도된 차이이다.
- 디스크: `repo/git/<소유자>/<이름>.git`과 `uploads/<SHA-256>`을 그대로 쓴다. 저장소 HEAD가 가리키는 브랜치가 실제 브랜치와 같아야 코드 화면이 열린다(JGit이 만든 v1.6 저장소는 `master`, v2가 새로 만드는 저장소는 `main`: X-22). v1.6 저장소를 복제하고 푸시할 수 있다.
- 열거형: `issue.state`, `milestone.state`, `pull_request.state`, `webhook.webhook_type`은 선언 순서(서수)로 저장하므로 순서가 v1.6과 같아야 한다. `EnumOrdinalParityTest`가 지킨다.
- 이전(Migration) 화면: 내보내기 JSON이 위 데이터에서 v1.6 모양으로 나오고, 화면에서 원본과 대상을 골라 마일스톤, 이슈, 게시글을 옮기면 GitHub로 나가는 요청(마일스톤 생성, `import/issues`)이 서버 내용과 같다. 단 토큰 교환이 폼 형식만 해석하는 v1.6 결함(R-58 1) 때문에 GitHub가 JSON으로 답하면 토큰이 비어 인증이 반복된다.

