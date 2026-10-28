---
title: 정적 자원, LESS 빌드, 다국어 메시지
type: web
sources: [conf/routes:28-31, conf/messages, conf/messages.ko-KR, conf/messages.ja-JP, conf/messages.ru-RU, conf/messages.uz-UZ, build.sbt:83-84, app/assets/stylesheets/yobi.less, minify-js.sh, app/views/layout.scala.html:29-36]
updated: 2026-10-22
---

# 정적 자원, LESS 빌드, 다국어 메시지

화면이 v1.6과 같으려면 템플릿이 참조하는 CSS, JS, 이미지 경로와 메시지 키가 그대로 유효해야 한다. 이 단계에서 그 바탕을 옮겼다.

## 정적 자원 (`/assets/`)

| v1.6 | v2 |
|---|---|
| `public/` 폴더를 `GET /assets/:file`로 제공 (`conf/routes:31`) | `public/`을 `src/main/resources/static/assets/`에 그대로 복사. Spring Boot의 기본 정적 자원 처리가 `/assets/` 경로로 제공한다 |
| `GET /favicon.ico`를 `images/favicon.ico`에 연결 (`conf/routes:30`) | `WebConfig`가 `/favicon.ico`를 `static/assets/images/`에 연결 |

- `public/compiler.jar`(6.5MB)는 JS 번들을 만드는 Closure Compiler 도구라 처음에는 복사하지 않았다. 그러나 v1.6이 `/assets/compiler.jar`로 그대로 내려 주므로 같은 주소와 같은 본문으로 제공한다(v1.6 정적 자원 755개 대조, `tools/v16-diff/assets.py`).
- JS 번들(`yona-layout.js`, `yona-common.js`, `yona-lib.js`)은 v1.6도 `minify-js.sh`로 만든 결과물을 `public/javascripts/`에 저장소에 포함했으므로 그대로 복사했다. 번들 재생성 방법은 v1.6 `minify-js.sh`를 따른다.
- 검증: `StaticAssetsTest` (레이아웃이 참조하는 CSS/JS, favicon, 404, 콘텐츠 형식).

## CSS는 LESS에서 컴파일한다

v1.6은 `public/`에 CSS 결과물을 두지 않고, sbt-less가 빌드할 때 `app/assets/stylesheets/*.less`에서 `yobi.css`, `usermenu.css`, `filetype.css`를 만든다 (`build.sbt:83-84`: `_`로 시작하는 파일은 제외하는 `includeFilter`/`excludeFilter`). `less/font` 아래의 `colours`, `fonts`, `icons`도 같은 방식으로 따로 컴파일되어 `/assets/stylesheets/less/font/*.css`와 `.css.map`으로 내려가므로, 같은 파일들을 하위 폴더 기준 소스 맵으로 만든다.

v2도 같은 방식이다. 선생님께서 "Gradle 빌드에서 lessc 실행"을 선택하셨다(2026-10-22).

- `build-tools/less/package.json`이 Less **1.7.5**를 고정한다. sbt-less 1.0.4가 쓰던 Less 1.x 계열에 맞춘 선택이다.
- Gradle 작업 `installLess`(`npm ci`) -> `compileLess`가 `build/generated/less/static/assets/stylesheets/`에 CSS를 만들고 `processResources`가 클래스패스에 포함한다. 생성된 CSS는 저장소에 두지 않는다.
- 빌드 환경에 Node와 npm이 필요하다.
- 소스는 v1.6의 `app/assets/stylesheets/`를 그대로 쓴다.

참고: `~/yona-convert/legacy_yobi.css`(v1.6 계열 서버가 만든 CSS)와 비교하면 약 116행이 다르다(`.pin-*` 규칙 등). 그 파일은 이 저장소의 `yobi.less`와 다른 시점의 소스에서 만들어진 것으로 보여 기준으로 삼지 않았다. 이 저장소의 LESS를 Less 1.7.5로 컴파일한 결과를 기준으로 한다.

## 다국어 메시지

`conf/messages`(영어 기본), `messages.ko-KR`, `messages.ja-JP`, `messages.ru-RU`, `messages.uz-UZ`를 `src/main/resources/messages.properties`, `messages_ko_KR.properties` 등으로 복사했다. 파일 내용은 바꾸지 않았다.

| 항목 | Play 2.3 | Spring 설정 |
|---|---|---|
| 형식 | 인자가 없는 메시지도 `MessageFormat`으로 해석한다. 작은따옴표는 `''`로 쓴다 | `spring.messages.always-use-message-format=true` |
| 번역이 없을 때 | 기본 메시지(영어) | `spring.messages.fallback-to-system-locale=false` (끄지 않으면 서버의 시스템 로캘로 대체된다) |
| 인코딩 | UTF-8 | `spring.messages.encoding=UTF-8` |
| 파일 이름 | `messages.ko-KR` | `messages_ko_KR.properties` |

검증: `MessagesTest` (영어/한국어/일본어/러시아어/우즈베크어 로딩, 인자 치환, `''` 해석, 대체 동작).

## 아직 하지 않은 것

- Play의 언어 결정 규칙(쿠키, `Accept-Language`, 사용자 `lang`)은 아직 옮기지 않았다.
- 메시지 키가 v1.6 소스의 모든 템플릿 참조와 일치하는지 전수 검사는 템플릿 이식 단계에서 한다.
