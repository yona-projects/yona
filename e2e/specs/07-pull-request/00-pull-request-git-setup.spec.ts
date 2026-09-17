import { test, expect } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed, writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/**
 * Pull requests need two divergent branches to be meaningful, and Playwright's browser can't
 * produce a git commit -- this test shells out to the real `git` CLI (same pattern as hg4j's own
 * RealHgInteropTest suite: exercise the real client against the real server, not a mock) to push
 * a `main` commit and a `feature/e2e-pr` branch into the seeded GIT project over yona's smart-HTTP
 * endpoint (`/git/*`, GitServletConfig.kt), authenticating with HTTP Basic (SecurityConfig.kt
 * wires httpBasic() for /git/**). Verified manually against this suite's own server instance
 * before writing this file (clone -> commit -> push main -> branch -> push feature/e2e-pr all
 * succeeded) -- this is not a guess.
 *
 * baseURL comes from playwright.config.ts (defaults to http://localhost:8080); this test reads
 * it from process.env directly since it never opens a page.
 */
const BASE_URL = process.env.YONA_BASE_URL ?? 'http://localhost:8080';

test('push a main commit and a feature branch into the seeded git project', async () => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const adminLoginId = requireSeed('adminLoginId');
  const adminPassword = requireSeed('adminPassword');

  const url = new URL(BASE_URL);
  const cloneUrl = `${url.protocol}//${encodeURIComponent(adminLoginId)}:${encodeURIComponent(adminPassword)}@${url.host}/git/${owner}/${name}`;

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-git-'));
  const run = (args: string[]) => execFileSync('git', args, { cwd: workDir, stdio: 'pipe' });

  // A unique-per-run marker in the content, not just --force on push: re-running this spec a
  // second time against a project whose `main` already has this exact README content (e.g. a
  // prior full-suite run against the same seeded project) left `git commit` with an empty diff
  // and nothing to commit -- reproduced live, `--force` only protects the push step, not the
  // local commit. Same fix pattern as 04-project/project-fork.spec.ts's unique fork content.
  const suffix = uniqueSuffix();

  run(['clone', cloneUrl, '.']);
  run(['config', 'user.email', 'admin@yona-e2e.test']);
  run(['config', 'user.name', 'E2E Admin']);
  fs.writeFileSync(path.join(workDir, 'README.md'), `# E2E test repo (git-setup ${suffix})\n`);
  run(['add', 'README.md']);
  run(['commit', '-m', `E2E: initial commit on main (${suffix})`]);
  run(['branch', '-M', 'main']);
  // --force: re-running this suite against a project that already has these branches (a prior
  // run, or manual verification while writing this spec) must not fail on a non-fast-forward
  // rejection -- this branch only ever exists for the e2e suite's own use, nothing else can be
  // depending on its history.
  run(['push', '--force', 'origin', 'main']);

  run(['checkout', '-b', 'feature/e2e-pr']);
  fs.writeFileSync(path.join(workDir, 'README.md'), `# E2E test repo (git-setup ${suffix})\nfeature line\n`);
  run(['add', 'README.md']);
  run(['commit', '-m', `E2E: feature branch commit (${suffix})`]);
  run(['push', '--force', 'origin', 'feature/e2e-pr']);

  fs.rmSync(workDir, { recursive: true, force: true });
  writeSeed({ pullRequestFeatureBranch: 'feature/e2e-pr' });
});
