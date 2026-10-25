---
title: 직접 만든 요청에서만 닿는 v1.6과의 차이 (A-24 적용)
type: architecture
updated: 2026-10-26
---

# 직접 만든 요청에서만 닿는 v1.6과의 차이

화면과 스크립트가 보내는 요청으로는 닿지 않고, 값을 직접 만들어 보내야만 v1.6과 달라지는 항목이다. [A-24](../decisions.md)(화면을 쓰는 사용자 입장에서 정상 동작이면 요청 조작으로만 가능한 v1.6의 허점은 유지하지 않는다)와 선생님 지시(2026-10-26: v1.6이 오류를 내는 경우는 v2를 유지한다, 재현하지 않는 항목은 A-24에 따라 보완한다)에 따라 **v1.6을 재현하지 않고** 여기에 근거를 남긴다. v1.6 열은 v1.6 소스(`60a5ac406`) 기준이다.

| # | 대상 | v1.6 | v2 |
|---|---|---|---|
| U-06 | `UserApp.newUser` | `loginId`, `password`가 없으면 검증에서 NPE, 500 | 검증 오류 화면 |
| U-08 | `resetUserPassword` | 숨은 `loginId`가 없거나 형식이 틀리면 400 화면, `retypedPassword`는 검사하지 않음 | `loginId` 형식을 검사하지 않음 |
| U-09 | `resetUserPassword` | 비로그인이거나 `oldPassword`가 없으면 `IllegalArgumentException`, 500 | 로그인 이동 또는 400 |
| U-10 | 클래스 수준 `@AnonymousCheck` | `allowsAnonymousAccess=false`일 때 `resetUserPassword` 등 일부 액션은 애너테이션이 없어 그대로 진입(403/500) | 모두 로그인 화면으로 이동. 기본 설정에서는 같다 |
| U-19 | 사이트 관리자의 비밀번호 재설정 | `action` 쿼리가 없으면 NPE, 500. 값이 다르면 400 | 둘 다 400 |
| U-28 | `oAuthLogout`, `returnToReferer` | Referer가 없으면 URL이 아닌 `index().toString()`으로 이동 | 홈으로 이동(R-03과 같은 취지) |
| P-02 | `projectOverviewUpdate` | JSON이 아닌 본문이면 NPE, 500 | 400 |
| P-10 | `mentionList` | 알 수 없는 `resourceType`은 `IllegalArgumentException`, 500 | 무시하고 후보를 돌려줌 |
| P-15 | `mentionListAtPullRequest` | 없는 풀 리퀘스트는 NPE, 500 | 404(A-72와 같은 취지) |
| P-21 | 프로젝트 `newMember` | 폼 오류(`loginId` 필수, 형식, 예약어)는 `project.members.addMember`로 알림 | 형식이 틀린 `loginId`도 `project.member.notExist` |
| P-35 | 조직 `addMember` | `loginId` 제약을 어기면 `get()`에서 예외, 500 | 303과 경고 |
| P-40 | `updateOrganizationInfo` | 형식 오류와 권한 없음이 겹치면 `get()`이 권한 검사보다 앞서 500 | 403 |
| I-08 | `newIssueFormByComment` | 없는 `commentId`는 NPE, 500 | 404 |
| I-11 | `massUpdate` | Referer가 없으면 `redirect(null)` | 홈 또는 목록(R-03과 같은 취지) |
| I-12 | `newIssue` | `targetProjectId`, `milestoneId`, `parentIssueId`가 숫자가 아니면 `NumberFormatException`, 500 | 무시하거나 400 |
| I-20b | `editIssue` | 없는 이슈 NPE, 숫자가 아닌 `milestoneId` NFE, 500 | 404 또는 400 |
| B-01 | `organizationBoards` | `orderBy`를 덮어쓴 뒤 404, `HIDE_PROJECT_LISTING` 검사 없음, `orderBy` 무검증 | 정렬 기준 화이트리스트(A-37, A-40과 같은 취지) |
| B-03b | 게시글 `newPost` | `issueTemplate`이 없으면 NPE, 500. 권한 없는 사용자의 `notice`도 저장 | 오류 화면. 공지 권한이 없으면 무시(보안 보완) |
| B-08 | 게시판 `newComment` | `parentCommentId`가 비숫자면 NFE, 빈 문자열이고 기존 댓글이 있으면 NPE, 500 | 400 또는 부모 없음으로 처리 |
| P-03(4) | 프로젝트 홈 `tabId` | 알 수 없는 값은 `MatchError`, 500 ([R-68](../refactoring-backlog.md)(4)) | 내용 없는 탭 화면 |

## 재현하지 않고 기록만 하는 실패 경로

- [R-74](../refactoring-backlog.md) `changeVCS`: 저장소 생성 실패 시 v1.6은 README 해제와 포크 분리만 반영된 중간 상태가 남고, v2는 데이터베이스 변경을 모두 되돌린다.
- [R-73](../refactoring-backlog.md) `editIssue`, `deleteIssue`: 권한 확인 전에 먼저 반영하거나 발송하는 v1.6 결함을 v2는 고쳐 두었다(P-07).
