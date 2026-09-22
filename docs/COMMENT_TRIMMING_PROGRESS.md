---
id: comment-trimming-progress
type: tracking
created: 2026-09-09
updated: 2026-09-22
status: complete
relates_to: [comment-trimming-guidelines]
---

# 주석 트리밍 진행 현황

## 4차 (2026-09-22, 이번 세션)

3차(dbb27d45f, 2026-09-17) 이후 변경된 파일 전부(git diff 기준 119개, 문서/락파일/설정파일
제외 후 106개 + 이번 세션에 새로 추가된 파일 4개 = 110개)를 대상으로 다시 트리밍했다. 이번
라운드부터는 처음으로 `e2e/`(Playwright TypeScript e2e 스위트) 디렉터리도 포함했다 — 3차까지는
Kotlin/템플릿/JS/CSS만 대상이었다.

**작업 방식**: 9개 그룹(Kotlin main 2개, Kotlin test 1개, JS/CSS/HTML 1개, e2e 5개)으로 나눠
병렬 에이전트에게 위임. 각 에이전트는 가이드라인을 먼저 읽고 자기 담당 파일만 Read+Edit로
편집했으며, gradle/npm/git 명령은 실행하지 않았다(다른 세션이 회귀 테스트를 백그라운드로 돌리고
있어 동시 실행 인프라 이슈를 피하기 위함). 코디네이터가 이후 다음을 검증:

- 전체 diff에서 comment-looking하지 않은 변경 라인을 스크립트로 걸러내 확인 — 실제 로직 변경
  0건(플래그된 라인은 전부 별도로 진행 중이던 기능 수정 커밋의 코드이거나, 멀티라인 HTML/CSS
  주석 블록의 연속 텍스트였음을 직접 확인).
- `./gradlew compileKotlin compileTestKotlin` 1회, e2e `tsc --noEmit` 1회 — 둘 다 통과.
- 가이드라인의 재확인 grep을 전체 대상 파일에 재실행 — 남은 매치는 전부 테스트명 문자열 리터럴
  (`it("... (P1-92)")` 형태, 코드이지 주석이 아니므로 대상 아님), "재현"의 정상 동사 용법,
  Thymeleaf `th:text` 날짜 포맷 샘플 값, 테스트 데이터 문자열(`'2099-12-31'`)뿐임을 확인.

**이번 라운드에 새로 확인된 패턴**: 영문 e2e 스펙 특유의 "confirmed live", "verified via ...",
"(#9 fixed)" 같은 영문 검증-시점/이슈번호 서술도 한국어 "실측 확인"/"(P#-##)"과 같은 성격으로
간주해 제거 대상에 포함했다(가이드라인 문서는 한국어 예시만 들었으나 취지상 동일).

1차~3차(326개 kt/java/템플릿) 기록은 아래 "완료" 절 참고.

코드 전반의 티켓ID/날짜/작업일지성 주석을 정리하는 작업. 기준은 [[comment-trimming-guidelines]] 참고.

## 완료 (전체 — kt/java 287개 + 템플릿 39개, 총 326개 파일)

1차(29개, 이전 세션) + 2차(286개, 이번 세션 7개 병렬 에이전트 + 코디네이터 재검증)로 전부 완료.
`git log`의 `docs: 코드 전반의 티켓ID/날짜 위주 주석을 WHY 중심으로 트리밍` 커밋들 참고.

**2차 작업 방식**: 잔여 286개 파일(kt/java 247 + 템플릿 39)을 참조밀도 기준으로 균형 있게
7개 그룹(그룹당 40~41개)으로 나눠 병렬 에이전트 7개에게 위임했다. 각 에이전트는 자기
담당 파일만 편집하고 git 커밋/진행문서 갱신은 하지 않도록 지시했으며, 전부 완료된 뒤
코디네이터가 다음을 재검증했다:

- `git diff -- src/main/kotlin src/main/java`를 주석/공백이 아닌 코드 라인만 걸러 확인한 결과
  실제 로직 변경 라인 0건 확인(순수 주석 변경만 이뤄졌음을 자동 검증).
- 템플릿 diff도 동일하게 확인 — 코드처럼 보인 라인은 전부 멀티라인 HTML/JS 주석 블록의
  연속 텍스트였음을 내용 검토로 확인.
- 할당 파일 목록(286개) vs 실제 변경된 파일 목록을 diff — 스코프 밖 파일 편집 0건, 미변경
  파일 5개는 전부 Thymeleaf `th:text` 날짜 포맷 placeholder(`2026-06-08` 등)만 있던
  false positive로 확인(`milestone/list.html`, `milestone/partial_status.html`,
  `milestone/view.html`, `search/list.html`, `site/userList.html`).
- 가이드라인의 "재확인 방법" grep 스크립트를 전체 kt/java + 템플릿에 다시 실행해 잔여 매치를
  전수 검토 — 대부분 "재현"의 일반 동사 용법(legacy 동작을 그대로 재현한다는 뜻)이거나
  `docs/...` 정상 문서 링크인 false positive였다. 그 중 실제 누락 2건을 코디네이터가 직접
  발견해 추가로 정리했다:
  - `AccessLogFilter.kt`: 남아있던 `(P2-48)` 티켓 참조 제거
  - `SshAuthServiceImpl.kt`: "mercurial/sshpeer.py 실측" → "mercurial/sshpeer.py 기준"으로 정정
  - (1차 완료로 표시돼 있었으나 실제로는 누락돼 있던 것도 함께 발견·정리)
    `YonaCubridDialect.kt`의 "실측 재현"/"실측 확인" 서술, `UserViewController.kt`의
    `[GL-controllers_UserApp-079;GL-controllers_UserApp-080]` 태그

**부수적으로 발견해 함께 정리한 코드 문제(주석 트리밍 범위 밖이지만 사소하고 확실해 즉시 수정)**:
- `GitServletConfig.kt`의 LFS/디스패처 서블릿에 남아있던 프로덕션 디버그 `println` 3곳 제거
  (그룹1 에이전트가 발견, 코디네이터가 최종 정리 단계에서 삭제).

**부수적으로 발견했으나 범위 밖이라 코드는 그대로 둔 사항(참고용 기록)**:
- `BareCommit.kt`의 `commitTextFile` 두 오버로드 간 리소스 정리(`repository.close()`) 비대칭 —
  4-인자(branchName 기반) 버전이 repository를 닫지 않음. 별도 확인 필요할 수 있음(그룹5 에이전트 보고).

## 재확인 방법

```bash
# kt/java
find src/main/kotlin src/main/java -type f \( -name "*.kt" -o -name "*.java" \) | while IFS= read -r f; do
  c=$(grep -cE "yona-wiki|P[0-9]-[0-9][0-9]*|20[0-9]{2}-[0-9]{2}-[0-9]{2}|TASK-[0-9]|실측|재현|코디네이터|GL-" "$f" 2>/dev/null)
  [ "$c" -gt 0 ] && echo -e "$c\t$f"
done | sort -rn

# 템플릿
find src/main/resources/templates -type f -name "*.html" | while IFS= read -r f; do
  c=$(grep -cE "yona-wiki|P[0-9]-[0-9][0-9]*|20[0-9]{2}-[0-9]{2}-[0-9]{2}|TASK-[0-9]|실측|재현|코디네이터|GL-" "$f" 2>/dev/null)
  [ "$c" -gt 0 ] && echo -e "$c\t$f"
done | sort -rn
```

남는 매치는 대부분 "재현"의 일반 동사 용법(legacy 동작을 그대로 재현/복제한다는 뜻)이거나
`docs/...` 정상 문서 링크다 — 내용을 읽어 실제 세션 작업일지성 서술인지 판단할 것.
