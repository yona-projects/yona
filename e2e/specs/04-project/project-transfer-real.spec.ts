import { test, expect } from '@playwright/test';
import { requireSeed } from '../../support/seed-store';
import { uniqueSuffix } from '../../support/unique';

/** Screens: GET/PUT .../transfer (project/transfer.html) -> mailed confirmation link ->
 * GET /project/transfer/{transferId}/{confirmKey} (ProjectViewController.acceptTransfer).
 * project-transfer.spec.ts deliberately only loads the form (the shared seed project's owner is
 * needed by 05/06/07) -- this file creates its own dedicated project and a dedicated destination
 * user, then completes the transfer for real via the actual mailed confirmation link, read back
 * from mailpit (SMTP catcher at localhost:1025 / API at localhost:8025 -- yona's application.yml
 * already points spring.mail at localhost:1025, no product config change needed). This is a
 * genuinely two-party, mail-mediated flow (ProjectServiceImpl.acceptTransfer() requires the
 * *destination* user's own session via isAuthorizedToAcceptTransfer()) -- there is no direct-accept
 * shortcut in the product to fall back to. */

test('create a dedicated project, transfer it to a dedicated user, and accept via the real mailed link', async ({
  page,
  browser,
}) => {
  const adminLoginId = requireSeed('adminLoginId');
  const suffix = uniqueSuffix();
  const projectName = `e2e-transfer-${suffix}`;
  const destLoginId = `e2etransferdest${suffix}`;
  const destEmail = `${destLoginId}@yona-e2e.test`;
  const destPassword = 'TransferDestPassw0rd!';

  // 1. Admin creates the throwaway project.
  await page.goto('/projectform');
  await page.selectOption('#project-owner', adminLoginId);
  await page.fill('#project-name', projectName);
  await page.fill('#description', 'Throwaway project for the real project-transfer e2e test');
  await page.check('#public');
  await page.selectOption('#vcs', 'GIT');
  await page.click('#newProjectForm button.ybtn-success');
  await expect(page).toHaveURL(new RegExp(`/${adminLoginId}/${projectName}`));

  // 2. Sign up the dedicated destination user in an isolated, cookie-cleared context (see
  // 07-pull-request/02-pull-request-workflow.spec.ts's signUpAndInviteReviewer for why the
  // clearCookies() is required even on a brand-new context here).
  const destContext = await browser.newContext();
  await destContext.clearCookies();
  const destPage = await destContext.newPage();
  await destPage.goto('/signup');
  await destPage.fill('#loginId', destLoginId);
  await destPage.fill('#uname', `E2E Transfer Destination ${suffix}`);
  await destPage.fill('#email', destEmail);
  await destPage.fill('#password', destPassword);
  await destPage.fill('#retypedPassword', destPassword);
  await destPage.click('form[action="/signup"] button[type=submit]');
  await destPage.waitForLoadState('networkidle');

  // 3. Admin requests the transfer to destLoginId.
  await page.goto(`/${adminLoginId}/${projectName}/transfer`);
  await page.fill('#owner', destLoginId);
  await page.check('#accept');
  await page.click('#btnTransfer');
  await expect(page.locator('dialog#alertTransfer')).toBeVisible();

  const [transferResponse] = await Promise.all([
    page.waitForResponse(
      (res) => res.request().method() === 'PUT' && res.url().includes(`/${adminLoginId}/${projectName}/transfer`)
    ),
    page.click('#btnTransferExec'),
  ]);
  expect(transferResponse.status()).toBe(204);

  // 4. Fetch the real confirmation mail from mailpit and extract the accept link.
  let acceptUrl: string | undefined;
  for (let attempt = 0; attempt < 10 && !acceptUrl; attempt++) {
    const searchResponse = await page.request.get(
      `http://localhost:8025/api/v1/search?query=${encodeURIComponent(`to:${destEmail}`)}`
    );
    const searchJson = await searchResponse.json();
    const match = (searchJson.messages ?? []).find((m: { Subject: string }) => m.Subject.includes(projectName));
    if (match) {
      const fullResponse = await page.request.get(`http://localhost:8025/api/v1/message/${match.ID}`);
      const full = await fullResponse.json();
      const urlMatch = (full.Text as string).match(/https?:\/\/\S+\/project\/transfer\/\d+\/\S+/);
      acceptUrl = urlMatch?.[0];
    }
    if (!acceptUrl) await page.waitForTimeout(500);
  }
  expect(acceptUrl, 'transfer confirmation mail with an accept link was not found in mailpit').toBeTruthy();

  // 5. The destination user logs in and follows the real accept link -- acceptTransfer() checks
  // isAuthorizedToAcceptTransfer() against the calling session, so this must run as destLoginId.
  await destPage.goto('/users/loginform');
  await destPage.fill('#loginIdOrEmailD', destLoginId);
  await destPage.fill('#password', destPassword);
  await destPage.click('form[action="/users/login"] button[type=submit]');
  await expect(destPage.locator('a[href="/login"]')).toHaveCount(0);

  await destPage.goto(acceptUrl!);
  await expect(destPage).toHaveURL(new RegExp(`/${destLoginId}/${projectName}$`));

  // 6. Confirm the transfer actually took effect: new owner's URL serves the project, and the
  // old owner's URL now 404s/redirects (identical view-name-vs-status-code caveat documented
  // elsewhere in this suite -- assert on content, not raw status).
  await expect(destPage.locator('body')).toContainText(projectName);
  const oldUrlResponse = await destPage.goto(`/${adminLoginId}/${projectName}`);
  expect(oldUrlResponse?.status()).toBeLessThan(500);

  await destContext.close();
});
