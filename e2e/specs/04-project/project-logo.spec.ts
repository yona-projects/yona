import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed } from '../../support/seed-store';

/**
 * Screen: GET /{owner}/{projectName}/setting (project/setting.html) -- the logo file input
 * (#logoPath) uploads immediately on its native "change" event (fetch POST to
 * /api/projects/{id}/logo, ProjectController.updateProjectLogo), then reloads the page on success.
 * There is no separate "save" button for this field.
 *
 * GET /projects/{projectId}/logo (ProjectViewController.projectLogo) serves the default
 * classpath image (static/images/project_default_logo.png) until an attachment exists, then
 * serves the uploaded bytes -- fetching it after upload and comparing it byte-for-byte against
 * what was uploaded confirms persistence regardless of the URL never changing.
 */
const TINY_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';

test('uploading a project logo persists and changes the served image', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-logo-'));
  const filePath = path.join(dir, 'logo.png');
  fs.writeFileSync(filePath, Buffer.from(TINY_PNG_BASE64, 'base64'));

  await page.goto(`/${owner}/${name}/setting`);
  const logoWrap = page.locator('.logo-wrap');
  await expect(logoWrap).toBeVisible();
  const style = await logoWrap.getAttribute('style');
  const projectId = style?.match(/\/projects\/(\d+)\/logo/)?.[1];
  expect(projectId).toBeTruthy();

  // The change handler's reload() only fires after its own fetch() resolves; page.waitForNavigation()
  // does not reliably detect this same-URL JS-triggered reload, so wait for the actual upload
  // response and re-fetch the logo directly instead of relying on a page reload at all.
  const uploadedBuffer = fs.readFileSync(filePath);
  const [uploadResponse] = await Promise.all([
    page.waitForResponse((res) => res.url().includes('/logo') && res.request().method() === 'POST'),
    page.setInputFiles('#logoPath', { name: 'logo.png', mimeType: 'image/png', buffer: uploadedBuffer }),
  ]);
  expect(uploadResponse.ok()).toBeTruthy();

  const after = await page.request.get(`/projects/${projectId}/logo`);
  expect(after.ok()).toBeTruthy();
  const afterBuffer = await after.body();

  // Comparing against a "before" snapshot is unreliable on a re-run against the same project --
  // this fixed fixture PNG may already be what a prior run left as the logo, making before/after
  // byte lengths coincidentally equal even though the upload worked. Asserting the served bytes
  // exactly match what was just uploaded is correct regardless of prior state.
  expect(afterBuffer.equals(uploadedBuffer)).toBe(true);

  fs.rmSync(dir, { recursive: true, force: true });
});
