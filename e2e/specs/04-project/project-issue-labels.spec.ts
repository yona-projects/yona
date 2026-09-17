import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET /{owner}/{projectName}/issue/labelsform (project/issuelabels.html). The actual
 * "add new label" UI is rendered entirely by a JS widget (`attachLabelListAdapter` into
 * `#labelsList`) that calls POST /{owner}/{projectName}/issue/labels via AJAX -- there is no
 * static form for it in the template, so this only covers the one real <form> on the page
 * (copy labels from another project) plus the widget mount point loading without erroring. */

test('labels screen loads with the copy-labels form and the label widget mount point', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/issue/labelsform`);
  await expect(page.locator('#copyLabel')).toBeVisible();
  await expect(page.locator('#labelsList')).toBeVisible();
});

test('copy-labels form accepts owner/projectName input', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/${owner}/${name}/issue/labelsform`);
  await page.fill('#copyLabel input[name="owner"]', owner);
  await page.fill('#copyLabel input[name="projectName"]', name);
  // Submitting would attempt to copy this project's own (empty) label set onto itself -- a
  // harmless no-op, but skipped here since the point is verifying the field exists and accepts
  // input, not exercising the copy side-effect.
});
