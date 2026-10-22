---
title: 설정 키 대응표 (v1.6 application.conf -> v2 yona.*)
type: architecture
sources: [conf/application.conf.default, app/controllers/Application.java:30-45, app/utils/Config.java:44-47]
updated: 2026-10-22
---

# 설정 키 대응표

v1.6은 `conf/application.conf`의 `application.*` 키를 `play.Configuration.root()`로 읽었다. v2는 `dev.yona.config.YonaProperties`(`@ConfigurationProperties("yona")`)로 읽고 `application.yml`에서 `yona.*` 키로 설정한다. 기본값은 v1.6과 같다.

| v1.6 키 | v2 키 | 기본값 | 구현 상태 |
|---|---|---|---|
| `application.siteName` | `yona.site-name` | `Yona` | 사용 중 |
| `application.feedback.url` | `yona.feedback-url` | `https://github.com/yona-projects/yona/issues` | 사용 중 |
| `application.hide.project.listing` | `yona.hide-project-listing` | `false` | 사용 중 |
| `application.send.yona.usage` | `yona.send-yona-usage` | `true` | 사용 중 |
| `application.show.user.email` | `yona.show-user-email` | `true` | 정의만 함 |
| `application.navbar.custom.link.name` / `.url` | `yona.navbar-custom-link-name` / `-url` | 빈 문자열 | 사용 중 |
| `application.login.page.loginId.placeholder` | `yona.login-page-login-id-placeholder` | 빈 문자열 | 사용 중 |
| `application.login.page.password.placeholder` | `yona.login-page-password-placeholder` | 빈 문자열 | 사용 중 |
| `application.use.social.login.only` | `yona.social-login-only` | `false` | 화면에서만 사용(로그인 컨트롤러는 아직 반영 안 함) |
| `application.maxFileSize` | `yona.max-file-size` | `2147483454` | 사용 중 |
| `signup.require.admin.confirm` | `yona.signup.require-admin-confirm` | `false` | 사용 중 |
| (v2 신규) | `yona.gravatar.check` | `true` | 사용 중. 끄면 기동 시 Gravatar 접속 검사를 건너뛰고 기본 아바타를 쓴다 |

아직 옮기지 않은 v1.6 키는 해당 기능을 이식할 때 이 표에 추가한다.

`application.langs`는 설정이 아니라 `LocaleConfig.SUPPORTED_LOCALES`(`en-US, ko-KR, ja-JP, ru-RU, uz-UZ`)에 고정했다. 다른 언어를 지원하려면 메시지 파일과 이 목록을 함께 늘려야 하기 때문이다.
