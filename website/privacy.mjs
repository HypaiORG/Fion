import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const DOCS = join(import.meta.dirname, '..', 'docs');
const FILES = {
  zh: 'privacy.zh-CN.md',
  en: 'privacy.md'
};

const LIST = /^(- |\d+\. )/;
const BLOCK_START = /^(#|\||- |\d+\. )/;

const escape = text => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function inline(text) {
  return escape(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
    .replace(/&lt;((?:https?:\/\/|[\w.+-]+@)[^&\s]+)&gt;/g,
      (_, target) => `<a href="${target.startsWith('http') ? target : `mailto:${target}`}">${target}</a>`);
}

function table(rows) {
  const cells = row => row.trim().replace(/^\||\|$/g, '').split('|').map(cell => cell.trim());
  const [head, , ...body] = rows.map(cells);
  return '<div class="table"><table><thead><tr>' +
    head.map(cell => `<th scope="col">${inline(cell)}</th>`).join('') +
    '</tr></thead><tbody>' +
    body.map(row => `<tr>${row.map(cell => `<td>${inline(cell)}</td>`).join('')}</tr>`).join('') +
    '</tbody></table></div>';
}

function parse(markdown, label) {
  const lines = markdown.split('\n');
  const sections = [{ heading: null, blocks: [] }];
  const current = () => sections.at(-1).blocks;
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim() || line.startsWith('# ')) { i++; continue; }
    if (line.startsWith('## ')) {
      sections.push({ heading: inline(line.slice(3).trim()), blocks: [] });
      i++;
    } else if (line.startsWith('|')) {
      const rows = [];
      while (i < lines.length && lines[i].startsWith('|')) rows.push(lines[i++]);
      current().push(table(rows));
    } else if (LIST.test(line)) {
      const tag = /^\d/.test(line) ? 'ol' : 'ul';
      const items = [];
      while (i < lines.length && LIST.test(lines[i])) items.push(lines[i++].replace(LIST, ''));
      current().push(`<${tag}>${items.map(item => `<li>${inline(item)}</li>`).join('')}</${tag}>`);
    } else {
      const words = [];
      while (i < lines.length && lines[i].trim() && !BLOCK_START.test(lines[i])) words.push(lines[i++].trim());
      current().push(`<p>${inline(words.join(' '))}</p>`);
    }
  }

  const [lead, ...headed] = sections;
  if (!headed.length) throw new Error(`${label}: no "## " sections found`);
  return {
    headedCount: headed.length,
    html: [lead, ...headed]
      .filter(section => section.heading || section.blocks.length)
      .map(section => `<section class="section">${section.heading ? `<h2>${section.heading}</h2>` : ''}${section.blocks.join('')}</section>`)
      .join('\n')
  };
}

const cache = new Map();

function load(lang) {
  if (!cache.has(lang)) {
    const file = FILES[lang];
    cache.set(lang, parse(readFileSync(join(DOCS, file), 'utf8'), `docs/${file}`));
  }
  return cache.get(lang);
}

export function assertPrivacyParity() {
  const zh = load('zh').headedCount;
  const en = load('en').headedCount;
  if (zh !== en) {
    throw new Error(`privacy parity mismatch: docs/privacy.zh-CN.md has ${zh} <h2> but docs/privacy.md has ${en}`);
  }
}

export function renderPrivacy(lang) {
  return load(lang).html;
}
