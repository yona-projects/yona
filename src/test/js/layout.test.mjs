import assert from 'node:assert/strict';
import fs from 'node:fs';
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
  {
    name: '사이트 관리 사용자 목록의 단추 열은 v1.6처럼 가입일 열 아래에 놓인다',
    async run() {
      // 단추 열의 클래스는 실제 템플릿에서 읽는다
      const template = fs.readFileSync(path.join(ROOT, 'src/main/resources/templates/site/user-list.html'), 'utf8');
      const actionClass = /<div th:if="\$\{userState != 'DELETED'\}" class="([^"]*action-buttons[^"]*)"/.exec(template)[1];
      const page = await open(`<div class="site-setting-wrap"><ul class="user-list-wrap"><li class="row listitem">
        <div class="col-lg-3 listitem-col"><a class="avatar-wrap list-avatar"><img width="32" height="32" alt=""></a><a class="user-name">이름</a><a class="user-id">@id</a></div><div class="col-lg-3 listitem-col"><span class="email">a@example.com</span></div>
        <div class="col-lg-2 listitem-col created-date"><span>2026-10-28</span></div>
        <div class="${actionClass}"><a class="ybtn ybtn-small">게스트로 전환</a> <a class="ybtn ybtn-small">계정잠그기</a></div></li></ul></div>`);
      const date = await rect(page, '.created-date');
      const buttons = await rect(page, '.action-buttons');
      const tops = await page.evaluate(() => [document.querySelector('.created-date'), document.querySelector('.action-buttons')].map((e) => Math.round(e.getBoundingClientRect().top)));
      assert.ok(tops[1] > tops[0], `단추 열이 가입일 열 아래 줄이어야 한다(위치 ${tops.join(', ')})`);
      assert.ok(Math.abs(buttons.left - date.left) <= 12, `단추 열 왼쪽 ${buttons.left}이 가입일 열 왼쪽 ${date.left}과 같아야 한다`);
      // v1.6은 단추 줄이 날짜 열 바로 아래에서 시작했다(이름 열의 높이에 밀려 내려가지 않는다)
      const gap = await page.evaluate(() => document.querySelector('.action-buttons').getBoundingClientRect().top - document.querySelector('.created-date span').getBoundingClientRect().bottom);
      assert.ok(gap <= 16, `날짜 글자 아래와 단추 열 사이가 ${gap}px로 벌어졌다`);
    },
  },
  {
    name: '폭 전체를 쓰는 드롭다운 단추(이슈의 상태 등)는 글자가 왼쪽에 놓인다',
    async run() {
      const page = await open(`<div id="state" class="btn-group auto" data-name="state"><button class="btn btn-outline-secondary dropdown-toggle"><span class="d-label"><a>닫힘</a></span></button></div>`);
      const align = await page.evaluate(() => getComputedStyle(document.querySelector('#state > .dropdown-toggle')).textAlign);
      assert.equal(align, 'left');
    },
  },
  {
    name: '상단 검색 상자는 v1.6처럼 입력란 전체 70x30, 단추 높이 20이다 (A-377)',
    async run() {
      const page = await open(`<form class="gnb-search-form"><div class="search-box"><input type="text" name="keyword"><button type="submit"><i>돋보기</i></button></div></form>`);
      const size = await page.evaluate(() => { const i = document.querySelector('.search-box input').getBoundingClientRect(); const b = document.querySelector('.search-box button').getBoundingClientRect(); return { iw: i.width, ih: i.height, bh: b.height }; });
      assert.equal(Math.round(size.iw), 70, `입력란 폭 ${size.iw}`);
      assert.equal(Math.round(size.ih), 30, `입력란 높이 ${size.ih}`);
      assert.equal(Math.round(size.bh), 20, `단추 높이 ${size.bh}`);
    },
  },
  {
    name: '폭을 정한 입력란은 v1.6처럼 안쪽 여백이 폭에 더해진다: 복제 주소 113x30, 최근 N일 44x30, 쪽 번호 44x30 (A-377)',
    async run() {
      const page = await open(`<div class="project-home-header row"><div class="project-clone-wrap col-lg-3"><input id="clone" class="project-clone-url form-control" value="http://x"></div></div>
        <div class="user-stream-box"><div class="float-end">최근<input id="days" type="number" class="input-mini-min form-control" value="30">일</div></div>
        <div class="page-navigation-wrap"><ul class="page-nums"><li class="page-num"><input id="pageNum" type="number" class="input-mini nospinner" value="1"></li></ul></div>`);
      const sizes = await page.evaluate(() => Object.fromEntries(['#clone', '#days', '#pageNum'].map((s) => { const r = document.querySelector(s).getBoundingClientRect(); return [s, [Math.round(r.width), Math.round(r.height)]]; })));
      // 복제 주소 입력란의 폭 값은 화면 너비에 따라 달라지므로(실제 화면은 100px) 전체 폭이 값에 안쪽 여백 12와 테두리 1을 더한 것인지 본다
      const clone = await page.evaluate(() => { const e = document.querySelector('#clone'); const cs = getComputedStyle(e); return { total: e.getBoundingClientRect().width, width: parseFloat(cs.width), box: cs.boxSizing, height: e.getBoundingClientRect().height }; });
      assert.equal(clone.box, 'content-box');
      assert.equal(Math.round(clone.total), Math.round(clone.width + 13), `전체 폭 ${clone.total}은 폭 값 ${clone.width}에 13을 더한 값이어야 한다`);
      assert.equal(Math.round(clone.height), 30);
      assert.deepEqual(sizes['#days'], [44, 30]);
      assert.deepEqual(sizes['#pageNum'], [44, 30]);
    },
  },
  {
    name: '왼쪽 9칸과 오른쪽 3칸 화면은 v1.6의 폭과 간격이다: 왼쪽 열 938, 오른쪽 열 295, 시작 위치 10과 975 (A-377)',
    async run() {
      const page = await open(`<form><div class="row"><div class="col-lg-12"><dl><dd><input id="title" class="zen-mode text title form-control" type="text"></dd></dl></div>
        <div class="row"><div class="col-lg-9 span-left-pane"><div id="left">왼쪽</div></div><div class="col-lg-3 span-hard-wrap"><div id="right">오른쪽</div></div></div></div></form>`);
      const left = await rect(page, '#left'); const right = await rect(page, '#right'); const title = await rect(page, '#title');
      assert.equal(Math.round(left.left), 10, `왼쪽 열 시작 ${left.left}`);
      assert.equal(Math.round(left.width), 938, `왼쪽 열 폭 ${left.width}`);
      assert.equal(Math.round(right.left), 975, `오른쪽 열 시작 ${right.left}`);
      assert.equal(Math.round(right.width), 295, `오른쪽 열 폭 ${right.width}`);
      assert.equal(Math.round(title.left), 10, `제목 입력란 시작 ${title.left}(안쪽 .row가 겹쳐 들여쓰면 20이다)`);
    },
  },
  {
    name: '제목 입력란은 높이 29, 마크다운 편집기 글상자는 1260x310이다 (A-377)',
    async run() {
      const page = await open(`<div class="content-wrap"><input id="title" class="zen-mode text title form-control" type="text"><textarea id="editor" class="editorSeries content comment nm form-control" style="width:1246px"></textarea></div>`);
      const t = await page.evaluate(() => document.querySelector('#title').getBoundingClientRect().height);
      const e = await page.evaluate(() => { const r = document.querySelector('#editor').getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)]; });
      assert.equal(Math.round(t), 29, `제목 입력란 높이 ${t}`);
      assert.deepEqual(e, [1260, 310]);
    },
  },
  {
    name: '왼쪽 2칸과 오른쪽 10칸 목록 화면은 끝에 붙은 script가 있어도 v1.6의 폭이다: 188, 시작 위치 10과 224 (A-377)',
    async run() {
      const page = await open(`<div class="row cb"><div class="left-menu col-lg-2" id="a">검색</div><div class="col-lg-10 span-hard-wrap" id="b">목록</div><script>/* 목록 조각의 스크립트 */</script></div>`);
      const a = await rect(page, '#a'); const b = await rect(page, '#b');
      assert.equal(Math.round(a.left), 10); assert.equal(Math.round(a.width), 188);
      assert.equal(Math.round(b.left), 224); assert.equal(Math.round(b.width), 1046);
    },
  },
  {
    name: '달력의 월 선택 상자는 높이 30이고 날짜 단추는 칸을 넘지 않는다 (A-377)',
    async run() {
      const page = await open(`<div class="pika-single"><div class="pika-lendar"><div class="pika-title"><div class="pika-label"><select class="pika-select pika-select-month"><option>November</option></select></div></div>
        <table class="pika-table"><tbody><tr><td data-day="1"><button class="pika-button pika-day" type="button">1</button></td></tr></tbody></table></div></div>`);
      const h = await page.evaluate(() => document.querySelector('.pika-select-month').getBoundingClientRect().height);
      assert.equal(Math.round(h), 30, `월 선택 상자 높이 ${h}`);
      const over = await page.evaluate(() => { const b = document.querySelector('.pika-day').getBoundingClientRect(); const c = document.querySelector('.pika-table td').getBoundingClientRect(); return b.width - c.width; });
      assert.ok(over <= 0.5, `날짜 단추가 칸보다 ${over}px 넓다`);
    },
  },
  {
    name: '탭은 v1.6처럼 좌우 안쪽 여백 30, 아래 간격 20이고 코드 화면은 0이며 작은 탭과 nm 탭은 그대로이다 (A-377)',
    async run() {
      const tab = (cls, id) => `<ul id="${id}" class="nav nav-tabs ${cls}"><li class="nav-item"><a class="nav-link active">탭</a></li></ul>`;
      const page = await open(`${tab('mb-3', 'plain')}${tab('mb-3 small', 'small')}${tab('nm mb-3', 'nm')}<div class="code-browse-wrap">${tab('mb-3', 'code')}</div>`);
      const m = await page.evaluate(() => Object.fromEntries(['plain', 'small', 'nm', 'code'].map((id) => { const ul = document.getElementById(id); return [id, [getComputedStyle(ul.querySelector('a')).paddingLeft, getComputedStyle(ul).marginBottom]]; })));
      assert.deepEqual(m.plain, ['30px', '20px']);
      assert.equal(m.small[0], '15px', '작은 탭의 안쪽 여백은 v1.6도 15px이다');
      assert.notEqual(m.small[1], '20px');
      assert.equal(m.nm[1], '0px');
      assert.deepEqual(m.code, ['30px', '0px']);
    },
  },
  {
    name: '폭을 따로 정하지 않은 글 입력란은 v1.6처럼 전체 220x30이다 (A-377)',
    async run() {
      const page = await open(`<dl><dd class="mt10"><input id="plain" class="text form-control" type="text"><input id="due" class="validate due-date form-control" type="text"><input id="wide" class="text form-control w-100" type="text"></dd></dl>`);
      const size = await page.evaluate(() => Object.fromEntries(['plain', 'due', 'wide'].map((id) => { const r = document.getElementById(id).getBoundingClientRect(); return [id, [Math.round(r.width), Math.round(r.height)]]; })));
      assert.deepEqual(size.plain, [220, 30]);
      assert.deepEqual(size.due, [220, 30]);
      assert.ok(size.wide[0] > 220, '폭 클래스를 쓴 입력란은 그 폭을 따른다');
    },
  },
  {
    name: '감시 단추는 v1.6처럼 28 높이이고 이슈 일괄 변경 드롭다운은 107x30이다 (A-377)',
    async run() {
      const page = await open(`<ul class="project-util"><li><div class="btn-group dropdown watch-btn"><a id="count" class="btn btn-sm watcher-count btn-light">3</a></div></li></ul>
        <div class="mass-update-wrap"><form class="mass-update-form float-start"><div class="btn-group"><button id="dd" class="btn btn-outline-secondary dropdown-toggle" type="button"><span class="d-label">담당자</span></button></div></form></div>`);
      const size = await page.evaluate(() => Object.fromEntries(['count', 'dd'].map((id) => { const r = document.getElementById(id).getBoundingClientRect(); return [id, [Math.round(r.width), Math.round(r.height)]]; })));
      assert.equal(size.count[1], 28, `감시 수 단추 높이 ${size.count[1]}`);
      assert.deepEqual(size.dd, [107, 30]);
    },
  },
  {
    name: '사용자 메뉴는 v1.6처럼 내용 폭 350, 검색란 높이 42이고 탭 셋은 한 줄에 놓인다 (A-377)',
    async run() {
      const page = await open(`<div id="mySidenav" class="sidenav row align-items-start" style="width:360px;position:absolute;right:0;top:0">
        <div class="col-lg-5 right-menu span-hard-wrap"><div class="user-menu-wrap"><span class="user-menu"><a>프로필 페이지</a></span></div>
        <ul class="nav nav-tabs nm mb-3"><li class="nav-item"><a id="t1" class="nav-link">즐겨찾기</a></li><li class="nav-item"><a id="t2" class="nav-link active">프로젝트</a></li><li class="nav-item"><a id="t3" class="nav-link">최근 읽은 이슈</a></li></ul>
        <div class="tab-content"><div class="tab-pane user-project-list active"><form><div class="group"><input id="s" class="search-input project-search form-control" placeholder="검색할 이름"></div></form></div></div></div></div>`);
      await page.evaluate(() => document.body.appendChild(document.getElementById('mySidenav'))); // 실제 메뉴는 페이지 본문(.page-wrap-outer) 밖에 있다
      const m = await page.evaluate(() => { const r = (s) => { const q = document.querySelector(s).getBoundingClientRect(); return [Math.round(q.left), Math.round(q.top), Math.round(q.width), Math.round(q.height)]; }; return { menu: r('.right-menu'), t1: r('#t1'), t2: r('#t2'), t3: r('#t3'), s: r('#s') }; });
      assert.equal(m.menu[2], 350, `메뉴 폭 ${m.menu[2]}`);
      assert.equal(m.menu[0], 1280 - 350, `메뉴 시작 ${m.menu[0]}`);
      assert.equal(m.t3[1], m.t1[1], '탭 셋은 한 줄에 놓인다(선생님 결정: v1.6의 줄바꿈은 의도가 아니다)');
      assert.equal(m.s[3], 42, `검색란 높이 ${m.s[3]}`);
    },
  },
];
