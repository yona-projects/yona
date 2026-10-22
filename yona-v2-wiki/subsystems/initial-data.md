---
title: 초기 데이터 적재
type: subsystem
sources: [conf/initial-data.yml, app/Global.java:70, app/Global.java:242-246, app/utils/YamlUtil.java:31-47]
updated: 2026-10-22
---

# 초기 데이터 적재

## v1.6 동작

`Global.insertInitialData()`는 사용자가 한 명도 없을 때에만 `conf/initial-data.yml`의 `users`, `roles`, `siteAdmins` 섹션을 저장한다(`app/Global.java:242-246`). `labels` 154건은 적재하지 않는다. `User.SITE_MANAGER_ID = 1`과 `Role` id 1~7은 코드가 상수로 참조한다.

## v2 구현

| 구성요소 | 역할 |
|---|---|
| `dev.yona.bootstrap.InitialDataLoader` | 사용자가 없으면 관리자(`admin`, id 1), 사이트 관리자 등록, 역할 7종(id 1~7)을 JPA로 저장한다. 역할 id가 기대와 다르면 예외를 던진다. |
| `dev.yona.bootstrap.InitialDataRunner` | 기동 직후 `InitialDataLoader.load()`를 호출한다. |
| `dev.yona.bootstrap.InitialDataLoaderTest` | 역할 id와 이름, 관리자 계정 값, 재적재 방지를 검증한다. |

관리자 비밀번호 해시(`5v4TVjzLo1bqullT1CU4/bENUNOUfX97WpdunGLvJvw=`)와 솔트 문자열(`[B@1032a4`)은 v1.6 값을 그대로 쓴다. 솔트는 바이트 배열의 `toString()` 흔적이지만 v1.6 계정과 호환되도록 유지한다.

## v1.6과 달라진 점과 이유

| 항목 | v1.6 | v2 | 이유 |
|---|---|---|---|
| 적재 방식 | SnakeYAML `!!models.X` 태그로 엔티티를 직접 생성 | Kotlin 코드로 엔티티 생성 | YAML 태그가 Ebean 모델 클래스에 결합되어 있다. 값은 동일하다. |
| 역할 id 보장 | Ebean이 빈 테이블에서 id를 부여 | 빈 테이블에 순서대로 저장하고 `check`로 확인 | 자동 증가 시퀀스를 어긋나게 하지 않으면서 id 1~7을 보장한다. |
| 실행기 분리 | 해당 없음 | 적재 로직과 `ApplicationRunner`를 별도 빈으로 분리 | 같은 클래스 안에서 호출하면 `@Transactional` 프록시를 우회한다. |
