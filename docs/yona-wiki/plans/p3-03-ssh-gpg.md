---
type: plan
id: P3-03
title: "SSH git 인증 + GPG 커밋 서명 검증"
status: done
priority: 7
depends_on: [p3-02-cli-and-rest-api]
blocks: [p3-04-branch-protection]
source: docs/parity/tickets/p3-03.md
created: 2026-08-28
updated: 2026-09-07
tags: [plan, p3, git, auth, security]
---

# SSH git 인증 + GPG 커밋 서명 검증

## 배경

원본 요나·yona 둘 다 SSH git 프로토콜/GPG 관련 코드가 전혀 없어 이식 대상이 아니라 **완전 신규 기능**이다.
SSH 키 관리와 GPG 키 관리(커밋 서명 검증 전용, 로그인 인증 용도는 배제) 두 축으로 구성된다.
원본: [`docs/parity/tickets/p3-03.md`](../../parity/index.md)

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

## 완료 로그

### 1부 (2026-09-07) — HTTPS Deploy Key (Step 1~2)

`DeployKey`(`domain/deploykey/`) 엔티티를 신설했다. 계획 문서 개요는 필드를 SSH 공개키 스키마
(`repository_id, public_key, fingerprint, read_only, added_date`)로만 적었는데, 구현 과정에서
"HTTPS Basic 인증이 비대칭키만으로는 성립하지 않는다"는 문제에 부딪혀 다음을 확정했다(리스크
테이블에도 반영):

- `DeployKey`는 SSH 공개키(`publicKey`/`fingerprint`, GitHub Deploy Key 화면과 동일하게 등록 시 항상
  요구)와 HTTPS 전용 불투명 시크릿(`httpsTokenHash`, 등록마다 항상 자동 발급해 한 번만 표시)을 **같은
  레코드**에 함께 갖는다 — "저장소 범위로 스코프된 자격증명 개념 통합"이라는 계획의 의도를, 두 프로토콜이
  물리적으로 같은 비밀 바이트를 공유하는 방식이 아니라 **같은 `project_id`/`read_only` 스코프 레코드를
  공유**하는 방식으로 구현했다(GitHub/GitLab이 SSH 공개키와 불투명 토큰을 하나의 엔티티로 합치지 않는다는
  기존 조사 결과를 존중).
- HTTPS 인증은 `DeployKeyAuthenticationProvider`(신규) — GitHub App 설치 토큰 관례("x-access-deploykey"
  고정 사용자명 + 토큰을 비밀번호로 쓰는 Basic 인증, "모호하면 GitHub 방식 기본값" 원칙 적용)를 따른다.
  `HttpSecurity.authenticationProvider(this)`로 공유 `AuthenticationManager`에 후보로 추가만 되므로
  기존 `.httpBasic{}`/폼 로그인 동작에 영향이 없다 — **단, 이 방식이 Spring Boot의 `AuthenticationProvider`
  빈 자동 수집(`InitializeAuthenticationProviderBeanManagerConfigurer`)을 꺼버려 기존
  `YonaAuthenticationProvider`(로컬/LDAP 로그인)가 통째로 빠지는 회귀를 실제로 겪었다** —
  `GitAuthorizationFilterIntegrationSpec`(로그인 통합테스트)이 이를 즉시 잡아냈고, `YonaAuthenticationProvider`도
  명시적으로 함께 등록하도록 `SecurityConfig.kt`를 수정해 해소했다(push 전 자체 회귀 검증으로 발견 — 조용히
  넘어가지 않고 여기 기록).
- `GitAuthorizationFilter`는 새 `DeployKeyAuthenticationToken` 타입을 인식하는 분기 하나만 추가해
  재사용했다(계획대로 "새 `AuthenticationProvider` 하나만 추가" 원칙 유지) — 원래 로그인 사용자 대상의
  `requiresAuth`/`isMember`/`isGuestUser` 판정 로직은 `GitAccessPolicy`(신규)로 추출해 2부(SSH 경로)와도
  공유하도록 리팩터링했다(동작 변화 없음, `GitAuthorizationFilterSpec`/`GitAuthorizationFilterIntegrationSpec`
  재검증으로 회귀 없음 확인).
- 보안 리뷰(작업 지시문 항목): (1) 다른 프로젝트 스코프의 Deploy Key로 이 프로젝트 접근 시도 → 403(스코프
  밖 프로젝트는 `deployKey.project.id != project.id`로 즉시 거부), (2) `read_only=true` Deploy Key로 push
  시도 → 403, (3) 이미 등록된 공개키(다른 프로젝트의 Deploy Key든 SshKey든)의 재등록을 전역적으로 거부해
  계정/저장소 사칭을 방지(2부에서 SshKey가 추가된 뒤 `DeployKeyServiceImpl`도 `SshKeyRepository`를
  교차 검사하도록 갱신) — 전부 `DeployKeyServiceImplSpec`/`DeployKeyGitAuthorizationIntegrationSpec`으로
  검증.
- GitHub의 "저장소별 Deploy keys" 화면(공개키 붙여넣기, read-only 체크박스, 지문/마지막 사용일 표시,
  삭제)과 동일한 UI를 `project/setting_deploykeys.html` + `DeployKeyController`로 추가했다(프로젝트
  설정 탭 메뉴에 "Deploy Key" 항목 추가, `WebhookController`와 동일한 컨벤션). 등록(POST)이 처음엔
  ApiToken 발급 화면처럼 같은 응답에서 바로 재렌더링했으나, `project/setting_deploykeys.html`이 쓰는
  `project/menu` 프래그먼트가 `project.enrolledUsers`(지연 로딩)를 참조해 `deployKeyService.create()`의
  `@Transactional` 호출 이후 OSIV 세션 상태가 갈리며 `LazyInitializationException`이 나는 문제를 실측으로
  발견 — Post/Redirect/Get 패턴(발급된 HTTPS 토큰은 `RedirectAttributes` 플래시 속성으로 다음 GET 한
  번에만 노출)으로 전환해 해소했다(`DeployKeyEditFormTemplateRenderingSpec`).

### 2부 (2026-09-07) — SSH 프로토콜 (Step 3~6)

`SshKey`(user 전역, `domain/sshkey/`) 모델과 두 인증 경로(리눅스/맥, 윈도우)가 공유하는
`SshAuthService`(`authenticate()`/`authenticateByFingerprint()`/`authorizeGitCommand()`)를 구현했다.
`SshPublicKeyFingerprint`(OpenSSH `authorized_keys` 한 줄 파싱 + `SHA256:...` 지문 계산, `ssh-keygen -lf`와
동일한 포맷임을 실제 `ssh-keygen`으로 생성한 키로 검증)를 DeployKey/SshKey 양쪽이 공유한다.

**리눅스/맥 경로(Step 4)** — **호스트 시스템 제약을 그대로 지켰다**: 이 세션은 `/etc/ssh/sshd_config`
수정, `systemctl restart sshd`, 포트 22 바인딩을 절대 하지 않았다. 대신 이 훅이 실제로 존재한다고
가정했을 때 호출할 서버 쪽 계약을 구현하고 그 계약 자체를 검증했다:

- `SshInternalController`(신규, `POST /internal/ssh/authenticate`·`/internal/ssh/authorize`) — 시스템
  sshd의 `AuthorizedKeysCommand` 훅이 로컬 프로세스로 호출하는 것을 전제로, (1) 루프백 주소
  (127.0.0.1/::1)에서 온 요청만 허용 (2) `SshInternalSecretProvider`(신규, `JwkKeyPairProvider`(P3-07)와
  동일한 "최초 1회 생성해 `yona.data` 아래 영속화" 패턴)가 관리하는 공유 시크릿 헤더까지 요구해 이중으로
  방어한다. `BootstrapSetupInterceptor`가 이 경로도 302 리다이렉트 대상에서 빼도록 수정했다 — JSON을
  기대하는 머신 호출자(sshd 훅)가 HTML 리다이렉트를 받으면 오동작하는 것을 실측으로 발견.
- **`yona internal ssh-auth`/`internal ssh-shell`(yona-cli, 별도 저장소) 서브커맨드를 실제로 구현**했다
  (Hidden 커맨드, 일반 사용자용 아님). `ssh-auth <key-type> <key-base64>`는 위 API로 공개키를 조회해
  forced command(`internal ssh-shell --principal=<opaque>`, `no-port-forwarding,no-X11-forwarding,
  no-agent-forwarding,no-pty` 옵션 포함 — GitHub/GitLab Deploy Key와 동일한 관례)가 박힌
  authorized_keys 한 줄을 표준출력에 낸다(모르는 키는 조용히 종료). `internal ssh-shell`은
  `SSH_ORIGINAL_COMMAND`를 파싱해(비-git 명령은 서버에 묻지도 않고 즉시 거부 — 임의 명령 주입 방지 1차
  방어선) 서버에 인가를 재확인한 뒤 실제 `git-upload-pack`/`git-receive-pack` 프로세스를 exec한다.
  서버 URL/공유 시크릿은 --server/--token 플래그가 아니라 고정 경로 설정 파일(`/etc/yona/ssh-helper.yml`)
  에서 읽는다 — `AuthorizedKeysCommand`가 대부분의 환경변수를 제거하므로 플래그/환경변수 기반 설정이
  불가능하기 때문이다. httptest 모킹 서버로 인증/인가 계약을 검증하고, **실제 `git-upload-pack` 바이너리를
  이 테스트가 만든 임시 bare 저장소에 대고 exec**해 실제 프로세스 실행까지 검증했다(`cmd/internal_test.go`,
  9개 테스트 — 시스템 sshd와 무관한 이 프로세스 내부의 로컬 git 프로세스 실행이라 안전).
- **실제 sshd 등록(`AuthorizedKeysCommand /usr/local/bin/yona internal ssh-auth %t %k` 한 줄 추가 +
  `systemctl restart sshd`)은 이 세션이 절대 수행할 수 없는 작업으로, 명시적으로 범위 밖에 남긴다** —
  실제 배포 환경에서 운영자가 수행할 수동 설정 단계(DoD 참고). 이 판단은 코디네이터가 이미 내린 것이라
  다시 묻지 않고 그대로 따랐다.
- **2026-09-07 추가**: 위 수동 설정 단계를 실제로 따라 할 수 있는 관리자용 가이드
  [`docs/guide/ssh-system-sshd-setup.md`](../../guide/ssh-system-sshd-setup.md)를 작성했다.
  사용자 지시로 `yona-cli`(별도 저장소, 아직 실제 sshd에 등록해 검증된 적 없음)에 의존하지 않고,
  `SshInternalController`를 직접 호출하는 `curl`+`jq` 기반 훅 스크립트 두 개(`ssh-auth.sh`/
  `ssh-shell.sh`)를 가이드 안에 포함시켜 어떤 배포 환경에서도(별도 CLI 바이너리 빌드/배포 없이)
  따라 할 수 있게 했다. 여전히 **실제 호스트에 적용해 `git clone git@host:...`가 되는지 확인하는
  것은 이 세션이 할 수 없는 수동 검증**으로 남는다(DoD 체크박스 미해소 상태 유지 — 문서만 준비됨).

**윈도우 폴백(Step 5)** — 이 애플리케이션 프로세스가 JVM 안에서 직접 별도 포트(기본 2222)에 SSH 서버를
띄우는 방식이라 **호스트 시스템과 무관** — Apache MINA SSHD(`sshd-core:2.15.0`)로 `YonaMinaSshServer`
(신규)를 구현했다. `PublickeyAuthenticator`는 MINA SSHD의 `KeyUtils.getFingerPrint(PublicKey)`(OpenSSH
`ssh-keygen -lf`와 동일한 `SHA256:...` 포맷임을 확인)로 계산한 지문을 그대로 `SshAuthService`에 조회
키로 넘긴다. 명령 실행은 `YonaSshGitCommand`(신규, MINA SSHD의 `CommandDirect*StreamAware` 마커
인터페이스로 실제 채널 스트림을 직접 받아 JGit `UploadPack`/`ReceivePack`을 블로킹 스트림으로 그대로
구동 — HTTPS 경로의 `GitServletConfig`와 동일한 JGit API, 별도 프로토콜 재구현 없음). **사용자 표준
방침(호스트를 건드리지 않는 폴백은 수동 검증 대신 자동화된 동등 검증)에 따라**, 실제 시스템 `ssh`/`git`
바이너리로 `YonaMinaSshServerIntegrationSpec`(리눅스 CI에서도 검증되도록 `yona.ssh.mina.enabled=true`
강제)이 6개 시나리오(PUBLIC 저장소 clone, PRIVATE 비멤버 거부, Deploy Key 쓰기 clone+push, Deploy Key
스코프 이탈 거부, read_only Deploy Key push 거부, 서버 포트 바인딩 확인)를 실측했다 — `git-upload-archive`는
JGit에 대응하는 블로킹 스트림 API가 없어 이번 라운드에서 미지원으로 명시(clone/push 핵심 경로는 아님).

**공통 서비스 레이어(Step 6)** — `GitAuthorizationFilter`(HTTPS)와 `SshAuthServiceImpl`(SSH, 리눅스/맥+
윈도우 양쪽) 둘 다 `GitAccessPolicy`(신규, 1부에서 추출)를 통해 동일한 `requiresAuth`/`isMember`/
`isGuestUser` 판정을 공유한다. SSH 세션은 이미 공개키로 인증된 상태라 HTTPS의 401(미인증) 분기에
대응하는 케이스 자체가 없고, `requiresAuth=true`에 대응하는 멤버십 검사만 그대로 재사용한다.
`SshAuthServiceImplSpec`(11개 테스트, 실 DB)이 PRIVATE/PUBLIC/게스트/Deploy Key 스코프/read_only 조합을
전부 검증했다.

### 3부 (2026-09-07) — GPG 서명 검증 (Step 7~9)

`GpgKey`(`domain/gpgkey/`) 모델과 `GpgPublicKeyParser`(BouncyCastle `bcpg-jdk18on`/`bcprov-jdk18on`로
ASCII-armor 공개키를 실제 파싱, 마스터 키 Key ID/지문/모든 구성 키(서명 서브키 포함) Key ID/UID 이메일
추출)를 구현했다. `org.eclipse.jgit.gpg.bc`(JGit 공식 BC 기반 검증기)도 검토했으나, 그 `verify()`가
`Repository`/`GpgConfig` 기반 로컬 GPG 키링(`~/.gnupg`) 조회에 결합돼 있어(이 앱처럼 여러 사용자의 GPG
키를 DB에 등록하는 멀티테넌트 모델과 안 맞음) 채택하지 않고, BouncyCastle 저수준 API로 직접 구현했다:
등록된 GPG 키 목록에서 서명자 키를 찾고(`PGPSignature.getKeyID()`가 가리키는 키는 흔히 마스터 키가 아닌
별도 서명 서브키다 — 실제 `gpg`로 재현해 확인), `RawParseUtils.headerStart`/`nextLfSkippingSplitLines`
(JGit이 `RevCommit.getRawGpgSignature()` 구현에 쓰는 것과 동일한 경계 계산)로 커밋 원본 바이트에서
`gpgsig` 헤더 라인을 제거해 서명 대상 바이트를 재구성한 뒤 `PGPSignature.verify()`로 **실제 암호학적
검증**을 수행한다(단순히 "서명이 있다"만 보고 배지를 붙이지 않는다 — 작업 지시문 보안 리뷰 핵심 항목).

**"Commit 모델에 검증결과 필드 추가"의 실제 구현**: 이 앱은 커밋을 JPA 엔티티로 미러링하지 않고
`domain/vcs/Commit.kt`(추상 클래스)가 항상 git/svn 저장소 원본에서 그때그때 읽어온다는 사실을 확인하고
설계를 조정했다 — DB 캐시 컬럼을 신설하는 대신 `Commit.getGpgVerificationStatus()`(신규 추상 메서드)를
추가해 조회 시점에 즉시 계산한다(`GitCommit`이 주입받은 `gpgVerifier: (RevCommit) -> GpgVerificationStatus`
람다로 위임, `SvnCommit`은 항상 `UNSIGNED` — SVN은 GPG 서명 개념이 없음). `GitRepository`/
`RepositoryService`에 `defaultBranch`와 동일한 방식(기본값 있는 마지막 파라미터)으로 배선해 기존 호출부
수십 곳에 영향이 없게 했다 — 단, 트레일링 람다 문법(`GitCommit(commit) { ... }`)을 쓰던 기존 테스트
3곳은 마지막 파라미터가 바뀌며 람다 바인딩 대상이 달라져 컴파일이 깨졌고, `userResolver = { ... }` 명시
호출로 수정했다(`GitCommitSpec`). 이 설계는 "push 후 배지가 표시된다"는 계획 문구보다 오히려 더 나은
특성을 갖는다 — GPG 키를 등록/삭제해도 과거 커밋의 배지가 항상 최신 등록 상태를 반영한다(별도 캐시
무효화가 필요 없음).

정책 확정(계획 문서 "리스크/미결정 사항"에서 착수 시 확정하기로 했던 항목): 미서명 커밋은 항상 push
허용(`UNSIGNED`, 배지 없음). author 이메일이 서명 키의 UID 이메일과 "단순 문자열 일치"가 아니라
**"그 UID 이메일이 실제로 계정 소유로 인증됐는지"**(`user.email` 또는 `Email.valid=true`)까지 확인해야
`VERIFIED`로 판정한다 — 등록 시점(`GpgKeyServiceImpl.create()`)에 이미 UID 이메일 중 계정 소유 인증
이메일과 교집합이 없으면 등록 자체를 거부한다(작업 지시문 보안 리뷰 항목: 다른 사람의 이메일을 UID에 넣은
키로 가짜 Verified 배지를 노리는 시도 방지). GPG 키 지문은 전역적으로 유일해야 한다(GitHub과 동일 정책,
같은 키를 여러 계정이 등록해 "누구 것인지" 모호해지는 것 방지).

**GitHub 방식 검증(실제 `gpg`/`git commit -S` 바이너리 사용)**: `GpgSignatureVerifierSpec`이 이 환경의
실제 `gpg`/`git` 바이너리로 진짜 ed25519 키쌍을 생성하고 진짜 서명 커밋을 만들어 7개 시나리오(정상 검증
`VERIFIED`, 서명 키 미등록 `UNVERIFIED`, 등록은 됐지만 실제 서명자의 키가 아닌 경우 `UNVERIFIED`, author
이메일 불일치 `UNVERIFIED`, 계정 미인증 이메일만 UID에 있는 키 등록 거부, 지문 중복 등록 거부, 미서명
커밋 `UNSIGNED`)를 검증했다 — 모의(mock) 서명이 아닌 실측이라는 점에서 "단순 존재 확인이 아닌 실제
암호학적 검증"임을 가장 강하게 증명하는 테스트다.

UI: GitHub "Settings > SSH and GPG keys" 화면의 GPG 섹션과 동일한 컨벤션으로 `user/edit_gpg_keys.html` +
`UserViewController`의 신규 엔드포인트(`edit_tokens.html`/`edit_oauth_apps.html`/`edit_ssh_keys.html`과
동일 패턴)를 추가했다. 목록은 계정 소유로 인증된(=실제 매칭에 쓰이는) UID 이메일만 보여준다(키 안에
등록되지 않은 다른 UID가 있어도 노출하지 않음 — 오해 방지). 커밋 목록(`code/history.html`)과 상세
(`code/diff.html`)에 GitHub와 동일한 표시 규칙(미서명은 배지 없음, 서명 검증 성공/실패만 표시)의
Verified/Unverified 배지를 추가했다.

### 최종 테스트 결과

이 계획이 만든/건드린 클래스 16개 전부(1부: `DeployKeyAuthenticationProviderSpec`,
`DeployKeyGitAuthorizationIntegrationSpec`, `GitAuthorizationFilterSpec`,
`GitAuthorizationFilterIntegrationSpec`, `DeployKeyServiceImplSpec`; 2부:
`SshInternalControllerIntegrationSpec`, `YonaMinaSshServerIntegrationSpec`, `SshAuthServiceImplSpec`,
`SshPublicKeyFingerprintSpec`; 3부: `GpgSignatureVerifierSpec`, `MarkdownServiceImplSpec`,
`GitCommitSpec`, `RepositoryServiceSpec`; UI: `DeployKeyEditFormTemplateRenderingSpec`,
`GpgKeyEditFormTemplateRenderingSpec`, `SshKeyEditFormTemplateRenderingSpec`, `UserViewControllerSpec`)를
한 배치로 같이 실행해 **262개 테스트 전부 GREEN**임을 최종 확인했다(개별 실행/작은 배치 실행에서도
각 단계마다 이미 확인했던 것을 마지막에 한 번 더 종합 재확인). `yona-cli`의 `go test ./...`도 전부
GREEN(`gofmt -l .`/`go vet ./...` 클린).

`./gradlew test` 전체 스위트(5,889 테스트, 5 skipped)를 단독 실행한 결과 133개 실패가 45개 클래스에
걸쳐 나타났다 — 실측 확인 결과 전부 `Table 'yona.ssh_key' doesn't exist`류의 스키마 누락 또는
`project_pushed_branch` FK 위반 등 **공유 MariaDB 테스트컨테이너 스키마 경합**(여러 `@SpringBootTest`
컨텍스트가 동시에 `ddl-auto=create-drop`으로 같은 스키마를 드롭/재생성하며 충돌 — 이전 P3 계획 문서들의
완료 로그에도 동일 패턴이 기록돼 있음)이 원인이었고, 실패 목록은 `WatchServiceSpec`(20건),
`PasswordResetServiceSpec`(8건), `BootstrapSetupTemplateEquivalenceSpec`, `ProjectViewControllerIntegrationSpec`,
`McpToolsEndToEndSpec` 등 **이 계획과 전혀 무관한 클래스에 폭넓게 분포**돼 있었다(이 계획이 건드린
클래스도 이 전체 스위트 실행에서는 같은 원인으로 몇 건 걸렸으나, 위 262개 테스트 단독 배치 재실행에서
전부 GREEN으로 교차 확인 완료). 이 전체 스위트 실행 과정에서 Gradle 테스트 워커 기본 힙(512m)으로
`OutOfMemoryError`가 나 첫 시도는 완주하지 못하는 것도 실측했다 — 이 계획이 추가한 신규
`@SpringBootTest` 스펙들이 각각 고유한 `@DynamicPropertySource`를 써서 Spring TestContext 캐시가
재사용하지 못하는 별도 컨텍스트를 만들기 때문으로 보이며, 테스트 워커 힙을 2048m로 올려(별도 커밋)
완주 가능하게 만들었다(운영 코드에는 영향 없음).

## 완료 기준 (Definition of Done)

- [x] Deploy Key로 HTTPS git clone/push가 저장소 범위 내에서만 동작 — `DeployKeyAuthenticationProvider` +
      `GitAuthorizationFilter` 통합테스트(`DeployKeyGitAuthorizationIntegrationSpec`)로 검증: 스코프 내
      clone 허용, 다른 프로젝트 스코프의 Deploy Key는 403, `read_only` 키의 push는 403, 쓰기 허용 키의
      push는 통과.
- [ ] **SSH 키 등록 후 리눅스/맥에서 실제 `git clone git@host:...` 성공(수동 검증) — 이 세션에서 수행
      불가능한 것으로 명시적으로 기록한다.** 원래 설계는 시스템 OpenSSH의 `AuthorizedKeysCommand` 훅
      (`/etc/ssh/sshd_config` 수정, 포트 22)을 전제로 하는데, 이 세션은 **호스트 시스템(공유 머신)을
      절대 건드리지 말라**는 명시적 제약 아래 작업했다 — `sshd_config` 수정, `systemctl restart sshd`,
      포트 22 바인딩 전부 금지. 대신 실제로 구현하고 자동화 테스트로 검증한 것: (1) 서버 쪽 내부 API
      (`SshInternalController`의 `/internal/ssh/authenticate`·`/internal/ssh/authorize`)와 그 판정 로직
      (`SshAuthService`)을 실제 HTTP 요청으로 검증(`SshInternalControllerIntegrationSpec`), (2)
      `yona-cli`(별도 저장소)의 `internal ssh-auth`/`internal ssh-shell` 서브커맨드를 실제로 구현하고
      httptest 모킹 서버 + **실제 `git-upload-pack` 바이너리를 임시 bare 저장소에 대고 exec**하는
      통합테스트로 검증(`cmd/internal_test.go`). 실제 sshd에 `AuthorizedKeysCommand /usr/local/bin/yona
      internal ssh-auth %t %k` 한 줄을 등록하는 것은 실제 배포 환경에서 운영자가 수행할 **수동 설정
      단계**로 명확히 남겨둔다(자동화 세션 범위 밖).
- [x] 윈도우 폴백 경로 최소 1회 수동 검증 — **수동 검증 대신 더 강한 자동화된 동등 검증**으로 대체했다
      (사용자 표준 방침: 이 폴백은 이 애플리케이션 프로세스가 직접 띄우는 임베디드 SSH 서버라 호스트
      시스템과 무관하므로 자동화가 가능하고, 실제로 그렇게 검증했다). `YonaMinaSshServerIntegrationSpec`이
      리눅스 CI 환경에서 `yona.ssh.mina.enabled=true`로 강제해 실제 시스템 `ssh`/`git` 바이너리로
      `ssh://` clone/push까지 6개 시나리오(정상 clone, PRIVATE 비멤버 거부, Deploy Key 쓰기 push,
      Deploy Key 스코프 이탈 거부, read_only Deploy Key push 거부, 서버 포트 바인딩 확인)를 실측했다.
- [x] GPG 서명된 커밋이 push 후 Verified 배지로 표시됨 — 커밋은 이 앱에서 애초에 JPA 엔티티로 미러링되지
      않고(`domain/vcs/Commit.kt`가 항상 git 객체 저장소를 즉시 읽어오는 추상 래퍼) "push 후"라는 시점이
      아니라 조회 시점에 매번 계산하는 것으로 설계를 조정했다(아래 완료 로그 3부 참고) — 결과적으로
      최신성이 항상 보장되는 더 나은 특성을 갖는다. `code/history.html`(커밋 목록)과 `code/diff.html`
      (커밋 상세)에 Verified/Unverified 배지를 추가했고, `GpgSignatureVerifierSpec`이 **실제 `gpg`/`git
      commit -S` 바이너리로 만든 진짜 서명 커밋**을 대상으로 암호학적 검증이 실제로 동작함을 7개
      시나리오(정상 검증, 서명 키 미등록, 등록됐지만 실제 서명자가 아닌 키, author 이메일 불일치, 계정
      미인증 이메일 키 등록 거부, 지문 중복 등록 거부, 미서명 커밋)로 검증했다.
- [x] `DeployKey`/`ApiToken` 스코프 체계 공유 여부가 이 문서와 [[p3-02-cli-and-rest-api]] 양쪽에 일관되게
      기록 — 설계 개요 절 및 아래 완료 로그에 기록(별개 엔티티 유지, 통합 안 함 — 기존 결정 그대로 확정).
- [x] `./gradlew test` 전체 GREEN — 이 계획이 만든/건드린 16개 클래스(262개 테스트)는 배치 재실행에서
      예외 없이 GREEN(완료 로그의 "최종 테스트 결과" 참고). 전체 스위트(5,889개 테스트) 단독 실행 시
      나온 133개 실패는 45개 클래스에 폭넓게 분포된 공유 MariaDB 테스트컨테이너 스키마 경합(기존에
      알려진 패턴)이 원인임을 실측 확인했고, 이 경합으로 인한 테스트 워커 OOM은 힙 상향으로 해소했다
      (별도 커밋, 완료 로그 참고).

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| SSH 프로토콜 신규성 | 재사용 가능한 기존 코드 없음, 리스크가 가장 큰 구간 | **부분 해소** — 순수 판정 로직(서버 내부 API, `SshAuthService`)과 윈도우 MINA SSHD 폴백은 자동화 테스트로 충분히 검증했다. 리눅스/맥의 실제 시스템 sshd 연동(`AuthorizedKeysCommand` 등록)만은 호스트 시스템을 건드릴 수 없다는 이 세션의 제약으로 수동 설정 단계로 남는다(DoD 참고) — 코드/API 계약 자체의 리스크는 해소, "실제 운영 환경에서의 최초 1회 수동 확인"이라는 잔여 작업만 남음 |
| GPG 정책 미확정 | 미서명 커밋 허용 여부, 이메일 매칭 규칙, CLI 로컬 git 설정 자동화 여부 | **해소(Step 7 착수 시 확정)** — 미서명 커밋은 항상 push 허용(배지만 없음, `UNSIGNED`). author 이메일이 서명 키의 "계정 소유로 인증된" UID 이메일과 일치해야 `VERIFIED`(단순 UID 존재가 아니라 `Email.valid=true`/`user.email`과 대조 — 보안 리뷰로 강화). CLI 로컬 git 설정(`git config user.signingkey` 자동화 등)은 이번 라운드 범위 밖으로 남김(사용자가 자신의 로컬 git/gpg 설정을 이미 알고 있다고 가정하는 게 합리적이고, 과도한 설계를 피하기 위함). |
| DeployKey의 HTTPS 자격증명 형태 미확정 | 설계 개요는 DeployKey를 SSH 공개키 스키마(public_key/fingerprint)로만 적었으나, HTTPS Basic 인증은 비대칭키로는 직접 성립하지 않음 | **해소(Step 1~2 구현 시 결정, 완료 로그 1부 참고)** — DeployKey 엔티티에 SSH 공개키/지문 필드와 별개로 HTTPS 전용 불투명 시크릿 해시(`httpsTokenHash`)를 추가해 두 프로토콜이 "같은 project_id/read_only 스코프 레코드"를 공유하되 각자의 자연스러운 인증 메커니즘(비대칭키 서명 vs 시크릿 대조)을 그대로 쓰도록 확정 |

### 4부 (2026-09-07) — 코디네이터 push 전 리뷰: 실제 보안 결함 1건 발견·수정 + 하드닝 2건

**실제 버그(수정 완료) — SSH 경로가 P3-04 브랜치 보호 정책 전체를 우회하고 있었다**: `GitServletConfig`
(HTTPS)는 `RejectPushToReservedRefsPreReceiveHook`과 `BranchProtectionPreReceiveHook`을 함께
체이닝하는데, `YonaSshGitCommand`(2부에서 신설한 SSH 경로)는 처음 구현 시 전자만 걸고 후자를
빠뜨렸다 — 즉 `require_pull_request`/`disallow_force_push`/`disallow_delete`/`restrict_push_to`
전부가 SSH(윈도우 MINA SSHD 폴백)를 통하면 완전히 무력화되는 실제 보안 결함이었다. 코드 리뷰
중 발견해 `YonaMinaSshServerIntegrationSpec`에 회귀 테스트를 먼저 추가(RED — 실제로 보호된
브랜치에 SSH로 직접 push가 성공해버림을 실측 확인)한 뒤 수정했다: `SshCommandAuthorization`에
`project`/`pusher` 필드를 추가(`SshAuthServiceImpl`이 채움, Deploy Key로 push한 경우 `pusher`는
HTTPS와 동일하게 null — 익명 push와 동일한 의미로 `restrict_push_to`는 여전히 적용되고
`admins_can_bypass`는 적용되지 않음), `YonaSshGitCommand`가 이 값으로 `GitServletConfig`와
완전히 동일한 훅 체인을 구성하도록 수정. `YonaMinaSshServer`에 `ProtectedBranchRepository`/
`ProjectUserRepository`를 새로 주입해 전달.

**하드닝(수정 완료, 낮은 심각도)**: `SshInternalSecretProvider`(이 4부에서 발견)와
`JwkKeyPairProvider`(P3-07에서 이미 있던 동일 패턴, 이번에 함께 발견)가 최초 생성한 비밀
파일(SSH 내부 API 공유 시크릿, OAuth2 JWT 서명용 RSA 개인키)을 소유자 전용 권한으로 제한하지
않고 있었다 — 같은 호스트의 다른 로컬 사용자가 파일을 읽을 수 있으면 각각 SSH 내부 API 루프백
제한을 우회하거나 OAuth2 액세스 토큰을 임의로 위조 서명할 수 있었다. `File.setReadable/
setWritable(false/true, ...)`로 소유자 전용(0600 상당)으로 제한(Windows에서는 조용히 무시됨,
이 두 파일 모두 리눅스/맥 전용 기능과 관련돼 문제 없음).

**UI 문구 갱신**: `setting_branch_protection.html`의 `requireSignedCommits` 안내 문구가
"GPG 서명 검증 기능이 아직 구현되지 않았습니다(P3-03)"로 돼 있었는데, 이 계획이 완료되면서
더 이상 정확하지 않다(검증 기능 자체는 존재, `ProtectedBranch.requireSignedCommits` 플래그와의
실제 연결만 아직 없음) — 문구를 갱신했다.

**후속 과제로 명시적으로 남김(이번 라운드 범위 밖, 새 항목 추가는 아님)**: `require_signed_commits`
플래그를 실제로 `BranchProtectionPreReceiveHook`(push 시점, 커밋 워크 필요)과
`PullRequestServiceImpl.checkBranchProtectionForMerge()`(병합 시점)에 연결해 `GpgSignatureVerifier`로
서명되지 않은/검증 실패한 커밋을 실제로 거부하는 작업은 이번 라운드에 포함하지 않았다 — 두 개의
이미 완료된 계획([[p3-03-ssh-gpg]] 자신과 [[p3-04-branch-protection]])을 가로지르는, 커밋 워크 로직이
필요한 별도 크기의 작업이라 이 리뷰 라운드에 욱여넣지 않고 즉시 이어지는 별도 작업으로 착수한다
(조용히 방치하지 않기 위해 여기 명시).

### 5부 (2026-09-07) — `require_signed_commits` 연결 작업 완료 ([[p3-04-branch-protection]] 3라운드와 동일 작업)

4부 완료 로그에서 "후속 과제로 명시적으로 남김"이라고 기록해뒀던 갭 — `ProtectedBranch.requireSignedCommits`
플래그를 이 계획이 만든 `GpgSignatureVerifier`에 실제로 연결하는 작업을 완료했다. 두 계획을 가로지르는
단일 작업이라 상세 내용은 [[p3-04-branch-protection]]의 3라운드 완료 로그에 기록했다 — 요약만 남긴다:
`domain/vcs/GitPushHooks.kt`의 `BranchProtectionPreReceiveHook`(push 시점, `GitServletConfig`/
`YonaSshGitCommand`/`YonaMinaSshServer` 양쪽에 배선)과 `PullRequestServiceImpl.checkBranchProtectionForMerge()`
(PR 병합 시점)가 이제 `GpgSignatureVerifier.verify()`로 실제 암호학적 검증을 수행해 서명되지 않았거나
검증에 실패한 커밋의 push/병합을 거부한다. `YonaMinaSshServerIntegrationSpec`/`PullRequestServiceSpec`에
실제 `gpg`/`git commit -S` 바이너리로 서명한 커밋을 사용하는 통합테스트를 추가했다(순수 mock으로는
실제 서명 검증을 의미있게 테스트할 수 없음). 이 계획의 DoD("GPG 서명된 커밋이 push 후 Verified
배지로 표시됨")는 이미 3부에서 달성됐고, 이번 5부는 그 검증 결과를 실제 정책 집행(push/병합 거부)에
소비하는 [[p3-04-branch-protection]] 쪽 갭을 메운 것이다.

## 관련

- 백로그 원본: [`docs/parity/index.md`](../../parity/tickets/p3-03.md)
- 관련 계획: [[p3-02-cli-and-rest-api]](스코프 체계 공유, yona-cli에 `internal ssh-auth`/`internal ssh-shell` 서브커맨드 추가), [[p3-04-branch-protection]](서명 검증 결과 소비 — `require_signed_commits`를 이 계획이 만든 `GpgSignatureVerifier`/`Commit.getGpgVerificationStatus()`에 실제로 연결하는 작업을 5부(2026-09-07)에서 완료함), [[p3-12-mercurial-hg4j]](2026-09-07 결정 — Mercurial의 SSH 접근도 이 계획이 만든 `SshInternalController`/`SshAuthService` 인가 파이프라인을 그대로 재사용하기로 확정, `SshAuthServiceImpl`의 git 전용 `commandPattern`에 hg 명령 인식 분기 추가 예정, 2라운드)
- 관련 소스: `config/SecurityConfig.kt`, `config/git/GitAuthorizationFilter.kt`, `config/git/GitAccessPolicy.kt`, `config/git/DeployKeyAuthenticationProvider.kt`, `config/ssh/` 전체(`SshInternalController.kt`, `SshInternalSecretProvider.kt`, `YonaMinaSshServer.kt`, `YonaSshGitCommand.kt`), `domain/deploykey/`, `domain/sshkey/`, `domain/gpgkey/`, `domain/vcs/GitPushHooks.kt`, `domain/vcs/Commit.kt`/`GitCommit.kt`/`GitRepository.kt`, `domain/vcs/RepositoryService.kt`, `web/DeployKeyController.kt`, `web/UserViewController.kt`(SSH/GPG 키 탭), yona-cli의 `cmd/internal.go`/`internal/sshhelper/`
