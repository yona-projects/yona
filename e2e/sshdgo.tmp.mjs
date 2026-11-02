// D-73 확인용(한 번 쓰고 버리는 스크립트): 진짜 OpenSSH 8.0 sshd(AlmaLinux 8 컨테이너)가 Go 도우미 `yona-ssh`로 Yona에 연결되는지 확인한다.
import { join } from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync, chmodSync, existsSync } from 'node:fs';
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
const B = await startServer({ port: PORT, dataDir: join(state, 'data'), extraArgs: [`--yona.ssh.relay.enabled=true`, `--yona.ssh.relay.socket-path=${sockDir}/relay.sock`, `--yona.ssh.relay.internal-secret=${secret}`, '--yona.ssh.user=git'] });
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); await s.deviceToken('repo read:ssh_key write:ssh_key');
  await s.api('POST', '/user/projects', { name: 'sshdemo', description: 'sshd 확인', scope: 'PRIVATE', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
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
  const url = 'git@127.0.0.1:admin/sshdemo.git';

  const hi = ssh('good', 'git@127.0.0.1'); console.log('-T:', hi.code, hi.out.trim()); if (hi.out.includes('Interactive shell access is not provided')) ok('셸을 주지 않는 안내'); else fail('안내 문구가 없다');
  const denied = ssh('bad', 'git@127.0.0.1', 'git-upload-pack', "'admin/sshdemo.git'"); if (denied.code !== 0 && /Permission denied/.test(denied.out)) ok('미등록 키 거부'); else fail('미등록 키 결과: ' + denied.out);

  const work = join(state, 'work'); mkdirSync(work, { recursive: true });
  must('git', ['init', '-q', '-b', 'master', join(work, 'seed')]); writeFileSync(join(work, 'seed', 'a.txt'), 'hello'); must('git', ['-C', join(work, 'seed'), 'add', '.']);
  must('git', ['-C', join(work, 'seed'), '-c', 'user.name=t', '-c', 'user.email=t@e.com', 'commit', '-qm', 'first']);
  const push = sh('git', ['-C', join(work, 'seed'), 'push', url, 'master:master'], { env: gitEnv('good') }); console.log('push:', push.code, push.out.trim().slice(0, 200)); if (push.code === 0) ok('푸시'); else fail('푸시 실패');
  const clone = sh('git', ['clone', '-q', url, join(work, 'clone')], { env: gitEnv('good') }); if (clone.code === 0 && existsSync(join(work, 'clone', 'a.txt'))) ok('복제'); else fail('복제 실패: ' + clone.out);
  const bad = sh('git', ['clone', '-q', url, join(work, 'clone2')], { env: gitEnv('bad') }); if (bad.code !== 0) ok('미등록 키로 복제 거부'); else fail('미등록 키로 복제됨');
  const missing = sh('git', ['clone', '-q', 'git@127.0.0.1:admin/nothing.git', join(work, 'clone3')], { env: gitEnv('good') }); if (missing.code !== 0 && /not found|Repository/i.test(missing.out)) ok('없는 저장소 메시지: ' + missing.out.trim().split('\n').pop()); else fail('없는 저장소: ' + missing.out);
  const shell = ssh('good', 'git@127.0.0.1', 'ls'); console.log('ls:', shell.code, JSON.stringify(shell.out.slice(0, 80))); if (/Invalid command/.test(shell.out) && !/\.bashrc|\.ssh/.test(shell.out)) ok('git이 아닌 명령은 실행하지 않고 오류 줄만 받는다'); else fail('ls 결과가 이상하다');
  const nl = sh('bash', ['-c', `ssh -p ${SSHD} -i ${join(keys, 'good')} -o IdentitiesOnly=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o BatchMode=yes -o LogLevel=ERROR git@127.0.0.1 $'git-upload-pack \\'admin/sshdemo.git\\'\\nX'`]); if (nl.code !== 0) ok('개행이 든 명령 거부: ' + nl.out.trim().slice(0, 80)); else fail('개행 명령 통과');
  console.log('forced command line:', ex(`runuser -u yona-ssh-hook -- /usr/local/bin/yona-ssh auth ssh-ed25519 ${pub.split(' ')[1]}`).trim().slice(0, 260));
} catch (e) { fail(String(e?.stack || e)); } finally { sh('docker', ['rm', '-f', NAME]); await B.stop(); rmSync(sockDir, { recursive: true, force: true }); }
console.log('RESULT', status);
