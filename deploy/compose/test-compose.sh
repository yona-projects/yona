#!/usr/bin/env bash
# compose 파일의 구조를 검증한다(docker compose가 없어도 돈다): YAML 문법, latest 태그 금지, 비밀번호를 환경 변수로 두지 않기,
# 프로파일, 상태 확인과 의존 관계, 비밀 값 파일 연결. `docker compose config`를 쓸 수 있으면 그것도 돌린다.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
python3 - "$HERE" <<'PY'
import sys, re, yaml
d = sys.argv[1]
fail = 0
def check(msg, ok):
    global fail
    print(("  ok   " if ok else "  FAIL ") + msg); fail |= (not ok)
c = yaml.safe_load(open(f"{d}/docker-compose.yml"))
s = c["services"]
check("서비스 yona, postgres, mariadb가 있다", {"yona", "postgres", "mariadb"} <= set(s))
check("이미지에 latest 태그가 없다", all(":latest" not in str(v.get("image", "")) for v in s.values()))
check("yona 이미지는 버전을 필수로 받는다", "YONA_VERSION:?" in s["yona"]["image"])
check("DB 서비스는 프로파일로 고른다", s["postgres"]["profiles"] == ["postgres"] and s["mariadb"]["profiles"] == ["mariadb"])
check("모든 서비스가 restart: unless-stopped", all(v.get("restart") == "unless-stopped" for v in s.values()))
env_text = yaml.dump({k: v.get("environment", {}) for k, v in s.items()})
check("비밀번호를 환경 변수 값으로 쓰지 않는다(파일 경로만)", not re.search(r"PASSWORD: (?!/run/secrets)", env_text))
check("yona는 configtree로 비밀 값 파일을 읽는다", "configtree:/run/secrets/" in s["yona"]["environment"]["SPRING_CONFIG_IMPORT"])
check("비밀 값 파일 이름이 spring.datasource.password이다", s["yona"]["secrets"][0]["target"] == "spring.datasource.password")
check("DB가 건강해진 뒤에 시작한다(선택적 의존)", s["yona"]["depends_on"]["postgres"] == {"condition": "service_healthy", "required": False})
check("모든 서비스에 상태 확인이 있다", all("healthcheck" in v for v in s.values()))
check("yona 데이터와 DB 데이터가 이름 있는 볼륨이다", {"yona-data", "yona-pg-data", "yona-maria-data"} <= set(c["volumes"]))
check("MariaDB는 12.3.3이고 utf8mb4_bin이다", s["mariadb"]["image"] == "mariadb:12.3.3" and "--collation-server=utf8mb4_bin" in s["mariadb"]["command"])
check("비밀 값 파일은 secrets/ 아래이다", all(v["file"].startswith("./secrets/") for v in c["secrets"].values()))
o = yaml.safe_load(open(f"{d}/docker-compose.ssh.yml"))
check("SSH 덧붙임은 내장 서버만 켠다(시스템 sshd 연동 없음)", "relay" not in str(o).lower() and o["services"]["yona"]["environment"]["YONA_SSH_MINA_ENABLED"] == "true")
for n in ("postgres", "mariadb", "h2"):
    e = open(f"{d}/env/{n}.env").read()
    check(f"env/{n}.env에 YONA_VERSION과 YONA_DB_URL이 있다", "YONA_VERSION=" in e and "YONA_DB_URL=" in e)
sys.exit(fail)
PY
rc=$?
if docker compose version >/dev/null 2>&1; then
  ( cd "$HERE" && cp env/postgres.env .env && mkdir -p secrets && printf 'x\n' > secrets/db_password && printf 'y\n' > secrets/db_admin_password && docker compose config >/dev/null && echo "  ok   docker compose config" ; rm -f .env secrets/db_password secrets/db_admin_password ) || rc=1
else
  echo "  건너뜀: docker compose가 없어 'docker compose config'는 돌리지 않았다"
fi
exit "$rc"
