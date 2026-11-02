// X-8 단계 0(한 번 쓰고 버리는 스크립트): 실제 git-lfs 3.8.1로 Yona의 LFS 서버를 확인한다.
import { join } from 'node:path';
import { rmSync, mkdtempSync, writeFileSync, readFileSync, existsSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { execFileSync, spawnSync } from 'node:child_process';
import { randomBytes, createHash } from 'node:crypto';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
const BIN = '/tmp/claude-1000/-home-jiho-yona-convert-yona/572b44fd-d452-40b9-ac89-25d34813d6ac/scratchpad/git-lfs-bin/git-lfs-3.8.1';
const state = join(ROOT, '.state', 'lfsclient'); rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19405, dataDir: state });
const env = { ...process.env, GIT_TRACE: '', GIT_CURL_VERBOSE: '1', PATH: `${BIN}:${process.env.PATH}`, GIT_TERMINAL_PROMPT: '0', HOME: mkdtempSync(join(tmpdir(), 'home-')), GIT_LFS_SKIP_SMUDGE: '' };
const sh = (dir, ...a) => { console.log('>', a.join(' ').replace(/yona_pat_\w+/, 'TOKEN')); try { return execFileSync(a[0], a.slice(1), { cwd: dir, env, stdio: 'pipe', timeout: 60000 }).toString(); } catch (e) { return 'FAIL ' + a.join(' ') + '\n' + String(e.stdout) + String(e.stderr); } };
const ok = (label, cond, extra = '') => console.log(cond ? 'OK  ' : 'FAIL', label, cond ? '' : extra);
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); const pat = await s.deviceToken('repo');
  await s.api('POST', '/user/projects', { name: 'assets', description: 'LFS', scope: 'PRIVATE', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
  const url = B.base.replace('http://', `http://admin:${pat}@`) + '/admin/assets';
  const a = mkdtempSync(join(tmpdir(), 'lfs-a-')); sh(a, 'git', 'init', '-q', '-b', 'master'); sh(a, 'git', 'config', 'user.email', 'a@example.com'); sh(a, 'git', 'config', 'user.name', 'admin');
  sh(a, 'git', 'remote', 'add', 'origin', url);
  console.log(sh(a, 'git', 'lfs', 'install', '--local').trim());
  sh(a, 'git', 'lfs', 'track', '*.bin');
  const big = randomBytes(6 * 1024 * 1024); writeFileSync(join(a, 'big.bin'), big); writeFileSync(join(a, 'small.bin'), 'hello lfs');
  sh(a, 'git', 'add', '.'); sh(a, 'git', 'commit', '-q', '-m', 'lfs files');
  sh(a, 'git', 'config', 'lfs.tustransfers', 'true');
  const ptr = sh(a, 'git', 'show', 'HEAD:big.bin'); const oid0 = /oid sha256:(\w+)/.exec(ptr)[1]; const size0 = Number(/size (\d+)/.exec(ptr)[1]);
  const authH = 'Basic ' + Buffer.from(`admin:${pat}`).toString('base64'); const lfsU = `${B.base}/admin/assets.git/info/lfs`;
  const b0 = await fetch(`${lfsU}/objects/batch`, { method: 'POST', headers: { Authorization: authH, 'Content-Type': 'application/vnd.git-lfs+json' }, body: JSON.stringify({ operation: 'upload', transfers: ['basic', 'tus'], objects: [{ oid: oid0, size: size0 }] }) }); const bj = await b0.json();
  console.log('batch transfer', bj.transfer);
  const part = big.subarray(0, 2 * 1024 * 1024);
  const p0 = await fetch(`${lfsU}/tus/${oid0}`, { method: 'PATCH', headers: { Authorization: authH, 'Tus-Resumable': '1.0.0', 'Upload-Offset': '0', 'Content-Type': 'application/offset+octet-stream' }, body: part }); console.log('partial PATCH', p0.status, p0.headers.get('upload-offset'));
  const rr = spawnSync('git', ['push', 'origin', 'master'], { cwd: a, env, encoding: 'utf8', timeout: 60000 }); console.log('EXIT', rr.status); console.log((rr.stdout + rr.stderr).slice(0, 400000));
  const stored0 = join(state, 'repo/lfs/admin/assets/objects', oid0.slice(0, 2), oid0.slice(2, 4), oid0);
  console.log('stored', existsSync(stored0) && createHash('sha256').update(readFileSync(stored0)).digest('hex') === oid0);
  await B.stop(); process.exit(0);
  const oid = createHash('sha256').update(big).digest('hex');
  const stored = join(state, 'repo/lfs/admin/assets/objects', oid.slice(0, 2), oid.slice(2, 4), oid);
  ok('서버 저장 위치에 객체가 있다', existsSync(stored), stored);
  console.log(sh(a, 'git', 'lfs', 'install', '--skip-repo').trim());
  const b = mkdtempSync(join(tmpdir(), 'lfs-b-')); const clone = sh(b, 'git', 'clone', url, 'c');
  ok('clone (내려받기)', !clone.startsWith('FAIL'), clone);
  const got = existsSync(join(b, 'c/big.bin')) ? readFileSync(join(b, 'c/big.bin')) : null;
  ok('내려받은 파일이 원본과 같다', got && createHash('sha256').update(got).digest('hex') === oid, String(got?.length));
  ok('small.bin 내용', existsSync(join(b, 'c/small.bin')) && readFileSync(join(b, 'c/small.bin'), 'utf8') === 'hello lfs');
  console.log(sh(a, 'git', 'lfs', 'ls-files').trim());
  // 잠금
  const lock = sh(a, 'git', 'lfs', 'lock', 'big.bin'); ok('lock', lock.includes('Locked'), lock);
  const list = sh(a, 'git', 'lfs', 'locks'); ok('locks 목록', list.includes('big.bin'), list);
  const verify = sh(a, 'git', 'lfs', 'locks', '--verify'); ok('locks --verify', verify.includes('big.bin') || !verify.startsWith('FAIL'), verify);
  const unlock = sh(a, 'git', 'lfs', 'unlock', 'big.bin'); ok('unlock', unlock.includes('Unlocked'), unlock);
  ok('잠금이 사라졌다', !sh(a, 'git', 'lfs', 'locks').includes('big.bin'));
  // 다시 푸시(이미 있는 객체는 올리지 않는다)
  writeFileSync(join(a, 'two.bin'), randomBytes(1024)); sh(a, 'git', 'add', '.'); sh(a, 'git', 'commit', '-q', '-m', 'two');
  ok('두 번째 push', !sh(a, 'git', 'push', 'origin', 'master').startsWith('FAIL'));
  // 이어받기: Range 요청을 직접 보내 확인한다
  const r = await fetch(`${B.base}/admin/assets.git/info/lfs/objects/${oid}`, { headers: { Authorization: 'Basic ' + Buffer.from(`admin:${pat}`).toString('base64'), Range: 'bytes=10-19' } });
  ok('Range 206', r.status === 206 && Buffer.from(await r.arrayBuffer()).equals(big.subarray(10, 20)), String(r.status));
  // 토큰 없이: 비공개 프로젝트의 clone은 실패해야 한다
  const anon = mkdtempSync(join(tmpdir(), 'lfs-c-')); const denied = sh(anon, 'git', 'clone', B.base + '/admin/assets', 'x'); ok('토큰 없는 clone은 막힌다', denied.startsWith('FAIL'), denied.slice(0, 120));
} finally { await B.stop(); }
