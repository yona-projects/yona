---
type: plan
id: P3-10
title: "Git 태그(tag) 지원"
status: done
priority: 8
depends_on: [p3-02-cli-and-rest-api]
blocks: []
source: 사용자 요청(2026-09-03) — "gh에 태그 기능이 있는걸로 아는데 yona도 구현할거거든"
created: 2026-09-03
updated: 2026-09-07
tags: [plan, p3, vcs, cli]
---

# Git 태그(tag) 지원

## 배경

사용자 요청(2026-09-03) — GitHub(`gh`)에 태그 기능이 있듯 yona에도 git 태그 지원을 넣을 계획.
아직 착수 전, TODO로만 등록한다(현재 라운드 작업 대상 아님 — [[p3-02-cli-and-rest-api]]의 `gh status`
갭 해소만 진행 중).

참고: `gh` 자체엔 독립된 `tag` 최상위 명령이 없다(태그는 `gh release`에 종속된 개념이거나 순수
`git tag`/`git push --tags`로 다룬다 — 2026-09-03 실측 확인, `gh --help`/`gh release --help`/`gh repo
--help`에 tag 서브커맨드 없음). yona가 무엇을 만들지는 착수 시점에 다시 정의해야 한다 — 최소한
아래를 확인하고 시작할 것:

- yona/legacy-yona에 태그 관련 기존 코드가 있는지(`Tag`/`TagController`/`git tag` 관련 JGit 호출)
  전수 확인 — [[p3-02-cli-and-rest-api]]의 감사표가 `gh release`를 "yona에 릴리즈/태그 배포 개념
  없음(전수 확인 — `Release`/`ReleaseController` 0건)"으로 이미 기록해뒀으므로, 태그 자체도 별도로
  다시 확인해야 한다(release와 태그는 다른 개념).
- 범위: 코드 브라우저에서 태그 목록/브라우징(브랜치 셀렉터 옆 태그 셀렉터), REST API(`/api/v1/
  projects/{owner}/{project}/tags` 등 — [[p3-02-cli-and-rest-api]]의 기존 리소스 세그먼트 패턴
  재사용), `yona-cli`의 `yona tag list/create/delete` 같은 서브커맨드.
- [[p3-02-cli-and-rest-api]]의 Fine-grained PAT 스코프 체계(`ApiTokenScopeGroup`)에 태그를 어느
  그룹(CODE?)으로 편입할지 결정 필요.

## 범위 확정 (2026-09-07 착수 시점 조사 결과)

### 사전 조사(실측)

- **yona/legacy-yona 태그 관련 기존 코드 전수 확인 — 0건.** `Tag`/`TagController`/JGit `TagCommand`
  등 grep 전수 조사 결과 태그 관련 코드가 전혀 없다. 완전 신규 기능.
- **`domain/vcs/GitRepository.kt`의 브랜치 처리 패턴이 태그에도 그대로 적용 가능함을 확인.**
  `getBranches()`/`deleteBranch()`/`createBranch()`(626~673번째 줄 근처)가 JGit `Git.branchList()`/
  `branchDelete()`/`branchCreate()`를 쓰는 것과 정확히 같은 방식으로 `Git.tagList()`/`tagDelete()`/
  `tag()`를 쓰면 된다.
- **git smart HTTP/SSH 프로토콜은 이미 태그 push/fetch를 지원함을 재확인.** JGit `GitServlet`/
  `ReceivePack`/`UploadPack`이 `refs/tags/*`를 브랜치와 동일하게 다뤄, 이번 작업은 프로토콜이 아니라
  "태그를 보고 관리하는" API/UI/CLI 계층만 추가하면 된다.
- **코드 브라우저의 ref 해석(`Repository.resolve()`)이 태그 이름을 브랜치와 동일하게 이미 인식함을
  실측 확인.** `GitRepository`의 대부분 메서드(`getMetaDataFromPath`, `getRawFile`, `getCommit`,
  `getArchive` 등)는 `repo.resolve(revision)` → `revWalk.parseCommit(objectId)` 패턴을 쓰는데,
  (1) `Repository.resolve()`는 단순 이름을 `refs/heads/`, `refs/tags/` 등 여러 네임스페이스에서
  순차 탐색하고, (2) `RevWalk.parseCommit()`은 annotated 태그 오브젝트를 만나면 자동으로 peel해
  최종 커밋까지 해석한다(JGit 소스 `RevWalk.java:1084-1091`, `peel(parseAny(id))`) — 즉
  `/{owner}/{project}/code/{tagName}` 같은 URL이 **코드 한 줄도 안 고치고 이미 동작**한다. 이번
  작업은 "태그를 볼 수 있게" 만드는 데 집중하면 된다(브라우징 자체는 무료로 따라옴).
- **`config/TemplateHelper.kt`에 브랜치/태그 통합 표시를 위한 헬퍼가 이미 존재하지만 미사용이었음을
  발견.** `branchItemType()`(471~487번째 줄)이 `refs/heads/*` → "branch", `refs/tags/*` → "tag"를
  이미 구분하고, `branchInHtml()`이 타입 라벨을 붙여 렌더링하는 코드까지 있었다 — legacy 이식 당시
  준비만 해두고 `getRefNames()`가 브랜치만 반환해 실제로는 한 번도 태그 경로를 타지 않던 죽은 코드였다.
  이번 작업은 이 기존 인프라를 실제로 배선하는 셈이다.

### 확정된 범위

1. **도메인 계층** — `PlayRepository`/`GitRepository`/`SvnRepository`에 브랜치와 대칭되는 메서드
   추가: `getTagNames()`(`refs/tags/*` 전체 ref 이름, 코드 브라우저 셀렉터용), `getTags()`(상세
   뷰모델 `GitTag` 목록, 태그 목록 화면용), `deleteTag()`, `createTag(name, startPoint, message,
   taggerName, taggerEmail)`. SVN은 브랜치와 동일하게 no-op/빈 목록.
2. **코드 브라우저 UI** — GitHub 방식(모호하면 GitHub 방식 기본값 원칙)의 브랜치/태그 통합 ref
   셀렉터: `code/view.html`(파일 브라우저)과 `code/history.html`(커밋 히스토리)의 기존
   `<select id="branches">`에 `<optgroup label="브랜치">`/`<optgroup label="태그">` 그룹 헤더를
   추가(GitHub의 "Branches"/"Tags" 그룹 드롭다운과 동일 패턴). 기존 `branches` 모델 속성은 그대로
   두고 `tags` 속성을 추가하는 방식이라 기존 동작(PR 브랜치 선택기 등, `getRefNames()`를 쓰는 다른
   화면)에는 영향이 없다 — PR 생성/브랜치 관리 화면은 태그를 보여줄 이유가 없어 의도적으로
   제외했다(과잉 노출 방지).
   추가로 별도 **태그 목록 화면**(`/{owner}/{project}/tags`, `code/tags.html`)을 브랜치 목록
   화면(`code/branches.html`)과 대칭으로 신설하고, 코드 브라우저/브랜치/태그 탭 4개(파일-커밋-
   브랜치-태그)를 서로 연결했다.
3. **REST API** — `GET/POST/DELETE /api/v1/projects/{owner}/{project}/tags`(+ `DELETE .../tags/{tag}`).
   P3-02가 만든 리소스 세그먼트 패턴을 그대로 따름. `ApiTokenAuthenticationFilter.
   resourceSegmentToResourceType`에 `"tags" -> ResourceType.CODE`(CODE 그룹) 매핑 추가 — "code"
   세그먼트와 동일 그룹으로, 저장소 콘텐츠를 다루는 스코프라는 점에서 자연스럽다.
4. **`yona-cli`** — `yona tag list/create/delete` 서브커맨드(`cmd/tag.go` + `internal/api/tag.go`),
   `cmd/label.go`의 구조/컨벤션(같은 `internal/api/` 클라이언트 패턴, `--json`/`-R` 자동감지) 그대로
   재사용.

### annotated 태그 지원 여부 — annotated까지 지원하기로 결정(근거)

git 태그는 lightweight(단순 ref)와 annotated(메시지·태거·타임스탬프를 담은 태그 오브젝트) 두 종류가
있다. yona에는 릴리즈 개념이 없으므로(P3-02 감사에서 이미 확인) GitHub처럼 "릴리즈와 연동된 태그"를
전제할 필요는 없지만, 다음 이유로 **lightweight만으로는 부족하다고 판단해 annotated도 지원**했다:

- "태그를 만들 때 메모를 남기고 싶다"는 요구는 릴리즈 노트가 아니라 순수 git 사용 패턴에서도 흔하다
  (`git tag -a v1.0 -m "..."`가 `git tag v1.0`보다 오히려 더 널리 권장되는 관례 — 커밋 서명/태거
  검증이 필요한 배포 태그 등).
- 구현 비용이 낮다 — `GitRepository.createTag()`는 JGit `TagCommand.setAnnotated(true/false)` 한
  분기로 나뉘고(`message`가 있으면 annotated), `getTags()`도 `RevWalk.parseAny()`로 반환된
  오브젝트가 `RevTag`인지(annotated) `RevCommit`인지(lightweight)만 분기하면 된다 — 별도 엔티티나
  DB 스키마가 필요 없다(모두 git 오브젝트 자체에 저장됨, GPG 커밋 서명 검증과 마찬가지로 "저장소
  원본에서 조회 시점에 즉시 계산" 패턴).
- 과잉 설계 방지 원칙과 상충하지 않는다 — 릴리즈 노트/자산(assets)/발행(publish) 같은 릴리즈
  개념은 전혀 만들지 않았고(범위 밖 유지), 순수하게 "태그 오브젝트에 메시지가 있는지 없는지"만
  다룬다.

annotated 태그 생성 시 태거(tagger) identity는 API 호출자(로그인 사용자/토큰 소유자)의
`User.name`/`User.email`을 사용하고, 지정하지 않으면 `"yona"`/`"yona@yona.io"` 기본값으로
대체한다(레포지토리 병합 커밋의 `PersonIdent(siteName, "yona@yona.io")` 폴백과 동일한 기존 관례).

### 권한 — 브랜치 삭제와 동일한 체계

`BranchApiController.deleteBranch()`가 요구하는 권한을 그대로 재사용한다:
`AccessControl.isAllowed(user, project, Operation.DELETE)` — PROJECT 리소스의 DELETE는
`AccessControl.kt:335-337`(UPDATE/DELETE 등 나머지 연산은 "매니저 또는 조직관리자 전용") 규칙에
따라 매니저/조직관리자 전용이다. 태그 생성은 브랜치의 "기본 브랜치로 설정"(`setAsDefault`, UPDATE
권한)과 대칭으로 `Operation.UPDATE`를 쓰지만, PROJECT 리소스에서는 UPDATE와 DELETE가 같은 문턱이라
실질적으로 생성/삭제 모두 매니저 전용으로 동일하다. 목록 조회는 `Operation.READ`(코드 브라우저/
브랜치 목록과 동일).

### 보안 리뷰(자체 수행)

- **경로 탈출/인젝션**: 태그 이름 검증에 새 정규식을 직접 만들지 않고 JGit
  `Repository.isValidRefName("refs/tags/" + name)`(공개 정적 메서드)를 그대로 재사용 —
  `..`/선행·후행 슬래시/제어문자/공백 등을 이미 전부 걸러낸다. `TagRestApiControllerIntegrationSpec`
  에 `../../../etc/evil` 같은 경로 탈출 이름으로 생성 요청 시 400을 반환하고 실제로 태그가
  만들어지지 않는지 실제 bare 저장소로 end-to-end 검증했다.
- **PAT 스코프 검증**: `ApiTokenScopedTagAuthorizationIntegrationSpec`(실제 Spring 컨텍스트 + DB)로
  CODE 그룹 스코프가 없는/READ만 있는 토큰은 각각 목록·생성 요청에서 403이 되는지, WRITE 스코프
  토큰은 필터를 통과하는지 실측 검증했다.
- **프로젝트 스코프 밖 호출 방지**: `TagRestApiController`가 `findByOwnerAndNameOrPreviousPlace(owner,
  project)`로 항상 URL의 owner/project로 프로젝트를 다시 조회하고, 그 프로젝트 객체 기준으로만
  `AccessControl`/`repositoryService.getRepository()`를 호출한다 — 요청 바디에 다른 프로젝트를
  가리키는 필드가 없어 "A 프로젝트 권한으로 B 프로젝트 태그를 조작" 같은 경로가 애초에 존재하지
  않는다.

## 구현 내역

- `domain/vcs/GitTag.kt`(신규): `GitBranch`와 대칭인 뷰모델(`name`, `targetCommit`, `tagger`,
  `message`, `annotated`, `shortName`).
- `domain/vcs/PlayRepository.kt`/`GitRepository.kt`/`SvnRepository.kt`: `getTagNames()`/`getTags()`/
  `deleteTag()`/`createTag()` 추가.
- `web/TagViewController.kt`(신규): `GET /{owner}/{project}/tags` — `BranchViewController`와 동일한
  접근 제어 구조.
- `web/TagApiController.kt`(신규): `DELETE /{owner}/{project}/tags/{tag}` — 웹 UI "삭제" 버튼
  액션(`BranchApiController.deleteBranch()`와 동일 패턴). 브랜치와 마찬가지로 웹 UI에 "새 태그"
  생성 폼은 두지 않았다(GitHub도 릴리즈 없이 독립된 "새 태그" 버튼을 web UI에 두지 않고, 브랜치도
  이 저장소에 생성 폼이 없다 — 생성은 REST API/CLI 전용).
- `web/TagRestApiController.kt`(신규): `GET/POST /api/v1/projects/{owner}/{project}/tags`,
  `DELETE .../tags/{tag}` — Issue/PR REST API와 달리 위임할 기존 세션 컨트롤러가 없어 AccessControl
  판정을 직접 수행(`ProjectRestApiController.create()`/`fork()`와 동일 패턴).
- `config/ApiTokenAuthenticationFilter.kt`: `resourceSegmentToResourceType`에 `"tags" ->
  ResourceType.CODE` 추가.
- `templates/code/tags.html`(신규), `templates/code/branches.html`/`code/view.html`/
  `code/history.html`: 태그 탭/통합 ref 셀렉터 배선.
- `messages/messages.properties`, `messages_ko.properties`, `messages_ko_KR.properties`:
  `title.tags`, `code.tags.targetCommit`, `code.tags.type.annotated`, `code.tags.type.lightweight`
  키 추가(브랜치 협업 관례에 따라 이 세 파일만 갱신 — ja_JP/ru_RU/uz_UZ는 최근 다른 신규 기능
  추가 때도 갱신되지 않은 관례를 그대로 따름).
- `yona-cli/internal/api/tag.go`, `yona-cli/cmd/tag.go`(신규), `cmd/root.go`에 `newTagCmd` 등록,
  `README.md`에 `yona tag` 섹션 추가.

## 테스트

서버(TDD, 전부 GREEN):
- `GitRepositorySpec`(도메인 로직 — lightweight/annotated 생성, 목록, 삭제, 접두사 처리, 잘못된
  시작점 예외, 브랜치/태그 네임스페이스 분리).
- `TagViewControllerSpec`/`TagApiControllerSpec`/`TagRestApiControllerSpec`(mockk 기반 단위,
  `BranchViewControllerSpec`/`BranchApiControllerSpec`/`ProjectRestApiControllerSpec`과 동일 패턴).
- `TagRestApiControllerIntegrationSpec`(실제 bare 저장소 + 실제 DB end-to-end — 생성/목록/삭제,
  annotated 태그 tagger/message, 잘못된 target, 경로 탈출 이름, 코드 브라우저 페이지에 태그
  optgroup이 실제로 렌더링되는지까지 검증).
- `ApiTokenScopedTagAuthorizationIntegrationSpec`(PAT 스코프 필터 통합 검증).
- 기존 `CodeViewControllerSpec`/`BranchViewControllerSpec`/`BranchApiControllerSpec` 등 회귀 없음
  재확인(코드 브라우저 컨트롤러가 `getTagNames()`를 추가로 호출하도록 바뀌어 관련 mock 스텁 추가).

`yona-cli`: `cmd/tag_test.go`(목록/생성 — lightweight·annotated·target 지정/삭제/필수 인자 검증),
`go build ./...`/`go test ./...`/`gofmt -l .`/`go vet ./...` 전부 클린.

`./gradlew test` 전체 스위트 실행 시 이 계획과 무관한 135건 실패는 전부 "Table 'yona.xxx' doesn't
exist"류 에러로, 여러 통합 스펙이 같은 공유 MariaDB에 대해 병렬로 스키마를 create-drop하며 경합하는
기존에 알려진 패턴(호출자 지시사항에 명시)이었다 — 실패했던 스펙들(`TagRestApiControllerIntegrationSpec`
포함)을 개별/소규모로 재실행해 전부 GREEN임을 교차 검증했다.

## 하지 않은 것(의도적 범위 제외)

- 릴리즈(release) 개념 신설 — 순수 git 태그만 다룬다.
- 태그별 자동화 트리거/배포 파이프라인(P3-05 영역) — 만들지 않음.
- 웹 UI의 "새 태그 만들기" 폼 — 브랜치와 동일하게 생성은 REST API/CLI 전용으로 유지(위 "구현 내역"
  근거 참고).
- SVN 프로젝트의 태그 지원 — SVN에는 git 태그 개념이 대응되지 않아 브랜치와 동일하게 no-op.
