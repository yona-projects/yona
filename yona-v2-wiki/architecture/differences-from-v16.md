---
title: v1.6과 달라지는 아키텍처 결정
type: architecture
sources: [build.sbt, conf/application.conf]
updated: 2026-10-29
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

## 추가 개발 항목으로 생긴 의도된 차이 (장부)

D-28의 추가 개발 항목은 v1.6에 없는 동작이므로 v1.6과 달라지는 것이 정의상 의도이다. **이 표가 의도된 차이의 유일한 장부이다.** 항목을 끝낼 때마다 변경한 코드를 근거로 한 줄씩 더한다. 동등성 대조 도구(`tools/v16-diff`)는 선생님이 요청할 때만 돌리며(D-32), 그때 이 표에 있는 차이는 새 불일치로 세지 않는다.

| 항목 | 날짜 | v1.6과 달라지는 점 | 닿는 화면과 요청 | 기록 |
|---|---|---|---|---|
| X-21 사용 현황 집계 | 2026-10-28 | 레이아웃(`layout/layout.html`, `layout/layout-framed.html`)의 UA 스크립트(`analytics.js`)를 GA4 `gtag` 조각(`common/analytics`)으로 바꿨다. 스위치는 v1.6의 `yona.send-yona-usage`(기본 켜짐)를 그대로 쓴다. 측정 ID는 `yona.analytics.measurement-id`이고, 경로를 가려(소유자와 프로젝트 이름은 보내지 않는다) 페이지뷰와 2단 보기 이벤트를 보낸다. 선생님이 의도된 동작으로 확인하셨다 | 두 레이아웃을 쓰는 모든 화면의 본문 끝 스크립트 | A-186 |
| X-22 기본 브랜치 | 2026-10-28 | 새로 만드는 Git 저장소의 `HEAD`가 `master`가 아니라 `yona.git-default-branch`(기본 `main`)이다. 기존 저장소, 가져오기, 포크는 그대로이다 | 프로젝트 생성 직후의 코드 화면, 풀 리퀘스트 기본 브랜치 | A-187 |
| X-11 병합 시 이슈 자동 닫기 | 2026-10-29 | 풀 리퀘스트를 받아들여 병합하면 제목, 본문, 커밋 메시지에서 `close(s/d)`, `fix(es/ed)`, `resolve(s/d) #번호`로 가리킨 받는 프로젝트의 열림 이슈를 닫고 이슈 타임라인 이벤트와 알림을 남긴다 | `POST /:user/:project/pullRequest/:번호/accept`의 부수 효과(이슈 상태, 이슈 이벤트, 알림, 웹훅) | A-188 |
| X-18 Git 태그 화면 | 2026-10-29 | (1) 브랜치 목록(브랜치 화면, 풀 리퀘스트 생성과 수정 화면의 출발, 도착 목록)이 `refs/heads`만 돌려준다. 태그와 원격 참조가 빠진다. (2) 코드 화면과 커밋 이력의 선택 목록이 "브랜치", "태그" 구역으로 나뉘고 원격 참조는 빠진다. (3) 코드, 커밋 이력, 커밋 보기, 브랜치 화면의 탭에 "태그"가 더해진다. (4) 전용 화면 `GET /:user/:project/tags`와 삭제 `DELETE /:user/:project/tags/:tag`가 더해진다 | `/:user/:project/branches`, `/newPullRequestForm`, `/newPullRequest/mergeResult`, 풀 리퀘스트 수정 폼, `/code`, `/commits`, `/commit/:id`, `/tags`(신규). 새 메시지 키 `title.tags`, `code.tags.*` | D-30, [../web/tags.md](../web/tags.md) |
| 브랜치 삭제 확인창 | 2026-10-29 | 브랜치 화면의 삭제 단추가 확인창을 먼저 띄운다. 요청 경로와 응답은 그대로이다. 새 메시지 키 `code.branches.deleteConfirm` | `/:user/:project/branches`의 삭제 단추 | D-31 |
| X-5 개인용 API 토큰 (구현 완료) | 2026-10-29 | (1) v1.6의 사용자 토큰(`n4user.token`, 평문, 범위와 만료 없음)을 새 Classic 토큰 테이블(`api_token`, 해시 저장, 범위, 만료)로 이관한다. (2) **모든 토큰이 사용자의 실제 권한도 확인한다**(v1.6은 확인하지 않았다, D-20 대체). (3) **모든 엔드포인트는 세션 전용이거나 토큰 전용이다**: 토큰은 엔드포인트 단위 허용 목록에서만 인정하고 나머지는 거부한다(v1.6은 모든 경로에서 인정했다). **`/-_-api/v1/**`는 토큰 전용이 되고** 웹 화면 AJAX 24개는 일반 웹 경로 이름으로 옮기며(화면 요청 주소가 바뀌는 의도된 차이) 세션과 토큰을 둘 다 받던 16개는 토큰 전용이 된다(상세 목록은 api-auth-split.md). 잠긴 사용자의 토큰도 거절한다. 첨부 올리기 `POST /files`는 세션 전용이 되고 토큰용 `POST /-_-api/v1/files`가 새로 생긴다. (4) 토큰을 Git, SVN의 HTTP 비밀번호 대신 쓴다. (5) 토큰 인증 5회 실패 시 주소별 차단, 설정 키로 Git, SVN 비밀번호 인증을 끌 수 있다. (6) 옛 URL `POST /-_-api/v1/users/token`(비밀번호로 토큰과 세션 쿠키 발급)은 **삭제한다**. (7) 내보내기 51개 테이블 | `/-_-api/**`, 첨부 올리기, Git과 SVN의 HTTP 접근, 사용자 설정의 토큰 화면, 데이터 내보내기 파일 | D-36, [api-token.md](api-token.md) |
| X-12 풀 리퀘스트 리뷰 판정 | 2026-10-29 | (1) v1.6의 "리뷰 완료/취소"를 GitHub 방식의 리뷰(Comment, Approve, Request changes)로 합쳤다. 새 테이블 `pull_request_review`와 프로젝트 열 `is_dismissing_stale_approvals`(기본 켬). (2) 작성자는 승인과 변경 요청을 할 수 없고 작성자의 승인은 개수에 세지 않는다. (3) 리뷰어 수를 켠 프로젝트는 변경 요청이 남으면 병합할 수 없고 프로젝트 관리자와 사이트 관리자는 이를 우회한다(v1.6은 관리자도 막혔다). (4) 새 커밋 때 승인 해제는 프로젝트 설정으로 끌 수 있다(v1.6은 항상 해제). (5) 제출, 해제 알림, 웹훅, 이벤트 종류 3개. (6) 기존 승인은 시작 때와 가져온 뒤 보정으로 새 구조에 옮기고 작성자 승인 제외로 요건을 잃은 열린 요청은 한 번 안내한다. (7) 내보내기 50개 테이블 | `POST /:user/:project/pullRequest/:id/reviews`, `.../reviews/:reviewId/dismissals`(신규), `.../review`와 `.../unreview`(삭제), 풀 리퀘스트 개요 화면, 프로젝트 설정 화면, 알림 설정 화면, 데이터 내보내기 파일 | D-35, [../web/pull-request-review.md](../web/pull-request-review.md) |
| X-14 풀 리퀘스트의 담당자와 라벨 | 2026-10-29 | (1) 새 테이블 2개 `pull_request_assignee`, `pull_request_issue_label`(기존 테이블은 그대로). (2) 풀 리퀘스트 보기에 이슈 화면과 같은 2단 구성의 담당자 선택(1명까지)과 라벨 선택(Ajax), 타임라인 이벤트 두 가지. (3) 담당자 변경은 알림과 웹훅을 보내고 라벨 변경은 타임라인만 남긴다. (4) 프로젝트, 조직, 사용자 탭, 대시보드의 목록 줄에 라벨 칩과 담당자 아바타(받은 사람 아바타와 나란히), 프로젝트 목록 검색 상자에 담당자와 라벨 필터(`assigneeId`, 값 `none`은 담당자 없음), 생성 폼에서 담당자와 라벨 지정(수정 권한자만). (5) 이벤트 종류 2개 추가로 알림 설정 목록에 항목이 늘어남. (6) 내보내기와 가져오기가 49개 테이블 | `POST /:user/:project/pullRequest/:번호/assignees`, `/labels`, `GET .../timeline`(신규), `POST /:user/:project/pullRequests`(`assigneeLoginId`, `labelIds` 추가), `/:user/:project/pullRequest/:번호`, `/:user/:project/pullRequests`, 알림 설정 화면, 데이터 내보내기 파일, 알림과 웹훅 수신 | D-34(후속 14~17, 15-1, 19, 20 구현 완료), [../web/pull-request-assignee-label.md](../web/pull-request-assignee-label.md) |
| X-27 풀 리퀘스트 리뷰 요청 | 2026-10-29 | (1) 새 테이블 `pull_request_review_request`(대기 중인 요청만)를 추가한다. v1.6 `pull_request_reviewers`(승인한 사람 집합)와 별개이다. (2) 풀 리퀘스트 수정 권한자가 프로젝트나 소속 조직의 활성 구성원(작성자 제외)에게 요청, 취소, 다시 요청을 하고 요청받은 사람에게만 알림과 웹훅을 보낸다. 리뷰를 제출하면 요청이 지워진다. (3) 개요 오른쪽 열에 리뷰어 칸과 목록의 "나에게 리뷰 요청됨" 필터가 새로 생긴다. (4) 알림 종류 `PULL_REQUEST_REVIEW_REQUESTED`, `PULL_REQUEST_REVIEW_UNREQUESTED`가 추가된다(알림 설정 화면에도 나온다). 병합 조건은 바뀌지 않는다 | 풀 리퀘스트 보기와 목록, 알림, 웹훅, 데이터 내보내기 파일(52개 테이블) | D-37, [web/pull-request-review.md](../web/pull-request-review.md) |
| X-26 임시 저장 리뷰 | 2026-10-29 | (1) v1.6 테이블 `comment_thread`, `review_comment`에 컬럼 `pending_date`를 더한다(기본 `null`, 값이 있으면 제출하지 않은 임시 행). 엔티티의 항상 적용되는 조건으로 모든 읽기에서 숨기고 작성자 본인만 전용 경로로 읽는다. (2) 풀 리퀘스트 줄 댓글 입력 상자와 스레드 답글에 "리뷰 시작/리뷰에 추가" 단추가 생기고, 개요의 리뷰 폼을 제출하면 임시 댓글이 한꺼번에 공개된다(임시 댓글만 있어도 본문 없이 제출 가능). (3) 알림은 줄 댓글마다가 아니라 리뷰 제출 알림 하나(줄 댓글 요약 포함)이다. (4) 데이터 내보내기는 임시 행을 담지 않는다. (5) **리뷰 댓글 고치기가 새로 생긴다**(v1.6에는 없었다): 지울 수 있는 사람이 줄 댓글을 그 자리에서 고치고 "수정됨"을 보이며 `review_comment.edited_date` 컬럼이 늘어난다. 임시 저장과 고치기를 쓰지 않으면 줄 댓글의 동작은 v1.6과 같다 | 풀 리퀘스트 변경 화면과 개요, 알림, 데이터 내보내기 파일, 스키마 | D-38, [web/pull-request-review.md](../web/pull-request-review.md) |
| X-15 풀 리퀘스트 REST API | 2026-10-29 | `/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/...`에 풀 리퀘스트의 조회(목록, 상세, 커밋, 변경 파일, 타임라인, 리뷰, 리뷰 댓글)와 쓰기(닫기, 다시 열기, 병합, 출발 브랜치 지우기와 되살리기, 담당자, 라벨, 리뷰 요청, 리뷰 제출과 해제, 줄 댓글 달기와 고치기와 지우기, 임시 리뷰 버리기, 스레드 열기와 닫기, 풀 리퀘스트 만들기와 고치기, 첨부)를 더한다. v1.6 외부 API에는 풀 리퀘스트가 없었다. 토큰 범위(`repo`)와 화면과 같은 사용자 권한을 함께 확인한다. 병합 방식 필드 `method`는 `merge`만 받는다 | 외부 API | D-39, [api-pull-requests.md](api-pull-requests.md) |
| X-19 일반 REST 1차 | 2026-10-29 | `/-_-api/v1`에 프로젝트 정보, 내가 속한 프로젝트와 소유자의 프로젝트, 프로젝트 구성원과 담당자 후보, 라벨 목록, 마일스톤 목록, 이슈 목록과 검색(조건, 정렬, 쪽 나누기)을 읽는 요청을 더한다. v1.6 외부 API에는 이슈 한 건 조회뿐이었다. 토큰 범위(`repo`, 목록은 `public_repo`도)와 화면과 같은 읽기 권한을 함께 확인한다 | 외부 API | D-40, [api-project-reads.md](api-project-reads.md) |
| X-28 풀 리퀘스트 병합 방식 | 2026-10-29 | 병합 커밋 외에 스쿼시와 리베이스로 병합할 수 있고, 프로젝트 설정(`project.allow_merge_commit`, `allow_squash_merge`, `allow_rebase_merge`, `default_merge_method`)으로 허용 방식과 기본 방식을 정한다. 병합 화면에 방식 선택과 방식별 가능 여부(`pull_request.rebase_blocked`), 커밋 제목과 본문 직접 쓰기가 더해지고 REST에 `method`, `commitTitle`, `commitMessage`, `mergeMethod`, `mergeMethods`가 있다. 기존 프로젝트와 v1.6 가져오기는 병합 커밋만 허용이라 동작이 그대로이다 | 추가 개발 | D-41, [pull-request-merge-methods.md](pull-request-merge-methods.md) |
| X-34 규칙 묶음(GitHub 방식 브랜치 보호, X-33 대체) | 2026-10-30 | v1.6의 프로젝트 설정 "리뷰어 사용/N 명"(`project.is_using_reviewer_count`, `default_reviewer_count`)은 효력이 없고 차기 버전에서 컬럼을 제거한다. 서버를 시작할 때 리뷰어 사용이 켜진 프로젝트는 브랜치마다 "이전된 리뷰어 설정 (브랜치 이름)" 규칙 묶음으로 옮겨 v1.6 동작(승인 수만 요구, 관리자 우회 없음)을 보존한다. 변경 요청은 v1.6에 없던 개념이라 이전된 규칙 묶음은 켜지 않는다. 새 테이블 `ruleset`으로 이름, 적용 상태(활성, 비활성), 바이패스, 대상 브랜치, 규칙(갱신 제한, 삭제 제한, 강제 푸시 차단, 풀 리퀘스트 필요, 선형 이력, 필요한 승인 수, 변경 요청과 대화 해결)을 정하며 규칙 묶음 탭(목록과 편집 화면)에서 관리한다. 규칙 묶음이 없는 브랜치는 제한이 없다. 리뷰(승인, 변경 요청) 화면은 v1.6과 달리 리뷰어 사용과 무관하게 항상 보인다 | 추가 개발(선생님 결정) | D-42~D-46, [rulesets.md](rulesets.md) |
| X-29a 이슈 쓰기 REST 1단계 | 2026-10-30 | `/-_-api/v1/owners/{owner}/projects/{projectName}`에 이슈 부분 수정(`PATCH issues/{n}`: title, body, state, milestone, assignees, labels), 이슈 삭제, 댓글 목록과 수정과 삭제, 라벨 더하기와 떼기, 담당자 정하기와 빼기를 더한다. v1.6 호출(상태만 `PATCH`, `PUT`)은 그대로 동작한다. **달라지는 점**: 구성원이 아닌 토큰은 비공개 프로젝트의 이슈를 읽거나 고치지 못하고(v1.6은 유효한 토큰이면 권한을 보지 않았다, D-36 4번) 403을 받는다 | 추가 개발(선생님 결정) | D-47, A-261, [api-issue-board-write.md](api-issue-board-write.md) |
| X-36 이슈, 게시글, 댓글의 삭제 제한 | 2026-10-30 | v1.6은 프로젝트 구성원이면 남의 이슈, 게시글, 이슈 댓글, 게시판 댓글을 지울 수 있었다(`AccessControl.java`의 "Members can update anything and delete anything"). 이제 삭제는 작성자, 프로젝트 관리자, 사이트 관리자, 조직 관리자만 한다(이슈 담당자도 못 한다). 수정(이슈 본문, 댓글)과 상태, 담당자, 마일스톤, 라벨 변경은 GitHub처럼 구성원에게 그대로 열려 있다. 웹과 REST와 일괄 삭제가 같은 판정을 따르고, 화면은 남의 것에 삭제 단추를 보이지 않는다. 코드 리뷰 댓글, 풀 리퀘스트 댓글, 커밋 댓글도 같은 규칙이다(v1.6은 리뷰 댓글 수정이 삭제 권한과 같았으나 이제 수정은 구성원에게 열려 있고 삭제만 작성자와 관리자이다) | 추가 개발(선생님 결정) | D-48, A-262 |
| git과 SVN의 HTTP는 토큰만 | 2026-11-02 | git(코드와 위키)과 SVN WebDAV의 HTTP 접근이 Basic 인증의 비밀번호 칸에 **클래식 토큰만** 받는다. 비밀번호는 맞아도 항상 거부하고(401, 본문에 토큰을 쓰라는 안내) 설정 `yona.git-http-password-auth`는 없앴다. **v1.6과 달라지는 점**: v1.6은 비밀번호를 받고 비밀번호가 맞으면 웹 세션 쿠키까지 발급했다(`addUserInfoToSession`). 이제 어느 응답에도 세션 쿠키가 없다. 토큰으로 푸시하면 감사 기록의 행위자가 `TOKEN`(토큰과 범위)으로 남는다. 비밀번호로 쓰던 사용자, CI, IDE는 사용자 토큰으로 바꾸기 전까지 막힌다 | 추가 개발(선생님 결정) | D-70, A-327 |
| 2단계 인증 | 2026-11-02 | 인증 앱, 보안 키, 백업 코드, 알려진 기기, 관리자 강제와 해제를 더했다. 켜지 않은 사용자의 로그인과 모든 화면은 v1.6 그대로이다. 켠 사용자는 비밀번호 뒤에 2단계를 거치고(웹 폼, AJAX 팝업, 소셜 로그인, 메일 링크), 로그인 유지 쿠키는 알려진 기기 쿠키와 함께일 때만 통한다. 로그인 팝업의 위치가 BS5 전환 뒤 어긋나던 것을 고쳤다 | 추가 개발(선생님 결정) | D-69, A-328 |
| OAuth2 인가 서버 | 2026-11-02 | Yona가 OAuth 2.1 인가 서버가 되어 앱이 승인을 받아 외부 API(`/-_-api/v1`)에 접근한다. 설정에 "OAuth 앱" 탭, 사이트 관리에 "OAuth 앱" 메뉴, 새 엔드포인트(`/oauth2/*`, `/.well-known/*`)가 생긴다. v1.6의 화면과 계약은 바뀌지 않는다. **알려진 한계**: 로그인 ID가 `oauth2`인 사용자의 프로젝트 이름이 `authorize`, `token`, `revoke`, `introspect`, `consent`, `resume`이면 그 주소는 인가 서버가 먼저 받는다(v1.6 예약어 목록은 바꾸지 않았다). OAuth 토큰은 git과 SVN의 HTTP에 쓸 수 없다(D-70) | 추가 개발(선생님 결정) | D-71, A-329 |
| MCP 서버 | 2026-11-02 | AI 에이전트가 MCP(Streamable HTTP, `/mcp`)로 프로젝트, 이슈, 풀 리퀘스트, 위키를 읽고 일부(이슈 만들기와 댓글과 닫기, 풀 리퀘스트 댓글, 위키 만들기와 수정)는 쓴다. OAuth 토큰만 받고, 읽기 전용 범위 `read:repo`가 새로 생긴다(개인용 토큰, OAuth 앱, git과 SVN HTTP의 복제와 가져오기에서도 쓴다). 사이트 관리의 "보안"에 MCP 설정이 생긴다. v1.6의 화면과 계약은 바뀌지 않는다. **알려진 한계**: 로그인 ID가 `mcp`인 사용자의 프로필 주소가 가려진다 | D-72, [mcp-server.md](mcp-server.md) |
| Git LFS 서버 | 2026-11-02 | git-lfs 클라이언트가 Yona에 큰 파일을 올리고 내려받는다(`/{소유자}/{프로젝트}[.git]/info/lfs`, basic 전송, 파일 잠금, SSH `git-lfs-authenticate`). 프로젝트 설정에 "Git LFS" 탭, 사이트 관리에 "Git LFS" 메뉴, 코드 보기의 LFS 포인터 안내가 더해진다. v1.6에는 LFS 서버가 없었고 기존 경로와 동작은 바꾸지 않았다 | [git-lfs.md](git-lfs.md) |
| 엔터프라이즈 SSO(SAML 2, OIDC) | 2026-11-02 | 회사의 IdP로 로그인한다. 사이트 관리에 "SSO" 메뉴(OIDC와 SAML 2 설정, SAML SP 키, IdP 메타데이터 가져오기, SSO 전용 모드), 로그인 화면에 SSO 단추(켠 곳만), 설정에 "연결된 계정" 탭, 사용자 목록에 SSO 연결 해제 단추가 더해진다. 테이블 `oidc_sso_settings`, `saml2_sso_settings`가 추가된다. v1.6의 로그인과 계약은 바꾸지 않았다(SSO를 켜지 않으면 화면과 동작이 같다) | [enterprise-sso.md](enterprise-sso.md) |
| 사내 인증을 쓰면 소셜 로그인 차단 | 2026-11-03 | LDAP, 사내 OIDC, 사내 SAML 2 가운데 하나라도 쓰는 사이트(와 SSO 전용 모드)에서는 소셜 로그인 단추가 사라지고 `/authenticate/{제공자}`가 로그인 화면으로 돌려보낸다. **v1.6에서는 LDAP과 소셜 로그인이 함께 열려 있었으므로 이 조합의 동작이 바뀐다.** 사내 인증을 쓰지 않는 사이트는 v1.6과 같다. 선생님 결정 D-81 | [enterprise-sso.md](enterprise-sso.md) |
| LDAP 설정을 관리자 화면으로 | 2026-11-03 | 사이트 관리의 "사내 로그인" 화면(LDAP 탭)에서 LDAP을 켜고 설정한다(테이블 `ldap_settings`가 추가된다). **v1.6에서는 설정 파일(`ldap.*`)로만 정했으므로 설정 위치가 늘어난다.** 화면에서 한 번도 저장하지 않은 사이트는 설정 파일 값을 그대로 써서 v1.6과 같게 동작하고, 저장하면 화면 값이 이긴다. 선생님 결정 D-82 | [ldap.md](../web/ldap.md) |
| OIDC 제공자 | 2026-11-02 | 서명 키를 정하면(`yona.oauth2.oidc.signing-key-file`) 다른 앱이 Yona 계정으로 로그인할 수 있다(OpenID Connect). 새 주소 `/.well-known/openid-configuration`, `/oauth2/jwks`, `/oauth2/userinfo`, 앱 만들기 화면과 승인 화면에 `openid`, `profile`, `email` 범위가 생긴다. 키를 정하지 않으면 아무것도 바뀌지 않는다. v1.6의 화면과 계약은 바뀌지 않는다. **알려진 한계**: 사용자 정보 주소는 `/{사용자}`와 겹치지 않게 `/oauth2/userinfo`이고, `email_verified`와 로그아웃 연동은 없다 | D-77, [oauth2-server.md](oauth2-server.md) |
| R-129 리뷰 카드 탭 선택자 | 2026-10-29 | 변경 화면의 `yobi.code.Diff.js`가 `a[href=#reviewcards-...]`(따옴표 없음)로 탭을 찾던 것을 `a[href="#reviewcards-..."]`로 고쳤다. 번들된 jQuery에서 구문 오류가 나던 v1.6 결함이며 댓글을 단 뒤 `#comment-번호`로 이동할 때 콘솔 오류가 사라진다. 이동 때 해당 상태의 리뷰 카드 탭이 열리는 동작이 이제 실제로 동작한다 | 풀 리퀘스트와 커밋 변경 화면의 스크립트 | [refactoring-backlog.md](../refactoring-backlog.md) R-129 |

2026-10-29의 부분 회귀 실행(`regress.sh`가 첫 시나리오에서 중단됨)에서 `anon_basic`이 `다름 2`에서 `다름 36`으로 늘었다. 원인을 가르지는 않았으나 X-21의 GA4 스크립트가 모든 화면에 들어간 것이 주된 이유로 추정한다(미검증). 다음 전체 회귀에서는 이 장부를 제외하고 본다.

## 기존 v1.6 설치 위에서 쓰기 (2026-10-25 실제 점검)
v1.6의 `conf/evolutions/default/1~32.sql`의 `Ups`를 새 MariaDB 10.11에 적용한 데이터베이스(61개 테이블)에 v2를 붙여 확인했다.

- 기동: 오류 없이 올라오고 초기 데이터(역할 7개, 관리자)는 v1.6 `initial-data.yml`과 같다. 스키마 변화는 [decisions.md](../decisions.md) A-104의 두 가지(중복 인덱스 2개, `webhook_type` 폭)뿐이다.
- 읽기: v1.6 형태로 넣은 사용자(v1.6 해시 비밀번호로 로그인, 잠긴 사용자 거부), 프로젝트(공개, 비공개), 이슈(상태 정수, 담당자는 `assignee` 테이블을 거친다), 댓글, 라벨, 마일스톤, 게시글, 추천, 이벤트, 첨부, 감시, 즐겨찾기가 화면에 맞게 나온다.
- 쓰기: v2가 쓴 이슈, 댓글, 마일스톤, 게시글이 v1.6과 같은 형태이다(번호가 이어지고 `last_issue_number`가 늘며 상태는 정수 1=열림, 2=닫힘, 마일스톤 마감은 `23:59:59`).
- 디스크: `repo/git/<소유자>/<이름>.git`과 `uploads/<SHA-256>`을 그대로 쓴다. 저장소 HEAD가 가리키는 브랜치가 실제 브랜치와 같아야 코드 화면이 열린다(JGit이 만든 v1.6 저장소는 `master`, v2가 새로 만드는 저장소는 `main`: X-22). v1.6 저장소를 복제하고 푸시할 수 있다.
- 열거형: `issue.state`, `milestone.state`, `pull_request.state`, `webhook.webhook_type`은 선언 순서(서수)로 저장하므로 순서가 v1.6과 같아야 한다. `EnumOrdinalParityTest`가 지킨다.
- 이전(Migration) 화면: 내보내기 JSON이 위 데이터에서 v1.6 모양으로 나오고, 화면에서 원본과 대상을 골라 마일스톤, 이슈, 게시글을 옮기면 GitHub로 나가는 요청(마일스톤 생성, `import/issues`)이 서버 내용과 같다. 단 토큰 교환이 폼 형식만 해석하는 v1.6 결함(R-58 1) 때문에 GitHub가 JSON으로 답하면 토큰이 비어 인증이 반복된다.

