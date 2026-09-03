# 설정 레퍼런스 (application.yml)

legacy Yona의 `docs/ko/application-conf-desc.md`(`conf/application.conf` 설명)를 yona의
`src/main/resources/application.yml` 기준으로 다시 정리한 문서. legacy는 하나의
`application.conf` 파일이었지만, yona는 Spring Boot 표준(`spring.*`)과 yona 고유 설정(`yona.*`)이
나뉘어 있다. 아래 표의 "기본값"은 코드(`@Value("${...:기본값}")`) 또는 `application.yml`에 실제로
박혀 있는 값이다 — 실제로 검증한 것만 적었다.

## 사이트 기본 정보

| 설정 키 | 기본값 | legacy 대응 | 설명 |
|---|---|---|---|
| `yona.site-name` | `Yona` | `application.siteName` | 사이트 이름 |
| `yona.base-url` | `http://localhost:8080` | `application.scheme`+`hostname`+`port` | 메일 등에 쓰이는 절대 URL |
| `yona.hostname` | `localhost` | `application.hostname` | 호스트명 |
| `application.noreferrer` | `false` | `application.noreferrer` | 외부 링크 이동 시 referer 헤더에서 Yona를 숨길지 |
| `yona.feedback-url` | `https://github.com/yona-projects/yona/issues` | `application.feedback.url` | 상단 메뉴 피드백 링크 |
| `yona.application.navbar.custom-link.name` / `.url` | 빈 문자열 | 없음(신규) | 상단 네비게이션 바에 커스텀 링크를 추가하는 기능. legacy에는 없던 yona 자체 추가 항목 |

## 접근 제어 / 가입

| 설정 키 | 기본값 | legacy 대응 | 설명 |
|---|---|---|---|
| `yona.access.allows-anonymous-access` | `true` | `application.allowsAnonymousAccess` | 비로그인 접근 허용 여부 |
| `yona.application.hide-project-listing` | `false` | `application.hide.project.listing` | 공개 프로젝트 전체 목록 숨김 |
| `yona.signup.require-admin-confirm` | `false` | `signup.require.admin.confirm` | 가입 후 관리자 승인 필요 여부 |
| `yona.signup.allowed-email-domains` | 빈 문자열(제한 없음) | `application.allowed.sending.mail.domains` | 가입 허용 이메일 도메인 allowlist |

legacy의 `application.guest.user.login.id.prefix`(범용 게스트 접두사), `application.displayPrivateRepositories`,
`project.default.scope.when.create`, `project.creation.default.menus`, `application.use.email.verification`은
현재 yona 코드베이스에서 대응하는 설정 키를 찾지 못했다 — 아직 설정 가능한 형태로 이식되지
않았을 가능성이 있다(하드코딩되어 있거나 범위에서 빠졌을 수 있음). `docs/PARITY_BACKLOG.md`에
없다면 새 항목으로 등록이 필요하다.

## 소셜 로그인 / LDAP

자세한 예시는 [social-login-settings.md](social-login-settings.md) 참고.

| 설정 키 | 기본값 | legacy 대응 |
|---|---|---|
| `spring.security.oauth2.client.registration.github.client-id`/`.client-secret` | `dummy-client-id`/`dummy-client-secret` | `social-login.conf`의 Github 설정 |
| `spring.security.oauth2.client.registration.google.client-id`/`.client-secret` | 〃 | `social-login.conf`의 Google 설정 |
| `yona.application.use-social-login-only` | `false` | `application.use.social.login.only` |
| `yona.ldap.enabled` | `false` | `application.use.ldap.login.supoort` |
| `yona.ldap.host`/`.port`/`.protocol` | `127.0.0.1`/`389`/`ldap` | `ldap.host`/`.port`/`.protocol` |
| `yona.ldap.base-dn`/`.dn-postfix` | 빈 문자열 | `ldap.baseDN`/`distinguishedNamePostfix` |
| `yona.ldap.login-property`/`.display-name-property`/`.user-name-property`/`.email-property`/`.department-property`/`.english-name-property` | 각각 legacy 기본값과 동일(`sAMAccountName`/`displayName`/`CN`/`mail`/`department`/빈값) | `ldap.loginProperty` 등 |
| `yona.ldap.use-email-base-login`/`.fallback-to-local-login` | `false`/`false` | `ldap.options.useEmailBaseLogin`/`fallbackToLocalLogin` |
| `yona.ldap.guest-login-id-prefix` | 빈 문자열 | 없음(LDAP 전용으로 신설, legacy의 범용 guest prefix와는 별개) |

legacy의 `application.use.social.login.name.sync`(소셜 로그인 시 이름 동기화)에 대응하는 설정 키는
아직 찾지 못했다.

## 알림 메일

자세한 예시는 [mail-settings.md](mail-settings.md) 참고.

| 설정 키 | 기본값 | legacy 대응 |
|---|---|---|
| `yona.notification.bymail.enabled` | `true` | `notification.bymail.enabled` |
| `yona.notification.bymail.interval-ms` | `60000`(60초) | `application.notification.bymail.interval` |
| `yona.notification.bymail.delay-ms` | `180000`(180초) | `application.notification.bymail.delay` |
| `yona.notification.bymail.recipient-limit` | `0`(제한 없음) | `application.notification.bymail.recipientLimit` |
| `yona.notification.bymail.hide-address` | `true` | `application.notification.bymail.hideAddress` |
| `yona.notification.bymail.allowed-domains` | 빈 문자열 | 없음(신규 — 알림 메일 발송을 허용할 도메인 제한) |
| `yona.notification.keep-days` | `-1`(무제한 보관) | `application.notification.keep-time` |

legacy의 `application.notification.draft-time`(비슷한 이벤트 병합 시간 창)은 코드 상수로
존재하나(P1-XX대 draft-time 병합 로직) 설정 키로 외부화되어 있는지는 이 문서 작성 시점에
확인하지 못했다.

## Mailbox (IMAP 수신)

`yona.mailbox.imap.*` — [mail-settings.md](mail-settings.md#메일로-이슈댓글-등록-imap-mailbox) 참고.

## 첨부파일 / 물리 저장소

| 설정 키 | 기본값 | legacy 대응 |
|---|---|---|
| `yona.upload.base-dir` | `${yona.data:data}/uploads` | `YONA_DATA/uploads` |
| `yona.git.base-dir` | `/tmp/yona/git` | `YONA_DATA/repo`(Git) |
| `yona.svn.base-dir` | `/tmp/yona/svn` | `YONA_DATA/repo`(SVN) |
| `yona.lfs.base-dir` | `/tmp/yona/lfs` | 없음(LFS는 legacy에 없던 기능) |
| `spring.servlet.multipart.max-file-size`/`.max-request-size` | **미설정 시 Spring Boot 기본값 1MB/10MB** | `application.maxFileSize`(기본 2GB) |

`max-file-size`는 legacy와 기본값 차이가 커서 실사용 전 반드시 올려야 한다 —
[troubleshooting.md](troubleshooting.md#첨부파일-업로드가-실패한다-413--maxuploadsizeexceededexception) 참고.

## GitHub 마이그레이션(Import)

| 설정 키 | 기본값 | legacy 대응 |
|---|---|---|
| `github.client.id` | 빈 문자열 | `github.client.id` |
| `github.client.secret` | 빈 문자열 | `github.client.secret` |
| `github.allow.migration` | `false` | `github.allow.migration` |

주의: 위 `github.*`는 `yona.*` 네임스페이스 아래가 아니라 최상위 키다(코드의 `MigrationService`
`@Value` 선언 그대로). 소셜 로그인용 `spring.security.oauth2.client.registration.github.*`와는
**별개의 GitHub OAuth App 등록**이 필요하다.

## Google Analytics / 소프트웨어 업데이트 체크

| 설정 키 | 기본값 | legacy 대응 |
|---|---|---|
| `yona.analytics.send-usage` | `false`(legacy는 기본 `true`였음 — 반대) | `application.send.yona.usage` |
| `yona.update.repository-url` | `https://github.com/yona-projects/yona.git` | `application.update.repositoryUrl` |
| `yona.update.current-version` | `1.15.0` | (legacy 버전 문자열 그대로 사용 중 — yona 자체 버전 체계로 갱신 필요할 수 있음) |
| `yona.update.interval-ms` | `21600000`(6시간) | `application.update.notification.interval` |

## 로깅

legacy는 `conf/application-logger.xml`(Logback)로 별도 관리했다. yona는 Spring Boot 표준
방식대로 `application.yml`의 `logging.level.*`(현재 `org.springframework.web: DEBUG`,
`org.hibernate: WARN`)로 관리하거나, 더 세밀한 제어가 필요하면 `logback-spring.xml`을
클래스패스에 추가한다(현재 저장소에는 없음 — 기본 Spring Boot 로깅 설정 그대로).

## 참고

- 이 문서는 실제 `@Value("${...}")` 선언과 `application.yml`을 검증해서 작성했지만, 코드가
  계속 바뀌는 프로젝트이므로 최종 근거는 항상 `src/main/resources/application.yml`과
  `grep -rn '@Value' src/main/kotlin`이다.
- Windows 환경에서 물리 저장소 경로를 재설정하는 방법은
  [README의 "운영 환경 설정"](../../README.md#운영-환경-설정-특히-windows) 참고.
