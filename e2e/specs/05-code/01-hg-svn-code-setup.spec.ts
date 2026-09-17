import { test } from '@playwright/test';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { requireSeed, writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/**
 * hg/svn diff and compare views can't be exercised without at least two real revisions -- neither
 * VCS can be driven from the browser, so this shells out to the real `hg`/`svn` CLIs (same
 * pattern as 07-pull-request/00-pull-request-git-setup.spec.ts's real `git` shellout) against
 * yona's wire endpoints (`/hg/{owner}/{project}` -- HgController.kt:82, `/svn/**` --
 * SvnController.kt:33), both permitAll + HTTP Basic per SecurityConfig.kt. Verified manually
 * against this suite's own running server before writing this file: `hg clone` / `svn checkout`
 * against a brand-new zero-commit project, two commits each, push/commit succeeded, and the
 * resulting /commit/{id} and /compare/{a}..{b} pages render the real diff content -- not a guess.
 *
 * A brand-new hg repo has no known remote branches yet, so the very first push must pass
 * `--new-branch` (confirmed live: without it, `hg push` aborts with "push creates new remote
 * branches: default"); `--new-branch` is a no-op on reruns once the branch is already known, so
 * it is always safe to pass.
 *
 * Reruns use a fresh clone/checkout each time (picking up whatever the server already has) and
 * write a brand-new uniquely-named file rather than editing README.md, so this is safe to run
 * repeatedly without hitting "nothing to commit" (hg) or file-already-exists (svn) errors.
 *
 * `hg config --local key value` is NOT a setter -- unlike `git config`, Mercurial's `config`
 * subcommand only reads config (or, with no value, opens $EDITOR to hand-edit hgrc); passing a
 * value as a positional arg made it launch vim non-interactively and hang forever (confirmed live
 * -- had to kill the orphaned process). `hg commit -u "..."` sets the commit user directly instead.
 */
const BASE_URL = process.env.YONA_BASE_URL ?? 'http://localhost:8080';

test('push two real hg changesets into the seeded Mercurial project', async () => {
  const owner = requireSeed('hgProjectOwner');
  const name = requireSeed('hgProjectName');
  const adminLoginId = requireSeed('adminLoginId');
  const adminPassword = requireSeed('adminPassword');

  const url = new URL(BASE_URL);
  const cloneUrl = `${url.protocol}//${encodeURIComponent(adminLoginId)}:${encodeURIComponent(adminPassword)}@${url.host}/hg/${owner}/${name}`;

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-hg-'));
  // timeout: a defensive backstop -- execFileSync blocks Node's single-threaded event loop, so if
  // a CLI subcommand ever unexpectedly waits on stdin/an editor, Playwright's own test timeout
  // can't preempt it and the process orphans forever (this bit us once already with a wrong
  // `hg config` invocation during development of this file).
  const run = (args: string[]) => execFileSync('hg', args, { cwd: workDir, stdio: 'pipe', timeout: 15_000 });

  run(['clone', cloneUrl, '.']);

  const suffix = uniqueSuffix();
  const hgUser = 'E2E Admin <admin@yona-e2e.test>';
  const fileName = `e2e-hg-note-${suffix}.md`;
  fs.writeFileSync(path.join(workDir, fileName), `hg note ${suffix} v1\n`);
  run(['add', fileName]);
  run(['commit', '-u', hgUser, '-m', `E2E: initial hg commit ${suffix}`]);
  const commitOld = run(['log', '-r', '.', '--template', '{node}']).toString();

  fs.writeFileSync(path.join(workDir, fileName), `hg note ${suffix} v2\n`);
  run(['commit', '-u', hgUser, '-m', `E2E: second hg commit ${suffix}`]);
  const commitNew = run(['log', '-r', '.', '--template', '{node}']).toString();

  run(['push', '--new-branch', cloneUrl]);

  fs.rmSync(workDir, { recursive: true, force: true });
  writeSeed({ hgCommitOld: commitOld, hgCommitNew: commitNew });
});

test('commit two real svn revisions into the seeded Subversion project', async () => {
  const owner = requireSeed('svnProjectOwner');
  const name = requireSeed('svnProjectName');
  const adminLoginId = requireSeed('adminLoginId');
  const adminPassword = requireSeed('adminPassword');

  const url = new URL(BASE_URL);
  const checkoutUrl = `${url.protocol}//${url.host}/svn/${owner}/${name}`;

  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'yona-e2e-svn-'));
  const run = (args: string[]) =>
    execFileSync(
      'svn',
      [...args, '--non-interactive', '--username', adminLoginId, '--password', adminPassword],
      { cwd: workDir, stdio: 'pipe', timeout: 15_000 }
    ).toString();

  run(['checkout', checkoutUrl, '.']);

  const suffix = uniqueSuffix();
  const fileName = `e2e-svn-note-${suffix}.md`;
  fs.writeFileSync(path.join(workDir, fileName), `svn note ${suffix} v1\n`);
  run(['add', fileName]);
  const commitOldOut = run(['commit', '-m', `E2E: initial svn commit ${suffix}`]);
  const revOld = Number(commitOldOut.match(/Committed revision (\d+)\./)?.[1]);

  fs.writeFileSync(path.join(workDir, fileName), `svn note ${suffix} v2\n`);
  const commitNewOut = run(['commit', '-m', `E2E: second svn commit ${suffix}`]);
  const revNew = Number(commitNewOut.match(/Committed revision (\d+)\./)?.[1]);

  fs.rmSync(workDir, { recursive: true, force: true });
  writeSeed({ svnRevOld: revOld, svnRevNew: revNew });
});
