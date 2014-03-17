Yobi 는 현재 요청의 사용자를 얻기 위해 session, token, context 정보를 활용한다.

session
-------

- 로그인시 생성되어 브라우저 종료 시점까지 보관되는 cookie 이다.
- 사용자가 로그아웃 하면 삭제된다.
- userId(고유ID), logindId(로그인ID), userName(이름) 을 key 로 사용해서 `encoded data` 를 만들고 이를 하나의 cookie value 로 저장한다.
- `encoded data` 는 각각의 key-value 쌍을 `key:value` 형태의 string 으로 만들고 이를 url encode 한 뒤, `%00` 으로 연결한 값이다.
    - ex) userId%3A1%00loginId%3Atest%00userName%3Atestname
- `PLAY_SESSION` 이란 이름으로 저장되며 값은 `hash`-`encoded data` 형태로 구성된다.
- `hash` 는 [Crypto#sign](http://www.playframework.com/documentation/2.1.x/api/scala/index.html#play.api.libs.Crypto$) 을 이용해서 얻는다. `message` 로 `encoded data` 를, `key` 로는 `application.conf` 에 정의되어 있는 `application.secret` 값을 사용한다.
- 위의 data encoding 과 hash 값을 얻는 내용은 play framework session 의 내부 동작을 설명한 것이다. Yobi 에서는 [play framework java session API](http://www.playframework.com/documentation/2.1.x/api/java/play/mvc/Http.Session.html) 를 사용한다.
- 고유ID 에 숫자 값을 가지고 있고 이에 대응되는 사용자 정보가 존재한다면 이를 현재 사용자로 간주한다.

token
-----

- 로그인 유지 기능을 선택하고 로그인시 생성되어 30일간 보관되는 cookie 이다.
- 사용자가 로그아웃 하면 삭제된다.
- 로그인 유지를 위한 정보(로그인ID, hash 값)를 저장한다.
- `yobi.token` 이란 이름을 사용하며 값을 `로그인ID:hash` 형태로 저장한다.
- `hash` 는 [Sha256Hash#toBase64()](http://shiro.apache.org/static/1.2.1/apidocs/org/apache/shiro/crypto/hash/Sha256Hash.html) 을 이용해서 얻는다. `source` 로 plain password 를, `salt` 로 사용자별 random 생성된 값을, `hashIterations` 으로 1024 상수 값을 사용한다.
- `salt` 값은 [SecureRandomNumberGenerator#nextBytes()](http://shiro.apache.org/static/1.2.1/apidocs/org/apache/shiro/crypto/SecureRandomNumberGenerator.html) 를 이용해서 생성한다.
- cookie 가 생성될 때 `Expires` 값으로 30일 이후의 날짜가 설정된다.
- 로그인ID 와 hash 값에 대응되는 사용자 정보가 존재한다면 이를 현재 사용자로 간주한다.

context
-------

- play framework 에서 제공되는 하나의 요청에 관련된 정보들이 담겨 있는 공간이다.
- [args](http://www.playframework.com/documentation/2.1.x/api/java/play/mvc/Http.Context.html#args) 라는 Map 객체를 이용해서 custom data 를 저장하고 꺼내올 수 있다.
- token 정보를 이용해 생성한 사용자 객체를 cache 하는 용도로 사용한다.

`UserApp#initTokenUser()`
-------------------------

- token 정보를 이용해서 사용자 객체를 생성한다.
- 생성된 객체를 context 에 저장한다.
- 사용자 객체가 익명(`User#anonymous`)이 아니고, session 정보가 없을 경우 session 정보를 생성한다.
- `Gloabl#onRequest` 에서 수행된다.

`UserApp#currentUser()`
-----------------------

- session 정보를 이용해서 사용자 객체를 생성한다.
- 생성된 객체가 익명이 아닐 경우 이를 현재 사용자로 판단한다.
- 사용자 객체를 생성할 수 없거나 생성된 객체가 익명일 경우 context 에 저장된 정보를 현재 사용자로 판단한다.
    - context 에 저장된 정보가 없을 경우 `UserApp#initTokenUser()` 를 호출하여 객체 생성/저장 후 사용한다.

notes
-----

- `UserApp#currentUser()` 에서 session 정보가 없을 경우 context, token 정보까지 활용하게 되는데 controller 에 action composition 이 적용되어 있을 경우 `UserApp#initTokenUser()` 보다 먼저 실행 될 수 있기 때문이다. (아래 action composition 실행 순서를 참고)
- context 를 사용해서 `UserApp#initTokenUser()` 의 결과를 저장해 두는건 하나의 요청 내에서 `UserApp#currentUser()` 의 결과가 token 정보를 중복활용하는 것을 방지하기 위해서이다.

action composition 실행 순서
---------------------------

1. action composition
2. `Global#onRequest`
3. controller method
