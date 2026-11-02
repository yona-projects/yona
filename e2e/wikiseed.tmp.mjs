// 이미 심어 둔 고정 데이터에 위키만 더한다(임시). .state를 지우지 않고 위키 화면을 확인하려는 용도이다.
import { join } from 'node:path';
import { readFileSync, writeFileSync } from 'node:fs';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seedWiki } from './seed.mjs';
const FIXTURE = join(ROOT, '.state', 'fixture.json');
const fx = JSON.parse(readFileSync(FIXTURE, 'utf8'));
const B = await startServer({ port: 19292, dataDir: join(ROOT, '.state', 'server') });
try {
  const admin = new Session(B.base); await admin.login(ADMIN.loginId); await admin.deviceToken('repo read:org write:org admin:org site_admin');
  // 예전 고정 데이터의 Subversion 프로젝트도 위키를 켠다(위키가 없는 화면 확인용)
  await admin.api('PATCH', `/owners/${fx.project.svn.owner}/projects/${fx.project.svn.name}`, { menus: { wiki: true } });
  await seedWiki(admin, `/owners/${fx.project.owner}/projects/${fx.project.name}`, fx);
  writeFileSync(FIXTURE, JSON.stringify(fx, null, 2));
  console.log('wikiRevisions', fx.wikiRevisions);
} finally { await B.stop(); }
