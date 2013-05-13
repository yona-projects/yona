HIVE는 사이트 운영자와 HIVE 개발자를 위해 운영중에 발생한 상황에 대한 로그를 남긴다. 로그는 logs 디렉토리에 있는 로그 파일 및 표준출력을 통해 남겨지게 된다.

로그 파일
---------

설정을 변경하지 않았다면, 로그는 다음의 파일에 기록된다.

    애플리케이션 로그: logs/application.log
    액세스 로그: logs/access.log
    모든 로그: logs/root.log

`root.log`에 기록되는 로그는 표준 출력으로도 출력되나, 포맷은 조금 다를 수 있다. 두 포맷이 서로 어떻게 다른지 정확하게 확인해보려면 로그 설정 파일인 `conf/application-logger.xml`을 보라.

로그 설정
---------

어떤 로그를 어디에(어떤 파일 혹은 표준 출력 등)에 기록할 것인지에 대한 설정은 `conf/application-logger.xml`에서 한다.

로그의 설정에 대해서는 [LOGBack 문서](http://logback.qos.ch/documentation.html)를 참조한다.

주의: PlayFramework는 `conf/application.conf`를 통해서도 로그에 대한 설정을 할 수 있도록 허용한다. 그러나 HIVE에서는 보다 정교한 설정을 위해 `conf/application-logger.xml`에 설정을 남기도록 하고 있다. 두 파일의 설정이 각각 다른 경우 의도하지 않은 동작을 하게 될 수 있으므로, 로그 관련 설정은 `conf/application-logger.xml`에서만 하도록 한다.

로그 레벨
---------

다음의 표는 로그 레벨의 의미하는 바를 설명한 것이다. 로그 메시지를 남길 때, 로그 레벨을 어떤 것으로 할 것인가에 대해서는 이 표를 참고하라.

<table>
<thead>
<tr><td>로그 레벨</td><td>설명</td></tr>
</thead>
<tbody>
<tr><td>ERROR</td><td>비정상적인 상황을 만났다. 오동작했거나, 혹은 그럴 가능성이 상당히 있다.</td></tr>
<tr><td>WARNING</td><td>비정상적인 상황을 만났다. 복원 혹은 무시하고 정상적으로 진행했으며, 오동작이 있었을 가능성은 낮다.</td></tr>
<tr><td>INFO</td><td>개발 및 운영에 도움을 주기 위한 정보</td></tr>
<tr><td>DEBUG</td><td>디버깅에 도움을 주기 위한 정보</td></tr>
<tr><td>TRACE</td><td>현재 사용하지 않는다.</td></tr>
</tbody>
</table>

로그 포맷
---------

로그 포맷은 로그 설정 파일을 통해 설정할 수 있다.

다만 logs/access.log에 기록되는(기본 설정에서) 액세스 로그의 경우, 다음의 예와 같이 [Apache HTTP Server의 Combined Log Format](http://httpd.apache.org/docs/2.2/logs.html)을 따른다. 다만 log entry의 끝에 처리시간(요청이 처리되는데 소요된 시간)이 추가된다는 점만이 Combined Log Format과 다르다.

    127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif" 200 - "http://www.example.com/start.html" "Mozilla/4.08 [en] (Win98; I ;Nav)" 70ms

또한 현재는 HIVE 기능상의 한계로 ident와 요청의 길이는 항상 "-" 으로 로그가 남게 된다. 응답이 정상적으로 처리되지 못해 에러가 발생한 경우에는 처리시간도 "-" 으로 기록된다.
