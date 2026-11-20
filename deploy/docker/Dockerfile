# Yona v2 컨테이너 이미지(X-64): 서버만 담는다. 데이터베이스는 담지 않는다(compose나 Helm이 따로 띄우거나 외부 DB를 쓴다).
# 빌드: docker build -f deploy/docker/Dockerfile --build-arg JAR=<빌드 컨텍스트 안의 jar 경로> -t yona:<버전> <컨텍스트>
#   릴리스에 올라간 jar를 그대로 복사한다(릴리스 jar와 이미지의 내용을 같게 한다).
# 설정은 환경 변수로 준다(스프링의 규칙: 점은 밑줄로, 대문자). 예: YONA_DATA_DIR, SERVER_PORT, SPRING_DATASOURCE_URL.
FROM eclipse-temurin:21-jre

ARG JAR=yona.jar
ARG UID=10001

# 전용 비루트 계정과 데이터 폴더. 새 이름 있는 볼륨은 이미지 폴더의 소유권을 이어받으므로 /data를 계정 소유로 둔다.
RUN groupadd --gid "${UID}" yona \
 && useradd --uid "${UID}" --gid yona --home-dir /data --no-create-home --shell /usr/sbin/nologin yona \
 && mkdir -p /data /app \
 && chown yona:yona /data

COPY ${JAR} /app/yona.jar

# 힙은 컨테이너 메모리 한도의 비율로 정한다(고정 -Xmx는 한도가 바뀌면 어긋난다). 시간대는 TZ로 바꾼다.
ENV YONA_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    TZ=UTC

USER ${UID}:${UID}
WORKDIR /data
VOLUME ["/data"]
# 웹(8080)과 내장 SSH 서버(2222, 켰을 때만 열린다)
EXPOSE 8080 2222

# 첫 시작은 스키마를 만드느라 몇 분 걸릴 수 있어 시작 유예 시간을 넉넉히 둔다.
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/-_-actuator/health" | grep -q '"status":"UP"' || exit 1

# JVM이 직접 PID 1이다: SIGTERM에 정상 종료한다(종료 코드 143).
ENTRYPOINT ["java", "-jar", "/app/yona.jar"]
