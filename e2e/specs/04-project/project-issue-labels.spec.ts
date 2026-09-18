import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

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

test('the real label-creation widget (yona-new-label-form, a Vue custom element) creates a label end to end', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const category = `e2e-cat-${uniqueSuffix()}`;
  const labelName = `e2e-label-${uniqueSuffix()}`;

  await page.goto(`/${owner}/${name}/issue/labelsform`);

  // <yona-new-label-form> renders its real <form class="new-label-wrap"> as a light-DOM child
  // (Playwright's CSS engine resolves this without any shadow-piercing tricks) -- scope to it
  // specifically, since #copyLabel above shares the same "new-label-wrap" class and `input[name=
  // "name"]` also matches hidden inputs inside the page's other dialog custom elements
  // (<yona-category-edit-dialog>/<yona-label-edit-dialog>), which would otherwise violate
  // Playwright's strict mode.
  const widget = page.locator('yona-new-label-form');
  const categoryInput = widget.locator('input[name="category"]');
  const nameInput = widget.locator('input[name="name"]');

  // .fill() does not reliably trigger this widget's onFocus-driven auto-color-assignment
  // (yona-new-label-form-element.js's `ie()`, wired to the name input's real `focus` event) --
  // use a real click+type sequence instead, matching actual user interaction.
  await categoryInput.click();
  await categoryInput.type(category, { delay: 20 });
  await nameInput.click();
  await nameInput.type(labelName, { delay: 20 });
  // Confirms a color got auto-assigned from the category name (ie()'s side effect) -- required by
  // the widget's own client-side validation (ce()) before it will submit at all.
  await expect(nameInput).toHaveAttribute('style', /background-color/);

  await widget.locator('button.btn-submit').click();

  // For a brand-new category, the widget's onSubmit handler ($()) does NOT POST immediately --
  // it first shows a real confirm dialog (the page's global <yona-dialog id="yonaDialog">, shared
  // site-wide) asking whether this category should allow multiple labels or just one, via two
  // slotted <button slot="buttons"> elements. Only after picking one does it actually fetch.
  const dialog = page.locator('yona-dialog#yonaDialog');
  await expect(dialog.locator('dialog[open]')).toBeVisible();
  await expect(dialog).toContainText(category);

  const [response] = await Promise.all([
    page.waitForResponse((res) => res.request().method() === 'POST' && res.url().includes('/issue/labels')),
    dialog.locator('button[slot="buttons"]', { hasText: 'only a single label' }).click(),
  ]);
  expect(response.ok()).toBeTruthy();

  // A successful create reloads the page (yona-new-label-form-element.js's ue(): `if (n &&
  // typeof n == "object") { document.location.reload(); return; }`) -- wait for that reload, then
  // confirm the new label actually shows up in the list.
  await page.waitForLoadState('load');
  await expect(page.locator(`#labelsList span[data-label-name="${labelName}"]`)).toBeVisible();
});
