---
title: 설정 키 대응표 (v1.6 application.conf -> v2 yona.*)
type: architecture
sources: [conf/application.conf.default, app/controllers/Application.java:30-45, app/utils/Config.java:44-47]
updated: 2026-10-28
---

# 설정 키 대응표

v1.6은 `conf/application.conf`의 `application.*` 키를 `play.Configuration.root()`로 읽었다. v2는 `dev.yona.config.YonaProperties`(`@ConfigurationProperties("yona")`)로 읽고 `application.yml`에서 `yona.*` 키로 설정한다. 기본값은 v1.6과 같다.

| v1.6 키 | v2 키 | 기본값 | 구현 상태 |
|---|---|---|---|
| `application.siteName` | `yona.site-name` | `Yona` | 사용 중 |
| `application.feedback.url` | `yona.feedback-url` | `https://github.com/yona-projects/yona/issues` | 사용 중 |
| `application.hide.project.listing` | `yona.hide-project-listing` | `false` | 사용 중 |
| `application.send.yona.usage` | `yona.send-yona-usage` | `true` | 사용 중. 태그는 v1.6의 UA에서 GA4로 바꿨다(X-21, `yona.analytics.*`) |
| `application.show.user.email` | `yona.show-user-email` | `true` | 사용 중(투표자 이메일 복사 단추, 사용자 화면의 이메일) |
| `application.navbar.custom.link.name` / `.url` | `yona.navbar-custom-link-name` / `-url` | 빈 문자열 | 사용 중 |
| `application.login.page.loginId.placeholder` | `yona.login-page-login-id-placeholder` | 빈 문자열 | 사용 중 |
| `application.login.page.password.placeholder` | `yona.login-page-password-placeholder` | 빈 문자열 | 사용 중 |
| `-Dyona.data` (JVM 시스템 속성) | `yona.data-dir` (시스템 속성 `yona.data`도 계속 인식) | 빈 문자열(작업 디렉터리) | 사용 중(저장소 위치). D-16 |
| `project.default.scope.when.create` | `yona.project-default-scope` | `public` | 사용 중(프로젝트 생성 폼) |
| `project.default.menus.when.create` | `yona.project-default-menus` | `code, issue, pullRequest, review, milestone, board` | 사용 중(프로젝트 생성 폼) |
| `application.displayPrivateRepositories` | `yona.display-private-repositories` | `false` | 사용 중(프로젝트 목록) |
| `application.context` | `yona.application-context` | 빈 문자열 | 사용 중(링크 접두어, README 상대 경로 변환, 그리고 서버의 컨텍스트 경로. `server.servlet.context-path`를 따로 정하지 않았으면 이 값이 컨텍스트 경로가 된다 `ApplicationContextCustomizer`) |
| `application.noreferrer` | `yona.noreferrer` | `true`(v1.6 기본 설정 파일의 값. 코드의 기본값은 `false`이나 설치는 기본 파일에서 출발한다) | 사용 중(마크다운 외부 링크, 알림 메일 링크) |
| `application.hostname` | `yona.hostname` | 빈 문자열(요청의 Host 헤더) | 사용 중(저장소 주소) |
| `application.port` | `yona.port` | 없음 | 사용 중(80이면 주소에서 생략) |
| `application.scheme` | `yona.scheme` | 빈 문자열(`http`; 요청의 https 여부는 보지 않는다. HTTPS 뒤에서는 `https`로 설정) | 사용 중 |
| `application.allowsAnonymousAccess` | `yona.allows-anonymous-access` | `true` | 사용 중 |
| `application.use.social.login.only` | `yona.social-login-only` | `false` | 사용 중(로그인, 로그인 대화상자, 가입 화면과 `LoginController`의 POST 로그인) |
| `application.maxFileSize` | `yona.max-file-size` | `2147483454` | 사용 중 |
| `signup.require.admin.confirm` | `yona.signup.require-admin-confirm` | `false` | 사용 중 |
| `notification.bymail.enabled` | `yona.notification-mail-enabled` | `true` | 사용 중(알림 메일 스케줄러) |
| `application.notification.bymail.hideAddress` | `yona.notification-mail-hide-address` | `true` | 사용 중 |
| `application.notification.bymail.recipientLimit` | `yona.notification-mail-recipient-limit` | `100`(v1.6 기본 설정 파일의 값. 코드의 기본값 `0`은 제한 없음) | 사용 중 |
| `application.notification.bymail.initdelay` / `.interval` / `.delay` | `yona.notification-mail-init-delay` / `-interval` / `-delay` (밀리초) | `5000` / `60000` / `180000` | 사용 중 |
| `application.notification.keep-time` | `yona.notification-keep-time` (일) | `-1` (지우지 않음) | 사용 중(알림 정리) |
| `application.notification.draft-time` | `yona.notification-draft-time` (밀리초) | `30000` | 사용 중(알림 합치기) |
| (없음, Akka 액터) | `yona.background-async` | `true` | 사용 중(풀 리퀘스트 병합 검사와 푸시 뒤 일을 별개의 스레드에서 실행. 테스트는 끔) |
| `slack.<이벤트 종류>` | `yona.slack.<이벤트 종류>` (예: `yona.slack.NEW_ISSUE`) | 없음 | 사용 중(슬랙 웹훅 첨부의 색) |
| `play-easymail.from.email` / `.name` | `yona.easymail.from-email` / `from-name` | 빈 문자열(smtp 주소) / `yona-no-reply` | 사용 중(가입 안내 메일). A-58 |
| `smtp.user` / `smtp.domain` | `yona.smtp.user` / `yona.smtp.domain` | 빈 문자열 | 사용 중(보내는 주소). 접속 정보(`smtp.host` 등)는 `spring.mail.*`로 옮겼다 |
| `application.codeBrowser.viewer.maxFileSize` | `yona.code-browser-max-file-size` | `1048576` | 사용 중(코드 브라우저) |
| (v2 신규) | `yona.gravatar.check` | `true` | 사용 중. 끄면 기동 시 Gravatar 접속 검사를 건너뛰고 기본 아바타를 쓴다 |
| (v2 신규) | `yona.analytics.measurement-id` | `G-CKTN17HLPP` | 사용 중(X-21). `yona.send-yona-usage`가 켜져 있고 측정 ID가 있으면 GA4 태그를 낸다 |
| (v2 신규) | `yona.api-token-failure-limit` | `5` | 사용 중(X-5). 같은 주소에서 토큰(Git, SVN 비밀번호 포함) 인증에 연달아 실패하면 막는 횟수이다. 0 이하이면 제한하지 않는다 |
| (v2 신규) | `yona.api-token-block-minutes` | `15` | 사용 중(X-5). 실패 한도를 넘은 주소를 막는 시간(분)이다 |
| (v2 신규) | `yona.api-token-max-days` | `0` | 사용 중(X-5). 새 토큰의 최대 수명(일)이다. 0이면 제한이 없고 "만료 없음"도 고를 수 있다 |
| (v2 신규) | `yona.git-http-password-auth` | `true` | 사용 중(X-5). Git, SVN의 HTTP 접근이 비밀번호도 받는지이다. `false`이면 토큰만 받는다 |
| (v2 신규) | `yona.git-default-branch` | `main` | 사용 중(X-22). 새 Git 저장소의 `HEAD` 브랜치이다. v1.6은 `master`였다 |

아직 옮기지 않은 v1.6 키는 해당 기능을 이식할 때 이 표에 추가한다.

`application.langs`는 설정 `yona.langs`(쉼표 목록, 기본 `en-US, ko-KR, ja-JP, ru-RU, uz-UZ`)로 읽는다. `Accept-Language`에 가장 알맞은 언어를 고르고 맞는 것이 없으면 첫 번째를 쓰며, 요청 밖의 문구(알림 등)도 첫 번째 언어이다. 처음에는 이 목록을 코드에 고정했으나 v1.6 시험(`PasswordResetAppTest`)이 목록을 `ko-KR` 하나로 줄여 한국어 응답을 기대하는 운영 방식이 있어 2026-10-27에 설정으로 되돌렸다(A-134).

## 기간 설정의 형식과 기본값 (2026-10-27)

v1.6은 기간 설정을 `getMilliseconds`로 읽어 `60s`, `5m`, `6h`, `1d` 같은 문자열과 단위 없는 숫자(밀리초)를 모두 받는다. 기본 설정 파일도 `60s`, `180s`, `30s`, `6h`를 쓴다. v2도 같은 형식을 받는다(`java.time.Duration`, 단위 없는 숫자는 밀리초). 대상은 `notification-mail-init-delay`, `notification-mail-interval`, `notification-mail-delay`, `notification-draft-time`, `issue-event-draft-time`, `mailbox-polling-interval`, `temporary-files-keep-up-time`, `update.init-delay`, `update.interval`이다. `@Scheduled` 값은 `schedulingDelays` 빈이 밀리초로 바꿔 준다.

설정이 없을 때의 기본값은 v1.6 기본 설정 파일의 값을 따른다(`noreferrer` true, 수신자 제한 100, 업데이트 확인 주기 6시간, 업데이트 확인 저장소 `https://github.com/yona-projects/yona`, `imap.use` 끔, `imap.ssl` 켬, `imap.folder` `Inbox`, LDAP 예시 값). v1.6은 기본 파일을 복사해 쓰는 설치가 표준이기 때문이다(A-136, A-174).
