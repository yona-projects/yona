# yona 모바일/데스크톱 클라이언트 설계 문서

- 대상 백엔드: `yona` (Kotlin/Spring Boot, REST API `/api/v1/**`, PAT + OAuth2 이중 인증)
- 대상 플랫폼: Android, iOS, SailfishOS
- 상태: 확정 (섹션 8의 전제조건 전부 확정 완료, 코드 스캐폴딩은 아직 없음)

---

## 1. 요약

- Android/iOS는 **React 또는 Vue 기반 반응형 PWA 하나**로 커버 가능하다는 원안의 방향은 유효하다. iOS는 **App Store 공개 배포가 확정 요구사항**(섹션 8-1)이라 순수 PWA로는 부족하므로, **Capacitor로 감싼 네이티브 셸을 App Store에 배포**하는 경로를 확정한다 — 푸시 알림은 필수 요구사항이 아니다(섹션 8-2).
- **SailfishOS는 PWA/하이브리드 셸로 답이 나오지 않는다.** Sailfish 브라우저는 PWA 표준(서비스워커 등)을 지원하지 않고, Capacitor 등 하이브리드 래퍼도 SailfishOS를 공식 플랫폼으로 지원하지 않는다. 이는 사용자가 이미 확정한 전제이며, 본 문서는 이를 그대로 반영해 **SailfishOS용 완전히 별도의 네이티브 Qt/QML(Silica UI) 앱**을 독립 프로젝트로 설계한다. 실사용자가 개발자 본인 1명뿐이라는 점도 확정됐으므로(섹션 8-4), 공식 스토어(Harbour) 심사 없이 개인 사이드로드로 배포 범위를 축소했다. 즉 "PWA 하나로 3플랫폼 전부"라는 원래 구상은 SailfishOS에 대해서는 성립하지 않는다 — 이 문서가 사용자 원안에서 정정하는 핵심 지점이다.
- 웹 코드베이스(React/Vue)는 Android(브라우저 설치형 PWA + 선택적 TWA)와 iOS(Capacitor 셸)까지만 담당하고, SailfishOS 네이티브 앱과는 **코드 공유가 없다** — 공유되는 것은 yona 백엔드의 REST API 계약(엔드포인트, 인증 방식, 스코프 모델)뿐이다.
- 프레임워크는 **Vue 3(Composition API) + Vite**를 권고한다. 근거: 기존 프론트가 jQuery+Thymeleaf 템플릿 기반이라 팀의 SPA 프레임워크 경험이 적고, Vue의 템플릿 문법이 Thymeleaf에서의 전환 학습곡선이 완만하며, Pinia/Vue Router가 공식 제공되어 의사결정 비용이 적고, PWA 빌드 산출물이 iOS Safari의 타이트한 저장공간 정책에 더 유리하다.
- yona 백엔드는 OAuth2 Authorization Server(PKCE 지원 가능)와 Fine-grained PAT을 모두 지원한다(`ResourceServerConfig.kt`, `ApiTokenAuthenticationFilter.kt` 실제 코드 확인). 대화형 모바일 클라이언트는 **Authorization Code + PKCE** 플로우를, 클라이언트는 **사전 등록된 고정 public client**(섹션 8-5)를 기본으로 하고, PAT은 자동화/개발자 도구용 보조 경로로 둔다. Refresh token은 30일 수명으로 로테이션되지만 탈취 재사용 탐지는 없어(섹션 8-6), 사용자의 수동 "연결 해제" 기능으로 대응하도록 설계했다.
- 저장소 구조는 **모노레포(웹 클라이언트: 공유 UI + iOS Capacitor 셸) + SailfishOS 네이티브 앱은 완전 별도 리포**의 하이브리드 구조를 권고한다.

---

## 2. 플랫폼별 실행 가능성 검증

### 2.1 Android — PWA (+ 선택적 TWA)

- Android Chrome은 서비스워커, Web App Manifest, Web Push, Background Sync 등 PWA 표준을 사실상 완전히 지원한다. 별도 검증 이슈 없음.
- Play Store 배포가 필요하면 **TWA(Trusted Web Activity)**로 감싸 배포 가능하다: Bubblewrap 또는 PWABuilder로 패키징, Lighthouse PWA 점수 기준 충족, Digital Asset Links(`assetlinks.json`)로 도메인 소유권 검증, TWA는 실제 Chrome 엔진을 브라우저 UI 없이 그대로 렌더링한다.
- 결론: 원안 그대로 유효. Android는 추가 네이티브 셸 없이도(브라우저에서 "홈 화면에 추가") 기능적으로 완결되고, 스토어 배포가 필요할 때만 TWA 셸을 얹으면 된다.

### 2.2 iOS — 순수 PWA의 한계와 절충안

검증 결과, 다음 제약이 실제로 존재한다 (2026-09 기준):

1. **App Store 배포 불가**: "홈 화면에 추가"는 웹 링크의 바로가기 방식이며 App Store 심사·유통 파이프라인을 거치지 않는다. **(확정, 섹션 8) App Store 공개 배포가 필요하다** — 사내 MDM 배포만으로는 부족하므로, Capacitor 셸 개발과 App Store Connect 심사 대응은 선택이 아니라 필수 경로다.
2. **푸시 알림 제약**: iOS 16.4+부터 웹 푸시가 지원되지만 조건부다.
   - 반드시 **홈 화면에 추가된 상태(standalone)**에서만 알림 권한 요청이 가능하다 — Safari 탭에서는 동작하지 않는다.
   - **EU(디지털시장법, DMA) 지역에서는 Apple이 standalone PWA 자체를 사실상 무력화**했다 — 홈 화면에서 열어도 Safari 탭으로 열리며 푸시가 동작하지 않는다. **(확정, 섹션 8) 현재는 EU/DMA 대상 사용자가 없지만 향후 생길 가능성이 있다** — 다만 iOS 경로 자체가 이미 Capacitor 네이티브 셸(App Store 배포)로 확정됐으므로 이 제약은 실질적으로 무관해진다: Capacitor 앱은 웹 PWA가 아니라 APNs를 쓰는 진짜 네이티브 앱이라 DMA의 standalone PWA 무력화 규정 대상이 아니다. 즉 순수 PWA 경로를 iOS 주력으로 남겨뒀다면 재확인이 필요했을 리스크지만, 이미 다른 이유(App Store 배포 요건)로 해소됐다.
   - Safari 18.4에서 서비스워커 없이 동작하는 "Declarative Web Push"가 추가됐지만 이는 표현 방식의 단순화이지 근본 제약 해소는 아니다.
3. **저장소/서비스워커 정책이 더 엄격함**: iOS는 앱을 몇 주간 사용하지 않으면 Cache/IndexedDB를 자동 삭제할 수 있고, 스크립트로 쓰는 저장소에는 **7일 캡**이 걸릴 수 있다. Background Sync API도 안정적으로 지원되지 않는다.

**요구사항 확정(섹션 8)**: 푸시 알림은 필수 기능이 아니다. 다만 App Store 공개 배포 자체가 확정 요구사항이므로, 그 이유 하나만으로도 **순수 PWA만으로는 부족**하다 — 배포 경로 확보가 곧 Capacitor 셸이 필요한 이유다.

**채택**: iOS는 **Capacitor로 웹 코드베이스를 네이티브 셸에 담아 App Store에 배포**한다.
- Capacitor는 WKWebView 위에 네이티브 브릿지를 제공하는 오픈소스 하이브리드 프레임워크로, iOS/Android/Web을 공식 지원한다.
- 이렇게 하면 실제 네이티브 앱이므로 App Store 배포, APNs 기반 푸시(웹 푸시의 iOS 제약을 우회), iOS 저장소 자동삭제 정책 회피가 모두 가능해진다.
- **핵심 UI/로직 코드(Vue 컴포넌트, 상태관리, API 클라이언트)는 그대로 재사용**되고, Capacitor는 그 위에 얇은 네이티브 셸(패키징 껍데기)만 추가한다 — "플랫폼별 코드가 갈라지지 않는다"는 원안의 정신은 iOS에서도 UI/로직 레벨에서는 유지된다. 다만 셸 빌드·서명·스토어 심사 대응이라는 플랫폼 고유 작업은 별도로 발생한다는 점은 정직하게 짚어야 한다.
- 순수 PWA(홈 화면 추가) 경로도 폐기하지 않고 "설치 없이 바로 써보는" 라이트 진입점으로 병행 제공 가능하다(브라우저에서 접속만 해도 동작).

### 2.3 SailfishOS — 네이티브 Qt/QML 앱으로 완전 분리 (사용자 확정 사실 반영)

**결론(확정)**: SailfishOS는 웹/PWA/하이브리드 셸(Cordova/Capacitor 포함)로는 답이 나오지 않는다. **Qt/QML + Silica UI 기반 네이티브 앱을 Harbour 스토어에 배포하는 것이 유일한 실행 가능 경로**다. 이는 조사로 뒤집을 수 있는 가정이 아니라 사용자가 직접 확정한 사실로 취급하며, 본 설계는 이를 전제로 한다.

이 결론을 뒷받침하는 기술적 배경(참고용):
- SailfishOS 내장 브라우저는 구버전 Gecko(Firefox ~38 계열) 기반으로, 서비스워커·매니페스트 기반 PWA 설치를 지원하지 않는다(커뮤니티 포럼에서도 서비스워커/알림 지원 공백이 반복 지적됨).
- Sailfish 앱 안에 웹뷰를 넣을 수 있는 `Sailfish.WebView` 컴포넌트가 존재하긴 하지만, 이 역시 같은 Gecko 엔진 기반이라 서비스워커/오프라인/푸시 등 PWA 핵심 기능을 앱 내부로 그대로 들여오지 못한다 — 단순 웹 콘텐츠 임베딩 수준이며, "PWA를 감싸는 하이브리드 셸"로 기능하지 않는다.
- Capacitor/Cordova는 공식 지원 플랫폼에 iOS/Android/Web만 명시하고 있고 SailfishOS(Linux 모바일)는 대상에 없다 — 커뮤니티 포크·비공식 대응조차 확인되지 않는다.

**설계 반영**:
- SailfishOS 앱은 **별도의 독립 저장소, 별도의 기술 스택(Qt/QML, C++ 또는 Kotlin/Native은 미지원이므로 QML+JS 비즈니스 로직)**으로 개발한다. 웹 클라이언트(React/Vue)와 **UI 컴포넌트/상태관리/빌드 파이프라인을 전혀 공유하지 않는다.**
- 유일한 공유 자산은 **yona 백엔드의 API 계약**이다: `/api/v1/**` REST 엔드포인트 스펙, Fine-grained PAT(`Authorization: token ...` 또는 `Yona-Token` 헤더) 또는 OAuth2 Authorization Code 플로우, 응답 스키마. 이 계약을 두 클라이언트가 각자의 언어/스택으로 독립 구현한다.
- "핵심 코드베이스 하나"라는 원안의 전제는 Android/iOS(웹 스택)에는 성립하지만 **SailfishOS에는 성립하지 않는다는 점을 이 문서에서 명확히 못박는다.** 이는 사용자 원안 대비 가장 크게 수정된 지점이다.
- **(확정, 섹션 8) 실사용자는 개발자 본인 1명뿐이다.** 이는 기술적 결론(네이티브 Qt/QML 필요)에는 영향이 없지만 배포·품질 기준에는 실질적인 단순화를 준다 — 공식 스토어 유통(Harbour 심사)이 굳이 필요 없으므로 개인 사이드로드 또는 커뮤니티 저장소(SailfishOS:Chum)로 배포 범위를 축소할 수 있다(섹션 7.3). 로드맵 우선순위도 최저로 두고, Android/iOS 대비 후행 착수해도 무방하다.
- SailfishOS 앱 개발은 Qt/QML 경험이 있는 인력이 별도로 필요하다 — 다만 단일 사용자·비공식 배포 전제이므로 정식 프로덕트급 QA/일정 압박 없이 기능 완성도 위주로 점진적으로 진행해도 된다.

### 2.4 플랫폼별 실행 가능성 요약표

| 플랫폼 | 실행 경로 | 코드 공유 | 스토어 배포 | 비고 |
|---|---|---|---|---|
| Android | 브라우저 PWA (+ 선택적 TWA) | 웹 코드베이스 그대로 | Play Store(TWA) 또는 무배포(직접 설치) | 제약 없음 |
| iOS | Capacitor 네이티브 셸 (+ 병행 PWA 라이트 진입점) | 웹 코드베이스 그대로 + 얇은 셸 | App Store 공개 배포 (확정) | 푸시는 필수 아님 |
| SailfishOS | 완전 별도 네이티브 Qt/QML(Silica) 앱 | 없음 (API 계약만 공유) | 개인 사이드로드 또는 Chum (Harbour 불필요, 섹션 2.3/7.3) | 사용자 1명(개발자 본인) — 최저 우선순위 |

---

## 3. 기술 스택 권고

### 3.1 React vs Vue 비교표

| 기준 | React | Vue 3 | 이 프로젝트 관점 |
|---|---|---|---|
| 학습 곡선 (jQuery+Thymeleaf 출신 팀 기준) | JSX·불변성·훅 규칙 등 개념 전환 폭이 큼 | 템플릿 문법이 Thymeleaf와 유사해 전환이 완만함 | Vue 유리 |
| 상태관리 | 공식 솔루션 없음 (Redux/Zustand/Jotai 중 선택 필요, 의사결정 비용) | Pinia가 공식·표준으로 정착, 보일러플레이트 적음 | Vue 유리 |
| 라우팅 | React Router(사실상 표준이나 비공식) | Vue Router(공식) | Vue 근소 우위 |
| PWA 빌드 도구 | Vite + vite-plugin-pwa (동일하게 사용 가능) | Vite + vite-plugin-pwa (Vue 스타터가 1급 지원) | 대등 |
| 번들 크기 (초기 로드, iOS 저장공간 정책 고려) | Vue 대비 다소 큼 (React+ReactDOM 런타임) | 상대적으로 작음 | Vue 유리(iOS 7일 캐시 삭제 정책 대응에 유리) |
| Capacitor/하이브리드 셸 호환 | Ionic Framework가 React 바인딩 1급 지원 | Ionic Framework가 Vue 바인딩 1급 지원 | 대등 |
| 생태계/채용 규모(글로벌) | 압도적으로 큼 | React보다 작음 | React 유리 |
| 국내 채용/커뮤니티 | 큼 | 국내(특히 커머스/플랫폼 스타트업) 채택 사례 많고 학습 자료 풍부 | 대등~Vue 근소 우위 |
| 타입스크립트 지원 | 우수 | 우수(3.x부터 대폭 개선) | 대등 |

### 3.2 최종 권고: Vue 3 (Composition API) + Vite

- 결정적 근거는 **팀 배경**이다 — 기존 프론트엔드가 jQuery + Thymeleaf 서버사이드 템플릿이라, SPA 프레임워크 자체가 처음인 팀에는 Vue의 템플릿 지향 문법과 "공식 정답이 하나로 정해져 있는" 생태계(Pinia, Vue Router, Vite)가 초기 생산성과 유지보수성 양쪽에서 유리하다.
- React가 갖는 압도적 생태계/채용 규모는 실제 강점이지만, 이 프로젝트는 신규 사내 도구 성격이 강해 외부 채용 풀보다 **현재 팀의 학습 곡선과 의사결정 피로도**가 더 중요한 변수로 판단했다.
- 부속 스택 권고:
  - 빌드 도구: **Vite** (HMR 속도, `vite-plugin-pwa`로 서비스워커/매니페스트 자동 생성)
  - 상태관리: **Pinia**
  - 라우팅: **Vue Router 4** (히스토리 모드)
  - 데이터 페칭/캐싱: **TanStack Query (Vue Query)** — REST API 호출의 캐싱/재시도/무효화를 선언적으로 관리, 오프라인 대응과도 궁합이 좋음
  - 폼: **VeeValidate** 또는 경량 자체 구현 (요구사항 규모에 따라 결정)
  - UI 컴포넌트: **Vuetify** 또는 헤드리스(**Headless UI for Vue** + Tailwind) 중 선택 — 반응형/폴더블 대응을 커스텀 CSS로 세밀히 제어하려면 헤드리스+Tailwind 조합을 권고
  - iOS 셸: **Capacitor** (Vue 웹 코드베이스를 그대로 감쌈)
  - 테스트: **Vitest** (단위) + **Playwright** (E2E, PWA 설치·오프라인 시나리오 포함)

---

## 4. 폴더블 대응 설계

원안의 "CSS 반응형 + Container Query, Viewport Segments API는 불필요"라는 판단은 **타당하다** — 이 문서는 그 판단을 그대로 확인해준다.

- **Viewport Segments API를 채택하지 않는 이유**: 이 API는 화면이 물리적으로 두 개의 독립 세그먼트로 나뉘는 기기(예: Surface Duo류 듀얼스크린)를 위한 것이다. 갤럭시 폴드 계열은 **단일 힌지·단일 연속 디스플레이**이며 접힘 여부와 무관하게 브라우저에는 하나의 뷰포트로 보인다. 세그먼트 분리 자체가 존재하지 않으므로 이 API가 관측할 대상이 없다 — 도입은 불필요한 복잡도만 추가한다.
- **CSS breakpoint 전략**:
  - 폴더블은 "접힘(커버 스크린, 폭 좁음)"과 "펼침(내부 스크린, 폭 넓고 정사각형에 가까운 비율)" 두 상태를 오가는 것으로 취급하고, 특정 기기명이 아니라 **뷰포트 폭/가로세로비 구간**으로 breakpoint를 정의한다.
    - 예: `~360px` (커버 스크린), `361~600px` (일반 폰 펼침 전/좁은 창), `601~900px` (폴더블 펼침 내부 화면, 태블릿 세로), `901px~` (태블릿 가로/데스크톱 PWA 창)
  - 폴더블 펼침 상태는 일반 태블릿과 유사한 폭이지만 **정사각형에 가까운 종횡비**를 갖는 경우가 많아, 폭 breakpoint만으로는 레이아웃이 어색해질 수 있다 → `aspect-ratio` 미디어 쿼리를 보조 신호로 병행 사용 권고.
- **Container Query 활용 방안**:
  - 이슈 목록/카드, 코드 리뷰 diff 패널처럼 **화면 어디에 배치되든 재사용되는 컴포넌트**는 뷰포트가 아니라 자기 컨테이너 폭 기준으로 레이아웃을 바꿔야 한다 — 폴더블 펼침 시 사이드바+본문 2단 레이아웃이 되면 본문 컨테이너 폭이 뷰포트 폭보다 훨씬 좁아지는데, 뷰포트 기준 breakpoint로는 이 축소를 반영할 수 없다.
  - `@container` 쿼리로 카드형 컴포넌트(이슈 카드, PR 카드)의 내부 정보 밀도(요약만 vs 라벨/담당자까지)를 컨테이너 폭 기준으로 전환.
- **실기기/DevTools 테스트 전략**:
  - Chrome DevTools의 "Foldable" 기기 프리셋(Galaxy Fold, Surface Duo 등)으로 접힘/펼침 폭 전환 시 레이아웃 리플로우를 1차 검증.
  - 실기기 테스트는 최소 1대 확보를 권고(에뮬레이터는 힌지 애니메이션 중 리플로우 타이밍, 실제 DPI 스케일링 차이를 재현하지 못함).
  - Playwright의 `viewport` 옵션으로 breakpoint 경계값(359/360/361px 등)에 대한 시각 회귀 테스트 자동화.
  - PWA 설치 상태(standalone display mode)에서의 안전 영역(`env(safe-area-inset-*)`) 처리도 폴더블 펼침 시 노치/카메라 컷아웃 위치가 달라질 수 있어 함께 검증.

---

## 5. 인증/API 연동 설계

### 5.1 yona 백엔드 인증 방식 (실제 코드 근거)

`ResourceServerConfig.kt`(`src/main/kotlin/com/github/yonaprojects/yona/config/oauth2server/ResourceServerConfig.kt`)와 `ApiTokenAuthenticationFilter.kt`(`src/main/kotlin/com/github/yonaprojects/yona/config/ApiTokenAuthenticationFilter.kt`)를 직접 읽어 확인한 사실:

- `/api/v1/**`은 **이중 인증**을 지원한다.
  1. **OAuth2 JWT** — yona 자신이 Spring Authorization Server 기반 OAuth2 인가 서버 역할을 겸한다(`AuthorizationServerConfig.kt`). Authorization Code 플로우(`/oauth2/authorize` → 로그인 시 `/users/loginform`으로 리다이렉트 → 동의 화면 `/oauth2/consent` → `/oauth2/token`) + **RFC7591 Dynamic Client Registration**(`/oauth2/register`, `DcrRateLimitFilter`로 레이트리밋)까지 지원한다. 발급된 JWT는 리소스(`mcp` vs `api`)별로 audience(`aud`)가 검증된다.
  2. **Fine-grained PAT** — `ApiTokenAuthenticationFilter`가 `Authorization: token <값>` 헤더 또는 `Yona-Token: <값>` 헤더를 파싱해 인증한다. `ApiTokenAuthenticationFilter`가 `BearerTokenAuthenticationFilter` **앞**에 등록되어 있어(`addFilterBefore`), PAT 헤더가 있으면 먼저 PAT 경로로, 없으면 OAuth2 JWT 경로로 넘어간다.
  - PAT은 `owner/project/resource` 3세그먼트 URL 구조(`/api/v1/projects/{owner}/{project}/{resource}`)에 대해 세밀한 스코프(리소스 타입 × READ/WRITE, `allRepositories` 플래그 등)를 갖고, GitHub Fine-grained PAT과 유사한 모델이다.
  - `GET /api/v1/projects/**`는 인증 없이도 permitAll(공개 프로젝트 조회), 그 외 모든 메서드는 인증 필요.
- 이 필터는 레거시 전권 토큰(`UserRepository.findByToken`, 세션 기반) 경로도 하위 호환으로 유지하지만, **신규 클라이언트는 Fine-grained PAT 또는 OAuth2만 사용**하는 것을 전제로 설계한다.

### 5.2 모바일 클라이언트 인증 방식 선택

**대화형 로그인(일반 사용자 흐름)은 OAuth2 Authorization Code + PKCE를 기본으로 채택**한다.

- yona가 Spring Authorization Server 기반이라 PKCE(RFC7636)는 공개 클라이언트(client_secret 없는 SPA/모바일 앱)를 위한 표준 확장으로 프레임워크 차원에서 지원 대상이다. 클라이언트 시크릿을 배포 바이너리/웹 코드에 내장할 수 없는 PWA/Capacitor 앱 특성상 PKCE가 필수다.
- 클라이언트 등록은 **사전 등록된 고정 public client**(관리자가 `OAuthAppsAdminController`로 등록)로 확정한다(섹션 8-5). DCR(`/oauth2/register`)은 이 코드베이스에서 이미 MCP 도구 클라이언트 전용으로 설계돼 있다 — `JpaRegisteredClientRepository`가 DCR로 들어오는 요청의 scope/TokenSettings를 신뢰하지 않고 항상 `McpOAuthScopes.ALL`+고정 TTL을 강제하는 등, "출처를 모르는 클라이언트가 알아서 등록"하는 상황을 전제로 방어적으로 만들어져 있다. 반면 모바일 앱은 우리가 직접 배포하는 **1급(first-party) 클라이언트**이므로 이 방어 장치가 불필요한 간접비만 늘린다 — client_id가 매 설치/빌드마다 달라지지 않고, DCR 레이트리밋(`DcrRateLimitFilter`) 경로를 아예 타지 않으며, GitHub Desktop/VS Code 등 업계에서도 자사 공식 앱은 정적으로 등록된 public client + PKCE를 쓰는 것이 일반적이다.
- 로그인 플로우: 앱 내 인앱 브라우저(Capacitor의 `@capacitor/browser`, 웹에서는 `window.location` 리다이렉트 또는 팝업) → `/oauth2/authorize`(PKCE code_challenge 포함) → yona 세션 로그인(`/users/loginform`, 기존 로그인 UI 재사용) → 동의 화면 → 콜백 URL(커스텀 스킴 또는 Universal Link/App Link)로 authorization code 수신 → 클라이언트가 `/oauth2/token`에서 code_verifier와 함께 교환 → JWT access token(및 refresh token, 스코프에 따라) 획득.
- **PAT은 자동화/개인 도구 시나리오의 보조 경로**로 설정 화면에 "Personal Access Token으로 로그인" 옵션을 남겨둔다(CI 연동, 파워유저의 헤드리스 스크립트 등) — 신규 유저 온보딩의 기본 경로로 노출하지 않는다.

### 5.3 토큰 저장 방식과 위험 요소

PWA/Capacitor 환경별로 안전한 저장소가 다르다.

| 저장 위치 | 웹(PWA, Vue 앱) | Capacitor(iOS 셸) | 위험 요소 |
|---|---|---|---|
| `localStorage` | 사용 가능하나 비권장 | 사용 가능하나 비권장 | XSS에 취약 — 스크립트가 실행되면 토큰 탈취 가능. 동기 API라 성능도 불리 |
| `IndexedDB` | 권장(웹 표준 범위 내 최선) | 사용 가능 | 여전히 XSS에 노출되나 접근에 비동기 API가 필요해 공격 난이도가 약간 높음. 실질적으로 origin-격리는 동일 |
| httpOnly 쿠키 | **불가** | 해당 없음 | yona가 SPA에 JWT를 내려주는 구조상, 서버가 쿠키를 직접 세팅하는 세션 방식이 아니면 SPA가 쿠키를 못 읽는 httpOnly의 이점을 못 살림 — 애초에 토큰 발급이 `/oauth2/token` JSON 응답이라 쿠키 경로 자체가 어색함 |
| 네이티브 Secure Storage(Keychain/Keystore) | 해당 없음(브라우저 API 없음) | **권장** — `@capacitor/preferences` 대신 `capacitor-secure-storage-plugin` 또는 iOS Keychain 직접 연동 | 네이티브 셸에서만 가능한 최선의 옵션 |

- **결론적 권고**:
  - **순수 웹(PWA) 경로**: IndexedDB에 저장하되, refresh token은 짧은 수명으로 운용하고 access token은 메모리(JS 변수)에 우선 두고 페이지 리로드 시에만 IndexedDB에서 재수화(rehydrate)하는 방식으로 XSS 노출 시간을 최소화. CSP(Content-Security-Policy)를 엄격히 설정해 XSS 자체 발생 가능성을 낮추는 것이 저장 위치 선택보다 근본적인 방어선임을 병기.
  - **Capacitor(iOS) 경로**: 네이티브 Secure Storage(Keychain 연동 플러그인)를 사용해 access/refresh token을 저장 — 웹 코드와 저장 계층만 어댑터 패턴으로 분리(`TokenStorage` 인터페이스를 두고 웹 빌드는 IndexedDB 구현체, Capacitor 빌드는 Keychain 구현체를 주입).
  - **(확정, 섹션 8) Refresh Token 회전/폐기 정책 — 코드로 직접 확인함**: `OAuthRegisteredClient.kt`의 기본값은 access token 1시간(`DEFAULT_ACCESS_TOKEN_TTL_SECONDS=3600`), refresh token 30일(`DEFAULT_REFRESH_TOKEN_TTL_SECONDS=2_592_000`), `reuseRefreshTokens=false`(매 갱신마다 새 refresh token 발급, 이전 값은 즉시 폐기되는 표준 로테이션). 다만 `JpaOAuth2AuthorizationService`는 Spring Authorization Server 공식 구현을 그대로 따르는 단순 CRUD 계층이라, **탈취된 refresh token이 정상 클라이언트보다 먼저 갱신을 시도하는 경쟁 상황(refresh token theft)에 대한 자동 탐지·전체 세션 무효화(reuse detection) 로직은 없다** — 로테이션으로 이전 토큰이 못 쓰이게 될 뿐, "이미 폐기된 토큰이 재사용됐다"는 신호로 다른 파생 토큰까지 강제 폐기하는 안전장치는 프레임워크 기본값 그대로 부재하다. 다행히 `OAuthAuthorizedAppsService.revoke()`가 이미 구현돼 있어(설정 화면의 "연결 해제") 사용자가 의심스러운 세션을 즉시 완전 폐기(consent+모든 authorization 삭제)할 수 있다. **클라이언트 설계 반영**: refresh 실패 시 곧바로 재로그인 유도(access token 수명이 1시간이라 노출 창이 짧음), 그리고 모바일 앱 설정 화면에 yona의 "연결된 앱 관리"(연결 해제) 화면으로 바로 이동하는 링크를 둬 탈취 의심 시 사용자가 즉시 대응할 수 있게 한다.
- SailfishOS 네이티브 앱은 완전히 독립된 스택이므로 토큰 저장은 Qt의 `QtKeychain` 또는 SailfishOS 표준 시크릿 저장 메커니즘을 사용 — 이 문서의 웹 클라이언트 저장 전략과는 무관하게 별도 설계된다.

---

## 6. 저장소/디렉터리 구조 권고

**하이브리드 구조**를 권고한다: 웹 스택(Android/iOS 공유)은 모노레포로, SailfishOS는 완전 별도 리포로 분리한다. 이유는 섹션 2.3에서 확정한 대로 SailfishOS가 코드/툴체인을 전혀 공유하지 않기 때문에, 하나의 모노레포에 억지로 편입시키면 CI 파이프라인·의존성 관리·릴리스 주기가 서로를 오염시킨다.

```
yona-mobile-web/              # 모노레포 (Android + iOS 공유 웹 코드베이스)
├── packages/
│   ├── app/                  # Vue 3 앱 본체 (라우팅, 화면, 상태관리)
│   ├── ui/                   # 공유 UI 컴포넌트 라이브러리 (디자인 시스템, 반응형/컨테이너쿼리 포함)
│   ├── api-client/           # yona REST API 클라이언트 (OAuth2 PKCE 플로우, PAT 지원, TokenStorage 인터페이스)
│   └── shared-types/         # API 응답 타입(TypeScript), 백엔드 DTO와 동기화
├── platforms/
│   ├── pwa/                  # 웹/Android PWA 빌드 설정 (vite-plugin-pwa, manifest.json)
│   └── ios-capacitor/        # Capacitor iOS 셸 프로젝트 (Xcode 워크스페이스, 네이티브 플러그인 설정)
├── package.json               # workspaces 루트 (pnpm/npm workspaces)
└── (Android는 platforms/pwa 산출물을 그대로 사용, 필요 시 platforms/android-twa/ 를 추후 추가)

yona-mobile-sailfish/          # 완전 별도 리포 — 웹 코드베이스와 어떤 코드도 공유하지 않음
├── qml/                       # Silica UI QML 화면
├── src/                       # C++/Qt 백엔드 로직 (네트워킹, 인증)
├── rpm/                       # Harbour 배포용 .spec, 패키징 스크립트
└── (yona API 계약 문서만 참조 — yona-mobile-web과 코드 의존성 없음)
```

- 웹 모노레포 도구: **pnpm workspaces**(또는 npm workspaces) 권장 — Nx/Turborepo 같은 빌드 오케스트레이션은 패키지 수가 4~5개 수준으로 적을 때는 과설계일 수 있어 초기에는 생략, 필요해지면 도입.
- `api-client` 패키지가 OAuth2/PAT 인증과 REST 호출을 캡슐화하고, `TokenStorage` 인터페이스로 PWA(IndexedDB)/Capacitor(Keychain) 구현체를 갈아끼우는 지점을 명확히 분리한다(섹션 5.3).
- SailfishOS 리포는 **yona 백엔드 리포의 API 계약 변경에 대해 별도로 구독**해야 한다 — 예: `docs/` 또는 OpenAPI 스펙이 있다면 그것을 두 리포가 공통으로 참조하는 방식(리포 간 코드 공유는 없지만 계약 문서 공유는 필요).

---

## 7. 배포 파이프라인 개요

### 7.1 Android

- 기본 배포: 사용자가 브라우저에서 접속 후 "홈 화면에 추가"로 설치 — 스토어 심사 불필요, 즉시 배포/롤백 가능.
- 스토어 배포가 필요한 경우: **TWA(Trusted Web Activity)**
  1. Bubblewrap CLI 또는 PWABuilder로 TWA 프로젝트 생성 (`platforms/pwa`의 manifest.json 기반)
  2. Digital Asset Links(`/.well-known/assetlinks.json`)를 yona 백엔드 정적 리소스 경로에 배포해 도메인 소유권 검증
  3. Lighthouse PWA 점수 기준(설치 가능성, 오프라인 대응 등) 충족 확인
  4. 서명된 APK/AAB를 Play Console에 업로드, 표준 Play 심사 절차 진행

### 7.2 iOS

- **App Store 배포(권장 경로)**: `platforms/ios-capacitor`에서 Capacitor 빌드 → Xcode 프로젝트 → 서명/프로비저닝(Apple Developer Program 등록 필요, 조직 계정 여부는 사용자 확인 사항) → App Store Connect 심사 제출.
  - 웹 콘텐츠를 대부분 원격 로드하는 앱은 Apple 심사 가이드라인상 "웹뷰 셸"로 반려될 수 있어, 네이티브 느낌의 최소 UI 요소(네이티브 네비게이션 바, 스플래시 등)를 Capacitor 플러그인으로 보강하는 것을 권고.
  - 푸시는 APNs(Capacitor `@capacitor/push-notifications` 플러그인)를 사용해 iOS 웹 푸시의 제약(standalone 필수, EU DMA 제약)을 우회.
- **병행 라이트 경로(선택)**: 순수 PWA "홈 화면에 추가" — 스토어 등록 없이 즉시 체험 가능한 진입점으로 유지, 단 푸시/오프라인 신뢰성은 App Store 앱보다 떨어짐을 사용자에게 안내.

### 7.3 SailfishOS

**(확정, 섹션 8) 실사용자가 개발자 본인 1명뿐이므로 Harbour 공식 스토어 심사는 불필요하다** — 아래 배포 경로 중 1)을 기본으로 권고한다.

1. **개인 사이드로드(권장)**: RPM으로 패키징(`.spec` 파일 작성, Sailfish SDK 빌드 타깃 사용) 후 `pkcon install-local` 또는 `sfdk deploy`로 본인 기기에 직접 설치. Harbour QA 체크리스트(Silica UI 가이드라인, 아이콘/스크린샷 규격, 개인정보 처리방침 등)를 준수할 필요가 전혀 없어 개발 속도가 가장 빠르다.
2. **SailfishOS:Chum 커뮤니티 저장소(선택)**: 나중에 다른 사용자와 공유하고 싶어지면, Harbour보다 심사가 가벼운 커뮤니티 저장소에 패키지를 올리는 경로도 있다 — 지금 단계에서 준비할 필요는 없다.
3. **Harbour 공식 스토어**: 대상 사용자가 늘어나 공식 유통이 필요해지기 전까지는 고려하지 않는다.

릴리스 주기는 웹 클라이언트(즉시 배포 가능)와 완전히 분리되어 있지만, 단일 사용자·비공식 배포 전제이므로 기능 패리티 지연에 따른 로드맵 압박은 없다.

---

## 8. 확정된 전제조건 및 남은 리스크

**사용자가 확정한 전제조건 (2026-09-10)**

1. **iOS App Store 배포 정책 — 확정**: 사내 MDM 배포로는 부족하고 **일반 App Store 공개 배포가 필요**하다. → Capacitor 셸 개발이 선택이 아닌 필수 경로로 확정(섹션 2.2/7.2).
2. **iOS 푸시 알림 — 확정**: **필수 기능이 아니다.** 다만 1번 결론(App Store 공개 배포 필요) 자체가 이미 Capacitor 셸을 요구하므로, 푸시 여부와 무관하게 결론은 바뀌지 않는다 — 확보되면 APNs로 자연스럽게 딸려오는 부가 이득 정도로 취급한다.
3. **EU/DMA 적용 지역 사용자 — 확정**: **현재는 없으나 향후 생길 가능성이 있다.** iOS 경로가 이미 Capacitor 네이티브 앱(APNs 기반)으로 확정돼 있어 DMA의 "standalone PWA 무력화" 규정 자체가 적용 대상이 아니므로, 이 리스크는 사실상 해소됐다(섹션 2.2).
4. **SailfishOS 사용자 규모 — 확정**: **실사용자는 개발자 본인 1명뿐이다.** 네이티브 Qt/QML 개발이 필요하다는 기술적 결론은 그대로지만, Harbour 공식 스토어 심사를 생략하고 개인 사이드로드로 배포 범위를 축소했다(섹션 2.3/7.3). 로드맵 우선순위는 최저로 두고 Android/iOS 이후 착수해도 무방하다.
5. **yona OAuth2 클라이언트 등록 방식 — 권고 채택**: **사전 등록된 고정 public client**를 권고하고 그대로 채택한다(섹션 5.2). 이 코드베이스의 DCR(`/oauth2/register`)은 출처를 모르는 제3자 클라이언트(MCP 도구 등)를 전제로 스코프/TokenSettings를 강제하는 방어적 설계라, 우리가 직접 배포하는 1급 모바일 앱에는 그 방어 계층이 불필요한 간접비만 더한다 — `OAuthAppsAdminController`로 관리자가 사전 등록.
6. **Refresh Token 회전/폐기 정책 — 코드로 확인 완료**: `OAuthRegisteredClient.kt` 기준 access token 1시간·refresh token 30일·매 갱신마다 로테이션(`reuseRefreshTokens=false`)이지만, **탈취된 refresh token의 재사용을 탐지해 전체 세션을 강제 폐기하는 안전장치는 없다**(Spring Authorization Server 기본 동작 그대로, 이 코드베이스가 추가 구현하지 않음). 대신 `OAuthAuthorizedAppsService.revoke()`로 사용자가 "연결 해제"를 통해 즉시 완전 폐기할 수 있다 — 클라이언트 설계에 이 사실을 반영했다(섹션 5.3).

**남은 리스크**

- **SailfishOS 네이티브 앱 개발 일정**: 단일 사용자·비공식 배포 전제이므로 정식 프로덕트급 일정 압박은 없으나, Qt/QML 학습·개발 시간 자체는 별도로 필요하다.
- **iOS Capacitor 셸 유지보수 이중 부담**: 웹 코드베이스는 하나지만, iOS 네이티브 플러그인(APNs, Keychain 등) 계층은 iOS 전용으로 별도 유지보수가 필요하다 — "코드가 갈라지지 않는다"는 것이 "iOS 전용 작업이 전혀 없다"는 뜻은 아님을 명확히 해둔다.
- **Refresh Token 탈취 시나리오**: 위 6번 — 재사용 탐지가 없으므로, access token 수명(1시간)에 노출 창을 의존하고 "연결 해제" 기능의 발견성(설정 화면 도달 용이성)을 클라이언트 UX에서 신경 써야 한다.
- **OAuth2 인앱 브라우저 UX**: iOS/Android 모두 시스템 브라우저 대신 인앱 브라우저(SFSafariViewController/Custom Tabs 상당)를 통한 OAuth 로그인이 애플/구글의 권장 패턴이나, Capacitor `@capacitor/browser`의 콜백 URL 처리(커스텀 스킴 vs Universal/App Link)는 실제 구현 단계에서 세부 설정이 필요하다.

---

## 9. 다음 단계 제안

섹션 8의 전제조건이 모두 확정됐으므로, 아래는 바로 착수 가능한 실행 항목이다.

1. `yona-mobile-web` 모노레포를 실제로 초기화하고 (`pnpm workspaces`), `api-client` 패키지의 OAuth2 PKCE 플로우를 yona 개발 환경 대상으로 스파이크(spike) 구현해 인증 왕복이 실제로 동작하는지 조기 검증한다.
2. yona 쪽에 모바일 클라이언트용 OAuth2 **사전 등록 고정 public client**(섹션 8-5)를 `OAuthAppsAdminController` 경로로 등록한다.
3. iOS Capacitor 셸(`platforms/ios-capacitor`) 프로젝트를 초기 단계부터 함께 세팅한다 — App Store 공개 배포가 확정 요구사항이므로 후순위로 미루지 않는다.
4. SailfishOS 네이티브 앱(`yona-mobile-sailfish`)은 최저 우선순위로 별도 트랙에 두고, Android/iOS 웹 클라이언트의 API 계약(엔드포인트/스코프 모델)이 안정화된 뒤 착수해도 무방하다. Harbour 심사 없이 개인 사이드로드로 배포하므로 준비 부담이 크지 않다.
5. 클라이언트의 "연결된 앱 관리(연결 해제)" 화면 도달 경로를 설정 메뉴에 눈에 띄게 배치한다 — refresh token 탈취 시 사용자의 유일한 즉시 대응 수단이다(섹션 8-6).
6. 폴더블 breakpoint 값(섹션 4)은 실제 UI 시안이 나온 뒤 실기기 1대 이상으로 재검증한다.
