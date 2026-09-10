yona supports logging of system messages for operators and yona programmers.
yona writes logs to standard output (console) as one JSON object per line, via
`src/main/resources/logback-spring.xml` (Logstash encoder).

Ported from legacy Yona's `docs/logging.md`, adapted for yona — most of it changed, since
legacy wrote to separate files under `logs/` via `conf/application-logger.xml`, and yona
writes structured JSON to stdout instead (see "Log Retention" below for why file rotation
isn't the mechanism here).

Log Output
---------

yona does not write to separate log files (`logs/application.log`, `logs/access.log`,
`logs/root.log`) like legacy did. Instead, `src/main/resources/logback-spring.xml` configures
a single console appender with `LogstashEncoder`, so every log line (application logs and
access logs alike) is a single-line JSON object on stdout. This is meant to be scraped by an
external log collector (e.g. Loki/Promtail, CloudWatch Logs, ELK) rather than read directly
from a file on disk.

Logging Configuration
--------------------

Configure log levels in `src/main/resources/application.yml` under `logging.level.*`
(currently `org.springframework.web: DEBUG`, `org.hibernate: WARN`), or edit
`src/main/resources/logback-spring.xml` for encoder/appender changes. This replaces legacy's
`conf/application-logger.xml`.

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

Each line is a JSON object produced by `LogstashEncoder` (timestamp, level, logger, thread,
message, MDC fields, stack trace when present). Configure the format by editing
`logback-spring.xml`.

### Access log

**This part is actually preserved from legacy almost verbatim** — `AccessLogFilter`
(`config/AccessLogFilter.kt`) still logs every request in Apache Combined Log Format (as the
JSON `message` field, wrapped like every other log line by the encoder above), with the same
trailing processing-time-in-milliseconds suffix legacy added:

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

Log Retention
-------------

Access log entries contain personal data (IP address, and the authenticated login ID when
present) as flagged in the legal compliance audit
(`docs/LEGAL_COMPLIANCE_AUDIT_2026-09-10.md`, item #8). **This repository has no file
appender and therefore no rotation/retention policy to configure at the application level**
— everything goes to stdout, and how long it survives depends entirely on whatever collects
it downstream (Loki, CloudWatch Logs, a container runtime's own log driver, etc.).

**Setting and enforcing a retention period for these logs is the responsibility of the
deployment environment's log collector, not this codebase.** Whoever operates a deployment
must configure a retention policy there (e.g. a Loki retention period, a CloudWatch Logs
retention setting, an index lifecycle policy) — nothing in this repository does it for them.

References
----------

[1]: http://logback.qos.ch/documentation.html
[2]: http://httpd.apache.org/docs/2.2/logs.html
