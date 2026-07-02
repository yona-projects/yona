#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   PORT=19000 YONA_DATA=/data/yona ./nohup-yon-run.sh
#
# Optional overrides:
#   SBT=/path/to/sbt
#   JAVA_HOME=/path/to/jdk-21
#   YONA_HOME=/path/to/yona
#   JAVA_XMS=2048m JAVA_XMX=4096m

PORT="${PORT:-9000}"
YONA_HOME="${YONA_HOME:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)}"
YONA_DATA="${YONA_DATA:-/data/yona}"
SBT="${SBT:-sbt}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

STAGE_DIR="$YONA_HOME/target/universal/stage"
APP_BIN="$STAGE_DIR/bin/yona"
PID_FILE="$STAGE_DIR/RUNNING_PID"
LOG_DIR="${YONA_LOG_DIR:-$YONA_DATA/logs}"
OUT_LOG="$LOG_DIR/yona.out"
GC_LOG="$LOG_DIR/gc.log"

mkdir -p "$YONA_DATA" "$LOG_DIR"

if ! "$JAVA_BIN" -version 2>&1 | grep -q 'version "21'; then
  echo "WARNING: Yona is expected to run on OpenJDK 21. Current java is:" >&2
  "$JAVA_BIN" -version >&2
fi

cd "$YONA_HOME"

echo "Staging Yona with sbt..."
"$SBT" stage

stop_pid() {
  pid="$1"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    echo "Stopping existing Yona process $pid"
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 20); do
      if ! kill -0 "$pid" 2>/dev/null; then
        return
      fi
      sleep 1
    done
    echo "Force stopping Yona process $pid"
    kill -9 "$pid" 2>/dev/null || true
  fi
}

if [ -f "$PID_FILE" ]; then
  stop_pid "$(cat "$PID_FILE" 2>/dev/null || true)"
  rm -f "$PID_FILE"
fi

for pid in $(pgrep -f "Dhttp.port=$PORT.*target/universal/stage/bin/yona" || true); do
  if [ "$pid" != "$$" ]; then
    stop_pid "$pid"
  fi
done

export YONA_HOME
export YONA_DATA
export JAVA_OPTS="${JAVA_OPTS:-} \
-Xms${JAVA_XMS:-4096m} \
-Xmx${JAVA_XMX:-4096m} \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=$LOG_DIR \
-Xlog:gc*:file=$GC_LOG:time,uptime,level,tags:filecount=5,filesize=10m \
-Dhttp.port=$PORT \
-Dapplication.port=$PORT \
-Dyobi.home=$YONA_HOME \
-Dyona.data=$YONA_DATA \
-Dapplication.home=$YONA_DATA \
-Dplay.evolutions.db.default.autoApply=true"

echo "Starting Yona on port $PORT"
echo "YONA_HOME=$YONA_HOME"
echo "YONA_DATA=$YONA_DATA"
echo "Log file=$OUT_LOG"

nohup "$APP_BIN" >> "$OUT_LOG" 2>&1 &
echo "Yona started with pid $!"
