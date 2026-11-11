import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 프로젝트 머리가 GitHub, GitLab처럼 얇은 한 줄인지 시험한다(A-483). 컴파일된 실제 CSS(`./gradlew compileSass`가 만드는 build/generated/sass)로 잰다.
// 예전에는 120px 배너 위에 로고가 튀어나오고 상단 바가 그 위에 반투명으로 겹쳤다. 지금은 상단 바 아래에 흰 바탕의 한 줄이다.
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');

const HEADER = `<div class="project-header-outer"><div class="project-header-inner"><div class="project-header-wrap">
    <div class="project-header-avatar"><img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" /></div>
    <div class="project-breadcrumb-wrap"><div class="project-breadcrumb">
      <span class="project-author"><a href="/a">admin</a></span><span class="project-separator">/</span><span class="project-name"><a href="/a/b">demo</a></span>
      <span class="user-project-list"><i class="star material-icons">star</i></span></div></div>
    <div class="project-util-wrap"><ul class="project-util"><li><div class="btn-group dropdown watch-btn"><a class="btn btn-sm btn-success watcher-count" href="#">1</a><button class="btn btn-sm btn-light" type="button">지켜보기</button></div></li></ul></div>
  </div></div></div>`;
// A-485: 머리는 사이드바 오른쪽 본문(.shell-main)의 첫 요소이고, 그 아래에 표지가 온다. [withHeader]가 false이면 머리 없는 화면(사이트 관리 등)이다.
const markup = (withHeader = true) => `<div id="main" class="main">
  <header class="gnb-outer project-header"><div class="gnb-inner"><ul class="gnb-nav"><li><a href="/" class="logo logo-letter">Y</a></li></ul></div></header>
  <div class="page-wrap-outer"><div class="project-page-wrap shell shell-below-banner">
    <nav class="shell-sidebar offcanvas-lg offcanvas-start"><div class="offcanvas-header d-lg-none"><h2 class="offcanvas-title h5">admin/demo</h2></div><div class="offcanvas-body"><div class="shell-sidebar-title d-none d-lg-block">admin/demo</div><ul class="nav"><li class="nav-item"><a class="nav-link" href="/a">홈</a></li></ul></div></nav>
    <div class="shell-main">${withHeader ? HEADER : ''}<div class="project-cover" style="background-image:url(data:image/gif;base64,R0lGODlhAQABAAAAACw=)"></div><p>본문</p></div>
  </div></div></div>`;

async function open(width, withHeader = true) {
  const page = await pageWith({ html: markup(withHeader), stubs: false });
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
  { name: '사이드바 위 여백이 줄었다(로고가 튀어나오지 않으므로 40px가 필요 없다)', async run() {
    const page = await open(1280);
    const pad = parseFloat((await style(page, '.shell-below-banner', ['paddingTop'])).paddingTop);
    assert.ok(pad <= 16, `위쪽 여백 ${pad}`);
  } },
  { name: '프로젝트 홈 표지는 본문 폭을 채우고 이미지를 덮어 맞춘다', async run() {
    const page = await open(1280);
    const cover = await box(page, '.project-cover'); const main = await box(page, '.shell-main');
    assert.ok(Math.abs(cover.width - main.width) <= 1, `표지 폭 ${cover.width}, 본문 폭 ${main.width}`);
    assert.ok(cover.height >= 120 && cover.height <= 220, `표지 높이 ${cover.height}`);
    assert.equal((await style(page, '.project-cover', ['backgroundSize'])).backgroundSize, 'cover');
  } },
  { name: '폰 폭(420)에서도 가로로 넘치지 않고 이름과 단추가 겹치지 않는다', async run() {
    const page = await open(420);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth), false);
    const crumb = await box(page, '.project-breadcrumb-wrap'); const util = await box(page, '.project-util-wrap');
    const overlap = crumb.left < util.right && util.left < crumb.right && crumb.top < util.bottom && util.top < crumb.bottom;
    assert.equal(overlap, false, `이름 [${crumb.left},${crumb.right}], 단추 [${util.left},${util.right}]`);
  } },
  { name: '머리는 본문 첫 요소라 사이드바와 같은 높이에서 시작하고 본문 폭 안에 있다(전체 폭 줄이 아니다, A-485)', async run() {
    const page = await open(1280);
    const side = await box(page, '.shell-sidebar'); const header = await box(page, '.shell-main > .project-header-outer'); const main = await box(page, '.shell-main');
    assert.ok(Math.abs(side.top - header.top) <= 1, `사이드바 위 ${side.top}, 머리 위 ${header.top}`);
    assert.ok(Math.abs(header.width - main.width) <= 1, `머리 폭 ${header.width}, 본문 폭 ${main.width}`);
    assert.ok(header.left >= side.right, '머리는 사이드바 오른쪽에 있다');
  } },
  { name: '표지는 머리 바로 아래에 놓인다', async run() {
    const page = await open(1280);
    const header = await box(page, '.shell-main > .project-header-outer'); const cover = await box(page, '.project-cover');
    assert.ok(cover.top >= header.bottom - 1 && cover.top - header.bottom <= 24, `머리 아래 ${header.bottom}, 표지 위 ${cover.top}`);
  } },
  { name: '머리가 본문에 있으면 사이드바 머리글(같은 이름)을 숨기고, 머리가 없는 화면에서는 보인다', async run() {
    const withHeader = await open(1280, true);
    assert.equal(await withHeader.isVisible('.shell-sidebar-title'), false);
    const without = await open(1280, false);
    assert.equal(await without.isVisible('.shell-sidebar-title'), true);
  } },
  { name: '폰 폭의 서랍 머리는 이름을 그대로 보인다', async run() {
    const page = await open(420);
    assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.offcanvas-header')).display !== 'none'), true);
  } },
  { name: '머리의 아바타가 본문 왼쪽 선에 맞는다(여백이 따로 밀지 않는다)', async run() {
    const page = await open(1280);
    const avatar = await box(page, '.project-header-avatar'); const main = await box(page, '.shell-main');
    assert.ok(Math.abs(avatar.left - main.left) <= 1, `아바타 왼쪽 ${avatar.left}, 본문 왼쪽 ${main.left}`);
    const util = await box(page, '.project-util-wrap');
    assert.ok(Math.abs(util.right - main.right) <= 1, `단추 오른쪽 ${util.right}, 본문 오른쪽 ${main.right}`);
  } },
];
