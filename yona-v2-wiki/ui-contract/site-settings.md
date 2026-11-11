---
title: UI 계약 - 사이트 설정 (보안, OAuth 앱, Git LFS, SSO)
type: ui-contract
sources:
  - src/main/resources/templates/site/{security,oauth-apps,lfs,sso}.html
  - src/main/kotlin/dev/yona/web/site/{SiteSecurityController,SiteOAuthAppsController,SiteLfsController,SiteSsoController}.kt
  - src/main/kotlin/dev/yona/domain/site/SiteSettingService.kt
  - src/main/kotlin/dev/yona/domain/sso/{SsoSettingsService,SsoSettings}.kt
  - src/main/kotlin/dev/yona/domain/ldap/LdapSettings.kt
updated: 2026-11-11
---

# UI 계약: 사이트 설정 화면 넷

모두 v1.6에 없는 추가 화면이다. 틀(`site-mng-layout`), 관리자 권한 판정, 반영 방식 표, CSRF, flash는 [site.md](site.md) 2절. 모든 화면과 요청은 `@SiteManagerOnly`이고 비사이트 관리자(비로그인 포함)는 `403 error.auth.unauthorized.waringMessage`이다. 모든 저장은 DB에 쓰며 요청마다 읽으므로 **재시작이 필요 없다**(OIDC 제공자 서명 키만 파일 설정이라 재시작, site.md 2.3절).

공통 모양: 저장 폼은 `method=post` HTML 폼이고 JS가 없다. 성공하면 `303` 뒤 같은 화면으로 돌아가며 flash `success`(`site.security.saved`, `site.lfs.saved`, `site.sso.saved` 등)가 토스트로 보인다. 체크박스는 체크하지 않으면 값이 오지 않으므로 서버가 "`true`가 아니면 끔"으로 읽는다.

## 1. 요청 요약

| 요청 | 컨트롤러 | 바디 | 성공 | 거부 |
|---|---|---|---|---|
| `GET /sites/security` | SiteSecurityController.kt:24 | | 화면 | |
| `POST /sites/security` | SiteSecurityController.kt:37 | `twoFactorEnforcement` = `OFF`, `ADMINS`, `ALL` | `303 → /sites/security` + `site.security.saved` | 알 수 없는 값 `400` |
| `POST /sites/security/oauth` | SiteOAuthAppsController.kt:53 (이름과 달리 보안 화면의 폼) | `cimd`, `userApps` (`true`이면 켬) | 같은 `303` | |
| `POST /sites/security/mcp` | SiteOAuthAppsController.kt:61 | `enabled`, `write` | 같은 `303` | |
| `GET /sites/oauth-apps` | SiteOAuthAppsController.kt:41 | | 화면 | |
| `POST /sites/oauth-apps/{id}/delete` | SiteOAuthAppsController.kt:45 | | `303 → /sites/oauth-apps` | 없는 앱 `404` |
| `GET /sites/lfs`, `POST /sites/lfs` | SiteLfsController.kt:25, 36 | `enabled`, `maxObjectMb`(정수, 1 이상), `quotaMb`(정수, 0 이상) | `303 → /sites/lfs` + `site.lfs.saved` | 숫자가 아니거나 범위를 벗어나면 `400` |
| `GET /sites/sso?tab=` | SiteSsoController.kt:46 | | 화면. `tab`은 `general`, `ldap`, `oidc`, `saml2`, `saml2-keys`이고 그 밖은 `general` | |
| SSO 저장 `POST /sites/sso/{mode,ldap,oidc,saml2}`, 확인 `POST /sites/sso/{ldap,oidc}/check`, `POST /sites/sso/saml2/{metadata,keys/generate,keys/upload,keys/remove-previous}`, `GET /sites/sso/saml2/sp-certificate.pem` | SiteSsoController.kt:62-155 | 5절 | 항상 `303 → /sites/sso?tab={탭}` + flash(`success` 또는 `warning`) | 저장 검증 실패는 `warning` flash(저장하지 않음). 키가 없는 PEM 내려받기는 `404` |

## 2. 보안 `site/security.html`

모델: `title`(`site.sidebar.security`), `enforcement`(`OFF`, `ADMINS`, `ALL`), `oauthCimd`, `oauthUserApps`, `mcpEnabled`, `mcpWrite`, `oidc`(상태 맵) (SiteSecurityController.kt:24-28). 레이아웃: 제목 `site.security.title`, 네 영역이 위에서 아래로 있다. 영역 셋은 독립된 폼(`#security-form`, `#oauth-settings-form`, `#mcp-settings-form`)이라 저장 단추가 영역마다 따로 있고, 한 영역을 저장해도 다른 영역의 미저장 변경은 보내지 않는다. 마지막(OIDC)은 읽기 전용 상태이다 (security.html:13-79).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 2단계 인증 강제 라디오 `twoFactorEnforcement` (`OFF`, `ADMINS`, `ALL`) | 라디오 3개 | 항상. 현재 값 `checked`. 문구 `site.security.twoFactor.{값}`와 `.help` | | 바디 `twoFactorEnforcement` | | security.html:17-23 |
| 저장 (`button.save`) | 제출 | 항상 | | `POST /sites/security` | 위 표 | security.html:24-26 |
| 체크박스 `cimd` (`#oauth-cimd`) | 체크박스 | 항상 | 클라이언트 ID 메타데이터 문서(CIMD) 앱 허용. 기본 켬 | 바디 `cimd=true` | | security.html:32-35 |
| 체크박스 `userApps` (`#oauth-user-apps`) | 체크박스 | 항상 | 일반 사용자와 조직의 OAuth 앱 만들기 허용. 기본 켬. 끄면 새 앱 만들기만 막힌다 | 바디 `userApps=true` | | security.html:36-39 |
| 저장 | 제출 | | | `POST /sites/security/oauth` | | security.html:40-42 |
| OIDC 상태 `#oidc-status` (`data-state=enabled|disabled`) | 읽기 전용 | 항상 | 켜졌으면 배지 `site.security.oidc.enabled`와 발급자, 발견 문서 주소, JWKS 주소, 알고리즘, 키 번호(`kid`)를 `code`로 표시, 꺼졌으면 `site.security.oidc.disabled`와 설정 방법 안내(`th:utext`). **화면에서 바꿀 수 없다**(서명 키는 파일 설정) | | | security.html:45-63; SiteSecurityController.kt:30-35 |
| 체크박스 `enabled` (`#mcp-enabled`) | 체크박스 | 항상 | MCP 서버(`/mcp`) 사용. 끄면 `/mcp`가 404. 기본 켬 | 바디 `enabled=true` | | security.html:68-71 |
| 체크박스 `write` (`#mcp-write`) | 체크박스 | 항상 | MCP 쓰기 도구 허용. 끄면 읽기 도구만 | 바디 `write=true` | | security.html:72-75 |
| 저장 | 제출 | | | `POST /sites/security/mcp` | | security.html:76-78 |

## 3. OAuth 앱 `site/oauth-apps.html`

모델: `title`(`site.sidebar.oauthApps`), `rows`(`Row`: id, 이름, clientId, 소유자 표시, 소유자가 조직인지, 등록 방식 `USER` 또는 `CIMD`, 동의한 사용자 수, 생성일, 기밀 여부) (SiteOAuthAppsController.kt:41-43, 62). 소유자 표시: CIMD 앱은 clientId의 호스트, 조직 소유 앱은 조직 이름, 그 밖은 사용자 로그인 ID. 레이아웃: `div.two-factor-page`에 제목 `site.oauthApps.title`, 설명, 빈 상태 `#all-apps-empty`(`site.oauthApps.empty`) 또는 표 `#all-apps`(이름, clientId, 소유자, 등록 방식, 승인 사용자 수, 삭제) (oauth-apps.html:13-34).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 소유자 칸 | 문구 | 행마다 | 조직 소유면 `oauth.apps.byOrganization(이름)` | | | oauth-apps.html:23 |
| 등록 방식 칸 | 문구 | 행마다 | `site.oauthApps.registration.{USER|CIMD}` | | | oauth-apps.html:24 |
| 삭제 (`oauth.apps.delete`) | 제출(폼 `onsubmit`의 `confirm(site.oauthApps.delete.confirm)`) | 행마다 | 확인창에서 취소하면 보내지 않는다. 앱의 모든 토큰이 쓸 수 없게 된다 | `POST /sites/oauth-apps/{id}/delete` | `303 → /sites/oauth-apps`. 없는 앱 404 | oauth-apps.html:27-29 |

스크립트 없음.

## 4. Git LFS `site/lfs.html`

모델: `title`(`site.sidebar.lfs`), `enabled`, `maxObjectMb`, `quotaMb`, `objectCount`, `usageText`. 값은 바이트를 MiB로 **정수 나눗셈**해 보인다(1 MiB 단위로 맞지 않는 값은 내림). 사용량은 Git(사이트 LFS 저장소)과 Mercurial(저장소 안)의 합이고 `B`, `KB`, `MB`, `GB`, `TB`로 소수 한 자리 (SiteLfsController.kt:25-34, 54-61). 레이아웃: `div.two-factor-page`, 제목 `site.lfs.title`, 설명, 폼 `#lfs-settings-form`, 사용량 `#lfs-site-usage` (lfs.html:11-34).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 사용 체크박스 `enabled` (`#lfs-enabled`) | 체크박스 | 현재 값 `checked` | 끄면 LFS 경로가 404. 기본 켬 | 바디 `enabled=true` | | lfs.html:15-18 |
| 객체 최대 크기 `maxObjectMb` (`#lfs-max-object`, `type=number min=1`) | 입력 | 항상 | LFS 객체 하나의 최대 크기(MiB). 기본 2 GiB | 바디 `maxObjectMb` | 1 미만이거나 정수가 아니면 `400` | lfs.html:19-23; SiteLfsController.kt:37 |
| 프로젝트별 한도 `quotaMb` (`#lfs-quota`, `min=0`) | 입력 | 항상 | 프로젝트 하나가 LFS에 쓸 수 있는 용량(MiB). `0`이면 제한 없음 | 바디 `quotaMb` | 음수나 정수가 아니면 `400` | lfs.html:24-28; SiteLfsController.kt:38 |
| Mercurial 안내 | 문구 | 항상 | `site.lfs.mercurialNote` | | | lfs.html:29 |
| 저장 (`site.lfs.save`) | 제출 | 항상 | | `POST /sites/lfs` | `303 → /sites/lfs` + `site.lfs.saved` | lfs.html:30 |
| 사용량 `#lfs-site-usage` | 문구 | 항상 | `lfs.usage(객체 수, 사용량)` | | | lfs.html:33 |

저장은 세 값을 각각 따로 저장한다(서비스 메서드마다 트랜잭션이 분리되어 일부만 저장될 수 있다, F15). 스크립트 없음.

## 5. SSO `site/sso.html`

### 5.1 개요

`GET /sites/sso?tab=` (SiteSsoController.kt:46-59). 모델: `title`(`site.sidebar.sso`), `tab`, `oidc`, `saml2`, `ssoOnly`, `ldap`(조회 비밀번호를 빈 값으로 바꾼 복사본), `ldapBindPasswordSet`, `ldapProtocol`, `ldapSaved`, `ldapEnabled`, `oidcEnabled`, `saml2Enabled`, `oidcSecretSet`, 그리고 IdP에 알릴 값 `redirectUri`(`{사이트 주소}/sso/oidc/callback/oidc`), `initiateUrl`(`/sso/oidc/initiate`), `acsUrl`(`/sso/saml2/acs`), `entityId`(SP Entity ID, 비어 있으면 메타데이터 주소), `metadataUrl`(`/sso/saml2/metadata/saml2`), `idpCertificates`, `spCertificate`, `spPreviousCertificate`(인증서 요약: 주체, 만료일, SHA-256 지문, 만료됨, 30일 안에 만료).

### 5.2 레이아웃

`div.two-factor-page`: 제목, 설명, Bootstrap 탭 `ul#sso-tabs`(`button[data-bs-toggle=tab]` 5개: 일반, LDAP, OIDC, SAML 2, SAML 2 키) + `div.tab-content` 안 `tab-pane` 5개. 처음 열리는 탭은 서버가 `tab` 값으로 `active`와 `show active`를 준다(클라이언트에는 마지막 탭을 기억하는 저장소가 없다). 모든 저장과 확인은 `?tab={해당 탭}`으로 돌아와 같은 탭이 열린다 (sso.html:11-24).

### 5.3 동작 요소

| 탭 | 요소 | 종류 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 일반 | 요약 `#sso-summary-ldap`, `-oidc`, `-saml2` (`data-state=on|off`) | 문구 | 켜짐과 꺼짐 표시 | | | sso.html:26-28 |
| 일반 | SSO 전용 체크박스 `ssoOnly` (`#sso-only`) | 체크박스 | 켜면 로컬 비밀번호 로그인, LDAP, 소셜 로그인을 막는다(사이트 관리자의 로컬 로그인만 남긴다) | 바디 `ssoOnly=true` | `POST /sites/sso/mode`. 켜려면 켠 SSO(OIDC 또는 SAML 2)가 하나 이상 있어야 하고 없으면 저장하지 않고 `warning` `site.sso.only.needProvider`. 성공 `site.sso.saved` | sso.html:31-37; SiteSsoController.kt:62-65; SiteSettingService.kt:121-132 |
| LDAP | 안내 `#ldap-from-file` | 알림 | 화면에서 저장한 적이 없으면(`!ldapSaved`) "설정 파일 값을 쓰는 중"(`site.sso.ldap.fromFile`) | | | sso.html:43 |
| LDAP | 폼 `#ldap-form` 입력: `enabled`, `protocol`(`ldap`, `ldaps`), `host`, `port`, `baseDn`, `distinguishedNamePostfix`, `loginProperty`, `userNameProperty`, `emailProperty`, `displayNameProperty`, `englishNameAttributeName`, `bindDn`, `bindPassword`(`type=password`, 저장된 값이 있으면 자리표시 `site.sso.secret.keep`, 비우면 저장된 값 유지), `useEmailBaseLogin`, `fallbackToLocalLogin` | 입력들 | 모든 텍스트는 앞뒤 공백을 다듬어 받고 `bindPassword`만 다듬지 않는다 | `POST /sites/sso/ldap` | 켜는데 서버 주소, 포트, 기준 DN, 프로토콜이 맞지 않거나 조회 계정이 불완전하면 저장하지 않고 `warning`(`sso.admin.error.ldapHost`, `ldapPort`, `ldapBaseDn`, `ldapProtocol`, `ldapBindPasswordRequired`, `ldapBindFailed`, `ldapUnreachable`). 성공 `site.sso.saved`. 즉시 적용 | sso.html:44-84; SiteSsoController.kt:68-80 |
| LDAP | 연결 확인 `#ldap-check` (`site.sso.ldap.check`) | 제출(별도 폼) | **저장된** 설정으로 연결을 확인한다(입력란의 미저장 값은 쓰지 않는다) | `POST /sites/sso/ldap/check` | 성공 `success` `site.sso.checked`, 실패 `warning`(오류 키) | sso.html:85-87; SiteSsoController.kt:83-87 |
| OIDC | 안내 `dl.sso-notify`: 리디렉션 주소 `#oidc-redirect-uri`, 시작 주소 `#oidc-initiate-url` | 문구(`code`) | IdP에 등록할 값. 복사 단추는 없고 선택해 복사한다 | | | sso.html:93-96 |
| OIDC | 폼 `#sso-oidc-form`: `enabled`, `displayName`(최대 100), `issuerUri`(`type=url`), `clientId`, `clientSecret`(`type=password`, 저장돼 있으면 자리표시 `site.sso.secret.keep`, 비우면 유지), `scopes`, `emailClaim`, `nameClaim`, `loginIdClaim`, 계정 규칙 체크박스(`autoCreate`, `linkByEmail`, `allowUnverifiedEmail`, `nameSync`, `initiateEnabled`), `clockSkewSeconds`(`min=0 max=600`, 숫자가 아니면 60) | 입력들 | | `POST /sites/sso/oidc` | 켜는데 발급자 형식(https, 루프백만 http), 클라이언트 ID와 비밀 값, 발견 문서 연결과 발급자 일치가 맞지 않으면 저장하지 않고 `warning`(`invalidIssuer`, `httpsRequired`, `missingClient`, `unreachable`, `issuerMismatch`, `invalidDiscovery`). 끄는 저장은 연결을 확인하지 않는다. 비밀 값은 암호화해 저장하고 화면에 다시 보이지 않는다 | sso.html:97-151; SiteSsoController.kt:90-100; SsoSettingsService.kt:46-60 |
| OIDC | 연결 확인 `#oidc-check` | 제출(별도 폼) | 저장된 설정의 발견 문서를 다시 확인. 마지막 확인 시각(`site.sso.oidc.checkedAt`)이 옆에 보인다 | `POST /sites/sso/oidc/check` | `success` `site.sso.checked` 또는 `warning` | sso.html:152-155; SiteSsoController.kt:103-107 |
| SAML 2 | 안내: SP Entity ID `#saml2-entity-id`, ACS 주소 `#saml2-acs-url`, 메타데이터 주소 `#saml2-metadata-url` | 문구 | IdP에 등록할 값 | | | sso.html:162-166 |
| SAML 2 | 메타데이터 가져오기 `#sso-saml2-metadata-form`: `metadataUrl`(`type=url`), `metadataXml`(textarea) | 폼 | XML을 붙여 넣었으면 XML을 우선 쓰고, 없으면 주소에서 가져온다. 발급자, 로그인 주소, 인증서를 채우고 **사용 여부는 바꾸지 않는다** | `POST /sites/sso/saml2/metadata` | 성공 `site.sso.saved`. 읽지 못하면 `warning`(`invalidCertificate`, `missingIdp`, 또는 `unreachable`) | sso.html:168-174; SiteSsoController.kt:124-130 |
| SAML 2 | 폼 `#sso-saml2-form`: `enabled`, `displayName`, `idpEntityId`, `idpSsoUrl`(`type=url`), 숨김 `idpSsoBinding`(비면 `REDIRECT`), `idpCertificates`(textarea PEM 여러 개 + 아래에 인증서 요약 목록 `#saml2-idp-certificates`, 만료됐으면 `text-danger` 만료 표시, 30일 안이면 `text-warning`), `emailAttribute`, `nameAttribute`, `loginIdAttribute`, `spEntityId`(자리표시 = 기본값), `nameIdFormat`, 계정 규칙(`autoCreate`, `linkByEmail`, `nameSync`, `initiateEnabled`), `signRequests`, `clockSkewSeconds`(`0~600`, 기본 60) | 입력들 | OIDC와 달리 "인증되지 않은 이메일 허용" 체크박스는 화면에 없지만 서버는 `allowUnverifiedEmail`을 읽는다(화면이 보내지 않으므로 항상 끔) | `POST /sites/sso/saml2` | 켜는데 IdP 정보나 인증서가 없거나 서명 요청인데 SP 키가 없으면 저장하지 않고 `warning`(`missingIdp`, `invalidCertificate`, `spKeyRequired`) | sso.html:176-242; SiteSsoController.kt:110-121 |
| SAML 2 키 | 현재 SP 인증서 `#saml2-sp-current` | 영역 | `spCertificate`가 있을 때 주체, 만료일(`site.sso.cert.notAfter`), 만료와 임박 표시, 지문 `#saml2-sp-fingerprint` | | | sso.html:248-255 |
| SAML 2 키 | 인증서 내려받기 (`site.sso.saml2.sp.download`) | 링크 | IdP 관리자에게 전달할 PEM | `GET /sites/sso/saml2/sp-certificate.pem` | `application/x-pem-file`, `attachment; filename="yona-saml-sp.pem"`. 키가 없으면 `404` | sso.html:254; SiteSsoController.kt:150-155 |
| SAML 2 키 | 이전 키 `#saml2-sp-previous` + 삭제 (`site.sso.saml2.sp.removePrevious`) | 영역, 제출 | 교체 중인 이전 SP 키를 지운다 | `POST /sites/sso/saml2/keys/remove-previous` | `site.sso.saved` | sso.html:256-264; SiteSsoController.kt:143-147 |
| SAML 2 키 | 키 만들기 `#saml2-generate-keys` (인증서가 없으면 `generate`, 있으면 `regenerate`) | 제출 | 새 키쌍을 만든다. 이미 있으면 이전 키로 옮겨 함께 둔다. 확인창은 없다 | `POST /sites/sso/saml2/keys/generate` | `site.sso.saved` | sso.html:265-267; SiteSsoController.kt:133-135 |
| SAML 2 키 | 업로드 `#saml2-upload-keys`: `privateKey`(textarea, PKCS#8 PEM), `certificate`(textarea PEM) | 폼 | 외부에서 만든 키쌍을 올린다 | `POST /sites/sso/saml2/keys/upload` | 성공 `site.sso.saved`, 형식 오류 `invalidKey`, `invalidCertificate`, `keyMismatch` | sso.html:268-273; SiteSsoController.kt:138-140 |

## 6. 발견 사항 (이어서, site-F14 이후)

| 번호 | 내용 | 근거 |
|---|---|---|
| site-F14 **[검증: 결함 아님]** | 보안 화면의 OAuth와 MCP 저장 요청(`/sites/security/oauth`, `/sites/security/mcp`)이 `SiteSecurityController`가 아니라 `SiteOAuthAppsController`에 있다. 새 UI는 컨트롤러가 아니라 주소로 부른다. `/sites/security`와 같은 화면으로 돌아간다 | SiteOAuthAppsController.kt:53-66 |
| site-F15 **[검증: 재현 불가]** | `POST /sites/lfs`는 사용 여부, 최대 크기, 한도를 세 번 따로 저장한다(각 서비스 메서드가 자기 트랜잭션). 도중 실패하면 일부만 반영될 수 있다(추측: 컨트롤러에 `@Transactional`이 없다는 사실에서 본 것이고 실패를 재현하지 않았다). 화면의 MiB 값은 정수 나눗셈이라 MiB로 나눠 떨어지지 않는 저장값은 내림되어 보인다. 저장하면 내림된 값으로 덮어쓴다 | SiteLfsController.kt:25-34, 36-44 |
| site-F16 **[검증: v2 추가 기능의 결함, 안내 문구로 수정 A-460]** | SSO 연결 확인 단추(LDAP, OIDC)는 입력란의 미저장 값이 아니라 **저장된** 설정으로 확인한다. 값을 고친 뒤 저장하지 않고 확인을 누르면 고친 값이 확인되지 않는다 | SiteSsoController.kt:83-87, 103-107 |
| site-F17 **[검증: 결함 아님, 조치 없음(SAML은 이메일을 항상 확인됨으로 처리해 그 설정이 효과가 없다)]** | SAML 2 화면에는 "인증되지 않은 이메일 허용"(`allowUnverifiedEmail`) 체크박스가 없는데 서버는 이 값을 읽어 저장한다(화면이 보내지 않으므로 SAML 저장 때마다 꺼진다). OIDC에는 있다. SAML은 이메일 검증 표시가 없어 의도일 수 있다(추측) | sso.html:139-140, 226-236; SiteSsoController.kt:117 |
| site-F18 **[검증: 결함 아님]** | OIDC 제공자(서명 키) 상태는 읽기 전용이고 키는 설정 파일에만 있어 바꾸면 재시작이 필요하다. 화면은 이를 알려 주는 문구만 있다(`site.security.oidc.howto`) | security.html:59-62; OidcSettings.kt:24-26 |
| site-F19 **[검증: 수정함(A-464, 2026-11-11)]** | 보안 화면의 세 저장 폼이 서로 독립이다. 두 영역의 변경을 한 번에 저장하는 방법이 없다(영역마다 `303` 뒤 화면이 새로 그려져 미저장 변경은 사라진다) | security.html:14, 29, 65 |
| site-F20 **[검증: 결함 아님]** | 메시지 키 검사에서 빠진 키는 없었다. 다만 `user.role.site_admin`, `site.oauthApps.registration.MANUAL`은 템플릿이 만들 수 있는 키가 아니다(등록 방식은 `USER`, `CIMD`). 새 UI가 등록 방식 값을 더하면 메시지 키도 더해야 한다 | oauth-apps.html:24; OAuthClient.kt:64-66 |

## 7. 시험 근거

`SiteSecurityTest`(9), `SiteOAuthAppsTest`(6), `SiteLfsTest`(6), `SiteSsoTest`(21) (모두 `src/test/kotlin/dev/yona/web/site/`). 로그인 쪽 효과는 `sso/SsoOidcLoginTest`, `SsoSaml2LoginTest`, `SsoWithOidcProviderTest`, LDAP은 `ldap` 시험 묶음. 설정 저장 직후 로그인에 반영되는지(재시작 불필요)를 한 시험으로 묶어 확인하는 곳은 찾지 못했다(서비스가 매번 DB를 읽는다는 코드 근거만 있다).
