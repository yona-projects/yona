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
    (re.compile(r"(127\.0\.0\.1|localhost):19[23]00"), "<host>"),
    (re.compile(r"\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?(\.\d+)?(Z|[+-]\d{2}:?\d{2})?"), "<시각>"),
    (re.compile(r"\d{4}[./-]\d{1,2}[./-]\d{1,2}"), "<날짜>"),
    (re.compile(r"\b\d+\s+(second|minute|hour|day|week|month|year)s?\s+ago\b", re.I), "<상대시간>"),
    (re.compile(r"\b(a|an|\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago\b", re.I), "<상대시간>"),
    (re.compile(r"\b\d{10,13}\b"), "<타임스탬프>"),
    (re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "<uuid>"),
    (re.compile(r"\b[0-9a-f]{40}\b"), "<sha1>"),
    (re.compile(r"[?&](v|_|t|ts|version)=[\w.-]+"), ""),
    (re.compile(r"(JSESSIONID|PLAY_SESSION|PLAY_FLASH|yobi\.token)=[^;\s\"']+"), r"\1=<값>"),
    # Thymeleaf의 자바스크립트 이스케이프(\/)와 문자열 이어 붙이기(" + ")는 같은 문자열을 만든다
    (re.compile(r"\\/"), "/"),
    (re.compile(r'"\s*\+\s*"'), ""),
    # Ebean 시퀀스가 20개씩 번호를 받아 가므로 재시작 뒤 이슈 내부 번호가 서버마다 다르다
    (re.compile(r"(issue-item-|issue-|parentIssueId=|issueId=|issueId\"\s*:\s*\")\d+"), r"\1<n>"),
    (re.compile(r"(resource\.id=)\d+(?!:)"), r"\1<n>"),
    (re.compile(r"(comment-|comments/|comment/|commentId=|editor-contents-|upload-|event-|comment-editform-|#edit-|#preview-|edit-|preview-)\d+"), r"\1<n>"),
    (re.compile(r"\bJust now\b"), "<상대시간>"),
    (re.compile(r"(comment-body-)\d+"), r"\1<n>"),
    # FileDiff.hashCode()는 enum의 신원 해시를 섞어 서버를 다시 띄울 때마다 값이 다르다
    (re.compile(r"(data-hashcode=\")-?\d+"), r"\1<해시>"),
    # 편집기가 만들 때마다 새로 정하는 8글자 영문 번호는 서버마다 다르다
    (re.compile(r"\b(edit|preview|editor-[a-z]+|help-nav|markdown-help)-[A-Za-z]{8}\b"), r"\1-<무작위>"),
]


# v1.6 서버는 H2에서 사용자 번호를 33부터 매긴다(가입 순서: tester, other, third). v2는 2부터이다. 비교할 때 v1.6 쪽 번호를 v2 번호로 바꾼다.
USER_ID_MAP = {"33": "2", "34": "3", "35": "4"}
_ID_ATTR = re.compile(r"^(data-.*id|data-value|.*-id|id|value|href|data-href|data-request-uri|content)$")


def map_user_ids_text(text: str) -> str:
    for old, new in USER_ID_MAP.items():
        text = re.sub(rf"(?<![\w.]){old}(?![\w.])", new, text)
    return text


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
        out["location"] = scrub(urllib.parse.unquote(re.sub(r"^https?://[^/]+", "", loc)))
    for k in ("content-disposition", "www-authenticate", "cache-control"):
        if k in h:
            out[k] = urllib.parse.unquote(h[k]) if k == "content-disposition" else h[k]
    cookies = sorted({c.split("=", 1)[0].strip() for c in re.findall(r"(?:^|, )([A-Za-z0-9_.\-]+=)", h.get("set-cookie", ""))
                      if c.split("=", 1)[0].strip().lower() not in ("expires", "path", "max-age", "domain", "samesite")})
    if cookies:
        # 세션 쿠키는 서버 종류마다 이름이 다르다(Play: PLAY_SESSION, 서블릿: JSESSIONID). 이름은 같은 것으로 본다.
        names = sorted({c for c in cookies if c not in ("PLAY_SESSION", "JSESSIONID", "PLAY_FLASH")})
        if names:
            out["set-cookie-names"] = ",".join(names)
    return out


_BOOLEAN_ATTRS = {"checked", "selected", "disabled", "readonly", "multiple", "required", "autofocus", "hidden"}


def html_lines(body: str, map_ids: bool = False) -> list[str]:
    soup = BeautifulSoup(body, "lxml")
    for c in soup.find_all(string=lambda t: isinstance(t, Comment)):
        c.extract()
    lines: list[str] = []

    def walk(node, depth):
        for child in node.children:
            if getattr(child, "name", None) is None:
                text = re.sub(r"\s+", " ", str(child)).strip()
                text = text.replace("\\/", "/").replace("\\u0026", "&")  # 스크립트 안의 같은 뜻의 이스케이프
                if text:
                    lines.append(("  " * depth) + scrub(text))
            else:
                items = []
                for k, v in sorted(child.attrs.items()):
                    value = " ".join(v) if isinstance(v, list) else str(v)
                    if k == "class" and not value.strip():
                        continue  # 빈 class는 의미가 없다
                    if k == "value" and value == "" and child.name == "input":
                        continue  # 비어 있는 value는 없는 것과 같다
                    value = re.sub(chr(32) + "+", chr(32), value).strip()
                    if k in ("data-comment-id",):
                        value = "<n>"
                    if k in ("data-issue-id", "data-posting-id") or (k == "value" and re.match(r"^(issues|postings)\[\d+\]\.id$", child.get("name", "") or "")):
                        value = "<n>"  # 이슈 내부 번호는 시퀀스 할당 탓에 서버마다 다르다
                    if k == "data-resourceid" or (k == "value" and (child.get("name") in ("id", "parentCommentId") or "parentCommentId" in (child.get("class") or []))):
                        value = "<n>"  # 댓글 내부 번호는 시퀀스 할당 탓에 서버마다 다르다
                    if k == "data-hashcode":
                        value = "<해시>"  # FileDiff.hashCode()는 enum의 신원 해시를 섞어 서버를 다시 띄울 때마다 다르다
                    if k == "method":
                        value = value.lower()  # 폼의 method는 대소문자를 가리지 않는다
                    if k in _BOOLEAN_ATTRS and value in ("", k):
                        value = ""  # checked="checked"와 checked=""는 같은 뜻이다
                    if k == "class":
                        value = " ".join(sorted(value.split()))  # class는 순서가 의미 없는 집합이다
                    if k in ("href", "src", "action", "data-href", "data-request-uri"):
                        value = re.sub(r"\s+", "", value)  # 주소 안의 공백과 줄바꿈은 브라우저가 지운다
                    if map_ids and _ID_ATTR.match(k):
                        value = map_user_ids_text(value)
                    items.append(f'{k}="{scrub(value)}"')
                attrs = " ".join(items)
                lines.append(("  " * depth) + f"<{child.name}{(' ' + attrs) if attrs else ''}>")
                walk(child, depth + 1)

    walk(soup, 0)
    return lines


def norm_json(value, map_ids=False, key=""):
    if isinstance(value, dict):
        return {k: norm_json(v, map_ids, k) for k, v in value.items()}
    if isinstance(value, list):
        return [norm_json(v, map_ids, key) for v in value]
    if isinstance(value, str):
        return scrub(map_user_ids_text(value) if map_ids else value)
    if map_ids and isinstance(value, int) and not isinstance(value, bool) and str(value) in USER_ID_MAP and key.lower().endswith("id"):
        return int(USER_ID_MAP[str(value)])
    return value


def body_view(resp: requests.Response, step: Step, map_ids: bool = False):
    if not step.compare_body:
        return ("skip", None)
    ct = (resp.headers.get("content-type") or "").lower()
    if "json" in ct:
        try:
            return ("json", norm_json(resp.json(), map_ids))
        except ValueError:
            pass
    if "html" in ct:
        return ("html", html_lines(resp.text, map_ids))
    if ct.startswith("text/") or "javascript" in ct or "xml" in ct:
        return ("text", [l for l in scrub(resp.text).splitlines() if l.strip()])
    import hashlib
    return ("bytes", f"{len(resp.content)}:{hashlib.md5(resp.content).hexdigest()}")


def char_diff(x: str, y: str, width=70) -> str:
    """긴 한 줄이 다른 곳을 앞뒤 문맥과 함께 보여 준다."""
    sm = difflib.SequenceMatcher(None, x, y, autojunk=False)
    parts = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag != "equal":
            parts.append(f"[{x[max(0, i1 - 25):i1]}«{x[i1:i2]}» → «{y[j1:j2]}»{x[i2:i2 + 25]}]")
    return " ".join(parts)[: width * 6]


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
        # 한 줄이 조금만 다른 경우는 글자 단위로도 보여 준다
        pairs = []
        sm = difflib.SequenceMatcher(None, va, vb, autojunk=False)
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag == "replace" and (i2 - i1) == (j2 - j1) and (i2 - i1) <= 3:
                for x, y in zip(va[i1:i2], vb[j1:j2]):
                    if len(x) > 120 or len(y) > 120:
                        pairs.append("글자 차이: " + char_diff(x.strip(), y.strip()))
        return (out[:limit] + ([f"... (차이 {len(out)}줄)"] if len(out) > limit else [])) + pairs[:6]
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
    trace = []
    from collections import Counter
    line_counter: Counter = Counter()
    line_steps: dict = {}
    for i, step in enumerate(steps, 1):
        try:
            ra, rb = a.send(step), b.send(step)
        except requests.RequestException as e:
            diffs.append((i, step, [f"요청 실패: {e}"]))
            continue
        trace.append({"i": i, "actor": step.actor, "method": step.method, "path": step.path, "v16": ra.status_code, "v2": rb.status_code,
                      "v16_cc": ra.headers.get("cache-control"), "v2_cc": rb.headers.get("cache-control"),
                      "v16_ct": ra.headers.get("content-type"), "v2_ct": rb.headers.get("content-type")})
        problems = []
        if ra.status_code != rb.status_code:
            problems.append(f"상태 코드: v1.6={ra.status_code} v2={rb.status_code}")
        ha, hb = norm_headers(ra, a.base), norm_headers(rb, b.base)
        for k in sorted(set(ha) | set(hb)):
            if ha.get(k) != hb.get(k):
                problems.append(f"헤더 {k}: v1.6={ha.get(k)!r} v2={hb.get(k)!r}")
        problems += diff_bodies(body_view(ra, step, True), body_view(rb, step, False))
        if problems:
            for p_ in problems:
                if p_.startswith(("-", "+")) and not p_.startswith(("---", "+++")):
                    line_counter[p_.strip()] += 1
                    line_steps.setdefault(p_.strip(), []).append(i)
                elif not p_.startswith((" ", "@@", "...")):
                    line_counter[p_.strip()] += 1
                    line_steps.setdefault(p_.strip(), []).append(i)
            diffs.append((i, step, problems))
        else:
            same += 1
    lines = [f"# {args.scenario}: 같음 {same}, 다름 {len(diffs)} (전체 {len(steps)})"]
    for i, step, problems in diffs:
        lines.append(f"## {i}. [{step.actor}] {step.method} {step.path} {('- ' + step.note) if step.note else ''}")
        lines += [f"    {p}" for p in problems]
        lines.append("")
    summary = ["", "## 자주 나오는 차이 (많이 나온 순서)"]
    for text, n in line_counter.most_common(args.top):
        steps_ = line_steps[text]
        summary.append(f"- ({n}회, 요청 {steps_[0]}번 외) {text[:230]}")
    summary.append("")
    lines[1:1] = summary
    report = "\n".join(lines)
    if args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(report)
    if args.out:
        with open(args.out.replace(".md", ".trace.jsonl"), "w", encoding="utf-8") as f:
            for t in trace:
                f.write(json.dumps(t, ensure_ascii=False) + "\n")
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
    r.add_argument("--top", type=int, default=40)
    args = p.parse_args()
    sys.exit(run(args))


if __name__ == "__main__":
    main()
