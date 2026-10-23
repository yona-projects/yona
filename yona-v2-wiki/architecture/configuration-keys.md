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
| `-Dyona.data` (JVM 시스템 속성) | `yona.data-dir` (시스템 속성 `yona.data`도 계속 인식) | 빈 문자열(작업 디렉터리) | 사용 중(저장소 위치). D-16 |
| `project.default.scope.when.create` | `yona.project-default-scope` | `public` | 사용 중(프로젝트 생성 폼) |
| `project.default.menus.when.create` | `yona.project-default-menus` | `code, issue, pullRequest, review, milestone, board` | 사용 중(프로젝트 생성 폼) |
| `application.displayPrivateRepositories` | `yona.display-private-repositories` | `false` | 사용 중(프로젝트 목록) |
| `application.context` | `yona.application-context` | 빈 문자열 | 사용 중(README 상대 경로 변환) |
| `application.noreferrer` | `yona.noreferrer` | `false`(v1.6 기본 설정 파일 예시는 `true`) | 사용 중(마크다운 외부 링크) |
| `application.hostname` | `yona.hostname` | 빈 문자열(요청의 Host 헤더) | 사용 중(저장소 주소) |
| `application.port` | `yona.port` | 없음 | 사용 중(80이면 주소에서 생략) |
| `application.scheme` | `yona.scheme` | 빈 문자열(요청의 스킴) | 사용 중 |
| `application.allowsAnonymousAccess` | `yona.allows-anonymous-access` | `true` | 사용 중 |
| `application.use.social.login.only` | `yona.social-login-only` | `false` | 화면에서만 사용(로그인 컨트롤러는 아직 반영 안 함) |
| `application.maxFileSize` | `yona.max-file-size` | `2147483454` | 사용 중 |
| `signup.require.admin.confirm` | `yona.signup.require-admin-confirm` | `false` | 사용 중 |
| `notification.bymail.enabled` | `yona.notification-mail-enabled` | `true` | 사용 중(알림 메일 스케줄러) |
| `application.notification.bymail.hideAddress` | `yona.notification-mail-hide-address` | `true` | 사용 중 |
| `application.notification.bymail.recipientLimit` | `yona.notification-mail-recipient-limit` | `0`(제한 없음) | 사용 중 |
| `application.notification.bymail.initdelay` / `.interval` / `.delay` | `yona.notification-mail-init-delay` / `-interval` / `-delay` (밀리초) | `5000` / `60000` / `180000` | 사용 중 |
| `application.notification.keep-time` | `yona.notification-keep-time` (일) | `-1` (지우지 않음) | 사용 중(알림 정리) |
| `application.notification.draft-time` | `yona.notification-draft-time` (밀리초) | `30000` | 사용 중(알림 합치기) |
| (없음, Akka 액터) | `yona.background-async` | `true` | 사용 중(풀 리퀘스트 병합 검사와 푸시 뒤 일을 별개의 스레드에서 실행. 테스트는 끔) |
| `slack.<이벤트 종류>` | `yona.slack.<이벤트 종류>` (예: `yona.slack.NEW_ISSUE`) | 없음 | 사용 중(슬랙 웹훅 첨부의 색) |
| `play-easymail.from.email` / `.name` | `yona.easymail.from-email` / `from-name` | 빈 문자열(smtp 주소) / `yona-no-reply` | 사용 중(가입 안내 메일). A-58 |
| `smtp.user` / `smtp.domain` | `yona.smtp.user` / `yona.smtp.domain` | 빈 문자열 | 사용 중(보내는 주소). 접속 정보(`smtp.host` 등)는 `spring.mail.*`로 옮겼다 |
| `application.codeBrowser.viewer.maxFileSize` | `yona.code-browser-max-file-size` | `1048576` | 사용 중(코드 브라우저) |
| (v2 신규) | `yona.gravatar.check` | `true` | 사용 중. 끄면 기동 시 Gravatar 접속 검사를 건너뛰고 기본 아바타를 쓴다 |

아직 옮기지 않은 v1.6 키는 해당 기능을 이식할 때 이 표에 추가한다.

`application.langs`는 설정이 아니라 `LocaleConfig.SUPPORTED_LOCALES`(`en-US, ko-KR, ja-JP, ru-RU, uz-UZ`)에 고정했다. 다른 언어를 지원하려면 메시지 파일과 이 목록을 함께 늘려야 하기 때문이다.
