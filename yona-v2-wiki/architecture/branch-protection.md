---
title: 브랜치 보호 규칙
type: architecture
sources:
  - app/models/Project.java (isUsingReviewerCount, defaultReviewerCount, 60a5ac406)
  - app/models/PullRequest.java (getRequiredReviewerCount, isReviewed, 60a5ac406)
updated: 2026-10-30
---

# 브랜치 보호 규칙 (X-32, D-42, D-43)

v1.6은 프로젝트 설정의 "리뷰어 사용/사용 안함"과 "N 명"으로 프로젝트의 모든 풀 리퀘스트에 승인 수를 강제했다. GitHub는 기본으로 강제하지 않고 브랜치 보호 규칙을 만든 브랜치에만 요구한다. 선생님 결정([D-42](../decisions.md))으로 같은 방식으로 바꿨다.

## 규칙

새 테이블 `branch_protection_rule`이다(내보내기와 가져오기 대상, 프로젝트 삭제 때 함께 지운다).

| 컬럼 | 뜻 |
|---|---|
| `project_id` | 규칙이 속한 프로젝트 |
| `pattern` | 브랜치 이름(`master`)이나 별표 패턴(`release/*`). 별표 하나는 `/`를 넘지 않고 별표 두 개는 넘는다 |
| `required_approvals` | 병합에 필요한 승인 수(1~100). 작성자 본인의 승인은 세지 않는다(D-35 12번) |
| `enforce_admins` | 켜면 프로젝트 관리자와 사이트 관리자도 우회하지 못한다. 기본은 끔 |
| `created_date` | 만든 시각 |

프로젝트와 패턴의 조합은 유일하다. 같은 패턴을 다시 저장하면 그 규칙을 고친다.

## 적용

- 풀 리퀘스트의 받는 브랜치에 맞는 규칙을 고른다(`Project.protectionFor`). 이름이 정확히 같은 규칙이 먼저이고, 없으면 패턴이 가장 긴 규칙이다.
- 맞는 규칙이 없고 프로젝트가 리뷰어를 쓰면(`is_using_reviewer_count`), 설정한 승인 수(`default_reviewer_count`)를 요구하는 **프로젝트 전체 기본 규칙**이 적용된다(D-43). 저장하지 않는 임시 규칙이며 관리자가 우회할 수 있다.
- 둘 다 없으면 승인 수와 변경 요청으로 막지 않는다. 규칙이 있으면 승인이 모자라거나 변경 요청이 남아 있을 때 병합을 막는다(`PullRequest.reviewBlocked`).
- 프로젝트 관리자와 사이트 관리자는 규칙이 "관리자에게도 적용"이 아니면 우회한다(`AccessControl.canBypassReview`, D-35 11번).
- 화면의 승인 영역과 목록의 리뷰어 수, REST `approvals.required`도 규칙을 따른다. 규칙이 없으면 `required`는 0이다.

## 화면

프로젝트 설정의 "리뷰어" 영역에 사용 여부와 인원 수(프로젝트 전체 기본 규칙)와 "새 커밋이 오면 승인 해제"(X-12)가 있다. 그 아래 설정 폼과 같은 카드 모양의 "브랜치 보호" 영역(`#branchProtectionPanel`)이 있다. 규칙은 표(브랜치, 필요한 승인, 관리자 우회 여부, 수정/삭제)로 보이고, 마지막 줄이 규칙이 없는 다른 브랜치의 적용 방식(제한 없음 또는 프로젝트 기본값)을 알려 준다. 수정 단추는 입력 줄에 값을 채우며, 저장은 설정 폼과 별개의 요청(`POST .../setting/protections`, `.../setting/protections/{id}/delete`)이고 프로젝트 관리자만 쓴다.

## 기존 데이터

`project.is_using_reviewer_count`, `default_reviewer_count` 컬럼은 v1.6과 같고 프로젝트 전체 기본 규칙으로 쓰인다. 리뷰어 수를 켜 두었던 프로젝트는 그대로 유지된다(D-42의 일괄 해제는 D-43이 취소했다).
