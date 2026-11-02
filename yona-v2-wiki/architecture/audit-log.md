---
title: 감사 로그 (X-9)
---

# 감사 로그

결정은 [decisions.md](../decisions.md) D-62이다. 엔터프라이즈 수준(GitHub Enterprise 감사 로그를 기준)으로 만든다. v1.6에 없는 추가 항목이다(D-28).

## 단계

A1 결정과 설계(이 문서), A2 핵심(기록기, 해시 사슬), A3 경계 이벤트(인증, 인가 거부), A4 계정과 토큰, A5 도메인 변경과 사이트 관리, A6 조회와 내보내기, A7 보존, A8 구조 시험과 마무리.

## 운영 주의: 리버스 프록시 뒤의 IP

감사 기록의 IP(`ip`)는 `request.remoteAddr`이다. **리버스 프록시(nginx, 로드 밸런서 등) 뒤에서는 이 값이 모든 요청에서 프록시의 주소가 되므로**, 올바른 접속 IP를 남기려면 서버가 `X-Forwarded-For`를 해석하도록 설정해야 한다.

```yaml
server:
  forward-headers-strategy: native   # Tomcat의 RemoteIpValve가 remoteAddr를 원래 클라이언트로 바꾼다
  tomcat:
    remoteip:
      internal-proxies: 10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+   # 믿을 프록시의 주소(정규식)
```

- `native`는 Tomcat이, `framework`는 스프링이 처리한다. 둘 다 `none`(기본)이 아니어야 한다. 설정 키는 Spring Boot의 `server.forward-headers-strategy`이다.
- **믿을 프록시만 등록한다.** `X-Forwarded-For`는 누구나 위조해 보낼 수 있으므로 프록시가 클라이언트가 보낸 값을 지우고 직접 넣게 하고(nginx의 `proxy_set_header X-Forwarded-For $remote_addr` 또는 `$proxy_add_x_forwarded_for`와 `real_ip` 설정), 서버는 그 프록시의 주소에서 온 요청의 헤더만 믿어야 한다. 믿을 범위를 넓게 열면 공격자가 IP를 위조해 감사 기록을 속일 수 있다.
- 같은 `remoteAddr`를 **로그인 실패 제한**(`TokenFailureLimiter`, 5회 실패하면 15분 차단)도 쓴다. 프록시 뒤에서 설정하지 않으면 모든 사용자가 한 주소로 보여 한 사람의 실패가 모두를 막을 수 있다. 감사 로그와 같은 이유로 이 설정이 필요하다.
- 확인 방법: 프록시를 거쳐 로그인한 뒤 사이트 관리자의 감사 로그 화면에서 `auth.login_success`의 IP가 클라이언트 주소인지 본다.

## 설계 개요

- **테이블**: `audit_log`(기록, 추가 전용)와 `audit_chain_head`(사슬의 끝, 한 행). 기록 한 건은 시각(UTC), 행위자 유형(사용자, 토큰, 시스템, 익명)과 번호와 로그인, 조치(`범주.조치`), 결과(성공, 실패, 거부), 대상 유형과 번호와 이름, 소속 조직과 프로젝트, IP, 사용자 에이전트, 요청 번호, 토큰 번호와 범위, 상세(JSON 글), 이전 해시와 해시를 담는다.
- **해시 사슬**: `hash = SHA-256(prev_hash + 정규화한 필드)`. 기록할 때 `audit_chain_head` 행을 비관적 잠금(`PESSIMISTIC_WRITE`)으로 잡아 순서를 직렬화하고 커밋까지 잠금을 쥔다. 그래서 사슬 순서는 커밋 순서이다.
- **기록기**: `AuditRecorder`가 행위자, IP, 사용자 에이전트, 요청 번호, 토큰을 요청 문맥에서 채운다. 비밀 값(토큰, 비밀번호, 웹훅 비밀값)은 상세에 넣지 않는다.
- **트랜잭션**: 상태 변경은 호출한 트랜잭션에 참여한다(fail-closed). 경계 이벤트는 별도 트랜잭션이고 실패해도 요청을 막지 않는다.

## A2 핵심 (구현)

- 코드: `domain/audit`의 `AuditLog`(엔티티), `AuditChainHead`(사슬의 끝), `AuditHash`, `AuditDetails`(비밀 값 가리기, 길이 제한), `AuditEvent`와 `AuditActor`, `AuditRecorder`, `AuditVerifier`, `AuditFileSink`. 설정은 `yona.audit-retention-days`(기본 365)와 `yona.audit-file-enabled`(기본 켜짐).
- `YonaAuthentication`에 `tokenId`를 더해 어느 토큰으로 한 일인지 남긴다.
- 번호는 DB 자동 증가가 아니라 사슬의 끝(`audit_chain_head`)을 비관적 잠금으로 잡고 `last_id + 1`로 매긴다. 잠금이 커밋까지 이어져 사슬 순서가 커밋 순서이다. 시각은 DB 정밀도 차이가 해시를 깨지 않게 에폭 밀리초(`created_at`)이다.
- 비밀 값 가리기: 키 이름이 비밀번호, secret, credential, authorization, signature, API 키, `token`(그 자체나 `...Token`)인 값을 `[redacted]`로 바꾼다. `tokenId`나 접두사는 그대로 둔다.
- 시험: `AuditRecorderTest` 20건.

## A3 경계 이벤트 (구현)

모두 별도 트랜잭션(`recordIndependent`)이다. 이벤트를 만드는 곳은 `AuditBoundary` 한 곳이고, 비밀 값(비밀번호, 토큰 값)은 어떤 필드에도 넣지 않는다.

| 조치 | 결과 | 언제 | 연결 지점 |
|---|---|---|---|
| `auth.login_success` | 성공 | 비밀번호나 소셜 로그인 성공(`details.method`) | `LoginController`, `SocialLoginController` |
| `auth.login_failure` | 실패 또는 거부 | 틀린 비밀번호와 없는 사용자(같은 이유 `invalid_credentials`), 잠김(`locked_after_authentication`), 삭제됨(`deleted`) | `LoginController`(폼과 AJAX) |
| `auth.logout` | 성공 | 로그인한 사용자의 로그아웃 | `LoginController` |
| `auth.token_failure` | 실패 | 외부 API의 틀린 토큰 | `YonaAuthenticationFilter` |
| `auth.basic_failure` | 실패 | git HTTP의 틀린 비밀번호나 토큰(`details.channel`, `reason`) | `BasicAuthResolver` |
| `two_factor.enabled`, `two_factor.disabled`, `two_factor.method_removed`, `two_factor.backup_code_used`, `two_factor.backup_codes_regenerated`, `site.two_factor_enforcement_changed` | 성공 | 2단계 인증의 켜기, 끄기(`by`: user, admin, emergency), 수단 삭제, 백업 코드, 강제 방식 변경(X-2, D-69). 비밀 값과 코드는 남기지 않는다. 로그인 실패 이유에 `two_factor_invalid`, `two_factor_locked`가 더해진다 | `TwoFactorService`, `SiteSettingService`, `TwoFactorLogin` |
| `oauth.client_created`, `oauth.client_updated`, `oauth.client_secret_regenerated`, `oauth.client_deleted` | 성공 | OAuth 앱을 만들고 고치고 비밀 값을 다시 만들고 지운다(`details.by`: owner, admin). 비밀 값은 남기지 않는다(X-1, D-71) | `OAuthClientService` |
| `oauth.authorized`, `oauth.token_issued`, `oauth.authorization_revoked` | 성공 | 사용자가 앱을 승인하고(`details.scopes`), 토큰을 발급하고(`details.grant`: authorization_code, refresh_token), 승인을 취소한다. 토큰 값은 남기지 않는다 | `YonaOAuth2AuthorizationConsentService`, `YonaOAuth2AuthorizationService`, `OAuthAuthorizedAppsService` |
| `oauth.refresh_reuse_detected` | 거부 | 이미 쓴 갱신 토큰이 다시 와서 그 인가 전체를 취소했다(탈취 의심) | `YonaOAuth2AuthorizationService` |
| `site.oauth_cimd_changed`, `site.oauth_user_apps_changed` | 성공 | 관리자가 CIMD 클라이언트 허용과 사용자의 앱 만들기 허용을 바꿨다(`details.from`, `to`) | `SiteSettingService` |
| `auth.token_blocked` | 거부 | 인증 실패가 쌓여 주소가 막힘(같은 주소는 1분에 한 번만) | 인증 필터, `BasicAuthResolver` |
| `access.denied` | 거부 | 응답이 403인 모든 요청(대상은 `메서드 경로`, 질의 문자열 제외) | `AccessDeniedAuditFilter`(인증 필터 뒤) |
| `access.api_scope_denied` | 거부 | 토큰 범위가 모자란 외부 API 요청. `access.denied`로 또 남기지 않는다 | `ApiScopeInterceptor` |

- **교착 방지**: 같은 요청의 상태 변경 기록(`record`)이 사슬의 끝을 이미 잠갔으면 경계 이벤트가 별도 트랜잭션으로 바로 쓰다 서로 기다린다. 그때는 바깥 트랜잭션이 끝난 뒤(`afterCompletion`)에 새 트랜잭션으로 남긴다. 바깥이 롤백해도 남는다.
- 시험: `AuditBoundaryTest` 14건, `AuditRecorderTest` 22건.

## A4 계정과 토큰 (구현)

변경과 같은 트랜잭션에서 `record`로 남긴다(fail-closed). 거부된 요청과 바뀐 것이 없는 저장은 남기지 않는다. 비밀 값(새 비밀번호, 해시, 토큰 값, 재설정 링크 값, 기기 코드)은 어떤 필드에도 없다.

| 조치 | 언제 | 상세 |
|---|---|---|
| `account.signup` | 가입 | 상태(`state`) |
| `account.password_changed` | 설정에서 변경, 메일 링크로 재설정, 관리자 재설정 | `via`: `settings`, `reset_token`, `admin_reset` |
| `account.password_reset_requested` | 비밀번호 분실 요청(맞으면 성공, 틀린 조합은 실패 `invalid_request`) | 시도한 로그인 ID만, 이메일은 남기지 않음 |
| `account.profile_changed` | 이름이나 이메일이 바뀜 | 바뀐 항목의 전후(`name`, `email`) |
| `account.email_added`, `email_confirmed`, `email_deleted`, `email_main_changed` | 보조 이메일 관리 | 이메일(대상 이름), 대표 변경은 전후 |
| `token.created`, `token.updated`, `token.regenerated`, `token.deleted` | API 토큰 | 이름, 범위, 만료, 토큰 주인(`owner`). 기기 인증이 만든 토큰은 인증 없이 CLI가 받아 가므로 행위자를 토큰 주인(승인한 사용자)으로 남기고 `via: device`를 둔다 |
| `gpg_key.added`, `gpg_key.deleted` | GPG 키 등록과 삭제(X-4, D-65). 같은 트랜잭션에서 기록하며 거절된 등록과 남의 키 삭제는 남기지 않는다 | 이름(대상), 지문, 키 ID, 하위 키 수(등록은 이메일 수도). **공개키 본문은 남기지 않는다** |
| `ssh_key.added`, `ssh_key.deleted` | SSH 키 등록과 삭제(X-3, D-66). 같은 트랜잭션에서 기록하며 거절된 등록과 남의 키 삭제는 남기지 않는다 | 이름(대상), 지문, 종류. **공개키 본문은 남기지 않는다** |
| `deploy_key.added`, `deploy_key.deleted` | 배포 키 등록과 삭제(X-3, D-66). 프로젝트를 가리켜 프로젝트의 감사 화면에 보인다 | 이름(대상), 지문, 종류, 읽기 전용 여부, `소유자/이름`. 공개키 본문은 남기지 않는다 |
| `token.device_approved`, `token.device_denied` | 기기 인증 승인과 거절 | 기기 이름(대상), 범위 |

시험: `AuditAccountTest` 14건.

## A5 도메인 변경과 사이트 관리 (구현)

서비스에서 변경과 같은 트랜잭션으로 `record`한다(fail-closed). 바뀐 것이 없거나 거부된 요청은 남기지 않고, 설정 변경은 바뀐 항목만 `{from, to}`로 남긴다. 프로젝트 이벤트는 프로젝트 번호와 소속 조직 번호를, 조직 이벤트는 조직 번호를 담아 조직 소유자의 조회 범위를 정한다.

| 조치 | 연결 지점 | 상세 |
|---|---|---|
| `project.created` | `ProjectCreateService`, `ExternalProjectService`(`via: api`), `ProjectImportService`(`via: import`, 가져온 주소는 질의와 계정 정보를 뗀 값) | 범위, VCS, 메뉴 |
| `project.forked` | `ForkService` | 원본 |
| `project.deleted` | `ProjectDeletionService`(지우기 전에 이름을 담아 남김) | 범위, VCS |
| `project.settings_changed` | `ProjectSettingService.update` | 이름, 공개 범위, 코드 접근 제한, 병합 방식, 메뉴(위키 포함)의 전후. 설명과 로고는 바뀌었다는 표시만 |
| `project.transferred` | `ProjectTransferService.accept` | 이전 소유자와 새 소유자, 이름 |
| `project.vcs_changed` | `ProjectVcsService.change` | 전후 VCS |
| `project.member_added`, `member_role_changed`, `member_removed` | `ProjectMemberService` | 대상 사용자, 역할 전후 |
| `project.ruleset_created`, `ruleset_updated`, `ruleset_deleted` | `RulesetService` | 값 전체(만들기), 바뀐 항목의 전후(고치기) |
| `project.webhook_created`, `webhook_deleted` | `WebhookService` | 형식, git push 포함 여부. 주소는 질의와 계정 정보를 뗀 값, **비밀값은 남기지 않음** |
| `org.created`, `org.deleted`, `org.settings_changed` | `OrganizationService`, `OrganizationManagementService` | 이름과 설명의 전후 |
| `org.member_added`, `member_role_changed`, `member_removed` | `OrganizationManagementService`(탈퇴는 `self: true`) | 역할 전후 |
| `site.admin_granted`, `site.admin_revoked` | `SiteUserService.toggleSiteAdmin` | 대상 사용자 |
| `site.user_state_changed`, `site.user_deleted`, `site.guest_mode_changed` | `SiteUserService` | 상태 전후, 삭제는 로그인 ID와 이전 상태만(이름과 이메일은 남기지 않음) |
| `site.data_exported`, `site.data_imported` | `SiteDataController`, `ExternalSiteBackupApiController` | 방식(`web`, `api`), 가져온 테이블과 실패한 테이블. 내보내기는 스트림이 다른 스레드에서 쓰여 요청 문맥을 잃으므로 요청 스레드에서 먼저 남김 |

- 사이트 데이터 내보내기와 가져오기(`DataService`)에는 감사 테이블이 들어 있지 않다. 가져오기가 사용자와 프로젝트를 교체해도 기록은 그대로 남는다.
- 시험: `AuditProjectTest` 10건, `AuditOrganizationSiteTest` 9건, `AuditSiteDataTest` 3건.
- 시험 환경은 파일 쓰기를 끄고(`yona.audit-file-enabled=false`) 파일 시험만 켠다. 켜 두면 기본 데이터 디렉터리(`.`)에 `audit/`이 생긴다.

## A6 조회, 화면, 내보내기 (구현)

- **조회 서비스**: `AuditQueryService.search`(최신순, 쪽 크기 1~200)와 `stream`(시간순, 쪽으로 나눠 읽음). 필터는 `AuditFilter`(행위자 부분 일치, 조치 접두사나 정확히, 결과, 대상 종류, 대상 이름 부분 일치, IP, 프로젝트 번호, 기간). 범위는 `AuditScope`: 사이트 전체, 조직(그 조직 번호를 담은 이벤트), 사용자 본인(행위자 번호나 로그인 ID가 본인이거나 대상이 본인이고 `auth.`, `token.`, `account.` 범주만).
- **화면**: 사이트 관리자 `/sites/audit`(필터, 표, 쪽 나누기, 내려받기, 무결성 검증 단추, 사이드바 링크), 조직 관리자 `/organizations/{이름}/audit`(조직 설정 탭), 사용자 `/user/editform/audit`("보안 기록" 설정 탭). 표는 `audit/list.html` 조각을 함께 쓴다.
- **내려받기**: `format=csv|jsonl|json`(기본 csv), `limit`(기본 1만, 최대 10만), 필터를 그대로 쓴다. CSV는 큰따옴표로 감싸고 `=`, `+`, `-`, `@`로 시작하는 값 앞에 `'`를 붙여 스프레드시트 수식 주입을 막는다. **내려받은 일도 `audit.exported`로 남는다.**
- **무결성 검증**: `/sites/audit/verify`(화면 단추), REST `GET /-_-api/v1/admin/audit/verify`. 처음부터 해시를 다시 계산해 어긋난 첫 기록 번호와 이유(`hash`, `prev_hash`, `head`)를 알린다.
- **REST**(OpenAPI 태그 "감사 로그"):

| 경로 | 범위와 자격 |
|---|---|
| `GET /-_-api/v1/admin/audit` | `site_admin` 범위이면서 사이트 관리자 |
| `GET /-_-api/v1/admin/audit/verify` | 같음 |
| `GET /-_-api/v1/orgs/{org}/audit` | `admin:org` 범위이면서 그 조직의 관리자 |
| `GET /-_-api/v1/user/audit` | `read:user` 범위, 본인 기록만 |

  응답은 `{"total", "page", "size", "events"}`이고 `page`는 1부터이다. 기록의 필드 이름은 줄 파일과 같은 snake_case이고 `details`는 JSON 글 그대로이다(외부에서 해시를 다시 계산할 수 있게).
- 시험: `AuditQueryTest` 12건, `AuditViewTest` 14건, `AuditApiTest` 9건. 탭이나 사이드바 개수를 단언하던 기존 시험 4건과 OpenAPI 계약 검증(`application/x-ndjson`은 건너뜀)을 갱신했다.

## A7 보존 (구현)

- 설정 `yona.audit-retention-days`(기본 365, 0이면 영구)가 지난 **앞부분**(번호 순서로 처음부터 기간을 넘긴 기록)만 지운다. 가운데를 비우면 사슬이 끊기므로 번호 순서의 앞에서부터 처음으로 기간 안인 기록을 만날 때까지만 지운다.
- 지우기 전에 `<데이터>/audit/archive/audit-archive-<처음 번호>-<끝 번호>.jsonl`로 옮기고(JSON 줄, `audit-<날짜>.jsonl`과 같은 모양, `fsync`) 그 뒤에 지운다. 보관 파일을 쓰지 못하면 아무것도 지우지 않는다. 한 묶음(기본 1000건)이 한 트랜잭션이다.
- 사슬의 끝 행(`audit_chain_head`)에 지운 마지막 번호와 해시(`pruned_through_id`, `pruned_hash`)를 남겨 남은 첫 기록의 이전 해시가 그 해시와 같게 이어진다. 무결성 검증은 이 값에서 시작한다. 전부 지워도 사슬은 이어진다.
- 지운 일은 시스템 행위자의 `audit.purged` 이벤트(건수, 번호 범위, 보관 파일 이름)로 남고, 내려받은 일은 `audit.exported`로 남는다.
- `AuditRetentionScheduler`가 서버를 띄운 10분 뒤 처음, 이후 6시간마다 돌린다. 보관 파일은 지우지 않는다(관리자가 관리한다).
- 시험: `AuditRetentionTest` 10건(시계를 바꿔 옛 기록을 만든다).

## A8 구조 시험 (구현)

`AuditCoverageTest`가 소스에서 세 가지를 지킨다: (1) 감사해야 하는 변경 메서드 목록(`AUDITED`, 서비스와 컨트롤러 메서드 약 55개)이 모두 감사 기록을 부른다. 새 변경 메서드를 만들면 목록에 올리고 기록을 붙여야 통과한다. (2) 감사 이벤트를 만드는 파일에 쓴 조치 이름이 모두 이 문서의 이벤트 표에 있다. (3) 감사 이벤트를 만드는 줄에 비밀 값 이름(`password`, `secret`, `deviceCode` 등)이 직접 들어가지 않는다.

## 해시 v2와 비움(X-9 2차 B2, D-63)

- 기록은 내용 해시(`content_hash`)를 따로 저장하고 `hash = SHA-256("v2" + 이전 해시 + 내용 해시)`이다. 내용 해시는 `prev_hash`, `hash`, `content_hash`, `redacted_at`을 뺀 모든 필드로 기록할 때 한 번 정한다. 옛(v1) 기록은 `content_hash`가 없고 이전 방식으로 검증한다.
- `git.*` 이벤트는 `yona.audit-git-retention-days`(기본 90, 0이면 따로 비우지 않음)가 지나면 비운다. 비우기 전 전체 내용을 `<데이터>/audit/archive/audit-redact-<처음 번호>-<끝 번호>.jsonl`에 `fsync`한 뒤 행위자, 대상, 조직과 프로젝트 번호, IP, 사용자 에이전트, 요청 번호, 토큰, 상세를 비우고 `redacted_at`을 적는다. 행과 해시는 남아 사슬 검증을 통과한다.
- 검증은 비우지 않은 v2 행의 내용을 다시 계산해 저장된 내용 해시와 맞추고(어긋나면 `reason=content`), 사슬은 저장된 내용 해시로 잇는다(어긋나면 `hash`). 비운 행의 내용 해시는 보관 파일의 원본으로 다시 계산해 확인할 수 있다.
- 이는 "추가 전용"의 두 번째 예외이다(첫째는 오래된 앞부분 삭제). 비운 행은 보안 이벤트의 보존 기간이 지나 앞부분 삭제로 지워질 때 물리적으로 사라진다. 보존 처리(`AuditRetention.purge`)는 먼저 비우고 그 뒤 앞부분을 지운다.

## B3 git 전송 이벤트 (구현)

`GitHooks`(푸시 전후 훅)가 남기며 복제와 가져오기는 양이 많아 기록하지 않는다. 인증 실패는 1차의 `auth.basic_failure`가 이미 남긴다. 기록이 실패해도 푸시는 막지 않는다(독립 기록). 보존은 `yona.audit-git-retention-days`(기본 90)이고 지나면 비운다(B2).

| 조치 | 시점 | 상세 |
|---|---|---|
| `git.push` | 만들기나 갱신이 반영됐을 때 한 번 | `refs`: ref마다 `ref`, `type`(CREATE, UPDATE, UPDATE_NONFASTFORWARD), `old`, `new` |
| `git.force_push` | 빨리 감기가 아닌 갱신(강제 푸시)마다 | `ref`, `type`, `old`, `new` |
| `git.ref_deleted` | 브랜치나 태그를 지웠을 때 ref마다 | `ref`, `old`, `kind`(`branch`, `tag`) |
| `git.push_rejected` | 규칙 묶음이나 예약 ref(`refs/yobi`)가 푸시를 거부했을 때(결과 DENIED) | `ref`, `reason` |

## B4~B6 외부 스트리밍과 보존 보호 (구현)

설정은 속성 파일에서만 한다. 대상이 하나도 설정되지 않으면 아무것도 보내지 않는다.

| 키 | 기본값 | 설명 |
|---|---|---|
| `yona.audit-stream-webhook-url` | 없음 | 웹훅 주소(http, https). 비어 있으면 보내지 않는다 |
| `yona.audit-stream-webhook-secret` | 없음 | 있으면 본문에 HMAC-SHA256 서명을 싣는다 |
| `yona.audit-stream-batch-size` | `100` | 한 번에 보내는 기록 수 |
| `yona.audit-stream-syslog-host` | 없음 | syslog 서버. 비어 있으면 보내지 않는다 |
| `yona.audit-stream-syslog-port` | `514` | syslog 포트 |
| `yona.audit-stream-syslog-protocol` | `udp` | `udp`, `tcp`, `tls` |
| `yona.audit-stream-syslog-hostname` | 서버 호스트 이름 | 메시지 머리의 HOSTNAME |

- **전달 방식**: `AuditStreamer`가 10초마다(서버를 띄운 30초 뒤부터) 대상별 커서(`audit_stream_cursor`)보다 큰 기록을 번호 순서로 묶음마다 보낸다. 번호가 커밋 순서이므로 순서가 지켜진다. 성공해야 커서를 올리므로 **적어도 한 번** 전달되고 수신 쪽은 `id`로 중복을 걸러야 한다. 실패하면 오류와 연속 실패 횟수를 커서에 남기고 다음 주기에 같은 묶음을 다시 보낸다. 대상이 처음 설정되면 그 시점까지의 기록은 보내지 않고 이후 기록부터 보낸다(과거는 줄 파일과 내보내기로 얻는다). 서버 한 대를 전제로 한다.
- **웹훅**: `POST`, 본문은 `{"events":[내보내기 JSON...]}`, 헤더는 `Content-Type: application/json; charset=utf-8`, `X-Yona-Event: audit`, `X-Yona-Delivery: <처음 번호>-<끝 번호>`, 비밀값이 있으면 `X-Yona-Signature: sha256=<본문의 HMAC-SHA256 16진수>`이다. 응답이 2xx가 아니면 실패이다.
- **syslog**: RFC 5424 `<우선순위>1 시각 호스트 yona - 기록번호 [audit@32473 action outcome actor ip] {내보내기 JSON}`. 우선순위는 시설 13(log audit)과 심각도(성공 6, 그 밖 4)이다. UDP는 기록마다 데이터그램 하나, TCP와 TLS는 옥텟 세기 틀(`길이 메시지`)이다. TLS는 JVM 기본 신뢰 저장소를 쓴다.
- **상태**: 사이트 감사 화면(`/sites/audit`) 위에 설정된 대상의 표(밀린 기록, 마지막 전송, 실패 횟수, 마지막 오류)가 보이고 REST `GET /-_-api/v1/admin/audit/streams`(범위 `site:admin`)가 같은 값을 준다. 대상이 없으면 표가 없고 빈 목록이다.
- **보존 보호**: 보존 처리(앞부분 삭제와 git 이벤트 비움)는 설정된 대상 중 가장 덜 보낸 커서 뒤의 기록(아직 보내지 않은 것)을 지우거나 비우지 않는다. 대상이 막혀 있으면 그만큼 DB에 쌓이므로 상태 표의 밀린 기록을 지켜봐야 한다.
- **감사 범위 밖**: 위키 변경은 기록하지 않는다(git 이력이 있다, D-62). git clone과 fetch는 기록하지 않는다(D-63).


## SSH 접속의 행위자 (X-3, D-66)

SSH 서버는 웹 요청 밖의 스레드라 `AuditRecorder.withRemote(행위자, 주소, "ssh")`로 문맥을 정한다. 푸시 훅이 남기는 `git.push`, `git.force_push`, `git.ref_deleted`, `git.push_rejected`에 행위자(사용자 키는 `USER`, 배포 키는 행위자 유형 `DEPLOY_KEY`와 키 번호, 키 이름), 접속 주소(`ip`), 접속 종류(`user_agent` 열의 `ssh`)가 들어간다. 인증하지 못한 접속은 `auth.basic_failure`(채널 `ssh`)로, 막힌 주소는 `auth.token_blocked`로 남는다.
