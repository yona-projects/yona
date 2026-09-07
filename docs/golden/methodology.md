# 골든 체크: yona → yona 정적 파리티 검증 계획

## Context

`yona`(레거시, Java/Play 2.3.10/sbt, 컴파일 불가, `/Users/mzc01-search5/yona-convert/yona`)에서 `yona`(Kotlin/Spring Boot, 이 저장소)로 오랫동안 포팅 작업을 진행했지만, 레거시 코드의 모든 기능이 실제로 옮겨졌는지 확신할 방법이 없었다. 레거시는 컴파일이 안 되므로 런타임/컴파일 기반 검증은 불가능하고, 정적 분석으로 파리티를 확인해야 한다.

조사 결과 이미 두 가지 자산이 존재한다:
1. `yona` 코드 곳곳(909줄, 284개 파일)에 `// yona X.java:NNN 대응 (티켓번호)` 형태의 마이그레이션 참조 주석이 있고, 이는 `docs/parity/index.md`(218개 티켓, P0~P2는 사실상 100% `[x]`)와 연결되어 있다.
2. `yona` 저장소는 이미 자체 Serena(Java LSP) 프로젝트로 인덱싱되어 있어, 컴파일 없이도 파일 단위 심볼(클래스/메서드) 추출이 가능함이 실증되었다(`.serena/cache/java/document_symbols.pkl` 기존 생성 확인).

하지만 실질적 공백이 확인됐다: `app/playRepository`(Git/SVN 접근 계층, 23파일)와 `app/validation`·`app/errors`·`app/service`(11파일)는 `docs/parity/index.md`에서 사실상 미언급이고, 218개 티켓 중 38개는 코드에 대응 주석이 전혀 없어 grep으로 역추적이 불가능하다. `doc2/*_SUPER_AUDIT.md` 문서군은 내용 품질 문제(라인 인용 없음, 존재하지 않는 내용 서술, 다른 환경 산출물로 추정되는 리눅스 경로)로 신뢰할 수 없고 현재 작업 디렉터리에서 이미 삭제된 상태다(git status상 `D`) — 이 문서군은 이번 golden check의 근거로 쓰지 않는다.

사용자가 확정한 방향: (1) 기존 "yona 대응" 주석을 역추적해 이미 검증 가능한 항목을 저비용으로 확인하고, (2) golden 마커를 **레거시 `yona` 소스에 직접** 심어 검증의 물리적 근거를 만들고, `yona` 쪽에는 대응하는 마커를 병기한다. 범위는 **백엔드 자바 파일 315개 전체**와 **뷰 템플릿(`docs/TEMPLATE_BACKLOG.md`의 242개, 그 중 50개 `[i]` 미검증분 포함)**을 함께, **전체 심볼/파일에 일괄** 다룬다.

**레거시 수정 범위에 대한 예외**: "레거시 `yona` 소스는 절대 수정하지 않는다"는 기존 운영 규칙에 대해, 이번 golden marker 삽입(주석 한 줄만 추가, `// GOLDEN:GL-NNNNN` / 템플릿은 `@* GOLDEN:GL-NNNNN *@`)에 한해 사용자가 명시적으로 예외를 승인했다(`yona`는 이미 컴파일 불가능한 참조용 사본이라 주석 추가는 빌드/동작에 영향 없음). **이 예외는 주석 삽입에만 한정되며, 로직/구조/포맷팅 등 다른 수정은 여전히 전면 금지**다.

## 설계 원칙

- **golden marker는 `yona` 소스에 직접 삽입, `yona`의 기존 "yona 대응" 주석 컨벤션에 GL-ID를 병기** — 라인범위 퍼지 매칭보다 고유 ID grep 매칭이 라인 밀림에 안전하고 견고함. CSV/ledger는 이 마커들의 인덱스 겸 사람이 읽는 뷰.
- **CSV는 기계 처리용, MD는 사람이 읽는 최종 요약**.
- **`yona` 소스(src/**/*.kt) 탐색은 Bash grep/sed 금지, Serena `search_for_pattern` 사용** — 표준 작업 규칙(소스 코드 탐색·편집은 항상 Serena)에 따름. Bash `find`(파일 존재/목록 확인)와 비-소스 산출물(생성된 CSV/로그) 처리에는 grep/sed를 써도 된다. Serena `get_symbols_overview`는 **`yona`(ECJ 파싱 필요) 쪽 심볼 추출에** 사용.
- **fork agent 배치 재사용, Workflow 툴 신규 도입 없음** — 이번 작업은 "정해진 파일 목록을 순회하며 정형 데이터를 뽑아내는" 반복 작업으로, `coverage/index.md`/FQN→import 리팩터링 때 성공한 fork 패턴과 동일 성격.
- **자동 승격 금지** — 버킷 C(공백 후보)에서 사람이 직접 검토해 "진짜 공백"으로 확정한 항목만 `docs/parity/index.md`에 신규 티켓으로 승격. 자동 판정으로 완료/공백 딱지를 붙이지 않는다.

## 1단계 — 레거시 yona 심볼 인벤토리 + golden marker 삽입 (백엔드, 315개 파일 전체)

**1a. 추출(읽기 전용, 병렬 가능)**: 파일 단위 `mcp__serena__get_symbols_overview(relative_path, depth=1)`로 클래스/메서드/필드와 라인 범위를 추출한다. 이 툴은 파일 하나씩만 처리하므로 **fork agent 9개**로 병렬 분담한다 (각 fork가 `activate_project("/Users/mzc01-search5/yona-convert/yona")` 먼저 호출해 기존 캐시 재사용):

| fork | 대상 디렉터리 | 파일 수 |
|---|---|---|
| F1 | playRepository + playRepository/hooks | 23 |
| F2 | validation + errors + service + notification + actors | 11 |
| F3 | models (A-G) | ~35 |
| F4 | models (H-R) | ~35 |
| F5 | models (S-Z) + enumeration + resource + support | ~36 |
| F6 | controllers + controllers/api | 51 |
| F7 | utils | 51 |
| F8 | data + data/exchangers | 50 |
| F9 | mailbox(+exceptions) + actions(+support) | 21 |

산출물: `docs/golden/YONA_SYMBOL_INVENTORY.csv`
```
gl_id,yona_file,package,class_name,symbol_kind,symbol_name,start_line,end_line,priority_flag
GL-00042,playRepository/GitRepository.java,playRepository,GitRepository,method,getWatchers,412,458,HIGH
```
(`gl_id`는 1b 단계 완료 전까지는 비워두고, 1b에서 채운 뒤 1c에서 실제 소스에 삽입한다.)
`priority_flag`는 F1/F2 담당 파일과 "38개 무참조 티켓" 관련 파일이면 `HIGH`, 나머지 `NORMAL`. trivial 심볼(getter/setter 등)도 `symbol_kind=trivial`로 보존(3단계에서 필터링 여부 결정).

메인 세션 검증: CSV의 `yona_file` unique count가 315와 일치하는지, `find app -name '*.java' ! -path '*/views/*'` 결과와 대조. 누락분은 재처리.

**1b. GL-ID 할당(메인 세션, 단일 스레드)**: 9개 fork의 CSV를 취합한 뒤, `yona_file` → `start_line` 순으로 정렬해 각 행에 `GL-00001`부터 순차적으로 고정 ID를 부여한다(재사용 안 함, 병렬 할당 시 충돌 위험이 있으므로 반드시 메인 세션이 단일 스레드로 처리). 1-T단계 템플릿 242개도 이어서 번호를 부여한다.

**1c. 마커 삽입(쓰기, 소규모 배치)**: ID가 확정된 CSV를 다시 F1~F9 그룹으로 나눠 각 심볼 위치에 `mcp__serena__insert_before_symbol`로 `// GOLDEN:GL-NNNNN` 한 줄을 삽입한다. **동시 실행 fork는 2~3개로 제한**한다 — 이전 세션에서 다수의 fork가 같은 Serena 프로젝트를 동시에 편집할 때 언어서버 락 컨텐션(`'language server manager is not initialized', cancelled -32800`)이 발생한 전례가 있어, 쓰기 단계는 동시성을 낮춰 재현을 방지한다. 삽입 후 `get_diagnostics_for_file`로 해당 파일이 여전히 파싱 가능한지(구문 깨짐 없는지) 확인한다.

## 1-T단계 — 레거시 뷰 템플릿 인벤토리 + 마커 (242개)

Twirl(`.scala.html`)은 Java LSP로 파싱되지 않으므로 심볼 단위가 아닌 **파일 단위**로 처리한다(메인 세션이 직접 수행, fork 불필요):
1. `find /Users/mzc01-search5/yona-convert/yona/app/views -name '*.scala.html'`로 242개 목록 확보.
2. `docs/TEMPLATE_BACKLOG.md`를 파싱해 파일별 기존 상태(`[x]`/`[i]`/`[ ]`)를 가져온다 — 특히 `[i]`(50개, 자체 기준 미검증) 목록을 별도로 뽑아둔다.
3. 1b에서 이어받은 GL-ID를 파일당 하나씩 부여하고, 각 템플릿 파일 최상단에 `@* GOLDEN:GL-NNNNN *@` 한 줄을 삽입한다(Read/Edit로 직접 처리 — 심볼 도구 대상이 아닌 일반 텍스트 삽입).

산출물: `docs/golden/YONA_TEMPLATE_INVENTORY.csv` (`yona_view_file,gl_id,template_backlog_status,notes`).

## 2단계 — yona 역참조 인덱스 구축 (백엔드 + 템플릿 공통)

Serena `mcp__serena__search_for_pattern`으로 `src/**/*.kt`에서 이미 검증된 정규식 패턴을 적용(`relative_path="src"`, `paths_include_glob="*.kt"`로 범위 한정):

```
# 파일좌표형 (.java, .scala.html 공통)
substring_pattern: "yona[[:space:]]+[A-Za-z0-9_./-]+\.(java|scala\.html):[0-9]+(-[0-9]+)?(,[0-9]+(-[0-9]+)?)*.{0,120}대응.{0,40}"

# 역방향 제외 (의도적으로 yona에 대응 없음)
substring_pattern: "yona(에|에는|와)[[:space:]]*(없|무관|존재하지[[:space:]]*하지)"
```
각 매치에서 `yona_file`, `yona_line`, `yona_file_ref`(basename 매칭), `yona_line_range`, `ticket_ids`(`P[0-2]-\d+`/`TASK-\d+`/`그룹\d+\s*#\d+`), `pattern_type`, `is_exclusion`을 파싱한다. 콤마로 여러 구간이면 행을 전개한다.

(이 시점의 yona 주석에는 아직 GL-ID가 없으므로, 이번 1회차 매칭은 기존 방식대로 파일명+라인범위로 수행한다. 매칭된 GL-ID는 5단계에서 yona 쪽에 병기해, 다음 회차부터는 GL-ID 자체가 1차 매칭 키가 되게 한다.)

산출물: `docs/golden/YONA_REVERSE_INDEX.csv`.

## 3단계 — 대조(diff) 로직

매칭 우선순위: (1) basename + 라인범위 overlap → (2) 같은 파일 + 메서드명 문자열 포함(보조, 저신뢰) → (3) 미매치.

| 버킷 | 조건 | 처리 |
|---|---|---|
| A. CONFIRMED | 매치 있음 + ticket이 PARITY_BACKLOG에서 `[x]` | 조치 없음 |
| B. TICKET_MISMATCH | 매치 있음 + ticket이 `[ ]`/`[~]`이거나 고아 티켓 | ledger 기록, 상태 재확인 |
| C. GAP_CANDIDATE | 매치 없음 + trivial 아님 + 제외 아님 | ledger 등재, `priority_flag=HIGH` 우선 정렬 |
| D. INTENTIONAL_EXCLUDED | 역방향 패턴 매치 | 참고용, 공백 집계에서 제외 |

**38개 무참조 티켓** 별도 처리: `docs/parity/index.md`의 "yona 근거" 컬럼(파일:라인)을 역으로 1단계 인벤토리와 대조해 해당 범위에 대응하는 yona 구현이 실존하는지(파일+심볼 존재 여부) 확인하는 부록 체크리스트.

**템플릿**: `[i]`(50개) 및 2단계에서 매치가 전혀 없는 뷰 파일을 템플릿용 GAP_CANDIDATE로 분류.

## 4단계 — golden 마커 산출물

`docs/golden/index.md`:
```markdown
# Golden Parity Ledger (생성일 YYYY-MM-DD)

## 버킷 A — 확인됨 (전체, gl_id 부여 완료)
| GL-ID | yona 파일:라인 | yona 심볼 | yona 매치 | 티켓 | yona 병기 상태 |
|---|---|---|---|---|---|

## 버킷 C — 공백 후보 (HIGH 우선)
| GL-ID | yona 파일:라인 | yona 심볼 | 영역 | 티켓 후보 | 비고 |
|---|---|---|---|---|---|

## 버킷 B — 티켓 상태 불일치
## 버킷 D — 의도적 제외 (참고용)
## 부록 — 티켓 근거 재검증 (무참조 38개)
## 템플릿 — 미검증/공백 후보
```
`GL-ID`는 1b에서 확정된, 재사용하지 않는 고정 ID(레거시 소스에 실제로 삽입된 값과 동일). 버킷 C 중 사람이 검토해 **진짜 공백**으로 확정한 항목만 `docs/parity/index.md`에 신규 티켓(다음 번호부터, 승격 시점에 재확인)으로 승격하고 ledger에 `promoted_to: P?-NN`, 상태 CLOSED 표시.

## 5단계 — yona 측 GL-ID 병기 (백필)

버킷 A(확인됨)로 분류된 모든 매치에 대해, 해당 `yona_file:yona_line`의 기존 "yona 대응 (티켓번호)" 주석 끝에 `[GL-NNNNN]`을 병기한다. Serena `replace_content`로 해당 줄만 정확히 치환(유일성 확인 후 1건씩)한다. **동시 실행은 2~3개 fork로 제한**(1c와 동일한 이유 — Kotlin LSP 락 컨텐션 전례). 완료된 행은 ledger의 "yona 병기 상태"를 `DONE`으로 표시. 버킷 B/C가 나중에 해소되어 새 코드가 작성될 때도 같은 컨벤션(`// yona X.java:NNN 대응 (P?-NN) [GL-NNNNN]`)을 따른다.

## 실행 순서 요약

1. F1~F9 fork 9개 병렬 실행 (1a, 백엔드 인벤토리 추출) — 메인 세션이 결과 취합, 커버리지 검증(315개 일치 확인).
2. 메인 세션이 템플릿 인벤토리(1-T 1·2) 직접 처리.
3. 메인 세션이 GL-ID 순차 할당(1b, 단일 스레드) — 315+242개 전체.
4. F1~F9 재구성 후 2~3개씩 소규모 배치로 마커 삽입(1c) + 템플릿 마커 삽입(1-T 3), 각 파일 `get_diagnostics_for_file`로 구문 확인.
5. Serena `search_for_pattern`으로 역참조 인덱스(2단계) 구축.
6. 메인 세션이 CSV join으로 3단계 대조 실행, 버킷 분류.
7. `docs/golden/index.md` 작성(4단계).
8. 버킷 A 전체에 대해 2~3개 fork 소규모 배치로 yona 측 GL-ID 병기(5단계).
9. 아래 Sanity Check 수행.
10. `git fetch` 후 신규 산출물 커밋+push (기존 프로젝트 관례: 설명적 커밋 메시지, `git add`로 명시적 파일 지정). **`yona` 리포도 golden marker 삽입분을 별도로 커밋+push한다** — 마커는 `yona`의 자체 git 저장소 이력에도 남아야 재현·감사가 가능하다.

## Sanity Check (검증)

1. **정답 케이스 역검증**: 이미 코드 인용이 명확한 완료 티켓(예: `P1-24` → `PushedBranch.kt`)이 버킷 A로 정확히 분류되는지 수동 확인.
2. **제외 케이스 검증**: 이미 확인된 역방향 패턴 4건(`UserDetailsServiceImplSpec.kt:13`, `UserSpec.kt:16`, `IncomingMailProcessingService.kt:344`, `DataBackupServiceImpl.kt:42`)이 버킷 D로만 가고 버킷 C에 섞이지 않는지 확인.
3. **정밀도 검증**: playRepository(F1)는 언급이 2회뿐이라 버킷 C가 다수 나올 것으로 예상됨 — 그중 일부를 `find_referencing_symbols`/`find_symbol`로 사람이 직접 재확인해 false positive 비율 측정.
4. **커버리지 대조**: 1단계 CSV의 `yona_file` unique count = 315, 1-T단계 = 242, 2단계 `yona_file` 매치 수가 grep raw count(약 904줄)와 근사한지 최종 확인.
5. **마커 무결성 검증**: `mcp__serena__search_for_pattern`(`restrict_search_to_code_files=false`)으로 `yona` 전체에서 `GOLDEN:GL-\d+`를 재검색해 (a) 중복 GL-ID가 없는지, (b) CSV의 gl_id 개수와 실제 삽입된 마커 개수가 정확히 일치하는지, (c) 마커 삽입 후 `get_diagnostics_for_file`에서 새로 생긴 파싱 오류가 없는지 확인.

### Critical Files
- `docs/parity/index.md`, `docs/TEMPLATE_BACKLOG.md`
- `docs/golden/YONA_SYMBOL_INVENTORY.csv`, `YONA_TEMPLATE_INVENTORY.csv`, `YONA_REVERSE_INDEX.csv`, `golden/index.md` (신규 산출물)
- `/Users/mzc01-search5/yona-convert/yona`의 315개 백엔드 파일 + 242개 템플릿 (golden marker 주석 삽입 대상 — 이 작업에 한해 수정 허용, 그 외 수정 금지)
- `/Users/mzc01-search5/yona-convert/yona/app/playRepository/**/*.java`, `app/{validation,errors,service}/*.java` (최우선 감사 대상)
- `/Users/mzc01-search5/yona-convert/yona/app/views/**/*.scala.html` (템플릿 대상)
