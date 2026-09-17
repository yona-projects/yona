import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: GET/POST /projects/{owner}/{projectName}/webhooks (project/setting_webhook.html). */

test('register a Slack-format webhook with git push events included', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const payloadUrl = 'https://hooks.example.com/e2e-webhook';

  await page.goto(`/projects/${owner}/${name}/webhooks`);
  await page.fill('#formNewWebhook #payloadUrl', payloadUrl);
  await page.fill('#formNewWebhook #secret', 'e2e-secret');
  await page.check('#formNewWebhook input[name="webhookType"][value="DETAIL_SLACK"]');
  await page.check('#formNewWebhook #gitPush');
  await page.click('#formNewWebhook button[type=submit]');

  await expect(page).toHaveURL(new RegExp(`/projects/${owner}/${name}/webhooks$`));
  await expect(page.locator('body')).toContainText(payloadUrl);
});

test('all four webhook format options are present', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  await page.goto(`/projects/${owner}/${name}/webhooks`);
  for (const value of ['SIMPLE', 'DETAIL_SLACK', 'DETAIL_HANGOUT_CHAT', 'JSON']) {
    await expect(page.locator(`input[name="webhookType"][value="${value}"]`)).toBeAttached();
  }
});

test('delete a webhook', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const payloadUrl = 'https://hooks.example.com/e2e-webhook-delete';

  await page.goto(`/projects/${owner}/${name}/webhooks`);
  await page.fill('#formNewWebhook #payloadUrl', payloadUrl);
  await page.check('#formNewWebhook input[name="webhookType"][value="SIMPLE"]');
  await page.click('#formNewWebhook button[type=submit]');
  await expect(page.locator('body')).toContainText(payloadUrl);

  const row = page.locator('[data-webhook-id]', { hasText: payloadUrl });
  await expect(row).toBeVisible();
  // No confirm() here -- the delete button is a data-request-method="delete" fetch (yona.Common.js
  // requestAs()), which calls document.location.reload() itself once the fetch resolves. Waiting
  // for the DELETE response confirms the mutation happened server-side; calling page.reload()
  // ourselves on top of that races the app's own in-flight reload and throws
  // net::ERR_ABORTED ("frame was detached"), so navigate via page.goto() to the same URL instead
  // -- goto() correctly supersedes whatever navigation the app's own script already started.
  const [deleteResponse] = await Promise.all([
    page.waitForResponse((res) => res.request().method() === 'DELETE'),
    row.locator('button[data-request-method="delete"]').click(),
  ]);
  expect(deleteResponse.ok()).toBeTruthy();
  await page.goto(`/projects/${owner}/${name}/webhooks`);

  await expect(page.locator('[data-webhook-id]', { hasText: payloadUrl })).toHaveCount(0);
});
