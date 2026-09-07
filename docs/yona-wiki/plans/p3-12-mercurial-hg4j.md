---
type: plan
id: P3-12
title: "Mercurial(hg4j) 저장소 지원 추가"
status: in-progress
priority: 9
depends_on: []
blocks: []
source: docs/parity/tickets/p3-12.md
created: 2026-08-31
updated: 2026-09-07
tags: [plan, p3, vcs, mercurial]
---

# Mercurial(hg4j) 저장소 지원 추가

## 배경

legacy yona와 yona 둘 다 Git/Subversion 두 VCS만 지원하고(`Project.vcs`가 `"GIT"`/`"SUBVERSION"` 두 값만
씀, 전수 확인) Mercurial 관련 코드는 0건 — 이식 대상이 아니라 **완전 신규 기능**이다. 세 번째 VCS 백엔드를
추가하는 작업이라, 이미 존재하는 두 번째 백엔드(SVN)가 Git 전용으로 설계됐던 구조 위에 어떻게 얹혔는지가
가장 가까운 선례다.

사용자가 지정한 라이브러리는 Maven Central에 공개돼 있던 구버전 TMate `org.tmatesoft.hg4j`가 아니라
**자체 개발 중인 [`search5/hg4j`](https://github.com/search5/hg4j)**다. 인터페이스를 **JGit과 동일하게
맞출 예정**이라고 확인됨 — 즉 `Repository`/`Git`/`RevCommit`류의 JGit API 형태를 그대로 따라가는 설계를
지향한다는 뜻이므로, yona 쪽 `HgRepository`는 `GitRepository.kt`(`domain/vcs/GitRepository.kt`)의 구현
패턴을 hg4j API로 그대로 옮겨 적는 방식으로 상당 부분 재사용 가능할 것으로 기대된다(실제 API 확정 전까지는
가정).

## 범위

### 포함
- `PlayRepository` 인터페이스(`domain/vcs/PlayRepository.kt`, 25개 메서드)를 구현하는 `HgRepository` 신설
- `RepositoryService.getRepository()`의 vcs 분기에 Mercurial 케이스 추가(현재 GIT/SVN 이분기 → 삼분기)
- 프로젝트 생성 폼(`project/create.html`)에 VCS 선택지 `"MERCURIAL"`(가칭) 추가
- Mercurial 프로토콜로 실제 clone/push가 되는 HTTP 서빙 경로(hg4j가 제공하는 서버 컴포넌트 유무에 따라 설계 갈림 — 아래 리스크 참고)
- push 시 알림/웹훅/브랜치 추적 이벤트 발행(`GitPushHooks.kt`/`GitServletConfig.kt`의 `YonaPostReceiveHook`
  대응물)
- 코드브라우저(`CodeViewController`/`code/view.html` 등)에서 Mercurial 저장소 파일 목록/커밋 이력/diff 조회

### 제외 (비범위)
- `hg4j` 라이브러리 자체의 구현(별도 저장소 `search5/hg4j`에서 진행 — 이 계획은 **yona 통합** 범위만 다룸)
- SSH를 통한 Mercurial 접근([[p3-03-ssh-gpg]]에서 SSH 인프라가 먼저 갖춰진 뒤 재검토)
- Mercurial의 named branch/bookmark/phase 등 Git에 없는 개념의 UI 노출(1차는 Git의 "브랜치" 개념에 최대한
  근사하게 매핑, 세부 지원은 후속 라운드)

## 의존성

- **선행 조건**: 없음(SVN 백엔드가 이미 있어 "세 번째 VCS 추가" 구조 자체는 새롭지 않음). 단, **`search5/hg4j`
  라이브러리가 실제 사용 가능한 API 표면을 갖추기 전까지는 착수 불가** — 이 저장소 진행 상황이 사실상의
  블로커다(별도 트래킹 필요, 이 문서의 의존성 그래프에는 안 잡히는 외부 저장소 의존성).
- **후속 파급**: 없음(다른 P3 항목이 이 항목을 전제로 하지 않음)

## 설계 개요

### 저장소 추상화 계층 — `HgRepository : PlayRepository`

`RepositoryService.getRepository(project)`(`domain/vcs/RepositoryService.kt:23-45`)는 현재 `project.vcs`를
대문자로 정규화해 `"SUBVERSION"/"SVN"` → `SvnRepository`, 그 외 전부 → `GitRepository`로 분기한다. 여기에
`"MERCURIAL"/"HG"` → `HgRepository` 세 번째 분기를 추가하는 구조는 기존 두 백엔드와 대칭적이라 리스크가 낮다.

`GitRepository`(`domain/vcs/GitRepository.kt`)는 생성자로 `ownerName`/`projectName`/`baseDir`/
`userResolver`/`defaultBranch`(P3-11에서 추가)를 받는 순수 POJO 스타일 클래스이고, `PlayRepository`의 25개
메서드(`create`/`getMetaDataFromPath`/`getRawFile`/`getHistory`/`getCommit`/`getBranches`/`getDiff`/
`getArchive` 등)를 JGit 저수준 API(`Repository`/`RevWalk`/`TreeWalk`/`DiffFormatter`)로 구현한다.
`search5/hg4j`가 JGit과 동일한 인터페이스를 지향한다면, `HgRepository`는 이 파일의 각 메서드를 1:1로 대응되는
hg4j 호출로 치환하는 방식으로 작성 가능할 것 — 단 이건 hg4j의 실제 API가 확정된 뒤에만 검증 가능한 가정이다.

### 프로토콜 서빙 — 미확정, hg4j 범위에 따라 설계가 갈림

Git은 `GitServletConfig.kt`가 JGit이 제공하는 `org.eclipse.jgit.http.server.GitServlet`을 `/git/{owner}/{project}`
경로에 등록해 smart HTTP 프로토콜(clone/fetch/push)을 그대로 처리하고, push 시 `ReceivePackFactory`에
`RejectPushToReservedRefsPreReceiveHook`(보호된 ref 차단)과 `YonaPostReceiveHook`(알림/웹훅/`PushedBranch`
추적 이벤트 발행)을 건다. SVN은 Apache Jackrabbit `DAVServlet` 기반의 WebDAV로 이 역할을 대신한다
(`SvnServletRequestWrapper.kt`/`SvnController.kt`).

Mercurial의 wire protocol(HTTP 기반 `hg serve` 프로토콜)을 `search5/hg4j`가 서버 컴포넌트(JGit의
`GitServlet`에 해당하는 것)까지 제공하는지, 아니면 저수준 리포지토리 읽기/쓰기 API만 제공하고 프로토콜
서빙은 yona 쪽에서 직접 구현해야 하는지가 **이 계획의 가장 큰 미확정 사항**이다. hg4j 저장소의 로드맵을
먼저 확인해야 Step 3(아래) 착수 여부를 판단할 수 있다.

### 인가 필터

`GitAuthorizationFilter`(`config/git/GitAuthorizationFilter.kt`)/`SvnAuthorizationFilter`
(`config/svn/SvnAuthorizationFilter.kt`)와 동일한 패턴(Basic 인증 후 프로젝트 단위 read/write 권한 체크)으로
`HgAuthorizationFilter`를 추가한다 — 두 기존 필터가 이미 거의 동일한 로직을 반복하고 있어(`isMember`/
`isAllowedIfGroupMember` 체크) 셋을 공통 추상화할지도 이 단계에서 검토할 만하다.

## 단계별 작업 계획 (TDD)

1. **Step 0 — 선행 확인**: `search5/hg4j`의 현재 API 표면(특히 bare 저장소 생성/읽기 지원 여부, 서버
   컴포넌트 유무)을 확인하고 이 문서의 "설계 개요"를 그 결과로 갱신. 이 Step 전까지는 이후 Step의 구체적
   구현 방법이 전부 가정이다.
2. **Step 1 — `HgRepository` 뼈대 + `create()`/`isEmpty()`/`getDirectory()`**
   - 실패 테스트: hg4j로 bare 저장소를 만들고 빈 상태를 확인 → RED → 구현 → GREEN
   - `GitRepositorySpec.kt`의 `create()/delete()/isEmpty()/getDirectory()` describe 블록을 참고 패턴으로 재사용
3. **Step 2 — 읽기 경로**: `getMetaDataFromPath`/`getRawFile`/`getHistory`/`getCommit`/`getDiff`/
   `getBranches`/`getHeadBranch`
   - 코드브라우저가 실제로 Mercurial 저장소의 파일 목록/커밋 이력/diff를 보여주는지 통합테스트로 검증
4. **Step 3 — 프로토콜 서빙**(Step 0 결과에 따라 범위 확정)
   - 실패 테스트: 실제 `hg clone`/`hg push` 클라이언트로 yona 서버에 접근 → RED → 구현 → GREEN(수동 검증 병행)
5. **Step 4 — `HgAuthorizationFilter`**
   - 실패 테스트: 비멤버가 비공개 Mercurial 저장소에 접근 시 거부 → RED → 구현 → GREEN
6. **Step 5 — push 이벤트 배선**: 알림/웹훅/`PushedBranch` 추적이 Git과 동일하게 동작
7. **Step 6 — `RepositoryService`/`project/create.html`/`ProjectServiceImpl`에 세 번째 VCS 옵션 노출**

## 완료 기준 (Definition of Done)

- [ ] `search5/hg4j` API 확정 사항이 이 문서의 설계 개요에 반영됨(Step 0)
- [ ] 실제 `hg clone`/`hg push` 클라이언트로 yona에 저장소를 만들고 커밋을 올릴 수 있음(수동 검증)
- [ ] 코드브라우저에서 Mercurial 저장소의 파일 목록/커밋 이력/diff가 Git 저장소와 동일한 화면으로 조회됨
- [ ] 비공개 Mercurial 저장소에 비멤버가 접근하면 거부됨
- [ ] push 시 알림/웹훅이 Git과 동일하게 발행됨
- [ ] `./gradlew test` 전체 GREEN

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| hg4j 성숙도 | `search5/hg4j`가 아직 개발 중 — API 안정성/완성도 미확정, bare 저장소·프로토콜 서빙 지원 범위 불명 | Step 0에서 그 저장소 상태를 먼저 확인, 이 계획을 그 결과로 갱신 |
| 프로토콜 서빙 방식 | hg4j가 JGit의 `GitServlet` 같은 서버 컴포넌트를 제공하는지 불명 — 안 준다면 Mercurial wire protocol을 yona가 직접 구현해야 해 범위가 크게 늘어남 | Step 0에서 확인, 필요시 이 항목만 별도 하위 계획으로 분리 |
| Git과 다른 브랜치 모델 | Mercurial의 named branch/bookmark/phase가 Git 브랜치와 1:1 대응하지 않음 — 코드브라우저 브랜치 셀렉터 UX 설계 필요 | 1차는 named branch만 Git 브랜치처럼 노출, bookmark/phase는 후속 범위 |
| 인증 필터 3종 중복 | Git/SVN/Hg 세 인가 필터가 거의 동일한 로직을 반복하게 됨 | Step 4에서 공통 추상화 여부 검토(선택적) |

## 완료 로그

- **2026-09-07 1라운드(Step 0/1/2 일부/6, TDD로 완료)**: 사용자가 "gh:search5/hg4j에서 가져와서
  등록"을 명시적으로 지시해 착수.
  - **Step 0 해소**: `search5/hg4j`가 이미 형제 디렉터리(`~/yona-convert/hg4j`, origin=`search5/hg4j`,
    클린 상태)로 로컬에 존재함을 확인. 저장소를 직접 읽어 이 계획의 최대 미확정 사항("프로토콜 서빙
    컴포넌트 유무")을 해소 — `transport/HgHttpWireServer.java`(jakarta `HttpServlet`, JGit의
    `GitServlet`에 정확히 대응)와 `transport/HgSshWireServer.java`(순수 프로토콜, SSH 채널
    비종속)가 이미 존재한다. Maven Central/JitPack에는 미발행(태그/릴리즈 0개)이라 소스 참조가
    유일한 실질 경로.
  - **의존성 연결**: `settings.gradle.kts`에 `includeBuild("../hg4j")`, `build.gradle.kts`에
    `implementation("io.github.search5.hg4j:hg4j")` 추가(hg4j의 `group`/`rootProject.name`이
    이 좌표와 일치해 자동 소스 빌드 치환, `./gradlew dependencies`로 확인).
  - **Step 1 + Step 2 일부**: `domain/vcs/HgRepository.kt`/`HgCommit.kt` 신설. `create`/`isEmpty`/
    `getDefaultBranch`/`getMetaDataFromPath`(파일+폴더 JSON 트리, hg4j `tree()`가 평평한 매니페스트만
    줘서 경로 접두어로 클라이언트 쪽 그룹핑)/`getRawFile`/`getHistory`/`getCommit`/
    `getParentCommitOf`/`move`/`renameTo` 구현. `HgRepositorySpec.kt`(신규, `GitRepositorySpec`/
    `SvnRepositorySpec`와 동일한 스타일 — 실제 로컬 hg 저장소로 end-to-end 검증) 14 tests GREEN.
    **범위 밖으로 명시적으로 미룸(2라운드)**: 브랜치/태그 CRUD(`getBranches`/`getTags`/
    `deleteBranch`/`createBranch`/`deleteTag`/`createTag` — 전부 `SvnRepository`의 선례를 따라 빈
    값/no-op), `getDiff`/`getPatch`(`UnsupportedOperationException`), `getArchive`(no-op) — hg4j가
    `BranchesCommand`/`TagsCommand`로 실제 branch/tag를 지원하는 것은 확인했으나, Mercurial의
    "branch"는 git과 달리 커밋에 영구히 새겨지는 개념(삭제가 아니라 "close"만 가능)이라 잘못된
    매핑을 이번 라운드에서 서둘러 확정하지 않기로 함.
  - **Step 6 일부**: `RepositoryService.getRepository()`에 `MERCURIAL`/`HG` 3번째 분기 +
    `yona.hg.base-dir` 설정값 추가. `project/create.html` VCS 선택지에 "Mercurial" 옵션 추가(6개
    로케일 `messages*.properties`에 `project.new.vcsType.mercurial` 키 추가 — "Subversion"과
    동일하게 번역 없이 고유명사 그대로). GIT↔SUBVERSION 2지선다였던 "VCS 전환" 토글
    (`ProjectViewController`/`ProjectServiceImpl`)을 `domain/vcs/VcsType.kt`의
    `nextVcsInCycle()`(GIT→SUBVERSION→MERCURIAL→GIT 순환) 공유 로직으로 3종 순환 확장.
  - **부수적으로 발견한 기존 결함(범위 밖, 별도 확인 필요)**: `ProjectServiceImpl`의 프로젝트
    이전(`acceptTransfer`)/포크(`forkProject`) 물리 디렉터리 이동 코드가 vcs 종류와 무관하게 항상
    `.git` 접미사를 붙이는데, `SvnRepository`/`HgRepository`의 `getDirectory()`는 접미사 없는 경로를
    쓴다 — SVN 프로젝트 이전/포크 시 물리 저장소가 조용히 이동되지 않는 기존 버그로 보임(Git만
    실제로 동작). 이번 라운드에서 고치지 않고 코드에 주석으로만 남김 — 별도 티켓 등록 필요.
  - **남은 것(2라운드 이후)**: `web/HgController.kt`(SVN처럼 프로젝트별 `HgHttpWireServer` 캐싱),
    SSH(`YonaSshHgCommand`+`HgSshWireServer`), `config/hg/HgAuthorizationFilter.kt`, 브랜치/태그
    CRUD 실제 매핑, diff/patch/archive, `ProjectRestApiController`/Import 화면 vcs 검증. 이 항목들
    전까지는 실제 `hg clone`/`hg push`로 저장소를 만들 수 없다(도메인 계층 골격만 완성).
  - 검증: `./gradlew test --tests "HgRepositorySpec" --tests "VcsTypeSpec" --tests
    "ProjectServiceImplSpec" --tests "RepositoryServiceSpec" --tests "ProjectViewControllerSpec"
    --tests "ProjectRestApiControllerSpec" --tests "YonaApplicationTests" -Dyona.it.db=h2` 전부
    GREEN(이 세션 샌드박스는 Docker 미접근이라 Testcontainers 대신 h2 프로파일 사용).

- **2라운드 착수 전 사용자 결정사항(2026-09-07 확정, 아직 미착수)**:
  1. **범위**: HTTP 프로토콜 서빙(`HgController`) + SSH(`YonaSshHgCommand`) + 브랜치/태그 CRUD
     실제 매핑까지 전부 이번 2라운드에 포함(가장 넓은 범위로 확정 — "우선 HTTP만" 등으로 쪼개지
     않음).
  2. **기존 버그 동시 수정**: 1라운드에서 발견한 `ProjectServiceImpl`의 `acceptTransfer`/
     `forkProject` 물리 디렉터리 이동 코드가 vcs 종류 무관하게 항상 `.git` 접미사를 붙이는
     버그(SVN 프로젝트도 이전/포크 시 물리 저장소가 조용히 안 옮겨짐)를 P3-12 2라운드 작업에
     함께 포함해 고친다(별도 티켓으로 분리하지 않음).
  - Mercurial의 named branch/bookmark/phase를 코드브라우저 UI에 정확히 어떻게 매핑할지(1차는
    named branch만 Git 브랜치처럼 노출하는 안이 1라운드 계획에 있었음)는 범위가 "브랜치/태그
    모델까지 전부"로 커진 만큼 2라운드 착수 시 실제 hg4j `BranchesCommand`/`TagsCommand` API를
    다시 확인하며 확정한다.

## 관련

- 백로그 원본: [`docs/parity/index.md`](../../parity/tickets/p3-12.md)
- 관련 계획: 없음
- 관련 소스: `domain/vcs/{PlayRepository,GitRepository,SvnRepository,RepositoryService}.kt`,
  `config/{GitServletConfig,git/GitAuthorizationFilter,svn/SvnAuthorizationFilter}.kt`,
  `web/SvnController.kt`, `templates/project/create.html`
- 외부 저장소: [github.com/search5/hg4j](https://github.com/search5/hg4j)
