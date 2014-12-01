이 문서는 첨부 파일 기능의 클라이언트(JavaScript) 코드 사용법을 설명한다

개요
----

yobi.Files 는 싱글톤으로 전역에서 동작하며 서버와의 API 통신을 전담한다.
yobi.Attachments 는 게시판/이슈/코드보내기와 같이 첨부파일 목록을 표현해야 하는 경우 사용한다.


yobi.Files
----
먼저 yobi.Files.init() 으로 서버측 API 주소를 설정해야 한다.
공통 페이지인 views/scripts.scala.html 에서 이미 yobi.Files.init() 을 아래와 같이 호출하고 있으므로
개별 페이지에서는 별도로 호출하지 않아도 무방하다.

```
yobi.Files.init({
    "sListURL"  : "@routes.AttachmentApp.getFileList",
    "sUploadURL": "@routes.AttachmentApp.uploadFile"
});
```

이 외에 제공되는 메소드는 다음과 같다


### .getList()

파일 목록 요청. 커스텀 이벤트는 발생하지 않는다.
옵션으로 콜백함수 fOnLoad, fOnError 를 지정하여 처리해야 한다

```
yobi.Files.getList({
    "sResourceType": "@Resource.ISSUE_POST",
    "sResourceId": "@issue.id",
    "fOnLoad": function(htData){
        // 응답 형식에 대해서는 uploader-server-internal.md 에서 "파일 목록" 항목을 참조.
    },
    "fOnError": function(htData){
        // 오류 발생시 실행될 콜백 함수
    }
});
```

### .deleteFile()

파일 삭제. 커스텀 이벤트는 발생하지 않는다.
옵션으로 콜백함수 fOnLoad, fOnError 를 지정하여 처리해야 한다

```
yobi.Files.deleteFile({
    "sURL": "/files/1234",
    "fOnLoad": function(oRes){
        // 성공시 콜백 함수
    },
    "fOnError": function(oRes){
        // 오류 발생시 콜백 함수
    }
});
```

### .uploadFile()

파일 전송. input[type=file] 엘리먼트 객체를 인자로 주면 선택된 파일을 전송한다.
XHR2 사용 가능 환경에서는 File, FileList, Blob 객체도 인자로 줄 수 있다.
사용 가능한 커스텀 이벤트는 아래와 같으며 .attach("이벤트명", function(){}); 로
핸들러를 지정하고 같은 방식으로 .detach() 를 이용해 핸들러를 제거할 수 있다.

- beforeUpload: 업로드 시작 전 이벤트. 핸들러 함수는 여러개 지정할 수 있는데 단 하나라도 false 를 명시적으로 반환하면 업로드를 중단한다
- uploadProgress: 업로드 진행 상태 처리를 위한 이벤트
- successUpload: 업로드 성공(완료) 시 발생하는 이벤트
- errorUpload: 업로드 실패시 발생하는 이벤트

예: 기본 사용법
```
yobi.Files.attach({
    "successUpload": function(htData){
        // 응답결과(htData) 형식은 uploader-server-internal.md 참조
        alert("파일 업로드 성공!");
    },
    "errorUpload": function(htData){
        // htData.oRes 는 XHR 또는 $.ajaxForm 에서 제공하는 응답결과 객체
        // htData.oRes.status
        // htData.oRes.statusText
    }
});
yobi.Files.attach("beforeUpload", function(){
    return pseudoValidator(); // false 를 반환하면 업로드 진행하지 않는다
});

yobi.Files.uploadFile($("input[type=file]"));
// 또는
yobi.Files.uploadFile($("input[type=file]")[0].files);
```

### .getUploader

첫 번째 인자로 지정한 컨테이너 영역내에 존재하는 input[type=file]의 change 이벤트,
그리고 가능하다면 해당 영역의 drop 이벤트에 핸들러를 설정하고 자동으로 uploadFile 메소드를 이용한 파일 업로드를 수행하도록 만들어 준다.
두 번째 인자인 elTextarea 를 지정할 경우 이 영역에 대해서도 drop 및 paste 이벤트 핸들러를 설정한다.

함수명이 get 으로 시작하는 이유는 이 함수의 실행 결과로 업로더 ID(Unique)를 반환하기 때문인데
이 값은 yobi.Attachments 를 사용하여 첨부 파일 목록을 표현할 때 필요하다.
yobi.Attachments 와 연계하여 사용할 것이 아니라면 반환값은 무시해도 무방하다.

서버측 HTML을 구성할 때 업로드 컨테이너 영역에는 resourceType 과 resourceId 를 지정해야 한다.

진행 상태 표시나 완료 후 처리 등은 위에서 설명한 커스텀 이벤트를 설정하여 구현한다.

예: 기본 사용법
```
<textarea id="comment-editor" name="contents" class="textbody" rows="5" markdown="true"></textarea>
<div id="upload" data-resourceType="@Resource.ISSUE_POST" data-resourceId="@issue.id>"</div>
<script type="text/javascript">
    yobi.Files.getUploader("#upload", "#comment-editor");
</script>
```


yobi.Attachments
----

yobi.Files 가 서버와의 통신을 담당하고 yobi.Attachments 는 커스텀 이벤트 핸들러를 이용해 화면에 첨부된 파일 목록을 나타내는데 사용한다.
파일 업로더와 함께 사용할 수도 있고, 단순히 resourceType 과 resourceId 만 지정해서 기존에 첨부된 파일 목록을 표현할 수도 있다.
yobi.Files 와 달리 new yobi.Attachments 로 인스턴스를 생성하여 사용한다.

예: 단순한 첨부파일 목록 표현 yobi.Attachments
```
<ul class="attachments" data-resource-type="ISSUE_COMMENT" data-resource-id="1234"></ul>
<script type="text/javascript">
    new yobi.Attachments({"elContainer": $("#attachments")});
</script>

```

파일 업로더와 혼합하여 커스텀 이벤트 발생에 맞추어 첨부 파일 목록을 표현하는 경우 아래와 같이 사용한다.

예: yobi.Files.getUploader 와 yobi.Attachments 연결 예제

```
var welUploader = $("#uploader");
var welTextarea = $("#body");
var sTplText = $("#tplFileItem").text();
var oUploader = yobi.Files.getUploader(welUploader, welTextarea);
var sUploaderId = oUploader.attr("data-namespace");

(new yobi.Attachments({
    "elContainer"  : welUploader,
    "elTextarea"   : welTextarea,
    "sTplFileItem" : sTplText,
    "sUploaderId"  : sUploaderId
}));
```

파일 목록을 가져오는 요청을 보내는 것을 피하기 위해, 다음과 같이
data-attachments 속성에 파일 목록을 json 형식으로 미리 넣어둘 수 있다.
이후 yobi.Attachments가 그 속성을 읽어서 파일 목록을 렌더링한다.

```
<ul class="attachments" data-attachments="@toJson(AttachmentApp.getFileList(ResourceType.ISSUE_COMMENT.toString(), comment.id.toString()))"></ul>
<script type="text/javascript">
    new yobi.Attachments({"elContainer": $("#attachments")});
</script>

```
