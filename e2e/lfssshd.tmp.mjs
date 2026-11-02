// D-73 확인용(한 번 쓰고 버리는 스크립트): 진짜 OpenSSH 8.0 sshd(AlmaLinux 8 컨테이너)가 Go 도우미 `yona-ssh`로 Yona에 연결되는지 확인한다.
import { join } from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync, chmodSync, existsSync, readFileSync } from 'node:fs';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';

const sh = (cmd, args, opt = {}) => { const r = spawnSync(cmd, args, { encoding: 'utf8', ...opt }); return { code: r.status, out: (r.stdout || '') + (r.stderr || '') }; };
const must = (cmd, args, opt) => { const r = sh(cmd, args, opt); if (r.code !== 0) throw new Error(`${cmd} ${args.join(' ')}\n${r.out}`); return r.out; };
const state = join(ROOT, '.state', 'sshdgo'); rmSync(state, { recursive: true, force: true });
const sockDir = '/tmp/yssh'; rmSync(sockDir, { recursive: true, force: true }); mkdirSync(sockDir, { recursive: true });
const secret = 'sshd-go-secret-' + Math.random().toString(36).slice(2);
const bin = join(state, 'yona-ssh'); mkdirSync(state, { recursive: true });
must('go', ['build', '-o', bin, '.'], { cwd: join(ROOT, '..', 'tools', 'ssh-helper'), env: { ...process.env, CGO_ENABLED: '0' } });
const PORT = 19399, SSHD = 2299, NAME = 'yona-sshd-check';
let status = 'ok'; const fail = (m) => { console.log('FAIL', m); status = 'fail'; }; const ok = (m) => console.log('ok  ', m);
const B = await startServer({ port: PORT, dataDir: join(state, 'data'), extraArgs: [`--yona.hostname=127.0.0.1`, `--yona.port=${PORT}`, `--yona.ssh.relay.enabled=true`, `--yona.ssh.relay.socket-path=${sockDir}/relay.sock`, `--yona.ssh.relay.internal-secret=${secret}`, '--yona.ssh.user=git'] });
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); await s.deviceToken('repo read:ssh_key write:ssh_key');
  await s.api('POST', '/user/projects', { name: 'assets', description: 'sshd 확인', scope: 'PRIVATE', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
  const keys = join(state, 'keys'); mkdirSync(keys, { recursive: true });
  must('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-C', 'good', '-f', join(keys, 'good')]); must('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-C', 'bad', '-f', join(keys, 'bad')]);
  const pub = (await import('node:fs')).readFileSync(join(keys, 'good.pub'), 'utf8').trim();
  await s.api('POST', '/user/ssh_keys', { title: 'good', publicKey: pub }, { ok: [200, 201] });

  // 컨테이너: 호스트 네트워크(내부 인증 주소가 루프백 요청만 받는다), 소켓 폴더를 마운트한다
  sh('docker', ['rm', '-f', NAME]);
  must('docker', ['run', '-d', '--name', NAME, '--network', 'host', '-v', `${sockDir}:${sockDir}`, 'almalinux:8', 'sleep', 'infinity']);
  const ex = (script) => must('docker', ['exec', NAME, 'bash', '-ec', script]);
  ex('dnf -y -q install openssh-server git >/dev/null 2>&1; ssh-keygen -A >/dev/null; sshd -V 2>&1 | head -1 || true');
  ex(`groupadd -g 1000 hostg || true; useradd --system --shell /bin/bash --home-dir /home/git --create-home git; usermod -aG hostg git;
      groupadd --system yona-ssh-hook; useradd --system --no-create-home --shell /sbin/nologin --gid yona-ssh-hook yona-ssh-hook;
      mkdir -p /etc/yona; printf '%s' '${secret}' > /etc/yona/ssh-internal-secret; chown root:yona-ssh-hook /etc/yona/ssh-internal-secret; chmod 640 /etc/yona/ssh-internal-secret;
      printf 'YONA_INTERNAL_URL=http://127.0.0.1:${PORT}\\nYONA_SSH_RELAY_SOCKET=${sockDir}/relay.sock\\n' > /etc/yona/ssh-auth.conf`);
  must('docker', ['cp', bin, `${NAME}:/usr/local/bin/yona-ssh`]);
  ex(`chown root:root /usr/local/bin/yona-ssh; chmod 755 /usr/local/bin/yona-ssh;
      printf 'Match User git\\n    AuthorizedKeysCommand /usr/local/bin/yona-ssh auth %%t %%k\\n    AuthorizedKeysCommandUser yona-ssh-hook\\n' >> /etc/ssh/sshd_config;
      sed -i 's/^#\\?Port .*/Port ${SSHD}/' /etc/ssh/sshd_config; sshd -t`);
  console.log(ex('sshd -V 2>&1 | head -1; rpm -q openssh-server').trim());
  must('docker', ['exec', '-d', NAME, '/usr/sbin/sshd', '-D', '-e']);
  await new Promise((r) => setTimeout(r, 1500));

  const ssh = (key, ...args) => sh('ssh', ['-p', String(SSHD), '-i', join(keys, key), '-o', 'IdentitiesOnly=yes', '-o', 'StrictHostKeyChecking=no', '-o', 'UserKnownHostsFile=/dev/null', '-o', 'BatchMode=yes', '-o', 'LogLevel=ERROR', ...args]);
  const gitEnv = (key) => ({ ...process.env, GIT_TERMINAL_PROMPT: '0', GIT_SSH_COMMAND: `ssh -p ${SSHD} -i ${join(keys, key)} -o IdentitiesOnly=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes -o LogLevel=ERROR` });

  const BIN = '/tmp/claude-1000/-home-jiho-yona-convert-yona/572b44fd-d452-40b9-ac89-25d34813d6ac/scratchpad/git-lfs-bin/git-lfs-3.8.1';
  const home = join(state, 'home'); mkdirSync(home, { recursive: true });
  const env = { ...gitEnv('good'), PATH: `${BIN}:${process.env.PATH}`, HOME: home };
  const run = (dir, ...a) => { console.log('>', a.join(' ')); const r = sh(a[0], a.slice(1), { cwd: dir, env }); return r; };
  const url = 'ssh://git@127.0.0.1:' + SSHD + '/admin/assets.git';
  const auth = ssh('good', 'git@127.0.0.1', 'git-lfs-authenticate', "'admin/assets.git'", 'upload'); console.log('authenticate:', auth.code, auth.out.trim().slice(0, 300));
  if (auth.out.includes('Bearer yona_lfs_')) ok('실제 sshd를 지나 git-lfs-authenticate가 토큰을 준다'); else fail('authenticate 실패');
  const work = join(state, 'work'); mkdirSync(work, { recursive: true });
  const a = join(work, 'a'); mkdirSync(a); run(a, 'git', 'init', '-q', '-b', 'master'); run(a, 'git', 'config', 'user.email', 'a@e.com'); run(a, 'git', 'config', 'user.name', 'admin'); run(a, 'git', 'remote', 'add', 'origin', url);
  run(a, 'git', 'lfs', 'install', '--local'); run(a, 'git', 'lfs', 'track', '*.bin');
  const big = (await import('node:crypto')).randomBytes(4 * 1024 * 1024); writeFileSync(join(a, 'big.bin'), big); run(a, 'git', 'add', '.'); run(a, 'git', 'commit', '-q', '-m', 'lfs');
  const push = run(a, 'git', 'push', 'origin', 'master'); console.log('push:', push.code, push.out.trim().slice(0, 400)); if (push.code === 0) ok('sshd 경유 push(LFS 업로드)'); else fail('push 실패');
  const oid = (await import('node:crypto')).createHash('sha256').update(big).digest('hex');
  if (existsSync(join(state, 'data/repo/lfs/admin/assets/objects', oid.slice(0, 2), oid.slice(2, 4), oid))) ok('서버에 LFS 객체가 저장됐다'); else fail('객체 없음');
  run(a, 'git', 'lfs', 'install', '--skip-repo');
  const c = join(work, 'c'); const clone = run(work, 'git', 'clone', url, c); console.log('clone:', clone.code, clone.out.trim().slice(0, 300));
  const got = existsSync(join(c, 'big.bin')) ? readFileSync(join(c, 'big.bin')) : null;
  if (got && (await import('node:crypto')).createHash('sha256').update(got).digest('hex') === oid) ok('sshd 경유 clone(LFS 내려받기), 해시 일치'); else fail('내려받은 파일이 다르다');
  const lock = run(a, 'git', 'lfs', 'lock', 'big.bin'); if (lock.out.includes('Locked')) ok('sshd 경유 lock'); else fail('lock: ' + lock.out.slice(0, 200));
  const unlock = run(a, 'git', 'lfs', 'unlock', 'big.bin'); if (unlock.out.includes('Unlocked')) ok('unlock'); else fail('unlock: ' + unlock.out.slice(0, 200));
  const bad = sh('ssh', ['-p', String(SSHD), '-i', join(keys, 'bad'), '-o', 'IdentitiesOnly=yes', '-o', 'StrictHostKeyChecking=no', '-o', 'UserKnownHostsFile=/dev/null', '-o', 'BatchMode=yes', '-o', 'LogLevel=ERROR', 'git@127.0.0.1', 'git-lfs-authenticate', "'admin/assets.git'", 'upload']);
  if (bad.code !== 0 && /Permission denied/.test(bad.out)) ok('미등록 키는 git-lfs-authenticate도 거부'); else fail('미등록 키 결과: ' + bad.out);
} catch (e) { fail(String(e?.stack || e)); } finally { sh('docker', ['rm', '-f', NAME]); await B.stop(); rmSync(sockDir, { recursive: true, force: true }); }
console.log('RESULT', status);
