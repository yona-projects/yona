# Kotlin 전체 컴파일 조사

## 범위와 기준

- 기준 SHA: `96aeaba398f070d1b3b588b021c47262aef55826` (`Clickin/yona-spring`). 조사 시작 당시 CI 브랜치 HEAD `f6761439e`와의 차이는 `BUGFIXES.md` 기록뿐이었다. 컴파일 대상은 정확히 기준 SHA로 고정했다.
- 전용 worktree: `/Users/senghyunjo/github/yona-kotlin-perf`, 브랜치 `perf/kotlin-compilation`. 기존 worktree와 사용자의 변경은 수정하지 않았다. 적용되는 AGENTS.md/CLAUDE.md/CONTEXT.md는 발견되지 않았다.
- Kotlin/KGP 및 Spring/JPA compiler plugin **2.4.10**, Spring Boot **4.1.1**, Gradle **9.7.1**, JVM target/toolchain **21**을 유지했다.
- 과거 CI `36502233585`/`36503351830`은 cache cold/warm 전체 workflow 비교다. 그 시간 차이를 Kotlin 컴파일 시간으로 사용하지 않았다.
- 제품 변경 후보는 `build.gradle.kts`의 **`-Xbackend-threads=4` 한 옵션**이다. warning 정책, null 검사, Spring/JPA plugin, 테스트 수와 DB matrix는 유지한다. 소스 정리 후보는 성능 근거가 없어 모두 원복했다.

## 환경과 통제

| 항목 | 로컬 측정 환경 |
|---|---|
| OS | 실제 `sw_vers`: macOS 26.5.2, build 25F84 |
| 하드웨어 | Mac16,1, arm64, physical/logical CPU 각각 10개, RAM 16 GiB |
| JDK | Eclipse Temurin 21.0.6+7 LTS, arm64 |
| Gradle/컴파일 JVM | in-process 기준 `-Xmx2048m`, 기본 G1; cold daemon은 별도 2 GiB compiler heap |
| 병렬도 | Gradle `--max-workers=2`; CPU affinity/quota 제한은 적용하지 않음 |
| 외부 부하 | 일반 사용자 세션 유지, 다른 Java 프로세스 종료하지 않음. 시작 load average 4.48/2.93/2.64; 최종 표본별 load average와 소유 프로세스 CPU/RSS 기록 |
| 의존성 | 전용 `/tmp/yona-kotlin-perf-gradle-home`, 실제 `clean bootJar testClasses` 온라인 준비 실행 후 측정은 `--offline` |
| 상태 초기화 | 매 표본의 worktree `build/`, `.gradle/`, `.kotlin/`만 제거; dependency cache 보존 |
| 비활성 캐시 | task output cache와 configuration cache 모두 비활성 |
| Kotlin 증분 설정 | 저장소 기본값 유지. 이전 산출물이 없고 두 task의 report가 모두 `Non-incremental compilation will be performed: Unknown inputs changes`임을 확인 |
| JVM 재사용 | 매 표본 새 Gradle single-use JVM. daemon은 별도 run-files 디렉터리와 marker로 격리하고 소유 PID만 종료. main→test 내부 재사용은 허용 |

`--max-workers=2`는 CPU 2개 제한도, Kotlin backend thread 수 제한도 아니다. backend 4-thread 후보는 CPU 사용을 늘려 wall time을 줄이는 설정이지 소스 분석량을 줄이는 변경이 아니다. daemon 실험은 compiler heap 외 Gradle heap도 따로 존재하므로 실행 방식만의 효과라고 해석하지 않는다.

도구: Python stdlib harness, Gradle task listener, Kotlin file reports, 0.5초 간격 `ps`, JVM GC logs. JFR/`--info`/Gradle `--profile`은 별도 진단 실행이다. 일반 비교에는 같은 task listener/GC logging/process sampling을 적용했다. stdout은 W0/W2/W3에서 파일에 직접 기록하고 W1만 파일과 로컬 PTY console 양쪽으로 전달했다. W1은 GitHub 로그 전송/렌더링 측정이 아니다.

## 재현과 원자료

```bash
python3 scripts/perf/kotlin-compile/run.py \
  --repo /path/to/dedicated-checkout \
  --gradle-user-home /tmp/yona-compile-gradle \
  --results /tmp/yona-compile-results \
  --java-home /path/to/jdk21 \
  --variant prepare --prepare --allow-clean

python3 scripts/perf/kotlin-compile/run.py \
  --repo /path/to/dedicated-checkout \
  --gradle-user-home /tmp/yona-compile-gradle \
  --results /tmp/yona-compile-results \
  --java-home /path/to/jdk21 \
  --variant baseline --repeat 5 --allow-clean

python3 scripts/perf/kotlin-compile/summarize.py --results /tmp/yona-compile-results
```

`--allow-clean`은 지정한 **실험용 checkout**의 세 디렉터리를 지우는 명시적 허가다. 작업 중인 일반 checkout에 사용하지 않는다. `--prepare`는 항상 통계에서 제외한다. `--disable-incremental`은 별도 진단용이며 기본 비교에는 사용하지 않았다.

`--variant`는 결과 라벨이지 소스 revision을 바꾸는 옵션이 아니다. 기준 재현 시에는
`git worktree add --detach /path/to/dedicated-checkout 96aeaba398f070d1b3b588b021c47262aef55826`
으로 checkout을 먼저 고정하고, 측정 도구는 이 브랜치의 경로에서 실행한다.
이 기준 checkout에 각각 `--tee`(W1), `--warnings suppress`(W2),
`--strategy daemon`, `--gc Parallel`, `--compiler-arg=-Xbackend-threads=4`를
단독 적용한다. W3/source-helper는 원자료의 해당 patch를 하나씩 적용한다.
JFR 진단은 `--jfr --profile --info`, warning 이름 inventory는
`--info --compiler-arg=-Xrender-internal-diagnostic-names`를 사용했다.

두 ref의 최종 비교는 `compare.py --repo ... --baseline <SHA> --candidate <SHA> --gradle-user-home ... --results ... --java-home ... --pairs 5`로 실행한다. 이 명령은 소유하는 임시 worktree 두 개를 만들고 의존성을 사전 준비한 뒤 seed 837의 pair별 순서로 교차 실행한다. 다른 worktree는 제거하지 않는다.

로컬 원자료: `/tmp/yona-kotlin-perf-results/`.

- `environment.json`, `source-inventory.json`, `classpath-inventory.json`.
- 표본별 `command.json`, `result.json`, `changes.diff`, `build.log`, `tasks.jsonl`, `processes.jsonl`, `reports/`, GC logs.
- `runs.csv`는 실패/무효 표본도 포함. `analysis-runs.csv`/`analysis.json`은 상세 단계·GC·CPU·warning 자료. `analysis.csv`는 유효 표본의 중앙값·범위·IQR.
- `exploration-schedule.json`, `source-schedule.json`, `final-schedule.json`: 순서와 seed.
- `patches/`: 원복한 source-helper/W3 실험 패치.
- `diagnostic-jfr/`: 실제 in-process compiler JVM의 JFR 및 execution samples. 계측 표본은 일반 성능 통계와 분리.
- `regression-baseline*`, `regression-source-candidates/`: 실행된 테스트 XML과 이름/순서 비교.
- 원본 로그와 대형 binary/JFR는 Git에 넣지 않는다. 보고서 집계 스크립트는 저장된 원자료를 수정하지 않는다.

표본 유효성은 성공한 두 Kotlin compile task의 실행 상태, cache/up-to-date/skip 여부, 실제 새 compiler PID, 다운로드 부재, per-task report의 전체 컴파일 근거로 검사한다. 단순 `clean` 출력이나 `--no-daemon`만으로 판정하지 않는다.

## 어디에 시간이 쓰였나

### 소스와 classpath

| 구분 | Kotlin 파일 / physical LOC | Java 파일 / physical LOC | compile classpath |
|---|---:|---:|---|
| main | 484 / 54,437 | 1 / 2,414 | 277 JAR, 134,243,484 bytes |
| test | 473 / 122,369 | 1 / 23 | 349 JAR, 180,756,602 bytes + main 출력 3개 경로 |

task source inventory와 checkout inventory가 일치한다. 별도 KAPT/KSP 생성 source task는 없으며 compiler plugin의 생성 IR/bytecode는 backend 비용에 포함된다. 실제 compiler arguments에는 scripting, all-open, no-arg 및 관련 plugin 경로가 기록된다. compiler plugin을 제거하는 실험은 하지 않았다. Java와 Kotlin의 소스량이 크게 달라 task 시간으로 언어 간 속도를 비교할 수 없다.

### 단일 backend baseline의 단계별 중앙값 (최종 7회)

| 단계 | main | test |
|---|---:|---:|
| Kotlin task 전체 | 15.005 s | 25.817 s |
| Compiler initialization | 0.363 s | 0.093 s |
| Code analysis | 7.842 s | 10.154 s |
| Translation to IR | 1.149 s | 1.289 s |
| IR lowering | 1.599 s | 4.447 s |
| Backend | 2.174 s | 7.393 s |
| Code generation (lowering/backend 포함) | 3.762 s | 11.825 s |

자식 단계와 합계를 중복 합산하지 않는다. 단계별 중앙값의 합도 전체 시간의 중앙값과 같지 않다. test는 소스량이 크고 특히 lowering/backend 비용이 main보다 크다. 타입 추론만이 병목이라는 결론은 나오지 않았다.

별도 JFR+Gradle profile 실행에서 startup 1.402 s, settings/buildSrc 0.057 s, loading projects 0.041 s, configuring projects 0.932 s, dependency resolution 0.790 s, artifact transforms 0 s였다. dependency resolution은 다른 구간에 포함될 수 있어 전체에서 별도 차감하지 않는다. 이 값은 계측 실행 값이며 비계측 표본에 혼합하지 않았다. 초기 온라인 준비 132.51 s에는 다운로드/초기 변환이 포함되어 통계에서 제외했다.

일반 baseline 한 표본에서 Java main/test task는 각각 0.933/1.055 s, bootJar 1.504 s, plain jar 0.583 s였다. 각 표본의 task 전체 원자료가 보존된다. 이번 조건에서는 이미 준비된 classpath/Gradle 설정보다 실제 Kotlin 분석·코드 생성이 더 컸다.

GC pause의 최종 baseline 중앙값은 JVM 전체 1.335 s였다. sampled compiler/Gradle RSS는 2,707 MiB이며 heap 2 GiB와 다르다. native/code cache/metaspace 등을 포함한 RSS를 heap으로 표기하지 않았다. GC pause만으로 concurrent GC CPU 비용 전체를 나타내지는 않는다.

### JFR 및 소스 패턴

진단 JFR에 435개 execution sample이 있었다. 거친 stack 분류에서 259개는 Kotlin backend/lowering, 96개는 FIR frontend를 포함했다. coroutine 이름을 포함한 stack은 52개, `FirCallResolver`는 19개였다. 이들은 exclusive wall time이 아니고, 중첩되는 stack-family count도 있다. `CoroutineTransformerMethodVisitor`, `acceptWithStateMachine`, FIR body/call resolution을 실제로 관측했다.

정확한 2.4.10 compiler help에는 JVM per-source-file timing을 보장하는 옵션이 없었다. 따라서 이 프로파일로 `WebhookServiceSpec` 한 파일을 hotspot으로 단정하지 않았다. 1,826-line DescribeSpec initializer와 nested Kotest/MockK가 후보인 점을 이용해 등록 lambda의 본문을 private receiver helper로 그대로 추출하는 단일 A/B 변경을 시험했다. fixture 생성 시점, receiver, hook/등록 순서, assertion을 유지했다.

| 소스 실험 (각 3회) | main 중앙값 | test 중앙값 | 전체 중앙값 |
|---|---:|---:|---:|
| 같은 batch의 source-control | 15.177 s | 26.380 s | 50.436 s |
| 등록 본문 helper 추출 | 15.258 s | 28.906 s | 53.260 s |

효과가 없으므로 원복했다. 전체 파일 분할이나 광범위한 명시 타입 추가는 채택하지 않았다. 이 한 실험이 모든 DSL/generic 패턴의 비용을 반증하는 것은 아니다. 대형 production 메서드와 JSON builder도 조사 후보로 살폈지만 특정 표현식에 비용을 귀속할 증거는 확보하지 못했다.

## Warning: 출력·억제·소스 수정의 분리

정확한 compiler가 지원하는 `-Xrender-internal-diagnostic-names`로 별도 inventory를 수집했다. 기본 `extraWarnings`/`-Wextra`는 사용하지 않는다.

| 진단 | 고유 개수 |
|---|---:|
| DEPRECATION | 187 |
| PLATFORM_CLASS_MAPPED_TO_KOTLIN | 61 |
| UNNECESSARY_NOT_NULL_ASSERTION | 46 |
| USELESS_ELVIS | 43 |
| UNNECESSARY_SAFE_CALL | 15 |
| SENSELESS_COMPARISON | 6 |
| USELESS_CAST | 3 |
| UNCHECKED_CAST | 2 |
| OVERRIDE_DEPRECATION | 1 |

원래 main 142개 + test 222개 = 364개. 일반 실행의 raw warning header도 364개이고 위치/메시지로 중복 제거한 header 역시 364개다. 진단 종류와 다른 개념인 고유 메시지 문구는 38개다. Kotlin warning header는 77,283 bytes, 전체 로그는 78,249 bytes였다. javac 및 Gradle/plugin 경고와 Kotlin warning을 혼합하지 않았다.

| 조건 (각 3회) | 단일 변경 | warning 수 | main | test | 전체 |
|---|---|---:|---:|---:|---:|
| W0 | 기본, 파일 직접 기록 | 364 | 15.226 | 29.044 | 53.255 s |
| W1 | 같은 소스/설정, 파일+로컬 console | 364 | 15.476 | 29.253 | 53.297 s |
| W2 | `compilerOptions.suppressWarnings=true` | 0 | 15.234 | 28.890 | 52.583 s |
| W3 | `shouldBeInstanceOf` 직후 redundant cast 3개 제거 | 361 | 15.337 | 28.748 | 52.877 s |

W0/W2는 seed 835의 실행 설정 batch, W3는 seed 836의 source-control batch다. W3의 직접 통제군 전체 중앙값은 위의 50.436 s이며, W3를 이전 W0 대비 개선으로 해석하지 않는다. W1은 별도의 3회 console block이어서 시간 흐름에 따른 host 부하와 분리하기 어렵다. 모두 원자료 범위/IQR을 함께 확인해야 한다.

억제로 로그는 822 bytes까지 줄었지만 시간 절감은 작고 변동 범위였다. `-nowarn`이 분석을 모두 생략한다거나, warning 생성의 순수 비용을 알아냈다고 해석하지 않는다. W3는 성능 근거가 없어 원복했다. **전역 warning 억제는 최종 변경에 없다.**

`WebhookServiceSpec.forceNullField`와 실제 null-injection 테스트를 확인했다. Kotlin 정적 타입만 보고 entity fallback, `?:`, `?.`, `!!`를 일괄 제거하지 않았다. 불확실한 deprecated API 대체도 하지 않았다.

## 실행 방식·GC·backend 탐색

각 조건 3회, source baseline 고정. 초기 5조건은 seed 835로 round별 순서를 무작위화했다. 이어지는 GC/backend/source 조건은 seed 836을 사용했다.

| 조건 | main | test | 전체 중앙값 | 판단 |
|---|---:|---:|---:|---|
| in-process G1 (W0) | 15.226 | 29.044 | 53.255 s | 통제군 |
| cold Kotlin daemon (기본 Parallel GC) | 15.503 | 24.119 | 48.827 s | JVM 분리·GC·메모리 조건 함께 변화 |
| cold daemon + G1 | 15.612 | 28.098 | 52.412 s | daemon 자체만으로 개선한다고 볼 수 없음 |
| in-process + Parallel GC | 15.311 | 27.776 | 51.436 s | pause 증가, 최종 후보로 채택하지 않음 |
| in-process + backend 2 threads | 14.667 | 24.191 | 47.761 s | 유망 |
| in-process + backend 4 threads | 14.417 | 23.003 | 45.847 s | 최종 교차 반복 대상으로 선택 |

실제 daemon PID/arguments에서 별도 compiler JVM, run-files 경로, Parallel/G1 선택을 확인했고 fallback을 금지했다. daemon+Gradle의 **같은 시점** RSS를 합산한 후 peak를 계산했다. 별도 프로세스 peak를 합산하지 않았다. daemon 기본 조건의 합산 sampled peak 중앙값 약 3,057 MiB는 W0 약 2,662 MiB보다 높다.

in-process Parallel GC의 pause 중앙값은 4.524 s로 W0 1.342 s보다 길었다. heap pressure가 지배한다는 근거가 없어 heap 확대 조합 탐색은 하지 않았다. Gradle worker 수는 유지하고 지원이 확인된 backend thread 옵션만 변경했다. JDK/Kotlin 버전 비교와 장기 daemon/JIT 재사용 시나리오는 이번 조사에서 측정하지 않았다. 특정 compiler 버전 회귀를 입증할 비교 자료도 없다.

## 최종 로컬 교차 검증: 각 7회

기준 소스에서 유일한 effective compiler option 차이는 `-Xbackend-threads=4`다. 탐색 표본을 최종 표본에 합치지 않았다. pair별 baseline/candidate 순서는 seed 837로 선택했으며 모든 표본은 fresh outputs, dependency warm, JVM cold, warning 활성, in-process, 2 GiB heap이다.

| 지표 | baseline 중앙값 [범위] | 후보 중앙값 [범위] |
|---|---:|---:|
| 전체 명령 wall | 49.393 [48.535–57.823] s | 46.803 [45.136–50.916] s |
| main Kotlin task | 15.005 [14.877–16.965] s | 14.295 [14.179–16.288] s |
| test Kotlin task | 25.817 [25.206–31.945] s | 22.803 [22.335–25.247] s |
| 두 Kotlin task 합 | 40.928 [40.082–48.910] s | 37.792 [36.708–41.140] s |
| 동시 sampled JVM RSS peak | 2,707 MiB | 2,685 MiB |
| sampled JVM 누적 CPU (종료 직전 관측 lower bound) | 194.11 s | 200.80 s |
| JVM 전체 GC pause | 1.335 s | 1.323 s |

- 전체 wall 중앙값 **2.589 s / 5.24% 감소**, 두 Kotlin task 합의 중앙값 **3.136 s / 7.66% 감소**.
- wall IQR: baseline 2.436 s, 후보 2.955 s. 범위는 겹친다. 같은 pair의 후보는 7/7회 빨랐고 paired 절감 중앙값 3.738 s였다. 작은 표본의 p95/유의확률을 주장하지 않는다.
- wall 원자료, 실행순 각 조건별:
  - baseline: `51.317, 57.823, 48.874, 49.227, 49.393, 48.535, 51.656`.
  - candidate: `50.916, 48.927, 45.136, 47.731, 45.514, 45.235, 46.803`.
- RSS는 sampling 결과로 true peak가 아니다. CPU는 약 3.4% 증가했다. 메모리 절감 자체를 최적화 효과로 주장하지 않는다.
- parallel backend의 report backend 값은 main 5.370 s/test 15.163 s로 오히려 증가한다. 정확한 2.4.10의 `JvmIrCodegenFactory`/`PerformanceManager` bytecode에서 **동시에 실행되는 각 파일 worker의 elapsed timer를 합산**함을 확인했다. exclusive wall이나 CPU 시간이 아니다. `CODE_GENERATION`도 이 집계를 포함하므로 task wall과 합산하거나 성능 악화로 해석하지 않는다. 근거는 원자료의 `yona-kotlin-2.4.10-parallel-metrics-javap.txt`와 `yona-kotlin-2.4.10-time-value-javap.txt`다.
- 약 46.8 s 전체/37.8 s Kotlin task 합이 이번 후보에도 남는다. 이를 언어의 필연적인 하한으로 일반화하지 않는다. 분석·lowering·JVM/Gradle 초기화·패키징 및 아직 줄이지 못한 비용이다.

### 최종 CI 확인

로컬 검증 이후 승인받은 새 브랜치만 push했다. 제품 변경 commit은 `6ded9c787`,
측정 workflow/도구를 포함해 CI가 실행한 후보 SHA는
`b431f5787733022120a6214a7d13d7ae9792819b`다. 기존 PR 브랜치는 변경하지 않았다.

- [성능 비교 36511088200](https://github.com/Clickin/yona-spring/actions/runs/36511088200): **성공**.
  한 `ubuntu-24.04` runner에서 준비 실행 두 번을 제외하고 baseline/candidate 각각 5회 교차 측정했다.
  runner는 x86_64 AMD EPYC 7763, logical CPU 4개(2 physical cores × SMT2), RAM 16,770,748,416 bytes,
  Temurin 21.0.12.1+1이다. 확인한 cgroup 루트의 `cpu.max`/`memory.max` 파일은 없었다.
  모든 표본에 같은 JDK/heap/worker 수/캐시 정책을 사용했다.
- 10개 표본 모두 새 compiler PID와 main 484/test 473 Kotlin source를 확인했다.
  output/configuration cache는 꺼져 있고, 두 task의 실제 전체 컴파일 report를 검증했다.
  자료는 해당 실행의 `kotlin-full-compilation` artifact와 로컬 `ci/performance-results/`에 있다.

| CI 지표 (각 5회) | baseline 중앙값 [범위] | 후보 중앙값 [범위] |
|---|---:|---:|
| 전체 명령 wall | 164.427 [162.986–167.792] s | 157.351 [151.942–160.715] s |
| main Kotlin task | 51.310 [49.697–52.570] s | 50.153 [49.237–53.033] s |
| test Kotlin task | 93.569 [91.866–94.762] s | 86.804 [81.536–87.296] s |
| 두 Kotlin task 합 | 144.279 [142.527–147.331] s | 137.001 [130.773–139.837] s |
| 동시 sampled JVM RSS peak | 2,933 MiB | 2,867 MiB |
| sampled JVM 누적 CPU | 534 s | 530 s |
| JVM 전체 GC pause | 5.819 s | 5.064 s |

CI 전체 명령 wall 중앙값은 **7.076 s / 4.30%**, Kotlin task 합은 **7.279 s / 5.04%** 감소했다.
wall IQR는 각각 1.271/4.338 s이며 이번 CI 표본의 전체 wall 범위는 겹치지 않는다.
wall 원자료는 baseline `165.000, 167.792, 163.729, 162.986, 164.427`,
candidate `153.779, 151.942, 157.351, 158.117, 160.715`다.
로컬과 CI의 절대 시간을 비교하지 않았으며 각 환경 안에서만 개선량을 계산했다.
이 결과로 **backend thread 옵션 한 줄만 최종 채택**했다.

[전체 기능 CI 36511129389](https://github.com/Clickin/yona-spring/actions/runs/36511129389)는
기존 workflow를 축소 없이 실행했다. DB-independent/dedicated integration **5,511개 통과**,
Playwright **223개 통과·2개 skip**, 별도 이메일 인증 단계 **2개 통과**다.

| DB | 통과 | 실패 | 기준 대비 새 실패 ID / 기존 통과 개수 손실 |
|---|---:|---:|---:|
| H2 | 1,196 | 0 | 0 / 0 |
| MariaDB | 1,200 | 0 | 0 / 0 |
| MySQL | 1,196 | 0 | 0 / 0 |
| PostgreSQL | 1,180 | 16 | 0 / 0 |
| MSSQL | 1,192 | 4 | 0 / 0 |
| CUBRID | 1,156 | 47 | 0 / 0 |

기준 실행 `36497731127`의 XML과 `(suite, testcase)`별 상태/개수를 대조했다.
전체 CI는 기존 67개 DB 실패 때문에 **failure**이며 전체 통과로 표현하지 않는다.
기능 workflow 전체 시간 12m20s는 이번 비증분 성능 비교의 지표가 아니며,
이전 branch의 warm-cache 6m54s와 같은 조건이라고 보지 않는다.
진행 출력은 `gh run watch`로 읽지 않고 완료 callback 후 로그/artifact만 수집했다.

### 탐색 표본의 변동과 메모리

각 조건은 3회다. 시간 단위는 초, RSS는 MiB이며 daemon 조건은 같은 시점의 Gradle+compiler 합이다.
GC는 두 JVM이 존재하면 각 JVM pause 합계의 표본별 값이다.

| 조건 | main 범위 | test 범위 | 전체 wall 범위 | sampled RSS 중앙값 | GC pause 중앙값 |
|---|---:|---:|---:|---:|---:|
| W0 | 14.98–15.68 | 25.96–29.29 | 49.17–53.39 | 2662 | 1.342 |
| W1 | 15.04–15.49 | 25.57–30.31 | 49.05–54.50 | 2679 | 1.294 |
| W2 | 14.94–17.83 | 25.55–29.14 | 48.92–55.53 | 2588 | 1.345 |
| W3 | 15.04–18.77 | 25.70–28.99 | 49.56–57.04 | 2672 | 1.326 |
| source-control | 15.13–16.68 | 25.94–27.25 | 49.85–53.45 | 2754 | 1.351 |
| source-helper | 15.17–15.26 | 25.94–29.12 | 49.68–53.88 | 2619 | 1.376 |
| cold-daemon | 15.29–15.73 | 24.02–26.34 | 47.73–51.10 | 3057 | 3.117 |
| daemon-G1 | 15.51–15.65 | 26.15–32.07 | 50.19–56.41 | 3031 | 1.405 |
| parallel-gc | 15.30–15.44 | 27.20–28.37 | 50.99–52.01 | 2490 | 4.524 |
| backend-2 | 14.43–15.22 | 23.85–25.33 | 46.74–48.45 | 2663 | 1.320 |
| backend-4 | 14.18–14.42 | 22.78–25.10 | 45.39–48.09 | 2664 | 1.325 |


## 기능 보존과 한계

소스 A/B 후보를 함께 적용한 실제 테스트 실행과 baseline 실행 모두 Webhook 93개, TwoFactor 16개, LDAP 9개가 통과했다. XML testcase 이름과 순서를 비교해 동일함을 확인했다. null reflection 주입/HTTP 왕복/암호화 및 인증 관련 기존 assertion을 유지했다. Gradle engine의 상위 suite/test node 수를 leaf 테스트 수에 더하지 않았다.

영구 backend 4-thread 설정을 build script에 반영한 뒤 별도 fresh 전체 컴파일 smoke도 유효 표본으로 완료했다. 추가 compiler argument 주입 없이 실제 설정을 사용했으며, 같은 118개 테스트를 다시 실행해 이름·순서와 통과 결과를 확인했다.

최초 baseline test 실행은 `/var/run/docker.sock` 부재로 LDAP 초기화가 실패했다. 실패 XML을 보존한 뒤 활성 Docker context가 가리키는 OrbStack socket을 `DOCKER_HOST`로 지정하여 해결했다. 다른 작업의 컨테이너나 Java 프로세스를 종료하지 않았다. 이 환경 준비 실패를 제품 회귀나 유효 성능 표본으로 분류하지 않았다.

이번 조사로 개별 파일/표현식의 exclusive 비용, allocation 전체량, compiler plugin 각각의 비용, Kotlin 버전 회귀는 측정하지 못했다. GC pause/RSS/JFR sampling만으로 이를 추정하지 않는다. helper 추출과 warning 정리는 효과가 없었지만 모든 소스 패턴이 무관하다고 결론 내리지도 않는다. 로컬 arm64 수치와 CI x86 수치의 절대값은 직접 비교하지 않는다.

## 근거 자료

- [Kotlin compilation and caches / build reports](https://kotlinlang.org/docs/gradle-compilation-and-caches.html)
- [Compiler execution strategies](https://kotlinlang.org/docs/compiler-execution-strategy.html)
- [Compiler reference](https://kotlinlang.org/docs/compiler-reference.html)
- [Gradle compiler options](https://kotlinlang.org/docs/gradle-compiler-options.html)
- [Gradle performance](https://docs.gradle.org/current/userguide/performance.html)
- [JDK 21 JFR CLI](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html)

옵션 지원은 문서뿐 아니라 설치된 2.4.10 compiler의 `-help`/`-X`와 KGP bytecode로 확인했다. `-Xbackend-threads` default는 1이다. file report만으로 사용한 단계 지표가 제공된다. 현재 문서와 달리 설치된 KGP의 JSON report는 별도 `kotlin.build.report.json.directory`를 요구하므로 file report를 사용했다. 보고서의 `Incremental compilation in daemon`이라는 metric 이름은 실제 in-process/non-incremental 실행에서도 나타나므로 실행 전략/증분 여부의 증거로 사용하지 않았다.
