import type { Locator, Page } from '@playwright/test';

/**
 * Many yona pages attach their click handlers in a DOMContentLoaded listener, and the layout loads several
 * type="module" widget bundles that defer DOMContentLoaded. On a cold or slow run an element rendered by the
 * server is therefore visible and clickable before it has a handler, and a click in that window does nothing
 * (it can even just change the URL hash). Playwright's actionability checks cannot see this.
 *
 * Call documentReady() after an assertion has proven that the document you want is the one on screen (for
 * example an element only the reloaded page renders) and before interacting with it.
 */
export async function documentReady(page: Page): Promise<void> {
  await page.waitForLoadState('load');
}

/**
 * Run an action whose handler does fetch(...) and then location.reload(), and wait for the NEW document to
 * finish loading. `page.waitForLoadState('load')` raced against the click does not do this: the current
 * document has already loaded, so it resolves at once and the next step runs against the old page.
 */
export async function afterReload(page: Page, action: () => Promise<unknown>): Promise<void> {
  const navigated = page.waitForEvent('framenavigated', { predicate: (frame) => frame === page.mainFrame() });
  await action();
  await navigated;
  await page.waitForLoadState('load');
}

export const clickAndReload = (page: Page, target: Locator): Promise<void> => afterReload(page, () => target.click());
