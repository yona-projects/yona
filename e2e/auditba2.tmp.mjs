// 전후 비교 그림을 만든다(Playwright로만). 전: 줄이기 전 캡처, 후: 지금 캡처. 필터와 단추가 있는 위쪽만 잘라 붙인다.
import { chromium } from 'playwright';
import { readFileSync, writeFileSync } from 'node:fs';
const img = (p) => 'data:image/png;base64,' + readFileSync(p).toString('base64');
const before = img('shots/x38i-audit-site-audit3.png'), after = img('shots/x38i-audit-site-audit4.png');
const crop = 'width:1010px;height:300px;object-fit:cover;object-position:-210px -130px;display:block';
const html = `<body style="margin:0;font:bold 18px sans-serif;background:#fff"><div style="background:#c00;color:#fff;padding:6px 12px">전(Before): 적용과 초기화 단추가 입력창 줄 높이로 늘어난다(37px)</div><img style="${crop}" src="${before}"><div style="background:#0a0;color:#fff;padding:6px 12px">후(After): 입력창 여백을 없애 모든 단추가 같은 높이(27.5px)</div><img style="${crop}" src="${after}"></body>`;
writeFileSync('/tmp/claude-1000/auditba.html', html);
const b = await chromium.launch(); const page = await b.newPage({ viewport: { width: 1010, height: 700 } });
await page.goto('file:///tmp/claude-1000/auditba.html'); await page.screenshot({ path: 'shots/x38i-audit-buttons-ba2.png' }); await b.close();
console.log('ok');
