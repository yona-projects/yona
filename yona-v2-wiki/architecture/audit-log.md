---
title: 감사 로그 (X-9)
---

# 감사 로그

결정은 [decisions.md](../decisions.md) D-62이다. 엔터프라이즈 수준(GitHub Enterprise 감사 로그를 기준)으로 만든다. v1.6에 없는 추가 항목이다(D-28).

## 단계

A1 결정과 설계(이 문서), A2 핵심(기록기, 해시 사슬), A3 경계 이벤트(인증, 인가 거부), A4 계정과 토큰, A5 도메인 변경과 사이트 관리, A6 조회와 내보내기, A7 보존, A8 구조 시험과 마무리.

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
| `token.created`, `token.updated`, `token.regenerated`, `token.deleted` | API 토큰 | 이름, 범위, 만료. 기기 인증이 만든 토큰도 `token.created`로 남음 |
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
