// privacy.html is filled from ../docs/privacy*.md, outside the Vercel project root,
// so Vercel must build locally: vercel build --prod && vercel deploy --prebuilt --prod

import { readFileSync, writeFileSync, mkdirSync, cpSync, rmSync, readdirSync, existsSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join, resolve } from 'node:path';
import vm from 'node:vm';
import { SITE, LOCALES } from './i18n.mjs';
import { assertPrivacyParity, renderPrivacy } from './privacy.mjs';

const ROOT = import.meta.dirname;
const OUT = join(ROOT, 'dist');
const ASSETS = ['images', 'fonts/jost-600-latin.woff2', 'robots.txt'];
const DESIGN_SECTIONS = ['base', 'bloom', 'nav', 'footer', 'motion'];

const sha = buf => createHash('sha256').update(buf).digest('hex');
const escHtml = text => String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const escAttr = text => escHtml(text).replace(/"/g, '&quot;');

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

function readAppVersion() {
  const gradlePath = resolve(ROOT, '..', 'app', 'build.gradle.kts');
  if (!existsSync(gradlePath)) {
    throw new Error(`missing app/build.gradle.kts at ${gradlePath}`);
  }
  const match = readFileSync(gradlePath, 'utf8').match(/versionName\s*=\s*"([^"]+)"/);
  if (!match) throw new Error(`${gradlePath}: versionName not found`);
  return match[1];
}
const APP_VERSION = readAppVersion();

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

function minifyJs(source, label) {
  let out = '';
  let i = 0;
  const n = source.length;
  const lastNonSpace = () => {
    for (let k = out.length - 1; k >= 0; k--) {
      if (!/[ \t\n\r]/.test(out[k])) return out[k];
    }
    return '';
  };

  while (i < n) {
    const ch = source[i];
    const next = source[i + 1] || '';

    if (ch === "'" || ch === '"' || ch === '`') {
      const q = ch;
      out += ch;
      i++;
      while (i < n) {
        const c = source[i];
        out += c;
        if (c === '\\') {
          i++;
          if (i < n) out += source[i];
        } else if (c === q) {
          i++;
          break;
        }
        i++;
      }
      continue;
    }

    if (ch === '/' && next === '/') {
      i += 2;
      while (i < n && source[i] !== '\n') i++;
      continue;
    }

    if (ch === '/' && next === '*') {
      i += 2;
      while (i < n && !(source[i] === '*' && source[i + 1] === '/')) i++;
      i += 2;
      continue;
    }

    if (ch === '/' && /[(,=:[!&|?;{}\n]/.test(lastNonSpace() || '(')) {
      out += ch;
      i++;
      let inClass = false;
      while (i < n) {
        const c = source[i];
        out += c;
        if (c === '\\') {
          i++;
          if (i < n) out += source[i];
        } else if (c === '[') {
          inClass = true;
        } else if (c === ']') {
          inClass = false;
        } else if (c === '/' && !inClass) {
          i++;
          break;
        }
        i++;
      }
      continue;
    }

    if (ch === '\n' || ch === '\r') {
      while (i < n && /[ \t\r\n]/.test(source[i])) i++;
      if (out.length && out.at(-1) !== '\n') out += '\n';
      continue;
    }

    if (ch === ' ' || ch === '\t') {
      while (i < n && (source[i] === ' ' || source[i] === '\t')) i++;
      const prev = out.at(-1) || '';
      const after = source[i] || '';
      const isId = c => /[\w$]/.test(c);
      if (prev && after && prev !== '\n' && after !== '\n' && after !== '\r') {
        if ((isId(prev) && isId(after)) || (prev === after && (prev === '+' || prev === '-')) || (prev === '/' && after === '/')) {
          out += ' ';
        }
      }
      continue;
    }

    out += ch;
    i++;
  }

  const trimmed = out.trim();
  new vm.Script(trimmed, { filename: label });
  return trimmed;
}

function minifyHtml(value) {
  const usedProperties = new Set([...value.matchAll(/var\(\s*(--[\w-]+)/g)].map(match => match[1]));
  value = value
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<style>([\s\S]*?)<\/style>/g, (_, sheet) => {
      const pruned = sheet.replace(/(--[\w-]+)\s*:[^;{}]+;?/g,
        (declaration, name) => usedProperties.has(name) ? declaration : '');
      return `<style>${minifyCss(pruned)}</style>`;
    })
    .replace(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g,
      (_, json) => `<script type="application/ld+json">${JSON.stringify(JSON.parse(json))}</script>`)
    .replace(/<svg\b[\s\S]*?<\/svg>/g, svg => svg.replace(/>\s+</g, '><'));
  return compactLines(value);
}

function replaceOnce(source, target, render, label) {
  const first = source.indexOf(target);
  if (first < 0 || source.indexOf(target, first + target.length) >= 0) {
    throw new Error(`${label}: expected exactly one match`);
  }
  return source.replace(target, () => render());
}

function inlineStyles(sourceName, styles, html) {
  const files = ['design.css', ...styles];
  const links = files.map(file => `<link rel="stylesheet" href="${file}">`).join('\n') + '\n';
  const css = [designBundle(DESIGN_SECTIONS), ...styles.map(file => readFileSync(join(ROOT, file), 'utf8'))].join('\n');
  const relative = css
    .replace(/url\((["'])data:[\s\S]*?\1\)/g, '')
    .match(/url\((['"]?)(?!\/|data:|https?:|#)[^)'"]+/g);
  if (relative) throw new Error(`${sourceName}: unanchored relative url(): ${relative.join(', ')}`);
  return replaceOnce(html, links, () => `<style>${css}</style>\n`, `${sourceName}: stylesheet chain`);
}

const LANG_SWITCH_SCRIPT = minifyJs(`(() => {
  document.querySelectorAll('[data-set-lang]').forEach(a => {
    a.addEventListener('click', () => {
      document.cookie = 'lang=' + a.dataset.setLang + '; path=/; max-age=31536000; SameSite=Lax';
    });
  });
})();`, 'lang-switch.js');

function injectLangSwitch(sourceName, html) {
  return replaceOnce(html, '</body>', () => `<script>${LANG_SWITCH_SCRIPT}</script>\n</body>`, `${sourceName}: lang-switch`);
}

function inlineScripts(sourceName, scripts, html) {
  if (!scripts.length) return html;
  const tags = scripts.map(file => `<script src="${file}"></script>`).join('\n') + '\n';
  const source = scripts
    .map(file => minifyJs(readFileSync(join(ROOT, file), 'utf8'), file))
    .join('\n');
  return replaceOnce(html, tags, () => `<script>${source}</script>\n`, `${sourceName}: script chain`);
}

function checkTextKeys(sourceName, html) {
  const used = new Set([...html.matchAll(/data-t(?:-aria|-alt)?="([^"]+)"/g)].map(match => match[1]));
  const missing = [];
  for (const lang of ['zh', 'en']) {
    for (const key of used) {
      if (LOCALES[lang].t[key] === undefined) missing.push(`${key} (${lang})`);
    }
  }
  if (missing.length) throw new Error(`${sourceName}: missing translation keys: ${missing.join(', ')}`);
}

function localizeHtml(html, locale, pageType) {
  const t = locale.t;
  const isZh = locale.code === 'zh';

  html = html
    .replaceAll('{{lang}}', locale.htmlLang)
    .replaceAll('{{home}}', locale.homeRoute)
    .replaceAll('{{privacy}}', locale.privacyRoute)
    .replaceAll('{{lang-zh-attr}}', isZh ? ' aria-current="page"' : '')
    .replaceAll('{{lang-en-attr}}', isZh ? '' : ' aria-current="page"')
    .replaceAll('{{img-prefix}}', locale.code)
    .replaceAll('{{year}}', String(SITE.year));

  html = html.replace(/<([a-z0-9]+)([^>]*?)\s+data-t="([^"]+)"([^>]*)>([\s\S]*?)<\/\1>/gi,
    (full, tag, before, key, after, inner) => {
      if (inner.includes('<')) throw new Error(`data-t="${key}" on <${tag}> contains nested markup`);
      return `<${tag}${before}${after}>${escHtml(t[key])}</${tag}>`;
    });

  html = html.replace(/<([a-z0-9]+)([^>]*?)\s+data-t-aria="([^"]+)"([^>]*)>/gi,
    (_, tag, before, key, after) => {
      const attrs = `${before}${after}`.replace(/\s+aria-label="[^"]*"/g, '');
      return `<${tag}${attrs} aria-label="${escAttr(t[key])}">`;
    });

  html = html.replace(/<([a-z0-9]+)([^>]*?)\s+data-t-alt="([^"]+)"([^>]*)>/gi,
    (_, tag, before, key, after) => {
      const attrs = `${before}${after}`.replace(/\s+alt="[^"]*"/g, '');
      return `<${tag}${attrs} alt="${escAttr(t[key])}">`;
    });

  if (/\bdata-t(?:-aria|-alt)?=/.test(html)) {
    throw new Error(`${pageType} (${locale.code}): unresolved data-t* attribute survived localization`);
  }
  return html;
}

function renderFaq(locale) {
  const items = locale.faq.map(({ q, a }) =>
    `<details><summary><span>${escHtml(q)}</span><svg class="ico" aria-hidden="true"><use href="#i-chev"/></svg></summary><p>${escHtml(a)}</p></details>`
  ).join('\n');
  return `<div class="faq-list">\n${items}\n</div>`;
}

function renderPageData(locale) {
  const t = locale.t;
  const payload = {
    hero: locale.hero,
    aes: {
      sample: t['aes.sample'],
      locked: { step: t['aes.step.locked'], out: t['aes.out.locked'], verdict: t['aes.v.locked'] },
      plain: { step: t['aes.step.plain'], out: t['aes.out.plain'], verdict: t['aes.v.plain'] }
    }
  };
  return `<script>window.__FION__=${JSON.stringify(payload).replace(/<\//g, '<\\/')};</script>`;
}

function renderHeadMeta(locale, pageType) {
  const isHome = pageType === 'index';
  const route = isHome ? locale.homeRoute : locale.privacyRoute;
  const enUrl = SITE.origin + (isHome ? '/' : '/privacy');
  const zhUrl = SITE.origin + (isHome ? '/zh' : '/zh/privacy');
  const url = SITE.origin + route;
  const title = isHome ? locale.meta.indexTitle : locale.meta.privacyTitle;
  const desc = isHome ? locale.meta.indexDesc : locale.meta.privacyDesc;
  const ogImg = SITE.origin + locale.ogImage;

  const orgNode = {
    '@type': 'Organization',
    '@id': 'https://hypai.org/#organization',
    name: SITE.orgName,
    url: SITE.orgUrl
  };

  const jsonLd = isHome ? {
    '@context': 'https://schema.org',
    '@graph': [
      orgNode,
      {
        '@type': 'WebSite',
        '@id': `${SITE.origin}/#website`,
        name: 'Fion',
        alternateName: '飞鸿',
        url: `${SITE.origin}/`,
        inLanguage: ['en', 'zh-CN'],
        publisher: { '@id': 'https://hypai.org/#organization' }
      },
      {
        '@type': 'WebPage',
        '@id': `${url}#webpage`,
        url,
        name: title,
        description: desc,
        inLanguage: locale.htmlLang,
        isPartOf: { '@id': `${SITE.origin}/#website` },
        primaryImageOfPage: { '@type': 'ImageObject', url: ogImg, width: 1200, height: 630 }
      },
      {
        '@type': 'SoftwareApplication',
        '@id': `${SITE.origin}/#software`,
        name: locale.meta.appName,
        alternateName: locale.meta.appAltName,
        description: locale.meta.appDesc,
        applicationCategory: 'UtilitiesApplication',
        operatingSystem: 'Android 15+',
        softwareVersion: APP_VERSION,
        inLanguage: ['en', 'zh-CN'],
        url,
        downloadUrl: SITE.download,
        license: SITE.license,
        sameAs: SITE.repo,
        image: `${SITE.origin}/images/icon_128.png`,
        screenshot: ogImg,
        featureList: locale.meta.features,
        isAccessibleForFree: true,
        offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
        publisher: { '@id': 'https://hypai.org/#organization' }
      },
      {
        '@type': 'HowTo',
        name: locale.meta.howToName,
        inLanguage: locale.htmlLang,
        step: [
          { '@type': 'HowToStep', position: 1, name: locale.t['s1.t'], text: locale.t['s1.s'], url: `${url}#go` },
          { '@type': 'HowToStep', position: 2, name: locale.t['s2.t'], text: locale.t['s2.s'], url: `${url}#go` },
          { '@type': 'HowToStep', position: 3, name: locale.t['s3.t'], text: locale.t['s3.s'], url: `${url}#go` }
        ]
      },
      {
        '@type': 'FAQPage',
        inLanguage: locale.htmlLang,
        mainEntity: locale.faq.map(({ q, a }) => ({
          '@type': 'Question',
          name: q,
          acceptedAnswer: { '@type': 'Answer', text: a }
        }))
      }
    ]
  } : {
    '@context': 'https://schema.org',
    '@graph': [
      orgNode,
      {
        '@type': 'WebPage',
        '@id': `${url}#webpage`,
        url,
        name: title,
        description: desc,
        inLanguage: locale.htmlLang,
        isPartOf: { '@id': `${SITE.origin}/#website` }
      },
      {
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: locale.meta.homeBreadcrumb, item: SITE.origin + locale.homeRoute },
          { '@type': 'ListItem', position: 2, name: locale.meta.privacyBreadcrumb, item: url }
        ]
      }
    ]
  };

  return [
    `<title>${escHtml(title)}</title>`,
    `<meta name="description" content="${escAttr(desc)}">`,
    `<link rel="canonical" href="${url}">`,
    `<link rel="alternate" hreflang="en" href="${enUrl}">`,
    `<link rel="alternate" hreflang="zh-CN" href="${zhUrl}">`,
    `<link rel="alternate" hreflang="x-default" href="${enUrl}">`,
    `<meta property="og:type" content="website">`,
    `<meta property="og:title" content="${escAttr(title)}">`,
    `<meta property="og:description" content="${escAttr(desc)}">`,
    `<meta property="og:url" content="${url}">`,
    `<meta property="og:site_name" content="Fion">`,
    `<meta property="og:locale" content="${locale.ogLocale}">`,
    `<meta property="og:locale:alternate" content="${locale.ogLocaleAlt}">`,
    `<meta property="og:image" content="${ogImg}">`,
    `<meta property="og:image:width" content="1200">`,
    `<meta property="og:image:height" content="630">`,
    `<meta property="og:image:alt" content="${escAttr(locale.ogAlt)}">`,
    `<meta name="twitter:card" content="summary_large_image">`,
    `<meta name="twitter:title" content="${escAttr(title)}">`,
    `<meta name="twitter:description" content="${escAttr(desc)}">`,
    `<meta name="twitter:image" content="${ogImg}">`,
    `<script type="application/ld+json">${JSON.stringify(jsonLd)}</script>`
  ].join('\n');
}

function buildSitemap(entries) {
  const urls = entries.map(({ loc, enHref, zhHref, lastmod }) => [
    '  <url>',
    `    <loc>${loc}</loc>`,
    `    <lastmod>${lastmod}</lastmod>`,
    `    <xhtml:link rel="alternate" hreflang="en" href="${enHref}"/>`,
    `    <xhtml:link rel="alternate" hreflang="zh-CN" href="${zhHref}"/>`,
    `    <xhtml:link rel="alternate" hreflang="x-default" href="${enHref}"/>`,
    '  </url>'
  ].join('\n')).join('\n');

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    urls,
    '</urlset>',
    ''
  ].join('\n');
}

function latestMtimeDate(paths) {
  let maxMs = 0;
  for (const p of paths) {
    if (existsSync(p)) maxMs = Math.max(maxMs, statSync(p).mtimeMs);
  }
  return new Date(maxMs || Date.now()).toISOString().slice(0, 10);
}

const TEMPLATES = ['404.html', 'index.html', 'privacy.html'];
const sourcePages = readdirSync(ROOT).filter(file => file.endsWith('.html')).sort();
if (JSON.stringify(TEMPLATES) !== JSON.stringify(sourcePages)) {
  throw new Error(`template manifest mismatch\n  declared ${TEMPLATES.join(', ')}\n  source   ${sourcePages.join(', ')}`);
}

assertPrivacyParity();

const indexTemplate = readFileSync(join(ROOT, 'index.html'), 'utf8');
const privacyTemplate = readFileSync(join(ROOT, 'privacy.html'), 'utf8');
const notFoundTemplate = readFileSync(join(ROOT, '404.html'), 'utf8');

checkTextKeys('index.html', indexTemplate);
checkTextKeys('privacy.html', privacyTemplate);

const outputs = [];

for (const lang of ['en', 'zh']) {
  const locale = LOCALES[lang];

  let home = indexTemplate;
  home = replaceOnce(home, '<!--head-meta-->', () => renderHeadMeta(locale, 'index'), `index.html (${lang}): head-meta`);
  home = replaceOnce(home, '<!--faq-->', () => renderFaq(locale), `index.html (${lang}): faq`);
  home = replaceOnce(home, '<!--page-data-->', () => renderPageData(locale), `index.html (${lang}): page-data`);
  home = localizeHtml(home, locale, 'index');
  home = inlineStyles('index.html', ['shared.css', 'index.css'], home);
  home = inlineScripts('index.html', ['fion-hero.js', 'index.js'], home);
  home = injectLangSwitch(`index.html (${lang})`, home);
  outputs.push({
    outPath: lang === 'en' ? 'index.html' : 'zh/index.html',
    sourceBytes: home.length,
    html: minifyHtml(home)
  });

  let priv = privacyTemplate;
  priv = replaceOnce(priv, '<!--head-meta-->', () => renderHeadMeta(locale, 'privacy'), `privacy.html (${lang}): head-meta`);
  priv = replaceOnce(priv, '<!--content-->', () => renderPrivacy(lang), `privacy.html (${lang}): content`);
  priv = localizeHtml(priv, locale, 'privacy');
  priv = inlineStyles('privacy.html', ['shared.css', 'privacy.css'], priv);
  priv = injectLangSwitch(`privacy.html (${lang})`, priv);
  outputs.push({
    outPath: lang === 'en' ? 'privacy.html' : 'zh/privacy.html',
    sourceBytes: priv.length,
    html: minifyHtml(priv)
  });
}

let nf = notFoundTemplate.replaceAll('{{year}}', String(SITE.year));
nf = inlineStyles('404.html', ['shared.css', 'privacy.css'], nf);
nf = injectLangSwitch('404.html', nf);
outputs.push({ outPath: '404.html', sourceBytes: nf.length, html: minifyHtml(nf) });

for (const asset of ASSETS) {
  if (!existsSync(join(ROOT, asset))) throw new Error(`missing build asset: ${asset}`);
}

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

for (const output of outputs) {
  const dest = join(OUT, output.outPath);
  mkdirSync(dirname(dest), { recursive: true });
  writeFileSync(dest, output.html);
  console.log(`  ${output.outPath.padEnd(16)} ${output.sourceBytes} -> ${output.html.length} bytes`);
}

for (const asset of ASSETS) {
  mkdirSync(join(OUT, asset, '..'), { recursive: true });
  cpSync(join(ROOT, asset), join(OUT, asset), { recursive: true });
}

const homeLastMod = latestMtimeDate([join(ROOT, 'index.html'), join(ROOT, 'i18n.mjs')]);
const privLastMod = latestMtimeDate([
  join(ROOT, 'privacy.html'),
  resolve(ROOT, '..', 'docs', 'privacy.md'),
  resolve(ROOT, '..', 'docs', 'privacy.zh-CN.md')
]);
const sitemapXml = buildSitemap([
  { loc: `${SITE.origin}/`, enHref: `${SITE.origin}/`, zhHref: `${SITE.origin}/zh`, lastmod: homeLastMod },
  { loc: `${SITE.origin}/zh`, enHref: `${SITE.origin}/`, zhHref: `${SITE.origin}/zh`, lastmod: homeLastMod },
  { loc: `${SITE.origin}/privacy`, enHref: `${SITE.origin}/privacy`, zhHref: `${SITE.origin}/zh/privacy`, lastmod: privLastMod },
  { loc: `${SITE.origin}/zh/privacy`, enHref: `${SITE.origin}/privacy`, zhHref: `${SITE.origin}/zh/privacy`, lastmod: privLastMod }
]);
writeFileSync(join(OUT, 'sitemap.xml'), sitemapXml);

console.log(`built ${outputs.length} pages + sitemap.xml (v${APP_VERSION}) into dist/`);
