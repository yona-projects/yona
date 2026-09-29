import * as Turbo from '/javascripts/turbo/turbo.es2017-esm.js';

// Sidebar fragments opt in; existing page navigation and forms keep their native lifecycle.
Turbo.session.drive = false;
Turbo.config.forms.mode = 'off';

const preference = 'shallWeOpenLeftNavigation';
let restoreFocus = false;

function setOpen(frame, open, focus) {
    frame.hidden = !open;
    document.body.classList.toggle('left-sidebar-open', open);
    localStorage.setItem(preference, String(open));
    document.querySelectorAll('[data-sidebar-toggle]').forEach(toggle => {
        toggle.setAttribute('aria-expanded', String(open));
    });
    if (open && !frame.hasAttribute('src')) frame.src = '/user/sidebar';
    if (focus) {
        const target = open ? frame.querySelector('[data-sidebar-close]') : document.querySelector('[data-sidebar-toggle]');
        target?.focus({preventScroll: true});
    }
}

function restore() {
    const frame = document.querySelector('turbo-frame#sidebar');
    if (!frame || frame.dataset.sidebarStandalone === 'true') return;
    setOpen(frame, localStorage.getItem(preference) === 'true', false);
}

function showFailure(frame, signIn) {
    let status = frame.querySelector('[data-sidebar-status]');
    if (!status) {
        status = document.createElement('p');
        status.className = 'sidebar-status';
        status.dataset.sidebarStatus = '';
        frame.prepend(status);
    }
    status.setAttribute('role', 'alert');
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.className = 'ybtn ybtn-small';
    retry.dataset.sidebarRefresh = '';
    retry.textContent = 'Retry';
    status.replaceChildren(document.createTextNode('Could not load sidebar. '), retry);
    if (signIn) {
        const login = document.createElement('a');
        login.href = '/users/loginform';
        login.dataset.turbo = 'false';
        login.textContent = 'Sign in';
        status.append(' ', login);
    }
}

// Delegation also covers controls delivered by Turbo and its loading/error states.
document.addEventListener('click', event => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    if (!(event.target instanceof Element)) return;
    const frame = document.querySelector('turbo-frame#sidebar');
    if (!frame) return;
    const standalone = frame.dataset.sidebarStandalone === 'true';
    if (event.target.closest('[data-sidebar-toggle]') && !standalone) {
        event.preventDefault();
        setOpen(frame, frame.hidden, true);
    } else if (frame.contains(event.target) && event.target.closest('[data-sidebar-close]')) {
        localStorage.setItem(preference, 'false');
        if (!standalone) {
            event.preventDefault();
            setOpen(frame, false, true);
        }
    } else if (frame.contains(event.target) && event.target.closest('[data-sidebar-refresh]')) {
        event.preventDefault();
        if (frame.hasAttribute('src')) frame.reload();
        else frame.src = '/user/sidebar';
    }
});

document.addEventListener('turbo:before-frame-render', event => {
    if (event.target.id === 'sidebar') restoreFocus = event.target.contains(document.activeElement);
});
document.addEventListener('turbo:frame-load', event => {
    if (event.target.id !== 'sidebar') return;
    if (restoreFocus && !event.target.hidden) event.target.querySelector('[data-sidebar-close]')?.focus({preventScroll: true});
    restoreFocus = false;
});
document.addEventListener('turbo:before-fetch-response', event => {
    if (event.target.id !== 'sidebar') return;
    const response = event.detail.fetchResponse.response;
    if (!response.ok || response.redirected) {
        event.preventDefault();
        showFailure(event.target, response.redirected || response.status === 401);
    }
});
document.addEventListener('turbo:frame-missing', event => {
    if (event.target.id !== 'sidebar') return;
    event.preventDefault();
    showFailure(event.target, false);
});
document.addEventListener('turbo:fetch-request-error', event => {
    if (event.target.id !== 'sidebar') return;
    event.preventDefault();
    showFailure(event.target, false);
});
document.addEventListener('DOMContentLoaded', restore, {once: true});
document.addEventListener('turbo:load', restore);
window.addEventListener('pageshow', event => { if (event.persisted) restore(); });
