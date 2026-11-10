# JS 번들 빌드 (D-92)

`yona-layout.js`, `yona-common.js`를 만든다. v1.6의 Closure Compiler 스크립트를 vite로 바꾼 것이다.

```bash
build-tools/minify-js.sh          # npm ci 후 vite build, 결과는 static/assets/javascripts/
cd build-tools/js-bundle && npm test   # 번들 동등성 시험(jsdom)
```

- 입력 목록과 순서: `bundles.mjs`(v1.6과 같은 순서, 전역 스크립트라 순서가 의존 순서이다).
- 합치는 방식: `vite.config.mjs`의 `concatScripts` 플러그인이 파일을 모듈로 만들지 않고 이어 붙여 압축한다.
  그래서 `temporarySaveHandler` 같은 전역 이름이 템플릿에서 그대로 보인다.
- 출력 위치를 바꾸려면 환경 변수 `YONA_JS_OUT`을 쓴다(시험이 임시 폴더로 쓴다).
- Node 20 이상이 필요하다. 서버 빌드(Gradle)와는 연결하지 않았다. 입력을 고치면 직접 실행하고 번들을 커밋한다.
