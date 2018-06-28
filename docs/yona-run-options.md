Additional options when running Yona
===

Linux, OSX
----

### Memory allocation

You can also use the `JAVA_OPTS` environment variable to specify Java environment variables.
If the memory is more than 4GB, it is recommended to increase the available memory with the following options.

    JAVA_OPTS="-Xmx2048m -Xms2048m" bin/yona

This is useful when an error related to memory shortage occurs.

```
Ex) Example of writing an execution script created with yona-run.sh


YONA_DATA=/yona-data;export YONA_DATA
JAVA_OPTS="-Xmx4096m -Xms4096m" bin/yona

```

### Change default port

By default it use port 9000. If you want to use a different port,
modify the environment variables.


```
Ex) Example of writing an execution script created with `yona-run.sh` It use 80 port and 2G memory.

YONA_DATA=/yona-data;export YONA_DATA
JAVA_OPTS="-Dhttp.port=80 -Xmx2048m -Xms2048m" bin/yona

```


Windows os
---

When you start Yona, specify the environment variable YONA_DATA and execute it in the following order!
The specified folder of YONA_DATA specifies the location where the configuration file, attachment, code repository, etc. will be created, not the location of the downloaded executable file.
Please refer to [install-yona-server.md](install-yona-server.md) in the Yona Installation Guide.

```
Windows OS Yona Recommended folder
C:\yona\yona-1.3.0 <- unpack by version under the yona folder
C:\yona-data <- conf where files, logs, uploads, and repo folders are created and maintained. Specify with the YONA_DATA environment variable
```

You can also create run.bat with the following contents!

```
SET YONA_DATA=c:\yona-data
bin\yona.bat
```

### Memory allocation

You can also specify Java environment variables using the `SET JAVA_OPTS` environment variable setting. system
If you have more than 4 gigabytes of memory, we recommend running with the following options:

```
Ex) Example of writing an execution script created with `yona-run.sh` It use 2G memory.
    SET YONA_DATA=c:\yona-data
    SET JAVA_OPTS=-Xmx2048m -Xms2048m
    bin\yona.bat
```

By default it use port 9000. If you want to use a different port,
modify the environment variables.

```
Ex) Example of writing an execution script created with `yona-run.sh` It use 80 port and 2G memory.

SET YONA_DATA=c:\yona-data
SET JAVA_OPTS=-Dhttp.port=80 -Xmx2048m -Xms2048m
bin\yona.bat
```

If you are upgrading, migrate the database schema as follows:
You may experience a situation that does not work with a warning message that you need it.

    [warn] play - Your production database [default] needs evolutions!

In such a case, the migration should be done as follows
ApplyEvolutions.default Adds a section with the Java property set to true.

```
SET YONA_DATA=c:\yona-data
SET JAVA_OPTS=-DapplyEvolutions.default=true -Dhttp.port=80 -Xmx2048m -Xms2048m
bin\yona.bat
```

#### A more detailed description of the options

[http://www.playframework.com/documentation/2.3.6/Production](http://www.playframework.com/documentation/2.3.6/Production) 

