# User this restart shell just for example
#
PORT=9000
YONA_DATA=/Users/doortts/repos/yona;export YONA_DATA
act=/Users/doortts/apps/play2

pid=`ps -ef | grep java | grep activator-launch | awk '{print $2}'`
kill $pid
_JAVA_OPTIONS="-Xmx2048m -Xms1024m -Dyona.data=$YONA_DATA -DapplyEvolutions.default=true -Dhttp.port=$PORT" $act dist
