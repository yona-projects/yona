#!/usr/bin/env python3
"""소셜 로그인 대조용 가짜 OAuth 제공자(GitHub, Google 흉내). 두 서버가 같은 제공자를 보며, 제공자가 받은 요청을 모두 JSON 줄로 남긴다.
사용: idp.py <포트> <기록 파일>
주소:
  GET  /authorize              인증 화면. 곧바로 redirect_uri?code=...&state=...로 보낸다(`/_mode`가 정한 응답을 따른다)
  POST /token                  코드를 토큰으로 바꾼다(JSON)
  GET  /user, /userinfo        사용자 정보(JSON). 토큰마다 정해진 신원을 돌려준다
  GET  /_set?...               다음 흐름이 쓸 신원과 동작을 정한다: id, email, name, token_error=1, user_status=500, user_body=..., deny=1, nostate=1
  GET  /_log                   기록을 비운다(?clear=1)"""
import json, sys, threading, time, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT, LOG = int(sys.argv[1]), sys.argv[2]
LOCK = threading.Lock()
SETTINGS = {"id": "1001", "email": "alice@example.com", "name": "Alice", "login": "alice"}
CODES, TOKENS = {}, {}
COUNTER = [0]


def log(entry):
    with LOCK, open(LOG, "a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _send(self, status, body=b"", ctype="application/json", headers=None):
        if isinstance(body, (dict, list)):
            body = json.dumps(body).encode()
        elif isinstance(body, str):
            body = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(n).decode("utf-8", "replace") if n else ""

    def _rec(self, body=""):
        u = urllib.parse.urlsplit(self.path)
        log({"t": time.time(), "method": self.command, "path": u.path, "query": dict(urllib.parse.parse_qsl(u.query)), "body": dict(urllib.parse.parse_qsl(body)) if body and "=" in body and not body.lstrip().startswith("{") else body,
             "accept": self.headers.get("Accept"), "authorization": self.headers.get("Authorization"), "ctype": self.headers.get("Content-Type"), "ua": self.headers.get("User-Agent")})

    def do_GET(self):
        u = urllib.parse.urlsplit(self.path)
        q = dict(urllib.parse.parse_qsl(u.query))
        if u.path == "/_set":
            SETTINGS.clear()
            SETTINGS.update(q)
            return self._send(200, {"ok": True})
        if u.path == "/_log":
            if q.get("clear"):
                open(LOG, "w").close()
            return self._send(200, {"ok": True})
        self._rec()
        if u.path == "/authorize":
            if SETTINGS.get("deny"):
                loc = q.get("redirect_uri", "") + ("&" if "?" in q.get("redirect_uri", "") else "?") + "error=access_denied" + ("&state=" + q["state"] if "state" in q else "")
                return self._send(302, b"", headers={"Location": loc})
            COUNTER[0] += 1
            code = f"code{COUNTER[0]}"
            CODES[code] = dict(SETTINGS, provider=("google" if "/authenticate/google" in urllib.parse.unquote(q.get("redirect_uri", "")) else "github"))
            loc = q.get("redirect_uri", "") + ("&" if "?" in q.get("redirect_uri", "") else "?") + "code=" + code
            if "state" in q and not SETTINGS.get("nostate"):
                loc += "&state=" + (SETTINGS.get("badstate") or q["state"])
            return self._send(302, b"", headers={"Location": loc})
        if u.path in ("/user", "/userinfo"):
            auth = (self.headers.get("Authorization") or "").replace("Bearer ", "").replace("token ", "")
            tok = q.get("access_token") or auth
            ident = TOKENS.get(tok)
            if ident is None:
                return self._send(401, {"message": "Bad credentials"})
            if ident.get("user_status"):
                return self._send(int(ident["user_status"]), ident.get("user_body", "oops"), ctype="text/plain")
            body = {"id": int(ident["id"]) if ident["id"].isdigit() else ident["id"], "name": ident.get("name"), "login": ident.get("login")}
            if ident.get("email") != "__missing__":
                body["email"] = None if ident.get("email") == "__null__" else ident.get("email")
            if u.path == "/userinfo":
                body["verified_email"] = True
            return self._send(200, body)
        self._send(404, {"message": "not found"})

    def do_POST(self):
        u = urllib.parse.urlsplit(self.path)
        body = self._body()
        self._rec(body)
        if u.path == "/token":
            f = dict(urllib.parse.parse_qsl(body))
            ident = CODES.get(f.get("code", ""))
            if ident is None or ident.get("token_error"):
                return self._send(200, {"error": "bad_verification_code", "error_description": "The code passed is incorrect or expired."})
            tok = "tok-" + f["code"]
            TOKENS[tok] = ident
            resp = {"access_token": tok, "token_type": "bearer", "scope": "user:email", "expires_in": 3600}
            if ident.get("provider") == "google":
                import base64
                def b64(d):
                    return base64.urlsafe_b64encode(json.dumps(d).encode()).decode().rstrip("=")
                claims = {"iss": "accounts.google.com", "sub": ident["id"], "aud": "id", "name": ident.get("name"), "exp": 4102444800, "iat": 1}
                if ident.get("email") not in ("__missing__", "__null__", None):
                    claims["email"] = ident["email"]
                    claims["email_verified"] = True
                resp["id_token"] = b64({"alg": "none"}) + "." + b64(claims) + "."
            return self._send(200, resp)
        self._send(404, {"message": "not found"})

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    open(LOG, "a").close()
    ThreadingHTTPServer(("127.0.0.1", PORT), H).serve_forever()
