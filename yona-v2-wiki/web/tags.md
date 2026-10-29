---
title: Git 태그 화면
type: web
sources: [app/controllers/BranchApp.java, app/playRepository/GitRepository.java:839-880, app/views/code/branches.scala.html, next:src/main/kotlin/com/github/yonaprojects/yona/web/TagViewController.kt, next:src/main/kotlin/com/github/yonaprojects/yona/web/TagApiController.kt]
updated: 2026-10-29
---

# Git 태그 화면

v1.6에 없는 추가 화면이다(로드맵 X-18). 결정과 근거는 [decisions.md](../decisions.md) D-30을 본다. 구현: `TagController`(웹), `TagService`(저장소 읽기와 삭제, JGit), 템플릿 `code/tags.html`. Git 프로젝트에서만 동작하고 Subversion은 400이다.

## v1.6의 태그 다루기와 달라진 점

v1.6의 `GitRepository.getBranches()`는 `refs/heads`, `refs/tags`, `refs/remotes`를 모두 모아 태그도 "브랜치"로 보여 주었다. D-30으로 다음을 바꿨다.

| 항목 | v1.6 | v2 |
|---|---|---|
| 브랜치 목록(`BranchService.branches`) | 태그와 원격 참조 포함 | `refs/heads`만. 브랜치 화면, 풀 리퀘스트 생성과 수정 화면의 출발, 도착 목록이 모두 이를 따른다. 목록을 만들 때 커밋을 읽기 전에 거르므로 커밋이 아닌 대상을 가리키는 태그가 있어도 500이 아니다 |
| 코드 화면과 커밋 이력의 선택 목록 | 참조 전체를 평평하게 나열 | "브랜치", "태그" 구역(`optgroup`)으로 나눈다. 원격 참조는 뺀다. 항목 앞 `branch`, `tag` 배지는 그대로이다 |
| 코드, 커밋, 브랜치 화면의 탭 | 파일, 커밋, 브랜치 | 파일, 커밋, 브랜치, **태그** |
| 태그 삭제 | 브랜치 삭제 경로(`DELETE /:user/:project/code/:branch/`)로 지움 | 그 경로는 그대로 두고, 전용 경로 `DELETE /:user/:project/tags/:tag`를 더했다 |
| 브랜치 삭제 단추 | 확인 없이 바로 삭제 | 같은 방식의 확인창을 둔다(`code.branches.deleteConfirm`, D-31). 요청 경로와 응답은 그대로 |

## 태그 목록 (`GET /:user/:project/tags`)

권한은 코드 보기와 같다(`@CodeAccessCheck`). 열의 구성은 태그, 커밋, (삭제 권한이 있으면) 동작이다.

- **정렬**: 태그를 단 시각이 최근인 순서이다. 주석 태그는 태거 시각, 경량 태그는 가리키는 커밋의 커밋 시각이다. 같으면 이름순이고, 시각이 없는 태그(커밋이 아닌 대상을 가리키는 경량 태그)는 맨 뒤이다.
- **종류**: `Annotated`(주석), `Lightweight`(경량) 배지. 주석 태그는 메시지의 첫 줄(전체는 툴팁)과 `태거, 시각`을 보인다.
- **커밋 열**: 7자리 커밋 번호(커밋 화면 링크)와 커밋 시각. 주석 태그의 태그(태그의 태그)는 끝까지 벗겨 최종 대상을 본다. 커밋이 아니면(트리, 블롭) "커밋이 아님"을 보인다.
- **이름**: 짧은 이름(`v1.0`, `release/1.0`)을 보이고, 링크는 브랜치 화면과 같이 `refs%252Ftags%252F…`로 슬래시를 두 번 인코딩한다.
- **태그가 없으면** 없다는 안내와 `git push --tags`를 알려 준다. 태그를 만드는 폼은 없다(GitHub의 태그 목록 화면에도 없다).

## 삭제 (`DELETE /:user/:project/tags/:tag`)

- 권한은 브랜치 삭제와 같은 `Operation.DELETE`(프로젝트 매니저, 사이트 관리자, 조직 관리자). 익명과 일반 구성원은 403이다(v1.6 브랜치 삭제와 같다).
- `refs/tags/<이름>`만 지운다. 없는 태그나 브랜치 이름(`master`, `refs/heads/master`)이면 404이고 아무것도 지우지 않는다.
- 이름은 주소에서 두 번 디코딩한다(`UrlPaths.decodeSegmentOrSelf`). 슬래시가 든 이름도 지워진다.
- 성공하면 태그 목록으로 `303`이다.
- 삭제 단추는 브라우저 `confirm()`으로 먼저 묻는다(`code.tags.deleteConfirm`). 요청을 보내는 `data-request-method` 처리보다 먼저(캡처 단계) 확인하고 취소하면 요청을 막는다.

## 하지 않은 것

- REST API(조회, 생성, 삭제)는 X-19(일반 REST)로 옮겼다.
- `refs/heads`, `refs/tags` 밖의 참조 푸시를 거부하는 정책은 하지 않았다. v1.6의 git 계약을 바꾸는 일이라 X-13 후보로만 기록했다(D-30 7번).
- 주석 태그로 커밋 이력을 열 때의 500(`next`의 P3-40)은 별도로 확인한다.

## 시험

`TagsTest`(목록, 정렬, 배지, 슬래시, 탭, 빈 목록, 삭제 권한과 404, SVN), `BranchesTest`, `PullRequestCreateTest`(브랜치 목록에서 태그 제외), `CodeBrowserWebTest`와 `CommitHistoryTest`(선택 목록 구역).
