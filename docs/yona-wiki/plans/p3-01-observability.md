---
type: plan
id: P3-01
title: "Observability(메트릭/로깅/트레이싱) 인프라 도입"
status: done
priority: 2
depends_on: []
blocks: []
source: docs/PARITY_BACKLOG.md#P3-01
created: 2026-08-28
updated: 2026-08-28
tags: [plan, p3, observability]
---

# Observability(메트릭/로깅/트레이싱) 인프라 도입

## 배경

`build.gradle.kts`에 `spring-boot-starter-actuator`+`micrometer-registry-prometheus`가 이미 포함돼 있고 `/actuator/health`,
`/actuator/prometheus`도 노출돼 있지만, 커스텀 메트릭 코드(`MeterRegistry`/`@Timed`/`Counter.builder`)가 저장소 전체에
0건이라 JVM/HTTP 기본 지표만 나가고 비즈니스 지표는 전혀 없다. 구조화 로깅과 분산 트레이싱 인프라도 없다.
`@Async`/`@EventListener` 체인이 많은 아키텍처(웹훅 발송·PR 재병합·알림메일 다이제스트)라 트레이싱 부재가 특히 아쉽다.
원본: [`docs/PARITY_BACKLOG.md#P3-01`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- 메트릭: 기존 Prometheus 스크랩 경로에 커스텀 `MeterRegistry` 계측 6곳 추가
- 구조화 로깅: `logstash-encoder` 기반 JSON 로그
- 분산 트레이싱: `micrometer-tracing-bridge-otel` + OTLP

### 제외 (비범위)
- Grafana 대시보드 자체의 패널 설계(계측이 끝난 뒤 별도 작업)
- Loki/Promtail, Tempo/Jaeger 등 수집 인프라의 K3s/Traefik 배포 자체(홈랩 인프라 구성은 이 문서의 범위 밖 — 애플리케이션 쪽 계측만 다룬다)

## 의존성

- **선행 조건**: 없음 — 7개 P3 항목 중 유일하게 완전히 독립적이며 즉시 착수 가능
- **후속 파급**: [[p3-05-ci-actions-runner]]의 러너 폴링/잡 실행, [[p3-02-cli-and-rest-api]]의 신규 REST API 모두 운영 단계에서 이 인프라를 그대로 사용하게 되므로, 먼저 끝내두면 이후 계획들의 계측 비용이 줄어든다(강한 의존은 아님, 순서상 유리할 뿐)

## 설계 개요

- **메트릭 소비처**: 기존 Prometheus 스크랩 그대로 사용, 신규 인프라 구축 불필요
- **로깅**: `logback-spring.xml` 신설 + `logstash-encoder` → JSON 포맷 → Loki/Promtail
- **트레이싱**: `micrometer-tracing-bridge-otel` + OTLP exporter → Tempo/Jaeger
- **계측 지점 6곳**(전부 실존 클래스, 코드로 확인됨):

| # | 클래스 | 계측 내용 |
|---|---|---|
| 1 | `domain/notification/NotificationEventRecorder.kt`의 `record()` | 전체 알림이 거치는 단일 지점 — `eventType`/`resourceType` 태그 카운터로 시스템 전체 활동량 |
| 2 | `IssueEvent`/`PullRequestEvent`의 draft-time 병합 확장함수(P1-38/40 대응) | "새 이벤트 저장" vs "직전 이벤트 병합" 비율 |
| 3 | `domain/notification/NotificationMailDigestScheduler.kt` | 60초 배치 처리시간, 병합률, 발송 성공/실패, 대기 큐 적체 게이지 |
| 4 | `domain/mail/ImapMailboxPoller.kt` | 폴링 사이클 소요시간, 처리 메시지 수, UID 재동기화 발생 카운터 |
| 5 | `domain/event/PullRequestMergeEventListener.kt` | 처리 PR 수, `attemptMerge` 소요시간, 충돌 상태 전이 카운터(P1-52의 5종 부수효과 실제 발동 검증용) |
| 6 | `domain/webhook/WebhookNotificationEventListener.kt` + `domain/vcs/GitPushHooks.kt` | 웹훅 발송 성공/실패(HTTP status 태그)+응답시간, git push 훅 처리시간+`PushedBranch` 갱신 건수 |

## 단계별 작업 계획 (TDD)

1. **Step 1 — 로깅 기반 정비**
   - `logback-spring.xml` 신설(JSON 인코더), 기존 텍스트 로그와 병행 출력 여부 결정
   - 검증: 로그 라인이 유효한 JSON으로 파싱되는지 통합 테스트
2. **Step 2 — 계측 지점 1(NotificationEventRecorder)**
   - `record()`에 `Counter.builder("yona.notification.events").tag("eventType", ...).tag("resourceType", ...)` 추가
   - 실패 테스트: 이벤트 기록 후 `MeterRegistry`에서 카운터 값이 태그별로 증가했는지 단언 → RED → 구현 → GREEN
3. **Step 3 — 계측 지점 2~6**
   - 지점마다 동일 패턴(테스트 먼저 → RED → `@Timed`/`Counter`/`Gauge` 추가 → GREEN) 반복
   - 배치 처리 지점(3, 4)은 소요시간을 `Timer.Sample`로, 성공/실패는 태그 있는 `Counter`로 분리
4. **Step 4 — 트레이싱 브리지 도입**
   - `micrometer-tracing-bridge-otel` 의존성 추가, OTLP exporter 설정
   - `@Async` 경계를 넘는 트레이스 컨텍스트 전파 확인(웹훅 발송 체인 하나를 골라 trace-id가 끊기지 않는지 수동 검증)
5. **Step 5 — 회귀 스위트**
   - `./gradlew test`로 계측 코드가 기존 비즈니스 로직 테스트를 깨지 않았는지 확인

## 완료 기준 (Definition of Done)

- [x] 계측 지점 6곳 모두 `/actuator/prometheus`에 커스텀 지표로 노출(전부 동일한 주입받은 `MeterRegistry` 빈을
      경유 — Prometheus 스크랩용 `PrometheusMeterRegistry`와 동일 컨테이너에 등록되므로 자동 노출)
- [x] 구조화 JSON 로그 출력 확인(`LogbackJsonLoggingSpec.kt`)
- [x] `@Async`/`@EventListener` 체인 최소 1개 경로에서 트레이스 컨텍스트가 끊기지 않음을 확인(`AsyncTraceContextPropagationSpec.kt`, taskExecutor 공유 경로 전체에 적용)
- [x] 각 계측 지점에 대응하는 단위 테스트(카운터/타이머 값 검증) 존재
- [x] `./gradlew test` 전체 GREEN 유지(5578건)

## 완료 로그 (2026-08-28)

TDD로 Step 1→2→3→4→5 순서 그대로 진행. 계측 지점 6곳 전부 기존 클래스에 `MeterRegistry` 생성자 주입(확장
함수인 계측 지점 2는 파라미터로 전달) + `Counter`/`Timer.Sample`/`gauge()` 추가, 각각 `SimpleMeterRegistry`
(또는 통합 테스트의 경우 실제 Spring 빈)로 카운터/타이머 값을 직접 단언하는 테스트를 먼저 작성해 RED→GREEN.

**TDD 과정에서 발견한 함정 4가지**:
1. **`LogbackJsonLoggingSpec`의 MDCAdapter NPE**: 독립 `LoggerContext()`는 전역 기본 컨텍스트와 달리
   `mdcAdapter`가 자동 배선되지 않아 `LoggingEvent.prepareForDeferredProcessing()`에서 NPE가 남 — `context.mdcAdapter
   = LogbackMDCAdapter()`를 명시적으로 달아 해결.
2. **ConsoleAppender의 System.out 캡처 시점**: `System.setOut()` 리다이렉트는 `JoranConfigurator.doConfigure()`
   (appender가 실제로 `start()`되는 시점)보다 반드시 먼저 해야 한다 — 나중에 하면 원래 콘솔로 새어나가 캡처가 항상
   빈 문자열이 됨.
3. **`micrometer-tracing-bridge-otel`+`opentelemetry-exporter-otlp`만 추가하면 `Tracer` 빈이 안 만들어짐**: Spring
   Boot 4.x는 트레이싱 자동구성을 `spring-boot-starter-actuator`에서 분리해 `spring-boot-starter-opentelemetry`
   스타터로 모듈화했다(공식 문서에 명확히 안 나와 있어 실제로 `NoSuchBeanDefinitionException`을 보고서야 확인) —
   의존성을 이 스타터 하나로 교체해 해결.
4. **`Tracer.withSpan()`+`Tracer.currentSpan()`으로는 컨텍스트 전파 테스트가 항상 실패**: `ContextPropagatingTaskDecorator`가
   쓰는 전역 `ContextRegistry`에는 `micrometer-observation`의 `ObservationThreadLocalAccessor`만 `META-INF/services`로
   자동 등록돼 있고(jar 안에서 실제 확인), `micrometer-tracing`의 Span 전용 accessor는 자동 등록되지 않는다 —
   `ObservationRegistry`로 Observation을 시작/스코프 진입하는 방식으로 테스트를 바꿔서 실제 운영 코드
   (`@Async` 리스너들)가 겪는 전파 경로와 동일하게 맞춰 해결. 부수적으로 `spring-boot-starter-opentelemetry`가
   OTLP 메트릭 푸시까지 자동구성해, 콜렉터가 없는 이 환경에서 컨텍스트 종료마다 연결 실패 재시도로 지연이
   발생함을 발견 — `management.otlp.metrics.export.enabled=false`로 명시적으로 꺼서 해결(메트릭은 기존
   Prometheus 스크랩으로 충분).

**의존성**: `net.logstash.logback:logstash-logback-encoder:9.0`(명시 버전 고정),
`org.springframework.boot:spring-boot-starter-opentelemetry`(버전은 Spring Boot BOM이 관리).

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| ~~착수 우선순위~~ | ~~"메트릭부터 할지 6곳 한번에 할지" 사용자 결정 대기 상태였음(원본 백로그)~~ | **해소** — Step 순서(로깅→계측 6곳→트레이싱)로 진행 완료 |
| ~~수집 인프라~~ | ~~Loki/Promtail, Tempo/Jaeger의 실제 배포는 범위 밖으로 정함~~ | **해소(범위 밖 확정 유지)** — 별도 인프라 작업으로 분리, 이 계획은 애플리케이션 계측까지만. OTLP tracing endpoint는 `YONA_OTLP_TRACING_ENDPOINT` 환경변수로 실제 배포 시 지정 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-01)
- 관련 계획: [[p3-05-ci-actions-runner]](운영 단계에서 동일 인프라 사용)
- 관련 소스: `domain/notification/NotificationEventRecorder.kt`, `domain/notification/NotificationMailDigestScheduler.kt`, `domain/mail/ImapMailboxPoller.kt`, `domain/event/PullRequestMergeEventListener.kt`, `domain/webhook/WebhookNotificationEventListener.kt`, `domain/vcs/GitPushHooks.kt`
