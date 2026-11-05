import assert from 'node:assert/strict';
import path from 'node:path';
import { pageWith, ROOT } from './harness.mjs';

// 화면 배치 시험이다. 컴파일된 실제 CSS(`./gradlew compileSass`가 만드는 build/generated/sass)를 불러 폭 1280에서 요소 위치를 잰다.
// BS5의 `.row`는 바깥으로 음수 여백을 두고 `.col-*`는 안쪽 여백을 두므로, v1.6의 `row-fluid`와 `span`을 옮긴 화면에서 여백을 맞추지 않으면
// 줄이 부모 밖으로 나가 잘리거나 가장자리에 붙는다(v1.6과 v2 화면 비교에서 확인한 결함).
const CSS = path.join(ROOT, 'build/generated/sass/static/assets/stylesheets');
const SELECT2 = path.join(ROOT, 'src/main/resources/static/assets/javascripts/lib/select2/select2.css');

async function open(html) {
  const page = await pageWith({ html: `<div class="page-wrap-outer"><div class="project-page-wrap">${html}</div></div>`, stubs: false });
  await page.setViewportSize({ width: 1280, height: 900 });
  for (const css of [path.join(CSS, 'bootstrap.css'), SELECT2, path.join(CSS, 'usermenu.css'), path.join(CSS, 'yobi.css')]) await page.addStyleTag({ path: css });
  return page;
}

const rect = (page, selector) => page.evaluate((s) => { const r = document.querySelector(s).getBoundingClientRect(); return { left: r.left, right: r.right, width: r.width }; }, selector);

export const tests = [
  {
    name: '코드 화면의 브랜치 선택 상자는 폭 220으로 고정된다 (R-코드 목록 비교)',
    async run() {
      const page = await open(`<div class="code-browse-wrap"><div class="code-browse-header">
        <div id="s2id_branches" class="select2-container float-start"><a class="select2-choice"><span class="select2-chosen">branch master</span></a></div>
        <div id="breadcrumbs" class="code-breadcrumb-wrap ml10 float-start">proj1</div></div></div>`);
      const r = await rect(page, '#s2id_branches');
      assert.equal(Math.round(r.width), 220);
    },
  },
  {
    name: '코드 화면의 파일 목록 줄은 부모 폭 안에 들어간다',
    async run() {
      const page = await open(`<div class="code-browse-wrap"><div class="code-viewer-wrap"><div class="list-wrap" style="display:block">
        <div class="row listhead"><div class="col-lg-6 filename"><strong>파일명</strong></div><div class="col-lg-4 commitMsg"><strong>커밋 메시지</strong></div><div class="col-lg-2 commitDate"><strong>커밋한 날짜</strong></div></div>
        <div class="row listitem"><div class="col-lg-6 filename">docs</div><div class="col-lg-5 commitMsg">dev</div><div class="col-lg-1 commitDate">01-03</div></div>
      </div></div></div>`);
      const parent = await rect(page, '.list-wrap');
      for (const selector of ['.listhead', '.listitem']) {
        const r = await rect(page, selector);
        assert.ok(r.left >= parent.left - 0.5 && r.right <= parent.right + 0.5, `${selector} [${r.left}, ${r.right}]가 부모 [${parent.left}, ${parent.right}] 밖이다`);
      }
      const head = await rect(page, '.listhead .commitDate strong');
      assert.ok(head.right <= parent.right, '마지막 열의 글자가 잘리지 않아야 한다');
    },
  },
  {
    name: '프로젝트 홈의 저장소 주소 상자는 페이지 폭 안에 들어간다',
    async run() {
      const page = await open(`<div class="project-home-header row">
        <div class="project-overview col-lg-9 span-hard-wrap">설명</div>
        <div class="project-clone-wrap col-lg-3 hide-in-mobile"><input id="cloneURL" class="project-clone-url form-control" value="http://tester@127.0.0.1:19200/tester/proj1"><a id="copyBtn" class="ybtn project-clone-button">주소 복사</a></div></div>`);
      const page_ = await rect(page, '.project-page-wrap');
      const button = await rect(page, '#copyBtn');
      assert.ok(button.right <= page_.right + 0.5, `주소 복사 단추 오른쪽 ${button.right}가 페이지 오른쪽 ${page_.right}을 넘는다`);
    },
  },
  {
    name: '사용자 메뉴(오른쪽에서 나오는 메뉴)는 열면 폭 전체를 쓰고 화면 밖으로 나가지 않는다',
    async run() {
      const page = await open(`<div id="mySidenav" class="sidenav row align-items-start" style="width:360px">
        <div class="col-lg-5 right-menu span-hard-wrap"><div class="user-menu-wrap"><span class="user-menu"><a>프로필 페이지</a></span><span class="user-menu"><a>사용자 설정</a></span></div>
        <ul class="nav nav-tabs nm mb-3"><li class="nav-item"><a class="nav-link">즐겨찾기</a></li><li class="nav-item"><a class="nav-link">프로젝트</a></li><li class="nav-item"><a class="nav-link">최근 읽은 이슈</a></li></ul></div></div>`);
      const side = await rect(page, '#mySidenav');
      const menu = await rect(page, '.right-menu');
      assert.ok(side.right <= 1280.5, `메뉴 오른쪽 ${side.right}가 화면 밖으로 나간다`);
      assert.ok(menu.width >= side.width - 30, `메뉴 내용 폭 ${menu.width}이 상자 폭 ${side.width}의 일부만 쓴다`);
    },
  },
  {
    name: '사용자 페이지의 `최근 N일` 입력란은 글자와 같은 줄에 놓인다',
    async run() {
      const page = await open(`<div class="user-stream-box"><div class="float-end" id="days">최근<input id="daysAgoBtn" name="daysAgo" type="number" min="1" max="99" class="input-mini-min form-control" value="30" style="margin:0px 5px; vertical-align:bottom;">일</div></div>`);
      const box = await rect(page, '#days');
      const height = await page.evaluate(() => document.querySelector('#days').getBoundingClientRect().height);
      assert.ok(height < 40, `한 줄이어야 하는데 높이가 ${height}이다(입력란이 블록이라 글자가 줄바꿈된다)`);
      assert.ok(box.width < 120, `폭이 ${box.width}로 넓다`);
      const content = await page.evaluate(() => { const i = document.querySelector('#daysAgoBtn'); const cs = getComputedStyle(i); return i.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight); });
      assert.ok(content >= 24, `입력란 안쪽 폭이 ${content}라 두 자리 숫자(30)가 잘린다`);
    },
  },
  {
    name: '조직 게시판의 검색 줄은 프로젝트 선택, 키워드 검색, 2단 보기가 한 줄에 놓인다',
    async run() {
      const page = await open(`<div class="search-wrap underline"><form id="option_form" method="get" class="float-start row align-items-start">
        <div class="project-selects col-lg-7"><select class="form-select" id="projects"><option>x</option></select></div>
        <div class="search-bar col-lg-4"><input name="filter" class="textbox group-board" type="text" placeholder="키워드로 검색"><button type="submit" class="search-btn">검색</button></div>
        <div class="two-column-icon mr10 hide-in-mobile" id="two-column-mode-checkbox"><div class="form-check"><label class="form-check-label"><div class="two-column-icon-border"><input id="two-column-mode" type="checkbox" class="form-check-input"><span class="two-column-mode-text">2단 보기</span></div></label></div></div>
      </form></div>`);
      const tops = await page.evaluate(() => ['.project-selects', '.search-bar', '#two-column-mode-checkbox'].map((s) => Math.round(document.querySelector(s).getBoundingClientRect().top)));
      assert.ok(Math.max(...tops) - Math.min(...tops) <= 6, `세 요소의 위쪽이 ${tops.join(', ')}로 한 줄이 아니다`);
      const right = await page.evaluate(() => document.querySelector('#two-column-mode-checkbox').getBoundingClientRect().right);
      assert.ok(right <= 1270.5, `2단 보기 오른쪽 ${right}이 내용 영역을 넘는다`);
    },
  },
];
