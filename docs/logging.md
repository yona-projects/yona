Yobi supports logging of system messages for operators and Yobi programmers.
Yobi writes the logs to the log files in `logs` directory and standard output.

Log Files
---------

Unless you modified the logging configuration, logs are written to these files:

* Application logs - `logs/application.log`
* Access logs - `logs/access.log`
* All logs - `logs/root.log`

Every log which is written to `root.log` also written to standard output, but
the formats can be differ. See `conf/application-logger.xml`, the configuration
for logging, for the difference between two formats.

Loggin Configuration
--------------------

You can configure logging in `conf/application-logger.xml`. See LOGBack
Document [1] for details about logging configuration.

Note: PlayFramework allows to configure logging also in
`conf/application.conf`. But Yobi uses `conf/application-logger.xml` for more
detailed configuration. If there is a difference between the two configurtaion
files, Yobi may works incorrectly. Use `conf/application-logger.xml` only for
logging configuration.

Log Levels
----------

<table>
<thead>
<tr><td>Log level</td><td>Description</td></tr>
</thead>
<tbody>
<tr><td>ERROR</td><td>Yobi met an abnormal situation and might work incorrectly.</td></tr>
<tr><td>WARNING</td><td>Yobi met an abnormal situation and recovered or ignored it, but probably work correctly.</td></tr>
<tr><td>INFO</td><td>Diagnostic information for operators and Yobi programmers</td></tr>
<tr><td>DEBUG</td><td>Information for debugging</td></tr>
<tr><td>TRACE</td><td>Not used</td></tr>
</tbody>
</table>

Log Format
----------

### Application log

You can configure the format for Application for by modifying the logging
configuration file.

### Access log

The format for Access log follows Combined Log Format for Apache HTTP Server
[2] except the processing time, the time between when Yobi receives a request
and when Yobi send a response for the request, added at the end of every log
entry, as follows:

    127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif" 200
    - "http://www.example.com/start.html" "Mozilla/4.08 [en] (Win98; I ;Nav)"
    70ms

#### Notes

* Ident field is always filled with "-".
* Processing time is filled with "-" if serving a request is failed.
* For chunked encoding, the time taken to generate response body, e.g. the time
  taken to read a file from a disk and write it to stream to provides it as
  a attachment a user requests, is not included in the processing time.

References
----------

[1]: http://logback.qos.ch/documentation.html
[2]: http://httpd.apache.org/docs/2.2/logs.html
