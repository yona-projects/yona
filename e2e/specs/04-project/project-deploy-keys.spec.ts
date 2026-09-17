import { test, expect } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screen: GET/POST /projects/{owner}/{projectName}/deploy-keys (project/setting_deploykeys.html). */

/** A hardcoded key would collide across repeated runs (deploy key uniqueness is apparently
 * checked beyond just this one freshly-created project) -- generate a fresh keypair instead,
 * same reasoning as 02-user/user-settings.spec.ts's SSH key test. */
function generateSshPublicKey(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-deploykey-'));
  const keyPath = path.join(dir, 'id_ed25519');
  execFileSync('ssh-keygen', ['-t', 'ed25519', '-N', '', '-C', `e2e-deploy-${uniqueSuffix()}@yona`, '-f', keyPath]);
  const publicKey = fs.readFileSync(`${keyPath}.pub`, 'utf-8').trim();
  fs.rmSync(dir, { recursive: true, force: true });
  return publicKey;
}

test('register a read-only deploy key', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const title = `e2e-deploy-key-${uniqueSuffix()}`;

  await page.goto(`/projects/${owner}/${name}/deploy-keys`);
  await page.fill('#deployKeyTitle', title);
  await page.fill('#deployKeyPublicKey', generateSshPublicKey());
  await expect(page.locator('#deployKeyReadOnly')).toBeChecked();
  await page.click('#formNewDeployKey button[type=submit]');

  await expect(page).toHaveURL(new RegExp(`/projects/${owner}/${name}/deploy-keys$`));
  await expect(page.locator('body')).toContainText(title);
});

test('delete a deploy key', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const title = `e2e-deploy-key-delete-${uniqueSuffix()}`;

  await page.goto(`/projects/${owner}/${name}/deploy-keys`);
  await page.fill('#deployKeyTitle', title);
  await page.fill('#deployKeyPublicKey', generateSshPublicKey());
  await page.click('#formNewDeployKey button[type=submit]');
  await expect(page.locator('body')).toContainText(title);

  const row = page.locator('[data-deploy-key-id]', { hasText: title });
  await expect(row).toBeVisible();
  // The delete form's submit handler wraps a native confirm() (setting_deploykeys.html's inline
  // script) around form submission -- must accept it before the POST actually fires.
  page.once('dialog', (dialog) => dialog.accept());
  await row.locator('button[type=submit]').click();
  await page.waitForURL(new RegExp(`/projects/${owner}/${name}/deploy-keys$`));

  await expect(page.locator('[data-deploy-key-id]', { hasText: title })).toHaveCount(0);
});
