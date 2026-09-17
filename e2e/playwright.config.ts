import { defineConfig, devices } from '@playwright/test';

/**
 * yona is booted separately (see README.md) with `--spring.profiles.active=h2` so this suite
 * never depends on Docker/Testcontainers -- every screen here is reachable from a single
 * zero-setup embedded DB. baseURL matches Spring Boot's default port; override with
 * YONA_BASE_URL if the app is running elsewhere.
 */
const baseURL = process.env.YONA_BASE_URL ?? 'http://localhost:8080';

export default defineConfig({
  testDir: './specs',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  // yona's screens are stateful (project/issue/PR numbers accumulate) -- parallel workers
  // racing to create same-named entities would collide, so specs run sequentially per file
  // but files themselves stay independent via the seed data written by global setup.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      // Bootstrap creates the very first admin account (a fresh H2 DB has zero users, and
      // BootstrapSetupInterceptor force-redirects every request to /bootstrap-setup until one
      // exists) and seeds an organization/project/issue/PR/wiki page/post/milestone that every
      // "view an existing X" spec below reads from seed.json -- this is the one thing every
      // other project depends on, matching Playwright's own setup-project convention.
      name: 'setup',
      testMatch: /global\.setup\.ts/,
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Most specs act as the bootstrapped admin -- specs that need to be logged OUT (login
        // page itself, signup) or logged in as a DIFFERENT user override this per-test via
        // `test.use({ storageState: ... })` or a fresh browser context.
        storageState: './.auth/admin.json',
      },
      dependencies: ['setup'],
    },
  ],
});
