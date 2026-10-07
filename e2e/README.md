# yona E2E (Playwright)

yona의 모든 화면·버튼·입력을 브라우저로 실제 클릭/입력해 검증하는 Playwright 스위트. 커버리지
현황은 [`matrix.md`](./matrix.md) 참고 — 새 스펙을 추가하면 그 표도 같이 갱신할 것.

## 왜 이런 구조인가

- **스펙끼리 서로 의존한다.** 로그인 → 프로젝트 생성 → 이슈/PR/위키/게시글 생성처럼, 화면에
  도달하려면 선행 상태가 필요한 경우가 대부분이다(예: 이슈 상세 화면은 이슈 번호가 이미
  있어야 함). 매번 각 스펙이 자기 몫의 픽스처를 처음부터 다시 만드는 대신, `support/seed-store.ts`
  를 거쳐 앞선 스펙이 만든 결과(프로젝트 owner/name, 이슈 번호 등)를 뒤 스펙이 읽어 쓴다 —
  `.seed/seed.json` 파일 하나가 전체 의존관계 그래프의 실체다.
- **로그인 상태도 마찬가지다.** `specs/global.setup.ts`가 최초 1회 관리자 계정을 만들고
  로그인해 `.auth/admin.json`에 세션을 저장하면, 나머지 모든 스펙은 `playwright.config.ts`의
  `storageState` 설정으로 그 세션을 그대로 재사용한다(매 스펙마다 새로 로그인하지 않음).
  로그아웃 상태가 필요한 스펙(회원가입/로그인 자체)은 `test.use({ storageState: { cookies: [], origins: [] } })`
  로 그 파일 하나만 오버라이드한다.
- **폴더 번호(`00-bootstrap` ~ `15-misc`)가 곧 의존 순서다.** Playwright는 기본적으로
  `testDir` 안의 파일을 알파벳 순으로 실행하고(`fullyParallel: false`, `workers: 1`로 고정
  — 프로젝트/이슈 번호가 순차 증가하는 화면들이라 병렬 실행 시 서로 경합한다), 뒷 번호
  폴더의 스펙은 앞 번호 폴더가 만든 seed 값을 전제로 작성한다.

## 리로드 직후에는 먼저 문서가 준비되기를 기다린다 (`support/ready.ts`)

yona의 많은 화면은 클릭 핸들러를 `DOMContentLoaded` 리스너에서 붙이고, 레이아웃은 `type="module"` 위젯 번들 여러 개를
함께 로드한다. 모듈 스크립트는 `DOMContentLoaded`를 늦추므로, 느리거나 콜드한 환경(특히 CI)에서는 서버가 그린 버튼이
이미 보이고 클릭도 되는데 아직 핸들러가 없는 구간이 생긴다. 그 구간의 클릭은 아무 일도 하지 않는다(해시만 바뀔 수도
있다). Playwright의 actionability 검사는 이를 볼 수 없어서, 로컬에서는 통과하다가 CI에서 매번 다른 테스트가 실패한다.

- `documentReady(page)` — 새 문서가 화면에 떴음을 단언으로 확인한 **직후**, 그 문서의 버튼을 누르기 **전에** 호출한다.
- `afterReload(page, action)` / `clickAndReload(page, locator)` — 핸들러가 `fetch` 후 `location.reload()`를 하는 동작에
  쓴다. **새** 문서의 `load`까지 기다린다.
- 쓰지 말 것: `Promise.all([page.waitForLoadState('load'), page.click(...)])`. 이미 로드된 *현재* 문서의 `load`를
  기다리므로 즉시 통과해 아무것도 보호하지 못한다. `waitForLoadState('networkidle')`도 리로드가 시작되기 전에
  지나가 버릴 수 있다.

새 테스트가 이 경쟁에 취약한지 확인하려면 위젯 모듈 응답(`/lib/yona-vue-widgets/*`)만 1초쯤 지연시키는 프록시를 앱 앞에
두고 같은 스펙을 돌려 본다(지연 없이는 통과하고 지연 시 실패하면 이 경쟁이다).

## 실행 방법

1. yona를 H2 프로파일로 띄운다(Docker/Testcontainers 불필요, 기본 포트 8080):
   ```bash
   cd .. && ./gradlew bootRun --args='--spring.profiles.active=h2'
   ```
   최초 실행 시 DB에 유저가 0명이면 모든 요청이 `/bootstrap-setup`으로 강제 리다이렉트된다 —
   `global.setup.ts`가 이 화면까지 자동으로 처리하므로 수동으로 건드릴 필요 없다.
2. 의존성 설치(최초 1회):
   ```bash
   npm install
   npx playwright install chromium
   ```
3. 전체 스위트 실행:
   ```bash
   npm test
   ```
   특정 영역만: `npx playwright test specs/06-issue`
   브라우저 보면서: `npm run test:headed`
   결과 리포트: `npm run report`

### 완전히 새로 시작하려면

`.auth/`, `.seed/`를 지우고 yona의 H2 데이터 파일(`../data/h2/yona.mv.db` 등, `application.yml`의
h2 프로파일 설정 참고)도 지운 뒤 yona를 재기동해야 `/bootstrap-setup`부터 다시 탄다. 데이터
파일을 안 지우면 이미 admin 계정이 있으므로 `global.setup.ts`가 로그인만 하고 넘어간다(안전 —
매번 지울 필요는 없다).

## 알려진 한계 / 스코프 밖

- WebAuthn(패스키)은 실제 인증기 없이는 브라우저만으로 끝까지 완주할 수 없다 — 화면 진입/폼
  존재까지만 검증한다.
- SSO(SAML/OIDC)는 실제 IdP 연동이 필요해 설정 화면 자체만 검증하고 실제 로그인 라운드트립은
  다루지 않는다.
- 관리자 `massmail` 화면은 실제 메일 발송 사이드이펙트를 피하려 수신자 없는 드라이런 형태로만
  검증한다.
- 이슈 제목 공백 허용처럼, 테스트 도중 실제로 발견한 yona의 입력 검증 갭은 "버그니까 통과하게
  고치기"가 아니라 **현재 동작을 그대로 문서화**하는 방향으로 스펙을 작성했다(테스트가 몰래
  깨진 가정 위에 서 있는 것보다, 실제 동작을 정직하게 기록하는 게 낫다) — 코드 자체를 고칠지는
  별도 논의 대상이다.
