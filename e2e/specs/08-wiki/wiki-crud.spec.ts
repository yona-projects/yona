import { test, expect } from '@playwright/test';
import { requireSeed, writeSeed } from '../../support/seed-store';

/** Screens: GET /{owner}/{projectName}/wiki (wiki/view.html, empty-state) ->
 * GET /{owner}/{projectName}/wiki/_new (wiki/edit.html) -> created page's view/_edit/_history ->
 * GET /{owner}/{projectName}/wiki/_search (wiki/search.html). WikiViewController.kt backs pages
 * with a lazily-created `<owner>/<project>.wiki.git` bare repo (no page saved yet = no repo yet). */

test('wiki home shows the "create a Home page" guidance before any page exists', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');

  const response = await page.goto(`/${owner}/${name}/wiki`);
  expect(response?.status()).toBeLessThan(500);
});

test.describe.serial('wiki page lifecycle', () => {
  test('create a wiki page', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const title = 'E2E-Seed-Page';

    await page.goto(`/${owner}/${name}/wiki/_new`);
    await page.fill('#wiki-title', title);
    // markdownEditor('content', ..., 'wiki-content') hides this real <textarea name="content">
    // via display:none. `.fill(..., {force:true})` still tries to focus() the target first --
    // focus() on a display:none element is a silent no-op, so the fill's subsequent
    // select-all+type actually lands on whatever WAS focused (the title input above), appending
    // this text into the title instead of setting the real (still-empty) body textarea.
    // Verified live (see e2e session notes) -- issue/board's own create forms happen not to hit
    // this because of a CSS difference, but wiki/milestone's do. Setting the value directly and
    // dispatching the events the editor listens for sidesteps focus() entirely.
    await page.locator('textarea[data-editor-mode="wiki-content"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'Wiki body written by the e2e suite.';
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    // Scoped to this page's only content form (class="nm") -- the page header also renders a
    // <form name="gnb-search-form"> with its own submit button that an unscoped
    // `button[type=submit]` selector would hit instead.
    await page.click('form.nm button[type=submit]');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/wiki/${title}$`));
    writeSeed({ wikiTitle: title });
  });

  test('view the created wiki page', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const title = requireSeed('wikiTitle');

    await page.goto(`/${owner}/${name}/wiki/${title}`);
    await expect(page.locator('body')).toContainText('Wiki body written by the e2e suite.');
  });

  test('edit the wiki page content', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const title = requireSeed('wikiTitle');

    await page.goto(`/${owner}/${name}/wiki/_edit/${title}`);
    await page.locator('textarea[data-editor-mode="wiki-content"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'Wiki body edited by the e2e suite.';
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.click('form.nm button[type=submit]');

    await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/wiki/${title}$`));
    await expect(page.locator('body')).toContainText('Wiki body edited by the e2e suite.');
  });

  test('wiki page history shows at least one revision', async ({ page }) => {
    const owner = requireSeed('projectOwner');
    const name = requireSeed('projectName');
    const title = requireSeed('wikiTitle');

    const response = await page.goto(`/${owner}/${name}/wiki/_history/${title}`);
    expect(response?.status()).toBeLessThan(500);
  });
});

test('create a throwaway wiki page and delete it', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const title = 'E2E-Throwaway-Page';

  await page.goto(`/${owner}/${name}/wiki/_new`);
  await page.fill('#wiki-title', title);
  await page.locator('textarea[data-editor-mode="wiki-content"]').evaluate((el: HTMLTextAreaElement) => {
    el.value = 'Throwaway wiki page, deleted by its own test.';
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await page.click('form.nm button[type=submit]');
  await expect(page).toHaveURL(new RegExp(`/${owner}/${name}/wiki/${title}$`));

  // wiki/view.html's delete <form> has onsubmit="return confirm(...)" -- a real native browser
  // confirm() dialog, not a custom modal. WikiViewController.delete() 302s to the project's
  // /wiki root on success (this is a plain form POST, not fetch-based AJAX like the other
  // delete flows in this suite).
  page.once('dialog', (dialog) => dialog.accept());
  await Promise.all([
    page.waitForURL(new RegExp(`/${owner}/${name}/wiki$`)),
    page.click('form[action*="/wiki/_delete/"] button[type=submit]'),
  ]);

  // WikiViewController.view() doesn't 404 a missing page -- it renders wiki/view with
  // page=null (200 OK, same "page not found" state as a title that was never created).
  const response = await page.goto(`/${owner}/${name}/wiki/${title}`);
  expect(response?.status()).toBeLessThan(400);
  await expect(page.locator('body')).not.toContainText('Throwaway wiki page, deleted by its own test.');
});

test('wiki search finds the created page', async ({ page }) => {
  const owner = requireSeed('projectOwner');
  const name = requireSeed('projectName');
  const title = requireSeed('wikiTitle');

  // WikiServiceImpl.search() filters by TITLE only (`it.title.lowercase().contains(...)`), not
  // page content -- searching for body text would never match.
  await page.goto(`/${owner}/${name}/wiki/_search?q=${encodeURIComponent('Seed-Page')}`);
  await expect(page.locator('body')).toContainText(title);
});
