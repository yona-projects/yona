---
title: 보안 점검(의존성 취약점과 코드 패턴), 2026-11-17
type: architecture
sources: [OSV(api.osv.dev) 조회, GitHub Dependabot 경보, src/main 코드 훑기, 직접 실행한 확인]
updated: 2026-11-17
---

# 보안 점검 (2026-11-17)

선생님 요청("현재 소스 코드를 훑으면서 CVE가 나올 수 있는 부분 찾기")으로 한 점검이다. 침투 시험이 아니다. **알려진 취약점 DB(OSV)에 있는 것만** 찾을 수 있고, 코드 점검은 위험한 패턴을 범주별로 훑은 표본 조사이다.

## 확인한 방법

| 영역 | 방법 | 한계 |
|---|---|---|
| 서버 의존성(런타임 297개) | Gradle이 해석한 `runtimeClasspath`의 `그룹:이름:버전`을 OSV에 조회 | 공개된 경보만 안다 |
| 번들 JS 라이브러리(18개) | `static/assets/javascripts/lib`의 버전을 읽어 OSV(npm)에 조회 | `autosize`, `tippy.js`는 파일에서 버전을 못 읽어 최신 버전으로 가정했다 |
| GitHub 경보 | `gh api .../dependabot/alerts` | |
| 코드 패턴 | 프로세스 실행, XML 파서, 역직렬화, 외부 주소 호출, 압축 해제, SQL 조합, 비이스케이프 출력, 난수와 해시, 보안 설정 해제, 리디렉션 | 표본이다. 47개 `th:utext` 중 대표 4곳, SQL 조합 4곳을 읽었다 |
| 직접 실행 | 정화 정책에 페이로드 8개, `ra_svn` 읽기 코드에 악의적 입력 2개 | |

## 1. 의존성 (서버에 실리는 것)

OSV가 17개 아티팩트에서 48건을 알려 줬다. 심각도 숫자보다 **Yona가 그 기능을 쓰는지**가 중요해서 적용 여부를 같이 적었다.

| 우선 | 라이브러리 | 건수(최고) | 수정 버전 | Yona의 사용과 판단 |
|---|---|---|---|---|
| 1 | Tomcat 11.0.24 | 4(치명) | 11.0.25 | DIGEST, FORM 인증 취약점은 컨테이너 인증을 쓰지 않아 해당 없음. HTTP/2 SNI 우회는 설정에 따라 다름. 접근 제어 오류(CVE-2026-65182)는 상세 확인 필요. **서버 전체가 받는 입구라 올리기를 권한다** |
| 1 | Jackson 2.21.5 / 3.1.5 | 7(높음) | 2.21.7 / 3.1.7 | 이중 이차 시간 파싱, 정규식 지연(ReDoS). **외부 API가 받는 JSON으로 서버의 CPU를 쓸 수 있다** |
| 1 | BouncyCastle 1.82 | 7(치명) | 1.84~1.85 | **GPG 키 업로드가 `bcpg`로 파싱한다**(CVE-2026-3505 자원 소모, CVE-2026-13506 ASN.1 중첩). 인증서 생성에도 쓴다 |
| 2 | OWASP HTML 정화기 20190610.1 | 1(치명) | 20211018.1 이상 | 사용자 마크다운의 정화에 쓴다. CVE-2021-42575는 `noscript`, `style` 등을 허용한 정책에서 우회된다. **Yona의 정책은 그 요소를 허용하지 않고 같은 페이로드가 제거됨을 실행으로 확인했다.** 그래도 올리기를 권하나 출력이 달라질 수 있어 v1.6 동등성 시험을 같이 돌려야 한다 |
| 2 | OpenTelemetry 1.62 | 1(보통) | 1.67.0 | 들어오는 `tracestate` 헤더 파싱에서 이차 CPU. 추적을 켠 사이트만 해당 |
| 2 | MariaDB 커넥터 3.5.10 | 4(보통) | 3.5.11 | 악의적 DB 서버가 응답할 때의 문제라 운영자가 정한 DB에 붙는 한 위험이 낮다 |
| 2 | mssql-jdbc 12.10.0 | 1(높음) | 12.10.2 | MSSQL을 쓰는 사이트만 해당 |
| 3 | Guava 33.0.0 (OpenSAML 경유) | 1(높음) | 33.7.2 | 신뢰할 수 없는 입력의 역직렬화에서만 문제이다. Yona는 역직렬화를 쓰지 않는다 |
| 3 | log4j 1.2.14 (`jxl` 엑셀 내보내기 경유) | 6(치명) | 1.x 수정판 없음 | 문제의 `JMSAppender`, `SocketServer`는 log4j 1.x를 **설정해서 쓸 때**만 열린다. Yona는 설정하지 않는다. 스캐너 경보를 없애려면 드롭인 포크 `ch.qos.reload4j`로 치환한다 |
| 3 | commons-lang 2.6 | 1(보통) | 없음 | 취약한 메서드(`ClassUtils` 계열)를 쓰지 않고 `escapeHtml`만 쓴다 |
| 3 | eddsa 0.3.0 (SVNKit 경유) | 1(보통) | 없음 | SSH 클라이언트 서명 검증의 문제이다. Yona의 SVN SSH는 서버이고 이 경로를 쓰지 않는다 |
| 고침 | lz4-java 1.4.1 (SVNKit 경유) | 7(높음) | 유지되는 포크 1.14.1 | **고쳤다(A-620)**. 아래 참조 |

**GitHub Dependabot의 6건**은 모두 `build-tools/js-bundle/package-lock.json`의 `vite`와 `launch-editor`이다. JS 번들을 만드는 개발 도구라 서버에 실리지 않는다.

**번들 JS 18개**(marked 4.3.0, jQuery 3.7.1, jQuery UI 1.14.2, htmx 2.0.11, mustache 4.2.0, highlight.js 11.12.0, ace 1.45.2, select2 4.1.0, video.js 7.21.2, vue 3.5.45, popper 2.11.8, viewerjs 1.15.2, cropperjs 2.3.1, tus 3.7.7, nprogress 0.2.0, vanilla-calendar-pro 3.4.0, autosize, tippy)는 알려진 취약점이 없었다.

## 2. 코드 패턴

### 없었던 것 (좋은 신호)
- 외부 프로세스 실행(`ProcessBuilder`, `Runtime.exec`) 0건, 직접 쓰는 XML 파서 0건, `ObjectInputStream`과 JSON 기본 타이핑 0건, TLS 검증을 끄는 코드 0건.
- 요청 위조 방지는 기본 켜짐(`yona.csrf-protection=true`)이고 쿠키는 `SameSite=Lax`, CORS 설정이 없어 교차 사이트 쓰기 요청이 쿠키를 싣지 못한다. 면제 경로(`/svn`, `git-receive-pack` 등)도 같은 이유로 안전하다.
- JPQL을 문자열로 조합하는 곳은 값을 `:kw` 같은 이름 매개변수로 바인딩하고 열 이름과 정렬은 고정 값이다(4곳을 읽음).
- 커밋 메시지와 이슈 제목 머리말을 HTML로 바꾸는 곳은 `htmlEscape`를 거친다. 댓글 본문은 마크다운 뒤 정화기를 거친다.

### 찾은 것

| 심각도 | 위치 | 내용 | 상태 |
|---|---|---|---|
| 해당 없음 | `domain/webhook/WebhookSender` | Yona의 웹훅은 **아웃고잉만 있다**(`/webhooks`, `/hooks`는 등록, 조회, 삭제 화면과 API이고 수신 엔드포인트는 없다). 사용자가 정한 주소로 보내는 것이 기능의 정의이고, 사내망에서는 내부 서버를 부르는 것이 정상 용도라 취약점으로 보지 않는다. 서버 자신만 믿는 엔드포인트(`/_init`은 GET, `/internal/ssh/authenticate`는 비밀 값 헤더)도 웹훅으로 부를 수 없음을 확인했다 | 변경 없음 |
| 중 | `markdown/MarkdownRenderer` 정화 정책(v1.6와 같음) | 임의 주소의 `iframe`(실행으로 통과를 확인), `file:` 주소, `input type=password`, 전역 `id`와 `class`를 허용한다. 댓글 안에 가짜 로그인 틀이나 외부 페이지를 끼워 넣을 수 있다(피싱, 클릭 가로채기). `style`은 CSS 정화를 거쳐 `position:fixed`, `background:url`이 제거됨을 확인했다. `javascript:` 주소도 제거된다 | 열림, v1.6 동등 동작이라 결정 필요(`iframe` 허용 주소 제한 등) |
| 해당 없음 | `domain/sso/Saml2MetadataFetcher`, `OidcDiscoveryClient` | 사이트 관리자가 정한 주소를 가져오는 것이 기능이다. 웹훅과 같은 이유로 취약점이 아니다 | 변경 없음 |
| 낮음 | `security/PasswordResetTokens` | 새 값을 만들 때 이전 값이 `issuedAt` 표에서 지워지지 않아 재설정 요청이 쌓이면 메모리가 늘 수 있다(코드를 읽은 추정이고 실행으로 확인하지는 않았다). 값 비교도 상수 시간이 아니다 | 열림, 실사용에서 악용하기는 어렵다 |
| 높음→고침 | `ssh/svn/RaSvnWire`(제가 만든 코드) | **직접 실행해서 재현했다.** 20만 겹 중첩 목록 한 줄이 `StackOverflowError`를, 9MB의 평평한 목록이 힙 111MB(약 12배)를 썼다. 인증된 사용자가 서버 자원을 쓸 수 있었다 | **고쳤다(A-620)**: 중첩 64, 항목 100만 개, 한 메시지의 문자열 합계 64MB 한도 |

## 3. 이번에 고친 것 (A-620)

1. `ra_svn` 읽기에 한도를 두었다(위 표). 시험 4건 추가(`RaSvnWireTest`).
2. `lz4-java`를 유지되는 포크(`at.yawk.lz4:lz4-java:1.14.1`)로 치환했다. SVNKit의 svndiff2는 클라이언트가 보낸 LZ4 데이터를 풀기 때문에, `accepts-svndiff2`를 광고한 뒤로 인증된 사용자의 데이터가 이 라이브러리에 닿는다. 같은 패키지(`net.jpountz.lz4`)를 쓰는 드롭인 대체이고 OSV에 알려진 취약점이 없다. SVN 시험(DAV, `ra_svn`, SSH)이 통과한다.

## 4. 남은 일 (결정이 필요한 순서)

1. 의존성 일괄 상향: Tomcat 11.0.25, Jackson 2.21.7과 3.1.7, BouncyCastle 1.85, MariaDB 3.5.11, mssql-jdbc 12.10.2, OpenTelemetry 1.67.0, Guava 33.7.2, log4j는 reload4j로 치환. 스프링 부트가 관리하는 버전이라 속성 재정의로 바꾸고 전체 시험을 돌려야 한다.
2. 정화 정책의 `iframe`, `input`, `file:` 처리(v1.6 동작을 바꾸는 결정).
3. OWASP 정화기 상향(출력 동등성 확인 필요).
