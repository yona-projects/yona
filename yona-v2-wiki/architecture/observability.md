# 관측성 (X-10)

결정은 [decisions.md](../decisions.md) D-64이다. 지표, 추적, 로그를 모두 OTLP로 수집기(Grafana Alloy 또는 OTel Collector)에 보내고 Grafana 스택(Mimir 또는 Prometheus, Tempo, Loki)이 받는다. v1.6에 없는 추가 항목이다(D-28).

## 구현 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| C1 | 의존성(`starter-actuator`, `starter-opentelemetry`), 기본 꺼짐, 주소 설정 시 켜기 | 완료 |
| C2 | 상태 확인(liveness, readiness) 노출과 나머지 엔드포인트 차단 | 완료(readiness 조건은 C5에서 보강) |
| C3 | 요청 번호 MDC와 JSON 콘솔 로그 | 완료 |
| C4 | OTLP 지표와 추적과 로그, 표본 비율, 비밀값과 URL 규칙 | 진행 중(내보내기 완료) |
| C5 | VCS, 협업, 백그라운드 도메인 지표와 readiness 조건 | 대기 |
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

