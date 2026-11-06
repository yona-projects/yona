---
title: Mercurial(X-7) 설계: Git 접점 조사와 공통화 계획
type: plan
sources: src/main 전수 검색, hg4j 1.1.0 소스, next 브랜치 P3-12와 P3-33
updated: 2026-11-06
---

# Mercurial(X-7) 설계: Git 접점 조사와 공통화 계획

> **상태: 설계와 검토 단계(2026-11-06). 구현은 시작하지 않았다.** 결정 질문은 [decisions.md](../decisions.md) D-86에 있고 선생님의 답을 기다린다. 이 항목은 v1.6에 없는 **추가 항목**이다(D-28). v1.6의 기존 동작과 계약은 바꾸지 않고 새 경로만 더한다.

## 1. 목적과 범위

`io.github.search5.hg4j`(Pure Java Mercurial)로 프로젝트의 저장소 종류에 Mercurial을 더한다. 선생님이 정하신 순서는 다음과 같다.

1. 먼저 **Git이 프로그램의 어디에 닿는지**를 조사한다.
2. 그 가운데 **Git, Subversion, Mercurial이 공통으로 겪는 처리 과정**을 가려내어 리팩터링 대상으로 삼는다.
3. 구현에 앞서 이 보고서를 검토한다.

로드맵 X-7의 범위(코드 보기, 커밋 이력, 브랜치 개념, HTTP와 SSH 서빙, zip 내려받기, 브랜치 보호와 push 알림, 웹훅, 풀 리퀘스트 병합, GPG 서명)를 기준으로 하되, 아래 8절의 질문에 답이 나온 뒤에 단계를 확정한다.

## 2. 조사 방법

- Git 접점은 `src/main`의 `org.eclipse.jgit` 사용 파일, `openRepository(`, `project.vcs`, `VCS_GIT`, `@IsOnlyGitAvailable`, 템플릿과 JS의 Git 전용 분기를 전수 검색해 모았다.
- Subversion이 이미 두 번째 종류로 들어와 있으므로 그 통합 방식(`SvnCodeBrowser`, `SvnRepositories`, `ProjectVcsService`)을 선례로 읽었다.
- hg4j는 `~/yona-convert/hg4j`(버전 1.1.0, Maven Central 게시, Mercurial 7.2 기준)의 README, CHANGELOG, `api`와 `transport` 패키지를 읽었다.
- `next` 브랜치에 hg4j를 쓴 이전 시도(P3-12, P3-27, P3-33)의 계획서와 구현이 있어 교훈을 가져왔다(5절).
- 이 컴퓨터에는 실제 `hg` 7.2가 설치되어 있어 상호운용 시험에 쓸 수 있다.

## 3. Git이 닿는 곳(조사 결과)

규모는 다음과 같다. JGit을 직접 쓰는 파일은 37개, 저장소를 여는 곳(`openRepository`)은 41곳, 종류를 가르는 비교(`vcs ==`, `VCS_GIT` 등)는 39개 파일 75줄, `@IsOnlyGitAvailable`은 15개 파일 37곳이다. 템플릿 16곳과 JS(`yobi.git.View.js`, `yobi.git.Write.js` 등)에도 Git 전용 분기가 있다.

| 영역 | 주요 위치 | Git 전용 요소 | Subversion의 현재 처리 | hg4j로 가능한가 |
|---|---|---|---|---|
| 저장소 수명(만들기, 지우기, 옮기기, 이름 바꾸기) | `RepositoryService`, `GitRepositories`, `SvnRepositories` | `repo/git/<소유자>/<이름>.git` 베어 저장소 | `repo/svn/<소유자>/<이름>` | 가능. `InitCommand`로 만들고 디렉터리 이동은 같다 |
| 포크, 복제 | `RepositoryService.cloneRepository` | JGit 로컬 복제 | 지원 안 함 | 가능. `CloneCommand`(로컬 pull 방식) |
| 원격 가져오기(프로젝트 가져오기) | `ProjectImportService`, `GitRepositories.cloneRemote` | JGit 원격 복제, 인증 | 지원 안 함 | 가능. HTTP, SSH pull. 인증 제공자가 있다 |
| 코드 보기(폴더, 파일, README, raw) | `CodeBrowserService`, `ProjectReadmeService`, `RepositoryFileReader` | `TreeWalk`, `ObjectId`, 브랜치 이름으로 해석 | `SvnCodeBrowser` | 가능. `ManifestCommand`, `CatCommand`. 폴더 구조는 평평한 매니페스트를 경로로 묶어야 한다 |
| 커밋 이력, 커밋 상세, 부모, 작성자 | `CommitHistoryService`, `CommitInfo` | `RevWalk` | 이미 `CommitInfo`로 중립화됨 | 가능. `LogCommand`(경로, 시작 리비전 지원) |
| 브랜치와 태그 | `BranchService`, `TagService`, `ExternalTagApiController` | `refs/heads`, `refs/tags`, 주석 태그 | 브랜치는 `HEAD` 하나, 태그 없음 | 가능하나 **개념이 다르다**(8절 질문 1) |
| diff, 커밋 변경, 비교 | `DiffService`, `FileDiff`, `V16DiffAlgorithm`, `DiffHtmlRenderer` | JGit `DiffEntry`, `RawText` | `svn-diff.html` 별도 화면 | 가능. `DiffCommand` 또는 **파일 내용 두 벌로 `V16DiffAlgorithm`을 다시 돌리는 방식**(권장, 6.4절) |
| 변경한 줄의 작성자 추정(리뷰어 추천) | `RelatedAuthorsService` | JGit `blame`, `DiffFormatter`, 이름 바꿈 추적 | 해당 없음 | 가능. `AnnotateCommand`와 복사 메타데이터. 코드 보기 화면에 blame은 없다 |
| zip 내려받기 | `CodeBrowserService.exportZip` | JGit archive | `SvnRepositories.exportZip` | 가능. `ArchiveCommand`(zip) |
| HTTP 서빙 | `GitHttpController`, `GitHttpService` | JGit 스마트 HTTP(`UploadPack`, `ReceivePack`) | WebDAV(`SvnDavService`) | 가능. `HgHttpWireServer`(jakarta 서블릿, 훅, `HgWebPermissions`) |
| SSH 서빙 | `GitSshHandler`, `SshVcsHandler`, `SshCommandRunner` | `git-upload-pack`, `git-receive-pack` | 없음(하지 않기로 함) | 가능. `HgSshWireServer`(`hg -R <저장소> serve --stdio`). **처리기 인터페이스가 이미 있다**(D-66 6번) |
| 인가(HTTP 기본 인증, 접근 권한, 시도 횟수 제한) | `GitHttpController.handle`, `SshAccess`, `ProjectCheckStage` | 종류와 무관한 부분이 대부분 | 별도 DAV 인가 | 공통. 컨트롤러에서 종류와 무관한 부분을 뽑아야 한다 |
| 푸시 전 검사(보호 규칙, 서명 검증) | `GitHooks.preReceive`, `Ruleset.pushViolation` | JGit `ReceiveCommand` 형태로 정의됨 | 없음 | 부분 가능. `registerPreTxnChangegroupHook`, `registerPrePushkeyHook`로 같은 일을 할 수 있다 |
| 푸시 뒤 처리(알림, 웹훅, 이슈 참조, PR 검사, 마지막 푸시 시각) | `GitHooks.postReceive`, `PostReceiveJobs` | `ReceiveCommand`, `RevWalk`로 새 커밋을 구한다 | 없음 | 부분 가능. `registerPostChangegroupHook`에서 노드 범위를 얻는다 |
| 풀 리퀘스트(병합 시도, 병합, 스쿼시, 리베이스, 브랜치 삭제와 복원, 변경 목록) | `PullRequestMerger`, `PullRequestChangesService`, `PullRequestBranchService`, `V16RecursiveMerger` | `refs/yobi/pull/<번호>/head, merged` 숨은 ref, JGit RECURSIVE 병합 | Git 전용(`@IsOnlyGitAvailable`) | **가장 어렵다**. 병합 엔진과 디스크 없는 병합 커밋(`TreeMergeCommand`, `GraftInMemory.commit`)은 있으나 숨은 ref와 병합 방식 세 가지의 대응이 필요하다(8절 질문 3) |
| 규칙(브랜치 보호) | `Ruleset`, `RulesetService`, `RulesetController` | 브랜치 이름 패턴, 푸시 종류(생성, 갱신, 삭제, 강제 갱신) | 해당 없음 | 개념 대응이 필요(8절 질문 1) |
| LFS | `LfsSupport`, `ProjectLfsController`, `LfsSshHandler` | 서버 쪽은 이미 VCS 중립(D-79 15번), 포인터 판별만 Git 전용 | 해당 없음 | 가능. hg4j에 `lfs` 패키지와 서버가 있다. 주소 규칙은 실제 `hg`로 확인해야 한다 |
| 위키 | `WikiService`, `WikiRepositories` | 항상 Git(D-60) | 같음 | **바꾸지 않는다**. 프로젝트 종류와 무관 |
| GPG 서명 검증 | `GitHooks.unverifiedCommits`, `gpg-signing.md` | Git 커밋 서명 | 해당 없음 | 별도 판단. hg4j에 `gpg`와 `sign` 명령이 있다. 서명 방식이 Git과 다르다 |
| 프로젝트 생성, 가져오기, 설정 화면 | `ProjectCreateController`(`VcsOption`), `project/create.html`, `importing.html`, `ProjectSettingController` | 종류 선택 목록은 둘뿐, 가져오기는 `vcs = "GIT"` 고정 | 선택 가능 | 선택지와 문구 추가 |
| 저장소 종류 변경(`changeVCS`) | `ProjectVcsService`, `Project.nextVcs()` | **`GIT`와 `Subversion` 둘을 번갈아 바꾼다**(`nextVcs`) | 같음 | 종류가 셋이 되면 "다음"이 의미가 없다. 대상을 고르는 화면이 필요(8절 질문 6) |
| REST API | `ExternalProjectManageApiController`, `ExternalTagApiController`, `ExternalForkApiController`, `ExternalDeployKeyApiController`, `ProjectDocs` | `vcs` 값 검증과 Git 전용 거절 | 같음 | 허용 값, 거절 문구, 문서 갱신 |
| 지표와 감사 | `VcsMetrics` | `vcs` 태그 `git`, `svn` | 같음 | `hg`만 더하면 된다(설계되어 있음) |
| 기타 연결 | `AutoLinkRenderer`(커밋 링크), `MentionListService`, `RelatedAuthorsService`, `NotificationReceivers`, `UpdateCheckService` | 커밋 해시가 Git 객체라는 가정 | 일부 분기 | 해시 해석을 한 곳으로 모아야 한다 |
| 데이터 교환, 마이그레이션 | `DataService`, `DataTables`, `project.vcs` 열 | 열 값 `GIT`, `Subversion` | 같음 | 열 값 추가(스키마 변경 없음). 가져오기와 내보내기 시험 필요 |
| 시험 | 24개 시험 파일이 JGit이나 Git을 쓰고 50개가 Subversion을 다룬다 | | | 종류별 공통 시험을 만들 수 있다(7.6절) |

### 3.1 한눈에 보이는 구조적 사실

- **저장소 접근은 `RepositoryService`가 문이다.** 그러나 그 문 뒤로 `org.eclipse.jgit.lib.Repository`를 그대로 내어 준다(`openRepository`). 호출한 쪽(41곳)이 JGit 타입을 쓰므로 Mercurial을 더하려면 이 반환형부터 바꿔야 한다.
- **중립화가 이미 된 곳이 있다.** `CommitInfo`, `BranchInfo`, `TagInfo`, 코드 브라우저의 맵 모양은 Git과 Subversion이 같이 쓴다. 이 모양을 Mercurial도 따르면 화면과 JSON 응답이 그대로 쓰인다.
- **SSH 처리기와 LFS는 이미 VCS를 나누어 설계되어 있다.** Mercurial은 같은 자리에 더하면 된다.
- **푸시 훅이 JGit 타입으로 정의되어 있다.** `Ruleset.pushViolation(ReceiveCommand.Type)`, `GitHooks`, `PostReceiveJobs`가 `ReceiveCommand`, `ObjectId`, `RevWalk`를 시그니처에 둔다. 푸시 뒤 처리의 본문(이슈 이벤트, 알림, 웹훅, PR 갱신)은 종류와 무관하지만 입구가 JGit이다.
- **풀 리퀘스트가 Git의 ref 모델에 가장 깊이 묶여 있다.** 숨은 ref(`refs/yobi/pull/...`)와 JGit의 RECURSIVE 병합에 기대고, 풀 리퀘스트 관련 컨트롤러 8개가 `@IsOnlyGitAvailable`이다.

## 4. hg4j가 제공하는 것

**있는 것**(소스에서 확인):

| 필요 | hg4j |
|---|---|
| 저장소 만들기, 열기 | `Hg.init()`, `Hg.open()`, `HgRepository` |
| 읽기 | `CatCommand`, `ManifestCommand`, `LogCommand`(경로와 시작 리비전), `DiffCommand`(리비전 두 개), `AnnotateCommand`, `TagsCommand`, `BranchesCommand`, `BookmarkCommand`, `HeadsCommand`, `ArchiveCommand`(zip 등) |
| 쓰기(서버에서 병합 커밋 만들기) | `TreeMergeCommand`(디스크 없이 3자 병합, `TreeMergeResult`), `GraftInMemory`(작업 디렉터리 없이 접붙이기), `CommitCommand`, `RebaseCommand` |
| 전송 | `PullCommand`, `PushCommand`, `CloneCommand`, HTTP와 SSH 클라이언트 |
| 서빙 | `HgHttpWireServer`(서블릿), `HgSshWireServer`(채널과 무관한 프로토콜), `HgWebPermissions` |
| 훅 | `registerPreChangegroupHook`, `registerPreTxnChangegroupHook`, `registerPostChangegroupHook`, `registerPrePushkeyHook`, `registerPostPushkeyHook`(Git의 pre/post-receive에 해당) |
| 원격 사용자 | `setRemoteUserResolver`(HTTP 인증을 마친 사용자를 넘긴다) |
| 잠금 | 실제 `hg`와 같은 잠금 파일과 순서(`wlock`, 저장소 `lock`)를 써서 **hg4j와 실제 hg가 서로를 막는다** |
| LFS, GPG | `lfs` 패키지(포인터, 서버), `gpg` 패키지와 `sign`, `sigs` 명령 |

**없거나 한계가 있는 것**(CHANGELOG의 알려진 문제와 소스 확인):

- **병합 커밋을 디스크 없이 저장하는 기능은 hg4j 안에 이미 있으나 공개되어 있지 않다.** `GraftInMemory.commit(트랜잭션, 변경로그, 부모1, 부모2, 원본, 원본 리비전, 병합 결과, 메타데이터 채우기)`가 두 번째 부모를 받아 파일 리비전, 매니페스트, 체인지셋을 쓰고, `RebaseCommand`가 병합 커밋을 다시 적용할 때 이것을 쓴다. 다만 패키지 전용이라 Yona가 부를 수 없다. `next`의 P3-33이 제안한 `MergeCommitCommand`를 새로 만들 필요는 없고, **이 경로를 부르는 공개 진입점 하나(잠금과 트랜잭션 포함)를 더하면 된다**(2026-11-06 재확인. 처음 조사에서는 이름 몇 개로만 검색해 "없다"고 잘못 적었다).
- Mercurial 7.2와 7.2.4만 시험되었고, wire protocol v2, `largefiles`, `upgrade`는 없다.
- `TransportE2EMatrixInteropTest`의 "머리를 만드는 푸시 거절" 시험이 전체 실행에서 가끔 실패한다는 보고가 있다(원인 미상).
- 폴더 트리는 평평한 매니페스트로 주어진다. `TreeWalk.setRecursive(false)`와 `PathFilter`로 한 단계씩 걷는 것은 가능하나 하위 폴더 항목 자체는 내주지 않으므로, 코드 보기용 폴더 목록은 서버가 경로 앞부분으로 묶는다(작은 코드).
- Java 21 필요. 이 프로젝트의 JDK와 맞는지 단계 0에서 확인한다.

hg4j는 선생님이 직접 관리하시므로 위 한계는 **hg4j에 요청하거나 직접 보완하는 선택지**가 있다. 로드맵에는 "hg4j를 먼저 보완한 뒤 착수"로 적혀 있다. 재확인 결과 보완이 꼭 필요한 것은 **병합 커밋 공개 진입점 하나**이고, 폴더 목록 묶기와 bookmark 변경 처리(`registerPrePushkeyHook`, `registerPostPushkeyHook`가 이미 있다)는 Yona 쪽에서 해결된다.

### 4.1 hg4j 담당 세션이 확인해 준 훅 계약(2026-11-06)

hg4j 담당 세션(`hg-da`)이 소스를 읽고 회신한 내용이다. 푸시 이벤트 중립화(6.3절)의 근거로 쓴다.

| 훅 | context 값 | 비고 |
|---|---|---|
| `postChangegroup` | `repository`, `nodes`(새로 들어온 체인지셋의 40자 hex 목록, 적용된 순서) | 노드 범위가 아니라 **목록**이다. 첫 노드, 마지막 노드, 개수는 목록에서 구한다. bundle1과 bundle2 푸시가 같은 경로라 키가 같다. 저장소 잠금을 놓은 뒤 실행된다. **이미 서버에 있는 체인지셋만 담긴 번들을 다시 푸시하면 훅이 불리고 `nodes`에 이미 있던 노드가 전부 들어온다**(hg4j 담당 세션 실측: 번들 2개 재푸시에서 훅 2회, 저장소는 중복 저장되지 않음). 실제 hg 7.2의 `unbundle`은 새 체인지셋이 없으면 훅을 실행하지 않는다. 실제 hg 클라이언트의 일반 푸시는 "no changes found"로 끝나 이런 번들을 보내지 않으므로 보통은 영향이 없다. **Yona는 `nodes`를 새 체인지셋으로 그대로 믿지 말고 푸시 전후 `heads` 또는 체인지셋 존재 여부로 걸러 쓴다.** hg4j를 고칠지는 선생님 결정 대기 |
| `preChangegroup`, `preTxnChangegroup` | 같은 `nodes` | 푸시 전 검사(보호 규칙, 서명)에 쓴다 |
| `prePushkey`, `postPushkey` | `namespace`, `key`, `old`, `new`, `repository` | bookmark 생성은 `old`가 빈 문자열, 삭제는 `new`가 빈 문자열이고 null이 아니다. 값은 노드 hex. pre가 거절하면 구버전 pushkey 명령은 `0\n`, bundle2 파트는 "prepushkey hook failed"로 응답한다. post는 성공했을 때만 불리고 반환값은 무시된다. `namespace`가 `bookmarks`가 아닌 pushkey(phases 등)도 훅은 불리므로 Yona는 `namespace`를 걸러야 한다 |
| SSH | `HgSshWireServer`에 같은 훅 다섯 종류가 있고 같은 `Wire1Commands` 경로로 부른다 | HTTP와 SSH가 같은 변환기를 쓸 수 있다 |

- 폴더 한 단계 걷기는 hg4j에 **없다**. `ManifestWalk`와 `TreeCommand`는 파일 항목 전체를 경로 오름차순으로 평평하게 돌려주므로(treemanifest 하위 폴더도 펼친다) 폴더 목록은 Yona가 경로 앞부분으로 묶는 방식이 맞다.
- 병합 커밋 공개 진입점은 요청서를 보냈고, hg4j 담당 세션은 **선생님의 진행 결정을 기다리고 있다**(구현 미착수).

## 5. 이전 시도(`next` 브랜치, P3-12)의 교훈

`next`에는 hg4j를 쓴 구현이 있다(`HgRepository`, `HgController`, `HgAuthorizationFilter`, `HgSshProtocolHandler`, `HgPushHooks`, `HgPostReceiveEventListener`, 병합 시험 등). 이 저장소(`next2`)와 구조가 달라 그대로 옮기지는 못하지만 판단의 근거로 쓴다.

- **브랜치 대응은 bookmark로 정했다.** Mercurial의 named branch는 커밋에 영구히 새겨지는 별개 개념이고 닫을 수만 있고 지울 수 없어서 Git 브랜치와 1:1이 아니다. bookmark는 움직이는 이름표라 Git 브랜치에 가장 가깝다.
- **포크 PR의 흔적은 bookmark로 남기고 병합 뒤 정리했다.**
- **병합은 임시 복제로 시작해 비용 문제가 보였고** 그래서 in-core 병합 커밋(P3-33)을 설계했다.
- **push 알림과 웹훅이 한동안 빠졌다**(2라운드까지 범위 밖). 푸시 뒤 처리의 입구가 Git에 묶여 있었기 때문이다. 이번에는 입구를 먼저 중립화한다.
- **`.git` 접미사 버그**: 프로젝트 이전과 포크의 물리 디렉터리 이동이 종류와 무관하게 `.git`을 붙였다. 이 저장소는 `RepositoryService`가 종류별로 경로를 처리하므로 같은 문제가 없는지 확인 시험을 둔다.
- **인가 필터 세 개가 비슷한 로직을 반복했다.** 이번에는 공통 부분을 먼저 뽑는다.

## 6. 공통 처리 과정과 리팩터링 설계

### 6.1 Git의 처리 과정을 단계로 보면

1. **수명**: 만들기, 지우기, 옮기기, 포크 복제, 가져오기, 종류 변경.
2. **읽기**: 기본 브랜치, 참조 이름, 폴더와 파일, 이력, 커밋, 부모, diff, blame, zip, README.
3. **서빙**: 클라이언트 프로토콜 처리(HTTP, SSH), 앞단에서 인증, 권한, 시도 제한, 감사, 지표.
4. **푸시 전 검사**: 보호 규칙, 서명 확인, 거절 기록.
5. **푸시 뒤 처리**: 새 커밋 목록 구하기, 이슈 참조 이벤트, 알림, 웹훅, 관련 PR 검사, 마지막 푸시 시각, 최근 푸시한 브랜치.
6. **협업**: 풀 리퀘스트(비교, 병합 시도, 병합, 브랜치 정리).

이 가운데 **종류마다 달라야 하는 것은 "저장소를 읽고 쓰는 일"뿐**이고, 서빙의 앞단(3), 푸시 뒤 처리의 본문(5), 협업의 화면과 상태 기록(6)은 같다. 지금은 이 둘이 JGit 타입을 사이에 두고 뒤섞여 있다. 리팩터링의 핵심은 **이 경계에 중립 계약을 세우는 것**이다.

### 6.2 중립 계약(제안)

```kotlin
/** 저장소 종류. DB 열 값은 v1.6과 같은 문자열을 유지한다("GIT", "Subversion"), Mercurial 값은 질문 2 */
enum class VcsType(val columnValue: String) { GIT("GIT"), SUBVERSION("Subversion"), MERCURIAL(/* D-86 질문 2 */) }

/** 종류가 할 수 있는 일의 선언. 화면, 컨트롤러, API가 `vcs ==` 비교 대신 이것을 묻는다 */
data class VcsCapabilities(
    val branches: Boolean, val tags: Boolean, val fork: Boolean, val pullRequests: Boolean,
    val importFromUrl: Boolean, val httpServing: Boolean, val sshServing: Boolean, val lfs: Boolean,
    val readmePosting: Boolean, val deployKeys: Boolean, val protectedRefs: Boolean, val zipDownload: Boolean
)

/** 한 종류의 저장소를 읽고 쓰는 구현. 지금의 Git*, Svn* 클래스가 이 계약을 구현하는 형태로 정리된다 */
interface VcsBackend {
    val type: VcsType
    val capabilities: VcsCapabilities
    fun lifecycle(): RepositoryLifecycle   // create, delete, move, cloneLocal, cloneRemote
    fun reader(project: Project): RepositoryReader  // entries, file, history, commit, refs, archive, blame
    fun differ(project: Project): RepositoryDiffer  // 두 리비전 사이 파일 내용 쌍
    fun merger(project: Project): RepositoryMerger? // 풀 리퀘스트용, 지원하지 않으면 null
}
```

원칙은 다음과 같다.

- **JGit 타입이 `RepositoryService`의 밖으로 새지 않게 한다.** `openRepository(project): Repository`는 없애고, 41곳이 `reader`, `differ`, `merger`를 쓰게 한다. 이미 중립인 `CommitInfo`, `BranchInfo`, `TagInfo`가 반환형의 바탕이다.
- **`vcs ==` 비교(75줄)는 `capabilities`를 묻는 것으로 바꾼다.** `@IsOnlyGitAvailable`은 `@RequiresCapability(PULL_REQUESTS)`처럼 능력을 선언하는 형태로 바꾼다(로드맵 X-7에 기록해 둔 선행 정리). **v1.6에서 Git 전용이던 기능은 Subversion에 계속 거절되어야 하므로 거절 응답은 지금과 같게 한다**(시험으로 고정).
- **모든 변경은 동작을 바꾸지 않는 리팩터링으로 먼저 한다.** Mercurial은 그 위에 더한다.

### 6.3 푸시 이벤트 중립화(핵심 공통 부분)

`GitHooks`와 `PostReceiveJobs`, `Ruleset.pushViolation`의 입구를 다음 값으로 바꾼다.

```kotlin
data class RefUpdate(val ref: String, val old: String?, val new: String?, val type: RefUpdateType) // CREATE, UPDATE, FAST_FORWARD 아님(강제), DELETE
data class PushedCommit(val id: String, val shortId: String, val message: String, val authorName: String, val authorEmail: String, val committerName: String, val committerEmail: String, val time: Date)
```

- Git 쪽은 `ReceiveCommand`를 `RefUpdate`로 옮기는 얇은 변환만 둔다.
- 푸시 뒤 본문(이슈 참조 이벤트, 알림, 웹훅, PR 갱신, 마지막 푸시 시각, 최근 푸시한 브랜치)은 `RefUpdate`와 `PushedCommit`만 받는다. 새 커밋을 구하는 일은 종류별 `reader`가 맡는다.
- **Mercurial의 푸시는 ref 갱신이 아니라 "체인지그룹 도착"이다.** 새 체인지셋 노드 범위와 이름표(bookmark) 변경이 따로 온다(`pushkey`). 변환기는 이 둘을 합쳐 `RefUpdate` 목록을 만든다: 새 머리가 생기면 CREATE나 UPDATE, bookmark의 이동과 삭제는 UPDATE와 DELETE. 이 대응이 8절 질문 1과 이어진다.

### 6.4 diff 중립화

`FileDiff`와 `V16DiffAlgorithm`은 v1.6의 diff 출력과 어긋나지 않도록 정밀하게 맞춘 코드다(A-번호가 다수). hg4j의 `DiffCommand` 출력을 따로 맞추면 두 번째 구현을 유지해야 한다. 제안은 **리비전 두 개의 같은 경로 파일 내용을 읽어 `V16DiffAlgorithm`에 넣는 방식**이다(Subversion도 이 방식으로 맞출 수 있다). 이러면 화면과 줄 단위 코멘트 위치가 종류와 무관하게 같고, 변경 파일 목록(추가, 삭제, 이름 바꿈)만 종류별로 구하면 된다. 이름 바꿈과 복사는 Mercurial이 커밋에 기록하므로(`copy` 메타데이터) 오히려 정확하게 얻을 수 있다.

### 6.5 서빙의 공통 앞단

`GitHttpController.handle`에는 기본 인증, 시도 횟수 제한, 권한 판정, 거절 응답, 지표가 종류와 무관하게 들어 있다. 이 부분을 `VcsServingGate`(가칭)로 뽑고 Git, Mercurial 컨트롤러가 같이 쓴다. SSH는 `SshVcsHandler`가 이미 이 모양이므로 Mercurial 처리기만 더한다.

### 6.6 풀 리퀘스트

공통화가 가장 어려운 곳이다. 계약은 다음 정도로 나눈다.

- `RepositoryMerger.attempt(pr)`: 충돌 여부와 가져올 커밋 목록.
- `RepositoryMerger.merge(pr, method, message)`: 병합 커밋, 스쿼시, 리베이스 중 하나로 반영하고 병합된 커밋 id를 돌려준다.
- 보내는 브랜치의 삭제와 복원, 변경 파일 목록, 이전 코멘트의 유효성 판정은 같은 계약 안에서 종류별 구현.
- **Mercurial 구현은 단계를 나눈다**(질문 3): 처음에는 "병합 커밋" 방식만 지원하고, 스쿼시와 리베이스는 hg4j의 `GraftInMemory`, `RebaseCommand` 상태를 보고 뒤 단계로 둔다.

## 7. 구현 단계 제안(시험 먼저, DB 세 종류)

| 단계 | 내용 | 동작 변화 | 시험 |
|---|---|---|---|
| 0 | hg4j 의존성(`io.github.search5.hg4j:hg4j:1.1.0`)과 JDK 확인, 실제 `hg` 7.2로 상호운용 기준선 만들기(복제, 푸시, 이름표, 서명), hg4j에 요청할 보완 목록 확정 | 없음 | 상호운용 시험 틀 |
| 1 | **공통화 리팩터링 A**: `VcsType`와 `VcsCapabilities` 도입, `vcs ==` 75줄과 `@IsOnlyGitAvailable` 37곳을 능력 질의로 교체 | 없음(거절 응답 동일) | 기존 시험 전체와 종류별 거절 시험 |
| 2 | **공통화 리팩터링 B**: `RepositoryReader`, `RepositoryLifecycle` 도입, `openRepository`가 JGit 타입을 내보내지 않게 하고 읽기 서비스 7곳을 계약 위로 옮김 | 없음 | 기존 시험 전체 |
| 3 | **공통화 리팩터링 C**: 푸시 이벤트 중립화(`RefUpdate`, `PushedCommit`), `Ruleset.pushViolation`, `GitHooks`, `PostReceiveJobs` | 없음 | 푸시 알림, 웹훅, 규칙 시험 |
| 4 | **공통화 리팩터링 D**: diff 내용 기반화, 서빙 앞단 분리, 종류 변경의 대상 선택 | 없음 | diff 시험(출력 바이트 동일), 서빙 시험 |
| 5 | Mercurial 저장소 수명과 읽기(코드 보기, 이력, 이름표, 파일, zip, README) | 새 종류 | 실제 저장소를 만들어 확인 |
| 6 | Mercurial 서빙(HTTP, SSH)과 인가, 푸시 전 검사와 푸시 뒤 처리 | 새 종류 | 실제 `hg clone`, `hg push`로 end-to-end, 알림과 웹훅 확인 |
| 7 | 포크, 가져오기, 프로젝트 생성 화면, REST, 문서, 지표, 감사 | 새 종류 | 화면 Playwright 한 번, REST |
| 8 | 풀 리퀘스트(병합 커밋), 이후 스쿼시와 리베이스 | 새 종류 | 병합 시험, 충돌 시험 |
| 9 | LFS 주소, GPG 서명, 데이터 교환 | 새 종류 | 실제 `hg`와 `git-lfs` 확인 |

1~4단계는 **새 기능 없이 구조만 바꾸므로** 기존 시험이 그대로 통과해야 한다. 이 4단계를 끝내면 Subversion과 Git이 같은 계약을 쓰게 되고, 그 자체로 코드 정리의 성과다. Mercurial 없이 멈춰도 손해가 없다.

### 7.6 시험 전략

- 종류별로 같은 시나리오를 도는 **공통 계약 시험**을 만든다(수명, 읽기, 푸시 이벤트). Git, Subversion, Mercurial이 모두 통과해야 한다.
- 서빙은 실제 `hg`(설치됨), 실제 `git`으로 end-to-end 시험한다. 선생님의 기존 방침(실제 클라이언트로 확인)과 같다.
- DB는 H2를 기본, 묶음 점검은 PostgreSQL만(기존 방침). `project.vcs`는 `String` 필드이고 JPA 기본 길이(255)라 값이 늘어도 스키마는 바뀌지 않는다.

## 8. 결정이 필요한 질문

[decisions.md](../decisions.md) D-86에 같은 내용을 권장안과 이유와 함께 올렸다.

1. **브랜치 개념을 무엇으로 대응할까.** 선택지: (A) bookmark를 "브랜치"로 보이고 named branch는 보이지 않는다. (B) named branch를 "브랜치"로 보인다. (C) 둘 다 보이고 종류를 표시한다. 권장은 (A)이다. 이유: bookmark는 Git 브랜치처럼 이동하고 지울 수 있어 보호 규칙, PR, 삭제와 복원이 그대로 맞고, `next`에서도 같은 결론이었다. 대신 `default` named branch를 쓰는 기존 Mercurial 사용자는 bookmark를 새로 써야 한다. 이 경우 기본 브랜치는 `default` 위의 bookmark(예: `main`)로 두는지도 정해야 한다.
2. **`project.vcs` 열에 넣을 값.** v1.6은 `"GIT"`, `"Subversion"`이다. 선택지: `"Mercurial"`(Subversion과 같은 표기), `"MERCURIAL"`(`next`와 같다). 권장은 `"Mercurial"`이다. 이유: v1.6이 Git 외에는 고유명사 표기를 써서 기존 데이터와 화면 문구가 어긋나지 않는다. `next`의 값(`MERCURIAL`)으로 이미 만든 데이터는 없다(이 저장소에는 Mercurial이 없었다).
3. **풀 리퀘스트 범위.** 선택지: (A) 병합 커밋부터 시작하고 스쿼시와 리베이스는 뒤로 미룬다. (B) 처음부터 세 방식 전부. (C) Mercurial은 풀 리퀘스트를 지원하지 않는다. 권장은 (A)이다. 이유: 병합 커밋은 hg4j 안에 구현이 있고 공개 진입점만 없으므로(4절), 병합 커밋 하나를 먼저 확실히 만들고 스쿼시와 리베이스(`RebaseCommand`와 `GraftInMemory`)는 같은 위에서 확장하는 편이 안전하다. (C)는 X-7의 범위에 어긋난다.
4. **hg4j 의존 방식.** 선택지: (A) Maven Central의 `1.1.0`을 쓴다. (B) 형제 디렉터리를 `includeBuild`로 붙여 개발 중인 소스를 쓴다. 권장은 (A)이다. 이유: 재현 가능한 빌드이고 CI, 다른 개발자의 환경이 단순하다. hg4j를 같이 고칠 때만 임시로 (B)를 쓴다.
5. **hg4j 보완을 먼저 할지.** 로드맵은 "hg4j를 먼저 보완한 뒤 착수"로 적혀 있다. 이번 조사로 확인한 보완 요청은 병합 커밋의 공개 진입점 하나(내부 구현은 `GraftInMemory.commit`에 있다)이다. 폴더 목록 묶기와 bookmark 변경 처리는 Yona 쪽에서 해결되므로 요청하지 않는다. 1~4단계는 hg4j와 무관하게 먼저 할 수 있으므로 **권장은 1~4단계를 지금 시작하고 hg4j 보완은 5단계 전에 마치는 것**이다.
6. **종류 변경(`changeVCS`)의 대상 선택.** v1.6은 Git과 Subversion을 번갈아 바꾼다. 종류가 셋이 되면 (A) 대상을 고르는 선택 상자를 더한다. (B) 순환(`GIT`, `Subversion`, `Mercurial`)으로 둔다. 권장은 (A)이다. 이유: 순환은 원하는 종류로 가려면 여러 번 바꾸어 저장소를 계속 지우고 만들게 된다. 또 v1.6의 두 종류 사이 동작(Git과 Subversion을 번갈아)은 그대로 유지한다.
7. **Mercurial의 GPG 서명.** 이번 범위에 넣을지. 권장은 **9단계 뒤로 미룬다**이다. 이유: 서명 방식이 Git과 달라(`.hgsigs` 파일에 서명을 커밋) 검증 흐름(푸시 전 `unverifiedCommits`)을 따로 설계해야 한다.

## 9. 위험과 주의

- **리팩터링의 폭이 크다.** 37개 파일, 41곳의 저장소 열기, 75줄의 비교를 바꾸므로 한 번에 하지 않고 1~4단계로 나눠 각 단계마다 H2 전체와 PostgreSQL 관련 시험을 돌린다. v1.6 동일성이 원칙이므로 응답 바이트와 거절 응답을 시험으로 고정한다.
- **중립 계약이 최소 공통분모로 얇아질 위험.** 능력 선언으로 종류별 차이를 드러내고, 계약에 없는 기능은 억지로 맞추지 않는다.
- **hg4j 성숙도.** 7.2만 시험되었고 일부 불안정한 시험이 있다. 상호운용 시험으로 먼저 확인하고, 문제는 hg4j에 되돌려 보낸다.
- **병합 비용.** 병합 커밋 공개 진입점이 생기기 전에는 임시 복제로 병합해야 해서 저장소가 클수록 느리다. 진입점을 먼저 더하면 이 위험은 없다.
- **이름과 해시 표기.** Mercurial의 짧은 해시는 12자, 리비전 번호는 로컬 번호라 공유되지 않는다. 화면과 API는 전체 40자 노드 해시를 기준으로 하고 링크와 자동 링크(`AutoLinkRenderer`)는 40자와 12자 두 길이를 받도록 한다.
- **데이터 위치.** `repo/hg/<소유자>/<이름>`(Subversion의 `repo/svn`과 같은 방식). 위키는 계속 Git이다.
- **프로젝트 이전과 이름 바꾸기.** 종류별 경로를 `RepositoryService`가 계속 맡도록 하고, `next`에서 나온 `.git` 접미사 문제를 확인하는 시험을 둔다.

## 10. 다음 일

1. 선생님이 이 보고서를 검토하고 D-86의 7개 질문에 답한다.
2. 답에 따라 단계와 일정을 확정하고 로드맵 X-7을 갱신한다.
3. 1단계(능력 선언)부터 시험 먼저로 시작한다.
