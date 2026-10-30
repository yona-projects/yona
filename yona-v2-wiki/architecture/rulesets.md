---
title: 룰셋(브랜치 보호)
type: architecture
sources:
  - app/models/Project.java (isUsingReviewerCount, defaultReviewerCount, 60a5ac406)
  - app/models/PullRequest.java (getRequiredReviewerCount, isReviewed, 60a5ac406)
  - https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets
  - https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets
updated: 2026-10-30
---

# 룰셋 (X-32, X-33, X-34, D-42~D-46)

v1.6은 프로젝트 설정의 "리뷰어 사용/사용 안함"과 "N 명"으로 프로젝트의 모든 풀 리퀘스트에 승인 수를 강제했다. GitHub는 기본으로 강제하지 않고, 지금은 룰셋(Ruleset)이라는 이름 있는 규칙 묶음을 만들어 브랜치의 병합과 푸시에 조건을 건다. 선생님 결정([D-46](../decisions.md))으로 같은 구조로 옮겼다. 리뷰(승인, 변경 요청) 기능은 GitHub처럼 항상 쓸 수 있고, 필요한 승인 수는 룰셋이 정한다.

## 룰셋

테이블 `ruleset`이다(내보내기와 가져오기 대상, 프로젝트 삭제 때 함께 지운다). 프로젝트 안에서 이름이 유일하다.

| 컬럼 | 뜻 | GitHub 대응 |
|---|---|---|
| `name` | 룰셋 이름(필수) | Ruleset Name |
| `enforcement` | 적용 상태: `active`, `disabled` | Enforcement status(평가 모드는 없다, X-35) |
| `pattern` | 대상 브랜치 이름이나 별표 패턴(`release/*`). 별표 하나는 `/`를 넘지 않고 둘(`**`)은 넘는다 | Target branches(◐ 패턴 하나) |
| `bypass_admins` | 켜면 프로젝트 관리자와 사이트 관리자가 이 룰셋을 우회한다. 기본은 끔 | Bypass list(◐) |
| `restrict_deletions` | 삭제 제한. 기본 켬 | Restrict deletions |
| `block_force_pushes` | 강제 푸시 차단. 기본 켬 | Block force pushes |
| `require_pull_request` | 직접 푸시 차단, 풀 리퀘스트로만 반영 | Require a pull request before merging |
| `require_linear_history` | 병합 커밋 방식 금지 | Require linear history(◐ 병합 때만) |
| `required_approvals` | 병합에 필요한 승인 수(0~100, 0이면 승인으로 막지 않음). 작성자 본인의 승인은 세지 않는다(D-35 12번) | Required approvals(◐ 범위) |
| `require_no_changes_requested` | 변경 요청이 남으면 병합 불가 | (GitHub는 승인 수와 함께 다룬다) |
| `require_conversation_resolution` | 열린 리뷰 스레드가 있으면 병합 불가 | Require conversation resolution |

승인 수, 변경 요청, 대화 해결은 GitHub와 달리 "풀 리퀘스트 필요" 체크와 상관없이 병합 때 적용한다(v1.6의 리뷰어 수 동작을 이전하기 위한 ◐ 차이이다).

## 적용 (합산)

- 풀 리퀘스트와 푸시는 받는 브랜치에 맞는 **활성 룰셋 전부**를 적용한다(`Project.rulesetsFor`). GitHub처럼 우선순위가 없고 합산한다. 승인 수는 가장 큰 값이고, 제한은 합집합이다. 비활성 룰셋과 대상이 아닌 룰셋은 아무것도 막지 않는다.
- **병합**(`PullRequest.blockingRulesets`, `PullRequestWorkflow.accept`): 룰셋마다 승인 수가 모자라거나, 변경 요청 조건을 켰는데 남아 있거나, 대화 해결 조건을 켰는데 열린 스레드가 있으면 그 룰셋이 막는 것이다. 선형 이력을 요구하는 룰셋이 하나라도 있으면 병합 커밋을 쓸 수 있는 방식에서 뺀다(`PullRequest.allowedMergeMethods`).
- **바이패스는 룰셋마다 판정한다**(`AccessControl.canBypass`): 막고 있는 룰셋이 모두 바이패스를 허용할 때만 프로젝트 관리자와 사이트 관리자가 우회한다.
- **푸시**(`GitHooks.preReceive`, `Ruleset.pushViolation`): `refs/heads/` 아래 ref만 보며, 삭제, 강제 푸시, 풀 리퀘스트를 요구하는 브랜치의 직접 푸시를 거부한다. 우회하지 못하는 룰셋이 막으면 거부하고 메시지에 룰셋 이름을 적는다. 브랜치를 새로 만드는 푸시는 막지 않는다. 풀 리퀘스트 병합처럼 서버가 직접 브랜치를 갱신하는 경로는 검사하지 않는다. SSH 푸시는 없다.
- **삭제**: 브랜치 목록의 삭제는 같은 기준이고, 풀 리퀘스트의 "보내는 브랜치 삭제"는 삭제를 막는 룰셋이 있으면 지우지 않는다(관리자 우회 없음).
- 화면의 승인 영역과 목록의 리뷰어 수, REST `approvals.required`도 룰셋을 따른다. 룰셋이 없으면 `required`는 0이다.

## 화면

프로젝트 설정 메뉴(Git 프로젝트)에 "룰셋" 탭이 있다.

- **목록**(`GET .../setting/rulesets`): 이름순으로 이름, 대상, 적용 상태, 규칙 요약을 보인다. "새 룰셋"으로 만든다.
- **편집**(`GET .../setting/rulesets/new`, `.../{id}`): 이름, 적용 상태, 바이패스, 대상 브랜치, 규칙을 GitHub의 편집 화면 순서로 둔다. 저장은 `POST .../setting/rulesets`(만들기)와 `.../{id}`(고치기), 삭제는 `.../{id}/delete`이다. 입력이 잘못되면 400으로 편집 화면을 다시 보인다. 프로젝트 관리자만 쓰며 Git 프로젝트에서만 동작한다.
- 설정 화면의 "리뷰어" 영역에는 "새 커밋이 오면 오래된 승인을 해제"(X-12)만 남았다(룰셋 단위 이전은 X-35).

## 기존 데이터와 무력화

`project.is_using_reviewer_count`와 `default_reviewer_count`는 **이번 버전부터 효력이 없고 차기 버전에서 컬럼을 제거한다.** 서버를 시작할 때와 데이터를 가져온 뒤에 이전이 돈다(`ReviewerSettingMigration`). 리뷰어 사용이 켜진 프로젝트는 "이전된 리뷰어 설정" 룰셋 하나(대상 `**`, 승인 수는 `default_reviewer_count`, 변경 요청 해결 켬, 바이패스 비움, 직접 푸시 차단과 삭제 제한과 강제 푸시 차단은 끔)로 옮기고, `is_using_reviewer_count`를 꺼 이전했다는 표시로 쓴다. 그래서 v1.6 동작이 그대로 보존되고 몇 번을 돌려도 같다. 룰셋 목록 화면에 그 사실을 알리는 안내가 보이고 시작 로그에 옮긴 프로젝트 수가 남는다. 값을 바꾸고 싶으면 그 룰셋을 고치거나 지우면 된다.

## 범위 밖(X-35 이후)

바이패스 목록(구성원, 조직 관리자, 개별 사용자)과 우회 방식(GitHub는 Always, Exempt, For pull requests only 세 가지), 대상 여러 개와 제외, 선형 이력의 푸시 검사, 승인 해제와 병합 방식의 룰셋 단위 이전, 평가 상태. 상태 검사, 서명 커밋, 코드 소유자, 배포, 스캔류는 기반 기능이 생기면 규칙 목록에 더한다.
