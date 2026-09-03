# 첨부파일 업로드 — 서버 측 동작

legacy Yona의 `docs/ko/technical/uploader-server-internal.md`를 옮김. 엔드포인트 경로와 JSON
응답 형식은 `AttachmentController`에 거의 그대로 남아 있다 — 실제 코드로 확인하고, legacy와
다른 부분은 아래에 명시했다.

## 개요

첨부파일 업로드/다운로드는 HTTP 요청과 응답으로 이루어진다. `AttachmentController`가
`AttachmentService`/`Attachment`를 이용해 처리한다.

엔드포인트:

```
POST    /files                  파일 업로드
GET     /files/{id}             파일 다운로드/인라인 조회
POST    /files/{id}             파일 삭제 (쿼리 파라미터 _method=delete 필요)
GET     /files                  파일 목록 조회
```

**legacy와 달리 삭제 요청에 `_method=delete` 쿼리 파라미터가 반드시 필요하다** — 없으면
`400 Bad Request`.

## 파일 업로드

`/files`에 `multipart/form-data`로 POST 요청을 보내고, 파일은 `filePath`라는 이름의
input으로 보낸다.

업로드된 파일은 우선 업로더 본인에게 첨부된다(`containerType=NOT_A_RESOURCE`). 로그인하지
않은 사용자(익명)는 업로드할 수 없다 — `403 Forbidden`.

파일이 정상 업로드되면 서버는 아래와 같은 JSON을 반환한다(legacy와 필드 구성 동일).

```json
{
    "id": "193",
    "name": "스크린샷.png",
    "url": "/files/193",
    "mimeType": "image/png",
    "size": "154440"
}
```

같은 내용(hash)의 파일이 이미 존재하면 `200 OK`, 새로 저장된 파일이면 `201 Created`로
응답한다(legacy에는 없던 구분).

## 파일 다운로드

`/files/{id}`로 GET 요청을 보내면 해당 id의 파일을 받을 수 있다. `action=download` 쿼리
파라미터를 주면 `Content-Disposition: attachment`(강제 다운로드), 없으면 `inline`(브라우저
내 표시 시도)으로 응답한다.

**legacy와 달리 명시적인 권한 체크가 있다** — 첨부파일이 속한 컨테이너(이슈/게시글/댓글 등)에
대한 READ 권한이 없으면 `403 Forbidden`을 반환한다(`accessControl.isAllowedAttachment`,
`docs/PARITY_BACKLOG.md` P1-96 — legacy에는 이 체크 자체가 없었던 보안 결손이었다).

ETag(`"<hash>-<inline|attachment>"`) 기반 조건부 요청(`If-None-Match`)도 지원해서 캐시되어
있으면 `304 Not Modified`를 반환한다 — legacy에는 없던 기능이다.

예외:

- 파일에 대한 읽기 권한이 없으면 `403 Forbidden`.
- 존재하지 않는 id면 `404 Not Found`.

## 파일 목록

`/files`로 `containerType`/`containerId` 쿼리 파라미터와 함께 GET 요청을 보내면 그 리소스에
첨부된 파일 목록을 얻을 수 있다.

```json
{
    "attachments": [
        {"id":"201","name":"스크린샷1.png","url":"/files/201","mimeType":"image/png","size":"267068"},
        {"id":"202","name":"스크린샷2.png","url":"/files/202","mimeType":"image/png","size":"277671"}
    ]
}
```

**legacy와 달리 `tempFiles` 키가 없다** — legacy 응답은 `{"tempFiles":[], "attachments":[...]}`
형태였지만, yona는 `{"attachments":[...]}`만 반환한다.

목록 조회에도 컨테이너에 대한 READ 권한 체크가 있다(다운로드와 동일한 로직 재사용).
