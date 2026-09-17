import { test, expect } from '@playwright/test';
import { requireSeed, writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screens: GET /{owner}/{projectName}/milestone/new (milestone/create.html) ->
 * GET /{owner}/{projectName}/milestone/{id} (milestone/view.html) ->
 * GET /{owner}/{projectName}/milestone/{id}/editform (milestone/edit.html) ->
 * GET /{owner}/{projectName}/milestones (milestone/list.html). */

// PRODUCT BEHAVIOR (real, not a bug): milestone titles must be unique per project
// (MilestoneViewController rejects a duplicate title with "This milestone title already
// exists."). This spec used to hardcode 'E2E seed milestone' with no suffix -- unlike every
// other entity in this suite (projects/orgs/issues all use uniqueSuffix()) -- which broke the
// very first time this file was re-run against the same persistent dev DB without a fresh
// H2 reset (confirmed live: repeated local runs during this session's debugging eventually hit
// the duplicate-title rejection deterministically). Suffixing it the same way as everything
// else fixes that.
const milestoneTitle = `E2E seed milestone ${uniqueSuffix()}`;

test.describe.serial('milestone lifecycle', () => {
  test('create a milestone with title, due date and description', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/milestone/new`);
    await page.fill('#title', milestoneTitle);
    await page.fill('#dueDate', '2099-12-31');
    // markdownEditor('contents', ..., 'content-body') hides the real <textarea name="contents">
    // behind its own widget. Unlike issue/wiki/board's create forms, milestone.Write.js's
    // _validateForm() blocks the actual submit client-side unless this field is non-empty --
    // and empirically, `.fill(..., {force:true})` on this specific textarea does not stick (the
    // value reads back empty immediately after), while a direct property set + dispatched
    // input/change events does. Root cause not fully isolated; this is the verified workaround.
    await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'Milestone description written by the e2e suite.';
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.check('#milestone-open');
    await page.click('#milestone-form button[type=submit]');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/milestone/\\d+`));
    const match = page.url().match(/\/milestone\/(\d+)/);
    const milestoneId = Number(match?.[1]);
    expect(milestoneId).toBeGreaterThan(0);
    writeSeed({ milestoneId });
  });

  test('view the created milestone', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const milestoneId = requireSeed('milestoneId');

    await page.goto(`/${owner}/${name}/milestone/${milestoneId}`);
    await expect(page.locator('body')).toContainText(milestoneTitle);
  });

  test('edit the milestone title', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const milestoneId = requireSeed('milestoneId');

    await page.goto(`/${owner}/${name}/milestone/${milestoneId}/editform`);
    await page.fill('#title', `${milestoneTitle} (edited)`);
    await page.click('#milestone-form button[type=submit]');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/milestone/${milestoneId}`));
    await expect(page.locator('body')).toContainText(`${milestoneTitle} (edited)`);
  });

  test('milestone list shows the created milestone', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/milestones`);
    await expect(page.locator('body')).toContainText(`${milestoneTitle} (edited)`);
  });

  test('close then reopen the seeded milestone', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const milestoneId = requireSeed('milestoneId');

    // MilestoneViewController.closeMilestone/openMilestone both redirect (200, not
    // 204+Location) back to the same view page -- yona.Common.js's requestAs() treats any
    // non-204 success as "reload current page", so clicking + waiting for load is enough.
    await page.goto(`/${owner}/${name}/milestone/${milestoneId}`);
    await Promise.all([
      page.waitForLoadState('load'),
      page.click(`button[data-request-uri*="/milestone/${milestoneId}/close"]`),
    ]);
    await expect(page.locator(`button[data-request-uri*="/milestone/${milestoneId}/open"]`)).toBeVisible();

    // Reopen so later specs (and re-runs) still find it under the "open" tab -- other specs
    // reference seed.milestoneId assuming it's the one live milestone.
    await Promise.all([
      page.waitForLoadState('load'),
      page.click(`button[data-request-uri*="/milestone/${milestoneId}/open"]`),
    ]);
    await expect(page.locator(`button[data-request-uri*="/milestone/${milestoneId}/close"]`)).toBeVisible();
  });

  test('create a throwaway milestone and delete it', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    const throwawayTitle = `E2E throwaway milestone ${uniqueSuffix()} (to be deleted)`;
    await page.goto(`/${owner}/${name}/milestone/new`);
    await page.fill('#title', throwawayTitle);
    await page.fill('#dueDate', '2099-12-31');
    await page.locator('textarea[data-editor-mode="content-body"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'Throwaway milestone, deleted by its own test.';
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.check('#milestone-open');
    await page.click('#milestone-form button[type=submit]');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/milestone/\\d+`));
    const match = page.url().match(/\/milestone\/(\d+)/);
    const throwawayId = Number(match?.[1]);
    expect(throwawayId).toBeGreaterThan(0);

    // deleteMilestone responds 204 + Location: /{owner}/{name}/milestones -- requestAs()
    // follows that redirect directly (unlike close/open above, which just reload in place).
    await page.click('a[href="#deleteConfirm"]');
    await expect(page.locator('dialog#deleteConfirm')).toBeVisible();
    await Promise.all([
      page.waitForURL(new RegExp(`/${owner}/${name}/milestones$`)),
      page.click('dialog#deleteConfirm button[data-request-method="delete"]'),
    ]);
    await expect(page.locator('body')).not.toContainText(throwawayTitle);
  });
});
