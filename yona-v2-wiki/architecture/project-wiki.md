---
title: 프로젝트 위키 (X-17)
---

# 프로젝트 위키

결정은 [decisions.md](../decisions.md) D-60, 구현 판단은 A-311, A-312이다. v1.6에는 위키가 없으며 D-28의 추가 개발 항목이다. 범위는 Forgejo 수준, 화면은 GitHub 위키 모양이다.

## 저장

- 위키는 프로젝트마다 하나씩 있는 별도 bare git 저장소이다. DB 테이블과 컬럼을 더하지 않는다(v1.6 DB 불변). 프로젝트의 VCS가 Subversion이어도 위키는 항상 git이다.
- 위치는 코드 저장소 옆의 `<데이터>/repo/git/<소유자>/<이름>.wiki.git`, clone 주소는 `/<소유자>/<이름>.wiki.git`이다(Forgejo 방식). 코드는 `WikiRepositories`.
- 저장소는 첫 페이지를 저장할 때 만든다. 프로젝트 이름 변경, 소유자 이전, 조직 이름 변경에는 코드 저장소와 함께 옮기고(`RepositoryService.moveRepository`), 프로젝트를 지울 때만 지운다(`deleteWiki`). 포크는 위키를 복사하지 않는다.
- 프로젝트 이름은 `.wiki`로 끝날 수 없다(`ProjectNameRules`). 이 규칙 이전에 만든 `<이름>.wiki` 프로젝트가 같은 소유자에 있으면 `<이름>`의 위키는 쓸 수 없다(`WikiService.available`).
- 위키 백업은 사이트 데이터 내보내기와 별개이며 저장소 디렉터리 단위이다.

## 페이지

- 페이지는 저장소 최상위의 `<이름>.md`이다. 제목의 공백은 파일 이름에서 `-`이다. 공백 표기와 하이픈 표기는 같은 페이지이다.
- 제목 규칙: 비어 있지 않고 200자 이하, `\ / : * ? " < > |`와 제어 문자 금지, 점만으로 된 제목 금지, `_`로 시작할 수 없다(`_Sidebar`, `_Footer` 제외).
- 하위 경로는 1차에서 지원하지 않는다. push로 들어온 하위 폴더와 `.md`가 아닌 파일은 목록에서 숨긴다.
- `_Sidebar`, `_Footer`는 목록에서 빼고 따로 읽는다.

## 서비스 (`dev.yona.domain.wiki.WikiService`)

| 메서드 | 동작 |
|---|---|
| `pages`, `search` | 제목순 목록, 제목 부분 일치(대소문자 무시) |
| `page(title, revision?)` | 한 건과 마지막 커밋. 없으면 `null` |
| `create`, `update`, `delete` | 커밋 한 개. 이름 바꾸기는 `update`의 `newTitle`로 같은 커밋에 담는다 |
| `history(title, page, size)` | 페이지 파일의 커밋, 최신순. 이름 바꾸기 이전은 따라가지 않는다 |
| `diff(title, commitId)`, `compare(title, revA, revB)` | 통합 diff 글 |
| `sidebar`, `footer` | 특수 페이지 내용 |

기본 커밋 메시지는 `Create <이름>`, `Update <이름>`, `Rename <옛 이름> to <새 이름>`, `Delete <이름>`이다. 저장하는 사이에 다른 커밋이 들어오면 `WikiConflictException`이다. 권한은 서비스가 아니라 웹과 REST 계층이 확인한다.

## REST (`ExternalWikiApiController`, W3)

경로는 `/-_-api/v1/owners/{owner}/projects/{project}/wiki/...`(D-51)이다. 범위는 `repo`나 공개 프로젝트의 `public_repo`(`ApiScopeRules`)이다. 읽기는 프로젝트를 읽을 수 있으면 되고(코드 접근을 구성원으로 제한한 프로젝트의 제한은 받지 않는다), 쓰기는 `ResourceType.WIKI_PAGE`의 `UPDATE`, 곧 프로젝트 구성원이다.

| 메서드와 경로 | 동작 | 상태 |
|---|---|---|
| `GET wiki/pages?q=` | 목록과 제목 검색 | 200 |
| `GET wiki/pages/{name}?revision=` | 한 건과 마지막 커밋 | 200, 404 |
| `POST wiki/pages` `{title, content?, message?}` | 만들기 | 201, 400, 409 |
| `PATCH wiki/pages/{name}` `{title?, content?, message?}` | 본문 고치기와 이름 바꾸기(한 커밋). 하나는 있어야 한다 | 200, 400, 404, 409 |
| `DELETE wiki/pages/{name}?message=` | 지우기 | 204, 404 |
| `GET wiki/pages/{name}/history?page=&size=` | 이력(최신순, 기본 30, 최대 100) | 200 |
| `GET wiki/pages/{name}/diff?commit=` 또는 `?from=&to=` | 통합 diff 글, 두 리비전 비교 | 200, 400, 404 |

CLI의 옛 경로(`/api/v1/.../wiki`, `PUT`과 `newTitle`)와 다른 점은 W5에서 CLI를 이 계약에 맞춘다. 시험은 `ExternalWikiApiTest`, 문서 구조는 `OpenApiDocumentTest`가 본다.

## 단계

W1 저장소 계층(완료), W2 서비스(완료), W3 REST(완료), W4 화면, W5 CLI, W6 검증과 문서, W7 git clone과 push.
