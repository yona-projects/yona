# 트러블슈팅

legacy Yona의 `docs/ko/trouble-shootings.md` + `docs/ko/db-error-767.md`를 yona 기준으로 갱신.
legacy 항목 중 yona에 그대로 적용되지 않는 것(배치파일 명령줄 길이 제한, Play evolutions 경고
등)은 빼고, yona에서 실제로 발생할 수 있는 항목으로 다시 정리했다.

## MariaDB 767 byte 에러

legacy에서는 흔한 문제였다 — Barracuda/동적 row format이 기본이 아니던 시절의 MariaDB에서
utf8mb4 인덱스가 767바이트 키 길이 제한에 걸려 아래처럼 실패했다.

```
[error] play - Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000]
```

**yona가 기본으로 쓰는 `mariadb:10.11`(docker-compose.yml)에서는 이 문제가 재현되지 않는다** —
MariaDB 10.2부터 Barracuda/동적 row format이 기본값이 되면서 사실상 해소된 문제이기 때문이다.
그래서 [`support-script/mariadb/my.cnf`](../../support-script/mariadb/my.cnf)에서도 legacy가
쓰던 `innodb_file_format=barracuda` / `innodb_large_prefix=on` 두 옵션을 뺐다 — 오히려 MariaDB
10.6부터는 서버가 이 두 변수를 인식하지 못해 **설정하면 기동이 실패한다.**

혹시 오래된(10.1 이전) MariaDB를 직접 운영 중이라 이 에러를 만났다면, my.cnf에
`innodb_file_format=barracuda`와 `innodb_large_prefix=on`을 추가하고 재시작 후
`SHOW VARIABLES LIKE 'innodb_lar%'` / `SHOW VARIABLES LIKE 'innodb_file%'`로 barracuda가
켜졌는지 확인한다. 다만 근본 해결은 MariaDB 자체를 10.2 이상으로 올리는 것을 권장한다.

## SQL Server / CUBRID에서 한글이 깨지거나 조회가 안 되는 경우

이식 과정에서 실제로 발견·해결된 DB별 결함이다(`docs/parity/index.md` P1-6x대 참고):

- **SQL Server**: JDBC URL에 `sendStringParametersAsUnicode=true`가 없으면 문자열 파라미터가
  비유니코드로 전송돼 한글이 깨진다. Hibernate가 String을 기본적으로 `varchar`(비유니코드)로
  매핑하는 문제도 있어 `use_nationalized_character_data: true`로 `nvarchar` 매핑을 강제해야
  한다 — 둘 다 `application.yml`의 `mssql` 프로파일 블록에 이미 반영되어 있다.
- **CUBRID**: JDBC URL에 `charSet=utf-8`이 없으면 한글이 깨져서 저장된다 — `application.yml`의
  `cubrid` 프로파일 블록에 이미 반영되어 있다. 또한 CUBRID 브로커가 유휴 커넥션을 서버 쪽에서
  먼저 끊는 경우가 있어 HikariCP `connection-test-query: SELECT 1`을 강제해야 죽은 커넥션으로
  인한 NPE를 피할 수 있다(이것도 이미 반영됨).

직접 접속 문자열을 바꿔 쓸 계획이라면 위 옵션들을 빠뜨리지 않도록 주의한다.

## 첨부파일 업로드가 실패한다 (413 / MaxUploadSizeExceededException)

legacy는 `application.maxFileSize`(기본 2GB)로 단일 파일 업로드 크기를 제한했다. yona는
Spring Boot 표준 멀티파트 설정을 쓰는데, **`spring.servlet.multipart.max-file-size`를 명시적으로
올려주지 않으면 Spring Boot 기본값(1MB)이 그대로 적용**되어 legacy보다 훨씬 낮은 한도에서
업로드가 막힌다. 큰 파일(코드 첨부, 이미지 등)을 다루려면 아래처럼 명시적으로 늘려야 한다.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB
```

한도를 넘기면 `GlobalExceptionHandler`가 잡아서 안내 화면을 보여준다(legacy의
`error/requestTextEntityTooLarge.scala.html`에 대응).

## OAuth 로그인 시 client id/secret 오류

legacy의 `RuntimeException: Provider 'google' missing needed setting 'clientId'`에 대응하는
문제. yona에서는 `application.yml`의 `spring.security.oauth2.client.registration.<provider>.client-id`/
`client-secret`이 비어 있거나 개발용 기본값(`dummy-client-id`)인 채로 방치된 경우 발생한다 —
[social-login-settings.md](social-login-settings.md) 참고해서 실제 값으로 채운다.

## Windows에서 Fork(프로젝트 복제)가 실패한다

[README의 "Windows에서 Fork(하드링크 복제) 사용 시 전제 조건"](../../README.md#windows에서-fork하드링크-복제-사용-시-전제-조건)
참고 — NTFS·단일 드라이브 제약이다.
