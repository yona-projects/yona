---
title: 풀 리퀘스트의 담당자와 라벨
type: web
sources: [app/models/User.java:447-478, app/models/NotificationEvent.java:872-905, app/models/NotificationEvent.java:988-1000, app/controllers/PullRequestApp.java:326-367, next:src/main/kotlin/com/github/yonaprojects/yona/domain/pullrequest/PullRequestServiceImpl.kt, next:src/main/kotlin/com/github/yonaprojects/yona/web/PullRequestApiController.kt]
updated: 2026-10-29
---

# 풀 리퀘스트의 담당자와 라벨

v1.6에 없는 추가 항목이다(로드맵 X-14). 결정과 근거는 [decisions.md](../decisions.md) D-34를 본다. 구현: `PullRequestAssigneeService`, `PullRequestLabelService`(도메인), `PullRequestAssigneeLabelController`(요청), `PullRequestMetaAssembler`(보기 화면 값), 템플릿 `pullrequest/view.html`, `pullrequest/partial-search.html`.

## 담당자란

처리가 끝나기 **전에** 이 풀 리퀘스트를 맡은 사람으로 지정된 사람이다. 작성자(보낸 사람), 리뷰어(리뷰 표시를 한 사람), 받은 사람(`receiver`, 받아들이거나 닫은 결과)과 다른 역할이다. GitHub은 한 건에 최대 10명까지 지정한다(공식 문서). Yona는 지금 **1명까지**이며 서비스의 상수 `PullRequestAssigneeService.MAX_ASSIGNEES`가 한도이다. 저장은 처음부터 연결 테이블이라 여러 명으로 키울 때는 이 상수와 화면의 선택(단일에서 다중)만 고치면 된다.

## 저장

| 테이블 | 컬럼 | 비고 |
|---|---|---|
| `pull_request_assignee` | `pull_request_id`, `user_id` | 두 컬럼이 기본 키라 한 쌍은 한 번만 들어간다 |
| `pull_request_issue_label` | `pull_request_id`, `issue_label_id` | 이슈 라벨을 그대로 쓴다 |

기존 `pull_request` 테이블은 바꾸지 않았다. v1.6 스키마와 다른 테이블이므로 `V16SchemaTest`는 "의도한 추가 목록"(`SchemaMetadata.INTENDED_ADDITIONS`)을 허용한다.

- **삭제 연쇄**: 프로젝트를 지우면(`ProjectDeletionService`) 그 프로젝트가 받는 풀 리퀘스트의 연결 행이 지워진다. 이슈 라벨을 지우면(`IssueLabelService.deleteLabel`) 풀 리퀘스트에서도 떨어진다. 사용자를 삭제 처리하면(`SiteUserService.changeState`) 담당자에서 빠진다.
- **내보내기와 가져오기**: `DataTables`에 두 테이블을 넣어 49개이다. v1.6 파일에는 이 테이블이 없어 그대로 가져와진다. 가져온 뒤 없는 풀 리퀘스트, 사용자, 라벨을 가리키는 연결 행은 지운다(`DataService.cleanOrphanLinks`).

## 담당자 지정

- **권한**: 풀 리퀘스트 수정 권한(`Operation.UPDATE`, v1.6 `@IsAllowed(UPDATE, PULL_REQUEST)`)을 그대로 따른다. 프로젝트 구성원과 공개, 보호 조직 프로젝트의 조직 구성원이다.
- **후보**: v1.6 이슈 담당자 후보 규칙(`User.findUsersByProjectAndOrganization`)을 그대로 쓴다. v2에서는 `IssueListService.assignableUsers`이다. (1) 프로젝트 구성원 전원, (2) 조직 소유 프로젝트이면 비공개는 조직 관리자만 공개와 보호는 조직 구성원 전원, (3) 보는 사람이 사이트 관리자이면 그 사람 자신. 이름순이다. 후보가 아닌 사람은 지정할 수 없다(400).
- **요청**: `POST /:user/:project/pullRequest/:번호/assignee`, 매개변수 `assigneeId`(비우면 해제). 끝나면 풀 리퀘스트 화면으로 303이다. 로그인하지 않았으면 로그인 화면으로, 권한이 없으면 403이다.
- 같은 사람을 다시 지정하거나 이미 비었는데 비우면 아무 일도 하지 않는다(이벤트, 알림 없음).
- 한도가 1명이므로 다시 지정하면 기존 담당자가 새 담당자로 바뀌고, 이벤트의 이전 값은 기존 담당자이다.

### 이벤트, 알림, 웹훅

| | 담당자 변경 | 라벨 변경 |
|---|---|---|
| 타임라인(`pull_request_event`) | `PULL_REQUEST_ASSIGNEE_CHANGED`(이전, 새 값은 로그인 ID) | `PULL_REQUEST_LABEL_CHANGED`(붙으면 새 값만, 떼면 이전 값만, `분류 - 이름 색` 형태) |
| 알림 | 보낸다 | **보내지 않는다** |
| 웹훅 | 보낸다 | **보내지 않는다** |

v1.6 이슈와 같다. 이슈 담당자 변경은 알림과 웹훅이 나가고(`afterAssigneeChanged`, `getReceiversWhenAssigneeChanged`) 이슈 라벨 변경은 `receivers = null`이라 받는 사람이 없고 웹훅 호출도 없다(`afterIssueLabelChanged`).

담당자 변경 알림의 수신자(`NotificationReceivers.forPullRequestAssigneeChanged`): 풀 리퀘스트를 직접 구독한 사용자, 작성자, **새 담당자**, 프로젝트에서 이 종류를 받겠다고 한 사용자. 구독 해제자와 이 종류를 받지 않겠다고 한 사용자, 바꾼 사람 본인은 뺀다. 이전 담당자는 본인이 아니면 더한다. 새 이벤트 종류 두 개는 사용자의 프로젝트별 알림 설정 목록(`notiTypes`)에도 나온다.

## 라벨

- 붙일 수 있는 것은 **받는 프로젝트(`toProject`)의 이슈 라벨뿐**이다. 다른 프로젝트의 라벨이 하나라도 있으면 400이고 아무것도 바꾸지 않는다.
- **요청**: `POST /:user/:project/pullRequest/:번호/labels`, 매개변수 `labelIds`(여러 개). 보낸 목록과 같아지도록 없는 것은 붙이고 목록에 없는 것은 뗀다(선택 상자가 고른 목록 전체를 보낸다). 비우면 모두 뗀다.
- 한 분류만 고르는 분류(`exclusive`)에 라벨을 붙이면 같은 분류의 기존 라벨이 빠진다(둘을 한꺼번에 보내면 뒤의 것만 남는다). 빠진 라벨마다 삭제 이벤트가 남는다.

## 화면

- **풀 리퀘스트 보기**: 작성자 줄 아래에 담당자 선택(`select#assignee-select`, 이슈의 사용자 선택과 같은 `data-format="user"`)과 라벨 선택(이슈 화면의 `partial-select-label`을 그대로 씀)이 있다. 수정 권한이 없으면 읽기만 한다(담당자 링크, 라벨 칩). 고르면 바로 보낸다(전체 페이지 이동). 타임라인에는 담당자와 라벨 변경이 나온다.
- **풀 리퀘스트 목록**(`/:user/:project/pullRequests`): 줄마다 제목 옆에 라벨 칩이, 오른쪽 칸에 담당자 아바타(마우스 설명 "담당자: 이름")가 받은 사람 아바타와 **나란히** 나온다. 열린 요청은 받은 사람이 없어 담당자 아바타만 보인다.
- **목록 필터**: 검색 상자의 고급 영역에 담당자와 라벨이 있다(보낸 요청 탭은 고급 영역 자체가 없다). 매개변수는 이슈 목록과 같은 `assigneeId`, `labelIds`이다. 라벨은 **고른 라벨을 모두 가진** 요청만 찾는다(이슈 목록과 같다). 기존 `contributorId` 필터와 기본 동작은 그대로이다.

## 후속 변경 예정 (2026-10-29 선생님 지시, 아직 구현하지 않았다)

위 문서의 "화면" 절은 **현재 구현**이다. 구현 뒤 검토에서 다음을 정했다(D-34 14~16).

- **Ajax 방식**(14번): 담당자와 라벨 선택이 이슈 화면처럼 화면을 다시 불러오지 않고 JSON을 보내며 알림창으로 알린다. 지금은 고를 때마다 폼을 보내 화면이 다시 열린다(라벨은 하나를 고를 때마다).
- **모든 풀 리퀘스트 목록**(15번): 조직 목록, 프로젝트 홈 대시보드, 사용자 화면의 풀 리퀘스트 탭에도 라벨 칩과 담당자 아바타를 보인다. 지금은 프로젝트 목록만 보인다.
- **모양**(16번, 미정): 이슈 화면처럼 오른쪽 열로 옮길지(권장) 오른쪽 패널만 둘지 정한다.

## 하지 않은 것

- REST API(담당자, 라벨 지정과 조회)는 X-15(풀 리퀘스트 REST)로 옮겼다. 담당자는 처음부터 `assignees`(목록) 모양으로 공개한다.
- 조직 풀 리퀘스트 목록, 대시보드, 사용자 화면의 풀 리퀘스트 줄에는 지금 담당자와 라벨을 보이지 않는다(프로젝트 목록만). 위 "후속 변경 예정"에서 넓히기로 했다.
- 풀 리퀘스트 담당자 변경의 웹훅 문구는 이벤트 종류 이름만 담는다(이슈 담당자 변경과 같다).

## 시험

`PullRequestAssigneeServiceTest`, `PullRequestLabelServiceTest`(서비스, 이벤트, 알림 수신자), `PullRequestAssigneeLabelStorageTest`(저장과 삭제 연쇄), `PullRequestAssigneeLabelWebTest`(보기, 요청, 타임라인, 목록, 필터), `WebhookDispatchTest`(웹훅), `AddedTablesSchemaTest`, `V16SchemaTest`, `DataTablesTest`, `DataExchangeTest`(내보내기와 가져오기), `MessageParityTest`(새 메시지 키).
