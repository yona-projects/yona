#!/usr/bin/env python3
"""변칙 요청을 소켓으로 직접 보내(클라이언트 라이브러리가 고쳐 보내지 못하게) 두 서버의 응답을 견준다: 경로 변형(`//`, `/./`, `..`, 대소문자, 퍼센트 인코딩, 세미콜론 경로 인자, 잘못된 UTF-8, 아주 긴 경로),
질의 문자열 변형, 메서드 재정의 머리글, 본문 형식(JSON, 문자 집합, 청크, 잘못된 멀티파트), 쿠키 변형, 요청 줄 변형(절대 URI, HTTP/1.0).
사용: weird.py [로그인 행위자=anon]  결과는 같음/다름 건수와 다른 요청의 내용이다."""
import hashlib, re, socket, sys, time
import requests

BASES = {"v1.6": ("127.0.0.1", 19200), "v2": ("127.0.0.1", 19300)}


def raw(host_port, data, timeout=15):
    s = socket.create_connection(host_port, timeout=timeout)
    s.sendall(data)
    chunks = []
    s.settimeout(timeout)
    try:
        while True:
            c = s.recv(65536)
            if not c:
                break
            chunks.append(c)
            if b"\r\n\r\n" in b"".join(chunks) and b"Content-Length" not in b"".join(chunks)[:4000] and b"chunked" not in b"".join(chunks)[:4000].lower():
                time.sleep(0.2)
                s.settimeout(0.5)
    except socket.timeout:
        pass
    finally:
        s.close()
    return b"".join(chunks)


def parse(resp):
    head, _, body = resp.partition(b"\r\n\r\n")
    lines = head.decode("latin-1").split("\r\n")
    status = lines[0] if lines and lines[0] else "(응답 없음)"
    hdrs = {}
    for l in lines[1:]:
        if ":" in l:
            k, v = l.split(":", 1)
            hdrs.setdefault(k.strip().lower(), v.strip())
    return status, hdrs, body


SKIP = {"date", "server", "set-cookie", "content-length", "transfer-encoding", "connection", "keep-alive", "vary", "x-content-type-options", "x-xss-protection", "x-frame-options",
        "cache-control", "expires", "pragma", "referrer-policy", "strict-transport-security", "content-security-policy", "permissions-policy", "cross-origin-opener-policy", "last-modified", "etag", "accept-ranges", "content-language"}


def norm_body(b):
    t = b.decode("utf-8", "replace")
    t = re.sub(r"\d+\s*(시간|분|초|일)\s*전", "<시각>", t)
    t = re.sub(r"\s+", " ", t)
    return t


def summary(resp):
    status, hdrs, body = parse(resp)
    code = status.split(" ")[1] if status.count(" ") else status
    keep = {k: (re.sub(r"\s+", "", v.lower()) if k == "content-type" else v) for k, v in hdrs.items() if k not in SKIP}
    title = re.search(r"<title>([^<]*)", body.decode("utf-8", "replace"))
    return {"code": code, "hdr": keep, "title": title.group(1).strip() if title else None, "empty": len(body.strip()) == 0}


def login_cookie(base, user):
    if user == "anon":
        return ""
    s = requests.Session()
    s.post(f"http://{base[0]}:{base[1]}/users/login", data={"loginIdOrEmail": user, "password": "admin1234" if user == "admin" else "pass1234"}, allow_redirects=False)
    return "; ".join(f"{k}={v}" for k, v in s.cookies.items())


def req(method, target, host, headers=None, body=b"", version="HTTP/1.1", cookie=""):
    h = [f"{method} {target} {version}"]
    hd = {"Host": host, "Accept": "text/html", "Accept-Language": "en-US", "Connection": "close"}
    if cookie:
        hd["Cookie"] = cookie
    hd.update(headers or {})
    if body and "Content-Length" not in hd and "Transfer-Encoding" not in hd:
        hd["Content-Length"] = str(len(body))
    for k, v in hd.items():
        if v is not None:
            h.append(f"{k}: {v}")
    return ("\r\n".join(h) + "\r\n\r\n").encode("utf-8", "surrogateescape") + body


CASES = []


def case(label, method, target, **kw):
    CASES.append((label, method, target, kw))


# 경로 변형
for t in ["//tester/proj1", "/tester//proj1", "/tester/proj1//issues", "/Tester/Proj1", "/TESTER/proj1", "/tester/PROJ1/issues", "/tester/proj1/", "/tester/proj1/issues/", "/tester/./proj1",
          "/tester/../tester/proj1", "/./tester/proj1", "/tester/proj1/.", "/tester/proj1/..", "/tester/proj1%00", "/tester/proj1%20", "/tester/proj1%2e", "/%74ester/proj1", "/tester/proj1/%69ssues",
          "/tester/proj1/%2569ssues", "/tester/proj%FF", "/tester/proj1/issue/%31", "/tester/proj1/issue/1;x=1", "/tester/proj1;jsessionid=abc", "/tester;a=b/proj1", "/tester/proj1/issues;pageNum=2",
          "/tester/proj1?", "/tester/proj1#frag", "/tester/proj1/issues?state=open#x", "/" + "a" * 3000, "/tester/proj1/" + "b" * 9000, "/tester/proj1/issue/99999999999999999999", "/tester/proj1/issue/-1",
          "/tester/proj1/issue/1.5", "/tester/proj1/issue/%EF%BC%91", "/tester/proj1/issue/١", "/테스터/프로젝트", "/%ED%85%8C%EC%8A%A4%ED%84%B0", "/tester/한글프로젝트", "/tester/%ED%95%9C%EA%B8%80%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8",
          "/assets/../conf/application.conf", "/assets/%2e%2e/conf/application.conf", "/assets/javascripts/../../conf/application.conf", "/assets/stylesheets/yobi.css/", "/assets/stylesheets//yobi.css",
          "/favicon.ico/", "/favicon.ico?x=1", "/robots.txt", "/.git/config", "/WEB-INF/web.xml", "/META-INF/MANIFEST.MF", "/error", "/actuator", "/actuator/health", "/h2-console", "/swagger-ui.html", "/v3/api-docs",
          "/index.html", "/index.php", "/%", "/%zz", "/tester/proj1/%", "/\\tester\\proj1", "/tester\\proj1", "/tester/proj1\\issues"]:
    case("경로 " + t[:70], "GET", t)

# 질의 문자열 변형
for q in ["?a=%ZZ", "?a=%FF", "?pageNum=1&pageNum=2", "?pageNum[]=1", "?pageNum=1;pageNum=2", "?state=open&state=closed", "?" + "x=1&" * 500, "?keyword=" + "가" * 3000, "?keyword=%00", "?orderBy=;drop", "?=", "?&&&", "?a",
          "?pageNum=%EF%BC%91", "?pageNum=+1", "?pageNum=1%20", "?pageNum=0x10", "?pageNum=1e3", "?pageNum=-1", "?pageNum=99999999999999999999", "?state=OPEN", "?state=Open", "?milestoneId=abc", "?assigneeId=", "?authorId=0"]:
    case("질의 /tester/proj1/issues" + q[:50], "GET", "/tester/proj1/issues" + q)

# 요청 줄과 머리글 변형
case("절대 URI 요청 줄", "GET", "http://127.0.0.1:19200/tester/proj1")
case("HTTP/1.0 Host 없음", "GET", "/tester/proj1", version="HTTP/1.0", headers={"Host": None})
case("HTTP/1.0 Host 있음", "GET", "/tester/proj1", version="HTTP/1.0")
case("Host 다른 값", "GET", "/tester/proj1", headers={"Host": "yona.example.org:8443"})
case("Host 두 번", "GET", "/tester/proj1", headers={"Host": "a.example.org"})
case("X-Forwarded-Host", "GET", "/tester/proj1", headers={"X-Forwarded-Host": "proxy.example.org", "X-Forwarded-Proto": "https", "X-Forwarded-For": "10.1.1.1", "X-Forwarded-Port": "443"})
case("Forwarded", "GET", "/tester/proj1", headers={"Forwarded": "for=10.2.2.2;proto=https;host=fwd.example.org"})
case("X-Forwarded-Proto만", "GET", "/tester/proj1", headers={"X-Forwarded-Proto": "https"})
case("Accept 없음", "GET", "/tester/proj1", headers={"Accept": None})
case("Accept json", "GET", "/tester/proj1", headers={"Accept": "application/json"})
case("Accept */* issues", "GET", "/tester/proj1/issues", headers={"Accept": "*/*"})
case("Accept-Language 이상", "GET", "/", headers={"Accept-Language": "zz-ZZ;q=abc,,,ko"})
case("Accept-Language ko-KR", "GET", "/", headers={"Accept-Language": "ko-KR,ko;q=0.9,en;q=0.8"})
case("Accept-Encoding gzip", "GET", "/assets/stylesheets/yobi.css", headers={"Accept-Encoding": "gzip, deflate, br"})
case("Accept-Encoding gzip 홈", "GET", "/", headers={"Accept-Encoding": "gzip"})
case("Range 정적", "GET", "/assets/stylesheets/yobi.css", headers={"Range": "bytes=0-99"})
case("Range 두 구간", "GET", "/assets/stylesheets/yobi.css", headers={"Range": "bytes=0-9,20-29"})
case("If-None-Match *", "GET", "/assets/stylesheets/yobi.css", headers={"If-None-Match": "*"})
case("If-Modified-Since 먼 미래", "GET", "/assets/stylesheets/yobi.css", headers={"If-Modified-Since": "Wed, 01 Jan 2200 00:00:00 GMT"})
case("메서드 재정의 머리글", "GET", "/tester/proj1/issues", headers={"X-HTTP-Method-Override": "DELETE"})
case("메서드 재정의 POST", "POST", "/tester/proj1/delete", headers={"X-HTTP-Method-Override": "DELETE"})
case("_method 인자", "POST", "/tester/proj1/delete?_method=DELETE")
case("User-Agent 없음", "GET", "/", headers={"User-Agent": None})
case("Referer 이상", "GET", "/", headers={"Referer": "javascript:alert(1)"})
case("Origin 다른 곳 POST", "POST", "/users/login", headers={"Origin": "http://evil.example", "Content-Type": "application/x-www-form-urlencoded"}, body=b"loginIdOrEmail=tester&password=pass1234")
case("X-Requested-With 홈", "GET", "/", headers={"X-Requested-With": "XMLHttpRequest"})
case("X-Requested-With issues", "GET", "/tester/proj1/issues", headers={"X-Requested-With": "XMLHttpRequest"})
case("아주 긴 머리글", "GET", "/", headers={"X-Long": "v" * 9000})
case("머리글 아주 많음", "GET", "/", headers={f"X-H{i}": "v" for i in range(200)})
case("TRACE", "TRACE", "/")
case("CONNECT", "CONNECT", "127.0.0.1:19200")
case("PROPFIND 일반", "PROPFIND", "/tester/proj1")
case("MKCOL 일반", "MKCOL", "/tester/proj1")
case("빈 메서드 모양", "FOO", "/")
case("소문자 메서드", "get", "/")

# 본문 형식 변형(로그인 시도)
LOGIN = b"loginIdOrEmail=tester&password=pass1234"
case("로그인 json 본문", "POST", "/users/login", headers={"Content-Type": "application/json"}, body=b'{"loginIdOrEmail":"tester","password":"pass1234"}')
case("로그인 text/plain", "POST", "/users/login", headers={"Content-Type": "text/plain"}, body=LOGIN)
case("로그인 Content-Type 없음", "POST", "/users/login", body=LOGIN)
case("로그인 charset 8859-1", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded; charset=ISO-8859-1"}, body=LOGIN)
case("로그인 charset 이상", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded; charset=nope"}, body=LOGIN)
case("로그인 청크", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded", "Transfer-Encoding": "chunked"}, body=b"%x\r\n" % len(LOGIN) + LOGIN + b"\r\n0\r\n\r\n")
case("로그인 Expect 100", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded", "Expect": "100-continue"}, body=LOGIN)
case("로그인 멀티파트 경계 없음", "POST", "/users/login", headers={"Content-Type": "multipart/form-data"}, body=LOGIN)
case("로그인 멀티파트 잘림", "POST", "/users/login", headers={"Content-Type": "multipart/form-data; boundary=XX"}, body=b"--XX\r\nContent-Disposition: form-data; name=\"loginIdOrEmail\"\r\n\r\ntester")
case("로그인 멀티파트 정상", "POST", "/users/login", headers={"Content-Type": "multipart/form-data; boundary=XX"},
     body=b"--XX\r\nContent-Disposition: form-data; name=\"loginIdOrEmail\"\r\n\r\ntester\r\n--XX\r\nContent-Disposition: form-data; name=\"password\"\r\n\r\npass1234\r\n--XX--\r\n")
case("로그인 값 중복", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded"}, body=b"loginIdOrEmail=nobody&loginIdOrEmail=tester&password=pass1234")
case("로그인 질의 인자", "POST", "/users/login?loginIdOrEmail=tester&password=pass1234")
case("로그인 UTF-8 BOM", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded"}, body=b"\xef\xbb\xbf" + LOGIN)
case("로그인 잘못된 UTF-8", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded"}, body=b"loginIdOrEmail=%FF%FE&password=x")
case("로그인 큰 본문", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded"}, body=b"loginIdOrEmail=tester&password=" + b"p" * 3_000_000)
case("본문 길이 틀림(짧게)", "POST", "/users/login", headers={"Content-Type": "application/x-www-form-urlencoded", "Content-Length": "5"}, body=LOGIN)
case("GET 본문", "GET", "/", headers={"Content-Type": "application/x-www-form-urlencoded"}, body=b"a=1")
case("DELETE 본문", "DELETE", "/tester/proj1/delete", headers={"Content-Type": "application/json"}, body=b"{}")
case("마크다운 JSON", "POST", "/markdown/tester/proj1", headers={"Content-Type": "application/json"}, body=b'{"markdown":"**x**"}')
case("마크다운 text", "POST", "/markdown/tester/proj1", headers={"Content-Type": "text/plain"}, body="**굵게** 한글".encode())
case("마크다운 폼 인코딩", "POST", "/markdown/tester/proj1", headers={"Content-Type": "application/x-www-form-urlencoded"}, body="markdown=%EA%B5%B5%EA%B2%8C".encode())

# 쿠키 변형
case("쿠키 PLAY_SESSION 위조", "GET", "/user/editform", headers={"Cookie": "PLAY_SESSION=deadbeef-username=tester"})
case("쿠키 이상한 값", "GET", "/", headers={"Cookie": "a=b; ; =; c"})
case("쿠키 아주 큼", "GET", "/", headers={"Cookie": "big=" + "c" * 20000})
case("쿠키 JSESSIONID 위조", "GET", "/user/editform", headers={"Cookie": "JSESSIONID=ABCDEF"})
case("쿠키 yobi.token 위조", "GET", "/user/editform", headers={"Cookie": "yobi.token=1:forged"})
case("쿠키 PLAY_FLASH 위조", "GET", "/", headers={"Cookie": "PLAY_FLASH=warning=user.login.alert&success=<script>"})


def main():
    actor = sys.argv[1] if len(sys.argv) > 1 else "anon"
    same = diff = 0
    for label, method, target, kw in CASES:
        out = {}
        for name, hp in BASES.items():
            cookie = login_cookie(hp, actor)
            host = kw.get("headers", {}).get("Host", f"{hp[0]}:{hp[1]}") if kw.get("headers", {}).get("Host", "x") is not None else None
            headers = dict(kw.get("headers", {}))
            if "Host" in headers and headers["Host"] is not None and name == "v1.6" and headers["Host"].startswith("127"):
                pass
            data = req(method, target.replace("19200", str(hp[1])), host or f"{hp[0]}:{hp[1]}", {k: v for k, v in headers.items() if k != "Host"} | ({"Host": None} if host is None else {}),
                       kw.get("body", b""), kw.get("version", "HTTP/1.1"), cookie)
            try:
                out[name] = summary(raw(hp, data))
            except Exception as e:  # noqa: BLE001
                out[name] = {"error": repr(e)[:80]}
        if out["v1.6"] == out["v2"]:
            same += 1
        else:
            diff += 1
            print(f"- {label}\n    v1.6={out['v1.6']}\n    v2  ={out['v2']}")
    print(f"변칙 요청 대조 [{actor}]: 같음 {same}, 다름 {diff} (전체 {same + diff})")


if __name__ == "__main__":
    main()
