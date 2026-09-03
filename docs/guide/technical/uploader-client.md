# 첨부파일 업로드 — 클라이언트(JavaScript) 사용법

legacy Yona의 `docs/ko/technical/uploader-client.md`를 옮김. `yobi.Files.js`/`yobi.Attachments.js`
파일 자체가 legacy와 동일한 이름·위치(`static/javascripts/common/`)로 남아 있다. 서버 응답
형식이 일부 달라진 부분(특히 `tempFiles` 키 제거)은
[uploader-server-internal.md](uploader-server-internal.md) 참고.

## 개요

`yobi.Files`는 싱글톤으로 전역에서 동작하며 서버와의 API 통신을 전담한다.
`yobi.Attachments`는 게시판/이슈/코드 등에서 첨부파일 목록을 화면에 표현할 때 쓴다.

## yobi.Files

먼저 `yobi.Files.init()`으로 서버 측 API 주소를 설정해야 한다. 공통 레이아웃에서 이미
호출하고 있으므로 개별 페이지에서 별도로 호출하지 않아도 된다.

```js
yobi.Files.init({
    "sListURL"  : "/files",
    "sUploadURL": "/files"
});
```

### `.getList()`

파일 목록 요청. 커스텀 이벤트는 발생하지 않는다. 콜백 `fOnLoad`, `fOnError`를 옵션으로
지정한다.

```js
yobi.Files.getList({
    "sResourceType": "ISSUE_POST",
    "sResourceId": issue.id,
    "fOnLoad": function(htData){
        // 응답 형식은 uploader-server-internal.md의 "파일 목록" 참고 (tempFiles 키 없음)
    },
    "fOnError": function(htData){
        // 오류 콜백
    }
});
```

### `.deleteFile()`

파일 삭제. 커스텀 이벤트는 발생하지 않는다. 콜백 `fOnLoad`, `fOnError`를 옵션으로 지정한다.

```js
yobi.Files.deleteFile({
    "sURL": "/files/1234",
    "fOnLoad": function(oRes){ /* 성공 콜백 */ },
    "fOnError": function(oRes){ /* 오류 콜백 */ }
});
```

### `.uploadFile()`

파일 전송. `input[type=file]` 엘리먼트를 인자로 주면 선택된 파일을 전송한다. XHR2를 쓸 수
있는 환경에서는 File/FileList/Blob 객체도 인자로 줄 수 있다.

커스텀 이벤트(`.attach("이벤트명", fn)`으로 지정, `.detach()`로 제거):

- `beforeUpload` — 업로드 시작 전. 핸들러 여러 개를 지정할 수 있고, 하나라도 명시적으로
  `false`를 반환하면 업로드를 중단한다.
- `uploadProgress` — 업로드 진행 상태.
- `successUpload` — 업로드 성공(완료) 시.
- `errorUpload` — 업로드 실패 시.

```js
yobi.Files.attach({
    "successUpload": function(htData){
        // 응답 형식은 uploader-server-internal.md 참고
        alert("파일 업로드 성공!");
    },
    "errorUpload": function(htData){
        // htData.oRes는 XHR 또는 $.ajaxForm 응답 객체
    }
});
yobi.Files.attach("beforeUpload", function(){
    return pseudoValidator(); // false를 반환하면 업로드하지 않는다
});

yobi.Files.uploadFile($("input[type=file]"));
// 또는
yobi.Files.uploadFile($("input[type=file]")[0].files);
```

### `.getUploader()`

첫 번째 인자로 지정한 컨테이너 영역 내 `input[type=file]`의 `change` 이벤트, 그리고 가능하면
그 영역의 `drop` 이벤트에 핸들러를 걸어 자동으로 `uploadFile()`을 호출하도록 만든다. 두 번째
인자 `elTextarea`를 지정하면 그 영역에도 `drop`/`paste` 핸들러를 건다.

함수명이 `get`으로 시작하는 이유는 실행 결과로 유일한 업로더 ID를 반환하기 때문이다 —
`yobi.Attachments`와 연계할 때 필요하다. 연계하지 않는다면 반환값은 무시해도 된다.

업로드 컨테이너 영역의 HTML에는 `resourceType`, `resourceId`를 지정해야 한다.

```html
<textarea id="comment-editor" name="contents" class="textbody" rows="5" markdown="true"></textarea>
<div id="upload" data-resourcetype="ISSUE_POST" data-resourceid="123"></div>
<script>
    yobi.Files.getUploader("#upload", "#comment-editor");
</script>
```

## yobi.Attachments

`yobi.Files`가 서버 통신을 담당하고, `yobi.Attachments`는 커스텀 이벤트 핸들러로 화면에
첨부파일 목록을 나타낸다. 파일 업로더와 함께 쓸 수도 있고, `resourceType`/`resourceId`만
지정해서 기존 첨부 목록을 표현할 수도 있다. `yobi.Files`와 달리 `new yobi.Attachments`로
인스턴스를 만들어 쓴다.

```html
<ul class="attachments" data-resource-type="ISSUE_COMMENT" data-resource-id="1234"></ul>
<script>
    new yobi.Attachments({"elContainer": $("#attachments")});
</script>
```

파일 업로더와 섞어서, 커스텀 이벤트에 맞춰 첨부 목록을 표현하는 경우:

```js
var welUploader = $("#uploader");
var welTextarea = $("#body");
var sTplText = $("#tplFileItem").text();
var oUploader = yobi.Files.getUploader(welUploader, welTextarea);
var sUploaderId = oUploader.attr("data-namespace");

new yobi.Attachments({
    "elContainer"  : welUploader,
    "elTextarea"   : welTextarea,
    "sTplFileItem" : sTplText,
    "sUploaderId"  : sUploaderId
});
```

목록 조회 요청을 아예 피하려면, `data-attachments` 속성에 파일 목록을 JSON으로 미리 넣어둘
수 있다 — `yobi.Attachments`가 그 속성을 읽어 렌더링한다.

```html
<ul class="attachments" th:data-attachments="${attachmentsJson}"></ul>
<script>
    new yobi.Attachments({"elContainer": $("#attachments")});
</script>
```
