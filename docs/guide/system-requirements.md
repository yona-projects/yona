# 시스템 요구 사항

legacy Yona의 `docs/system-requirements.md`를 yona 기준으로 갱신.

## 서버

- JDK 21 (JDK 8이 아니다 — legacy Yona는 Java 8 전용이었지만 yona는 Java 21 툴체인으로 빌드된다.
  `build.gradle.kts`의 `java.toolchain.languageVersion`)
- 운영/테스트 DB 중 하나: MariaDB(기본), PostgreSQL, MySQL, SQL Server, CUBRID
  (자세한 프로파일 선택 방법은 [README](../../README.md#데이터베이스-선택))

## 클라이언트

- 최신 Chrome/Firefox/Safari/Edge 권장. legacy처럼 특정 구버전(IE10+, Chrome 30+ 등) 최소 버전을
  명시적으로 검증·명문화한 적은 아직 없다 — evergreen 브라우저 기준으로 만들어졌다는 뜻일 뿐,
  별도의 브라우저 호환성 매트릭스가 있는 건 아니다.
- Git v1.6.6 이상 (Git 저장소 기능을 쓰는 경우)
