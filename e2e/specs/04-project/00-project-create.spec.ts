import { test, expect } from '@playwright/test';
import { requireSeed, writeSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screens: GET /projectform (project/create.html) -> GET /{owner}/{projectName} (project/home.html). */

test('create a PUBLIC Git project with every menu toggle left on', async ({ page }) => {
  const adminLoginId = requireSeed('adminLoginId');
  const projectName = `e2e-git-${uniqueSuffix()}`;

  await page.goto('/projectform');
  await page.selectOption('#project-owner', adminLoginId);
  await page.fill('#project-name', projectName);
  await page.fill('#description', 'Seed project created by the e2e suite');
  await page.check('#public');
  await page.selectOption('#vcs', 'GIT');
  await page.click('#newProjectForm button.ybtn-success');

  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}`));
  writeSeed({ projectOwner: adminLoginId, projectName });
});

test('create a PRIVATE Mercurial project', async ({ page }) => {
  const adminLoginId = requireSeed('adminLoginId');
  const projectName = `e2e-hg-${uniqueSuffix()}`;

  await page.goto('/projectform');
  await page.selectOption('#project-owner', adminLoginId);
  await page.fill('#project-name', projectName);
  await page.check('#private');
  await page.selectOption('#vcs', 'MERCURIAL');
  await page.click('#newProjectForm button.ybtn-success');

  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}`));
  writeSeed({ hgProjectOwner: adminLoginId, hgProjectName: projectName });
});

test('create a Subversion project (pull request menu is not offered)', async ({ page }) => {
  const adminLoginId = requireSeed('adminLoginId');
  const projectName = `e2e-svn-${uniqueSuffix()}`;

  await page.goto('/projectform');
  await page.selectOption('#project-owner', adminLoginId);
  await page.fill('#project-name', projectName);
  await page.selectOption('#vcs', 'SUBVERSION');
  await expect(page.locator('#svn')).toBeVisible();
  await page.click('#newProjectForm button.ybtn-success');

  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}`));
  writeSeed({ svnProjectOwner: adminLoginId, svnProjectName: projectName });
});

test('blank project name is rejected client-side (required attribute)', async ({ page }) => {
  await page.goto('/projectform');
  await page.fill('#project-name', '');
  const isValid = await page.locator('#project-name').evaluate((el: HTMLInputElement) => el.checkValidity());
  expect(isValid).toBe(false);
});
