---
type: plan
id: P3-13
title: "프런트엔드 분리 (React/Vue3/Angular 등 SPA)"
status: planned
priority: 10
depends_on: [p3-02-cli-and-rest-api]
blocks: []
source: docs/PARITY_BACKLOG.md#P3-13
created: 2026-08-31
updated: 2026-08-31
tags: [plan, p3, frontend, api]
---

# 프런트엔드 분리 (React/Vue3/Angular 등 SPA)

## 배경

사용자 제안(2026-08-31) — 지금은 Spring Boot(Thymeleaf) 서버 렌더링 + jQuery 기반 레거시 위젯 시스템
(`$yobi.loadModule("...")`가 `/javascripts/service/yobi.*.js`를 동적 로드하는 방식, legacy 그대로 이식된
구조)인데, 이걸 React/Vue3/Angular 같은 현대적 SPA 프레임워크로 프런트엔드만 분리할 수 있는지 물어봄.
가능하지만 "나중에 프런트만 떼어내기"가 아니라 **별도의 큰 트랙**으로 취급해야 한다는 게 이 계획의 핵심
결론 — 아래 현황 조사 참고.

## 현황 조사(2026-08-31, 코드 확인)

- `web/` 패키지에 `@RestController`(JSON) 31개, `@Controller`(Thymeleaf HTML) 31개, 템플릿 파일 159개.
- 즉 JSON API 자체는 이미 상당히 있다 — 다만 대부분 **개별 기능의 폼 제출/액션 엔드포인트**(예:
  `BoardApiController`/`MilestoneApiController`의 생성·수정·삭제)이지, "이 화면을 SPA로 그리는 데 필요한
  모든 데이터를 한 번에 주는 화면 단위 read API"로 설계되지는 않았음.
- [[p3-02-cli-and-rest-api]]가 `/api/v1/projects/{owner}/{project}/{resource}` 네임스페이스로 이슈/PR/
  프로젝트 조회 API와 Fine-grained PAT 인증 체계를 만드는 중이라, 이 계획의 가장 직접적인 토대다 — 단
  현재는 이슈/PR/프로젝트 세 리소스만 커버(게시판/마일스톤/코드브라우저/프로젝트 설정/GNB/사용자 설정/
  관리자 화면 등은 미커버).
- 인증: `SecurityConfig.kt`가 CSRF를 아예 비활성화해두고(`csrf { it.disable() }`) 폼 로그인(세션 쿠키) +
  HTTP Basic을 함께 씀. SPA가 세션 쿠키로 갈지, [[p3-02-cli-and-rest-api]]의 토큰 인증으로 갈지 결정 필요.

## 범위

### 포함
- 프레임워크 선정(React/Vue3/Angular 중 — 아직 미결정, 이 계획 진행하며 결정)
- 화면 단위 read API 설계(현재의 액션 API 위주 구조를 보완)
- 인증 방식 결정: 세션 쿠키 유지 vs [[p3-02-cli-and-rest-api]] 토큰 체계로 통일
- 마이그레이션 전략: 화면 단위 점진 전환(Thymeleaf/SPA 공존) vs 전체 컷오버 중 결정
- 최소 1개 화면(예: 프로젝트 홈 또는 이슈 목록)의 엔드투엔드 SPA 파일럿

### 제외 (비범위)
- 159개 템플릿 전체를 이 계획 안에서 전부 전환(파일럿 이후 후속 라운드로 분리)
- 모바일 네이티브 앱(별도 논의 필요, 이 계획은 웹 SPA만)

## 의존성

- **선행 조건**: [[p3-02-cli-and-rest-api]]의 REST API 네임스페이스·인증 모델이 이 계획이 그 위에 API를
  더 쌓을지, 별도 체계로 갈지를 좌우함 — 강한 블로커는 아니지만(파일럿 화면 하나 정도는 지금 API로도
  시도 가능) 전면 전환 전에 인증 모델은 반드시 먼저 정리돼야 함
- **후속 파급**: 없음

## 설계 개요

아직 설계 확정 전 — Step 0/1(아래)에서 프레임워크·인증·마이그레이션 전략을 정하는 것 자체가 이 계획의
1차 작업이다. 착수 시 다음 질문에 먼저 답해야 한다:

1. **프레임워크**: React/Vue3/Angular 중 어느 것으로? 팀 숙련도·생태계·[[p3-07-mcp-server]]의 OAuth 2.1
   인가 화면과의 UI 톤 일치 여부가 판단 기준이 될 수 있음.
2. **인증**: 세션 쿠키(현재 방식 그대로, CSRF는 여전히 비활성 상태 유지 or 재활성화 검토) vs
   [[p3-02-cli-and-rest-api]]의 Fine-grained PAT/향후 OAuth 2.1 토큰. 브라우저 SPA는 보통 쿠키+세션이
   단순하지만, API를 CLI/MCP와 공유한다는 이 프로젝트의 방향과는 토큰 인증이 더 일관적.
3. **마이그레이션 전략**: 화면 단위로 Thymeleaf와 SPA가 한동안 공존(예: `/app/**`는 SPA, 나머지는 기존
   Thymeleaf)하는 점진 전환이 리스크가 낮음 — 전체 컷오버는 159개 템플릿을 한 번에 검증해야 해 리스크가 큼.
4. **레거시 JS 위젯 대체 범위**: `$yobi.loadModule` 계열 위젯 다수가 이번 세션에서 발견된 것처럼 실제로는
   부분적으로 깨져 있거나 legacy 그대로 이식만 된 상태(P0-27, GNB 드롭다운 색상, project.Home 모듈 누락
   등, `docs/PARITY_BACKLOG.md`/`docs/TEMPLATE_BACKLOG.md` 최근 항목 참고) — SPA 전환은 이런 결함을
   그대로 옮기지 않고 재설계할 기회이기도 하다.

## 단계별 작업 계획 (TDD)

1. **Step 0 — 결정 사항 확정**: 위 설계 개요 4개 질문에 답해 이 문서를 갱신
2. **Step 1 — 파일럿 화면 선정 + read API 설계**: 상대적으로 단순한 화면(예: 이슈 목록) 하나를 골라
   화면 단위 read API 스펙 확정
3. **Step 2 — 파일럿 화면 SPA 구현**: 선택한 프레임워크로 실제 동작하는 화면 하나를 끝까지 완성,
   Thymeleaf 버전과 병행 배치해 비교 검증
4. **Step 3 — 파일럿 결과 회고**: 개발 속도·API 설계 적합성·인증 방식 문제점을 정리해 다음 화면 확장
   여부/방법을 결정(이 계획의 완료 기준)

## 완료 기준 (Definition of Done)

- [ ] 프레임워크/인증/마이그레이션 전략이 이 문서에 확정 기록됨
- [ ] 파일럿 화면 1개가 실제 SPA로 동작하고 기존 Thymeleaf 화면과 기능 동등성 확인됨
- [ ] 파일럿 회고 결과에 따라 "전면 확장 진행" 또는 "보류/재설계" 결정이 이 문서에 기록됨
- [ ] `./gradlew test` 전체 GREEN(백엔드 API 변경분 한정)

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 프레임워크 미결정 | React/Vue3/Angular 중 아직 아무것도 정해지지 않음 | Step 0에서 팀 판단으로 확정 |
| API 재설계 범위 | 현재 API가 액션 위주라 화면 단위 read API를 상당 부분 새로 만들어야 함 — 범위가 159개 템플릿만큼 클 수 있음 | 파일럿(Step 1~2)으로 화면 1개 기준 실제 작업량을 먼저 측정 |
| 인증 모델 이원화 위험 | 세션 쿠키와 [[p3-02-cli-and-rest-api]] 토큰 인증이 병존하면 유지보수 부담 증가 | Step 0에서 하나로 통일하거나, 통일 시점을 명시적으로 미루는 결정을 문서화 |
| 레거시 위젯 결함 이월 | 부분적으로 깨진 `$yobi.loadModule` 위젯의 "의도"를 SPA로 잘못 그대로 재현할 위험 | 화면 전환 시 legacy(`~/yona-convert/yona`) 원본 동작을 다시 대조 확인 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-13)
- 관련 계획: [[p3-02-cli-and-rest-api]](REST API/인증 토대), [[p3-07-mcp-server]](동일 REST API를 소비하는 또 다른 클라이언트)
- 관련 소스: `web/*Controller.kt`(31개 REST + 31개 View), `src/main/resources/templates/**`(159개),
  `config/SecurityConfig.kt`
