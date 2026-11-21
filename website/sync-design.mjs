// Pulls design.css from the hypai.org repo, which is its canonical home, and
// stamps design.lock with the hash of what was pulled.
//
//   node sync-design.mjs          copy if the source differs, then re-stamp
//   node sync-design.mjs --check  report only, exit 1 if out of date
//
// Requires the hypai repo checked out beside this one. build.mjs verifies the
// stamp on every build, so a copy edited by hand cannot ship; this script is
// how a change made upstream gets here.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, resolve } from 'node:path';

const HERE = import.meta.dirname;
const LOCK = join(HERE, 'design.lock');
const COPY = join(HERE, 'design.css');

// Fion/website -> Fion -> GitHub -> Hypai/design.css
const SOURCE = resolve(HERE, '..', '..', 'Hypai', 'design.css');

const sha = buf => createHash('sha256').update(buf).digest('hex');
const check = process.argv.includes('--check');

if (!existsSync(SOURCE)) {
  console.error(`✗ canonical design.css not found at ${SOURCE}`);
  console.error('  Check out the hypai repo beside this one, then run again.');
  process.exit(1);
}

const source = readFileSync(SOURCE, 'utf8');
const current = existsSync(COPY) ? readFileSync(COPY, 'utf8') : null;
let lock = null;
try {
  lock = JSON.parse(readFileSync(LOCK, 'utf8'));
} catch {}

const sourceHash = sha(source);
const lockValid = lock &&
  lock.file === 'design.css' &&
  lock.sha256 === sourceHash &&
  lock.source === 'hypai.org repo — design.css' &&
  /^\d{4}-\d{2}-\d{2}$/.test(lock.syncedAt);

if (current === source && lockValid) {
  console.log(`✓ design.css and design.lock up to date  sha256 ${sourceHash.slice(0, 16)}`);
  process.exit(0);
}

if (check) {
  if (current !== source) {
    console.error('✗ design.css is out of date with the hypai repo');
    console.error(`  local  ${current === null ? '(missing)' : sha(current).slice(0, 16)}`);
    console.error(`  source ${sourceHash.slice(0, 16)}`);
  }
  if (!lockValid) {
    console.error('✗ design.lock is missing, malformed or does not describe the canonical design.css');
  }
  console.error('  Run: node sync-design.mjs');
  process.exit(1);
}

if (current !== source) writeFileSync(COPY, source);
writeFileSync(LOCK, JSON.stringify({
  file: 'design.css',
  sha256: sourceHash,
  source: 'hypai.org repo — design.css',
  syncedAt: new Date().toISOString().slice(0, 10),
}, null, 2) + '\n');

console.log(`✓ design.css and design.lock synced  sha256 ${sourceHash.slice(0, 16)}`);
console.log('  Rebuild and commit changed files.');
