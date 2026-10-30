---
title: Bootstrap 전환의 전 화면 확인 도구
type: architecture
sources:
  - ../e2e/
  - ../src/test/kotlin/dev/yona/audit/LegacyBootstrapLintTest.kt
  - decisions.md
updated: 2026-10-30
---

# Bootstrap 전환의 전 화면 확인 도구 (X-38a)

[D-53](../decisions.md)에 따라 Bootstrap 2.3.1을 5로 단계별로 올리기 전에 만든 확인 도구이다. 자동 시각 회귀 시험이 없고 템플릿이 192개라서, 핵심 화면만 보면 확인하지 않은 화면에서 깨진다. 그래서 **모든 화면**을 같은 방법으로 열고 기록해 전후를 비교한다. 코드는 `e2e/`와 `LegacyBootstrapLintTest`에 있다.

## 구성

| 구성 | 위치 | 하는 일 |
|---|---|---|
| 구조 검사 | `LegacyBootstrapLintTest`, `src/test/resources/bs/legacy-baseline.json` | 단계마다 사라져야 할 낡은 표기 20종의 수를 세어 기준과 대조한다. 늘면 실패(전환 중에 낡은 표기가 새로 들어오는 것을 막는다), 줄어도 실패(기준을 새로 쓰라는 알림, `UPDATE_BS_BASELINE=1`). `DONE_STAGES`에 올린 단계의 패턴은 0이어야 한다 |
| 서버 기동 | `e2e/lib/server.mjs` | 부트 jar를 H2 파일 DB와 전용 데이터 폴더로 띄운다. 첫 실행에는 사이트 비밀 키 설정을 채우고 다시 시작한다. 기본 브랜치는 v1.6처럼 `master`(`--yona.git-default-branch`) |
| 고정 데이터 | `e2e/seed.mjs` | 사용자 3명(admin, alice 구성원, bob 외부인), 조직 `acme`, 공개 Git(`acme/web`), 비공개 Git, Subversion, 코드(브랜치 3, 태그 2), 라벨, 마일스톤, 이슈 4, 게시글 2, 풀 리퀘스트(열림, 병합, 닫힘), 리뷰와 줄 댓글, 웹훅. 토큰은 기기 인증으로 받는다. 결과 번호는 `.state/fixture.json` |
| 화면 목록 | `e2e/screens.mjs` | 121개 화면(역할별): v1.6 경로 표의 화면 경로와 v2에서 더한 화면(토큰, 기기 인증, 태그, 규칙 묶음) |
| 순회 캡처 | `e2e/crawl.mjs` | 모든 화면을 역할별로 열어 캡처하고 콘솔 오류, 깨진 자원, 상태 코드를 기록한다. 외부 주소는 막아 네트워크에 따라 달라지지 않게 한다 |
| 상호작용 점검 | `e2e/lib/interact.mjs` (`--interact`) | 화면의 모달, 드롭다운, 탭, 팝오버, 툴팁, 접기 트리거를 눌러 열리는지 본다. `data-toggle`과 `data-bs-toggle`를 모두 찾고, 열림은 클래스 이름이 아니라 **대상이 실제로 보이는지**로 판정한다 |
| 비교 | `e2e/compare.mjs` | 두 결과를 나란히 놓고 새 문제, 고친 문제, 상태 변화, 사라진 화면을 앞에 모은다. 모양이 의도적으로 바뀌므로 픽셀 차이는 합격 기준이 아니다 |

## 원인 추적 도구 (X-38b에서 더함)

| 도구 | 하는 일 |
|---|---|
| `e2e/style-diff.mjs` | 기준 빌드(`.state/baseline/yona-pre-bs5.jar`, `pre-bs5` 태그에서 만든 jar)와 현재 빌드를 나란히 띄워 같은 화면의 요소마다 계산된 스타일을 대조하고, (요소, 속성, 이전 값 → 현재 값)별로 몇 개 요소가 바뀌었는지 센다. "어디가 다른가"를 보여 주는 캡처 비교와 달리 "왜 다른가"의 후보를 준다. `--role`, `--only`, `--moved` |
| `e2e/explain.mjs <화면 id> "<선택자>" <속성>` | 한 요소의 한 속성이 어느 규칙에서 왔는지 두 빌드에서 나란히 보인다(CDP) |
| `e2e/ids-diff.mjs <화면 id>` | id와 주요 클래스가 있는 요소의 위치와 크기를 두 빌드에서 대조한다. 요소 순서가 달라져 `style-diff`가 짝을 못 찾는 화면에 쓴다 |

기준 jar를 만들려면 `git worktree add <폴더> pre-bs5` 후 그 폴더에서 `./gradlew bootJar`를 하고 `e2e/.state/baseline/yona-pre-bs5.jar`로 복사한다. 기준 데이터는 고정 데이터를 심은 `.state/server`를 `.state/server-base`로 복사해 쓴다. 두 빌드의 서버는 새 버전 알림이 달라지지 않게 `--yona.update.check=false`로 띄운다(A-286).

## 쓰는 법

```
cd e2e
node crawl.mjs --label pre-bs5 --interact      # 서버를 띄우고(고정 데이터가 없으면 심고) 전 화면을 순회, shots/pre-bs5/
node compare.mjs pre-bs5 bs3                   # 단계를 끝낸 뒤: shots/compare-pre-bs5-bs3/index.html
UPDATE_BS_BASELINE=1 ./gradlew test --tests '*LegacyBootstrapLintTest'   # 낡은 표기가 줄어든 뒤 기준을 새로 쓴다
```

서버 jar가 필요하다(`./gradlew bootJar`). 고정 데이터를 다시 심으려면 `e2e/.state`를 지운다.

## 단계마다의 합격 기준

1. 구조 검사 통과(끝낸 단계의 패턴이 0).
2. 전 화면 순회에서 콘솔 오류, 깨진 자원이 없다(알려진 오류는 `screens.mjs`의 `knownErrors`로 목록에 남긴다).
3. 상호작용 점검에서 **실패**가 기준 순회보다 늘지 않는다.
4. 기준 순회와의 비교 모음을 훑어 이상한 화면을 사람이 상세히 본다.

## 판정 규칙과 한계

- **건너뜀**: 가려져 있거나 시간 안에 누를 수 없었던 트리거는 실패가 아니라 건너뜀으로 기록한다(사용자가 누를 수 없는 것을 깨졌다고 단정할 수 없다). 건너뜀 수는 `report.json`에 있다.
- **모달 판정**: 이 사이트는 대상 모달 대신 공용 대화상자(`yobiDialog`)를 여는 화면이 있어 "보이는 모달이 하나라도 있으면 열림"으로 본다.
- **알려진 기준 오류**: `compare` 화면의 `Cannot read properties of undefined (reading 'top')`는 v1.6 코드(`yobi.code.Diff.js`의 `_setReviewListHeight`)가 리뷰 목록이 없는 화면에서 내는 오류이다. 기준 결함이므로 목록에 `knownErrors`로 두고, 전환 중에 새로 생기는 오류와 구분한다.
- **다루지 못하는 것**: 확인 단추를 누른 뒤의 흐름(폼 제출, 삭제 확정), 메일 인증 링크처럼 비밀 값이 필요한 경로, 파일 내려받기와 JSON 경로. 폼 제출 같은 흐름은 기존 서버 시험(MockMvc)이 맡는다.
- **이주 화면(`/migration`)**: 이 서버의 설정으로는 접근할 수 없어 403이 정상이다.
- 화면은 영어(`en-US`) 로케일로 본다. 다른 언어는 메시지 번들 시험(`MessageParityTest`)이 맡는다.
