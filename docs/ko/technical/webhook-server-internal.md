이 문서는 웹후크 기능의 내부적 동작에 대해 설명한다.

개요
--------

Yobi의 웹후크 기능은 프로젝트 내의 특정 이벤트(푸시, 코드 주고받기, 이슈 생성 등)와 이로 인해 발생하는 HTTP 요청을 통해 이루어진다. 현재는 push 이벤트에 대해 웹후크가 구현되어 있다.

웹후크의 등록과 삭제는 HTTP 요청과 응답을 통해 이루어진다. 요청은 `controller.ProjectApp`이 `models.Webhook`을 이용하여 처리하며, 해당 요청은 conf/routes 파일에 다음과 같이 정의되어 있다.

```
    GET     /:user/:project/webhooks              controllers.ProjectApp.webhooks(user, project)
    POST    /:user/:project/webhooks              controllers.ProjectApp.newWebhook(user, project)
    POST    /:user/:project/webhooks/:id          controllers.ProjectApp.deleteWebhook(user, project, id: Long)
```

웹후크 등록 및 삭제
--------

웹후크의 등록 및 등록한 웹후크의 확인은 `/:user/:project/webhooks`으로 route되는 view를 통해 할 수 있다. 등록된 웹후크는 해당 웹후크가 등록된 프로젝트 내에서만 확인하고 관리할 수 있으며, 해당 프로젝트에서 발생하는 이벤트를 통해서만 작동한다.

`/:user/:project/webhooks`으로 route되는 view는 등록된 웹후크의 리스트를 보여주며, 웹후크의 등록과 삭제를 POST 요청을 통해 처리하는 UI를 제공한다.

새로운 웹후크의 등록은 `/:user/:project/webhooks`으로 HTTP POST 요청을 함으로서 이루어진다. 이 때 폼을 통해 두 종류의 입력을 payload로 받게 된다. 해당 payload의 명세는 다음과 같다.
* Payload URL : 웹후크가 동작할 시 HTTP 요청을 보낼 주소를 입력한다. URL의 길이는 2000바이트로 제한한다.
* Secret : HTTP 요청을 받을 서버에서 해당 요청을 구분하기 위한 비밀 토큰을 입력한다. 토큰의 길이는 250바이트로 제한한다.

웹후크가 정상적으로 등록되면, 서버는 view를 새로고침하여 추가된 웹후크가 view에 정상적으로 반영되도록 한다. 웹후크 등록에 대한 실제 코드는 `model.Webhook.create`에서 확인할 수 있다.

등록된 웹후크의 삭제는 `/:user/:project/webhooks/:id` 로 HTTP POST 요청을 함으로서 이루어지며, id가 :id인 웹후크를 삭제한다. 웹후크 삭제에 대한 실제 코드는 `model.Webhook.delete`에서 확인할 수 있다.


웹후크 작동
--------

등록된 웹후크는 웹후크가 등록된 프로젝트에서 특정 이벤트가 발생했을때 작동한다. 현재는 push event에 대해서만 구현이 되어 있다.

Push event가 발생하면 Yobi는 `playRepository.RepositoryService.PostReceiveHook`을 동작하여 사전에 정의된 hook들을 실행한다. 이때 `actors.CommitsNotificationActor`를 통해 `model.NotificationEvent.afterNewCommits`가 호출되며, 이곳에서 Yobi는 Project에 걸려 있는 webhook들을 iterate하며 해당하는 payload URL들에 POST 요청을 전송한다. 향후 이벤트의 종류를 확장할 시 이와 같이 `playRepository.RepositoryService`의 알맞은 곳에 이벤트가 invoke되도록 정의를 해줌으로서 적절하게 대응할 수 있다.

웹후크가 작동할 시, 서버는 웹후크에 등록된 Payload URL로 HTTP POST 요청을 전송한다. 요청을 보낼 시 payload에는 웹후크 이벤트의 정보가 담긴 JSON object가 포함된다. 전송되는 JSON object의 예시는 아래와 같다.
```
{
    "ref":[
        "refs/heads/master"
    ],
    "commits":[
        {
            "id":"c2f9f27ea16004020d1f4e846217c2825d217a12",
            "message":"test\n",
            "timestamp":"2015-06-12T04:41:21+0900",
            "url":"http://localhost:9000/dddeeee/commit/c2f9f27ea16004020d1f4e846217c2825d217a12",
            "author":{
                "name":"hello",
                "email":"hello@hello.com"
            },
            "committer":{
                "name":"hello",
                "email":"hello@hello.com"
            }
        }
    ],
    "head_commit":{
        "id":"c2f9f27ea16004020d1f4e846217c2825d217a12",
        "message":"test\n",
        "timestamp":"2015-06-12T04:41:21+0900",
        "url":"http://localhost:9000/dddeeee/commit/c2f9f27ea16004020d1f4e846217c2825d217a12",
        "author":{
            "name":"hello",
            "email":"hello@hello.com"
        },
        "committer":{
            "name":"hello",
            "email":"hello@hello.com"
        }
    },
    "sender":{
        "login":"hello",
        "id":2,
        "avatar_url":"/assets/images/default-avatar-128.png",
        "type":"User",
        "site_admin":false
    },
    "pusher":{
        "name":"hello",
        "email":"hello@hello.com"
    },
    "repository":{
        "id":33,
        "name":"dddeeee",
        "owner":"hello",
        "html_url":"/hello/dddeeee",
        "overview":"eee",
        "private":false
    }
}
```
요청 전송에 대한 실제 코드는 `model.Webhook.sendRequestToPayloadUrl`에서 확인할 수 있다.
