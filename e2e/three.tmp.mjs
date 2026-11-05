// 남은 세 가지 차이를 v1.6과 v2로 같은 부분만 잘라 위아래로 놓는다.
import fs from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';
const B = { v16: 'http://127.0.0.1:19200', v2: 'http://127.0.0.1:19300' };
const PASS = { tester: 'pass1234', admin: 'admin1234' };
const OUT = join(process.cwd(), '..', 'shots', 'v16-vs-v2');
const CASES = [
  { name: 'three-1-userlist', actor: 'admin', path: '/sites/userList', target: '.listitem', pad: [10, 10, 10, 10], title: '사이트 관리, 사용자 목록의 첫 사용자 줄(단추 열 배치)' },
  { name: 'three-2-issue-option', actor: 'tester', path: '/tester/proj1/issue/1/editform', target: '.subtask-message', pad: [420, 40, 30, 90], title: '이슈 수정 폼의 `이슈 옵션` 단추 위치(제목 줄 오른쪽 끝)' },
  { name: 'three-3-state', actor: 'tester', path: '/tester/proj1/issue/1/editform', target: '#state', pad: [60, 40, 20, 160], title: '이슈 수정 폼의 `상태` 선택' },
];
const b = await chromium.launch();
async function shot(side, c) {
  const base = B[side];
  const ctx = await b.newContext({ locale: 'ko-KR', viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  await ctx.request.get(base + '/users/loginform');
  const x = (await ctx.cookies(base)).find((k) => k.name === 'XSRF-TOKEN')?.value;
  await ctx.request.post(base + '/users/login', { form: { loginIdOrEmail: c.actor, password: PASS[c.actor] }, headers: x ? { 'X-XSRF-TOKEN': x } : {}, maxRedirects: 0 });
  const p = await ctx.newPage();
  await p.goto(base + c.path, { waitUntil: 'load' });
  await p.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
  const r = await p.evaluate((sel) => { const e = document.querySelector(sel); e.style.cssText += ';outline:3px solid #e00;outline-offset:2px'; const q = e.getBoundingClientRect(); return { x: q.left + scrollX, y: q.top + scrollY, w: q.width, h: q.height }; }, c.target);
  const [l, t, rr, bb] = c.pad;
  const clip = { x: Math.max(0, r.x - l), y: Math.max(0, r.y - t), width: Math.min(1280, r.w + l + rr), height: r.h + t + bb };
  if (clip.x + clip.width > 1280) clip.width = 1280 - clip.x;
  await p.screenshot({ path: join(OUT, `${c.name}-${side}.png`), clip, fullPage: true });
  await ctx.close();
  return clip;
}
for (const c of CASES) {
  const clips = {};
  for (const side of ['v16', 'v2']) clips[side] = await shot(side, c);
  const ctx = await b.newContext({ viewport: { width: 1320, height: 900 } });
  const p = await ctx.newPage();
  const f = join(OUT, `${c.name}.html`);
  const panel = (t, side, color) => `<div style="margin:0 0 14px"><div style="font:bold 20px sans-serif;color:${color};padding:4px 0">${t}</div><img style="width:${clips[side].width}px;border:1px solid #999;display:block" src="file://${join(OUT, `${c.name}-${side}.png`)}"></div>`;
  fs.writeFileSync(f, `<body style="margin:0;padding:12px 20px;background:#fff;font-family:sans-serif"><div style="font:16px sans-serif;color:#333;padding-bottom:8px">${c.title}. 붉은 상자가 대상이다</div>${panel('1. v1.6', 'v16', '#111')}${panel('2. v2', 'v2', '#c00')}</body>`);
  await p.goto('file://' + f); await p.waitForLoadState('load'); await p.waitForTimeout(300);
  await p.screenshot({ path: join(OUT, `${c.name}.png`), fullPage: true });
  await ctx.close();
  fs.unlinkSync(f);
  console.log('만듦', c.name);
}
await b.close();
