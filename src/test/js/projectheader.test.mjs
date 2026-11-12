import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 프로젝트 머리가 GitHub, GitLab처럼 얇은 한 줄인지 시험한다(A-483). 컴파일된 실제 CSS(`./gradlew compileSass`가 만드는 build/generated/sass)로 잰다.
// 예전에는 120px 배너 위에 로고가 튀어나오고 상단 바가 그 위에 반투명으로 겹쳤다. 지금은 상단 바 아래에 흰 바탕의 한 줄이다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');

const PAGE = `<div id="main" class="main">
  <header class="gnb-outer project-header"><div class="gnb-inner"><ul class="gnb-nav"><li><a href="/" class="logo logo-letter">Y</a></li></ul></div></header>
  <div class="project-header-outer"><div class="project-header-inner"><div class="project-header-wrap">
    <div class="project-header-avatar"><img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" /></div>
    <div class="project-breadcrumb-wrap"><div class="project-breadcrumb">
      <span class="project-author"><a href="/a">admin</a></span><span class="project-separator">/</span><span class="project-name"><a href="/a/b">demo</a></span>
      <span class="user-project-list"><i class="star material-icons">star</i></span></div></div>
    <div class="project-util-wrap"><ul class="project-util"><li><div class="btn-group dropdown watch-btn"><a class="btn btn-sm btn-success watcher-count" href="#">1</a><button class="btn btn-sm btn-light" type="button">지켜보기</button></div></li></ul></div>
  </div></div></div>
  <div class="page-wrap-outer"><div class="project-page-wrap shell shell-below-banner">
    <nav class="shell-sidebar offcanvas-lg offcanvas-start"><div class="offcanvas-body"><div class="shell-sidebar-title d-none d-lg-block">admin/demo</div><ul class="nav"><li class="nav-item"><a class="nav-link" href="/a">홈</a></li></ul></div></nav>
    <div class="shell-main"><p>본문</p></div>
  </div></div></div>`;

async function open(width) {
  const page = await pageWith({ html: PAGE, stubs: false });
  await page.setViewportSize({ width, height: 800 });
  for (const css of ['bootstrap.css', 'usermenu.css', 'yobi.css']) await page.addStyleTag({ path: path.join(CSS, css) });
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  return page;
}

const box = (page, selector) => page.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { left: r.left, right: r.right, top: r.top, bottom: r.bottom, width: r.width, height: r.height, cy: r.top + r.height / 2 }; }, selector);
const style = (page, selector, props) => page.evaluate(([s, p]) => { const cs = getComputedStyle(document.querySelector(s)); return Object.fromEntries(p.map((k) => [k, cs[k]])); }, [selector, props]);

export const tests = [
  { name: '머리는 배경 이미지 없는 흰 바탕의 얇은 줄이다(72px 이하)', async run() {
    const page = await open(1280);
    const header = await box(page, '.project-header-outer');
    assert.ok(header.height <= 72, `머리 높이 ${header.height}`);
    const st = await style(page, '.project-header-outer', ['backgroundColor', 'backgroundImage']);
    assert.equal(st.backgroundImage, 'none'); assert.notEqual(st.backgroundColor, 'rgba(0, 0, 0, 0)');
    assert.equal(st.backgroundColor, 'rgb(255, 255, 255)');
  } },
  { name: '상단 바는 머리 위에 겹치지 않고 위쪽 줄에 놓인다(불투명한 단색)', async run() {
    const page = await open(1280);
    const bar = await box(page, '.gnb-outer'); const header = await box(page, '.project-header-outer');
    assert.ok(header.top >= bar.bottom - 1, `머리 위 ${header.top}, 상단 바 아래 ${bar.bottom}`);
    const st = await style(page, '.gnb-outer', ['position', 'backgroundColor']);
    assert.notEqual(st.position, 'absolute'); assert.equal(st.backgroundColor, 'rgb(27, 27, 27)');
  } },
  { name: '아바타는 작은 정사각형이고 흐름 안에 있다(배너 아래로 튀어나오지 않는다)', async run() {
    const page = await open(1280);
    const avatar = await box(page, '.project-header-avatar'); const header = await box(page, '.project-header-outer');
    assert.ok(avatar.width <= 48 && Math.abs(avatar.width - avatar.height) <= 1, `아바타 ${avatar.width}x${avatar.height}`);
    assert.ok(avatar.bottom <= header.bottom + 0.5, '아바타가 머리 밖으로 나가면 안 된다');
    assert.equal((await style(page, '.project-header-avatar', ['position'])).position, 'static');
  } },
  { name: '이름 줄과 동작 단추가 같은 가운데 선에 놓이고 단추는 오른쪽 끝에 있다', async run() {
    const page = await open(1280);
    const crumb = await box(page, '.project-breadcrumb'); const util = await box(page, '.project-util-wrap');
    assert.ok(Math.abs(crumb.cy - util.cy) <= 2, `이름 ${crumb.cy}, 단추 ${util.cy}`);
    const header = await box(page, '.project-header-outer');
    assert.ok(header.right - util.right <= 24, `단추 오른쪽 끝 ${util.right}, 머리 오른쪽 ${header.right}`);
  } },
  { name: '머리 줄과 사이드바 사이에 틈이 없다(위 여백 0)', async run() {
    const page = await open(1280);
    const pad = parseFloat((await style(page, '.shell-below-banner', ['paddingTop'])).paddingTop);
    assert.equal(pad, 0, `위쪽 여백 ${pad}`);
    const header = await box(page, '.project-header-outer'); const side = await box(page, 'nav.shell-sidebar');
    assert.ok(Math.abs(side.top - header.bottom) <= 1, `머리 아래 ${header.bottom}, 사이드바 위 ${side.top}`);
  } },
  { name: '폰 폭(420)에서도 가로로 넘치지 않고 이름과 단추가 겹치지 않는다', async run() {
    const page = await open(420);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth), false);
    const crumb = await box(page, '.project-breadcrumb-wrap'); const util = await box(page, '.project-util-wrap');
    const overlap = crumb.left < util.right && util.left < crumb.right && crumb.top < util.bottom && util.top < crumb.bottom;
    assert.equal(overlap, false, `이름 [${crumb.left},${crumb.right}], 단추 [${util.left},${util.right}]`);
  } },
  { name: '머리에 같은 이름이 있으면 사이드바 머리글(프로젝트 이름)은 숨긴다(A-487)', async run() {
    const page = await open(1280);
    assert.equal(await page.isVisible('.shell-sidebar-title'), false, '머리줄이 이름을 보이므로 사이드바에서 되풀이하지 않는다');
  } },
  { name: '프로젝트 홈의 머리(has-banner)는 높이를 키우지 않은 얇은 줄 그대로 배경 이미지와 어두운 그라데이션을 깔고 이름과 작성자를 흰 글자로 보인다', run: async () => {
    const page = await open(1280);
    await page.evaluate(() => { const h = document.querySelector('.project-header-outer'); h.classList.add('has-banner'); h.style.backgroundImage = 'url(data:image/gif;base64,R0lGODlhAQABAAAAACw=)'; });
    const r = await page.evaluate(() => { const h = document.querySelector('.project-header-outer'); const c = (sel) => getComputedStyle(document.querySelector(sel)).color; const inner = getComputedStyle(h.querySelector('.project-header-inner')); return { h: Math.round(h.getBoundingClientRect().height), size: getComputedStyle(h).backgroundSize, grad: inner.backgroundImage.startsWith('linear-gradient'), name: c('.project-name a'), author: c('.project-author a'), sep: c('.project-separator') }; });
    assert.ok(r.h < 80, `머리 높이 ${r.h}px: 얇은 줄 그대로여야 한다`); assert.equal(r.size, 'cover'); assert.equal(r.grad, true, '어두운 그라데이션');
    for (const k of ['name', 'author', 'sep']) assert.ok(r[k] === 'rgb(255, 255, 255)' || /^rgba?\(2[2-5]\d, 2[2-5]\d, 2[2-5]\d/.test(r[k]), `${k} 글자색 ${r[k]}가 밝지 않다`);
    await page.context().close();
  } },
  { name: 'has-banner가 없는 머리는 지금처럼 흰 바탕의 얇은 줄이다', run: async () => {
    const page = await open(1280);
    const r = await page.evaluate(() => { const h = document.querySelector('.project-header-outer'); return { h: Math.round(h.getBoundingClientRect().height), bg: getComputedStyle(h).backgroundColor, name: getComputedStyle(document.querySelector('.project-name a')).color }; });
    assert.ok(r.h < 80, `머리 높이 ${r.h}px`); assert.equal(r.bg, 'rgb(255, 255, 255)'); assert.equal(r.name, 'rgb(33, 37, 41)');
    await page.context().close();
  } },
  { name: '머리 줄 뒤에 script 같은 요소가 끼어 있어도(조직 화면) 사이드바와의 틈이 없다', async run() {
    const page = await open(1280);
    await page.evaluate(() => { const h = document.querySelector('.project-header-outer'); h.insertAdjacentHTML('afterend', '<script type="text/plain"></script>'); });
    const header = await box(page, '.project-header-outer'); const side = await box(page, 'nav.shell-sidebar');
    assert.ok(Math.abs(side.top - header.bottom) <= 1, `머리 아래 ${header.bottom}, 사이드바 위 ${side.top}`);
  } },
];
