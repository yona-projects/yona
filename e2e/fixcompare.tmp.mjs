// 고친 곳을 눈으로 알아보게 한다: v1.6, 고치기 전 v2(옛 CSS), 고친 뒤 v2를 같은 부분만 잘라 붉은 상자로 표시해 위아래로 놓는다.
import fs from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';

const SC = process.argv[2];
const OLD = fs.readFileSync(join(SC, 'old-yobi.css'), 'utf8');
const OUT = join(process.cwd(), '..', 'shots', 'v16-vs-v2');
const B = { v16: 'http://127.0.0.1:19200', v2: 'http://127.0.0.1:19300' };
const b = await chromium.launch();

async function shot(side, oldCss, path, boxes, clip, file) {
  const base = B[side];
  const ctx = await b.newContext({ locale: 'ko-KR', viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  await ctx.request.get(base + '/users/loginform');
  const x = (await ctx.cookies(base)).find((c) => c.name === 'XSRF-TOKEN')?.value;
  await ctx.request.post(base + '/users/login', { form: { loginIdOrEmail: 'tester', password: 'pass1234' }, headers: x ? { 'X-XSRF-TOKEN': x } : {}, maxRedirects: 0 });
  if (oldCss) await ctx.route('**/assets/stylesheets/yobi.css*', (r) => r.fulfill({ contentType: 'text/css', body: OLD }));
  const p = await ctx.newPage();
  await p.goto(base + path, { waitUntil: 'load' });
  await p.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {});
  await p.evaluate((sels) => { for (const s of sels) for (const e of document.querySelectorAll(s)) e.style.cssText += ';outline:3px solid #e00;outline-offset:2px'; }, boxes);
  // 페이지 내용의 오른쪽 끝(1270)에 푸른 점선을 긋는다
  await p.evaluate(() => { const l = document.createElement('div'); l.style.cssText = 'position:absolute;left:1270px;top:0;height:100%;border-left:2px dashed #06f;z-index:99999;pointer-events:none'; document.body.appendChild(l); });
  await p.screenshot({ path: join(OUT, file), clip });
  await ctx.close();
}
const sets = [
  { name: 'fix-code', path: '/tester/proj1/code/master', boxes: ['#s2id_branches', '.listhead .filename strong', '.listhead .commitDate strong', '.listitem .commitDate'], clip: { x: 0, y: 205, width: 1280, height: 190 },
    note: '붉은 상자: 브랜치 선택 상자의 폭, 목록 머리와 날짜 열의 글자(`파일명`, `커밋한 날짜`, `01-03`). 푸른 점선은 페이지 내용의 오른쪽 끝이다' },
  { name: 'fix-home', path: '/tester/proj1', boxes: ['#cloneURL', '#cloneURLBtn'], clip: { x: 880, y: 165, width: 400, height: 115 },
    note: '붉은 상자: 저장소 주소 입력란과 `주소 복사` 단추. 푸른 점선은 페이지 내용의 오른쪽 끝이다. 단추가 점선을 넘으면 어긋난 것' },
];
try {
  for (const s of sets) {
    await shot('v16', false, s.path, s.boxes, s.clip, `${s.name}-1-v16.png`);
    await shot('v2', true, s.path, s.boxes, s.clip, `${s.name}-2-before.png`);
    await shot('v2', false, s.path, s.boxes, s.clip, `${s.name}-3-after.png`);
    const ctx = await b.newContext({ viewport: { width: s.clip.width + 40, height: 900 } });
    const p = await ctx.newPage();
    const f = join(OUT, `${s.name}.html`);
    const panel = (title, img, color) => `<div style="margin:0 0 14px"><div style="font:bold 20px sans-serif;color:${color};padding:4px 0">${title}</div><img style="width:${s.clip.width}px;border:1px solid #999;display:block" src="file://${join(OUT, img)}"></div>`;
    fs.writeFileSync(f, `<body style="margin:0;padding:12px 20px;background:#fff;font-family:sans-serif"><div style="font:16px sans-serif;color:#333;padding-bottom:8px">${s.note}</div>${panel('1. v1.6 (기준)', `${s.name}-1-v16.png`, '#111')}${panel('2. 고치기 전 v2', `${s.name}-2-before.png`, '#c00')}${panel('3. 고친 뒤 v2', `${s.name}-3-after.png`, '#070')}</body>`);
    await p.goto('file://' + f); await p.waitForLoadState('load'); await p.waitForTimeout(300);
    await p.screenshot({ path: join(OUT, `${s.name}.png`), fullPage: true });
    await ctx.close();
    console.log('만듦', s.name);
  }
} finally { await b.close(); }
