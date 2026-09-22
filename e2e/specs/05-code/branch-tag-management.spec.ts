import { test, expect } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/**
 * Screens: GET /{owner}/{projectName}/branches (code/branches.html) and
 * GET /{owner}/{projectName}/tags (code/tags.html) -- "set as default branch" (BranchApiController
 * .setAsDefault, POST) and "delete" (BranchApiController.deleteBranch / TagApiController.deleteTag,
 * both DELETE) buttons. These render via yona.Common.js's generic requestAs() data-API
 * ([data-request-method] + [data-request-uri], fetch-based, no native confirm() -- reloads the
 * page on a 200 response), so no dialog handling is needed here (contrast with
 * project-deploy-keys.spec.ts's delete, which does wrap a confirm()).
 *
 * Fully self-contained: shells out to real git (same pattern as
 * 07-pull-request/00-pull-request-git-setup.spec.ts) to push its own commit/branch/tag into the
 * seeded GIT project, independent of whatever other specs are doing to the same repo's history --
 * a branch/tag this suite creates is never referenced by main, so it can't collide with other
 * specs' content.
 */
const BASE_URL = process.env.YONA_BASE_URL ?? 'http://localhost:8080';

test.describe.serial('branch and tag management', () => {
  const suffix = uniqueSuffix();
  const branchName = `e2e-branch-mgmt-${suffix}`;
  const tagName = `e2e-tag-${suffix}`;

  test.beforeAll(() => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const adminLoginId = requireSeed('adminLoginId');
    const adminPassword = requireSeed('adminPassword');

    const url = new URL(BASE_URL);
    const cloneUrl = `${url.protocol}//${encodeURIComponent(adminLoginId)}:${encodeURIComponent(adminPassword)}@${url.host}/git/${owner}/${name}`;

    const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-branch-tag-'));
    const run = (args: string[]) => execFileSync('git', args, { cwd: workDir, stdio: 'pipe' });

    run(['clone', cloneUrl, '.']);
    run(['config', 'user.email', 'admin@yona-e2e.test']);
    run(['config', 'user.name', 'E2E Admin']);
    fs.writeFileSync(path.join(workDir, `BRANCH-TAG-${suffix}.md`), `# branch/tag mgmt fixture ${suffix}\n`);
    run(['add', `BRANCH-TAG-${suffix}.md`]);
    run(['commit', '-m', `E2E: branch/tag management fixture ${suffix}`]);
    run(['branch', branchName]);
    run(['push', 'origin', branchName]);
    run(['tag', tagName]);
    run(['push', 'origin', tagName]);

    fs.rmSync(workDir, { recursive: true, force: true });
  });

  test('set a non-default branch as the default branch, then restore main', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/branches`);
    const row = page.locator('tbody tr', { hasText: branchName });
    await expect(row).toBeVisible();

    // requestAs() calls document.location.reload() itself once its fetch() resolves -- calling
    // page.reload() ourselves on top of that races the app's own in-flight reload
    // (net::ERR_ABORTED; same failure mode as the webhook-delete button in
    // project-webhooks.spec.ts), so navigate via page.goto() to the same URL instead, which
    // correctly supersedes it. NOTE: setAsDefault returns "redirect:/.../branches" (a real 3xx),
    // not a 200 -- fetch() follows it internally before the app's own JS ever sees the (200) final
    // response, but Playwright's network events surface the intermediate redirect hop itself to
    // page.waitForResponse(), so assert status < 400 (covers both 2xx and 3xx) rather than
    // response.ok() (2xx only).
    const [setDefaultResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/setAsDefault') && res.request().method() === 'POST'),
      row.locator('button[data-request-uri*="/setAsDefault"]').click(),
    ]);
    expect(setDefaultResponse.status()).toBeLessThan(400);
    await page.goto(`/${owner}/${name}/branches`);

    const headRow = page.locator('tr.head');
    await expect(headRow.locator('.branchName')).toContainText(branchName);

    // Restore main as the default so later/other specs relying on the default branch are unaffected.
    const mainRow = page.locator('tbody tr', { hasText: 'main' });
    const [restoreResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/setAsDefault') && res.request().method() === 'POST'),
      mainRow.locator('button[data-request-uri*="/setAsDefault"]').click(),
    ]);
    expect(restoreResponse.status()).toBeLessThan(400);
    await page.goto(`/${owner}/${name}/branches`);
    await expect(page.locator('tr.head .branchName')).toContainText('main');
  });

  test('delete the e2e-only branch', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/branches`);
    const row = page.locator('tbody tr', { hasText: branchName });
    await expect(row).toBeVisible();

    const [deleteResponse] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'DELETE'),
      row.locator('a[data-request-method="delete"]').click(),
    ]);
    // deleteBranch also returns "redirect:/.../branches" (3xx), not 200 -- see the status < 400
    // comment above.
    expect(deleteResponse.status()).toBeLessThan(400);
    await page.goto(`/${owner}/${name}/branches`);

    await expect(page.locator('tbody tr', { hasText: branchName })).toHaveCount(0);
  });

  test('delete the e2e-only tag', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');

    await page.goto(`/${owner}/${name}/tags`);
    const row = page.locator('tbody tr', { hasText: tagName });
    await expect(row).toBeVisible();

    const [deleteResponse] = await Promise.all([
      page.waitForResponse((res) => res.request().method() === 'DELETE'),
      row.locator('a[data-request-method="delete"]').click(),
    ]);
    // deleteTag also returns "redirect:/.../tags" (3xx), not 200 -- see the status < 400 comment
    // above.
    expect(deleteResponse.status()).toBeLessThan(400);
    await page.goto(`/${owner}/${name}/tags`);

    await expect(page.locator('tbody tr', { hasText: tagName })).toHaveCount(0);
  });
});
