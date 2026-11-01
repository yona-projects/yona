# Yona 관측 스택 (개발용)

Yona가 OTLP로 보내는 지표, 추적, 로그를 Grafana 스택에서 보는 설정이다. 설명과 결정은 `yona-v2-wiki/architecture/observability.md`와 `decisions.md`(D-64)에 있다.

```
docker compose -f observability/docker-compose.yml up -d     # Grafana http://localhost:23000 (admin / admin)
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:24318 java -jar yona.jar
```

대시보드와 알림 규칙을 고치려면 `tools/generate_grafana.py`를 고친 뒤 `python3 observability/tools/generate_grafana.py`를 실행한다.
