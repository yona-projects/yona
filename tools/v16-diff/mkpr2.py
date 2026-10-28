#!/usr/bin/env python3
"""scenarios/pr1.py를 바탕으로 조직 소유 한글 이름 프로젝트용 scenarios/pr2.py를 만든다(경로와 프로젝트 번호만 바꾼다)."""
import os

here = os.path.dirname(os.path.abspath(__file__))
src = open(f"{here}/scenarios/pr1.py", encoding="utf-8").read()
t = src.replace("/tester/proj1", "/corp1/한글저장소").replace("/other/proj1", "/other/한글포크").replace("other / proj1", "other / 한글포크")
t = t.replace('toProjectId="1"', 'toProjectId="<<tid>>"').replace("toProjectId=1&", "toProjectId=<<tid>>&")
t = t.replace('"""포크(other/proj1)에서 원본(tester/proj1)으로', '"""포크(other/한글포크)에서 조직 소유 한글 이름 원본(corp1/한글저장소)으로')
marker = 's.append(Step("other", "GET", "/other/한글포크/newPullRequestForm", capture={"fid"'
read_tid = 's.append(Step("tester", "GET", "/corp1/한글저장소/settingform", capture={"tid": ("body", r\'name="id" value="(\\d+)"\')}, note="원본 번호 읽기"))\n    '
assert marker in t
t = t.replace(marker, read_tid + marker, 1)
open(f"{here}/scenarios/pr2.py", "w", encoding="utf-8").write(t)
print("pr2.py 만듦", t.count("<<tid>>"), "곳")
