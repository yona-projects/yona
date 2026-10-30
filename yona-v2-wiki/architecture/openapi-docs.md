---
title: OpenAPI 문서(Swagger UI)
type: architecture
sources:
  - src/main/kotlin/dev/yona/config/OpenApiConfig.kt
  - src/main/kotlin/dev/yona/web/api/docs
  - src/test/kotlin/dev/yona/web/api/OpenApiDocumentTest.kt
  - src/test/kotlin/dev/yona/support/OpenApiContractSupport.kt
  - https://springdoc.org
updated: 2026-10-30
---

# OpenAPI 문서 (X-31)

결정은 [D-50](../decisions.md)이다. `/-_-api/v1` 아래의 외부 REST 전체(100개 동작)를 한국어로 문서화하고 Swagger UI로 보여 준다. 문서는 **springdoc 어노테이션으로 컨트롤러 옆에서** 만들고, 문서와 실제 동작이 어긋나지 않게 **구조 시험과 계약 시험**이 지킨다.

## 주소

| 주소 | 내용 |
|---|---|
| `/-_-api/docs` | Swagger UI 화면(`/-_-api/swagger-ui/index.html`로 이동한다). 누구나 연다 |
| `/-_-api/docs/openapi` | 명세(JSON, OpenAPI 3.1) |
| `/-_-api/docs/openapi.yaml` | 명세(YAML) |

문서 주소도 외부 API 이름 공간(`/-_-api`) 안에 둔다. 사용자 로그인 ID 예약어(`LoginIdRules.RESERVED_WORDS`)에 이미 `-_-api`가 있어 v1.6의 예약어 목록을 건드리지 않아도 되고, `/{user}` 라우트와 겹치지 않는다. 이 경로는 토큰이 필요 없어 `ApiScopeRules`가 접두사(`/-_-api/docs`, `/-_-api/swagger-ui`)의 읽기 요청을 범위 없이 허용한다. 화면의 **Authorize** 단추에 `token <토큰>`을 넣으면 `Try it out`으로 바로 호출해 볼 수 있다.

## 구성

| 파일 | 역할 |
|---|---|
| `config/OpenApiConfig` | 제목과 인증 방식(`tokenAuth`: `Authorization: token ...`), 태그 순서. **반복되는 내용을 `ApiScopeRules` 대응표에서 채우는** 사용자 정의: 토큰 인증 요구, "필요한 범위" 문장과 `x-required-scopes`, 공통 오류 응답(401, 403), 경로 매개변수 설명, 와일드카드 미디어 형식을 `application/json`으로, 응답 모델의 필수 항목(`required`) |
| `web/api/docs/ApiTags` | 영역(태그) 이름과 설명. 동작마다 태그는 하나이다 |
| `web/api/docs/*Docs.kt` | 응답과 요청 본문의 **문서용 모델**. 처리기가 JSON을 직접 읽고 쓰므로 이 클래스들은 실행 중에는 쓰이지 않고 문서에만 나타난다 |
| 컨트롤러의 `@ApiOperation`, `@ApiResponse`, `@BodyDoc` | 동작의 요약, 설명, 응답, 요청 본문. 표준 springdoc 어노테이션이다(`Operation`은 권한의 `Operation`과 이름이 겹쳐 `ApiOperation`으로 가져온다) |

범위(스코프)는 어노테이션에 적지 않는다. `ApiScopeRules`의 표가 하나뿐인 진실이고 문서가 거기서 읽는다.

## 모델을 쓰는 규칙

- JSON에 **항상 있는 키**는 널을 허용하지 않는 타입으로, **값이 `null`이거나 키가 빠질 수 있는 항목**은 `?`와 기본값 `null`로 쓴다. 응답에만 쓰이는 모델은 널을 허용하지 않는 항목이 자동으로 `required`가 된다. 요청 본문 모델은 `@Schema(requiredMode = REQUIRED)`로 밝힌 항목만 필수이다.
- Kotlin의 `isXxx` 불리언은 Jackson이 `xxx`로 바꾸므로 `@get:JsonProperty("isXxx")`를 붙인다(`isConflict`, `isMerging`, `isExclusive`).
- 키 이름과 형식은 실제 JSON과 같아야 한다. 틀리면 계약 시험이 알려 준다.
- 주석에 `/*`나 `*/`가 들어가는 경로(`swagger-ui/**` 등)를 쓰지 않는다(코틀린 주석이 끊긴다).

## 의존성의 부작용

springdoc은 Jackson 2를 끌어온다. 이것이 타임리프의 인라인 자바스크립트 직렬화 방식을 바꿔 실제 서버의 화면이 끊기는 회귀를 일으켰으므로 `spring.thymeleaf.servlet.produce-partial-output-while-processing=false`를 둔다([decisions.md](../decisions.md) A-273). `RealServerRenderTest`가 실제 서버에서 화면이 끝까지 그려지는지 지킨다.

## 시험

| 시험 | 확인하는 것 |
|---|---|
| `OpenApiDocumentTest`(구조) | 문서가 누구나 열린다. **범위 대응표의 모든 동작이 명세에 있고 명세의 모든 동작이 대응표에 있다**(홈 별칭 `/-_-api`, `/-_-api/v1/` 제외). 모든 동작에 한국어 요약과 설명, 고유한 `operationId`(자동 번호 금지), 태그 하나, 성공 응답이 있다. 성공 응답과 요청 본문의 모양이 밝혀져 있고 경로 매개변수에 설명이 있다. 토큰이 필요한 동작은 인증과 필요한 범위와 401, 403 응답이 적혀 있다 |
| `OpenApiContractSupport`(계약) | **모든 `MockMvc` 시험**에 붙는 결과 처리기이다. 시험이 `/-_-api/v1`에 요청해 JSON 응답을 받으면 그 응답이 명세의 응답 모양과 맞는지 본다: 문서에 없는 키, 널이 아닌데 빠진 키, 값 형식이 다른 것은 시험을 실패시킨다. 문서에 없는 상태 코드와 JSON이 아닌 응답은 건너뛴다 |

새 외부 API를 더하면 먼저 `ApiScopeRules`에 올리고(안 올리면 기본 거부), 구조 시험이 문서를 쓰게 만든다. 그 API의 시험을 쓰면 계약 시험이 문서의 정확도를 함께 확인한다.

## 새 API를 더할 때

1. 컨트롤러 처리기에 `@ApiOperation(operationId, tags, summary, description)`, `@ApiResponse`(성공과 주요 실패), 요청 본문이 있으면 `@BodyDoc`를 단다. 질의 매개변수는 `@Parameter`로 설명한다.
2. 응답과 요청 본문의 모양은 `web/api/docs`에 문서용 모델로 만든다.
3. `ApiScopeRules`에 (메서드, 경로, 범위)를 올린다.
4. `OpenApiDocumentTest`와 그 API의 시험을 돌려 문서와 동작이 맞는지 확인한다.
