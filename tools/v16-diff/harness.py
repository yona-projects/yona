#!/usr/bin/env python3
"""v1.6(공식 v1.16.0 릴리스)과 v2에 같은 요청을 보내 응답을 비교하는 도구이다.

사용법:
    harness.py run <시나리오 이름> --v16 http://127.0.0.1:19200 --v2 http://127.0.0.1:19300 [--out 보고서경로]

시나리오는 scenarios/<이름>.py 의 `steps()`가 돌려주는 `Step` 목록이다. 두 서버에 같은 순서로 같은 요청을 보내므로 만들어지는 번호(id)도 같다.
비교 항목은 상태 코드, 주요 응답 헤더(내용 형식, 이동 주소, 첨부 이름, 인증 요구, 쿠키 이름), 본문이다. 본문은 JSON이면 구조로,
HTML이면 정규화한 요소 목록으로, 그 밖에는 바이트로 비교한다. 날짜, 상대 시간, 세션과 같은 매번 달라지는 값은 지운다.
"""
import argparse
import difflib
import importlib.util
import json
import os
import re
import sys
import urllib.parse
from dataclasses import dataclass, field

import requests
from bs4 import BeautifulSoup, Comment

HERE = os.path.dirname(os.path.abspath(__file__))


@dataclass
class Step:
    """한 번의 요청이다. `actor`는 로그인 세션 이름(`anon`, `admin`, `tester` 등)이다."""
    actor: str
    method: str
    path: str
    data: dict | list | None = None          # 폼 본문(application/x-www-form-urlencoded)
    json_body: object | None = None           # JSON 본문
    files: dict | None = None                 # 멀티파트 파일 {필드: (이름, 바이트, 형식)}
    headers: dict = field(default_factory=dict)
    note: str = ""
    follow: bool = False                      # 이동을 따라갈지
    raw_body: bytes | None = None
    compare_body: bool = True                 # 본문이 달라도 상관없는 요청이면 끈다


class Server:
    def __init__(self, name: str, base: str):
        self.name, self.base = name, base.rstrip("/")
        self.sessions: dict[str, requests.Session] = {}

    def session(self, actor: str) -> requests.Session:
        if actor not in self.sessions:
            s = requests.Session()
            s.headers["Accept-Language"] = "en-US,en;q=0.9"
            self.sessions[actor] = s
        return self.sessions[actor]

    def send(self, step: Step) -> requests.Response:
        s = self.session(step.actor)
        url = self.base + step.path
        kwargs = dict(allow_redirects=step.follow, headers=dict(step.headers), timeout=120)
        if step.json_body is not None:
            kwargs["json"] = step.json_body
        elif step.files is not None:
            kwargs["files"] = step.files
            kwargs["data"] = step.data
        elif step.raw_body is not None:
            kwargs["data"] = step.raw_body
        elif step.data is not None:
            kwargs["data"] = step.data
        return s.request(step.method, url, **kwargs)


# --- 정규화 ----------------------------------------------------------------------------------

VOLATILE = [
    (re.compile(r"\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?(\.\d+)?(Z|[+-]\d{2}:?\d{2})?"), "<시각>"),
    (re.compile(r"\d{4}[./-]\d{1,2}[./-]\d{1,2}"), "<날짜>"),
    (re.compile(r"\b\d+\s+(second|minute|hour|day|week|month|year)s?\s+ago\b", re.I), "<상대시간>"),
    (re.compile(r"\b(a|an|\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago\b", re.I), "<상대시간>"),
    (re.compile(r"\b\d{10,13}\b"), "<타임스탬프>"),
    (re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "<uuid>"),
    (re.compile(r"\b[0-9a-f]{40}\b"), "<sha1>"),
    (re.compile(r"[?&](v|_|t|ts|version)=[\w.-]+"), ""),
    (re.compile(r"(JSESSIONID|PLAY_SESSION|PLAY_FLASH|yobi\.token)=[^;\s\"']+"), r"\1=<값>"),
]


def scrub(text: str) -> str:
    for pattern, repl in VOLATILE:
        text = pattern.sub(repl, text)
    return text


def norm_headers(resp: requests.Response, base: str) -> dict:
    h = {k.lower(): v for k, v in resp.headers.items()}
    out = {}
    ct = h.get("content-type")
    if ct:
        out["content-type"] = re.sub(r"\s+", "", ct.lower()).replace("charset=utf-8", "charset=utf-8")
    loc = h.get("location")
    if loc:
        out["location"] = urllib.parse.unquote(re.sub(r"^https?://[^/]+", "", loc))
    for k in ("content-disposition", "www-authenticate", "cache-control"):
        if k in h:
            out[k] = urllib.parse.unquote(h[k]) if k == "content-disposition" else h[k]
    cookies = sorted({c.split("=", 1)[0].strip() for c in re.findall(r"(?:^|, )([A-Za-z0-9_.\-]+=)", h.get("set-cookie", ""))
                      if c.split("=", 1)[0].strip().lower() not in ("expires", "path", "max-age", "domain", "samesite")})
    if cookies:
        out["set-cookie-names"] = ",".join(cookies)
    return out


def html_lines(body: str) -> list[str]:
    soup = BeautifulSoup(body, "lxml")
    for c in soup.find_all(string=lambda t: isinstance(t, Comment)):
        c.extract()
    lines: list[str] = []

    def walk(node, depth):
        for child in node.children:
            if getattr(child, "name", None) is None:
                text = re.sub(r"\s+", " ", str(child)).strip()
                if text:
                    lines.append(("  " * depth) + scrub(text))
            else:
                attrs = " ".join(f'{k}="{scrub(" ".join(v) if isinstance(v, list) else str(v))}"' for k, v in sorted(child.attrs.items()))
                lines.append(("  " * depth) + f"<{child.name}{(' ' + attrs) if attrs else ''}>")
                walk(child, depth + 1)

    walk(soup, 0)
    return lines


def norm_json(value):
    if isinstance(value, dict):
        return {k: norm_json(v) for k, v in value.items()}
    if isinstance(value, list):
        return [norm_json(v) for v in value]
    if isinstance(value, str):
        return scrub(value)
    return value


def body_view(resp: requests.Response, step: Step):
    if not step.compare_body:
        return ("skip", None)
    ct = (resp.headers.get("content-type") or "").lower()
    if "json" in ct:
        try:
            return ("json", norm_json(resp.json()))
        except ValueError:
            pass
    if "html" in ct:
        return ("html", html_lines(resp.text))
    if ct.startswith("text/") or "javascript" in ct or "xml" in ct:
        return ("text", scrub(resp.text).splitlines())
    return ("bytes", len(resp.content))


def diff_bodies(a, b, limit=24) -> list[str]:
    ka, va = a
    kb, vb = b
    if ka != kb:
        return [f"본문 종류가 다르다: {ka} 대 {kb}"]
    if ka == "skip":
        return []
    if ka in ("html", "text"):
        if va == vb:
            return []
        out = list(difflib.unified_diff(va, vb, "v1.6", "v2", lineterm="", n=1))
        return out[:limit] + ([f"... (차이 {len(out)}줄)"] if len(out) > limit else [])
    if ka == "json":
        if va == vb:
            return []
        sa = json.dumps(va, ensure_ascii=False, indent=1, sort_keys=False).splitlines()
        sb = json.dumps(vb, ensure_ascii=False, indent=1, sort_keys=False).splitlines()
        out = list(difflib.unified_diff(sa, sb, "v1.6", "v2", lineterm="", n=1))
        if not out:  # 키 순서만 다른 경우는 위에서 같다고 나온다(딕셔너리 비교). 순서까지 보려면 문자열로 비교한다
            return []
        return out[:limit] + ([f"... (차이 {len(out)}줄)"] if len(out) > limit else [])
    return [] if va == vb else [f"본문 크기가 다르다: {va} 대 {vb}"]


# --- 실행 -------------------------------------------------------------------------------------

def load_scenario(name: str):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, "scenarios", f"{name}.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def run(args):
    mod = load_scenario(args.scenario)
    steps: list[Step] = mod.steps()
    a, b = Server("v1.6", args.v16), Server("v2", args.v2)
    same, diffs = 0, []
    for i, step in enumerate(steps, 1):
        try:
            ra, rb = a.send(step), b.send(step)
        except requests.RequestException as e:
            diffs.append((i, step, [f"요청 실패: {e}"]))
            continue
        problems = []
        if ra.status_code != rb.status_code:
            problems.append(f"상태 코드: v1.6={ra.status_code} v2={rb.status_code}")
        ha, hb = norm_headers(ra, a.base), norm_headers(rb, b.base)
        for k in sorted(set(ha) | set(hb)):
            if ha.get(k) != hb.get(k):
                problems.append(f"헤더 {k}: v1.6={ha.get(k)!r} v2={hb.get(k)!r}")
        problems += diff_bodies(body_view(ra, step), body_view(rb, step))
        if problems:
            diffs.append((i, step, problems))
        else:
            same += 1
    lines = [f"# {args.scenario}: 같음 {same}, 다름 {len(diffs)} (전체 {len(steps)})", ""]
    for i, step, problems in diffs:
        lines.append(f"## {i}. [{step.actor}] {step.method} {step.path} {('- ' + step.note) if step.note else ''}")
        lines += [f"    {p}" for p in problems]
        lines.append("")
    report = "\n".join(lines)
    if args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(report)
    print(lines[0])
    return 0


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("scenario")
    r.add_argument("--v16", default="http://127.0.0.1:19200")
    r.add_argument("--v2", default="http://127.0.0.1:19300")
    r.add_argument("--out")
    args = p.parse_args()
    sys.exit(run(args))


if __name__ == "__main__":
    main()
