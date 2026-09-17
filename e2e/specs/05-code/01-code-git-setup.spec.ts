import { test } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed, writeSeed } from '../../support/seed-store';

/**
 * Diff/commit/compare views are meaningless against a zero-commit repo, so this pushes two real,
 * distinct commits onto the seeded GIT project's `main` branch -- same real-`git`-CLI-shellout
 * pattern as 07-pull-request/00-pull-request-git-setup.spec.ts (Playwright's browser can't produce
 * a commit). Runs before 07-pull-request's own git-setup (folder execution order is 05 before 07),
 * so it force-pushes its own history first; 07's setup later force-pushes a *different*,
 * unrelated single-commit history onto the same `main` -- harmless here since this file's specs
 * (00-/01- in this folder, and code-diff.spec.ts) all run to completion before 07 ever starts.
 *
 * Content/commit messages are deliberately distinct from 07-pull-request's and
 * 04-project/project-fork.spec.ts's own git-setups to avoid an empty "nothing to commit" push --
 * a real failure mode hit earlier in this suite's development when two specs pushed identical
 * content to the same project.
 */
const BASE_URL = process.env.YONA_BASE_URL ?? 'http://localhost:8080';

test('push two distinct commits into the seeded git project for diff/compare tests', async () => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const adminLoginId = requireSeed('adminLoginId');
  const adminPassword = requireSeed('adminPassword');

  const url = new URL(BASE_URL);
  const cloneUrl = `${url.protocol}//${encodeURIComponent(adminLoginId)}:${encodeURIComponent(adminPassword)}@${url.host}/git/${owner}/${name}`;

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-code-diff-'));
  const run = (args: string[]) => execFileSync('git', args, { cwd: workDir, stdio: 'pipe' });

  run(['clone', cloneUrl, '.']);
  run(['config', 'user.email', 'admin@yona-e2e.test']);
  run(['config', 'user.name', 'E2E Admin']);

  const filePath = path.join(workDir, 'DIFF_FIXTURE.md');
  fs.writeFileSync(filePath, '# Diff fixture\n\nline one\nline two\nline three\n');
  run(['add', 'DIFF_FIXTURE.md']);
  run(['commit', '-m', 'E2E code-diff: add fixture file']);
  run(['branch', '-M', 'main']);
  run(['push', '--force', 'origin', 'main']);
  const firstCommit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: workDir }).toString().trim();

  fs.writeFileSync(filePath, '# Diff fixture\n\nline one\nline two MODIFIED\nline three\nline four (added)\n');
  run(['add', 'DIFF_FIXTURE.md']);
  run(['commit', '-m', 'E2E code-diff: modify fixture file']);
  run(['push', '--force', 'origin', 'main']);
  const secondCommit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: workDir }).toString().trim();

  fs.rmSync(workDir, { recursive: true, force: true });
  writeSeed({ codeDiffFirstCommit: firstCommit, codeDiffSecondCommit: secondCommit });
});
