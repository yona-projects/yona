import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

// Diagnostic experiment, NOT an application implementation or acceptance suite.
// The HTTP seam wraps real Thymeleaf HTML; Turbo alone performs the DOM replacement.
// Requires the existing project-create and issue-crud fixtures, and processResources.
test('probe unchanged issue detail lifecycle inside a real Turbo Frame', async ({ page }, testInfo) => {
  test.skip(process.env.YONA_TURBO_PROBE !== '1', 'Opt-in architecture experiment');
  const base = `/${requireSeed('projectOwner')}/${requireSeed('projectName')}`;
  const detail = `${base}/issue/${requireSeed('issueNumber')}`;
  const evidence: Record<string, unknown> = {};

  // A non-destructive control: the same existing delete trigger opens its dialog on a full load.
  await page.goto(detail);
  await page.locator('a[href="#deleteConfirm"]').click();
  await expect(page.locator('#deleteConfirm')).toHaveAttribute('open', '');
  evidence.fullPageDialog = 'opens';

  await page.goto(`${base}/issues`);
  await page.evaluate(() => localStorage.setItem('useTwoColumnMode', 'false'));
  await page.reload();
  await page.addScriptTag({ type: 'module', content: `
    import * as Turbo from '/javascripts/turbo/turbo.es2017-esm.js';
    Turbo.session.drive = false;
    Turbo.config.forms.mode = 'off';
    document.documentElement.dataset.turboProbeReady = 'true';
  ` });
  await expect(page.locator('html')).toHaveAttribute('data-turbo-probe-ready', 'true');
  await page.route(`**${detail}`, async route => {
    if (route.request().headers()['turbo-frame'] !== 'issue-detail') {
      await route.continue();
      return;
    }
    const response = await route.fetch();
    const html = await response.text();
    evidence.serverHtmlBytes = Buffer.byteLength(html);
    evidence.serverContentType = response.headers()['content-type'];
    // Test-only response transformation: preserve the actual rendered markup and ready handlers.
    // No fake detail data, JSON screen API, custom fetch/swap, or replay of DOMContentLoaded.
    const framed = await page.evaluate(source => {
      const doc = new DOMParser().parseFromString(source, 'text/html');
      const frame = doc.createElement('turbo-frame');
      frame.id = 'issue-detail';
      const content = doc.createElement('div');
      content.dataset.turbo = 'false';
      content.append(doc.querySelector('.page-wrap-outer')!, doc.querySelector('#deleteConfirm')!);
      frame.append(content);
      for (const script of doc.querySelectorAll('script:not([src])')) {
        if (script.textContent?.includes('DOMContentLoaded')) frame.append(script);
      }
      doc.body.append(frame);
      return '<!DOCTYPE html>' + doc.documentElement.outerHTML;
    }, html);
    evidence.frameResponseHtmlBytes = Buffer.byteLength(framed);
    await route.fulfill({ response, body: framed });
  });

  await page.evaluate(url => {
    const frame = document.createElement('turbo-frame');
    frame.id = 'issue-detail';
    frame.dataset.turboAction = 'advance';
    const link = document.createElement('a');
    link.href = url;
    link.textContent = 'Probe selected issue';
    link.dataset.turbo = 'true';
    link.dataset.turboFrame = 'issue-detail';
    link.id = 'turbo-probe-link';
    document.body.append(link, frame);
  }, detail);
  const requests: string[] = [];
  page.on('request', request => {
    if (request.headers()['turbo-frame']) requests.push(request.url());
  });
  await page.locator('#turbo-probe-link').click();
  await expect(page.locator('turbo-frame#issue-detail .board-header')).toContainText('E2E seed issue');
  await expect(page.locator('turbo-frame#issue-detail')).toHaveAttribute('complete', '');
  expect(requests).toHaveLength(1);
  await expect(page.locator('iframe, yona-page-slide')).toHaveCount(0);
  evidence.frameRequests = requests;
  evidence.frameHtmlRendered = true;
  evidence.iframeCount = 0;

  // Negative gate: unchanged DOMContentLoaded initialization does not run on frame insertion.
  const dialog = page.locator('turbo-frame#issue-detail #deleteConfirm');
  await page.locator('turbo-frame#issue-detail a[href="#deleteConfirm"]').click();
  await expect(dialog).not.toHaveAttribute('open', '');
  evidence.frameDialog = 'does not open';
  evidence.verdict = 'HTML transport succeeds; unchanged detail interaction is not frame-compatible';
  console.log('TURBO_LIFECYCLE_PROBE\n' + JSON.stringify(evidence, null, 2));
  await testInfo.attach('turbo-lifecycle-probe.json', {
    body: JSON.stringify(evidence, null, 2), contentType: 'application/json',
  });
  await testInfo.attach('turbo-frame.png', { body: await page.screenshot(), contentType: 'image/png' });
});

test('Turbo forms-off keeps regular Thymeleaf POST and CSRF enforcement', async ({ page }) => {
  test.skip(process.env.YONA_TURBO_PROBE !== '1', 'Opt-in architecture experiment');
  const base = `/${requireSeed('projectOwner')}/${requireSeed('projectName')}`;
  await page.goto(`${base}/issueform`);
  await page.addScriptTag({ type: 'module', content: `
    import * as Turbo from '/javascripts/turbo/turbo.es2017-esm.js';
    Turbo.session.drive = false;
    Turbo.config.forms.mode = 'off';
    document.documentElement.dataset.turboProbeReady = 'true';
  ` });
  await expect(page.locator('html')).toHaveAttribute('data-turbo-probe-ready', 'true');
  const rejected = await page.request.post(`${base}/issues`, { form: { title: 'Must not be created' } });
  expect(rejected.status()).toBe(403);
  await page.fill('#title', 'Turbo forms-off CSRF probe');
  const [response] = await Promise.all([
    page.waitForResponse(res => res.request().method() === 'POST' && res.url().endsWith(`${base}/issues`)),
    page.click('#button-save'),
  ]);
  expect(response.status()).toBe(302);
  expect(response.request().isNavigationRequest()).toBe(true);
  expect(response.request().headers()['turbo-frame']).toBeUndefined();
  expect(response.request().postData()).toContain('name="_csrf"');
  await expect(page.locator('.board-header')).toContainText('Turbo forms-off CSRF probe');
});

test('canonical selected URLs collide in the unchanged comment draft storage', async ({ page }, testInfo) => {
  test.skip(process.env.YONA_TURBO_PROBE !== '1', 'Opt-in architecture experiment');
  const base = `/${requireSeed('projectOwner')}/${requireSeed('projectName')}`;
  const issueA = requireSeed('issueNumber');
  await page.goto(`${base}/issueform`);
  await page.fill('#title', 'Separate issue B for draft isolation probe');
  await page.click('#button-save');
  await expect(page).toHaveURL(/\/issue\/\d+$/);
  const issueB = new URL(page.url()).pathname.split('/').pop()!;
  const listPath = `${base}/issues`;
  // This HTTP alias is only a diagnostic seam, not an implemented selected controller.
  // Full document navigation deliberately removes the frame-mount problem from this check.
  for (const number of [String(issueA), issueB]) {
    await page.route(`**${listPath}?selected=${number}`, async route => {
      const response = await route.fetch({ url: new URL(`${base}/issue/${number}`, page.url()).href });
      await route.fulfill({ response });
    });
  }
  await page.goto(`${listPath}?selected=${issueA}`);
  const draft = `Unsubmitted private draft for issue A ${issueA}`;
  const editor = page.locator('#comment-form textarea[data-editor-mode="comment-body"]');
  await editor.evaluate((element: HTMLTextAreaElement, value) => {
    element.value = value;
    element.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'a' }));
  }, draft);
  await expect.poll(() => page.evaluate(key => localStorage.getItem(key), listPath), { timeout: 7000 })
    .toBe(draft);
  await page.goto(`${listPath}?selected=${issueB}`);
  await expect(page.locator('.board-header')).toContainText('Separate issue B for draft isolation probe');
  await expect(editor).toHaveValue(draft);
  const evidence = {
    issueA, issueB, draftKey: listPath,
    outcome: 'A draft restored into B despite a fresh full document and different selected number',
    scope: 'real Thymeleaf views at test-only canonical URL aliases; no production selected route claimed',
  };
  console.log('TURBO_DRAFT_ISOLATION_PROBE\n' + JSON.stringify(evidence, null, 2));
  await testInfo.attach('draft-isolation-probe.json', {
    body: JSON.stringify(evidence, null, 2), contentType: 'application/json',
  });
});
