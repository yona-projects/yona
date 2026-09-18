import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screen: GET /{owner}/{projectName}/issueform (issue/create.html) -- the <yona-attachments>
 * custom element (a Vue 3 SFC compiled to a native custom element,
 * static/lib/yona-vue-widgets/yona-attachments-element.js) that owns the drop zone / file input /
 * uploaded-file list. AttachmentController.uploadFile (POST /files) is the endpoint it calls.
 *
 * The element renders a real <input type="file"> inside it (fileInputRef in the compiled source);
 * Playwright's locator engine pierces open shadow roots for plain CSS selectors, so no special
 * shadow-DOM handling is needed to reach it.
 */
test('uploading a file through the issue create form attachment widget', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-attach-'));
  const fileName = `e2e-attachment-${uniqueSuffix()}.txt`;
  const filePath = path.join(dir, fileName);
  fs.writeFileSync(filePath, 'attachment content written by the e2e suite\n');

  await page.goto(`/${owner}/${name}/issueform`);
  const widget = page.locator('yona-attachments#upload');
  await expect(widget).toBeAttached();

  await widget.locator('input[type="file"]').setInputFiles(filePath);

  // Upload happens immediately on file selection (fetch POST /files), independent of the issue
  // form's own submit -- wait for the widget's own "complete" state rather than submitting the form.
  const uploadedEntry = widget.locator('.attached-file.complete', { hasText: fileName });
  await expect(uploadedEntry).toBeVisible({ timeout: 15_000 });

  fs.rmSync(dir, { recursive: true, force: true });
});

test('deleting an uploaded attachment removes it from the widget', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-attach-'));
  const fileName = `e2e-attachment-delete-${uniqueSuffix()}.txt`;
  const filePath = path.join(dir, fileName);
  fs.writeFileSync(filePath, 'attachment content written by the e2e suite (delete test)\n');

  await page.goto(`/${owner}/${name}/issueform`);
  const widget = page.locator('yona-attachments#upload');
  await widget.locator('input[type="file"]').setInputFiles(filePath);

  const uploadedEntry = widget.locator('.attached-file.complete', { hasText: fileName });
  await expect(uploadedEntry).toBeVisible({ timeout: 15_000 });

  // AttachmentController.deleteFile is POST /files/{id} (not DELETE -- the widget's compiled JS
  // POSTs with a `_method=delete` body param, matching this codebase's other legacy-style
  // method-override endpoints). The delete trigger is `.btn-delete` inside the file's own row.
  const deleteButton = uploadedEntry.locator('.btn-delete');
  await expect(deleteButton).toBeVisible();
  await Promise.all([
    page.waitForResponse((res) => res.request().method() === 'POST' && /\/files\/\d+$/.test(new URL(res.url()).pathname)),
    deleteButton.click(),
  ]);
  await expect(uploadedEntry).toHaveCount(0);

  fs.rmSync(dir, { recursive: true, force: true });
});
