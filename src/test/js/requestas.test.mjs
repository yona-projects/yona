import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

export const tests = [{
  name: 'requestAs를 GET 링크에 걸어도 ReferenceError가 나지 않는다 (C003)',
  async run() {
    const page = await pageWith({ html: '<a id="a" href="/x">링크</a>', scripts: ['lib/jquery/jquery.requestAs.js'] });
    const result = await page.evaluate(() => {
      try { $('#a').requestAs({ sMethod: 'get' }); return 'ok'; } catch (e) { return String(e.message); }
    });
    await page.close();
    assert.equal(result, 'ok', result);
  }
}];
