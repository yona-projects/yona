---
type: plan
id: P3-03
title: "SSH git 인증 + GPG 커밋 서명 검증"
status: planned
priority: 7
depends_on: [p3-02-cli-and-rest-api]
blocks: [p3-04-branch-protection]
source: docs/PARITY_BACKLOG.md#P3-03
created: 2026-08-28
updated: 2026-08-28
tags: [plan, p3, git, auth, security]
---

# SSH git 인증 + GPG 커밋 서명 검증

## 배경

원본 요나·yona 둘 다 SSH git 프로토콜/GPG 관련 코드가 전혀 없어 이식 대상이 아니라 **완전 신규 기능**이다.
SSH 키 관리와 GPG 키 관리(커밋 서명 검증 전용, 로그인 인증 용도는 배제) 두 축으로 구성된다.
원본: [`docs/PARITY_BACKLOG.md#P3-03`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- SSH 키 관리(OS별 이원화: 리눅스/맥 OpenSSH 훅 방식, 윈도우 폴백은 Apache MINA SSHD)
- GPG 키 관리 + 커밋 서명 검증 pre-receive 훅
- HTTPS 경로의 Deploy Key 인증(`GitAuthorizationFilter` 뒤에 신규 `AuthenticationProvider` 추가)

### 제외 (비범위)
- GPG를 로그인 인증 수단으로 쓰는 것(커밋 서명 검증 전용으로 한정)
- `DeployKey`/`ApiToken` 엔티티 통합 — 조사 완료, 채택 안 함(아래 설계 개요 참고)

## 의존성

- **선행 조건**: [[p3-02-cli-and-rest-api]]의 `ApiToken` 저장소 범위·리소스 스코프 모델이 확정돼야
  `DeployKey`가 그 스코프 체계를 공유하도록 설계할 수 있음 — 강한 블로커는 아니지만(HTTPS 경로는 독립적으로도
  착수 가능) SSH 프로토콜 전체가 신규 구현이라 순서상 P3-02 이후로 배치
- **후속 파급**: [[p3-04-branch-protection]]의 `require_signed_commits` 플래그가 이 계획의 GPG 검증 결과를 소비

## 설계 개요

### SSH 키 관리 — OS별 이원화

- **리눅스/맥(표준 경로)**: 시스템 OpenSSH `AuthorizedKeysCommand` 훅 — `yona internal ssh-auth`(공개키→사용자 조회,
  forced command 라인 생성), `yona internal ssh-shell`(`SSH_ORIGINAL_COMMAND` 파싱 후 AccessControl 체크·git 프로세스 exec)
  두 헬퍼 필요(둘 다 신규 구현, [[p3-02-cli-and-rest-api]]의 Go CLI에 서브커맨드로 추가하는 게 자연스러움)
- **윈도우(폴백)**: JVM 내장 Apache MINA SSHD로 별도 포트(예: 2222) 구동
- 두 경로를 공통 서비스 레이어(`sshAuthService.authenticate()`, `authorizeGitCommand()`)로 통합
- `SshKey` 모델(user_id, public_key, fingerprint, added_date), `UserApi`에 등록/삭제/조회 엔드포인트 신설

### GPG 키 관리

- `GpgKey` 모델(user_id, key_id, armored_public_key, added_date)
- `GpgSignatureVerifyPreReceiveHook`(`GitPushHooks.kt`의 `RejectPushToReservedRefsPreReceiveHook` 옆에 나란히,
  JGit `RevCommit.getRawGpgSignature()` + BouncyCastle `bcpg-jdk18on` 사용)
- Commit 모델에 검증결과 필드 추가 → UI에 Verified 배지
- 정책(잠정, Step 착수 시 확정): 미서명 커밋도 push는 허용(배지만 안 붙음) / author 이메일과 GPG UID 이메일 일치로 매칭

### SSH/HTTPS 인증 통합 — Deploy Key로 스코프 통일

`SshKey`(user 전역)와는 별개로 `DeployKey`(repository_id, public_key, fingerprint, read_only 플래그, added_date)를
신설해 **저장소 범위**로 스코프된 자격증명 개념으로 통합. GitHub/GitLab/Forgejo 세 곳 모두 SSH 공개키와 불투명
토큰을 하나의 엔티티로 합치지 않는다는 점을 조사 완료(비대칭키 vs 시크릿 문자열로 인증 메커니즘 자체가 다름) —
**`DeployKey`/`ApiToken` 완전 통합은 채택하지 않고, 별개 엔티티 + 스코프 체계 공유로 확정**.

HTTPS 경로 실현 가능성은 검증 완료: `SecurityConfig.kt`에 `.httpBasic { }`이 전역 활성화돼 있고
`.addFilterAfter(gitAuthorizationFilter, BasicAuthenticationFilter::class.java)`로 `GitAuthorizationFilter`가
Basic 인증 이후 `/git/**` 프로젝트 단위 접근 제어를 수행하는 구조 — **새 `AuthenticationProvider` 하나만 추가**하면
(Basic 인증 자격증명 검증 단계에 Deploy Key 매칭 로직을 끼워넣는 방식) 기존 `GitAuthorizationFilter`를 그대로
재사용 가능. SSH 프로토콜 자체(포트 22 리스닝)는 재사용할 기존 코드가 전혀 없는 완전 신규 구현.

## 단계별 작업 계획 (TDD)

### 1부 — HTTPS Deploy Key (재사용 가능 구조, 먼저 착수)

1. **Step 1 — `DeployKey` 모델**
   - 실패 테스트: `repository_id` 범위 밖 저장소 접근 시 거부 → RED → 구현 → GREEN
2. **Step 2 — HTTPS `AuthenticationProvider` 추가**
   - 실패 테스트: Deploy Key로 Basic 인증 → 스코프 내 저장소만 clone/push 가능 → RED → 구현 → GREEN(기존 `GitAuthorizationFilter` 재사용 확인)

### 2부 — SSH 프로토콜 (완전 신규)

3. **Step 3 — `SshKey` 모델 + `UserApi` 등록/삭제/조회**
4. **Step 4 — 리눅스/맥 `AuthorizedKeysCommand` 훅**
   - `yona internal ssh-auth`/`yona internal ssh-shell` 구현, 로컬 OpenSSH로 실제 clone/push 통합 테스트
5. **Step 5 — 윈도우 Apache MINA SSHD 폴백**
6. **Step 6 — 공통 서비스 레이어로 두 경로 통합**(`sshAuthService`)

### 3부 — GPG 서명 검증

7. **Step 7 — `GpgKey` 모델 + 등록/삭제/조회 API**
8. **Step 8 — `GpgSignatureVerifyPreReceiveHook`**
   - 실패 테스트: 서명 없는 커밋 push는 허용되지만 배지 없음, 서명 있고 매칭 안 되는 커밋은 검증 실패 상태로 저장 → RED → 구현 → GREEN
9. **Step 9 — Commit 모델 검증결과 필드 + UI 배지**

## 완료 기준 (Definition of Done)

- [ ] Deploy Key로 HTTPS git clone/push가 저장소 범위 내에서만 동작
- [ ] SSH 키 등록 후 리눅스/맥에서 실제 `git clone git@host:...` 성공(수동 검증)
- [ ] 윈도우 폴백 경로 최소 1회 수동 검증
- [ ] GPG 서명된 커밋이 push 후 Verified 배지로 표시됨
- [ ] `DeployKey`/`ApiToken` 스코프 체계 공유 여부가 이 문서와 [[p3-02-cli-and-rest-api]] 양쪽에 일관되게 기록
- [ ] `./gradlew test` 전체 GREEN

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| SSH 프로토콜 신규성 | 재사용 가능한 기존 코드 없음, 리스크가 가장 큰 구간 | Step 4를 로컬 환경 수동 검증까지 포함해 충분히 검증 |
| GPG 정책 미확정 | 미서명 커밋 허용 여부, 이메일 매칭 규칙, CLI 로컬 git 설정 자동화 여부 | Step 7 착수 전 확정하고 이 문서 갱신 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-03)
- 관련 계획: [[p3-02-cli-and-rest-api]](스코프 체계 공유), [[p3-04-branch-protection]](서명 검증 결과 소비)
- 관련 소스: `config/SecurityConfig.kt`, `domain/vcs/GitPushHooks.kt`
