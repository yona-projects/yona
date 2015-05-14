This document is a troubleshooting guide to help you resolve well-known
problems you might have with Yobi.

### Cannot commit a large file into a SVN repository

If it takes a lot of time to commit a large file into a SVN repository so that
you can't commit the file, the cause may be a lack of memory. Please allocate
more memory to Yobi by using java options as follows:

    _JAVA_OPTIONS="-Xmx4096m -Xms2048m" activator "start -DapplyEvolutions.default=true -Dhttp.port=9000"

Thanks to @zepinos for reporting at https://github.com/naver/yobi/issues/901.
