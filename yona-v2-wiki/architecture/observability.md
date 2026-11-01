# 관측성 (X-10)

결정은 [decisions.md](../decisions.md) D-64이다. 지표, 추적, 로그를 모두 OTLP로 수집기(Grafana Alloy 또는 OTel Collector)에 보내고 Grafana 스택(Mimir 또는 Prometheus, Tempo, Loki)이 받는다. v1.6에 없는 추가 항목이다(D-28).

## 구현 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| C1 | 의존성(`starter-actuator`, `starter-opentelemetry`), 기본 꺼짐, 주소 설정 시 켜기 | 완료 |
| C2 | 상태 확인(liveness, readiness) 노출과 나머지 엔드포인트 차단 | 완료(readiness 조건은 C5에서 보강) |
| C3 | 요청 번호 MDC와 JSON 콘솔 로그 | 완료 |
| C4 | OTLP 지표와 추적과 로그, 표본 비율, 비밀값과 URL 규칙, 감사 `request_id` 연결 | 완료 |
| C5 | 도메인 지표: C5a 시스템과 readiness, C5b 백그라운드 작업, C5c VCS, C5d 인증과 협업과 외부 연동, C5e 저장소 총 용량 | C5a, C5b, C5c, C5d 완료 |
| C6 | Alloy와 Grafana 설정 문서, 대시보드, 알림 규칙을 컨테이너로 확인 | 대기 |

## 켜는 방법

수집기 주소를 설정하면 지표, 추적, 로그 내보내기가 모두 켜진다. 주소가 없으면 아무것도 내보내지 않는다.

```
OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4318
```

주소에서 신호별 주소를 만든다(`/v1/metrics`, `/v1/traces`, `/v1/logs`). `OTEL_EXPORTER_OTLP_HEADERS`(`이름=값,이름=값`)는 세 신호의 헤더로 옮긴다(Grafana Cloud처럼 인증이 필요한 수집기). 주소가 **없으면 세 신호를 모두 끈다**(Spring Boot 기본값은 켜짐이라 localhost:4318로 보내려 한다).

개별로 끄려면 `management.otlp.metrics.export.enabled`, `management.tracing.export.otlp.enabled`, `management.logging.export.otlp.enabled`를 `false`로 명시한다(명시한 값이 우선한다). 신호별 주소와 헤더도 `management.*`로 직접 정하면 그 값이 우선한다.

- 추적 표본: `management.tracing.sampling.probability`(기본 0.1, D-64 3번). 오류와 느린 요청을 항상 남기는 것은 Alloy의 tail sampling으로 한다.
- 로그: 수집기 주소가 있으면 프로파일 `otlp-logs`가 켜져 WARN 이상의 로그만 OTLP로 나간다(접근 로그는 제외, D-64 2번). MDC(`request_id`, `user_id`)가 속성으로 담긴다. Spring Boot가 설치하지 않는 Logback 연결(`OpenTelemetryAppender`)을 `OtlpLogAppenderInstaller`가 OpenTelemetry에 잇는다.
- 확인: 모의 수집기 시험(`OtlpExportTest`)과 실제 jar로 모두 확인했다. 주소가 없을 때는 4318 포트로 아무것도 보내지 않고, 주소를 주면 지표, 추적, 로그가 각 경로로 나간다.

## 상태 확인 (`/-_-actuator/health`)

- 인증 없이 열고 응답은 `UP`과 `DOWN`뿐이다(상세 숨김). `/-_-actuator/health/liveness`, `/-_-actuator/health/readiness` 그룹이 있다.
- 기본 경로 `/actuator`는 v1.6의 `/:user` 라우트와 겹치므로 `-_-` 이름 공간에 둔다.
- `health` 밖의 엔드포인트(`env`, `beans`, `metrics`, `prometheus` 등)는 노출하지 않는다. 시험으로 고정한다.

## 로그 문맥과 JSON 로그 (C3)

- **요청 번호**: `RequestIdFilter`가 요청마다 번호를 정해 MDC `request_id`와 요청 속성에 같은 값으로 넣는다. `X-Request-Id` 헤더가 64자 이하이고 글자, 숫자, `._-`만 있으면 그 값을 잇고(프록시와 수집기가 정한 번호) 아니면 새로 만든다(로그 위조 방지). 감사 기록의 `request_id`가 같은 값이므로 **감사 한 건에서 그 요청의 로그로 바로 넘어갈 수 있다.** 응답 헤더는 더하지 않는다(v1.6 응답 불변).
- **사용자 번호**: `UserLogContextFilter`(보안 필터 사슬, 인증 필터 뒤)가 로그인한 사용자의 번호만 MDC `user_id`에 넣는다. 이름, 로그인 ID, 이메일은 넣지 않는다. `yona.observability-identity-in-logs=false`로 끈다(D-64 1번).
- **추적 번호**: 추적이 켜져 있으면 Micrometer가 `traceId`, `spanId`를 MDC에 넣는다(확인함).
- **JSON 콘솔 로그**: 프로파일 `json-log`(`SPRING_PROFILES_ACTIVE=json-log`)를 켜면 콘솔이 ECS 형식 JSON 한 줄이 된다. 컨테이너에서 Alloy가 stdout을 읽어 Loki로 보내는 용도이다. 파일 로그(`access.log` 등)는 v1.6 형식 그대로이고 프로파일이 꺼져 있으면 콘솔도 기존 텍스트이다. `logging.structured.format.console`은 이 프로젝트의 사용자 정의 `logback-spring.xml`에서는 적용되지 않아 프로파일로 구현했다(A-320 14).
- **확인**: 실제 서버를 `json-log`로 띄우고 `X-Request-Id: probe-123`으로 요청해 접근 로그 줄에 `request_id`와 `traceId`, `spanId`, `service.version`이 JSON으로 담김을 확인했다.

## 카디널리티, 비밀값, 연결 (C4)

- **지표 이름의 `uri` 태그**는 요청 경로가 아니라 라우트 템플릿이다(`/{user}/{project}/issues`, `/{ownerName}/{project}/info/refs`, `/-_-api/v1/owners/{owner}/projects/{projectName}`). 모르는 경로(스캐너, 404)는 `/**` 하나로 모인다. 사용자와 프로젝트 이름이 태그 값이 되지 않고, 요청 수가 늘어도 태그 종류가 늘지 않음을 시험(`ObservabilityMetricsTest`)으로 고정했다.
- **비밀값**: 요청 쿼리(`?token=...`), `Authorization`, `Cookie` 헤더의 값은 추적, 지표, 로그 어느 신호로도 나가지 않는다. 모의 수집기가 받은 본문 전체를 검사하는 시험(`OtlpExportTest`)으로 고정했다(경로는 남고 위 값만 없음을 함께 확인). JDBC 계측은 켜지 않아 SQL 바인딩 값도 없다.
- **추적 속성**: 서버 span에 `request_id`(필터가 정한 값), 로그인했으면 `user_id`(번호만)가 로그 MDC와 같은 키로 들어간다. Grafana에서 Loki 로그와 Tempo 추적을 같은 키로 찾는다. 감사 기록의 `request_id`도 같은 값이다.
- **리소스 속성**: `service.name=yona`(애플리케이션 이름), `service.version`(빌드 정보), 그 밖에는 `OTEL_RESOURCE_ATTRIBUTES`(예: `deployment.environment=prod`)나 `management.opentelemetry.resource-attributes.*`로 정한다.

## 시스템 지표와 상태 확인 묶음 (C5a)

- **자동 지표**(Micrometer 기본, 모두 켬): CPU(`process.cpu.usage`, `system.cpu.usage`, `system.load.average.1m`), JVM 메모리와 GC와 스레드, 파일 핸들(`process.files.open`), DB 풀(`hikaricp.*`), HTTP 서버(`http.server.requests`), 톰캣 스레드 풀(`tomcat.threads.*`, `server.tomcat.mbeanregistry.enabled=true`로 켬). 실제 jar로 수집기까지 나감을 확인했다.
- **디스크**: `yona.storage.disk.total`, `yona.storage.disk.free`(바이트)가 데이터 디렉터리가 있는 파티션의 전체와 여유 용량이다. 파티션 전체 값이라 비용이 작고 프로젝트 라벨은 없다.
- **readiness**: `readinessState`, `db`, `dataDirectory`만 본다. `dataDirectory`는 디렉터리가 있고 쓸 수 있으며 여유 용량이 `yona.observability-min-free-percent`(기본 5, 0이면 여유 용량은 보지 않음) 이상이어야 UP이다. 메일, LDAP, 디스크 공간 지표(`diskspace`)는 상태 확인에서 뺐다(외부 의존이 죽었다고 서버를 트래픽에서 빼지 않는다). **디스크 경고는 알림 규칙(85% 등)이 먼저 알리고 readiness는 마지막 방어선이다.**
- **liveness**: 프로세스 상태(`livenessState`)만 본다.

## 백그라운드 작업 지표 (C5b)

모든 `@Scheduled` 작업은 공통 장치 `JobMetrics`로 실행한다(작업 이름은 고정된 값 6개).

| 지표 | 태그 | 설명 |
|---|---|---|
| `yona.job.duration` | `job`, `result`(success, failure) | 실행 시간과 횟수 |
| `yona.job.last.success` | `job` | 마지막 성공 시각(에폭 초, 성공한 적이 없으면 0). **알림은 `time() - 이 값`이 커지면 "작업이 조용히 멈춤"으로 본다** |
| `yona.job.active` | `job` | 지금 실행 중인 수 |

작업 이름: `notification_mail`, `notification_cleanup`, `temporary_files_cleanup`, `audit_retention`, `audit_stream`, `update_check`. 알려진 작업은 시작할 때 값을 미리 만들어 한 번도 안 돌아도 알림이 잡을 수 있다.

- **백그라운드 일**(푸시 뒤 작업과 풀 리퀘스트 병합, `BackgroundJobRunner`): `yona.background.duration{result}`와 `yona.background.active`. 스레드 풀에 상한이 없어 `active`가 쌓이면 서버가 밀린다는 신호이다. 일의 설명에는 이름이 들어갈 수 있어 태그로 쓰지 않는다(시험으로 고정).
- **감사 스트리밍**: `yona.audit.stream.backlog{target}`(아직 보내지 않은 기록 수), `yona.audit.stream.failures{target}`(연속 실패), `yona.audit.stream.last.success{target}`(마지막으로 보낸 시각). 설정된 대상만 만든다.

## VCS 지표 (C5c)

이름은 VCS 중립(`yona.vcs.*`)이고 `vcs` 태그(`git`, `svn`, 앞으로 `hg`)만 다르다. **X-7(Mercurial)은 같은 계측 지점에 `hg`만 더하면 된다.** 태그는 모두 고정된 낮은 카디널리티 값이며 프로젝트, 사용자, 경로 이름이 들어가지 않는다(시험으로 고정).

| 지표 | 태그 | 설명 |
|---|---|---|
| `yona.vcs.request` | `vcs`, `service`, `target`(code, wiki), `result` | 저장소 서비스 요청의 시간과 횟수 |
| `yona.vcs.request.active` | `vcs`, `service`, `target` | 진행 중인 요청 수. **긴 git 요청이 스레드를 점유해 풀이 고갈되는 것의 선행 지표** |
| `yona.vcs.transfer` | `vcs`, `target`, `direction`(in, out) | 전송한 바이트 |
| `yona.vcs.pushes` | `vcs`, `kind`(branch, tag, other), `result`(ok, rejected), `reason` | 푸시한 ref 수. 거부 이유는 `ruleset_deletion`, `ruleset_update`, `ruleset_force`, `ruleset_pull_request`, `reserved` |
| `yona.vcs.hook.duration` | `hook`(last_pushed, recent_branch, post_receive_jobs, audit), `result` | 푸시 뒤 훅의 시간과 실패 |
| `yona.vcs.operation.duration` | `vcs`, `op`(history, commit, browse, raw_file, archive, diff, branches, tags), `result` | 코드 보기, 이력, diff 같은 저장소 작업의 시간 |

- **서비스 이름**: git은 `info_refs`, `upload_pack`, `receive_pack`이다(`info/refs`의 `service` 요청 매개변수는 태그로 쓰지 않고 `info_refs`로 고정). svn은 알려진 WebDAV 메서드(`OPTIONS`, `PROPFIND`, `REPORT`, `MERGE` 등)이고 나머지는 `other`이다(공격자가 태그 값을 늘리지 못하게).
- **결과**: `ok`(2xx와 3xx), `denied`(401, 403, 429), `not_found`(404), `client_error`(그 밖의 4xx), `error`(5xx, 그리고 처리 중 `IOException`). git 처리 중 `IOException`은 v1.6처럼 로그만 남기고 삼키지만 응답이 200이어도 `error`로 센다.
- **전송량**: git은 요청과 응답 본문(요청은 gzip을 푼 뒤)을 센다. svn은 요청 본문의 `Content-Length`만 센다(응답 크기는 세지 않음).
- **HTTP 서버 지표**(`http.server.requests`)와 따로 둔 이유: svn은 필터가 처리해 일반 지표에서는 한 값으로 묶이고, git과 svn은 일반 화면 요청과 지연의 성격(수 초에서 수 분)이 달라 같은 히스토그램에 섞으면 평균이 의미를 잃는다.

## 인증, 협업, 외부 연동 지표 (C5d)

| 지표 | 태그 | 설명 |
|---|---|---|
| `yona.security.events` | `event`, `channel`, `via`, `denied` | 인증과 접근 거부. `event`는 `login_success`, `login_failure`, `logout`, `token_failure`, `basic_failure`, `blocked`, `access_denied`, `api_scope_denied`. 감사 경계 이벤트와 같은 지점에서 센다. **감사 기록은 같은 주소의 차단을 1분에 한 번만 남기지만 지표는 매번 센다.** 급증은 공격 신호이다 |
| `yona.entities.created` | `entity`(project, user, issue, issue_comment, post, post_comment, pull_request, review_comment, attachment) | **커밋된 삽입**을 하이버네이트 이벤트로 한 곳에서 센다(화면, API, 가져오기 모두 포함, 롤백은 제외). DB를 세는 gauge가 아니라 카운터라 DB 부하가 없다 |
| `yona.pull_requests.merge` | `result`(merged, conflict, method_not_allowed, not_reviewed, updates_restricted) | 병합 시도 결과 |
| `yona.mail.sent` | `result`(success, failure, skipped) | 알림 메일 발송(skipped는 메일 서버 미설정) |
| `yona.notification.mail.queue` | 없음 | 발송을 기다리는 알림 메일 수. 내보내는 주기(60초)마다 `count` 한 번 |
| `yona.webhook.sent` | `result`(success, redirect, client_error, server_error, unreachable) | 프로젝트 웹훅 발송 결과. 주소는 태그에 넣지 않는다 |
| `yona.ldap.authenticate` | `result`(success, not_found, auth_failure, unreachable, error) | LDAP 인증의 시간과 결과. **LDAP이 느리면 로그인이 멈춘다** |
| `yona.search.duration` | `op`(counts, page), `result` | 통합 검색의 시간 |
| `cache.gets`, `cache.size`, `cache.evictions` 등 | `cache`(markdown_render, session_user), `result`(hit, miss) | 렌더링 캐시와 세션 사용자 캐시. 적중률이 떨어지면 마크다운 렌더링과 권한 계산 비용이 는다 |

- 모든 태그는 고정된 낮은 카디널리티 값이며 로그인 ID, 주소, 경로, 프로젝트 이름은 들어가지 않는다(시험으로 고정). 알 수 없는 채널과 결과는 `other`이다.
- **제외한 것**: 댓글의 종류별 생성 수는 `yona.entities.created`의 댓글 엔티티로 충분해 따로 두지 않았다. 이슈 목록 쿼리 지연은 `http.server.requests`의 이슈 목록 라우트와 `hikaricp` 지표로 본다. 첨부 업로드 크기와 실패는 후속이다.
- **발견한 구조 문제**: `WebConfig.forceHtmlViews`(후처리기)가 정적이 아니라 설정 클래스와 그 의존(인터셉터에서 감사 경계, 지표 레지스트리까지)이 후처리기보다 먼저 만들어졌고, 그러면 `MeterBinder` 빈이 레지스트리에 붙지 않았다. 정적 메서드로 고쳤다(스프링 권고).

