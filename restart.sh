#!/bin/bash
#
# User this restart shell just for example
#
PORT=9000
YONA_DATA=/yona-data;export YONA_DATA

pid=`ps -ef | grep java | grep com.typesafe.play | awk '{print $2}'`
kill $pid
JAVA_OPTS="-Xmx2048m -Xms1024m -Dyona.data=$YONA_DATA -DapplyEvolutions.default=true -Dhttp.port=$PORT" nohup bin/yona </dev/null >/dev/null 2>&1 &
