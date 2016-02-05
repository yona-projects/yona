#
# User this restart shell just for example
#
PORT=9000
YONA_HOME=/data/yona;export YONA_HOME
PLAY2_HOME=/home/doortts/apps/play2

pid=`fuser $PORT/tcp`
kill $pid
_JAVA_OPTIONS="-Xmx2048m -Xms1024m -Dyobi.home=$YONA_HOME -Dconfig.file=$YONA_HOME/conf/application.conf -Dlogger.file=$YONA_HOME/conf/application-logger.xml"  $PLAY2_HOME/activator "start -DapplyEvolutions.default=true -Dhttp.port=$PORT"
