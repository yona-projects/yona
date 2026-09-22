import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: the "Download ZIP" button on code/view.html (CodeViewController.download(), GET
 * /{owner}/{projectName}/code/download/{branch}). Note: ProjectViewController also maps a
 * near-identical GET /{owner}/{projectName}/code/{branch}/download -- a second, differently-shaped
 * route implementing the same archive-download feature. Both work; the UI button only ever links
 * to the CodeViewController one, so that's what this test drives. */

test('clicking "Download ZIP" on the code browser actually downloads a real zip archive', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/code`);
  const downloadLink = page.locator(`a[href="/${owner}/${name}/code/download/main"]`);
  await expect(downloadLink).toBeVisible();

  const href = await downloadLink.getAttribute('href');
  const response = await page.request.get(href!);
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('application/zip');
  expect(response.headers()['content-disposition']).toContain('attachment');

  const body = await response.body();
  // ZIP local-file-header magic bytes ("PK\x03\x04") -- confirms this is a real archive, not an
  // empty or error payload mislabeled with a zip content-type.
  expect(body.subarray(0, 4).toString('hex')).toBe('504b0304');
  expect(body.length).toBeGreaterThan(100);
});

// FIXED (was: PRODUCT BUG -- CodeViewController.download() had no branch-existence check before
// calling repository.getArchive(), unlike its near-duplicate sibling route
// (ProjectViewController.kt's /{owner}/{projectName}/code/{branch}/download, which already 404s
// via getMetaDataFromAncestorDirectories() first). A nonexistent branch used to come back 200 +
// application/zip + a 0-byte body. Fixed by adding the same existence check to
// CodeViewController.download() before it starts building the archive.
test('downloading a non-existent branch 404s cleanly instead of streaming a broken empty archive', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.request.get(`/${owner}/${name}/code/download/no-such-branch-e2e`);
  expect(response.status()).toBe(404);
});
