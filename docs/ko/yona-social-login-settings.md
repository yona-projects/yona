소셜 로그인 설정
----
- Yona는 v1.3 기준으로 Github과 Gmail OAuth 로그인을 지원합니다.
- 해당 설정은 conf 디렉터리의 social-login.conf 파일에서 설정합니다.
   - application.conf 파일 맨 아래에 `include "social-login.conf"` 부분이 추가되어 있는지 확인 해 주세요
- 만약 소셜로그인 설정을 제거하거나 제한하고 싶다면 conf 폴더의 application.social.login.support 항목을 수정하세요
   - 상세 설정들은 [application.conf 설명](application-conf-desc.md) 파일을 참고해주세요.
- 공개망(Public IP Network)에서 Yona 서버를 운영한다면 자체 가입 및 로그인은 제한하는 것을 권장합니다.
- 소셜 로그인용 최초 로그인시에 만약 기존 가입된 계정의 이메일 유저가 아니라면 자동가입이 일어난다면 자동 생성된 암호등의 정보가 포함된 메일을 발송합니다. 

관련 설정
---
conf 폴더의 application.conf 파일 및 social-login.conf 파일을 통해 설정이 가능합니다.

- 오직 소셜로그인(Github/Gmail)을 통한 가입/로그인만으로 제한 (자체 계정 생성 및 로그인 금지)
    - application.use.social.login.only = true
- 지원 소셜로그인 제공자 설정 가능
    - application.social.login.support = "github, google"
- application.conf 끝 부분의 메일 play-easymail 설정을 해 놓으면 이때 새로 가입/로그인된 유저에게 안내 메일이 발송됩니다.

```
play-easymail {
  from {
    # Mailing from address
    email="you@gmail.com"

    # Mailing name
    name="Your Name"

    # Seconds between sending mail through Akka (defaults to 1)
    # delay=1
  }
}
```