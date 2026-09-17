/** Short run-scoped suffix so re-running the suite against a DB that already has data from a
 * previous run (bootstrap-setup only works once, so full resets are opt-in -- see README) never
 * collides on a unique loginId/project name/org name. */
export function uniqueSuffix(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
}
