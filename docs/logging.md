yona supports logging of system messages for operators and yona programmers.
yona writes logs to standard output (console), using Spring Boot's default Logback setup.

Ported from legacy Yona's `docs/logging.md`, adapted for yona — most of it changed, since
legacy wrote to separate files under `logs/` via `conf/application-logger.xml`, and yona doesn't
currently do that.

Log Output
---------

**Unlike legacy, yona does not currently write to separate log files**
(`logs/application.log`, `logs/access.log`, `logs/root.log`) — there's no `logback-spring.xml`
in this repository, so everything (application logs and access logs alike) goes to the console
via Spring Boot's default Logback configuration. If you need file output, add your own
`logback-spring.xml` to `src/main/resources/` (standard Spring Boot mechanism) — this hasn't
been done yet.

Logging Configuration
--------------------

Configure logging in `src/main/resources/application.yml` under `logging.level.*` (currently
`org.springframework.web: DEBUG`, `org.hibernate: WARN`), or add a `logback-spring.xml` for
finer control. This replaces legacy's `conf/application-logger.xml`.

Log Levels
----------

| Log level | Description |
|---|---|
| ERROR | yona hit an abnormal situation and might work incorrectly. |
| WARNING | yona hit an abnormal situation and recovered or ignored it, but probably works correctly. |
| INFO | Diagnostic information for operators and yona programmers. |
| DEBUG | Information for debugging. |
| TRACE | Not used. |

Log Format
----------

### Application log

Configure the format by modifying the Logback configuration, as above.

### Access log

**This part is actually preserved from legacy almost verbatim** — `AccessLogFilter`
(`config/AccessLogFilter.kt`) still logs every request in Apache Combined Log Format, with the
same trailing processing-time-in-milliseconds suffix legacy added:

```
127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif" 200
- "http://www.example.com/start.html" "Mozilla/4.08 [en] (Win98; I ;Nav)"
70ms
```

#### Notes

* The ident field is always `-`.
* Processing time is `-` if serving the request failed.
* Unlike legacy, this filter runs for **every** request, not just successful ones — legacy hooked
  it into `Global.onRequest()`/`onError()`/`onBadRequest()`/`onHandlerNotFound()` separately;
  yona uses a single Spring Security filter (`addFilterAfter(accessLogFilter, ...)`) that always
  runs.
* Access log entries currently go through a single `"access"` SLF4J logger (legacy dynamically
  created a separate `Logger("access." + uri)` per path) — route by path via log pattern/MDC if
  you need that level of separation.

References
----------

[1]: http://logback.qos.ch/documentation.html
[2]: http://httpd.apache.org/docs/2.2/logs.html
