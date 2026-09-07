---
type: plan
id: P3-12
title: "Mercurial(hg4j) 저장소 지원 추가"
status: planned
priority: 9
depends_on: []
blocks: []
source: docs/parity/tickets/p3-12.md
created: 2026-08-31
updated: 2026-08-31
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

## 관련

- 백로그 원본: [`docs/parity/index.md`](../../parity/tickets/p3-12.md)
- 관련 계획: 없음
- 관련 소스: `domain/vcs/{PlayRepository,GitRepository,SvnRepository,RepositoryService}.kt`,
  `config/{GitServletConfig,git/GitAuthorizationFilter,svn/SvnAuthorizationFilter}.kt`,
  `web/SvnController.kt`, `templates/project/create.html`
- 외부 저장소: [github.com/search5/hg4j](https://github.com/search5/hg4j)
