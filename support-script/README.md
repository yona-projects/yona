# support-script

legacy Yona의 `support-script/`를 yona(Spring Boot 단일 jar) 기준으로 옮긴 운영 지원 자료.

## mariadb/my.cnf

MariaDB 튜닝 샘플. utf8mb4 관련 설정은 legacy와 동일하게 유지했고,
`innodb_file_format`/`innodb_large_prefix`는 뺐다 — MariaDB 10.2부터 deprecated,
10.6부터는 서버가 인식하지 못해 오히려 기동을 실패시킨다(yona의 `docker-compose.yml`이
쓰는 `mariadb:10.11` 이미지 기준). 배경은 [`docs/guide/troubleshooting.md`](../docs/guide/troubleshooting.md)의
"MariaDB 767 byte 에러" 항목 참고.

## systemd/yona.service

legacy의 `init.d/yona.sh`(SysV init, nohup으로 백그라운드 실행)를 대체하는 systemd 유닛 예시.
init.d 스크립트를 그대로 옮기지 않은 이유:

- yona는 `bin/yona` 같은 자체 실행 스크립트가 없다 — `java -jar <jar>` 하나로 뜨는 Spring Boot
  애플리케이션이라 nohup으로 감싸는 방식보다 systemd가 재시작 정책·로그 통합(`journalctl`) 면에서
  더 자연스럽다.
- init.d(SysV init)는 대부분의 최신 배포판에서 systemd로 대체된 지 오래다.

바로 쓰려면 실행 계정/경로(`User`, `WorkingDirectory`, jar 경로)를 환경에 맞게 고치고
`/etc/systemd/system/yona.service`로 복사한 다음:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now yona
```

컨테이너로 운영한다면 이 유닛 대신 저장소 루트의 `docker-compose.yml`(로컬 개발용 DB 예시) 구성을
참고해 자체 배포 매니페스트를 구성하는 쪽을 권장한다.
