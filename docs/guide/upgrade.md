# 업그레이드

legacy Yona의 `docs/ko/yona-upgrade.md`를 yona 기준으로 갱신.

## 방법

- 서비스 중지 (`Ctrl-C` 또는 `sudo systemctl stop yona`)
- 새 소스로 갱신 (`git pull` 등) 후 재빌드
  ```bash
  ./gradlew bootJar
  ```
- 다시 실행 (`java -jar build/libs/yona-0.0.1-SNAPSHOT.jar ...` 또는
  `sudo systemctl start yona`)

DB 스키마는 [run-options.md의 "DB 스키마 마이그레이션"](run-options.md#db-스키마-마이그레이션)에
적힌 대로 `ddl-auto: update`가 기동 시 자동으로 반영한다 — legacy처럼 evolutions 관련 경고를
보고 별도 플래그를 켜야 하는 수동 단계가 없다.

## legacy에서 yona로 넘어오는 경우

이건 "버전 업그레이드"가 아니라 **아키텍처가 다른 별도 애플리케이션으로의 마이그레이션**이다
(Play/Java/Ebean → Spring Boot/Kotlin/JPA, DB 드라이버·스키마 매핑 방식도 다름). legacy Yona의
DB를 그대로 붙여서 기동하는 것은 검증된 경로가 아니다. 화면·데이터 모델·동작을 legacy와
동일하게 유지하는 것이 이 프로젝트의 목표이긴 하지만, 실제 마이그레이션(레거시 운영 DB →
yona) 절차 자체는 아직 별도로 문서화되어 있지 않다 — `docs/parity/index.md`의 이식 진행
상황을 참고하되, 실사용 데이터로 전환하기 전에는 반드시 백업 후 테스트 환경에서 먼저
검증해야 한다.
