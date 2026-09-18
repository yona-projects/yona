import { test, expect } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed, writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screens: GET /{owner}/{projectName}/newFork (project/fork.html) -> POST .../fork ->
 * pullrequest/clone.html (interstitial, JS waits 3s then calls /api/.../doClone via AJAX and
 * navigates to the result).
 *
 * ProjectService.forkProject() (backed by JGit) throws on a zero-commit source repo (no HEAD to
 * clone) -- doClone's catch-all swallows that and redirects back to the original project's
 * /pulls instead. This spec runs before 07-pull-request's own git setup, so the seeded project
 * has no commits yet at this point -- push one directly (same pattern as
 * 07-pull-request/00-pull-request-git-setup.spec.ts) so forking has something real to clone. */

const BASE_URL = process.env.YONA_BASE_URL ?? 'http://localhost:8080';

test('fork the seeded Git project under a new name', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const adminLoginId = requireSeed('adminLoginId');
  const adminPassword = requireSeed('adminPassword');
  const forkName = `e2e-fork-${uniqueSuffix()}`;

  const url = new URL(BASE_URL);
  const cloneUrl = `${url.protocol}//${encodeURIComponent(adminLoginId)}:${encodeURIComponent(adminPassword)}@${url.host}/git/${owner}/${name}`;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-fork-setup-'));
  const run = (args: string[]) => execFileSync('git', args, { cwd: workDir, stdio: 'pipe' });
  run(['clone', cloneUrl, '.']);
  run(['config', 'user.email', 'admin@yona-e2e.test']);
  run(['config', 'user.name', 'E2E Admin']);
  // Content must differ from 07-pull-request/00-pull-request-git-setup.spec.ts's own commit
  // (which runs later against this same project) -- an identical README.md content would leave
  // nothing staged to commit (`git commit` fails with "nothing to commit") since that spec
  // clones the state this one already pushed.
  fs.writeFileSync(path.join(workDir, 'README.md'), `# E2E test repo (fork setup ${forkName})\n`);
  run(['add', 'README.md']);
  run(['commit', '-m', 'E2E: initial commit for fork test']);
  run(['branch', '-M', 'main']);
  run(['push', '--force', 'origin', 'main']);
  fs.rmSync(workDir, { recursive: true, force: true });

  await page.goto(`/${owner}/${name}/newFork`);
  await page.fill('form:has(#inputName) #inputName', forkName);
  await page.click('form:has(#inputName) button[type=submit]');

  // POST response renders the "cloning..." interstitial at the same URL (template
  // pullrequest/clone.html) BEFORE its JS's 3s setTimeout fires doClone() -- snapshot the
  // interstitial's own content (progress legend naming the real owner/name/forkName, the two
  // "this may take a while / redirects automatically" messages) while it's still up, then let the
  // redirect proceed.
  await expect(page.locator('.content-wrap.frm-wrap legend')).toContainText(name);
  await expect(page.locator('.content-wrap.frm-wrap legend')).toContainText(forkName);
  await expect(page.locator('.content-wrap.frm-wrap')).toContainText(/take a long time|시간이 걸릴 수 있습니다/i);

  await page.waitForURL(new RegExp(`/${owner}/${forkName}$`), { timeout: 15_000 });

  writeSeed({ forkedProjectOwner: owner, forkedProjectName: forkName });
});
