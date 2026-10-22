---
title: 공용 편집기 부품과 멘션 후보
type: web
sources: [app/views/common/editor.scala.html, app/views/common/fileUploader.scala.html, app/views/common/uploadForm.scala.html, app/views/help/markdown.scala.html, app/controllers/ProjectApp.java:383-497, app/controllers/AbstractPostingApp.java:221-268, app/controllers/AttachmentApp.java:234-259]
updated: 2026-10-22
---

# 공용 편집기 부품과 멘션 후보

이슈, 게시글, 마일스톤, 댓글 폼이 함께 쓰는 부품이다.

## 템플릿 조각

| 조각 | 원본 | 매개변수 |
|---|---|---|
| `common/editor :: editor(editorName, textValue, additionalAttr, editorMode, viaEmail)` | `common/editor` | `이름-식별자` 형식이면 앞이 textarea의 `name`, 뒤가 영역 식별자(없으면 무작위 영문). `additionalAttr`는 v1.6 호출부가 쓰는 `tabindex=N`, `style=...`, `data-is-user-has-typed=true`를 읽는다 |
| `common/file-uploader :: fileUploader(resourceType, resourceId)` | `common/fileUploader` | 리소스 종류 이름(`MILESTONE` 등), 수정 때의 번호(없으면 `null`) |
| `common/upload-form :: uploadForm(resourceType, resourceId, formId)` | `common/uploadForm` | 올리기 영역 하나 |
| `help/markdown :: markdown` | `help/markdown` | 없음. 마크다운 문법 도움말 10항목 |

## 첨부 목록과 임시 첨부

- `AttachmentListing`: 컨테이너의 첨부를 `{"attachments": [{id, mimeType, name, url, size}]}`로 만든다(이름은 NFC 정규화). 읽을 수 없는 첨부가 하나라도 있으면 `GET /files`는 `403`, 화면의 `data-attachments`는 오류이다.
- `PostAttachments`: 글을 저장할 때 `temporaryUploadFiles`(쉼표로 이은 번호)의 임시 첨부를 글로 옮긴다. **멀티파트 본문일 때만 읽는다**(R-23). 일부를 찾지 못하면 `post.popup.fileAttach.hasMissing`, `...description` 안내를 확인 대화상자(`title`, `description`)로 알린다. 사이트 관리자는 임시 첨부가 아닌 첨부도 옮길 수 있다.

## 멘션 후보 (`GET /:user/:project/mentionList`)

`number`(기본 -1), `resourceType`, `query`, `mentionType`(`user` 또는 `issue`)를 받아 `{"result": [...]}`를 돌려준다. 응답에 `Vary: Accept`를 붙이고 항상 JSON이다. 프로젝트를 읽을 수 있어야 한다(A-35).

- **사용자**: 검색어가 없거나 비공개 프로젝트이면 (1) 글의 댓글 작성자를 최근 순으로, 그 뒤에 글쓴이 (2) 프로젝트 구성원 (3) 조직 구성원 (4) 프로젝트에 이슈, 게시글, 풀 리퀘스트를 썼거나 프로젝트를 구독한 사람 (5) 이슈 공유자 순으로 모으고, 공개 프로젝트에서 검색어가 있으면 활성 사용자를 로그인 ID, 이름, 영문 이름, 이메일에서 찾는다(최근 가입 순 30명, 사이트 관리자 계정 제외). 현재 사용자는 한 번 빼고 맨 뒤에 둔다. 항목은 `loginid`, `searchText`(이름+표시 이름+로그인 ID), `name`(표시 이름), `image`. `admin` 계정은 빠진다.
- 끝에 **프로젝트 전체**(`@project all:`, 열 명을 넘으면 열 번째 자리, 조직 프로젝트는 여덟 번째)와 **조직 전체**(`@group all: `) 항목을 붙인다. 열 명을 넘으면 조직 항목이 열 번째 자리와 맨 끝에 두 번 들어간다(v1.6 동작).
- **이슈**: 최근 이슈 20개. 검색어가 있으면 제목에 들어 있거나 번호가 검색어로 시작하는 것. 포크 프로젝트는 원본 프로젝트의 이슈이다. 항목은 `name`(번호+제목), `issueNo`, `title`.

## 폼 본문 크기 한도

Play의 `parsers.text.maxLength`(기본 100KB)에 해당하는 `yona.parsers-text-max-length`(기본 102400)이다. `application/x-www-form-urlencoded` 본문이 한도를 넘으면 `TextBodyLimitFilter`가 본문을 비우고 쿼리 문자열의 값만 남긴다. v1.6은 이때 컨트롤러가 빈 폼을 받아 `body`/`contents`가 `null`이면 `413`과 `error/request-text-entity-too-large` 화면(`error.tooLargeText.title`, `.limit`, 사이트 관리자에게는 `.admin`)을 보여 준다(A-36). 멀티파트(파일 올리기)에는 적용하지 않는다.
