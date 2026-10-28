#!/usr/bin/env python3
"""LDAP 로그인을 두 서버에서 같은 순서로 해 보고 응답, 만들어진 사용자, LDAP 서버가 받은 바인드와 검색(필터)을 견준다. `ldap.sh`가 서버를 설정해 부른다.
사용: ldap.py [시나리오 ...]"""
import json, re, sys
import requests

BASES = {"v1.6": "http://127.0.0.1:19200", "v2": "http://127.0.0.1:19300"}
LDAP_LOG = "/home/jiho/yona-convert/yona-diff-work/ldap.log"
NOTIFY = re.compile(r'\$yobi\.notify\("((?:[^"\\]|\\.)*)"')


def log_size():
    try:
        return len(open(LDAP_LOG, encoding="utf-8").read().splitlines())
    except FileNotFoundError:
        return 0


def norm_line(l):
    l = re.sub(r"^\[[^\]]*\] ", "", l)
    l = re.sub(r"from=\"[^\"]*\"", "from=<포트>", l)
    l = re.sub(r"etime=[\d.]+", "etime=<t>", l)
    return l


def log_since(n):
    # 연결 종료 줄(UNBIND, DISCONNECT)은 클라이언트 쪽 정리 방식의 차이라 뺀다
    return [norm_line(l) for l in open(LDAP_LOG, encoding="utf-8").read().splitlines()[n:] if l.strip() and not l.split("] ", 1)[-1].startswith(("UNBIND", "DISCONNECT"))]


def facts(r, base):
    t = r.text if "html" in (r.headers.get("Content-Type") or "") else ""
    title = re.search(r"<title>([^<]*)", t)
    return {"status": r.status_code, "loc": (r.headers.get("Location") or "").replace(base, "<서버>"), "notify": NOTIFY.findall(t), "title": title.group(1).strip() if title else None,
            "cookies": sorted(c.split("=")[0] for c in r.raw.headers.getlist("Set-Cookie") if c.split("=")[0] not in ("PLAY_FLASH", "PLAY_SESSION", "JSESSIONID"))}


def login(base, ident, pw, extra=None):
    s = requests.Session()
    s.headers["Accept-Language"] = "en-US"
    r = s.post(base + "/users/login", data={"loginIdOrEmail": ident, "password": pw, **(extra or {})}, allow_redirects=False)
    rec = {"login": facts(r, base)}
    r2 = s.get(base + "/", allow_redirects=False)
    rec["home_logged_in"] = bool(re.search(r"class=\"user-item-btn\"[^>]*data-login=\"required\"", r2.text)) is False
    r3 = s.get(base + "/user/editform", allow_redirects=False)
    rec["edit"] = r3.status_code
    if r3.status_code == 200:
        for name in ("loginId", "name", "email"):
            m = re.search(r'name="%s"[^>]*value="([^"]*)"' % name, r3.text) or re.search(r'value="([^"]*)"[^>]*name="%s"' % name, r3.text)
            rec[name] = m.group(1) if m else None
        m = re.search(r'name="englishName"[^>]*value="([^"]*)"', r3.text)
        rec["englishName"] = m.group(1) if m else None
    return s, rec


def user_state(base):
    a = requests.Session()
    a.post(base + "/users/login", data={"loginIdOrEmail": "admin", "password": "admin1234"}, allow_redirects=False)
    t = a.get(base + "/sites/userList?size=500", allow_redirects=False).text
    rows = re.findall(r'<a href="/([^"/?]+)"[^>]*>', t)
    return sorted({r for r in rows if r.startswith(("ldap", "nomail", "g-", "mixed", "hangul", "emaillogin", "dupmail", "badmail", "blankname", "emptypw", "locked", "tester", "other"))})


SC = {}


def sc(f):
    SC[f.__name__] = f
    return f


@sc
def a_basic(base):
    out = {}
    for label, ident, pw in (("신규 사용자", "ldapalice", "alicepw"), ("같은 사용자 다시", "ldapalice", "alicepw"), ("틀린 비밀번호", "ldapalice", "wrong"), ("없는 사용자", "ghost", "x"),
                             ("빈 비밀번호", "ldapbob", ""), ("비밀번호 공백", "ldapbob", " "), ("영문 이름", "ldapbob", "bobpw"), ("메일 없는 사용자", "nomail", "nomailpw"), ("한글 CN", "hangul", "hangulpw"),
                             ("대소문자 다른 ID", "MIXEDCASE", "mixedpw"), ("이름 없는 사용자", "blankname", "blankpw"), ("비밀번호 없는 항목", "emptypw", ""), ("비밀번호 없는 항목 임의 값", "emptypw", "x"),
                             ("메일 형식 이상", "badmail", "badpw"), ("다른 OU", "otherou", "otherpw")):
        n0 = log_size()
        _, rec = login(base, ident, pw)
        rec["ldap"] = log_since(n0)
        out[label] = rec
    return out


@sc
def b_collisions(base):
    out = {}
    for label, ident, pw in (("로컬 사용자와 같은 ID", "tester", "ldaptesterpw"), ("로컬 비밀번호로", "tester", "pass1234"), ("같은 이메일", "dupmail", "duppw"), ("이메일로 로그인", "emaillogin@corp.example", "emailpw"),
                             ("이메일로 로그인 로컬", "other@example.com", "pass1234")):
        n0 = log_size()
        _, rec = login(base, ident, pw)
        rec["ldap"] = log_since(n0)
        out[label] = rec
    return out


@sc
def c_injection(base):
    out = {}
    for label, ident in (("별표", "*"), ("괄호", "a)(uid=*"), ("역슬래시", "a\\b"), ("널 문자", "a%00b"), ("쉼표", "a,b=c"), ("공백 앞뒤", " ldapbob "), ("아주 긴 ID", "x" * 500), ("유니코드", "ｌｄａｐｂｏｂ"), ("점", "a.b")):
        n0 = log_size()
        _, rec = login(base, ident, "bobpw")
        rec["ldap"] = log_since(n0)
        out[label] = rec
    return out


@sc
def d_state(base):
    return {"사용자": user_state(base)}


def run(names):
    res = {k: {} for k in BASES}
    for k, base in BASES.items():
        for n, f in SC.items():
            if names and n not in names:
                continue
            try:
                res[k][n] = f(base)
            except Exception as e:  # noqa: BLE001
                res[k][n] = {"예외": repr(e)[:200]}
    return res


def main():
    names = sys.argv[1:]
    res = run(names)
    same = diff = 0
    import os
    dump = os.environ.get("LDAP_DUMP")
    for n in res["v1.6"]:
        for label in res["v1.6"][n]:
            a, b = res["v1.6"][n][label], res["v2"][n].get(label)
            if dump and re.search(dump, label):
                print(f"[덤프] {n} / {label}\n    v1.6={json.dumps({k: v for k, v in a.items() if k != 'ldap'}, ensure_ascii=False)}\n    v2  ={json.dumps({k: v for k, v in b.items() if k != 'ldap'}, ensure_ascii=False)}")
            if a == b:
                same += 1
            else:
                diff += 1
                print(f"- {n} / {label}\n    v1.6={json.dumps(a, ensure_ascii=False)[:900]}\n    v2  ={json.dumps(b, ensure_ascii=False)[:900]}")
    print(f"LDAP 로그인 대조: 같음 {same}, 다름 {diff} (전체 {same + diff})")


if __name__ == "__main__":
    main()
