#!/usr/bin/env python3
"""Grafana 대시보드와 알림 규칙을 만든다(X-10 C6). 지표 이름은 OTLP로 Prometheus에 실제로 들어온 이름이다(점이 밑줄로, 단위가 접미사로 바뀐다).

사용: python3 observability/tools/generate_grafana.py
출력: observability/grafana/dashboards/*.json, observability/grafana/provisioning/alerting/alert-rules.yaml
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "grafana")
PROM = {"type": "prometheus", "uid": "prometheus"}
LOKI = {"type": "loki", "uid": "loki"}
# 지표를 60초마다 내보내므로 짧은 창(rate 기본 $__rate_interval)에서는 점이 거의 나오지 않는다. 5분 창을 쓴다.
R = "5m"
J = 'job="yona"'


class Board:
    def __init__(self, uid, title, description):
        self.uid, self.title, self.description = uid, title, description
        self.panels = []
        self.y = 0
        self.x = 0
        self.next_id = 1

    def row(self, title):
        self.x = 0
        self.panels.append({"id": self._id(), "type": "row", "title": title, "collapsed": False, "gridPos": {"h": 1, "w": 24, "x": 0, "y": self.y}, "panels": []})
        self.y += 1

    def _id(self):
        i = self.next_id
        self.next_id += 1
        return i

    def _place(self, w, h):
        if self.x + w > 24:
            self.x = 0
            self.y += h
        pos = {"h": h, "w": w, "x": self.x, "y": self.y}
        self.x += w
        if self.x >= 24:
            self.x = 0
            self.y += h
        return pos

    def panel(self, kind, title, targets, unit="short", w=12, h=8, description="", ds=PROM, options=None, thresholds=None, mappings=None, min_=None, max_=None, stack=False):
        defaults = {"unit": unit}
        if min_ is not None:
            defaults["min"] = min_
        if max_ is not None:
            defaults["max"] = max_
        if thresholds:
            defaults["thresholds"] = {"mode": "absolute", "steps": thresholds}
        if mappings:
            defaults["mappings"] = mappings
        if stack:
            defaults["custom"] = {"stacking": {"mode": "normal"}, "fillOpacity": 30}
        panel = {
            "id": self._id(), "type": kind, "title": title, "description": description, "datasource": ds, "gridPos": self._place(w, h),
            "targets": [dict(t, refId=chr(65 + i), datasource=ds) for i, t in enumerate(targets)],
            "fieldConfig": {"defaults": defaults, "overrides": []}, "options": options or {},
        }
        self.panels.append(panel)

    def ts(self, title, targets, **kw):
        self.panel("timeseries", title, targets, **kw)

    def stat(self, title, targets, w=6, h=4, **kw):
        self.panel("stat", title, targets, w=w, h=h, **kw)

    def logs(self, title, expr, w=24, h=10):
        self.panel("logs", title, [{"expr": expr, "queryType": "range"}], w=w, h=h, ds=LOKI, options={"showTime": True, "wrapLogMessage": True, "enableLogDetails": True, "sortOrder": "Descending"})

    def render(self, variables=None):
        return {
            "uid": self.uid, "title": self.title, "description": self.description, "tags": ["yona"], "timezone": "browser", "schemaVersion": 39, "version": 1,
            "refresh": "30s", "time": {"from": "now-1h", "to": "now"}, "editable": True, "graphTooltip": 1,
            "templating": {"list": variables or []}, "annotations": {"list": []}, "panels": self.panels,
        }

    def write(self, **kw):
        path = os.path.join(ROOT, "dashboards", f"{self.uid}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.render(**kw), f, ensure_ascii=False, indent=2)
            f.write("\n")


def q(expr, legend=""):
    return {"expr": expr, "legendFormat": legend, "range": True}


def hist(metric, by="", extra=""):
    group = f"le, {by}" if by else "le"
    return f"histogram_quantile(0.95, sum by ({group}) (rate({metric}_bucket{{{J}{extra}}}[{R}])))"


GREEN_RED = [{"color": "green", "value": None}, {"color": "red", "value": 80}]
RED_GREEN = [{"color": "red", "value": None}, {"color": "orange", "value": 0.1}, {"color": "green", "value": 0.2}]

# ---------------------------------------------------------------- 시스템
b = Board("yona-system", "Yona / 시스템", "CPU, 메모리, 디스크, 스레드, DB 풀, HTTP. 디스크와 스레드 풀과 GC 압력이 가장 먼저 봐야 할 신호이다.")
b.row("상태")
b.stat("앱 상태 확인", [q('probe_success{job=~"integrations/blackbox/yona"}')], mappings=[{"type": "value", "options": {"0": {"text": "중단", "color": "red"}, "1": {"text": "정상", "color": "green"}}}], description="Alloy가 /-_-actuator/health를 15초마다 확인한 결과. 앱이 죽으면 OTLP 데이터가 끊길 뿐이라 이 지표가 앱 사망 감지의 근거이다.")
b.stat("가동 시간", [q(f'process_uptime_milliseconds{{{J}}} / 1000')], unit="s")
b.stat("데이터 디스크 여유", [q(f'yona_storage_disk_free_bytes{{{J}}} / yona_storage_disk_total_bytes{{{J}}}')], unit="percentunit", thresholds=RED_GREEN, description="데이터 디렉터리(저장소와 첨부)가 있는 파티션. 알림 규칙은 15% 미만에서 경고하고 readiness는 5% 미만에서 내려간다.")
b.stat("HTTP 5xx 비율", [q(f'sum(rate(http_server_requests_milliseconds_count{{{J},outcome="SERVER_ERROR"}}[{R}])) / sum(rate(http_server_requests_milliseconds_count{{{J}}}[{R}]))')], unit="percentunit")
b.row("CPU와 메모리")
b.ts("CPU 사용률", [q(f'process_cpu_usage{{{J}}}', "프로세스"), q(f'system_cpu_usage{{{J}}}', "시스템")], unit="percentunit", min_=0, max_=1)
b.ts("부하 평균(1분)", [q(f'system_load_average_1m{{{J}}}', "부하"), q(f'system_cpu_count{{{J}}}', "코어 수")])
b.ts("JVM 힙", [q(f'sum(jvm_memory_used_bytes{{{J},area="heap"}})', "사용"), q(f'sum(jvm_memory_committed_bytes{{{J},area="heap"}})', "커밋"), q(f'jvm_gc_max_data_size_bytes{{{J}}}', "최대")], unit="bytes", description="git pack과 diff 계산이 큰 저장소를 열 때 힙이 튄다.")
b.ts("GC 일시 정지", [q(f'sum(rate(jvm_gc_pause_milliseconds_sum{{{J}}}[{R}]))', "ms/초")], unit="ms", description="초당 GC로 멈춘 시간. 높으면 힙이 모자라다.")
b.row("스레드와 DB 풀")
b.ts("톰캣 스레드 풀", [q(f'tomcat_threads_busy{{{J}}}', "사용 중"), q(f'tomcat_threads_current{{{J}}}', "현재"), q(f'tomcat_threads_config_max{{{J}}}', "최대")], description="긴 git 요청이 스레드를 점유해 풀이 고갈되면 전체가 멈춘다.")
b.ts("DB 커넥션 풀", [q(f'hikaricp_connections_active{{{J}}}', "사용 중"), q(f'hikaricp_connections_pending{{{J}}}', "대기"), q(f'hikaricp_connections_max{{{J}}}', "최대")])
b.ts("열린 파일", [q(f'process_files_open{{{J}}}', "열림"), q(f'process_files_max{{{J}}}', "한도")])
b.ts("JVM 스레드", [q(f'jvm_threads_live{{{J}}}', "살아 있는 스레드")])
b.row("디스크")
b.ts("데이터 디렉터리 파티션", [q(f'yona_storage_disk_free_bytes{{{J}}}', "여유"), q(f'yona_storage_disk_total_bytes{{{J}}}', "전체")], unit="bytes")
b.ts("저장소 총 용량(켠 경우)", [q(f'yona_storage_repositories_bytes{{{J}}}', "{{vcs}}")], unit="bytes", description="yona.observability-storage-scan=true 일 때만 나온다(10분마다 계산).")
b.row("HTTP")
b.ts("요청 수(5분 합계, 결과별)", [q(f'sum by (outcome) (increase(http_server_requests_milliseconds_count{{{J}}}[{R}]))', "{{outcome}}")], stack=True, min_=0)
b.ts("응답 시간 p95", [q(hist("http_server_requests_milliseconds"), "p95")], unit="ms")
b.ts("라우트별 요청 수 상위(5분 합계)", [q(f'topk(10, sum by (uri) (increase(http_server_requests_milliseconds_count{{{J}}}[{R}])))', "{{uri}}")], min_=0, description="uri는 라우트 템플릿이다. 모르는 경로는 /** 하나로 모인다.")
b.write()

# ---------------------------------------------------------------- VCS
b = Board("yona-vcs", "Yona / 저장소(git, svn)", "git과 svn 서비스 요청의 시간, 전송량, 푸시, 훅, 저장소 작업. vcs 태그가 git, svn(앞으로 hg)이다.")
b.row("요청")
b.ts("요청 수(5분 합계, 서비스·결과별)", [q(f'sum by (vcs, service, result) (increase(yona_vcs_request_milliseconds_count{{{J}}}[{R}]))', "{{vcs}} {{service}} {{result}}")], stack=True, min_=0)
b.ts("응답 시간 p95(서비스별)", [q(hist("yona_vcs_request_milliseconds", "vcs, service"), "{{vcs}} {{service}}")], unit="ms", description="clone과 fetch와 push는 수 초에서 수 분이라 일반 HTTP 지표와 따로 본다.")
b.ts("진행 중인 요청", [q(f'sum by (vcs, service) (yona_vcs_request_active{{{J}}})', "{{vcs}} {{service}}")], description="톰캣 스레드 고갈의 선행 지표.")
b.ts("오류와 거부(5분 합계)", [q(f'sum by (vcs, result) (increase(yona_vcs_request_milliseconds_count{{{J},result!="ok"}}[{R}]))', "{{vcs}} {{result}}")], min_=0, description="denied는 인증과 권한, not_found는 없는 저장소, error는 서버 오류나 처리 중 IOException.")
b.row("전송")
b.ts("전송량(5분 합계)", [q(f'sum by (vcs, direction) (increase(yona_vcs_transfer_bytes_total{{{J}}}[{R}]))', "{{vcs}} {{direction}}")], unit="bytes", min_=0, description="in은 클라이언트가 보낸 것(푸시), out은 서버가 보낸 것(복제와 가져오기). svn은 in만 센다.")
b.row("푸시와 훅")
b.ts("푸시한 ref(5분 합계, 종류·결과별)", [q(f'sum by (kind, result, reason) (increase(yona_vcs_pushes_total{{{J}}}[{R}]))', "{{kind}} {{result}} {{reason}}")], min_=0, description="거부 이유: ruleset_deletion, ruleset_update, ruleset_force, ruleset_pull_request, reserved.")
b.ts("푸시 뒤 훅 평균 시간", [q(f'sum by (hook) (rate(yona_vcs_hook_duration_milliseconds_sum{{{J}}}[{R}])) / sum by (hook) (rate(yona_vcs_hook_duration_milliseconds_count{{{J}}}[{R}]))', "{{hook}}")], unit="ms")
b.ts("훅 실패", [q(f'sum by (hook) (increase(yona_vcs_hook_duration_milliseconds_count{{{J},result="failure"}}[{R}]))', "{{hook}}")])
b.row("저장소 작업")
b.ts("작업 시간 p95", [q(hist("yona_vcs_operation_duration_milliseconds", "vcs, op"), "{{vcs}} {{op}}")], unit="ms", description="코드 보기, 이력, diff, 브랜치, 태그. 큰 파일의 diff는 CPU를 많이 쓴다.")
b.ts("작업 수(5분 합계)", [q(f'sum by (vcs, op, result) (increase(yona_vcs_operation_duration_milliseconds_count{{{J}}}[{R}]))', "{{vcs}} {{op}} {{result}}")], min_=0)
b.write()

# ---------------------------------------------------------------- 협업
b = Board("yona-collaboration", "Yona / 협업과 외부 연동", "생성 수, 인증과 접근 거부, 병합, 메일, 웹훅, LDAP, 검색, 캐시.")
b.row("협업")
b.ts("객체 생성(선택 기간 합계)", [q(f'sum by (entity) (increase(yona_entities_created_total{{{J}}}[$__range]))', "{{entity}}")], description="커밋된 삽입만 센다(화면, API, 가져오기 모두).")
b.ts("풀 리퀘스트 병합 결과", [q(f'sum by (result) (increase(yona_pull_requests_merge_total{{{J}}}[{R}]))', "{{result}}")])
b.row("인증과 접근 거부")
b.ts("보안 이벤트(5분 합계)", [q(f'sum by (event) (increase(yona_security_events_total{{{J}}}[{R}]))', "{{event}}")], min_=0, description="로그인 실패, 토큰과 Basic 실패, 차단, 접근 거부의 급증은 공격 신호이다. 감사 기록은 같은 주소의 차단을 1분에 한 번만 남기지만 지표는 매번 센다.")
b.ts("로그인과 인증 실패(5분 합계)", [q(f'sum by (event, channel) (increase(yona_security_events_total{{{J},event=~"login_failure|token_failure|basic_failure|blocked"}}[{R}]))', "{{event}} {{channel}}")], min_=0)
b.row("메일과 웹훅")
b.ts("알림 메일 발송", [q(f'sum by (result) (increase(yona_mail_sent_total{{{J}}}[{R}]))', "{{result}}")])
b.ts("알림 메일 대기열", [q(f'yona_notification_mail_queue{{{J}}}', "대기")], description="쌓이면 메일 스케줄러가 막혔거나 메일 서버가 느리다.")
b.ts("웹훅 발송", [q(f'sum by (result) (increase(yona_webhook_sent_total{{{J}}}[{R}]))', "{{result}}")])
b.row("LDAP과 검색")
b.ts("LDAP 인증 시간 p95", [q(hist("yona_ldap_authenticate_milliseconds"), "p95")], unit="ms", description="LDAP이 느리면 로그인이 멈춘다.")
b.ts("LDAP 결과", [q(f'sum by (result) (increase(yona_ldap_authenticate_milliseconds_count{{{J}}}[{R}]))', "{{result}}")])
b.ts("검색 시간 p95", [q(hist("yona_search_duration_milliseconds", "op"), "{{op}}")], unit="ms")
b.row("캐시")
b.ts("캐시 적중률", [q(f'sum by (cache) (rate(cache_gets_total{{{J},result="hit"}}[{R}])) / sum by (cache) (rate(cache_gets_total{{{J}}}[{R}]))', "{{cache}}")], unit="percentunit", min_=0, max_=1, description="떨어지면 마크다운 렌더링과 권한 계산 비용이 는다.")
b.ts("캐시 크기", [q(f'cache_size{{{J}}}', "{{cache}}")])
b.write()

# ---------------------------------------------------------------- 백그라운드
b = Board("yona-background", "Yona / 백그라운드 작업", "스케줄 작업의 마지막 성공 시각이 가장 중요하다. 조용히 멈춘 작업을 잡는다.")
b.row("스케줄 작업")
b.panel("table", "마지막 성공 이후 경과(초)", [{"expr": f'time() - yona_job_last_success_seconds{{{J}}} and yona_job_last_success_seconds{{{J}}} > 0', "instant": True, "format": "table", "legendFormat": ""}], unit="s", w=12, description="성공한 적이 없으면(값 0) 나타나지 않는다. 작업마다 주기가 다르므로 알림 규칙의 임계값을 작업별로 둔다.", options={"sortBy": [{"displayName": "Value", "desc": True}]})
b.ts("작업 실행 시간 p95", [q(hist("yona_job_duration_milliseconds", "task"), "{{task}}")], unit="ms")
b.ts("작업 실패", [q(f'sum by (task) (increase(yona_job_duration_milliseconds_count{{{J},result="failure"}}[{R}]))', "{{task}}")])
b.ts("실행 중인 작업", [q(f'sum by (task) (yona_job_active{{{J}}})', "{{task}}")])
b.row("푸시 뒤 작업과 병합")
b.ts("백그라운드 일 실행 중", [q(f'yona_background_active{{{J}}}', "실행 중")], description="스레드 풀에 상한이 없어 이 값이 쌓이면 서버가 밀린다.")
b.ts("백그라운드 일 시간 p95", [q(hist("yona_background_duration_milliseconds", "result"), "{{result}}")], unit="ms")
b.row("감사 로그 스트리밍")
b.ts("밀린 기록 수", [q(f'yona_audit_stream_backlog{{{J}}}', "{{target}}")], description="늘기만 하면 대상(웹훅, syslog)이 막혔다. 막힌 동안 보존 처리가 기록을 지우지 않아 DB가 커진다.")
b.ts("연속 실패", [q(f'yona_audit_stream_failures{{{J}}}', "{{target}}")])
b.ts("마지막 성공 이후 경과(초)", [q(f'time() - yona_audit_stream_last_success_seconds{{{J}}} and yona_audit_stream_last_success_seconds{{{J}}} > 0', "{{target}}")], unit="s")
b.write()

# ---------------------------------------------------------------- 로그
b = Board("yona-logs", "Yona / 로그와 추적", "WARN 이상의 애플리케이션 로그(Loki). request_id로 감사 기록과 요청의 로그를 잇는다. 로그 줄의 trace_id를 눌러 Tempo의 추적으로 넘어간다.")
variables = [{"name": "request_id", "type": "textbox", "label": "요청 번호(request_id)", "query": "", "current": {"text": "", "value": ""}, "hide": 0}]
b.row("로그")
b.panel("timeseries", "수준별 로그 수", [{"expr": 'sum by (detected_level) (count_over_time({service_name="yona"}[$__auto]))', "queryType": "range"}], w=24, h=6, ds=LOKI, stack=True)
b.logs("WARN 이상 로그", '{service_name="yona"} | detected_level=~"warn|error"')
b.logs("요청 번호로 찾기(위 변수에 감사 기록의 request_id를 넣는다)", '{service_name="yona"} | request_id=~"$request_id"')
b.write(variables=variables)

# ---------------------------------------------------------------- 알림 규칙


def rule(uid, title, expr, op, threshold, for_, severity, summary, no_data="OK", window=600):
    return {
        "uid": uid, "title": title, "condition": "C", "for": for_, "noDataState": no_data, "execErrState": "Error",
        "annotations": {"summary": summary}, "labels": {"severity": severity, "app": "yona"},
        "data": [
            {"refId": "A", "relativeTimeRange": {"from": window, "to": 0}, "datasourceUid": "prometheus", "model": {"expr": expr, "instant": True, "refId": "A", "intervalMs": 1000, "maxDataPoints": 43200}},
            {"refId": "B", "datasourceUid": "__expr__", "model": {"type": "reduce", "expression": "A", "reducer": "last", "refId": "B", "settings": {"mode": "dropNN"}, "datasource": {"type": "__expr__", "uid": "__expr__"}}},
            {"refId": "C", "datasourceUid": "__expr__", "model": {"type": "threshold", "expression": "B", "refId": "C", "conditions": [{"evaluator": {"type": op, "params": [threshold]}}], "datasource": {"type": "__expr__", "uid": "__expr__"}}},
        ],
    }


RULES = [
    rule("yona-app-down", "Yona 앱 상태 확인 실패", 'probe_success{job=~"integrations/blackbox/yona"}', "lt", 1, "2m", "critical", "Alloy의 상태 확인이 2분 넘게 실패했다. 앱이 죽었거나 readiness(DB, 데이터 디렉터리)가 내려갔다."),
    rule("yona-no-data", "Yona 지표가 들어오지 않음", f'absent_over_time(process_uptime_milliseconds{{{J}}}[5m])', "gt", 0, "5m", "critical", "5분 동안 지표가 없다. 앱이 죽었거나 수집기 연결이 끊겼다(OTLP는 푸시라 앱이 죽으면 데이터가 끊길 뿐이다).", window=600),
    rule("yona-disk-low", "데이터 디스크 여유 15% 미만", f'yona_storage_disk_free_bytes{{{J}}} / yona_storage_disk_total_bytes{{{J}}}', "lt", 0.15, "5m", "warning", "데이터 디렉터리 파티션 여유가 15% 미만이다. 5% 미만이면 readiness가 내려가 로드 밸런서가 서버를 뺀다."),
    rule("yona-heap-high", "JVM 힙 사용 90% 지속", f'sum(jvm_memory_used_bytes{{{J},area="heap"}}) / jvm_gc_max_data_size_bytes{{{J}}}', "gt", 0.9, "10m", "warning", "JVM 힙이 최대의 90%를 넘은 채 10분 지속됐다. 큰 저장소의 pack이나 diff, 메모리 설정을 확인한다."),
    rule("yona-gc-pressure", "GC 일시 정지 과다", f'sum(rate(jvm_gc_pause_milliseconds_sum{{{J}}}[5m]))', "gt", 100, "10m", "warning", "초당 GC 정지가 100ms를 넘는 상태가 10분 지속됐다."),
    rule("yona-threads-saturated", "톰캣 스레드 풀 80% 지속", f'tomcat_threads_busy{{{J}}} / tomcat_threads_config_max{{{J}}}', "gt", 0.8, "5m", "warning", "톰캣 스레드 풀 사용이 80%를 넘었다. 긴 git 요청이 스레드를 점유하는지 저장소 대시보드의 진행 중인 요청을 본다."),
    rule("yona-db-pool-pending", "DB 커넥션 대기", f'hikaricp_connections_pending{{{J}}}', "gt", 0, "5m", "warning", "DB 커넥션을 기다리는 요청이 5분 넘게 있다. 감사 로그 사슬 잠금이나 느린 쿼리를 확인한다."),
    rule("yona-5xx", "HTTP 5xx 비율 5% 초과", f'sum(rate(http_server_requests_milliseconds_count{{{J},outcome="SERVER_ERROR"}}[5m])) / sum(rate(http_server_requests_milliseconds_count{{{J}}}[5m]))', "gt", 0.05, "5m", "warning", "5xx 응답이 5%를 넘는 상태가 5분 지속됐다."),
    rule("yona-git-slow", "git 요청 p95 60초 초과", f'histogram_quantile(0.95, sum by (le) (rate(yona_vcs_request_milliseconds_bucket{{{J},vcs="git",service=~"upload_pack|receive_pack"}}[10m])))', "gt", 60000, "10m", "warning", "git clone, fetch, push의 p95가 60초를 넘는다.", window=900),
    rule("yona-mail-queue", "알림 메일 대기열 100건 초과", f'yona_notification_mail_queue{{{J}}}', "gt", 100, "15m", "warning", "알림 메일 대기열이 15분 넘게 100건을 넘었다. 메일 서버나 스케줄러를 확인한다."),
    rule("yona-audit-stream-backlog", "감사 로그 스트리밍 밀림", f'max(yona_audit_stream_backlog{{{J}}})', "gt", 1000, "10m", "warning", "감사 기록 1000건 넘게 외부로 보내지 못했다. 웹훅이나 syslog 대상을 확인한다(막힌 동안 DB가 커진다)."),
    rule("yona-audit-stream-failing", "감사 로그 스트리밍 연속 실패", f'max(yona_audit_stream_failures{{{J}}})', "gt", 5, "5m", "warning", "감사 로그 스트리밍이 5번 넘게 연속 실패했다."),
]
# 스케줄 작업이 조용히 멈춘 것: 작업마다 주기가 달라 임계값(초)을 따로 둔다
STALL = [
    ("notification_mail", 3600, "알림 메일 스케줄러"), ("audit_stream", 300, "감사 스트리밍"), ("audit_retention", 43200, "감사 보존"),
    ("notification_cleanup", 172800, "알림 정리"), ("temporary_files_cleanup", 172800, "임시 첨부 정리"),
]
for task, seconds, label in STALL:
    RULES.append(rule(
        f"yona-job-stalled-{task.replace('_', '-')}", f"{label} 작업이 멈춤", f'time() - yona_job_last_success_seconds{{{J},task="{task}"}} and yona_job_last_success_seconds{{{J},task="{task}"}} > 0',
        "gt", seconds, "5m", "warning", f"{label}({task})의 마지막 성공이 {seconds}초를 넘었다. 작업이 조용히 멈췄다.",
    ))


def yaml_dump(obj, indent=0):
    pad = "  " * indent
    out = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            if isinstance(v, (dict, list)) and v:
                out.append(f"{pad}{k}:")
                out.append(yaml_dump(v, indent + 1))
            else:
                out.append(f"{pad}{k}: {scalar(v)}")
    elif isinstance(obj, list):
        for item in obj:
            if isinstance(item, (dict, list)):
                lines = yaml_dump(item, indent + 1).split("\n")
                out.append(f"{pad}- {lines[0].lstrip()}")
                out.extend(lines[1:])
            else:
                out.append(f"{pad}- {scalar(item)}")
    return "\n".join(out)


def scalar(v):
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, (dict, list)):
        return json.dumps(v, ensure_ascii=False)
    return json.dumps(v, ensure_ascii=False)


doc = {"apiVersion": 1, "groups": [{"orgId": 1, "name": "yona", "folder": "Yona", "interval": "1m", "rules": RULES}]}
with open(os.path.join(ROOT, "provisioning", "alerting", "alert-rules.yaml"), "w", encoding="utf-8") as f:
    f.write("# Grafana 알림 규칙(X-10 C6). observability/tools/generate_grafana.py가 만든다. 임계값은 예시이며 환경에 맞게 바꾼다.\n")
    f.write(yaml_dump(doc) + "\n")
print("dashboards:", sorted(os.listdir(os.path.join(ROOT, "dashboards"))), "rules:", len(RULES))
