import { chromium } from 'playwright';
import { join } from 'node:path';
import { ROOT } from './lib/server.mjs';
const G = 'http://localhost:23000';
const TID = '79e1d8e311bd5c5c8ff8d74a90a8f65c';
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await ctx.newPage();
// 로그인 도우미: 폼으로 한 번만 로그인하고(기본 비밀번호 변경 안내는 Skip) 같은 세션으로 모든 화면을 캡처한다
await page.goto(G + '/login', { waitUntil: 'networkidle' });
await page.waitForSelector('input[name="user"]', { timeout: 20000 });
await page.fill('input[name="user"]', 'admin'); await page.fill('input[name="password"]', 'admin');
await page.click('button[type="submit"]');
await page.getByText('Skip', { exact: true }).click({ timeout: 15000 }).catch(() => {});
await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 20000 });
const shots = [
  ['grafana-system', '/d/yona-system?from=now-30m&to=now&kiosk', 2300],
  ['grafana-vcs', '/d/yona-vcs?from=now-30m&to=now&kiosk', 2300],
  ['grafana-collaboration', '/d/yona-collaboration?from=now-30m&to=now&kiosk', 2400],
  ['grafana-background', '/d/yona-background?from=now-30m&to=now&kiosk', 1700],
  ['grafana-logs', '/d/yona-logs?from=now-30m&to=now&kiosk', 1500],
  ['grafana-alert-rules', '/alerting/list?view=list', 1400],
];
if (TID) shots.push(['grafana-trace', '/explore?schemaVersion=1&orgId=1&panes=' + encodeURIComponent(JSON.stringify({ a: { datasource: 'tempo', queries: [{ refId: 'A', datasource: { type: 'tempo', uid: 'tempo' }, queryType: 'traceql', query: TID }], range: { from: 'now-1h', to: 'now' } } })), 1350]);
for (const [name, path, height] of shots) {
  await page.setViewportSize({ width: 1600, height });
  await page.goto(G + path, { waitUntil: 'load' });
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(5000);
  await page.screenshot({ path: join(ROOT, 'shots', 'x10-' + name + '.png') });
  console.log(name);
}
await browser.close();
