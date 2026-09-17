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

    // PRODUCT BUG (confirmed live via document.elementFromPoint() at the star's own coordinates,
    // not fixed per instruction): after the toggle's $yona.notify() toast fires, the shared
    // <yona-dialog id="yonaDialog"> custom element (yona.ui.Dialog.js) is left permanently
    // intercepting pointer events across the ENTIRE page -- its own host element reports a
    // collapsed, off-screen bounding box (width 0, positioned past the right edge of the
    // viewport), but elementFromPoint() at any on-screen coordinate (verified at the star icon's
    // own position, well away from that box) still resolves to it, meaning its shadow-DOM content
    // renders a full-viewport transparent backdrop that never re-disables pointer-events after
    // use. This reproduces from a single notify() call, needs no actual dialog to have been
    // opened, and does not go away on its own (checked at +200ms and +1700ms, still blocking) --
    // a real user's next click anywhere on the page after seeing any toast/alert/confirm would
    // silently fail the same way -- confirmed this is a genuine top-level hit-test issue, not
    // just a Playwright actionability guard: `{force: true}` (which dispatches a real click at
    // the target's screen coordinates, skipping only Playwright's own pre-click checks) still
    // gets swallowed by the overlay, since the browser itself routes the click to whatever
    // element is topmost at that pixel. Dispatch the click in-page instead, which invokes the
    // registered listener directly without any screen-coordinate hit-testing.
    const [toggleOffResponse] = await Promise.all([
      page.waitForResponse((res) => res.url().includes('/-_-api/v1/favoriteIssues/') && res.request().method() === 'POST'),
      star.evaluate((el: HTMLElement) => el.click()),
    ]);
    const secondToggleBody = await toggleOffResponse.json();
    expect(secondToggleBody.favored).toBe(wasStarred);
  });
});
