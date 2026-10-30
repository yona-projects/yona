---
title: 브랜치 보호 규칙
type: architecture
sources:
  - app/models/Project.java (isUsingReviewerCount, defaultReviewerCount, 60a5ac406)
  - app/models/PullRequest.java (getRequiredReviewerCount, isReviewed, 60a5ac406)
updated: 2026-10-30
---

# 브랜치 보호 규칙 (X-32, X-33, D-42~D-45)

v1.6은 프로젝트 설정의 "리뷰어 사용/사용 안함"과 "N 명"으로 프로젝트의 모든 풀 리퀘스트에 승인 수를 강제했다. GitHub는 기본으로 강제하지 않고 브랜치 보호 규칙을 만든 브랜치에만 병합과 푸시 조건을 건다. 선생님 결정([D-45](../decisions.md))으로 같은 방식으로 완전히 이행했다. 리뷰(승인, 변경 요청) 기능은 GitHub처럼 항상 쓸 수 있고, 필요한 승인 수는 규칙이 정한다.

## 규칙

테이블 `branch_protection_rule`이다(내보내기와 가져오기 대상, 프로젝트 삭제 때 함께 지운다). 프로젝트와 패턴의 조합은 유일하며 같은 패턴을 다시 저장하면 그 규칙을 고친다.

| 컬럼 | 뜻 |
|---|---|
| `project_id`, `pattern` | 규칙이 속한 프로젝트와 브랜치 이름이나 별표 패턴(`release/*`). 별표 하나는 `/`를 넘지 않고 둘(`**`)은 넘는다 |
| `required_approvals` | 병합에 필요한 승인 수(0~100). 0이면 승인으로는 막지 않는다. 작성자 본인의 승인은 세지 않는다(D-35 12번) |
| `require_no_changes_requested` | 켜면 변경 요청이 남은 풀 리퀘스트는 병합하지 못한다 |
| `require_conversation_resolution` | 켜면 열린 리뷰 스레드가 있는 풀 리퀘스트는 병합하지 못한다 |
| `require_linear_history` | 켜면 병합 커밋 방식으로 병합하지 못한다(스쿼시, 리베이스만) |
| `require_pull_request` | 켜면 이 브랜치에 직접 푸시하지 못하고 풀 리퀘스트로만 반영한다 |
| `allow_force_pushes`, `allow_deletions` | 강제 푸시와 브랜치 삭제를 허용한다. 기본은 끔(규칙이 있으면 막는다) |
| `enforce_admins` | 켜면 프로젝트 관리자와 사이트 관리자도 우회하지 못한다 |

## 적용

- 풀 리퀘스트와 푸시는 받는 브랜치에 맞는 규칙을 고른다(`Project.protectionFor`). 이름이 정확히 같은 규칙이 먼저이고 없으면 패턴이 가장 긴 규칙이다. 규칙이 없으면 아무것도 막지 않는다.
- **병합**(`PullRequest.reviewBlocked`, `PullRequestWorkflow.accept`): 승인 수가 모자라거나, 변경 요청 조건을 켰는데 남아 있거나, 대화 해결 조건을 켰는데 열린 스레드가 있으면 막는다. 선형 이력을 요구하면 병합 커밋을 쓸 수 있는 방식에서 뺀다(`PullRequest.allowedMergeMethods`). 규칙이 "관리자에게도 적용"이 아니면 프로젝트 관리자와 사이트 관리자는 병합 조건을 우회한다(`AccessControl.canBypassReview`).
- **푸시**(`GitHooks.preReceive`, `BranchProtectionRule.pushViolation`): `refs/heads/` 아래 ref만 보며, 삭제, 강제 푸시, 풀 리퀘스트를 요구하는 브랜치의 직접 푸시를 거부한다. 브랜치를 새로 만드는 푸시는 막지 않는다. 풀 리퀘스트 병합처럼 서버가 직접 브랜치를 갱신하는 경로는 검사하지 않는다. SSH 푸시는 없다.
- **삭제**: 브랜치 목록의 삭제는 같은 기준이고(관리자 우회 가능), 풀 리퀘스트의 "보내는 브랜치 삭제"는 삭제를 막는 규칙이 있으면 지우지 않는다.
- 화면의 승인 영역과 목록의 리뷰어 수, REST `approvals.required`도 규칙을 따른다. 규칙이 없으면 `required`는 0이다.

## 화면

프로젝트 설정의 "브랜치 보호" 영역 하나에 모두 있다. 규칙은 카드 한 장씩이며 브랜치 이름이나 패턴, 병합 조건(필요한 승인 수, 변경 요청, 대화 해결, 선형 이력), 푸시 제한(풀 리퀘스트 요구, 강제 푸시 허용, 삭제 허용), 관리자 적용 체크와 삭제 단추가 있다. "규칙 추가"로 카드를 늘린다. 카드는 설정 폼의 입력칸(`protectionKey`, `protection.<키>.<항목>`)이고 줄 추가와 삭제는 화면에서만 바뀐다. 설정 폼의 저장 단추 한 번으로 저장하며 서버는 규칙 목록을 통째로 맞춘다(`BranchProtectionService.replaceAll`). 폼이 규칙을 보냈다는 표시(`protectionRulesSent`)가 없으면 규칙은 그대로 둔다. 같은 영역에 "새 커밋이 오면 오래된 승인을 해제"(X-12)가 남아 있다.

## 기존 데이터와 무력화

`project.is_using_reviewer_count`와 `default_reviewer_count`는 **이번 버전부터 효력이 없고 차기 버전에서 컬럼을 제거한다.** 서버를 시작할 때와 데이터를 가져온 뒤에 이전이 돈다(`ReviewerSettingMigration`). 리뷰어 사용이 켜진 프로젝트는 모든 브랜치(`**`) 규칙 하나(승인 수는 `default_reviewer_count`, 변경 요청 해결과 관리자에게도 적용 켬, 직접 푸시 차단 끔, 강제 푸시와 삭제 허용)로 옮기고, `is_using_reviewer_count`를 꺼 이전했다는 표시로 쓴다. 그래서 v1.6 동작이 그대로 보존되고 몇 번을 돌려도 같다. 이전한 프로젝트의 설정 화면에는 그 사실을 알리는 안내가 보이고, 시작 로그에는 옮긴 프로젝트 수가 남는다. 값을 바꾸고 싶으면 `**` 규칙을 고치거나 지우면 된다.
