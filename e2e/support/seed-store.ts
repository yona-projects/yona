import * as fs from 'fs';
import * as path from 'path';

/**
 * Shared JSON store every spec file reads/writes through -- this is what makes the specs
 * "related": a later spec (e.g. issue view) depends on an id an earlier spec (issue create)
 * produced, rather than each spec re-creating its own throwaway fixtures. Keyed by an
 * arbitrary string per entity so unrelated specs never collide on the same key.
 */
const SEED_FILE = path.join(__dirname, '..', '.seed', 'seed.json');

export interface Seed {
  adminLoginId?: string;
  adminPassword?: string;
  orgName?: string;
  projectOwner?: string;
  projectName?: string;
  hgProjectOwner?: string;
  hgProjectName?: string;
  svnProjectOwner?: string;
  svnProjectName?: string;
  issueNumber?: number;
  issueCommentPosted?: boolean;
  codeDiffFirstCommit?: string;
  codeDiffSecondCommit?: string;
  pullRequestFeatureBranch?: string;
  pullRequestNumber?: number;
  wikiTitle?: string;
  postNumber?: number;
  milestoneId?: number;
  secondUserLoginId?: string;
  secondUserPassword?: string;
  [key: string]: unknown;
}

function readAll(): Seed {
  if (!fs.existsSync(SEED_FILE)) return {};
  return JSON.parse(fs.readFileSync(SEED_FILE, 'utf-8'));
}

export function readSeed(): Seed {
  return readAll();
}

export function writeSeed(partial: Partial<Seed>): void {
  const current = readAll();
  const merged = { ...current, ...partial };
  fs.mkdirSync(path.dirname(SEED_FILE), { recursive: true });
  fs.writeFileSync(SEED_FILE, JSON.stringify(merged, null, 2));
}

/** Fails fast with a clear message instead of a confusing undefined-property error deep in a
 * spec -- every downstream spec should call this for the seed values it depends on. */
export function requireSeed<K extends keyof Seed>(key: K): NonNullable<Seed[K]> {
  const value = readSeed()[key];
  if (value === undefined || value === null) {
    throw new Error(
      `Seed value "${String(key)}" is missing -- the spec that is supposed to produce it ` +
        `(see matrix.md) must run first and must have passed.`
    );
  }
  return value as NonNullable<Seed[K]>;
}
