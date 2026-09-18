import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';

/** Screen: the site-wide "Favorite" GNB panel (common/usermenu_tab_content_list.html, wired by
 * yona.Usermenu.js) -- star toggles for projects, organizations, and issues. Each POSTs to a
 * dedicated REST toggle endpoint (FavoriteController.kt) and flips the `starred` class on success. */
test.describe('favorites (star) toggles', () => {
  test('starring and unstarring a project from the Favorite sidebar panel persists', async ({ page }) => {
    await page.goto('/');
    // The GNB "Favorite" tab panel lives inside the collapsible sidebar (site/layout.html) --
    // without opening it first, its "Favorite" tab link is occluded by the main page content
    // (confirmed live: clicking it directly times out with "<h3> ... intercepts pointer events").
    await page.click('#sidebar-open-btn');
    await page.click('a[href="#myOrganizationList"]');

    // common/usermenu_tab_content_list.html's very first <li class="org-li"> is a pseudo-org for
    // the current user's own (not-under-an-organization) projects ("createdByMe") -- reliable
    // here since this session's admin has created many such projects directly. Each project <li>
    // under it starts with class "user-li hide" unless already favorited (star-project's parent
    // is hidden by default); yona.Usermenu.js's ".all-orgs" click handler reveals it by removing
    // "hide" from every descendant of the closest <li> -- click the header at a position away
    // from the (empty, for this pseudo-org) star-org div to avoid triggering its own handler.
    const personalOrgLi = page.locator('.org-li').first();
    await personalOrgLi.locator('.org-list.all-orgs').click({ position: { x: 5, y: 5 } });

    const star = personalOrgLi.locator('.star-project[data-project-id]').first();
    await expect(star).toBeVisible();
    const projectId = await star.getAttribute('data-project-id');
    expect(projectId).toBeTruthy();

    const wasStarred = (await star.locator('i').getAttribute('class'))?.includes('starred') ?? false;

    const [toggleOnResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes(`/-_-api/v1/favoriteProjects/${projectId}`) && res.request().method() === 'POST'),
      star.click(),
    ]);
    expect(toggleOnResponse.ok()).toBeTruthy();
    const firstToggleBody = await toggleOnResponse.json();
    expect(typeof firstToggleBody.favored).toBe('boolean');
    expect(firstToggleBody.favored).toBe(!wasStarred);

    // Toggle back to the original state so this test doesn't leave a permanent side effect.
    const [toggleOffResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes(`/-_-api/v1/favoriteProjects/${projectId}`) && res.request().method() === 'POST'),
      star.click(),
    ]);
    const secondToggleBody = await toggleOffResponse.json();
    expect(secondToggleBody.favored).toBe(wasStarred);
  });

  test('starring and unstarring an organization from the Favorite sidebar panel persists', async ({ page }) => {
    await page.goto('/');
    await page.click('#sidebar-open-btn');
    await page.click('a[href="#myOrganizationList"]');

    // Skip the pseudo-org's own empty <div class="star-org flex-item"></div> (no
    // data-organization-id, not interactive) by requiring the attribute -- matches a real
    // organization the admin belongs to, already visible without needing the ".all-orgs" reveal
    // click (that only hides/shows each org's nested *project* list, not the org row itself).
    const star = page.locator('.star-org[data-organization-id]').first();
    await expect(star).toBeVisible();
    const orgId = await star.getAttribute('data-organization-id');
    expect(orgId).toBeTruthy();

    const wasStarred = (await star.locator('i').getAttribute('class'))?.includes('starred') ?? false;

    const [toggleOnResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes(`/-_-api/v1/favoriteOrganizations/${orgId}`) && res.request().method() === 'POST'),
      star.click(),
    ]);
    expect(toggleOnResponse.ok()).toBeTruthy();
    const firstToggleBody = await toggleOnResponse.json();
    expect(firstToggleBody.favored).toBe(!wasStarred);

    const [toggleOffResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes(`/-_-api/v1/favoriteOrganizations/${orgId}`) && res.request().method() === 'POST'),
      star.click(),
    ]);
    const secondToggleBody = await toggleOffResponse.json();
    expect(secondToggleBody.favored).toBe(wasStarred);
  });

  test('starring and unstarring an issue from the issue view page persists', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const issueNumber = requireSeed('issueNumber');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    const star = page.locator('.favorite-issue');
    await expect(star).toBeVisible();

    const wasStarred = (await star.locator('i').getAttribute('class'))?.includes('starred') ?? false;

    const [toggleOnResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/-_-api/v1/favoriteIssues/') && res.request().method() === 'POST'),
      star.click(),
    ]);
    expect(toggleOnResponse.ok()).toBeTruthy();
    const firstToggleBody = await toggleOnResponse.json();
    expect(firstToggleBody.favored).toBe(!wasStarred);

    // #9 fixed (see yona.Usermenu.js's _bindOnce()): this toggle used to need an in-page
    // el.click() dispatch here to work around a permanent click-blocking overlay left by the
    // shared <yona-dialog id="yonaDialog">. A real locator.click() now works -- see the
    // dedicated repro below for what actually caused that overlay.
    const [toggleOffResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/-_-api/v1/favoriteIssues/') && res.request().method() === 'POST'),
      star.click(),
    ]);
    const secondToggleBody = await toggleOffResponse.json();
    expect(secondToggleBody.favored).toBe(wasStarred);
  });

  // #9 repro/regression: a $yona.notify() toast (fired here by the issue-star toggle above) must
  // never leave anything on the page intercepting clicks. Root cause turned out NOT to be
  // notify()/Toast/Dialog CSS at all (a plain, standalone $yona.notify() call was confirmed live
  // to leave the page fully clickable) -- it was yona.Usermenu.js's afterUsermenuLoaded()
  // being invoked twice per page load (once immediately for elements already present in the
  // initial HTML, once again after the GNB usermenu AJAX partial loads, for elements inside
  // that partial). `.favorite-issue` is rendered both ways -- once directly on the issue page
  // itself, and once inside the AJAX-loaded sidebar "favorite issues" list -- so it matched both
  // afterUsermenuLoaded() passes and got a second, duplicate click listener. A single real click
  // fired two POSTs to /-_-api/v1/favoriteIssues/{id}; the second lost the DB unique-constraint
  // race (500), and its .catch() handler called $yona.alert(...), which opens the shared
  // <yona-dialog id="yonaDialog"> as a real showModal() dialog. That native <dialog> promotes
  // itself (and its full-viewport ::backdrop) to the browser's top layer -- elementFromPoint() at
  // ANY on-screen coordinate resolved to it, even though the dialog's own host element reports a
  // collapsed, off-screen bounding box -- and it never got dismissed, permanently swallowing every
  // later click on the page. Fixed by making afterUsermenuLoaded()'s listener bindings idempotent
  // per element (yona.Usermenu.js's _bindOnce()), so a single click never double-fires again.
  test('a notify() toast from the favorite-issue toggle does not block a later real click elsewhere on the page', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const issueNumber = requireSeed('issueNumber');

    await page.goto(`/${owner}/${name}/issue/${issueNumber}`);
    const star = page.locator('.favorite-issue');
    await expect(star).toBeVisible();
    const wasStarred = (await star.locator('i').getAttribute('class'))?.includes('starred') ?? false;

    // Trigger the toast (and, pre-fix, the duplicate request + stray error dialog) via a real click.
    const [toggleResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/-_-api/v1/favoriteIssues/') && res.request().method() === 'POST'),
      star.click(),
    ]);
    expect(toggleResponse.ok()).toBeTruthy();

    // Give the toast (and, pre-fix, the stray dialog) time to fully appear.
    await page.waitForTimeout(500);

    // Now attempt a REAL locator.click() on a completely unrelated element -- the GNB sidebar
    // toggle button -- and assert the click actually registered (the sidebar actually opens),
    // rather than silently no-op'ing behind an invisible full-page overlay.
    const sidebar = page.locator('#mySidenav');
    await expect(sidebar).toHaveCSS('width', '0px');
    await page.click('#sidebar-open-btn');
    await expect(sidebar).not.toHaveCSS('width', '0px');

    // Clean up: close the sidebar and restore the star's original state via real clicks too.
    await page.click('#sidebar-open-btn');
    const [restoreResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/-_-api/v1/favoriteIssues/') && res.request().method() === 'POST'),
      star.click(),
    ]);
    const restoreBody = await restoreResponse.json();
    expect(restoreBody.favored).toBe(wasStarred);
  });
});
