import * as Turbo from '/javascripts/turbo/turbo.es2017-esm.js';

Turbo.session.drive = false;
Turbo.config.forms.mode = 'off';

/**
 * 목록(turbo-frame, permanent) + 상세(turbo-frame) 2단 보기의 공용 Turbo 어댑터.
 * 화면별 차이(프레임 id, 상세 루트, 상세 mount/dispose)는 config로 받는다.
 *
 * config: {
 *   layout:     2단 레이아웃 컨테이너 셀렉터 (data-selection-clear-url 보유)
 *   list:       목록 turbo-frame id
 *   detail:     상세 turbo-frame id
 *   detailRoot: 상세 루트 엘리먼트 id (없으면 상세 없음)
 *   mountDetail(root): 상세 초기화, dispose 함수를 반환
 *   searchField: (선택) 검색 폼 안에서 선택 번호를 실어 나르는 hidden input 셀렉터
 *                기본값 '#search input[name="selected"]'
 * }
 */
export function setupTwoColumn(config) {
    const layoutSelector = config.layout;
    const listId = config.list;
    const detailId = config.detail;
    const mobile = window.matchMedia('(max-width: 720px)');
    let detailRoot;
    let disposeDetail;

    function dispose() {
        if (disposeDetail) disposeDetail();
        disposeDetail = null;
        detailRoot = null;
    }

    function updateNavigation() {
        const layout = document.querySelector(layoutSelector);
        if (!layout) return;
        const selected = new URL(location.href).searchParams.get('selected');
        const checkbox = document.getElementById('two-column-mode');
        const preferred = selected !== null || localStorage.getItem('useTwoColumnMode') === 'true';
        const enabled = !mobile.matches && preferred;
        if (checkbox) {
            checkbox.checked = preferred;
            checkbox.onchange = () => {
                localStorage.setItem('useTwoColumnMode', String(checkbox.checked));
                if (!checkbox.checked && selected !== null) {
                    location.assign(layout.dataset.selectionClearUrl);
                } else {
                    updateNavigation();
                }
            };
        }
        const field = document.querySelector(config.searchField || '#search input[name="selected"]');
        if (field) {
            field.value = selected || '';
            field.disabled = selected === null;
        }
        document.querySelectorAll('#' + listId + ' a[data-selection-url]').forEach(link => {
            const number = new URL(link.dataset.selectionUrl, location.href).searchParams.get('selected');
            link.href = enabled ? (number === selected ? layout.dataset.selectionClearUrl : link.dataset.selectionUrl) : link.dataset.detailUrl;
            link.dataset.turbo = String(enabled);
            if (enabled) {
                link.dataset.turboFrame = detailId;
                link.dataset.turboAction = 'advance';
            } else {
                delete link.dataset.turboFrame;
                delete link.dataset.turboAction;
            }
        });
        document.querySelectorAll('#' + listId + ' .post-item').forEach(row => {
            const link = row.querySelector('a.title[data-selection-url]');
            const number = link && new URL(link.dataset.selectionUrl, location.href).searchParams.get('selected');
            row.classList.toggle('highlightBg', selected !== null && number === selected);
        });
        const pagination = document.getElementById('pagination');
        // 전체 개수를 data-total로 싣는 화면만 여기서 갱신한다(그렇지 않은 화면은 검색 폼 제출로 페이지 이동).
        if (pagination && pagination.dataset.total !== undefined) {
            yona.Pagination.update(pagination, Number(pagination.dataset.total), {url: location.href});
        }
        document.querySelectorAll('#' + listId + ' .filter-wrap .filters a').forEach(link => {
            const url = new URL(link.href);
            if (selected === null) url.searchParams.delete('selected');
            else url.searchParams.set('selected', selected);
            link.href = url.href;
        });
    }

    function mount() {
        const layout = document.querySelector(layoutSelector);
        if (!layout) return;
        const root = document.getElementById(config.detailRoot);
        layout.classList.toggle('has-detail', !!root);
        document.getElementById(detailId).hidden = !root;
        if (root !== detailRoot) {
            dispose();
            if (root) {
                detailRoot = root;
                disposeDetail = config.mountDetail(root);
            }
        }
        const list = document.getElementById(listId);
        if (!list._yonaTurboBound) {
            list._yonaTurboBound = true;
            list.addEventListener('click', event => {
                if (event.target.closest('a, button, input, label, select, textarea')) return;
                const row = event.target.closest('.post-item');
                const link = row && row.querySelector('a.title[data-selection-url]');
                if (link && link.dataset.turbo === 'true') link.click();
            });
        }
        updateNavigation();
        NProgress.done();
    }

    document.addEventListener('DOMContentLoaded', mount);
    document.addEventListener('turbo:frame-load', event => {
        if (event.target.id === detailId) mount();
    });
    document.addEventListener('turbo:load', mount);
    document.addEventListener('turbo:before-frame-render', event => {
        if (event.target.id === detailId) dispose();
    });
    document.addEventListener('turbo:before-cache', dispose);
    document.addEventListener('turbo:before-render', event => {
        dispose();
        // The permanent list/shell keep their live listeners; only the new detail needs mounting.
        event.detail.newBody.querySelectorAll('script').forEach(script => { script.dataset.turboEval = 'false'; });
    });
    window.addEventListener('pagehide', dispose);
    window.addEventListener('pageshow', event => { if (event.persisted) mount(); });
    mobile.addEventListener('change', updateNavigation);
}
