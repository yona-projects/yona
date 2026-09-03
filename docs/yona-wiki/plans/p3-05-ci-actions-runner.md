---
type: plan
id: P3-05
title: "CI/Actions — GitHub Actions 러너 아키텍처 이식"
status: planned
priority: 8
depends_on: [p3-01-observability]
blocks: []
source: docs/PARITY_BACKLOG.md#P3-05
created: 2026-08-28
updated: 2026-08-28
tags: [plan, p3, ci, runner]
---

# CI/Actions — GitHub Actions 러너 아키텍처 이식

## 배경

2026-08-24 사용자 결정: GitHub Actions 아키텍처를 그대로 이식. 범위는 핵심만 — ① 워크플로 YAML 스키마
(`on:`/`jobs:`/`steps:`/`runs-on:`), ② 러너 등록·폴링 프로토콜, ③ `run:` 직접 명령어 스텝. `uses:`(서드파티
액션)은 이번 범위에서 제외하되, 나중에 통합할 방향을 전제로 스키마를 열어둔다.

코드 검증 완료: yona에 워크플로 파싱·잡 모델·러너 관련 코드 0건(완전 신규)이지만, **트리거 레이어는 재사용 가능**
— `GitPushHooks.kt`의 `YonaPostReceiveHook`이 매 push마다 `GitPostReceiveEvent`를 발행하고, PR 생성/갱신 시
`PullRequestServiceImpl.kt`가 `PullRequestMergeEvent`를 발행함. 웹훅 시스템(`WebhookNotificationEventListener`)과
동일하게 이 기존 이벤트 버스에 새 `@EventListener`를 얹으면 core git 서빙 로직 변경 없이 트리거 구현 가능.

7개 P3 항목 중 **범위가 가장 크고 신규성이 가장 높은 서브시스템**이라 우선순위 최하위로 배치.
원본: [`docs/PARITY_BACKLOG.md#P3-05`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- 워크플로 정의: `.yona/workflows/*.yml`|`*.yaml`(GitHub의 `.github/workflows`가 아닌 yona 자체 네임스페이스)
- 트리거: `on: push`/`on: pull_request` — 기존 이벤트 버스에 리스너 추가
- 러너 등록/폴링 프로토콜(백엔드 독립적 — 로컬 프로세스/VM/K8s Pod 어디서든 서버 코드 변경 없이 동작)
- `run:` 스텝 실행 엔진(재사용 검토: `nektos/act`, MIT 라이선스 — Gitea의 `act_runner`가 동일 문제를 이미 풀어둔 선례)
- 실시간 로그 뷰(러너→서버 업로드 + 서버→브라우저 SSE 푸시)

### 제외 (비범위)
- `uses:`(서드파티 액션 다운로드·실행) — 스키마는 열어두되 이번 범위에서 실행 엔진 통합은 하지 않음
  (단, `act` 임베드를 전제로 하면 큰 추가 비용 없이 함께 들어올 수 있다는 점은 원본 백로그에 검토 필요로 남아 있음 — Step 착수 시 재확인)
- Secrets(저장소별 암호화 시크릿을 러너에 전달하는 방법) — 미결정 상태로 이 계획의 범위 밖, 별도 후속 과제

## 의존성

- **선행 조건**: [[p3-01-observability]] — 러너 폴링/잡 실행/로그 스트리밍은 운영 즉시 관측이 필요한 영역이라 계측
  인프라가 먼저 있는 편이 유리(약한 의존, 병렬 진행도 가능하나 순서상 유리하도록 배치)
- **후속 파급**: 없음 — CLI 명령(`yona runner`/`yona workflow`)은 [[p3-02-cli-and-rest-api]]의 Go 스택과 자연스럽게
  통합되지만 강한 블로킹 의존은 아님(별도 Go 모듈로도 구현 가능)

## 설계 개요

### 아키텍처 결정 — 별도 Pluggable Backend 불필요

GitHub 실제 러너 모델은 서버가 잡 실행 위치를 직접 관리하지 않는다 — 러너가 등록 토큰으로 자신을 등록하고,
아웃바운드 롱폴링으로 자기 라벨(`runs-on:`)에 맞는 잡을 서버에 물어보고, 받으면 실행 후 로그·결과를 보고한다.
이 프로토콜을 그대로 이식하면 러너가 로컬 프로세스든 VM이든 K8s Pod(GitHub 공식 `actions-runner-controller`와
동일 패턴)든 서버 쪽 코드 변경 없이 동작 — 애초에 구상했던 `CiBackend` 전략+어댑터 인터페이스는 **불필요**.

### 트리거 조건 — 워크플로 파일 존재 여부 선확인

`CiWorkflowTriggerListener`는 이벤트 수신 즉시 잡을 만들지 않고, 먼저 해당 push/PR이 가리키는 커밋의 트리에서
`.yona/workflows/` 디렉터리를 조회해 파일이 없으면 즉시 no-op. JGit으로 `RevCommit`의 트리를 열어 해당 경로만
조회(전체 워킹 카피 체크아웃 불필요) — `GitPostReceiveEvent`가 이미 `commands`(변경된 ref 목록)를 들고 있으므로
그 대상 커밋들의 트리만 확인하면 됨.

### 러너 에이전트 — `act`/`act_runner` 패턴 재사용

`nektos/act`가 워크플로 실행 엔진을 이미 구현(`run:` 스텝뿐 아니라 `uses:`의 Docker 컨테이너 액션·Composite
액션 실행까지 지원). Gitea의 `act_runner`는 서버와 등록/폴링 프로토콜로 통신하는 얇은 Go 코디네이터이고,
실제 워크플로 실행은 내부적으로 `act` 라이브러리를 호출해 잡마다 Docker 컨테이너를 띄우는 구조(러너 라벨은
`ubuntu-latest` 등 `runs-on` 값과 매칭). yona 러너도 이 구조를 채택하면([[p3-02-cli-and-rest-api]] CLI와 같은
Go 스택이라 라이브러리 임베드 자연스러움) 자체 구현 범위가 "등록·폴링·서버 API 연동"으로 줄어든다.

### 실시간 로그 뷰 — SSE

코드 검증 완료: yona 전체에 `SseEmitter`/WebSocket/STOMP 등 실시간 푸시 인프라 0건(전통적 서블릿 기반 Spring MVC +
Thymeleaf, 리액티브 스택 아님). Spring MVC는 서블릿 스택에서도 컨트롤러가 `SseEmitter`를 반환하는 방식으로 SSE를
네이티브 지원하므로 WebFlux 전환/WebSocket 의존성 추가 없이 구현 가능 — 로그는 서버→클라이언트 단방향이라
WebSocket보다 SSE가 적합. 러너가 로그 청크 업로드 → `eventPublisher.publishEvent(JobLogAppendedEvent(...))` →
워크플로 런 조회 페이지에 연결된 잡 단위 `SseEmitter` 구독자에게 실시간 전달. 완료된 로그는 별도 저장(DB/파일)해
재접속/종료 후 재생 가능하게.

## 단계별 작업 계획 (TDD)

### 1부 — 트리거 레이어(기존 이벤트 버스 재사용)

1. **Step 1 — 워크플로 파일 존재 확인 로직**
   - 실패 테스트: 워크플로 디렉터리 없는 push는 `CiWorkflowTriggerListener`가 no-op → RED → JGit 트리 조회 구현 → GREEN
2. **Step 2 — YAML 파서 + 잡 모델**(`WorkflowRun`/`Job`/`Step` 엔티티)
   - `on:`/`jobs:`/`steps:`/`runs-on:` 스키마 파싱, 스텝이 `run:` 또는 `uses:` 중 하나를 갖는 구조로 설계(스키마는 열어두되 `uses:` 실행은 미구현)
   - 실패 테스트: 유효한 워크플로 YAML → `WorkflowRun` 생성, 스키마 오류 YAML 처리 방침(잡 미생성 vs 실패 상태 런 생성) 결정 후 테스트

### 2부 — 러너 등록/폴링 API(서버 쪽)

3. **Step 3 — 러너 등록 API**(등록 토큰 발급·검증, 러너 라벨 저장)
4. **Step 4 — 러너 폴링 API**(라벨 매칭 잡 할당)
5. **Step 5 — 로그 스트리밍 수신 엔드포인트**(러너→서버)

### 3부 — 러너 에이전트(Go, `act` 임베드)

6. **Step 6 — 러너 코디네이터**: 등록/폴링 프로토콜 구현(Gitea `act_runner` 참고)
7. **Step 7 — `act` 라이브러리 임베드**로 `run:` 스텝 실행(Docker 컨테이너)

### 4부 — 실시간 로그 뷰

8. **Step 8 — `JobLogAppendedEvent` + `SseEmitter` 구독**
   - 실패 테스트: 로그 청크 업로드 후 구독 중인 SSE 클라이언트가 실시간 수신 → RED → 구현 → GREEN
9. **Step 9 — 로그 영속화 + 재생**
   - 실행 종료 후 재접속 시 처음부터 재생 가능한지 테스트

### 5부 — 배포 형태

10. **Step 10 — K8s ephemeral 러너 컨트롤러**(선택적, `actions-runner-controller` 패턴)와 **VM 상시 러너**(정적 설치) 두 배포 형태 문서화 — 프로토콜은 동일하므로 코드 변경 없음

## 완료 기준 (Definition of Done)

- [ ] 워크플로 파일이 없는 저장소는 push해도 잡이 생성되지 않음(no-op 확인)
- [ ] `run:` 스텝만으로 구성된 워크플로가 push 트리거로 실제 실행되고 결과가 저장됨
- [ ] 러너 등록 → 폴링 → 잡 할당 → 실행 → 결과 보고 전체 사이클이 로컬 러너로 수동 검증
- [ ] 실행 중인 잡의 로그가 브라우저에서 실시간(SSE)으로 보임
- [ ] 완료된 잡의 로그가 재접속 후 처음부터 재생됨
- [ ] `.yona/workflows/` 경로 확인이 전체 워킹 카피 체크아웃 없이 동작(성능 요구사항)
- [ ] `./gradlew test`(서버) + Go 러너 테스트 스위트 GREEN

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| YAML 스키마 오류 처리 | 잡 미생성 vs 실패 상태 런 생성 미결정 | Step 2 착수 시 결정 |
| Secrets 전달 방식 | 저장소별 암호화 시크릿을 러너에 안전하게 전달하는 방법 미결정 — 이번 범위 밖 | 별도 후속 계획으로 분리, 이 문서에 명시적으로 범위 제외 기록 |
| `uses:` 통합 시점 | `act` 임베드 시 추가 비용이 낮다는 판단이 있으나 검토 미착수 | Step 7 착수 시 `act`의 `uses:` 지원 범위를 재확인하고 이번 범위 포함 여부 재결정 |
| 신규 서브시스템 리스크 | 7개 P3 항목 중 가장 크고 신규성 높음 — 일정/복잡도 리스크 최대 | 1~5부를 순차 마일스톤으로 쪼개 각 부마다 독립적으로 검증·머지 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-05)
- 관련 계획: [[p3-01-observability]](약한 선행), [[p3-02-cli-and-rest-api]](CLI 통합 시 Go 스택 재사용)
- 레퍼런스: `nektos/act`(MIT), Gitea `act_runner`, GitHub `actions-runner-controller`
- 관련 소스: `domain/vcs/GitPushHooks.kt`(`YonaPostReceiveHook`), `domain/pullrequest/PullRequestServiceImpl.kt`, `domain/webhook/WebhookNotificationEventListener.kt`(참고 패턴)
