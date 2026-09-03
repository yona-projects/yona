# 실행시 추가 가능한 옵션

legacy Yona의 `docs/ko/yona-run-options.md`를 yona 기준으로 갱신. legacy의 Windows 전용 절
(`bin/yona.bat`, `%TEMP%` 경로 이슈)은 대부분 해소됐다 — yona는 `java -jar` 하나로 OS 무관하게
실행되기 때문이다. Windows 고유의 남은 이슈(경로 설정, NTFS Fork 하드링크 제약)는
[README의 "운영 환경 설정 (특히 Windows)"](../../README.md#운영-환경-설정-특히-windows)에
정리되어 있다.

## 메모리 할당

`JAVA_OPTS`가 아니라 `java` 명령 자체에 JVM 옵션을 준다(legacy의 `bin/yona`는 내부적으로
`JAVA_OPTS`를 읽어 넘겼지만, yona는 그런 래퍼 스크립트가 없다).

```bash
java -Xmx2048m -Xms2048m -jar build/libs/yona-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

## 포트 변경

legacy는 `-Dhttp.port=80` 이었지만, Spring Boot에서는 `server.port`를 쓴다.

```bash
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar --server.port=80
# 또는
java -Dserver.port=80 -jar build/libs/yona-0.0.1-SNAPSHOT.jar
```

## DB 프로파일 선택

legacy에는 없던 개념 — yona는 하나의 jar로 5개 DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID)를
전부 지원하고, 어떤 DB에 붙을지는 `--spring.profiles.active=<프로파일>`로 고른다. 자세한 내용은
[README의 "데이터베이스 선택"](../../README.md#데이터베이스-선택) 참고.

## DB 스키마 마이그레이션

legacy는 Play의 evolutions 기능을 썼고, 업그레이드 후 최초 기동 시
`[warn] play - Your production database [default] needs evolutions!` 경고가 뜨면
`-DapplyEvolutions.default=true`를 줘야 했다. yona는 JPA/Hibernate의 `ddl-auto: update`
(각 DB 프로파일의 `application.yml` 블록에 이미 설정됨)로 애플리케이션 기동 시 스키마 변경분을
자동 반영한다 — legacy처럼 별도 플래그를 켜줘야 하는 수동 단계가 없다.

## 물리 저장소 경로 옵션

`--yona.git.base-dir=...` 등 4개 경로 설정(Git/SVN/LFS/업로드 저장 위치)은
[README의 "설정 변경 방법"](../../README.md#설정-변경-방법) 절에 정리되어 있다.
