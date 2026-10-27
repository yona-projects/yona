#!/usr/bin/env python3
"""웹훅을 받아 JSON 줄로 쌓는 작은 HTTP 수신기이다.
  httpsink.py <포트> <출력 파일>
요청 하나마다 {"method", "path", "headers", "body"} 한 줄을 더하고 200을 돌려준다."""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def make_handler(out):
    class Handler(BaseHTTPRequestHandler):
        def _record(self):
            length = int(self.headers.get("Content-Length") or 0)
            body = self.rfile.read(length).decode("utf-8", errors="replace") if length else ""
            headers = {k: v for k, v in self.headers.items()}
            with open(out, "a", encoding="utf-8") as f:
                f.write(json.dumps({"method": self.command, "path": self.path, "headers": headers, "body": body}, ensure_ascii=False) + "\n")
            self.send_response(200)
            self.send_header("Content-Length", "2")
            self.end_headers()
            self.wfile.write(b"ok")

        do_POST = do_PUT = do_GET = _record

        def log_message(self, *args):
            pass

    return Handler


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), make_handler(sys.argv[2])).serve_forever()
