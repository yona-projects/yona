백업하기
===

DB 백업 및 이전 복구 방법
---
[DB 백업 및 이전 복구 방법](https://github.com/yona-projects/yona/wiki/DB-%EB%B0%B1%EC%97%85-%EB%B0%8F-%EC%9D%B4%EC%A0%84-%EB%B3%B5%EA%B5%AC-%EB%B0%A9%EB%B2%95)

참고: https://mariadb.com/kb/en/mariadb/backup-and-restore-overview

각종 설정 및 파일들 백업
---
YONA_DATA 환경변수로 지정된 디렉터리를 통째로 압축해서 보관합니다.


참고: YONA_DATA에 생성된 디렉터리 설명
```
- conf - 서버 설정파일들이 있는 곳
- uploads - 업로드된 첨부파일들이 있는 곳
- repo - 코드 저장소 파일들
- logs - 로그 파일들
```


#### v1.3 이전

코드 저장소의 파일과 업로드 파일은 아래 디렉터리에 저장됩니다. 

    directory: repo, uploads

주기적으로 두 디렉터리는 따로 잘 백업해 주세요. 
