#!/usr/bin/env python3
"""무작위 마크다운 조각을 두 서버의 미리보기에 보내 결과가 다른 경우를 찾는다. 사용: mdfuzz.py [개수] [씨앗]"""
import random
import sys

from probe import session, BASES

TOKENS = ["# ", "## ", "### ", "- ", "* ", "+ ", "1. ", "2) ", "> ", ">> ", "    ", "\t", "```", "~~~", "`", "``", "**", "*", "__", "_", "~~", "[", "]", "(", ")", "![", "<", ">",
          "&", "&amp;", "|", "---", "***", "===", "\\", "\n", "\n\n", "  \n", "http://a.b/c", "www.x.com", "a@b.c", "#1", "@tester", "tester/proj1#1", "5559df3", "abc", "한글", "😀",
          " ", "  ", "<b>", "</b>", "<div>", "</div>", "<br>", "<!--", "-->", "<script>", "|a|b|", "|--|--|", ":smile:", "[x]", "[ ]", "<kbd>", "<a href='x'>", "</a>", "\"", "'", "%", "{", "}"]


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 300
    random.seed(int(sys.argv[2]) if len(sys.argv) > 2 else 1)
    s16, s2 = session(BASES["v1.6"], "tester"), session(BASES["v2"], "tester")
    diffs = 0
    for i in range(n):
        text = "".join(random.choice(TOKENS) for _ in range(random.randint(1, 40)))
        a = s16.post(BASES["v1.6"] + "/markdown/tester/proj1", json={"body": text, "breaks": True})
        b = s2.post(BASES["v2"] + "/markdown/tester/proj1", json={"body": text, "breaks": True})
        if (a.status_code, a.text) != (b.status_code, b.text):
            diffs += 1
            print(f"--- 다름 #{i}: {text!r}\n    v1.6 {a.status_code} {a.text[:300]!r}\n    v2   {b.status_code} {b.text[:300]!r}")
            if diffs >= 12:
                break
    print(f"검사 {i + 1}건, 다른 것 {diffs}건")


main()
