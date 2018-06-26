#
# User this restart shell just for example
#
PORT=9000
YONA_DATA=/Users/doortts/apps/activator-1.2.10-minimal/yona;export YONA_DATA
act=../activator

pid=`ps -ef | grep java | grep activator-launch | awk '{print $2}'`
kill $pid
JAVA_OPTS="-Xmx2048m -Xms1024m -Dyona.data=$YONA_DATA -DapplyEvolutions.default=true -Dhttp.port=$PORT" $act run
