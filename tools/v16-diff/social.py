#!/usr/bin/env python3
"""소셜 로그인 전체 흐름을 두 서버에 같은 순서로 하고 관찰을 견준다. 가짜 제공자(`idp.py`)를 보게 띄운 서버(`social.sh`)에서 실행한다.
각 시나리오는 제공자의 신원을 정하고, 인증 시작 -> 제공자 -> 돌아온 주소 -> 이후 이동을 따라가며 상태, 이동 위치, 안내 문구, 쿠키 이름, 제공자가 받은 요청을 기록한다.
사용: social.py [시나리오 이름 ...]"""
import json, re, sys, urllib.parse
import requests

IDP = "http://127.0.0.1:19700"
IDP_LOG = "/home/jiho/yona-convert/yona-diff-work/idp.jsonl"
BASES = {"v1.6": "http://127.0.0.1:19200", "v2": "http://127.0.0.1:19300"}
NOTIFY = re.compile(r'\$yobi\.notify\("((?:[^"\\]|\\.)*)"')


def idp_set(**kw):
    requests.get(IDP + "/_set", params=kw)


def idp_log_size():
    try:
        return sum(1 for _ in open(IDP_LOG, encoding="utf-8"))
    except FileNotFoundError:
        return 0


def idp_entries_since(n):
    lines = open(IDP_LOG, encoding="utf-8").read().splitlines()[n:]
    out = []
    for l in lines:
        e = json.loads(l)
        if e["path"] == "/_set":
            continue
        q = {k: v for k, v in e["query"].items() if k not in ("state", "redirect_uri")}
        body = e["body"] if isinstance(e["body"], str) else {k: v for k, v in e["body"].items() if k not in ("code", "redirect_uri")}
        redirect = (e["query"].get("redirect_uri") or (e["body"].get("redirect_uri") if isinstance(e["body"], dict) else "")) or ""
        if e["path"] in ("/user", "/userinfo"):
            # 사용자 정보 요청의 토큰 전달 방식(v1.6: 질의 인자 access_token, v2: Authorization: Bearer)은 A-95가 정한 의도한 차이라 방식만 남긴다
            out.append({"m": e["method"], "p": e["path"], "token": bool(e["query"].get("access_token") or e["authorization"])})
            continue
        out.append({"m": e["method"], "p": e["path"], "q": q, "body": body, "accept": e["accept"], "auth": bool(e["authorization"]), "redirect_path": urllib.parse.urlsplit(redirect).path,
                    "state": bool(e["query"].get("state"))})
    return out


def norm_loc(loc, base):
    if not loc:
        return loc
    loc = loc.replace(base, "<서버>").replace(IDP, "<제공자>").replace(urllib.parse.quote(base, safe=""), "<서버>")
    loc = re.sub(r"state=[^&]+", "state=<s>", loc)
    loc = re.sub(r"code=code\d+", "code=<c>", loc)
    return loc


def facts(resp, base):
    text = resp.text if "text" in (resp.headers.get("Content-Type") or "") or "json" in (resp.headers.get("Content-Type") or "") else ""
    title = re.search(r"<title>([^<]*)", text)
    return {"status": resp.status_code, "loc": norm_loc(resp.headers.get("Location"), base), "notify": NOTIFY.findall(text),
            "title": title.group(1).strip() if title else None, "loggedin": (re.search(r'data-login="required"', text) is None) if text and "gnb-usermenu" in text else None,
            "cookies": sorted(c.split("=")[0].strip() for c in re.findall(r"(?:^|, )([A-Za-z_.]+=)", resp.headers.get("Set-Cookie", "")) if c.split("=")[0].strip() not in ("PLAY_SESSION", "PLAY_FLASH", "JSESSIONID", "Expires", "Path")) }


def follow(s, base, url, record, label, max_hops=6, **kw):
    for i in range(max_hops):
        r = s.get(url, allow_redirects=False, **kw)
        record.append({"step": f"{label}#{i}", **facts(r, base)})
        loc = r.headers.get("Location")
        if r.status_code in (301, 302, 303, 307) and loc:
            if loc.startswith(IDP):
                return r
            if not (loc.startswith("http") or loc.startswith("/")):
                return r  # v1.6의 logout Location 같은 이상한 값은 따라가지 않는다
            url = loc if loc.startswith("http") else base + loc
        else:
            return r
    return r


def whoami(s, base, record, label):
    r = s.get(base + "/user/editform", allow_redirects=False)
    rec = {"step": label, "status": r.status_code, "loc": norm_loc(r.headers.get("Location"), base)}
    if r.status_code == 200:
        for name in ("loginId", "name", "email"):
            m = re.search(r'name="%s"[^>]*value="([^"]*)"' % name, r.text) or re.search(r'value="([^"]*)"[^>]*name="%s"' % name, r.text)
            rec[name] = m.group(1) if m else None
    record.append(rec)


def login_admin(base):
    a = requests.Session()
    a.post(base + "/users/login", data={"loginIdOrEmail": "admin", "password": "admin1234"}, allow_redirects=False)
    return a


def users_list(base):
    a = login_admin(base)
    t = a.get(base + "/sites/userList?pageNum=1").text
    rows = re.findall(r'href="/([^"/]+)"[^>]*class="[^"]*username', t) or re.findall(r'data-login-id="([^"]+)"', t)
    return sorted(set(re.findall(r'"/(?:[^"/?]+)"[^>]*>\s*\(?@?([\w.-]+)\)?', t)))[:0], t


def user_rows(base):
    a = login_admin(base)
    t = a.get(base + "/sites/userList?pageNum=1&size=200").text
    names = re.findall(r'<a href="/([^"/?]+)" class="[^"]*">\s*([^<]*)<', t)
    return sorted({n for n, _ in names if n not in ("projects", "orgs", "sites", "users", "user", "assets", "search", "files", "migration", "notifications", "lostPassword", "restricted", "admin")})


def social_flow(provider, base, ident, session=None, record=None, label="", callback_extra=None, start_path=None, direct_callback=None):
    s = session or requests.Session()
    s.headers["Accept-Language"] = "en-US,en;q=0.9"
    record = record if record is not None else []
    idp_set(**ident)
    if direct_callback is not None:
        follow(s, base, base + direct_callback, record, label + ":직접 콜백")
        return s, record
    r = s.get(base + (start_path or f"/authenticate/{provider}"), allow_redirects=False)
    record.append({"step": label + ":시작", **facts(r, base)})
    loc = r.headers.get("Location")
    if not loc or not loc.startswith(IDP):
        return s, record
    ri = requests.get(loc, allow_redirects=False)
    cb = ri.headers.get("Location", "")
    record.append({"step": label + ":제공자", "status": ri.status_code, "loc": norm_loc(cb, base)})
    if cb.startswith(base):
        follow(s, base, cb, record, label + ":콜백")
    return s, record


SCEN = {}


def scen(fn):
    SCEN[fn.__name__] = fn
    return fn


def mk(**kw):
    return kw


@scen
def s01_new_user(provider, base):
    r = []
    s, _ = social_flow(provider, base, mk(id="1001", email="alice@example.com", name="Alice"), record=r, label="신규")
    whoami(s, base, r, "신규 뒤 내 정보")
    s2, _ = social_flow(provider, base, mk(id="1001", email="alice@example.com", name="Alice"), record=r, label="재로그인")
    whoami(s2, base, r, "재로그인 뒤 내 정보")
    r.append({"step": "로그아웃", **facts(s2.get(base + "/logout", allow_redirects=False), base)})
    whoami(s2, base, r, "로그아웃 뒤 내 정보")
    return r


@scen
def s02_link_existing_email(provider, base):
    r = []
    s, _ = social_flow(provider, base, mk(id="2002", email="other@example.com", name="Other Social"), record=r, label="기존 이메일")
    whoami(s, base, r, "연결 뒤 내 정보")
    return r


@scen
def s03_login_id_collision(provider, base):
    r = []
    for i, em in enumerate(["tester@elsewhere.org", "tester@third.org", "tester@fourth.org"]):
        s, _ = social_flow(provider, base, mk(id=f"30{i}", email=em, name=f"Dup{i}"), record=r, label=f"충돌{i}")
        whoami(s, base, r, f"충돌{i} 뒤 내 정보")
    return r


@scen
def s04_email_problems(provider, base):
    r = []
    for tag, ident in (("이메일 없음", mk(id="4004", email="__missing__", name="NoMail")), ("이메일 null", mk(id="4005", email="__null__", name="NullMail")),
                       ("이메일 문자열 null", mk(id="4006", email="null", name="StrNull")), ("이메일 빈 값", mk(id="4007", email="", name="EmptyMail")),
                       ("이메일 형식 이상", mk(id="4008", email="not-an-email", name="BadMail")), ("이름 없음", mk(id="4009", email="noname@example.com", name=""))):
        s, _ = social_flow(provider, base, ident, record=r, label=tag)
        whoami(s, base, r, f"{tag} 뒤 내 정보")
    return r


@scen
def s05_provider_failures(provider, base):
    r = []
    social_flow(provider, base, mk(deny="1"), record=r, label="거절")
    social_flow(provider, base, mk(token_error="1", id="5001", email="tok@example.com"), record=r, label="토큰 오류")
    social_flow(provider, base, mk(user_status="500", id="5002", email="u500@example.com"), record=r, label="사용자 정보 500")
    social_flow(provider, base, mk(user_status="200", user_body="garbage{", id="5003", email="gar@example.com"), record=r, label="사용자 정보 깨짐")
    social_flow(provider, base, mk(user_status="401", user_body="nope", id="5004", email="u401@example.com"), record=r, label="사용자 정보 401")
    return r


@scen
def s06_state_and_direct(provider, base):
    r = []
    s, _ = social_flow(provider, base, mk(id="6001", email="state@example.com", name="State", badstate="WRONG"), record=r, label="상태값 불일치")
    whoami(s, base, r, "상태값 불일치 뒤")
    s, _ = social_flow(provider, base, mk(id="6002", email="nostate@example.com", name="NoState", nostate="1"), record=r, label="상태값 없음")
    whoami(s, base, r, "상태값 없음 뒤")
    social_flow(provider, base, mk(), record=r, label="시작 없이", direct_callback=f"/authenticate/{provider}?code=bogus&state=x")
    social_flow(provider, base, mk(), record=r, label="코드 없이", direct_callback=f"/authenticate/{provider}?foo=bar")
    social_flow(provider, base, mk(), record=r, label="오류 인자", direct_callback=f"/authenticate/{provider}?error=server_error&error_description=x")
    social_flow(provider, base, mk(), record=r, label="거절 주소", direct_callback=f"/authenticate/{provider}/denied")
    social_flow(provider, base, mk(), record=r, label="없는 제공자", direct_callback="/authenticate/unknownprov")
    social_flow(provider, base, mk(), record=r, label="없는 제공자 거절", direct_callback="/authenticate/unknownprov/denied")
    return r


@scen
def s07_local_session_plus_social(provider, base):
    r = []
    s = requests.Session()
    s.post(base + "/users/login", data={"loginIdOrEmail": "third", "password": "pass1234"}, allow_redirects=False)
    s, _ = social_flow(provider, base, mk(id="7001", email="third-social@example.com", name="Third Social"), session=s, record=r, label="로컬 로그인 중 소셜")
    whoami(s, base, r, "로컬 로그인 중 소셜 뒤")
    s, _ = social_flow(provider, base, mk(id="7001", email="third-social@example.com", name="Third Social"), session=s, record=r, label="같은 신원 한 번 더")
    whoami(s, base, r, "한 번 더 뒤")
    r.append({"step": "로그아웃", **facts(s.get(base + "/logout", allow_redirects=False), base)})
    s2, _ = social_flow(provider, base, mk(id="7001", email="third-social@example.com", name="Third Social"), record=r, label="연결 뒤 새 세션")
    whoami(s2, base, r, "연결 뒤 새 세션 내 정보")
    return r


@scen
def s08_locked_user(provider, base):
    r = []
    a = login_admin(base)
    rr = a.post(base + "/sites/toggleAccountLock/third", allow_redirects=False)
    r.append({"step": "third 잠금", "status": rr.status_code})
    s, _ = social_flow(provider, base, mk(id="8001", email="third@example.com", name="Third"), record=r, label="잠긴 사용자")
    whoami(s, base, r, "잠긴 사용자 뒤")
    a.post(base + "/sites/toggleAccountLock/third", allow_redirects=False)
    return r


@scen
def s09_return_to_original(provider, base):
    r = []
    s = requests.Session()
    s.headers["Accept-Language"] = "en-US,en;q=0.9"
    follow(s, base, base + "/tester/proj1/issues?state=closed", r, "원래 주소 방문")
    follow(s, base, base + "/users/loginform?redirectUrl=/tester/proj1/issues", r, "로그인 화면")
    s, _ = social_flow(provider, base, mk(id="9001", email="back@example.com", name="Back"), session=s, record=r, label="돌아가기")
    return r


@scen
def s10_idp_request_shape(provider, base):
    """제공자가 받은 요청의 모양만 본다(시작, 토큰, 사용자 정보)."""
    r = []
    social_flow(provider, base, mk(id="1010", email="shape@example.com", name="Shape"), record=r, label="요청 모양")
    return r


@scen
def s11_name_change(provider, base):
    r = []
    s, _ = social_flow(provider, base, mk(id="1111", email="namer@example.com", name="Old Name"), record=r, label="처음")
    whoami(s, base, r, "처음 뒤")
    s, _ = social_flow(provider, base, mk(id="1111", email="namer@example.com", name="New Name"), record=r, label="이름 바뀜")
    whoami(s, base, r, "이름 바뀐 뒤")
    s, _ = social_flow(provider, base, mk(id="1111", email="renamed-mail@example.com", name="New Name"), record=r, label="이메일 바뀜")
    whoami(s, base, r, "이메일 바뀐 뒤")
    return r


def run_all(provider, names):
    out = {}
    for k in ("v1.6", "v2"):
        base = BASES[k]
        out[k] = {}
        for name, fn in SCEN.items():
            if names and name not in names:
                continue
            n0 = idp_log_size()
            try:
                rec = fn(provider, base)
            except Exception as e:  # noqa: BLE001
                rec = [{"step": "예외", "error": repr(e)[:200]}]
            out[k][name] = {"obs": rec, "idp": idp_entries_since(n0)}
    return out


def main():
    names = [a for a in sys.argv[1:] if not a.startswith("-")]
    providers = ["github", "google"]
    total = same = 0
    report = []
    for provider in providers:
        res = run_all(provider, names)
        for name in res["v1.6"]:
            for part in ("obs", "idp"):
                a, b = res["v1.6"][name][part], res["v2"][name][part]
                total += 1
                if a == b:
                    same += 1
                    continue
                report.append(f"### {provider} {name} [{part}]")
                n = max(len(a), len(b))
                shown = 0
                for i in range(n):
                    x = a[i] if i < len(a) else None
                    y = b[i] if i < len(b) else None
                    if x != y and shown < 8:
                        report.append(f"  {i}: v1.6={json.dumps(x, ensure_ascii=False)}\n     v2  ={json.dumps(y, ensure_ascii=False)}")
                        shown += 1
    print(f"소셜 로그인 대조: 같음 {same}, 다름 {total - same} (전체 {total})")
    print("\n".join(report))


if __name__ == "__main__":
    main()
