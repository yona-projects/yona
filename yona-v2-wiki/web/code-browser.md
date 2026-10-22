---
title: 코드 브라우저
type: web
sources: [app/controllers/CodeApp.java, app/playRepository/GitRepository.java:185-640, app/playRepository/RepositoryService.java:76-125, app/views/code/view.scala.html, app/views/code/partial_view_folder.scala.html, app/views/code/partial_view_file.scala.html, app/views/code/nohead.scala.html, app/utils/TemplateHelper.scala:215-264, app/utils/TemplateHelper.scala:506-584]
updated: 2026-10-23
---

# 코드 브라우저

저장소의 폴더와 파일을 화면과 JSON으로 보여 준다. 구현: `CodeBrowserController`(웹), `CodePageAssembler`(화면 값), `CodeBrowserService`(저장소 읽기, JGit), 템플릿 `code/view.html`, `code/nohead.html`. Git만 지원하며 Subversion은 8단계에서 더한다(그때까지 `501`, `{vcs} is not supported!`).

| 요청 | 동작 |
|---|---|
| `GET /:user/:project/code` | 읽기 권한. 저장소가 비었으면 처음 올리는 방법(`nohead`, 구성원 이상에게는 `git clone 로그인ID@주소` 안내 포함), 아니면 기본 브랜치의 `/code/<브랜치>/`로 `303` |
| `GET …/code/:branch`, `…/code/:branch/*path` | 폴더 목록 또는 파일 보기. 위쪽 폴더(`recursiveData`)부터 차례로 그린다. 없는 경로나 브랜치는 프로젝트 안의 `404`(`code`) |
| `GET …/code/!`, `…/code/!/*path`, `…/code/:branch/!`, `…/code/:branch/!/*path` | 폴더나 파일의 메타데이터 JSON(코드 브라우저 JS가 폴더를 펼칠 때 쓴다). 없으면 빈 `404` |
| `GET …/code/:branch/download` | 브랜치를 zip으로(`Content-Disposition: attachment; filename=<프로젝트>-<브랜치>.zip`) |
| `GET …/rawcode/:rev/*path` | 원본을 `text/plain`으로. 내용에서 문자 집합을 추정해 붙인다 |
| `GET …/image/:rev/*path` | 내용으로 감지한 이미지 종류로 |
| `GET …/files/:rev/*path` | 이름과 내용으로 감지한 종류 그대로 브라우저에서 연다(읽기 권한만 요구) |

코드를 구성원에게만 여는 프로젝트(`is_code_accessible_member_only`)는 비구성원에게 `403`이다(`@CodeAccessCheck`).

## 메타데이터 JSON

- 폴더: `{"type": "folder", "data": {이름: {type, msg, avatar, userName, userLoginId, createdDate, author, commitId(7자), commitUrl}}}`. 항목마다 **마지막으로 바꾼 커밋**의 정보이다. 이름은 정렬된 순서이다.
- 폴더 하나만 담은 중간 폴더는 건너뛰어 `one/two`처럼 합친 경로로 나온다(folder jumping). 판단은 브랜치가 아니라 HEAD의 트리로 한다(v1.6 그대로).
- 항목이 없는 폴더는 `null`이다.
- 파일: `{"type": "file", msg, author, avatar, userName, userLoginId, createdDate, commitMessage, commiter, commitDate, commitId(브랜치 머리 커밋 전체), size, isBinary, data, mimeType}`. `data`는 텍스트이고 `yona.code-browser-max-file-size`(기본 1MB) 이하일 때만 있다. 문자 집합은 내용으로 추정한다.
- 작성자는 커밋 이메일로 사용자를 찾는다. 없으면 `userLoginId`는 빈 값, `userName`은 `user.notExists.name` 문구이다.

## 화면

- 탭(Files, Commit, Branches), 브랜치 선택 상자(`#branches`, 값은 같은 경로의 다른 브랜치 주소), 이동 경로(`#breadcrumbs`), 내려받기, 로그인하면 새 파일(`postform?path=&branch=`) 단추.
- 폴더 목록은 폴더가 앞, 파일이 뒤이다. 아이콘 클래스는 폴더 `dynatree-ico-cf`, 파일 `dynatree-ico-c`.
- 파일 보기: 작성자, 시각, 리비전(7자), 커밋 메시지, 줄바꿈 종류, Raw와 Edit(로그인), 브라우저로 열기, 변경 이력. 본문은 이미지, 이진 파일 안내, 너무 큰 파일 안내, 마크다운 렌더링(`.md` 등), 코드(`#codeVal` 숨김 + `pre#showCode`, ace가 색을 입힌다)이다.
- 브랜치와 리비전을 주소에 넣을 때는 v1.6처럼 두 번 인코딩한다(`feature/x` → `feature%252Fx`). 서버는 한 번 디코딩해 받은 값을 `decodePathSegment`로 한 번 더 디코딩한다. 그래서 `SecurityConfig`가 방화벽의 `%25`, `%2F` 거부를 푼다([decisions.md] A-69).

## v1.6과 다른 점과 재현한 결함

[decisions.md] A-67(폴더 메타데이터를 캐시하지 않음), A-68(없는 원본 파일은 `404`), A-69(방화벽 설정), A-70(이동 경로 이스케이프). 재현한 결함은 [refactoring-backlog.md] R-38(폴더 이동 주소에 `#cb-…` 앵커가 붙지 않음), R-39(이진 파일 안내의 크기 표시가 비어 있음).

## 테스트

`CodeBrowserServiceTest` 11건(실제 저장소에 커밋을 만들어 폴더, 파일, 중간 폴더 건너뛰기, 이진, 큰 파일, 브랜치, 상위 폴더, 원본, zip), `CodeBrowserWebTest` 14건.
