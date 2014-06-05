# Play Framework Migration (2.1 -> 2.2)

## SBT version

### build.properties

sbt.version 값을 수정한다.

    sbt.version=0.13.0

### plugins.sbt

sbt plugin 의 groupId 와 version 값을 수정한다.

    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2-SNAPSHOT")

## library version

기존 사용하고 있던 play framework 관련 library 들의 version 을 2.2 버전에 호환되는 버전으로 변경한다.
이 때, 해당 라이브러리가 여전히 scala 2.10.0 이나 play 2.1 에 의존하고 있다면 제외한다.

    "info.schleichardt" %% "play-2-mailplugin" % "0.9.1" exclude("org.scala-lang", "scala-library") exclude("play", "play_2.10")
    "com.github.julienrf" %% "play-jsmessages" % "1.5.1" exclude("org.scala-lang", "scala-library") exclude("play", "play_2.10")

## akka migration

[http://doc.akka.io/docs/akka/2.2.0/project/migration-guide-2.1.x-2.2.x.html](http://doc.akka.io/docs/akka/2.2.0/project/migration-guide-2.1.x-2.2.x.html "akka 2.2 migration guide")


### applicafion.default.conf, application.conf

event-handlers 설정은 deprecated 되었다. 대신 loggers 를 사용한다.

    loggers = ["akka.event.Logging$DefaultLogger", "akka.event.slf4j.Slf4jLogger"]

### akka.actor.Props 를 사용하는 class

Props 의 construcor 가 deprecated 되었다. 대신 create 를 사용한다.

    Akka.system().actorOf(Props.create(MyActor.class)).tell(message, null);

## jackson

jackson library 의 version 이 변경되면서 package 구성이 변경됨.

기존 ```org.codehaus.jackson.node.ObjectNode``` 와 ```org.codehaus.jackson.JsonNode``` 대신 아래를 사용한다.

    com.fasterxml.jackson.databind.node.ObjectNode
    com.fasterxml.jackson.databind.JsonNode

## 변경된 API 적용

### play.mvc.Action

action composition 을 위해 사용되고 있는 class 들의 상위 class 인 ```play.mvc.Action``` 의 interface 가 변경됨.

call method 의 return type 을 ```play.mvc.Result``` 에서 ```play.libs.F.Promise``` 로 바꿔 주어야 한다.

    public abstract class Action<T> extends Results {
        ...
        public abstract Promise<SimpleResult> call(Context ctx) throws Throwable;
        ...
    }

#### example

AS-IS

    return forbidden();

TO-BE

    return Promise.pure((SimpleResult) forbidden()));

### play.GlobalSettings

onBadRequest, onError, onHandlerNotFound method 들의 return type 이 ```play.mvc.Result``` 에서 ```play.libs.F.Promise``` 로 변경됨.

### play.api.mvc.PlainResult

deprecated. play 2.2 에서부터는 SimpleResult 만을 사용한다.

### jackson

기존의 node 값을 얻어 오는 method 이름이 변경됨.

* get[data-type]Value 형태의 method 들에서 get 이 삭제
* jsonNode.getTextValue() -> jsonNode.textValue()
* jsonNode.getBooleanValue() -> jsonNode.booleanValue()

### jsmessage

JsMessages class 의 generate method 는 더 이상 static method 가 아니다.
따라서 JsMessages 의 객체를 만들어서 사용해야 한다.

    public class Application extends Controller {
        ...

        static final JsMessages messages = new JsMessages(play.Play.application());

        public static Result jsMessages() {
            return ok(messages.generate("Messages")).as("application/javascript");
        }

        ...
    }

## Known Issues

### 현상

play 2.1 에서 사용하던 코드 중, 아래와 같은 구조의 코드는 play 2.2 에서 정상적으로 compile 은 되지만, runtime 시 ```java.lang.VerifyError``` 를 발생시킨다.

	TestObject object = new TestObject();

	object.name = "test";  // get or set string value

	try { // in try
	    object.id = 100L;  // get or set long value
	    String name = object.name;  // get or set string value
	} catch (Throwable t) {
	    t.printStackTrace();
	}

* member variable 에 대한 접근이 꼭 하나의 object 에 대한 것이 아니어도 동일한 문제가 발생한다.
* string - long 이 아니더라도 서로 cast 할 수 없는 Object 에 대해서도 문제가 발생한다.
* member variable 에 대한 접근을 getter/setter 를 통해서 할 경우 문제가 발생하지 않는다.
* 위 코드에서 try-catch 가 없을 경우 문제가 발생하지 않는다.

### 원인

- play framework 는 javassist 를 이용해서 public member variable 에 대한 직접적인 accesss 를 getter/setter 로 변경한다.
- v2.1 에선 javassist 3.16 을 사용, v2.2 에선 javassist 3.18을 사용
- javassist 3.17 version 이후부터 class file 의 stackmap 을 구성하는 코드가 변경되었고,
이로 인해서 변조된 class file 이 JAVA7 이상에서 사용되는 class verifier 의 검증을 통과하지 못하는 경우가 발생한다.

### workaround

이 문제는 javassist 의 bug 로 3.18.2 version 에서 해결되었다. 아래와 같이 plugin.sbt 에서 javassist 의 version 을 변경해야 한다.

    libraryDependencies += "org.javassist" % "javassist" % "3.18.2-GA"

play framework 에 [근본적인 해결을 위한 커밋](https://github.com/playframework/playframework/commit/78b8bf8b6b180d3a29eb1f61e7626c35b6e77f85) 이 작성 되었지만, 아직 릴리즈 계획을 알 수 없다.

### 관련 링크

- [javassist issue](https://issues.jboss.org/browse/JASSIST-212)
- [play framework issue](https://github.com/playframework/playframework/issues/1966)
- [Play 2.2 Migration Guide](http://www.playframework.com/documentation/2.2.x/Migration22)
