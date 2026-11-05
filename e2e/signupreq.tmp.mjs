// R-110 확인: 가입 화면이 이메일 중복 확인을 부를 때 실제로 보내는 요청(머리글, 본문)과 서버 응답을 본다.
import { join } from 'node:path';
import { chromium } from 'playwright';
import { ROOT, startServer } from './lib/server.mjs';

const S = await startServer({ port: 19293, dataDir: join(ROOT, '.state', 'server') });
const browser = await chromium.launch();
try {
  const page = await (await browser.newContext({ locale: 'en-US' })).newPage();
  const seen = [];
  page.on('request', (q) => { if (q.url().includes('/user/is')) seen.push(q); });
  await page.goto(S.base + '/users/signupform', { waitUntil: 'load' }); console.log('page', page.url().replace(S.base, ''), (await page.title()), await page.locator('input').evaluateAll(e => e.map(x => x.name + (x.readOnly ? '(ro)' : ''))));
  for (const [sel, val] of [['input[name=loginId]', 'someone1'], ['input[name=email]', 'admin@example.com']]) {
    await page.fill(sel, val); await page.locator(sel).blur(); await page.waitForTimeout(800);
  }
  for (const q of seen) {
    const h = await q.allHeaders(); const r = await q.response();
    console.log(JSON.stringify({ method: q.method(), url: q.url().replace(S.base, ''), contentType: h['content-type'] ?? null, accept: h['accept'], 본문: q.postData(), 응답: r?.status(), 응답본문: await r?.text() }));
  }
  console.log('요청 수', seen.length);
} finally { await browser.close(); await S.stop(); }
