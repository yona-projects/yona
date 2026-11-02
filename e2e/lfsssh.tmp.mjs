// X-8 단계 4 확인용(한 번 쓰고 버리는 스크립트): 실제 git-lfs 3.8.1과 실제 ssh 클라이언트로 내장 SSH 서버의 LFS를 확인한다.
import { join } from 'node:path';
import { rmSync, mkdtempSync, writeFileSync, readFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { execFileSync } from 'node:child_process';
import { randomBytes, createHash } from 'node:crypto';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
const BIN = '/tmp/claude-1000/-home-jiho-yona-convert-yona/572b44fd-d452-40b9-ac89-25d34813d6ac/scratchpad/git-lfs-bin/git-lfs-3.8.1';
const state = join(ROOT, '.state', 'lfsssh'); rmSync(state, { recursive: true, force: true });
const SSH = 2222;
const B = await startServer({ port: 19406, dataDir: state, extraArgs: ['--yona.ssh.enabled=true', `--yona.ssh.port=${SSH}`, '--yona.hostname=localhost', '--yona.port=19406'] });
const home = mkdtempSync(join(tmpdir(), 'home-')); const keyDir = join(home, '.ssh'); mkdirSync(keyDir, { recursive: true });
execFileSync('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-f', join(keyDir, 'id')]);
const sshCmd = `ssh -i ${join(keyDir, 'id')} -o IdentitiesOnly=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR`;
const env = { ...process.env, PATH: `${BIN}:${process.env.PATH}`, GIT_TERMINAL_PROMPT: '0', HOME: home, GIT_SSH_COMMAND: sshCmd };
const sh = (dir, ...a) => { console.log('>', a.join(' ')); try { return execFileSync(a[0], a.slice(1), { cwd: dir, env, stdio: 'pipe', timeout: 60000 }).toString(); } catch (e) { return 'FAIL ' + a.join(' ') + '\n' + String(e.stdout) + String(e.stderr); } };
const ok = (label, cond, extra = '') => console.log(cond ? 'OK  ' : 'FAIL', label, cond ? '' : extra);
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); await s.deviceToken('repo read:ssh_key write:ssh_key');
  await s.api('POST', '/user/projects', { name: 'assets', description: 'LFS ssh', scope: 'PRIVATE', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
  await s.api('POST', '/user/ssh_keys', { title: 'lfs', publicKey: readFileSync(join(keyDir, 'id.pub'), 'utf8').trim() }, { ok: [200, 201] });
  console.log(sh(home, 'bash', '-c', `${sshCmd} -p ${SSH} git@localhost "git-lfs-authenticate 'admin/assets.git' download" 2>&1 | head -c 600`).trim());
  const url = `ssh://git@localhost:${SSH}/admin/assets.git`;
  const a = mkdtempSync(join(tmpdir(), 'lfs-a-')); sh(a, 'git', 'init', '-q', '-b', 'master'); sh(a, 'git', 'config', 'user.email', 'a@example.com'); sh(a, 'git', 'config', 'user.name', 'admin'); sh(a, 'git', 'remote', 'add', 'origin', url);
  sh(a, 'git', 'lfs', 'install', '--local'); sh(a, 'git', 'lfs', 'track', '*.bin');
  const big = randomBytes(3 * 1024 * 1024); writeFileSync(join(a, 'big.bin'), big); sh(a, 'git', 'add', '.'); sh(a, 'git', 'commit', '-q', '-m', 'lfs');
  const push = sh(a, 'git', 'push', 'origin', 'master'); ok('SSH push (LFS 업로드)', !push.startsWith('FAIL'), push.slice(0, 600));
  const oid = createHash('sha256').update(big).digest('hex'); ok('서버 저장', readFileSync(join(state, 'repo/lfs/admin/assets/objects', oid.slice(0, 2), oid.slice(2, 4), oid)).length === big.length);
  sh(a, 'git', 'lfs', 'install', '--skip-repo');
  const b = mkdtempSync(join(tmpdir(), 'lfs-b-')); const clone = sh(b, 'git', 'clone', url, 'c'); ok('SSH clone (LFS 내려받기)', !clone.startsWith('FAIL'), clone.slice(0, 600));
  let got = null; try { got = readFileSync(join(b, 'c/big.bin')); } catch {} ok('내려받은 파일이 같다', got && createHash('sha256').update(got).digest('hex') === oid, String(got?.length));
  const lock = sh(a, 'git', 'lfs', 'lock', 'big.bin'); ok('SSH lock', lock.includes('Locked'), lock.slice(0, 300));
  ok('SSH unlock', sh(a, 'git', 'lfs', 'unlock', 'big.bin').includes('Unlocked'));
} finally { await B.stop(); }
