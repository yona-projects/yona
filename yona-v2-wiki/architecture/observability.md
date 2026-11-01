# 관측성 (X-10)

결정은 [decisions.md](../decisions.md) D-64이다. 지표, 추적, 로그를 모두 OTLP로 수집기(Grafana Alloy 또는 OTel Collector)에 보내고 Grafana 스택(Mimir 또는 Prometheus, Tempo, Loki)이 받는다. v1.6에 없는 추가 항목이다(D-28).

## 구현 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| C1 | 의존성(`starter-actuator`, `starter-opentelemetry`), 기본 꺼짐, 주소 설정 시 켜기 | 완료 |
| C2 | 상태 확인(liveness, readiness) 노출과 나머지 엔드포인트 차단 | 완료(readiness 조건은 C5에서 보강) |
| C3 | 요청 번호 MDC와 JSON 콘솔 로그 | 대기 |
| C4 | OTLP 지표와 추적과 로그, 표본 비율, 감사 `request_id` 연결 | 대기 |
| C5 | VCS, 협업, 백그라운드 도메인 지표와 readiness 조건 | 대기 |
| C6 | Alloy와 Grafana 설정 문서, 대시보드, 알림 규칙을 컨테이너로 확인 | 대기 |

## 켜는 방법

수집기 주소를 설정하면 지표, 추적, 로그 내보내기가 모두 켜진다. 주소가 없으면 아무것도 내보내지 않는다.

```
OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4318
```

개별로 끄려면 `management.otlp.metrics.export.enabled`, `management.opentelemetry.tracing.export.otlp.enabled`, `management.logging.export.otlp.enabled`를 `false`로 명시한다(명시한 값이 우선한다).

## 상태 확인 (`/-_-actuator/health`)

- 인증 없이 열고 응답은 `UP`과 `DOWN`뿐이다(상세 숨김). `/-_-actuator/health/liveness`, `/-_-actuator/health/readiness` 그룹이 있다.
- 기본 경로 `/actuator`는 v1.6의 `/:user` 라우트와 겹치므로 `-_-` 이름 공간에 둔다.
- `health` 밖의 엔드포인트(`env`, `beans`, `metrics`, `prometheus` 등)는 노출하지 않는다. 시험으로 고정한다.
