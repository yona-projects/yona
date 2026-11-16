---
title: v1.6 설정 키별 설명 (이관되는 키와 v2에서의 쓰임)
type: architecture
sources: [conf/application.conf.default, app/utils/Config.java, app/utils/LdapService.java, src/main/kotlin/dev/yona/config/YonaProperties.kt, src/main/kotlin/dev/yona/domain/upgrade/V16ConfigConverter.kt]
updated: 2026-11-16
---

# v1.6 설정 키별 설명

`scripts/migrate-v16.sh --v16-conf=<application.conf>`가 옮기는 키를 하나씩 설명한다. 모든 키는 v2 소스에서 읽히는 것을 확인했고(2026-11-16), 아래 "쓰이는 곳"은 그 확인 결과이다.
이전 방법은 [v16-migration-guide.md](v16-migration-guide.md), 전체 대응표는 [configuration-keys.md](configuration-keys.md)를 본다.

읽는 법: 기본값은 **v2에서 키가 없을 때**의 값이다. 기간 값은 `60s`, `6h`, `1d`처럼 단위를 붙이거나 숫자(밀리초)로 쓴다. "v1.6과 다름"에 적힌 것은 값은 그대로 옮기지만 쓰임이 달라진 부분이다.

## 사이트 기본

| v1.6 키 | v2 키 | 기본값 | 목적 | 쓰이는 곳 | v1.6과 다름 |
|---|---|---|---|---|---|
| `application.siteName` | `yona.site-name` | `Yona` | 사이트 이름. 화면 제목, 2단계 인증 앱에 보이는 발급자 이름 | `UserMenuController`, `TwoFactorSettingsController` 등 14곳 | 2단계 인증 표시는 v2 신규 |
| `application.secret` | `yona.secret` | 자리표시 값(=첫 기동으로 본다) | 사이트 비밀 키. 아래 "비밀 키" 참고 | `SiteSecret`, `RememberMeCookies`, `TwoFactorService`, `SsoSettingsService`, `LdapSettingsService` | **쓰임이 다르다.** v1.6은 세션 서명, v2는 아래 다섯 가지 |
| `application.langs` | `yona.langs` | `en-US, ko-KR, ja-JP, ru-RU, uz-UZ` | 지원 언어 목록. `Accept-Language`에 가장 알맞은 언어를 고르고 맞는 것이 없으면 첫 번째를 쓴다 | `LocaleConfig` | 같음 |
| `application.server` | `yona.server` | `Play/2.3` | SVN WebDAV 오류 본문의 `<address>`에 쓰는 서버 이름 | `SvnDavService` | 같음 |
| `application.scheme` | `yona.scheme` | 빈 값(`http`) | 저장소 주소, 링크의 `http`/`https`. 리버스 프록시 뒤 HTTPS라면 `https`로 정한다 | `RepositoryUrls`, `IssueCreateController`, `SiteUrls` | 같음 |
| `application.hostname` | `yona.hostname` | 빈 값(요청의 Host) | 저장소 주소와 외부 API 응답의 서버 주소 | `RepositoryUrls`, `ExternalUserApiController` | 같음 |
| `application.port` | `yona.port` | 없음 | 저장소 주소의 포트(80이면 생략). `hostname`이 없으면 무시된다(v1.6 진단과 같음) | `RepositoryUrls`, `SiteUrls` | 같음 |
| `application.context` | `yona.application-context` | 빈 값 | 하위 경로에 둘 때의 경로(링크 접두어, README 상대 경로). 서버의 컨텍스트 경로가 되기도 한다 | `SiteUrls`, `IssueCreateController`, `ApplicationContextCustomizer` | v1.6은 라우터 접두어였다. v2는 앞뒤 `/`를 정리해 링크와 서블릿 컨텍스트에 쓴다 |
| `application.feedback.url` | `yona.feedback-url` | Yona 이슈 페이지 | 상단 메뉴의 "피드백" 링크. 비우면 링크를 숨긴다 | `templates/common/navbar.html` | 같음 |
| `application.navbar.custom.link.name` / `.url` | `yona.navbar-custom-link-name` / `-url` | 빈 값 | 사용자 메뉴에 붙이는 사이트 맞춤 링크. 이름이 비면 숨긴다 | `templates/common/usermenu.html` | 같음 |
| `application.login.page.loginId.placeholder` / `password.placeholder` | `yona.login-page-login-id-placeholder` / `-password-placeholder` | 빈 값(기본 안내 문구) | 로그인 화면 입력란의 안내 문구 | `templates/user/login.html` | 같음 |
| `application.allowsAnonymousAccess` | `yona.allows-anonymous-access` | `true` | 끄면 익명 접근이 가능한 화면도 로그인을 요구한다 | `AnonymousCheckStage`, `AccessControl` | 같음 |
| `application.guest.user.login.id.prefix` | `yona.guest-user-login-id-prefix` | 빈 값 | 이 접두어로 시작하는 LDAP 로그인 ID를 손님 계정으로 만든다(쉼표로 여러 개) | `LdapAuthService` | 같음 |
| `application.noreferrer` | `yona.noreferrer` | `true` | 외부 링크에 `rel="noreferrer"`를 붙인다(마크다운, 알림 메일) | `MarkdownConfig`, `MailHtml` | v1.6 코드 기본값은 `false`. 키가 없던 설정은 변환기가 `false`로 고정한다 |
| `application.displayPrivateRepositories` | `yona.display-private-repositories` | `false` | 프로젝트 목록에 비공개 저장소도 이름을 보인다 | `ProjectListController`, `OrganizationController`, `ProjectListService` | 같음 |
| `application.hide.project.listing` | `yona.hide-project-listing` | `false` | 구성원이 아닌 사용자에게 프로젝트 목록과 검색을 숨긴다 | `ProjectListController`, `ShellNavs`, `ExternalSearchApiController` | 같음 |
| `application.show.user.email` | `yona.show-user-email` | `true` | 사용자 화면과 투표자 목록에 이메일을 보인다 | `UserPageController`, `partial-voter-list.html` | 같음 |
| `application.send.yona.usage` | `yona.send-yona-usage` | `true` | 사용 통계 태그를 낸다(측정 ID가 있을 때만) | `AnalyticsSettings` | v1.6은 UA 태그, v2는 GA4 태그(X-21) |
| `application.maxFileSize` | `yona.max-file-size` | `2147483454`(약 2GB) | 첨부 파일 하나의 최대 크기(바이트). 넘으면 413 | `AttachmentController`, `WikiService` | 같음 |
| `application.codeBrowser.viewer.maxFileSize` | `yona.code-browser-max-file-size` | `1048576`(1MB) | 코드 브라우저가 내용을 보이는 파일의 최대 크기. 넘으면 내용을 보이지 않는다 | `CodeBrowserController`, `GitCodeReader` | 같음 |
| `application.use.email.verification` | `yona.use-email-verification` | `false` | 가입할 때 이메일 인증을 요구한다 | `SignupService` | 같음 |
| `application.allowed.sending.mail.domains` | `yona.allowed-sending-mail-domains` | 빈 값(제한 없음) | 메일을 보낼 수 있는 수신자 도메인 목록(쉼표) | `SystemMails`, `NotificationMailSender`, `SignupService` | 같음 |
| `signup.require.admin.confirm` | `yona.signup.require-admin-confirm` | 없음(꺼짐) | 새 계정을 관리자가 승인하기 전까지 잠가 둔다 | `Signup.required()` -> `YonaPasswordAuthenticationProvider`, `LocalAuthenticator` | 같음 |
| `signup.require.confirm` | `yona.signup.require-confirm` | `false` | v1.1 호환 옛 키. `require-admin-confirm`이 없을 때만 쓴다 | `Signup.required()` | 같음 |

## 프로젝트 기본값

| v1.6 키 | v2 키 | 기본값 | 목적 | 쓰이는 곳 | v1.6과 다름 |
|---|---|---|---|---|---|
| `project.default.scope.when.create` | `yona.project-default-scope` | `public` | 새 프로젝트를 만들 때 처음 고르는 공개 범위 | `ProjectCreateController`, `ImportController`, `ExternalProjectManageApiController` | 같음 |
| `project.default.menus.when.create`, `project.creation.default.menus` | `yona.project-default-menus` | `code, issue, pullRequest, review, milestone, board, wiki` | 새 프로젝트에서 켜 두는 메뉴 | `ProjectCreateController`, `ExternalProjectManageApiController` | v1.6 코드 기본값에는 `wiki`가 없다(프로젝트 위키는 v2 신규) |

## 알림과 메일

| v1.6 키 | v2 키 | 기본값 | 목적 | 쓰이는 곳 | v1.6과 다름 |
|---|---|---|---|---|---|
| `notification.bymail.enabled` | `yona.notification-mail-enabled` | `true` | 알림 메일 발송 스케줄러를 켠다 | `NotificationMailScheduler` | 같음 |
| `application.notification.bymail.hideAddress` | `yona.notification-mail-hide-address` | `true` | 알림 메일의 수신자 주소를 숨긴다 | `NotificationMailSender` | 같음 |
| `application.notification.bymail.recipientLimit` | `yona.notification-mail-recipient-limit` | `100` | 한 메일에 보이는 수신자 수 한도(0이면 제한 없음) | `NotificationMailSender` | v1.6 코드 기본값은 0. 키가 없던 설정은 변환기가 `0`으로 고정한다 |
| `application.notification.bymail.initdelay` / `.interval` / `.delay` | `yona.notification-mail-init-delay` / `-interval` / `-delay` | `5s` / `60s` / `180s` | 스케줄러의 첫 지연 / 확인 주기 / 이벤트가 이 시간 지나야 보낸다 | `SchedulingDelays`, `NotificationMailScheduler` | 같음 |
| `application.notification.keep-time` | `yona.notification-keep-time` | `-1`(지우지 않음) | 알림을 보관하는 일수. 지나면 지운다 | `NotificationCleaner` | 같음 |
| `application.notification.draft-time` | `yona.notification-draft-time` | `30s` | 같은 대상의 알림을 이 시간 안에 연달아 만들면 하나로 합친다 | `NotificationEvents`, `PullRequestEventService` | 같음 |
| `application.issue-event.draft-time` | `yona.issue-event-draft-time` | `30s` | 같은 사용자의 같은 종류 이슈 변경을 이 시간 안에 합친다 | `IssueEventService` | 같음 |
| `play-easymail.from.email` / `.name` | `yona.easymail.from-email` / `-from-name` | 빈 값(`smtp` 주소) / `yona-no-reply` | 가입 안내 등 시스템 메일의 보내는 주소와 이름 | `SystemMails` | 같음 |
| `smtp.user` | `yona.smtp.user` + `spring.mail.username` | `yona` | 알림 메일의 보내는 주소(`user@domain`)와 SMTP 로그인 이름 | `SystemMails`, `NotificationMailSender` | 같음 |
| `smtp.domain` | `yona.smtp.domain` | `yona.local` | 보내는 주소의 도메인. 비우면 사이트 호스트 이름 | `NotificationMailSender` | 같음 |
| `smtp.host` / `.port` / `.ssl` / `.password` | `spring.mail.host` / `.port` / `.properties.mail.smtp.ssl.enable` / `.password` | 없음 | SMTP 서버 접속 정보. `host`가 없으면 메일을 보내지 않고 경고만 남긴다 | `MailDispatcher`(스프링 `JavaMailSender`) | **`smtp.mock=true`이면 옮기지 않는다.** v1.6 모의 발송기는 보내지 않았는데 v2에는 모의 발송이 없다 |
| `smtp.mock`, `smtp.archive.size` | (없음) | - | v1.6의 시험용 모의 발송기와 그 보관 개수 | - | v2에 해당 없음. 변환기가 알려 준다 |

## 메일함(IMAP): 메일로 이슈와 게시글에 답하기

| v1.6 키 | v2 키 | 기본값 | 목적 | 쓰이는 곳 |
|---|---|---|---|---|
| `imap.use` | `yona.imap.use` | `false` | 메일함 서비스를 켠다 | `MailboxService` |
| `imap.host` / `.port` / `.ssl` | `yona.imap.host` / `.port` / `.ssl` | 없음 / 기본 포트 / `true` | IMAP 서버 접속 | `MailboxService` |
| `imap.user` / `.password` | `yona.imap.user` / `.password` | 없음 | 메일함 계정 | `MailboxService` |
| `imap.folder` | `yona.imap.folder` | `Inbox` | 읽을 폴더 | `MailboxService` |
| `imap.address` / `.domain` | `yona.imap.address` / `.domain` | 없음 | 답장을 받는 Yona 주소. `address`가 없으면 `user@domain`(도메인이 없으면 사이트 호스트 이름)이다 | `MailboxAddress`, `MailParsing` |
| `application.mailbox.polling.interval` | `yona.mailbox-polling-interval` | `5m` | IDLE을 쓸 수 없을 때 새 메일을 확인하는 주기 | `MailboxService` |

## 업데이트 확인과 정리 주기

| v1.6 키 | v2 키 | 기본값 | 목적 | 쓰이는 곳 | v1.6과 다름 |
|---|---|---|---|---|---|
| `application.update.check.use` | `yona.update.check` | `true` | 새 버전 확인을 켠다 | `UpdateCheckScheduler`, `SiteMaintenanceController` | 같음 |
| `application.update.notification.interval` | `yona.update.interval` | `6h` | 새 버전 확인 주기 | `UpdateCheckScheduler` | v1.6 코드 기본값은 1시간. 키가 없던 설정은 변환기가 `1h`로 고정한다 |
| `application.update.notification.initdelay` | `yona.update.init-delay` | `5s` | 서버를 시작한 뒤 첫 확인까지의 지연 | `UpdateCheckScheduler` | 같음 |
| `application.update.repositoryUrl` | `yona.update.repository-url` | Yona GitHub 저장소 | 태그를 읽어 새 버전을 찾는 저장소 | `UpdateCheckService` | v1.6 코드 기본 저장소(`repo.yona.io`)는 더 쓰이지 않아 v2 기본값을 쓴다 |
| `application.update.releaseUrlFormat` | `yona.update.release-url-format` | GitHub 릴리스 주소 형식(`%s`가 버전) | 새 버전 안내의 릴리스 링크 | `UpdateCheckService` | 같음 |
| `application.temporaryfiles.keep-up.time` | `yona.temporary-files-keep-up-time` | `1d` | 첨부 후 게시하지 않은 임시 파일을 보관하는 시간 | `PostAttachments`, `TemporaryFileCleaner` | 같음 |

## 외부 연동

| v1.6 키 | v2 키 | 기본값 | 목적 | 쓰이는 곳 | v1.6과 다름 |
|---|---|---|---|---|---|
| `github.allow.migration` | `yona.github.allow-migration` | `false` | GitHub 이전 화면(`/migration`)을 연다 | `MigrationController` | 같음 |
| `github.client.id` / `.secret` | `yona.github.client-id` / `-client-secret` | 자리표시 문구 | 이전 화면이 GitHub OAuth 코드를 토큰으로 바꿀 때 보내는 앱 정보 | `GithubTokenClient` | v1.6은 소셜 로그인에도 썼다. v2는 이전 화면에만 쓴다(소셜 로그인 없어짐) |
| `slack.<이벤트>` | `yona.slack.<이벤트>` | 없음(빈 색) | 슬랙 웹훅으로 보내는 첨부의 **색**(`color`)을 이벤트 종류별로 정한다(예: `NEW_ISSUE`). 값이 비면 옮기지 않는다 | `WebhookPayloads` | 같음 |
| `application.extras.translation.api` / `.headerKey` / `.headerValue` | `yona.translation-api` / `-header-key` / `-header-value` | 빈 값(번역 끔) | 이슈 번역 API 주소와 인증 헤더 | `TranslationClient`, `TranslationApiController` | 같음 |

## LDAP

v1.6은 설정 파일로만 정했다. v2는 사이트 설정(DB, 사이트 관리 > SSO > LDAP)이 있으면 그것을 쓰고, 없으면 이 키들을 쓴다(D-82). 이관은 켜진 LDAP을 DB로 옮긴다([v16-migration-guide.md](v16-migration-guide.md)). 모든 키는 `LdapSettingsService.effective()`를 거쳐 `Ldap.kt`(접속과 조회), `LdapAuthService`(로그인 흐름)에서 쓰인다.

| v1.6 키 | v2 키 | v1.6 기본값(LDAP을 켰는데 키가 없을 때 변환기가 채운다) | 목적 |
|---|---|---|---|
| `application.use.ldap.login.supoort` | `yona.ldap.enabled` | `false` | LDAP 로그인을 켠다(키 이름의 `supoort` 오타는 v1.6 그대로) |
| `ldap.host` / `.port` | `yona.ldap.host` / `.port` | `127.0.0.1` / `389` | LDAP 서버 주소 |
| `protocol`(최상위) | `yona.protocol` | `ldap` | 접속 프로토콜(`ldap`, `ldaps`). **v1.6은 `ldap.protocol`이 아니라 이 키를 읽었다**(`ldap.protocol`은 옮기지 않는다) |
| `ldap.baseDN` | `yona.ldap.base-dn` | 빈 값 | 사용자를 찾는 기준 DN |
| `ldap.distinguishedNamePostfix` | `yona.ldap.distinguished-name-postfix` | 빈 값 | 인증할 때 만드는 주체 DN의 꼬리(`<userNameProperty>=<이름>,<꼬리>`) |
| `ldap.loginProperty` | `yona.ldap.login-property` | `sAMAccountName` | 로그인 ID를 찾는 속성 |
| `ldap.displayNameProperty` | `yona.ldap.display-name-property` | `displayName` | 사용자 이름으로 쓰는 속성 |
| `ldap.userNameProperty` | `yona.ldap.user-name-property` | `CN` | 주체 DN을 만들 때의 사용자 이름 속성 |
| `ldap.emailProperty` | `yona.ldap.email-property` | `mail` | 이메일 속성(이메일로 로그인할 때의 검색 속성이기도 하다) |
| `ldap.options.useEmailBaseLogin` | `yona.ldap.use-email-base-login` | `false` | 로그인 ID로 찾은 사용자의 이메일로 인증한다 |
| `ldap.options.fallbackToLocalLogin` | `yona.ldap.fallback-to-local-login` | `false` | LDAP 서버에 접속하지 못하거나 인증에 실패하면 로컬 비밀번호로 로그인을 시도한다 |
| `ldap.options.englishNameAttributeName` | `yona.ldap.english-name-attribute-name` | 빈 값 | 영문 이름 속성(비우면 쓰지 않는다) |

## 서버와 데이터베이스

| v1.6 키 | v2 키 | 목적 | v1.6과 다름 |
|---|---|---|---|
| `%prod.http.port`, `http.port` | `server.port` | 서버가 받는 포트 | Play 운영 모드 키를 스프링 키로 옮긴다 |
| `db.default.url` / `.user` / `.password` | `spring.datasource.url` / `.username` / `.password` | 데이터베이스 접속 | `jdbc:mysql:`은 `jdbc:mariadb:`로 바꾼다 |
| `data.batch.size` | `yona.data-batch-size` | 사이트 데이터 가져오기에서 한 번에 넣는 행 수(기본 100) | v1.6의 숨은 설정 |
| `parsers.text.maxLength` | `yona.parsers-text-max-length` | 폼 본문의 최대 크기(기본 102400바이트). 넘으면 거절한다 | v1.6(Play `parsers.text.maxLength`)과 같음 |

## 비밀 키 (`yona.secret`)

v2에서 이 값은 다섯 곳에서 쓰인다. **운영 중에 바꾸면 2~5번에 저장된 값을 읽지 못하므로 v1.6의 값을 그대로 옮기고 이후로는 바꾸지 않는다.**

1. 첫 기동 판정: 값이 비었거나 자리표시 값이면 서버가 처음 기동한 것으로 보고 모든 요청에 기본 관리자 계정을 정하는 화면(`welcome/secret`)만 보인다(`SiteSecret`). 이 화면에서 정하면 `<데이터>/config/secret.properties`에 쓴다.
2. 로그인 유지(remember-me) 쿠키의 HMAC-SHA256 서명 키(`RememberMeCookies`). 바꾸면 모든 사용자의 로그인 유지가 풀린다.
3. 2단계 인증의 비밀 값과 백업 코드의 암호화 키, WebAuthn 사용자 핸들 키(`TwoFactorService`).
4. SSO(OIDC, SAML) 설정의 클라이언트 비밀 값과 SAML SP 비밀 키의 암호화 키(`SsoSettingsService`).
5. LDAP 조회 계정 비밀번호의 암호화 키(`LdapSettingsService`).

## 옮기지 않는 것

- 소셜 로그인 키(`application.use.social.login.only`, `.name.sync`, `application.social.login.support`, `application.social.login.github.name`)와 `include "social-login.conf"`: v2는 소셜 로그인을 없앴다(D-127).
- `ldap.protocol`: v1.6이 읽지 않던 키다(위 LDAP 표 참고).
- `smtp.mock`, `smtp.archive.size`: v1.6 시험용 모의 발송기 설정이다.
- Play, Akka, Ebean 내부 설정(`play.*`, `akka.*`, `ebean*`, `applyEvolutions.*`, `application.global`, `db.default.driver` 등): v2에서 뜻이 없다.
- 위에 없는 키: 변환기가 "대응하는 v2 키를 모르는 키"로 보고한다. 직접 확인한다.

## 확인한 방법

- v1.6: `app/`에서 설정을 읽는 키 71개를 뽑아(`getString`, `getBoolean`, `getInt`, `getMilliseconds` 등) 변환기와 대조했다. 기본값은 v1.6 코드의 기본값과 비교했다.
- v2: 각 키를 읽는 코드의 파일과 줄을 찾아 읽고 용도를 판정했다. 설정 클래스의 설명(`YonaProperties`)과 어긋나지 않는지 봤다.
- 시험: `V16ConfigConverterTest`(20건)가 변환 규칙, 변환기가 만드는 `yona.*` 키가 모두 `YonaProperties`에 있는지, `YonaProperties` 주석의 v1.6 키가 모두 변환기에 있는지를 지킨다.
