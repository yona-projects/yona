이 문서는 파일 업로드 기능의 서버측 동작을 설명한다.

개요
----

Yobi의 파일 업로드 및 다운로드 기능은 HTTP 요청과 응답을 통해 이루어진다. 요청은 `controller.AttachmentApp`이 `models.Attachment`를 이용하여 처리한다.

파일을 업로드하거나 다운로드하는 요청은 conf/routes 파일에 다음과 같이 정의되어 있다.

    GET     /files                                          controllers.AttachmentApp.getFileList()
    POST    /files                                          controllers.AttachmentApp.newFile()
    GET     /files/:id                                      controllers.AttachmentApp.getFile(id: Long)
    GET     /files/:id/?                                    controllers.AttachmentApp.getFile(id: Long)
    POST    /files/:id/?                            		controllers.AttachmentApp.deleteFile(id: Long)
    POST    /files/:id                            			controllers.AttachmentApp.deleteFile(id: Long)

파일 업로드
-----------

/files로 POST 요청을 보내서 파일을 업로드 할 수 있다. 파일 업로드의 HTTP 요청은 인코딩이 `multipart/form-data`인 HTML form으로 파일을 `filePath`라는 이름의 file 타입 input element를 통해 submit하는 것과 같게 하면 된다.

업로드된 파일은 사용자에게 첨부되며, 그 사용자와 사이트 관리자를 제외한 다른 사용자의 접근은 금지된다. (일반적인 첨부파일의 경우, 첨부한 대상에 대한 읽기 권한이 있다면 첨부파일에도 읽기 권한이 있는 것으로 간주한다는 것을 생각하면 이 동작은 다소 예외적인 것이다. 실제 동작이 궁금하다면 `utils.AccessControl.isGlobalResourceAllowed` 메소드를 보라)

사용자에게 첨부된 첨부 파일을 다른 리소스(이슈, 포스팅 등)에 첨부하려면, models.Attachment.moveAll 메소드를 이용한다.

사용자에게 첨부되는 것을 생략하고 바로 파일을 리소스에 첨부하는 것도 가능한데, 이를 위해서는 models.Attachment.store 메소드를 호출한다. 프로젝트의 로고를 설정하는 기능의 경우 이 방법을 사용한다.

파일이 정상적으로 업로드되었다면, 서버는 업로드된 파일에 대한 메타데이터를 다음과 같이 json 형식으로 반환한다.

    {
        "id":"193",
        "name":"스크린샷, 2013-03-08 031557.png",
        "url":"/files/193",
        "mimeType":"image/png",
        "size":"154440"
    }

주의: 위 응답은, 클라이언트의 유저에이전트가 인터넷 익스플로러인 경우, 실제로는 본문이 json 형식임에도 불구하고 Content-Type이 text/html로 설정되어 반환된다. 이유는 Content-Type을 application/json로 하여 응답하면, 인터넷 익스플로러는 무조건 다운로드 받으려 시도하기 때문에, 이 응답을 자바스크립트로 처리하게 할 수 없기 때문이다.

예외처리
* 사용자가 로그인을 하지 않은 경우, 403 Forbidden이 반환된다.

파일 업로드에 대한 실제 코드는 models.AttachmentApp.newFile 메소드에서 확인할 수 있다.

파일 다운로드
-------------

/files/:id로 GET 요청을 보내면 id가 :id인 파일을 다운로드 할 수 있다.

주의: 파일명은 RFC 2231에 따라 인코딩된다. 따라서 인터넷 익스플로러 8 이하 버전, 사파리 5 이하 버전에서는 웹브라우저가 파일 이름을 올바르게 해석하지 못해 오동작이 발생할 수 있다.

예외처리
* 파일이 첨부된 리소스에 대한 읽기 권한이 없는 경우 403 Forbidden이 반환된다.
* :id에 해당하는 파일이 존재하지 않는 경우 404 Not Found가 반환된다.

파일 다운로드에 대한 실제 코드는 models.AttachmentApp.getFile 에서 확인할 수 있다.

파일 목록
---------

/files로 GET 요청을 보내면 특정 리소스에 첨부된 파일들의 목록을 얻을 수 있다. 이 HTTP 요청은, 인코딩이 application/x-www-form-urlencoded 이고, 이름이 containerType과 containerId인 input element를 포함한 HTML form을 submit 했을 때의 요청과 같다. `containerType`의 값은 첨부파일을 얻을 리소스의 타입, `containerId`의 값은 첨부파일을 얻을 리소스의 아이디이다.

첨부파일의 목록은 다음의 예와 같이 json 형식으로 반환된다. (요청의 Accept 헤더는 무시한다)

    {
        "tempFiles":[],
        "attachments":[
            {
                "id":"201",
                "name":"스크린샷, 2013-03-09 012043.png",
                "url":"/files/201",
                "mimeType":"image/png",
                "size":"267068"
            },
            {
                "id":"202",
                "name":"스크린샷, 2013-03-15 225249.png",
                "url":"/files/202",
                "mimeType":"image/png",
                "size":"277671"
            }
        ]
    }

파일 목록에 대한 실제 코드는 models.AttachmentApp.getFileList 에서 확인할 수 있다.
