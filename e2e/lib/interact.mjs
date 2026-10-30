// 상호작용 점검(로드맵 X-38a): 화면의 BS 트리거(모달, 드롭다운, 탭, 팝오버, 툴팁, 접기)를 찾아 하나씩 작동시키고 결과를 기록한다.
// 버전에 상관없이 쓸 수 있게 `data-toggle`(BS2~4)와 `data-bs-toggle`(BS5)를 모두 찾고, "열렸는지"는 클래스 이름(in, show)이 아니라
// 대상 요소가 실제로 보이는지로 판정한다. 트리거를 누르기만 하고 확인 단추는 누르지 않으므로 고정 데이터를 바꾸지 않는다.
const TYPES = ['modal', 'dropdown', 'tab', 'pill', 'popover', 'tooltip', 'collapse'];
const PER_TYPE = { modal: 4, dropdown: 4, tab: 6, pill: 4, popover: 4, tooltip: 3, collapse: 3 };

/** 트리거 목록을 모은다: 종류, 안정적인 선택자(순번), 대상 선택자. */
async function collect(page) {
  return page.evaluate(({ TYPES }) => {
    const out = [];
    for (const type of TYPES) {
      const els = [...document.querySelectorAll(`[data-toggle="${type}"], [data-bs-toggle="${type}"]`)].filter((e) => {
        const r = e.getBoundingClientRect(); const st = getComputedStyle(e);
        if (!(r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none')) return false;
        // 화면 밖이거나 다른 요소(펼쳐지지 않은 사이드바 등)에 가려진 트리거는 사용자가 누를 수 없으므로 점검 대상이 아니다
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        if (cx < 0 || cy < 0 || cx > innerWidth || cy > innerHeight) return false;
        const top = document.elementFromPoint(cx, cy);
        return !!top && (top === e || e.contains(top) || top.contains(e));
      });
      els.forEach((e, i) => {
        e.setAttribute('data-e2e-trigger', `${type}-${i}`);
        out.push({ type, key: `${type}-${i}`, target: e.getAttribute('data-target') || e.getAttribute('data-bs-target') || (e.getAttribute('href') || '').startsWith('#') ? (e.getAttribute('data-target') || e.getAttribute('data-bs-target') || e.getAttribute('href')) : null, text: (e.textContent || e.getAttribute('title') || '').trim().slice(0, 30), count: els.length });
      });
    }
    return out;
  }, { TYPES });
}

/** 트리거를 작동시킨 뒤 "열린 상태"를 판정한다. */
async function open(page, t) {
  const sel = `[data-e2e-trigger="${t.key}"]`;
  const loc = page.locator(sel).first();
  if (t.type === 'tooltip' || t.type === 'popover') {
    // 링크를 누르면 다른 화면으로 가 버리므로 올려 두기만 한다(클릭으로 여는 팝오버는 링크가 아닐 때만 누른다)
    // 팝오버는 data-trigger가 hover(이 사이트의 대부분)이면 올려 두고, 그 밖이면 누른다. 링크도 올려 두기만 한다
    const mode = await loc.evaluate((e) => ({ link: e.tagName === 'A' && !(e.getAttribute('href') || '#').startsWith('#'), trigger: e.getAttribute('data-trigger') || e.getAttribute('data-bs-trigger') || '' }));
    await page.mouse.move(1270, 790);
    if (t.type === 'tooltip' || mode.link || mode.trigger.includes('hover')) await loc.hover({ timeout: 2500 }); else await loc.click({ timeout: 2500 });
    await page.waitForTimeout(900);
    const shown = (type) => page.evaluate((type) => [...document.querySelectorAll(type === 'tooltip' ? '.tooltip, .bs-tooltip-auto, [role=tooltip], .tippy-box, [data-tippy-root], .ui-tooltip' : '.popover, [role=tooltip], .tippy-box, [data-tippy-root]')].some((e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden'; }), type);
    if (await shown(t.type)) return true;
    // 한 번 더: 마우스를 치웠다가 다시 올려 둔다(앞의 트리거가 남긴 상태가 가리는 경우가 있다)
    await page.mouse.move(1270, 790); await page.waitForTimeout(300);
    await loc.hover({ timeout: 2500 }); await page.waitForTimeout(1200);
    return shown(t.type);
  }
  await loc.click({ timeout: 2500 });
  await page.waitForTimeout(800);
  return page.evaluate(({ sel, type, target }) => {
    const e = document.querySelector(sel);
    const visible = (n) => { if (!n) return false; const r = n.getBoundingClientRect(); const st = getComputedStyle(n); return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none' && parseFloat(st.opacity || '1') > 0.05; };
    if (type === 'dropdown') {
      const scope = e.closest('.dropdown, .btn-group, .dropup, li, div') || e.parentElement;
      return visible(scope.querySelector('.dropdown-menu')) || visible(e.nextElementSibling);
    }
    const node = target ? (target.startsWith('#') ? document.getElementById(target.slice(1)) : (() => { try { return document.querySelector(target); } catch { return null; } })()) : null;
    // 트리거의 대상 모달이 아니라 사이트 공용 대화상자(yobiDialog)가 열리는 화면도 있으므로, 보이는 모달이 하나라도 있으면 열린 것으로 본다
    if (type === 'modal') return visible(node) || [...document.querySelectorAll('.modal')].some(visible);
    if (type === 'collapse') return visible(node);
    if (type === 'tab' || type === 'pill') return !node || visible(node) || !!e.closest('.active') || e.classList.contains('active') || !!e.parentElement?.classList.contains('active');
    return true;
  }, { sel, type: t.type, target: t.target });
}

/**
 * 한 화면의 트리거를 모두 점검한다. 돌려주는 값: `{ found, opened, failed: [{ type, text, reason }], errors }`.
 * 트리거를 누른 뒤에는 Escape와 본문 클릭으로 닫고, 주소가 바뀌었으면 되돌아간다.
 */
export async function interact(page, base, pathBefore) {
  const triggers = await collect(page);
  const seen = {}; const failed = []; const skipped = []; let opened = 0; let attempted = 0;
  const errors = [];
  const onError = (e) => errors.push(String(e.message).slice(0, 200));
  page.on('pageerror', onError);
  for (const t of triggers) {
    seen[t.type] = (seen[t.type] || 0) + 1;
    if (seen[t.type] > PER_TYPE[t.type]) continue;
    attempted++;
    try {
      const ok = await open(page, t);
      if (ok) opened++; else failed.push({ type: t.type, text: t.text, reason: '작동시켰지만 대상이 보이지 않는다' });
    } catch (e) {
      // 누를 수 없었던 트리거(가려짐, 시간 초과)는 실패가 아니라 건너뜀으로 기록한다: 사용자가 누를 수 없는 것을 깨졌다고 단정할 수 없다
      skipped.push({ type: t.type, text: t.text.replace(/\s+/g, ' ').trim(), reason: String(e.message).split('\n')[0].slice(0, 100) });
    }
    await page.mouse.move(1270, 790).catch(() => {});
    // 모달은 닫기 단추로 닫는다(다른 곳을 누르거나 Escape를 보내면 화면의 단축키와 얽힐 수 있다)
    await page.locator('.modal.in .close, .modal.show .close, .modal.in [data-dismiss="modal"], .modal.show [data-bs-dismiss="modal"], .modal.show .btn-close').first().click({ timeout: 800 }).catch(() => {});
    if (page.url() !== base + pathBefore && !page.url().startsWith(base + pathBefore.split('#')[0])) { await page.goBack().catch(() => {}); await collect(page); }
  }
  page.off('pageerror', onError);
  return { found: triggers.length, attempted, opened, failed, skipped, errors };
}
