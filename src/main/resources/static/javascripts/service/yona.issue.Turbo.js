import * as Turbo from '/javascripts/turbo/turbo.es2017-esm.js';

Turbo.session.drive = false;
Turbo.config.forms.mode = 'off';

let detailRoot;
let disposeDetail;
const mobile = window.matchMedia('(max-width: 720px)');

function dispose() {
    if (disposeDetail) disposeDetail();
    disposeDetail = null;
    detailRoot = null;
}

function updateNavigation() {
    const layout = document.querySelector('.issue-columns');
    if (!layout) return;
    const selected = new URL(location.href).searchParams.get('selected');
    const checkbox = document.getElementById('two-column-mode');
    const enabled = !mobile.matches && (selected !== null || localStorage.getItem('useTwoColumnMode') === 'true');
    checkbox.checked = selected !== null || localStorage.getItem('useTwoColumnMode') === 'true';
    checkbox.onchange = () => {
        localStorage.setItem('useTwoColumnMode', String(checkbox.checked));
        if (!checkbox.checked && selected !== null) {
            location.assign(layout.dataset.selectionClearUrl);
        } else {
            updateNavigation();
        }
    };
    const field = document.querySelector('#search input[name="selected"]');
    field.value = selected || '';
    field.disabled = selected === null;
    document.querySelectorAll('#issue-list a[data-selection-url]').forEach(link => {
        const number = new URL(link.dataset.selectionUrl, location.href).searchParams.get('selected');
        link.href = enabled ? (number === selected ? layout.dataset.selectionClearUrl : link.dataset.selectionUrl) : link.dataset.detailUrl;
        link.dataset.turbo = String(enabled);
        if (enabled) {
            link.dataset.turboFrame = 'issue-detail';
            link.dataset.turboAction = 'advance';
        } else {
            delete link.dataset.turboFrame;
            delete link.dataset.turboAction;
        }
    });
    document.querySelectorAll('#issue-list .post-item').forEach(row => {
        const link = row.querySelector('a.title[data-selection-url]');
        const number = link && new URL(link.dataset.selectionUrl, location.href).searchParams.get('selected');
        row.classList.toggle('highlightBg', selected !== null && number === selected);
    });
    const pagination = document.getElementById('pagination');
    if (pagination) yona.Pagination.update(pagination, Number(pagination.dataset.total), {url: location.href});
    document.querySelectorAll('#issue-list .filter-wrap .filters a').forEach(link => {
        const url = new URL(link.href);
        if (selected === null) url.searchParams.delete('selected');
        else url.searchParams.set('selected', selected);
        link.href = url.href;
    });
}

function mount() {
    const layout = document.querySelector('.issue-columns');
    if (!layout) return;
    const root = document.getElementById('issue-detail-content');
    layout.classList.toggle('has-detail', !!root);
    document.getElementById('issue-detail').hidden = !root;
    if (root !== detailRoot) {
        dispose();
        if (root) {
            detailRoot = root;
            disposeDetail = yona.mountIssueDetail(root);
        }
    }
    const list = document.getElementById('issue-list');
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
    if (event.target.id === 'issue-detail') mount();
});
document.addEventListener('turbo:load', mount);
document.addEventListener('turbo:before-frame-render', event => {
    if (event.target.id === 'issue-detail') dispose();
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
