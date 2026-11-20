#!/usr/bin/env bash
# Helm 차트 시험: helm lint, 값 조합별 helm template, 렌더링 결과의 규칙(복제본 1, 비루트, 읽기 전용 루트, 프로브, 비밀 값 파일, 볼륨 보존).
# 쿠버네티스 클러스터에 설치하는 시험은 하지 않는다(이 컴퓨터에 kind가 없다).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
CHART="$HERE/yona"
fail=0
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1: 기대 [$2] 실제 [$3]"; fail=1; fi; }
echo "== helm lint"
helm lint "$CHART" --set database.url=jdbc:postgresql://db:5432/yona --set database.existingSecret=x >/dev/null 2>&1 && echo "  ok   lint(외부 PostgreSQL)" || { echo "  FAIL lint"; fail=1; }
helm lint "$CHART" --set database.type=h2 >/dev/null 2>&1 && echo "  ok   lint(H2)" || { echo "  FAIL lint h2"; fail=1; }
echo "== 필수 값"
out="$(helm template t "$CHART" 2>&1)"; check "database.url이 없으면 오류" "yes" "$(echo "$out" | grep -q 'database.url is required' && echo yes || echo no)"
echo "== 렌더링 규칙"
render() { helm template yona "$CHART" "$@" 2>&1; }
pg="$(render --set database.url=jdbc:postgresql://db:5432/yona --set database.existingSecret=dbsec --set yona.hostname=git.example.com --set yona.scheme=https --set yona.behindHttpsProxy=true --set ssh.enabled=true --set ssh.publicPort=22 --set ingress.enabled=true)"
python3 - "$pg" <<'PY'
import sys, yaml
docs = [d for d in yaml.safe_load_all(sys.argv[1]) if d]
kinds = {d["kind"] for d in docs}
sts = next(d for d in docs if d["kind"] == "StatefulSet")
c = sts["spec"]["template"]["spec"]["containers"][0]
ps = sts["spec"]["template"]["spec"]
env = {e["name"]: e.get("value") for e in c["env"]}
fail = 0
def check(msg, ok):
    global fail
    print(("  ok   " if ok else "  FAIL ") + msg); fail |= (not ok)
check("StatefulSet, Service(웹, SSH), Ingress가 있다", {"StatefulSet", "Service", "Ingress"} <= kinds and sum(d["kind"] == "Service" for d in docs) == 2)
check("복제본은 1개이다", sts["spec"]["replicas"] == 1)
check("비루트이고 fsGroup을 맞춘다", ps["securityContext"]["runAsNonRoot"] and ps["securityContext"]["fsGroup"] == 10001 and ps["securityContext"]["runAsUser"] == 10001)
check("루트 파일 시스템은 읽기 전용이고 /tmp는 emptyDir이다", c["securityContext"]["readOnlyRootFilesystem"] and any(v["name"] == "tmp" and "emptyDir" in v for v in ps["volumes"]) and any(m["mountPath"] == "/tmp" for m in c["volumeMounts"]))
check("권한 상승을 막고 모든 capability를 뺀다", c["securityContext"]["allowPrivilegeEscalation"] is False and c["securityContext"]["capabilities"]["drop"] == ["ALL"])
check("시작, 생존, 준비 프로브가 상태 확인 주소를 쓴다", c["startupProbe"]["httpGet"]["path"] == "/-_-actuator/health" and c["livenessProbe"]["httpGet"]["path"].endswith("/liveness") and c["readinessProbe"]["httpGet"]["path"].endswith("/readiness"))
check("비밀번호는 Secret 파일을 configtree로 읽는다(환경 변수 아님)", env["SPRING_CONFIG_IMPORT"] == "optional:configtree:/run/secrets/" and "SPRING_DATASOURCE_PASSWORD" not in env
      and any(v["name"] == "db-secret" and v["secret"]["secretName"] == "dbsec" and v["secret"]["items"][0]["path"] == "spring.datasource.password" for v in ps["volumes"]))
check("데이터 볼륨 요청 템플릿이 있다(helm uninstall로 지워지지 않는다)", sts["spec"]["volumeClaimTemplates"][0]["metadata"]["name"] == "data" and sts["spec"]["volumeClaimTemplates"][0]["spec"]["resources"]["requests"]["storage"] == "10Gi")
check("HTTPS 프록시 뒤 운영 설정", env["SERVER_FORWARD_HEADERS_STRATEGY"] == "framework" and env["YONA_SCHEME"] == "https" and env["YONA_HOSTNAME"] == "git.example.com")
check("내장 SSH 설정(시스템 sshd 연동은 없다)", env["YONA_SSH_MINA_ENABLED"] == "true" and env["YONA_SSH_PUBLIC_PORT"] == "22" and not any("RELAY" in k for k in env))
check("이미지 태그는 appVersion이고 latest가 아니다", c["image"] == "ghcr.io/yona-projects/yona:2.0.0")
check("종료 유예 시간이 있다", ps["terminationGracePeriodSeconds"] == 60)
sys.exit(fail)
PY
fail=$((fail | $?))
echo "== H2와 기존 PVC와 차트가 만드는 Secret"
h2="$(render --set database.type=h2)"
check "H2는 Secret 볼륨과 configtree를 쓰지 않는다" "0" "$(echo "$h2" | grep -c 'configtree\|db-secret')"
check "H2 접속 주소는 데이터 폴더의 파일이다" "1" "$(echo "$h2" | grep -c 'jdbc:h2:file:/data/db')"
claim="$(render --set database.type=h2 --set persistence.existingClaim=mypvc)"
check "기존 PVC를 쓰면 volumeClaimTemplates를 만들지 않는다" "0" "$(echo "$claim" | grep -c volumeClaimTemplates)"
check "기존 PVC 이름을 쓴다" "1" "$(echo "$claim" | grep -c 'claimName: mypvc')"
sec="$(render --set database.url=jdbc:postgresql://db/yona --set database.password=pw1)"
check "비밀번호를 주면 차트가 Secret을 만든다" "1" "$(echo "$sec" | grep -c '^kind: Secret')"
nosec="$(render --set database.url=jdbc:postgresql://db/yona --set database.existingSecret=mine)"
check "기존 Secret을 쓰면 차트가 Secret을 만들지 않는다" "0" "$(echo "$nosec" | grep -c '^kind: Secret')"
echo "== kubectl 클라이언트 검증(서버 없이)"
if command -v kubectl >/dev/null 2>&1; then
  echo "$pg" | kubectl apply --dry-run=client --validate=false -f - >/dev/null 2>&1 && echo "  ok   kubectl apply --dry-run=client" || echo "  건너뜀: kubectl이 클러스터 없이 검증하지 못했다"
fi
[ "$fail" -eq 0 ] && echo "Helm 차트 시험 통과" || echo "Helm 차트 시험 실패"
exit "$fail"
