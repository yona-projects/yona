# P3-33 설계 — hg4j in-core 병합 커밋 API

관련 백로그: [[../../parity/tickets/p3-33|P3-33]]. 착수 전 설계 문서 — 승인 후 구현 시작.

## 배경 (요약, 상세는 티켓 참고)

[[../../parity/tickets/p3-27|P3-27]]에서 구현한 Mercurial PR 실제 병합(`PullRequestServiceImpl.
hgMerge()`)이 병합 커밋 하나를 만들 때마다 대상 프로젝트 저장소 전체를 임시 디렉터리에
클론(`CloneCommand`가 실제로는 `Hg.init()`+`PullCommand`로 **전체 히스토리를 changegroup
포맷으로 재인코딩/재복원**)하고 전체 작업 디렉터리를 체크아웃한 뒤, 병합 커밋 하나만 만들고
push해서 임시 클론을 통째로 버린다. 비용이 "PR이 바꾼 파일 수"가 아니라 "대상 저장소 전체
크기(히스토리+파일)"에 비례해 저장소가 커질수록/병합이 잦을수록/동시 병합이 많을수록 실제
병목이 될 것으로 판단.

## 목표

- 병합 커밋 생성 비용을 "대상 저장소 전체 크기"가 아니라 "이 병합이 실제로 바꾼 파일 수"에
  비례하도록 만든다(JGit의 inCore 3-way merge와 동등한 성격).
- 임시 디렉터리 생성/클론/체크아웃/push/삭제를 전부 제거한다.
- [[../../parity/tickets/p3-27|P3-27]]이 이미 확립한 사용자 관찰 가능 동작(성공/충돌 판정,
  fast-forward여도 명시적 머지 커밋 생성 정책, 포크 PR bookmark 흔적 정리)은 전부 그대로
  유지한다 — 내부 구현 방식만 바뀐다.

## 비목표

- Mercurial의 실제 워킹커밋(`hg commit`, 사용자가 직접 커밋하는 일반 흐름)을 in-core로
  바꾸는 것은 범위 밖이다 — 이건 PR 병합처럼 "서버가 프로그램적으로 만드는 병합 커밋"에만
  해당한다.
- subrepo(`.hgsub`)가 있는 프로젝트의 병합은 이번 범위에서 다루지 않는다(기존 `hgMerge()`도
  다루지 않았음 — 동일 한계 유지).
- 대용량 파일(largefiles/lfs) 확장은 범위 밖.

## 높은 수준의 접근

`TreeMergeCommand.call()`이 반환하는 `TreeMergeResult`는 이미 병합 결과를 **디스크에 쓰지
않은 순수 데이터**로 담고 있다:

```java
Map<String, byte[]> getChangedFiles()   // 경로 -> 병합 후 최종 콘텐츠
Set<String> getRemovedFiles()           // 삭제할 경로
Map<String, Integer> getChangedModes()  // 실행권한 등 모드 변경
boolean isConflicted()
List<String> getConflicts()
```

이 데이터 + 두 부모 노드ID + 커밋 메타데이터(author/message/date)만 있으면, `Revlog`의 기존
범용 API인 `appendRevision(byte[] content, int parent1, int parent2, ...)`을 filelog/manifest/
changelog 세 곳에 순서대로 호출해서 새 changeset을 만들 수 있다 — `CommitCommand`(일반 커밋)도
결국 이 메서드를 호출하는 건 똑같고, 차이는 "무엇이 바뀌었는지"를 디스크에서 읽어오느냐
(`CommitCommand`) vs 이미 계산된 `TreeMergeResult`에서 가져오느냐(신설 API) 뿐이다.

## 신설 API 설계 (가칭 `MergeCommitCommand`)

새 클래스로 분리한다(기존 `CommitCommand`/`MergeCommand`에 모드 플래그를 추가하는 대신) —
두 커맨드 다 "실제 워킹 디렉터리가 있다"는 전제가 코드 곳곳에 스며들어 있어서, 그 전제 자체가
없는 새 흐름을 억지로 끼워 넣으면 오히려 두 커맨드 모두 더 읽기 어려워진다. 대신 아래처럼
기존 클래스의 **재사용 가능한 조각들**(주로 `Revlog.appendRevision`, 그리고 `CommitCommand`의
`buildChangelogText`류 헬퍼 — 현재 `private static`이라 패키지 접근 가능하게 조정 필요)을
가져다 쓴다.

```java
public class MergeCommitCommand {
    public MergeCommitCommand(HgRepository repository) { ... }
    public MergeCommitCommand setParents(byte[] p1, byte[] p2) { ... }        // 병합 양쪽 부모
    public MergeCommitCommand setTreeMergeResult(TreeMergeResult result) { ... } // TreeMergeCommand 결과 그대로
    public MergeCommitCommand setAuthor(String author) { ... }
    public MergeCommitCommand setMessage(String message) { ... }
    public MergeCommitCommand setBranch(String branch) { ... }               // named branch (선택)
    public byte[] call() throws IOException, HgLockException;                // 새 changeset 노드ID 반환
}
```

`call()`의 알고리즘:

1. **락 획득**: `repository.lockStore()` + `repository.lockWorkingCopy()`(`CommitCommand`와
   동일한 패턴 — 작업 디렉터리를 안 건드려도 dirstate/기타 메타데이터 동시 접근 방지 목적으로
   워킹카피 락도 함께 잡는다. 착수 시 실제로 워킹카피 락이 필요한지, 스토어 락만으로 충분한지는
   `CommitCommand`/`MergeCommand`의 락 사용처를 더 자세히 감사해서 확정).
2. **부모 manifest 로드**: p1/p2 changelog 리비전에서 각각의 manifest 노드ID를 읽어(`CommitCommand`
   350~380행대의 `manifestP1`/`manifestP2` 로딩 로직과 동일한 방식), `TreeMap<String, String>`
   (경로 → hex필네이드+플래그)으로 병합 시작점을 만든다. **주의**: 이건 "충돌 없는 3-way
   병합 결과"를 다시 조립하는 것이지 임의의 3-way 병합 알고리즘을 새로 여기서 돌리는 게
   아니다 — 실제 병합 판정(충돌 여부/최종 콘텐츠)은 이미 `TreeMergeCommand`가 끝냈고, 여기서는
   그 결과를 changeset으로 "기록"만 한다.
3. **filelog 갱신**: `getChangedFiles()`의 각 경로에 대해, 그 파일의 filelog에
   `appendRevision(content, parentFileRev1, parentFileRev2, ...)`로 새 리비전을 추가하고
   반환된 파일 노드ID를 manifest 맵에 반영한다. `getRemovedFiles()`의 경로는 manifest 맵에서
   제거한다. `getChangedModes()`는 manifest 엔트리의 플래그 문자('x'/'l' 등)에 반영한다.
4. **manifest 리비전 추가**: 갱신된 `TreeMap`을 정렬된 텍스트로 직렬화(`CommitCommand`가 이미
   하는 것과 동일한 포맷 — 재사용/추출)해 manifest revlog에 `appendRevision(...)`으로 추가,
   두 부모 manifest 리비전을 p1/p2로 넘긴다.
5. **changelog 리비전 추가**: `buildChangelogText(manifestNode, author, secs, offsetSeconds,
   extraParts, sortedFiles, message)`(현재 `CommitCommand`의 `private static` — 패키지 전용
   유틸 클래스로 옮기거나 package-private으로 완화해 재사용)로 텍스트를 조립해 changelog
   revlog에 `appendRevision(...)`으로 추가, p1/p2를 병합 양쪽 부모로 넘긴다.
6. **반환**: 새로 만들어진 changelog 노드ID.

`PullRequestServiceImpl.hgMerge()` 쪽에서 그 뒤에 `BookmarkCommand`(이미 존재)로 toBranch
bookmark를 이 노드ID로 전진시킨다 — `MergeCommitCommand` 자신은 bookmark를 모르게 유지(단일
책임, `CommitCommand`도 bookmark를 안 건드리는 것과 동일한 경계).

## yona `PullRequestServiceImpl.hgMerge()` 재작성 방향

현재(P3-27) 흐름:

```
임시 디렉터리 생성 → clone(toDir) → update(toBranch) → fetch(fromDir) →
merge(rightHex) → [fast-forward 보정] → commit → push(tempDir → toDir) → 임시 디렉터리 삭제
```

새 흐름:

```
toDir를 직접 Hg.open() → leftHex 해석 → (포크면) fetch(fromDir)로 rightHex를 toDir에
직접 가져옴(이미 previewMerge/attemptMerge가 하는 것과 동일, 클론 없음) →
TreeMergeCommand(leftHex, rightHex).call() → 충돌이면 여기서 종료(현재와 동일) →
충돌 없으면 MergeCommitCommand(parents=[leftHex,rightHex], treeMergeResult, author, message).call()
→ BookmarkCommand로 toBranch를 새 노드ID로 전진 → (포크면) hgImportBranchWithoutBookmarkLeak과
동일한 정리
```

- **fast-forward 보정 로직 자체가 필요 없어진다** — `MergeCommitCommand`는 애초에 항상
  명시적으로 두 parent를 가진 changeset을 만들 뿐, "지름길"이라는 개념 자체가 없다(그건
  워킹카피 기반 `MergeCommand`가 dirstate를 통해 갖는 개념이었다).
- **push 단계가 완전히 사라진다** — 애초에 toDir 자신에 직접 쓰기 때문.
- `checkSignedCommitsForMerge()`(require_signed_commits 브랜치 보호 검사)는 diff 커밋 목록만
  있으면 되므로 그대로 재사용 가능.

## 재사용 vs 신규 정리

| 조각 | 상태 |
|---|---|
| `TreeMergeCommand`(충돌/변경분 계산) | 이미 있음, 그대로 재사용 |
| `Revlog.appendRevision(byte[], int, int, ...)` | 이미 있음, 그대로 재사용 |
| 부모 manifest 로드/병합 로직 | `CommitCommand`에 있음(private), 공용 유틸로 추출 필요 |
| manifest 텍스트 직렬화 | `CommitCommand`에 있음(private), 공용 유틸로 추출 필요 |
| `buildChangelogText(...)` | `CommitCommand`에 있음(private static), package-private으로 완화 또는 유틸 클래스 이동 |
| `BookmarkCommand` | 이미 있음, 그대로 재사용 |
| `repository.lockStore()`/`lockWorkingCopy()` | 이미 있음, 그대로 재사용 |
| `MergeCommitCommand`(오케스트레이션 전체) | **신규** |
| `PullRequestServiceImpl.hgMerge()` 재작성 | yona 쪽, 기존 함수 대체 |

즉 "새로 발명해야 하는" 저장소 계층 기능은 없고, **기존 조각들을 새 순서로 조합하는 오케스트레이션
계층 하나**가 신규다 — [[../../parity/tickets/p3-27|P3-27]] 완료 로그가 이미 "hg4j 신규 구현
없이 해소" 판단을 내렸던 것과 같은 근거.

## 열린 질문 / 리스크 (착수 시 확정)

1. **락 범위(미확정 — 착수 시 검증 후 방향 결정)**: 워킹카피 락이 실제로 필요한지, 스토어
   락만으로 동시성 안전이 충분한지 — `CommitCommand`/`MergeCommand`가 왜 둘 다 잡는지 코드
   근거를 더 파야 한다(가설: `MergeCommitCommand`는 dirstate/작업 디렉터리를 전혀 안 건드리므로
   스토어 락만으로 충분할 것으로 보이나, 실제로 착수해서 검증한 뒤 방향을 정한다 — 미리
   단정하지 않는다).
2. **manifest 텍스트 포맷 재현 정확도**: `CommitCommand`의 기존 직렬화 로직을 그대로
   재사용하면 문제없지만, 혹시 dirstate 관련 로직과 뒤엉켜 분리가 지저분해질 경우 별도
   구현이 필요할 수 있음 — 착수 시 실제 추출해보고 판단.
3. **copy/rename 추적**: `TreeMergeResult`가 copy/rename 메타데이터(Mercurial의 `hg cp`/
   `hg mv` 추적)까지 담고 있는지 먼저 확인한다. 담고 있으면 그대로 반영, **없으면 hg4j에
   추가 구현**한다(범위 확대를 감수하고 진행 — 사용자 결정, 2026-09-09). 기존 P3-27
   임시클론 방식은 실제 `hg merge`/`hg commit`을 거치므로 이 문제가 없었을 가능성이 높아
   반드시 비교 검증한다.
4. **실제 hg 바이너리 호환성 검증 필수**: 구현을 전부 완성한 뒤(부분 구현 상태에서 하지
   않음), hg4j로 직접 조립한 changeset이 real `hg`(CLI)로 정상적으로 읽히는지(`hg log`,
   `hg cat`, `hg verify`) 실측 검증한다 — 이 저장소가 반복적으로 강조해온 "hg4j 자체
   라운드트립만으로 끝내지 말 것" 원칙(사용자 결정, 2026-09-09).
5. **동시성 — 해결(2026-09-09 사용자 결정): JGit과 동일한 낙관적 동시성 방식을 따른다.**
   JGit 쪽 실제 코드(`createMergeCommitAndUpdateRef()`)를 확인한 결과, 대상 브랜치 갱신 시
   `setExpectedOldObjectId(leftParent)`로 "병합 계산을 시작했을 때 그 브랜치가 가리키던 값
   그대로인가"만 확인하고, 다르면(동시에 다른 병합/push가 있었으면) **대기 없이 즉시
   `PullRequestException`을 던져 호출자가 처음부터 재시도**하게 한다(`RefUpdate.Result.
   LOCK_FAILURE` 등). hg4j 자체 컨벤션(`HgRepository.java` 주석)도 커밋류 작업은 fail-fast가
   기본이고, "기다리는" 락(`lockStore(timeoutMs)`)은 push 경로 하나에만 예외적으로
   썼다가 **실제 데드락을 한 번 겪은 전례**가 있다 — 따라서 대기 방식은 채택하지 않는다.

   다만 Git과 Mercurial은 저장 모델이 달라 완전히 동일하게 옮길 수는 없다는 점을 설계에
   반영해야 한다: Git 오브젝트는 내용주소화+불변이라 **오브젝트 생성 자체는 원래 락이
   필요 없고**(동시에 여러 스레드가 서로 다른 커밋 오브젝트를 만들어도 충돌 안 남), CAS가
   필요한 건 오직 "ref가 어디를 가리키는가" 뿐이다. 반면 Mercurial의 revlog는 "파일 끝에
   이어붙이는" 구조라 **리비전 추가 자체가 이미 상호배제가 필요한 공유 자원 접근**이다 —
   그래서 `MergeCommitCommand`는 revlog 3곳(filelog/manifest/changelog) 쓰기 구간만은
   fail-fast 스토어 락(`lockStore()`, 대기 없음)으로 감싸 안전하게 하고, 그 위에 Git과
   동등한 "낙관적 확인" 한 겹을 추가한다: **락을 잡기 직전에 대상 bookmark가 여전히
   `leftHex`(병합 계산에 쓴 기준점)를 가리키는지 확인**하고, 다르면(그 사이 다른 병합/push가
   있었으면) 아예 리비전을 쓰지 않고 즉시 실패시킨다(Git의 `setExpectedOldObjectId`와 동일한
   지점의 동일한 검사). 이러면 "락을 오래 붙잡고 있다가 실패"가 아니라 "애초에 조건이 다르면
   시작도 안 함"이 되어 컨텐션 창을 최소화한다.

   **알려진 트레이드오프(문서로 남김, 완료 기준에는 영향 없음)**: 그럼에도 bookmark 확인과
   실제 리비전 쓰기 사이의 아주 짧은 창에서 경합이 나면(스토어 락으로 대부분 막히지만, 락
   획득 자체가 실패하는 경우) 이미 append된 changelog/manifest/filelog 리비전이 고아로
   남을 수 있다 — Git은 참조 안 되는 오브젝트가 결국 GC되지만, Mercurial revlog는 append-only라
   **자동으로 청소되지 않는다**(디스크에 영구히 남되, 어떤 bookmark/브랜치도 가리키지 않으니
   실사용에는 영향 없음 — [[../../parity/tickets/p3-27|P3-27]]이 이미 기록한 "포크 PR
   changeset은 되돌릴 수 없다"는 한계와 같은 종류의, Mercurial 저장 모델 자체의 근본적 제약).

## 테스트 계획

- 기존 [[../../parity/tickets/p3-27|P3-27]]의 `PullRequestServiceSpec.kt` Mercurial 7개
  시나리오를 **동일하게** 통과시켜야 한다(동작 계약은 안 바뀜 — 회귀 방지의 기준선).
- hg4j 쪽 `MergeCommitCommandTest`(가칭) 신설 — 충돌 없음/충돌 있음(호출 전에 걸러지므로
  이 커맨드 레벨에서는 충돌 케이스 자체가 안 옴, 방어적으로 assert만)/copy 추적/모드 변경
  케이스.
- 실사용 검증: 실제 `hg` CLI로 만든 두 브랜치를 hg4j로 병합 → 결과 changeset을 다시 실제
  `hg` CLI로 clone/log/cat해서 내용·부모·manifest가 real hg의 기대와 일치하는지 확인.

## 예상 규모

hg4j 쪽 신규 클래스 1개(기존 로직 추출·재배선 포함) + yona `hgMerge()` 재작성 — [[../../parity/tickets/p3-27|P3-27]]
본편보다는 작지만(핵심 3-way 계산/충돌판정은 이미 있음), manifest/changelog 텍스트 조립
로직을 `CommitCommand`에서 안전하게 추출하는 작업이 예상보다 까다로울 수 있어(위 열린 질문
2번) TDD로 단계적으로 진행 권장.
