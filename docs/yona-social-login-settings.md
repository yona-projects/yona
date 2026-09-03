Set up social sign-in
----

Ported from legacy Yona's `docs/yona-social-login-settings.md`, adapted for yona.

Legacy configured Github/Google OAuth in `conf/social-login.conf` (plus
`include "social-login.conf"` at the bottom of `application.conf`). yona uses Spring Security
OAuth2's standard client registration instead — `application.yml`'s
`spring.security.oauth2.client.registration.*`.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: your_real_github_client_id
            client-secret: your_real_github_client_secret
            scope: user:email,read:user
          google:
            client-id: your_real_google_client_id
            client-secret: your_real_google_client_secret
            scope: profile,email
```

The shipped defaults (`dummy-client-id`/`dummy-client-secret`) are development placeholders —
replace them with real values issued by each provider (a Github OAuth App, a Google Cloud OAuth
client) to actually use social login.

**Unlike legacy, there's no allowlist setting yet** (`application.social.login.support`) to turn
individual providers on/off — if a provider's client-id/secret is registered, it's always shown
as a login option. To disable one, remove its registration block entirely for now.

If you're on a public IP, restricting self-signup/login is recommended.

To send a welcome mail to newly registered social-login users, configure
[`yona-mail-settings.md`](yona-mail-settings.md)'s `spring.mail.*` first (this replaces legacy's
`play-easymail` block).

GitHub project migration (Import) uses a separate `github.client.id`/`github.client.secret`/
`github.allow.migration` — **not** the OAuth2 login registration above. See
[README's "Migration"](../README.md#migration).
