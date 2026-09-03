# 소셜 로그인 설정

legacy Yona의 `docs/ko/yona-social-login-settings.md`를 yona 기준으로 갱신.

legacy는 `conf/social-login.conf`(및 `application.conf` 맨 아래 `include "social-login.conf"`)로
Github/Google OAuth를 설정했다. yona는 Spring Security OAuth2의 표준 클라이언트 등록 방식을
쓴다 — `application.yml`의 `spring.security.oauth2.client.registration.*`.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: 실제_github_client_id
            client-secret: 실제_github_client_secret
            scope: user:email,read:user
          google:
            client-id: 실제_google_client_id
            client-secret: 실제_google_client_secret
            scope: profile,email
```

기본값(`dummy-client-id`/`dummy-client-secret`)은 개발용 자리표시자다 — 실제 소셜 로그인을
쓰려면 각 provider(Github OAuth App, Google Cloud OAuth 클라이언트)에서 발급받은 값으로 반드시
덮어써야 한다.

**legacy와 달리 provider 목록 자체를 껐다 켰다 하는 `application.social.login.support` 같은
허용 목록 설정은 yona에 아직 없다** — Github/Google 두 provider의 client-id/secret이 등록되어
있으면 둘 다 항상 로그인 옵션으로 노출된다. 특정 provider만 막고 싶다면 현재로선 해당
provider의 등록 자체(`spring.security.oauth2.client.registration.<provider>`)를 지우는 방법뿐이다.

새 가입/로그인 시 안내 메일을 보내려면 [mail-settings.md](mail-settings.md)의 `spring.mail.*`
설정이 먼저 되어 있어야 한다(legacy의 `play-easymail` 블록에 대응하는 부분).

GitHub 프로젝트 마이그레이션(Import) 기능을 쓰려면 별도로 `github.client.id` /
`github.client.secret` / `github.allow.migration`을 설정해야 한다 — 위 OAuth2 로그인용
client-id/secret과는 **별개의 설정**이다. 자세한 내용은
[README의 "마이그레이션"](../../README.md#마이그레이션) 참고.
