# 백업 및 복구

legacy Yona의 `docs/ko/yona-backup-restore.md`를 yona 기준으로 갱신.

## 애플리케이션 데이터 백업/복원 (전체 DB 테이블)

legacy에는 없던 기능 — yona는 사이트매니저(관리자) 전용 API로 DB의 모든 테이블을 JSON으로
export/import할 수 있다(`SiteApiController`, `DataBackupService`, 테이블 자동 탐지 방식이라
특정 테이블만 누락되는 문제가 없다).

```bash
# 백업 다운로드 (관리자 인증 필요)
curl -u <admin-loginId>:<password> http://127.0.0.1:8080/site/export -o yona-backup.json

# 복원 — 전체 테이블을 통째로 교체하는 "완전 복원"이므로 운영 데이터에 바로 쓰지 말고
# 반드시 테스트 환경에서 먼저 검증할 것
curl -u <admin-loginId>:<password> -F "data=@yona-backup.json" http://127.0.0.1:8080/site/import
```

## DB 자체 백업

애플리케이션 레이어의 export/import와는 별개로, DB 엔진 자체 백업 도구를 쓰는 것이 더 안전하고
표준적이다(대용량/운영 환경 기준).

- MariaDB/MySQL: `mariadb-dump`(`mysqldump`)
- PostgreSQL: `pg_dump`/`pg_dumpall`
- SQL Server, CUBRID: 각 벤더 도구의 백업 절차를 따른다

## 물리 저장소 백업

legacy는 `YONA_DATA` 디렉터리 하나(conf/uploads/repo/logs)를 통째로 압축해두면 됐지만,
yona는 저장 위치가 설정 키별로 분리되어 있다. 백업 대상은 아래 4곳이다
(각 설정 키의 기본값·용도는 [README의 "운영 환경 설정"](../../README.md#운영-환경-설정-특히-windows) 참고):

- `yona.git.base-dir` — Git bare 저장소
- `yona.svn.base-dir` — SVN 저장소
- `yona.lfs.base-dir` — Git LFS 객체
- `yona.upload.base-dir` — 첨부파일 업로드

`application.yml`에 별도 재정의가 없다면 기본값이 `/tmp/yona/...`이므로, 운영 환경에서는
먼저 이 경로들을 영구 보존 디렉터리로 재설정한 다음 그 디렉터리들을 정기적으로 백업해야 한다.
설정(`application.yml` 내용 자체)은 소스/배포 산출물에 포함되므로 별도 백업 대상은 아니지만,
프로덕션 전용으로 오버라이드한 값(DB 비밀번호, OAuth2 client secret 등)이 있다면 그 값도
같이 백업 대상에 포함해야 한다.
