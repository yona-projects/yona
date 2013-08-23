이 문서는 첨부 파일 기능의 클라이언트(JavaScript) 코드 사용법을 설명한다

개요
----

yobi.Files 는 싱글톤으로 전역에서 동작하며 서버와의 API 통신을 전담한다 (Controller 성격).

yobi.Attachments 는 게시판/이슈/코드보내기와 같이 첨부파일 목록을 표현해야 하는 경우 사용한다 (View 성격).


yobi.Files
----
먼저 yobi.Files.init() 으로 서버 API 주소를 설정해야 한다.

공통 페이지인 views/scripts.scala.html 에서 이미 yobi.Files.init() 을 아래와 같이 호출하고 있으므로

개별 페이지에서는 별도로 호출하지 않아도 무방하다.

```
yobi.Files.init({
    "sListURL"  : "@routes.AttachmentApp.getFileList",
    "sUploadURL": "@routes.AttachmentApp.uploadFile"
});
```

이외에 제공되는 메소드는 다음과 같다

- .getList(): 파일 목록 수신. 응답 형식에 대해서는 uploader-server-internal.md 에서 "파일 목록" 항목을 참조.
- .uploadFile(): 파일 전송. XHR2 사용 가능 환경에서는 File 또는 Blob 객체여도 가능하다
- .deleteFile(): 파일 삭제
- .setUploader(): 지정한 컨테이너 영역을 파일 업로더로 만들어 주는 함수
- .attach(): 커스텀 이벤트 핸들러 지정
- .detach(): 커스텀 이벤트 핸들러 삭제

제공되는 커스텀 이벤트는 아래와 같다

- beforeUpload: 업로드 시작 전 이벤트. 핸들러 함수는 여러개 지정할 수 있는데 단 하나라도 false 를 명시적으로 반환하면 업로드를 중단한다
- uploadProgress: 업로드 진행 상태 처리를 위한 이벤트
- successUpload: 업로드 성공(완료) 시 발생하는 이벤트
- errorUpload: 업로드 실패시 발생하는 이벤트

getList, deleteFile 은 일회성 호출이므로 커스텀 이벤트가 발생하지 않으며

대신 fOnLoad, fOnError 옵션으로 콜백 함수를 지정하여 사용한다.

예: 목록 표시 필요 없이 파일 업로드 기능만 구현하는 경우

```  
yobi.Files.attach({  
    "successUpload": function(){
        alert("파일 업로드 성공!");
    }
});
yobi.Files.uploadFile($("input[type=file]"));
```

setUploader 는 지정한 영역내에 존재하는 input[type=file]의 change 이벤트, 그리고 가능하다면 해당 영역의 drop 및 paste 이벤트에 핸들러를 설정하고

자동으로 uploadFile 메소드를 이용한 파일 업로드를 수행하도록 만들어 준다. 

진행 상태 표시나 완료 후 처리 등은 위에서 설명한 커스텀 이벤트를 설정한다.

```
yobi.Files.setUploader("#uploader");
```


yobi.Attachments
----

yobi.Files 가 서버와의 통신을 담당하고 yobi.Attachments 는 커스텀 이벤트 핸들러를 이용해 화면에 첨부된 파일 목록을 나타내는데 사용한다. 

파일 업로더와 함께 사용할 수도 있고, 단순히 resourceType 과 resourceId 만 지정해서 기존에 첨부된 파일 목록을 표현할 수도 있다.

yobi.Files 와 달리 new yobi.Attachments 로 인스턴스를 생성하여 사용한다.


예: 단순한 첨부파일 목록 표현 yobi.Attachments
```
<ul class="attachments" data-resourceType="ISSUE_COMMENT" data-resourceId="1234"></ul>  
<script type="text/javascript">
    new yobi.Attachments({"elContainer": $("#attachments")});
</script>

```

파일 업로더와 혼합하여 커스텀 이벤트 발생에 맞추어 첨부 파일 목록을 표현하는 경우 아래와 같이 사용한다.

예: yobi.Files.setUploader 와 yobi.Attachments 연결 예제

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
