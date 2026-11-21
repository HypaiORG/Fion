// privacy.html is filled from ../docs/privacy*.md, outside the Vercel project root,
// so Vercel must build locally: vercel build --prod && vercel deploy --prebuilt --prod

import { readFileSync, writeFileSync, mkdirSync, cpSync, rmSync, readdirSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, resolve } from 'node:path';
import vm from 'node:vm';
import { renderPrivacy } from './privacy.mjs';

const ROOT = import.meta.dirname;
const OUT = join(ROOT, 'dist');
const ASSETS = ['images', 'fonts/jost-600-latin.woff2', 'robots.txt', 'sitemap.xml'];
const PAGES = [
  {
    source: 'index.html',
    route: '/',
    design: ['base', 'bloom', 'nav', 'footer', 'motion'],
    styles: ['shared.css', 'index.css'],
    scripts: ['fion-hero.js', 'i18n.js', 'index.js']
  },
  {
    source: 'privacy.html',
    route: '/privacy',
    design: ['base', 'bloom', 'nav', 'footer', 'motion'],
    styles: ['shared.css', 'privacy.css'],
    scripts: ['i18n.js', 'privacy.js'],
    render: renderPrivacy
  }
];
const CLEAN_ROUTES = Object.fromEntries(PAGES.map(page => [page.source, page.route]));
const CONTENT_SLOT = '<!--content-->';

const sha = buf => createHash('sha256').update(buf).digest('hex');

const design = readFileSync(join(ROOT, 'design.css'), 'utf8');
const lock = JSON.parse(readFileSync(join(ROOT, 'design.lock'), 'utf8'));

if (sha(design) !== lock.sha256) {
  throw new Error(
    'design.css does not match design.lock.\n' +
    '  design.css is owned by the hypai.org repo. Change it there and run `node sync-design.mjs`.'
  );
}

const SOURCE = resolve(ROOT, '..', '..', 'Hypai', 'design.css');
if (existsSync(SOURCE)) {
  if (readFileSync(SOURCE, 'utf8') !== design) {
    throw new Error('design.css is stale. The hypai repo has a newer one. Run `node sync-design.mjs`.');
  }
  console.log(`  design.css verified against the hypai repo  sha256 ${lock.sha256.slice(0, 16)}`);
} else {
  console.log(`  design.css matches design.lock  sha256 ${lock.sha256.slice(0, 16)}  (source repo not present)`);
}

const sectionStart = name => {
  const start = design.indexOf(`/* ═══ ${name} `);
  if (start < 0) throw new Error(`design.css: missing ${name} section`);
  return start;
};
const arc = sectionStart('ARC FIELD');
const bloom = sectionStart('BLOOM');
const nav = sectionStart('NAV SHELL');
const footer = sectionStart('FOOTER');
const motion = sectionStart('REDUCED MOTION');
const DESIGN = {
  base: design.slice(0, arc),
  bloom: design.slice(bloom, nav),
  nav: design.slice(nav, footer),
  footer: design.slice(footer, motion),
  motion: design.slice(motion)
};
const designBundle = sections => sections.map(section => DESIGN[section]).join('\n');

const compactLines = value => value
  .split('\n')
  .map(line => line.trim())
  .filter(Boolean)
  .join('\n');

function minifyCss(value) {
  value = value.replace(/\/\*[\s\S]*?\*\//g, '');
  let out = '', quote = '', escaped = false;

  for (let i = 0; i < value.length; i++) {
    const char = value[i];
    if (quote) {
      out += char;
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = '';
      continue;
    }
    if (char === '"' || char === "'") {
      quote = char;
      out += char;
      continue;
    }
    if (/\s/.test(char)) {
      let next = i + 1;
      while (next < value.length && /\s/.test(value[next])) next++;
      const before = out.at(-1) || '';
      const after = value[next] || '';
      if (before && !/[{}:;,>]/.test(before) && !/[{}:;,>]/.test(after)) out += ' ';
      i = next - 1;
      continue;
    }
    if (char === '}' && out.at(-1) === ';') out = out.slice(0, -1);
    if (/[{}:;,>]/.test(char) && out.at(-1) === ' ') out = out.slice(0, -1);
    out += char;
  }
  return out.trim();
}

function minifyHtml(value) {
  const usedProperties = new Set([...value.matchAll(/var\(\s*(--[\w-]+)/g)].map(match => match[1]));
  value = value.replace(/(--[\w-]+)\s*:[^;{}]+;?/g,
    (declaration, name) => usedProperties.has(name) ? declaration : '');
  return compactLines(value
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<style>([\s\S]*?)<\/style>/g, (_, sheet) => `<style>${minifyCss(sheet)}</style>`)
    .replace(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g,
      (_, json) => `<script type="application/ld+json">${JSON.stringify(JSON.parse(json))}</script>`)
    .replace(/<svg\b[\s\S]*?<\/svg>/g, svg => svg.replace(/>\s+</g, '><')));
}

function replaceOnce(source, target, render, label) {
  const first = source.indexOf(target);
  if (first < 0 || source.indexOf(target, first + target.length) >= 0) {
    throw new Error(`${label}: expected exactly one match`);
  }
  return source.replace(target, () => render());
}

function rewriteInternalLinks(page, html) {
  html = html.replace(
    /href=(["'])(?:\.\/|\/)?(index\.html|privacy\.html)([?#][^"']*)?\1/g,
    (_, quote, target, suffix = '') => `href=${quote}${CLEAN_ROUTES[target]}${suffix}${quote}`
  );
  const stale = html.match(/href=["'](?:\.\/|\/)?(?:index|privacy)\.html(?:[?#][^"']*)?["']/g);
  if (stale) throw new Error(`${page}: internal .html link survived clean URL rewrite: ${stale.join(', ')}`);
  return html;
}

function inlineStyles(page, html) {
  const files = ['design.css', ...page.styles];
  const links = files.map(file => `<link rel="stylesheet" href="${file}">`).join('\n') + '\n';
  const css = [designBundle(page.design), ...page.styles.map(file => readFileSync(join(ROOT, file), 'utf8'))].join('\n');
  const relative = css
    .replace(/url\((["'])data:[\s\S]*?\1\)/g, '')
    .match(/url\((['"]?)(?!\/|data:|https?:|#)[^)'"]+/g);
  if (relative) throw new Error(`${page.source}: unanchored relative url(): ${relative.join(', ')}`);
  return replaceOnce(html, links, () => `<style>${css}</style>\n`, `${page.source}: stylesheet chain`);
}

function inlineScripts(page, html) {
  const tags = page.scripts.map(file => `<script src="${file}"></script>`).join('\n') + '\n';
  const source = page.scripts.map(file => readFileSync(join(ROOT, file), 'utf8').trim()).join('\n');
  return replaceOnce(html, tags, () => `<script>${source}</script>\n`, `${page.source}: script chain`);
}

function readDictionary() {
  const context = {};
  vm.createContext(context);
  vm.runInContext(`${readFileSync(join(ROOT, 'i18n.js'), 'utf8')}\nthis.out = FION_I18N;`, context);
  return context.out;
}
const DICT = readDictionary();

function checkText(page, html) {
  const used = new Set([...html.matchAll(/data-t(?:-aria|-alt)?="([^"]+)"/g)].map(match => match[1]));
  const missing = [];
  for (const lang of ['zh', 'en']) {
    for (const key of used) if (DICT[lang][key] === undefined) missing.push(`${key} (${lang})`);
  }
  if (missing.length) throw new Error(`${page}: data-t key with no translation: ${missing.join(', ')}`);
}

function checkTrees(page, html) {
  const zhAt = html.indexOf('id="content-zh"');
  const enAt = html.indexOf('id="content-en"');
  if (zhAt < 0 && enAt < 0) return;
  const endAt = html.indexOf('</main>', enAt);
  if (zhAt < 0 || enAt < zhAt || endAt < 0) throw new Error(`${page}: expected content-zh, then content-en, then </main>`);
  const headings = slice => (slice.match(/<h2\b/g) || []).length;
  const zh = headings(html.slice(zhAt, enAt));
  const en = headings(html.slice(enAt, endAt));
  if (zh !== en) throw new Error(`${page}: content-zh has ${zh} <h2> but content-en has ${en}. docs/privacy*.md must have matching sections.`);
}

const declaredPages = PAGES.map(page => page.source).sort();
const sourcePages = readdirSync(ROOT).filter(file => file.endsWith('.html')).sort();
if (JSON.stringify(declaredPages) !== JSON.stringify(sourcePages)) {
  throw new Error(`page manifest mismatch\n  declared ${declaredPages.join(', ')}\n  source   ${sourcePages.join(', ')}`);
}

const outputs = PAGES.map(page => {
  let html = readFileSync(join(ROOT, page.source), 'utf8');
  if (page.render) html = replaceOnce(html, CONTENT_SLOT, page.render, `${page.source}: content slot`);
  checkTrees(page.source, html);
  checkText(page.source, html);
  html = inlineStyles(page, html);
  html = inlineScripts(page, html);
  html = rewriteInternalLinks(page.source, html);
  return { page, sourceBytes: html.length, html: minifyHtml(html) };
});

for (const asset of ASSETS) {
  if (!existsSync(join(ROOT, asset))) throw new Error(`missing build asset: ${asset}`);
}

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

for (const output of outputs) {
  writeFileSync(join(OUT, output.page.source), output.html);
  console.log(`  ${output.page.source}  ${output.sourceBytes} -> ${output.html.length} bytes`);
}

for (const asset of ASSETS) {
  mkdirSync(join(OUT, asset, '..'), { recursive: true });
  cpSync(join(ROOT, asset), join(OUT, asset), { recursive: true });
}

console.log(`built ${PAGES.length} pages into dist/`);
