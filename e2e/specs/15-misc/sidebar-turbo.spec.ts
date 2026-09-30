import {test, expect} from '@playwright/test';
import {requireSeed} from '../../support/seed-store';

test.describe('Turbo sidebar', () => {
  test.use({hasTouch: true});

  for (const width of [390, 1280]) {
    test(`opening and closing at ${width}px preserves the current document and unsaved form`, async ({page}) => {
      await page.setViewportSize({width, height: 844});
      await page.goto('/user/editform?sidebar-check=1#unsaved-profile');
      const originalUrl = page.url();
      const field = page.locator('#frmBasic input[name=name]');
      await field.fill('Unsaved sidebar profile');
      await field.evaluate(element => Reflect.set(window, '__sidebarOriginalField', element));
      const originalBounds = (await field.boundingBox())!;
      const navigations: string[] = [];
      const fragments: string[] = [];
      page.on('request', request => {
        if (request.isNavigationRequest() && request.frame() === page.mainFrame()) navigations.push(request.url());
        if (new URL(request.url()).pathname === '/user/sidebar') fragments.push(request.headers()['turbo-frame']);
      });
      for (let attempt = 0; attempt < 2; attempt++) {
        await page.locator('[data-sidebar-toggle]').tap();
        await expect(page.locator('#sidebar')).toBeVisible();
        await expect(page.locator('#sidebar [data-sidebar-refresh]')).toBeVisible();
        const currentBounds = (await field.boundingBox())!;
        expect(Math.abs(currentBounds.x - originalBounds.x)).toBeLessThanOrEqual(1);
        expect(Math.abs(currentBounds.width - originalBounds.width)).toBeLessThanOrEqual(1);
        const panel = (await page.locator('#sidebar').boundingBox())!;
        const close = (await page.locator('#sidebar [data-sidebar-close]').boundingBox())!;
        if (attempt === 1) {
          await page.locator('#sidebar').evaluate(panel => {
            const overflow = document.createElement('div');
            overflow.style.height = '1500px';
            panel.append(overflow);
            panel.scrollTop = panel.scrollHeight;
          });
          await expect(page.locator('#sidebar [data-sidebar-close]')).toBeInViewport();
        }
        expect(Math.abs(close.x + close.width - panel.x - panel.width)).toBeLessThanOrEqual(1);
        await page.locator('#sidebar [data-sidebar-close]').tap();
        await expect(page.locator('#sidebar')).toBeHidden();
        await expect(field).toHaveValue('Unsaved sidebar profile');
        expect(await field.evaluate(element => element === Reflect.get(window, '__sidebarOriginalField'))).toBe(true);
        await expect(page).toHaveURL(originalUrl);
      }
      expect(navigations).toEqual([]);
      expect(fragments).toEqual(['sidebar']);
      await expect(page.locator('#mainFrameId, iframe[name=mainFrame]')).toHaveCount(0);
    });
  }

  test('closing while the first fragment is pending does not reopen it or discard typing', async ({page}) => {
    await page.goto('/user/editform');
    const {promise: requested, resolve: markRequested} = Promise.withResolvers<void>();
    const {promise: gate, resolve: release} = Promise.withResolvers<void>();
    await page.route('**/user/sidebar', async route => {
      markRequested();
      await gate;
      await route.continue();
    });
    try {
      await page.locator('[data-sidebar-toggle]').click();
      await requested;
      await expect(page.locator('#sidebar [data-sidebar-status]')).toBeVisible();
      await page.locator('#sidebar [data-sidebar-close]').click();
      await page.locator('#frmBasic input[name=name]').fill('Typed while sidebar was loading');
      release();
      await expect(page.locator('#sidebar [data-sidebar-refresh]')).toBeAttached();
      await expect(page.locator('#sidebar')).toBeHidden();
      await expect(page.locator('#frmBasic input[name=name]')).toHaveValue('Typed while sidebar was loading');
      await expect(page).toHaveURL(/\/user\/editform$/);
    } finally { release(); }
  });

  test('failed sidebar requests leave the page usable and explicit Retry loads the real fragment', async ({page}) => {
    await page.goto('/user/editform#keep-form');
    const field = page.locator('#frmBasic input[name=name]');
    await field.fill('Keep on failure');
    let requests = 0;
    await page.route('**/user/sidebar', async route => {
      requests++;
      if (requests === 1) await route.fulfill({status: 503, contentType: 'text/html', body: '<h1>Unavailable</h1>'});
      else await route.continue();
    });
    await page.locator('[data-sidebar-toggle]').click();
    await expect(page.locator('#sidebar [role=alert]')).toContainText('Could not load sidebar');
    await expect(field).toHaveValue('Keep on failure');
    await page.locator('#sidebar').getByRole('button', {name: 'Retry', exact: true}).click();
    await expect(page.locator('#sidebar #sidebar-myOrganizationList')).toBeAttached();
    await expect(page.locator('#sidebar [role=alert]')).toHaveCount(0);
    expect(requests).toBe(2);
    await expect(field).toHaveValue('Keep on failure');
    await expect(page).toHaveURL(/\/user\/editform#keep-form$/);
  });

  test('an expired session shows a sign-in link without navigating away from unsaved input', async ({page}) => {
    await page.goto('/user/editform#expired-session');
    const field = page.locator('#frmBasic input[name=name]');
    await field.fill('Unsaved during session expiry');
    await page.context().clearCookies();
    await page.locator('[data-sidebar-toggle]').click();
    await expect(page.locator('#sidebar [role=alert]')).toBeVisible();
    await expect(page.locator('#sidebar').getByRole('link', {name: 'Sign in', exact: true})).toHaveAttribute('href', '/users/loginform');
    await expect(field).toHaveValue('Unsaved during session expiry');
    await expect(page).toHaveURL(/\/user\/editform#expired-session$/);
  });

  test('an explicit reload restores the preference without creating a framed page', async ({page}) => {
    await page.goto('/user/editform#restore-sidebar');
    await page.locator('[data-sidebar-toggle]').click();
    await expect(page.locator('#sidebar [data-sidebar-refresh]')).toBeVisible();
    await page.reload();
    await expect(page.locator('#sidebar [data-sidebar-refresh]')).toBeVisible();
    await expect(page).toHaveURL(/\/user\/editform#restore-sidebar$/);
    await expect(page.locator('#mainFrameId, iframe[name=mainFrame]')).toHaveCount(0);
    await page.locator('#sidebar [data-sidebar-close]').click();
    await page.reload();
    await expect(page.locator('#sidebar')).toBeHidden();
    await expect(page.locator('#sidebar')).not.toHaveAttribute('src');
  });
});

test.describe('standalone sidebar without JavaScript', () => {
  test.use({javaScriptEnabled: false});

  test('projects are readable native links rather than iframe targets', async ({page}) => {
    const owner = requireSeed('projectOwner');
    const project = requireSeed('projectName');
    await page.goto('/user/sidebar');
    const projectLink = page.locator(`#sidebar .project-name > a[href="/${owner}/${project}/go"]`).first();
    await expect(projectLink).toBeVisible();
    await projectLink.click();
    await expect(page).toHaveURL(new RegExp(`/${owner}/${project}(?:/|$)`));
    await expect(page.locator('#mainFrameId')).toHaveCount(0);
  });
});
