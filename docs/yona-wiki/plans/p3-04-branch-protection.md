---
type: plan
id: P3-04
title: "브랜치 보호"
status: planned
priority: 4
depends_on: []
blocks: []
source: docs/PARITY_BACKLOG.md#P3-04
created: 2026-08-28
updated: 2026-08-28
tags: [plan, p3, git, security]
---

# 브랜치 보호

## 배경

`ProtectedBranch` 모델과 관련 로직이 전무 — 설계만 진행됨. 적용 지점은 두 곳: (1) 직접 push 차단,
(2) PR 병합 시 체크. `AccessControl`과는 별개 레이어(권한 확인 통과 후 추가 정책)로 설계할 계획.
원본: [`docs/PARITY_BACKLOG.md#P3-04`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- `ProtectedBranch` 모델(`branch_pattern`, `require_pull_request`, `require_approvals`, `require_signed_commits`,
  `restrict_push_to`, `disallow_force_push`, `disallow_delete`, `admins_can_bypass`)
- 직접 push 차단 훅, PR 병합 시 체크
- `yona branch-protection set/list/unset` CLI 명령(선언만, 실제 구현은 [[p3-02-cli-and-rest-api]] 완료 후)

### 제외 (비범위)
- `require_signed_commits`의 실제 서명 검증 로직 — 이건 [[p3-03-ssh-gpg]]의 GPG 검증 파이프라인에 위임(이 계획은 플래그를 걸고 검증 결과를 소비하는 쪽만 구현, 검증 자체는 만들지 않음)

## 의존성

- **선행 조건**: 없음(독립적) — 단, `require_approvals` 구현 가능 여부를 확인하는 스파이크가 Step 1로 선행돼야 함
- **후속 파급**: `require_signed_commits` 플래그는 [[p3-03-ssh-gpg]]의 GPG 검증 파이프라인이 완성돼야 실질적으로 동작(그 전까지는 플래그만 있고 항상 통과 처리)

## 설계 개요

- **직접 push**: `GitPushHooks.kt`에 `BranchProtectionPreReceiveHook` 추가(기존 `RejectPushToReservedRefsPreReceiveHook`과 같은 방식)
- **PR 병합**: `PullRequestServiceImpl.merge()`/`processMergeCheck()`에 체크 추가
- **막힌 지점(선행 스파이크 필요)**: `require_approvals` 구현 가능 여부가 `CommentThread.ThreadState`에 승인/변경요청
  판정 상태가 있는지에 달려 있는데, 원본 백로그 작성 시점까지 확인되지 않았음 — 이 계획의 Step 1로 확정한다

## 단계별 작업 계획 (TDD)

1. **Step 1 — 스파이크: `CommentThread.ThreadState` 확인**
   - `domain/` 하위에서 `ThreadState` 정의를 찾아 승인/변경요청에 대응하는 상태가 있는지 확인
   - 없다면: `require_approvals`를 이번 범위에서 제외할지, 상태를 신규 추가할지 결정 필요 — 이 문서를 갱신
2. **Step 2 — `ProtectedBranch` 모델**
   - 실패 테스트: 패턴 매칭(`branch_pattern`이 `release/*` 같은 glob을 매칭하는지) → RED → 구현 → GREEN
3. **Step 3 — 직접 push 차단 훅**
   - 실패 테스트: 보호된 브랜치에 force-push 시도 시 거부 → RED → `BranchProtectionPreReceiveHook` 구현 → GREEN
   - `disallow_delete`, `restrict_push_to`도 각각 동일 패턴으로 추가
4. **Step 4 — PR 병합 체크**
   - 실패 테스트: `require_pull_request` 켜진 브랜치로의 직접 병합 없이 PR 없이 병합 시도 시 거부 → RED → 구현 → GREEN
   - Step 1 결과에 따라 `require_approvals` 포함 여부 결정
5. **Step 5 — `admins_can_bypass` 우회 경로**
   - 실패 테스트: 관리자 권한 사용자가 보호 정책을 우회할 수 있는지/없는지(플래그 값에 따라) → RED → 구현 → GREEN

## 완료 기준 (Definition of Done)

- [ ] `ProtectedBranch` 모델 및 패턴 매칭 테스트 존재
- [ ] 직접 push 차단(force-push, delete, restrict) 각각 테스트로 검증
- [ ] PR 병합 체크가 `require_pull_request` 최소 시나리오에서 동작
- [ ] `require_approvals` 착수 여부와 근거가 이 문서에 명시(스파이크 결과 반영)
- [ ] `require_signed_commits`는 플래그만 존재하고 [[p3-03-ssh-gpg]] 완료 전까지 항상 통과 처리됨을 명시적으로 테스트/문서화
- [ ] `./gradlew test` 전체 GREEN

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| `require_approvals` 실현 가능성 | `ThreadState`에 승인 개념이 있는지 미확인 상태로 백로그가 남겨짐 | Step 1 스파이크로 이 계획 시작 즉시 해소 |
| `AccessControl`과의 레이어 순서 | 권한 확인 통과 후 추가 정책이라는 설계만 있고 구체적 체이닝 방식 미정 | Step 3/4 구현 시 기존 `AccessControl` 호출 지점 뒤에 체이닝하는 방식으로 확정 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-04)
- 관련 계획: [[p3-03-ssh-gpg]](서명 검증 결과 소비)
- 관련 소스: `domain/vcs/GitPushHooks.kt`, `domain/pullrequest/PullRequestServiceImpl.kt`
