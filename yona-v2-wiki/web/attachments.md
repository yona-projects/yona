---
title: 첨부 파일 (저장, 내려받기)
type: web
sources: [app/models/Attachment.java, app/controllers/AttachmentApp.java:131-172, app/utils/FileUtil.java:105-135, app/utils/HttpUtil.java:64-70, app/models/resource/Resource.java:110-168]
updated: 2026-10-22
---

# 첨부 파일

## 저장 (`AttachmentService`)

- 파일 내용은 SHA-256 해시를 이름으로 `<데이터 디렉터리>/uploads/<해시>`에 **한 벌만** 둔다. 이름, 컨테이너(종류와 번호), 크기, 파일 종류, 올린 사람, 만든 시각은 `attachment` 행에 둔다.
- 올린 파일은 업로드 디렉터리로 **옮겨진다**(원본은 남지 않는다). 이름 변경이 안 되면 복사 후 삭제한다.
- 이름, 해시, 컨테이너가 모두 같은 첨부가 이미 있으면 새로 만들지 않고 기존 첨부를 돌려준다(`created = false`). 내용이 같고 이름이나 컨테이너가 다르면 행은 따로 만들고 파일은 하나다.
- 이름이 없으면 `현재시각(밀리초).확장자`로 만든다. 확장자는 감지한 종류에서 얻는다.
- 삭제하면 행을 지우고, **같은 해시를 쓰는 첨부가 더 없을 때** 파일도 지운다. `deleteAll`은 컨테이너의 첨부를 모두 지운다.
- v1.6의 컨테이너별 목록 캐시(`AttachmentCache`)는 두지 않는다(동작은 같고 조회만 매번 DB에서 한다).

### 파일 종류 감지

Apache Tika(`tika-core` 3.3.3)로 이름과 내용을 보고 감지한다. 텍스트이면 `; charset=<인코딩>`을 덧붙이고, 오그 오디오인데 확장자가 `.ogv`이면 `video/ogg`로 고친다. v1.6은 Tika가 앞 8192바이트를 읽은 뒤의 스트림 나머지로 문자셋을 감지하므로 **8192바이트 이하의 텍스트 파일은 항상 UTF-8**이 된다. 같게 재현한다(R-17).

## 내려받기 (`GET /files/:id`, `GET /files/:id/`)

| 상황 | 응답 |
|---|---|
| 첨부가 없음 | `404` `The file does not exist.` (text/plain) |
| 읽을 권한이 없음 | `403` `You have no permission to get the file.` |
| `If-None-Match`가 ETag와 같음 | `304`, `ETag`, `Cache-Control: private, max-age=3600` |
| 저장소에 파일이 없음 | `500` `The file does not exist` |
| 그 밖 | `200`, 파일 내용 전체, `Content-Type`=저장된 종류, `Content-Disposition`, `ETag` |

- `ETag`는 `"<해시>-<inline|attachment>"`이다. `?action=download`이면 `attachment`, 아니면 `inline`이다.
- `Content-Disposition`의 파일 이름은 `:`, `\`, `/`, `{`, `?`를 `_`로 바꾸고 UTF-8 퍼센트 인코딩(공백은 `%20`)해 `filename*=UTF-8''...`로 쓴다.
- `RANGE` 요청 헤더가 있으면 `Accept-Ranges: bytes`만 붙이고 부분 응답(206)은 하지 않는다(v1.6 동작).

### 읽기 권한

첨부는 컨테이너를 따른다. 컨테이너가 프로젝트에 속하면 그 프로젝트의 규칙(`ATTACHMENT`는 컨테이너 의존 종류), 아니면 전역 규칙이다. 컨테이너가 사용자(`USER`)인 첨부는 임시 첨부로 올린 사용자만 읽을 수 있고, 아바타(`USER_AVATAR`)나 조직은 누구나 읽는다. 컨테이너의 프로젝트를 잃은 첨부는 오류이다.

`ResourceLookup`이 종류와 번호로 리소스를 찾는다. 지금은 이슈, 게시글, 마일스톤, 사용자, 아바타, 프로젝트, 조직만 지원하고 나머지 종류(코멘트, 풀 리퀘스트 등)는 해당 서브시스템을 옮길 때 추가한다.

## 아직 없는 부분

`POST /files`(업로드), `GET /files`(목록), `POST /files/:id`(삭제), 임시 파일 정리 스케줄러는 편집기와 사용자 화면을 옮길 때 추가한다.
