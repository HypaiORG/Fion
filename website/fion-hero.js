function FionHero(canvas, options) {
  const ctx = canvas.getContext('2d');
  const host = options.host;
  const textEl = options.textEl;
  const copy = options.copy;
  const C = {
    paper: '#F6F2EB', navy: '#14213A', navy2: '#2C3E62', sub: '#55627A',
    amber: '#F0B05E', ice: '#A9C1EE', red: '#C23A30', ok: '#5FBF85'
  };
  const parseCells = rows => {
    const cells = [];
    for (let y = 0; y < rows.length; y++) {
      const row = rows[y];
      for (let x = 0; x < row.length; x++) if (row[x] === 'X') cells.push([x, y]);
    }
    return cells;
  };
  const BIRD = [
    ['..XXX.........', '..XXXXX.......', '...XXXXX......', '...XXXXXX.XX..', '....XXXXXXXXX.', 'XXXXXXXXXXXXXX', '.XXXXXXXXXXX..', '..XXXXXXXXX...', '....XXXXX.....', '..............'],
    ['..............', '..............', '..........XX..', '.........XXXX.', '....XXXXXXXXX.', 'XXXXXXXXXXXXXX', '.XXXXXXXXXXX..', '...XXXXXX.....', '....XXXXX.....', '.....XXX......']
  ].map(parseCells);
  const EYE = [11, 4];
  const BIRD_HAS_EYE = BIRD.map(frame => frame.some(([cx, cy]) => cx === EYE[0] && cy === EYE[1]));
  const CLOUDS = [
    ['...XXX......', '.XXXXXXX.XX.', 'XXXXXXXXXXXX'],
    ['....XX....', '..XXXXXX..', 'XXXXXXXXXX'],
    ['..XXX.......', 'XXXXXXX.XXX.', 'XXXXXXXXXXXX', '.XXXXXXXXX..']
  ].map(parseCells);
  const CIPHER = 'k8mQ7x2pL4vR9nB3cT6wY';
  const SANS = '"PingFang SC","Noto Sans SC",-apple-system,"Segoe UI",sans-serif';
  const MONO = 'ui-monospace,"SF Mono",Menlo,monospace';
  const GEO = 'Futura,"Avenir Next",Jost,"PingFang SC",sans-serif';
  const T = { type: 1100, hold: 1700, gather: 2500, fly: 4900, land: 5500, cycle: 6400 };
  const TALL = 11;
  const SHADOW_PAD = 64;

  const dateFmt = new Intl.DateTimeFormat(copy.locale, { month: 'long', day: 'numeric', weekday: 'long' });
  const timeFmt = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
  let cachedMinute = -1, cachedDate = '', cachedTime = '';

  let W = 0, H = 0, dpr = 1, P = 9, L = null;
  let fieldCanvas = null, androidSprite = null, iphoneSprite = null;
  let flights = [], cards = [], sparks = [], nextIndex = 0, nextSpawn = 0, lastLanding = -1e9;
  let pointer = null, raf = 0, running = false, inView = true, clock = 0, last = 0;
  let ambient = [], clouds = [], msgCache = [];
  const glyphCache = new Map();
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const ease = t => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  const sine = t => -(Math.cos(Math.PI * t) - 1) / 2;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const span = (t, a, b) => clamp((t - a) / (b - a), 0, 1);
  const tint = sim => sim === 2 ? C.ice : C.amber;

  function nowStrings() {
    const min = Math.floor(Date.now() / 60000);
    if (min !== cachedMinute) {
      const now = new Date();
      cachedMinute = min;
      cachedDate = dateFmt.format(now);
      cachedTime = timeFmt.format(now);
    }
    return [cachedDate, cachedTime];
  }

  function glyphs(str) {
    const hit = glyphCache.get(str);
    if (hit) return hit;
    const off = document.createElement('canvas');
    const octx = off.getContext('2d', { willReadFrequently: true });
    octx.font = `700 ${TALL}px ${SANS}`;
    const w = Math.ceil(octx.measureText(str).width) + 2;
    off.width = w;
    off.height = TALL + 4;
    octx.font = `700 ${TALL}px ${SANS}`;
    octx.textBaseline = 'top';
    octx.fillText(str, 1, 1);
    const data = octx.getImageData(0, 0, w, TALL + 4).data;
    const cells = [];
    for (let y = 0; y < TALL + 4; y++) {
      for (let x = 0; x < w; x++) {
        if (data[(y * w + x) * 4 + 3] > 120) cells.push([x, y]);
      }
    }
    const out = { cells, cols: w, rows: TALL + 4 };
    glyphCache.set(str, out);
    return out;
  }

  function bezier(p, t) {
    const u = 1 - t;
    return {
      x: u * u * p[0].x + 2 * u * t * p[1].x + t * t * p[2].x,
      y: u * u * p[0].y + 2 * u * t * p[1].y + t * t * p[2].y
    };
  }

  function roundRect(c, x, y, w, h, r) {
    c.beginPath();
    c.moveTo(x + r, y);
    c.arcTo(x + w, y, x + w, y + h, r);
    c.arcTo(x + w, y + h, x, y + h, r);
    c.arcTo(x, y + h, x, y, r);
    c.arcTo(x, y, x + w, y, r);
    c.closePath();
  }

  function fit(c, text, maxW) {
    if (c.measureText(text).width <= maxW) return text;
    let s = text;
    while (s.length > 1 && c.measureText(s + '…').width > maxW) s = s.slice(0, -1);
    return s + '…';
  }

  function wrap(c, text, maxW, maxLines) {
    const out = [];
    let line = '';
    for (const ch of text) {
      if (c.measureText(line + ch).width > maxW && line) {
        out.push(line);
        line = '';
        if (out.length === maxLines) return out;
      }
      line += ch;
    }
    if (line && out.length < maxLines) out.push(line);
    return out;
  }

  function makeOffscreen(w, h) {
    const el = document.createElement('canvas');
    el.width = Math.max(1, Math.round(w * dpr));
    el.height = Math.max(1, Math.round(h * dpr));
    const c = el.getContext('2d');
    c.setTransform(dpr, 0, 0, dpr, 0, 0);
    return [el, c];
  }

  function drawLabel(c, text, x, y) {
    c.font = `500 11px ${MONO}`;
    c.fillStyle = C.sub;
    c.textAlign = 'center';
    c.textBaseline = 'middle';
    c.fillText(text, x, y);
    c.textAlign = 'left';
  }

  function bakeStatic() {
    const [fc, fctx] = makeOffscreen(W, H);
    const t = L.text;
    fctx.fillStyle = C.navy2;
    for (let y = P / 2; y < H; y += P) {
      for (let x = P / 2; x < W; x += P) {
        const inText = x > t.x - 10 && x < t.x + t.w + 10 && y > t.y - 10 && y < t.y + t.h + 10;
        fctx.globalAlpha = inText ? 0.05 : 0.09;
        fctx.fillRect(x - 0.65, y - 0.65, 1.3, 1.3);
      }
    }
    fctx.globalAlpha = 1;
    fieldCanvas = fc;

    const { phone, iphone, lp } = L;
    const aw = phone.w + SHADOW_PAD * 2;
    const ah = phone.h + SHADOW_PAD * 2 + 24;
    const [ac, actx] = makeOffscreen(aw, ah);
    const px = SHADOW_PAD, py = SHADOW_PAD;
    const ps = { x: px + 9, y: py + 9, w: phone.w - 18, h: phone.h - 18 };
    actx.save();
    actx.shadowColor = 'rgba(16,32,56,0.38)';
    actx.shadowBlur = 44;
    actx.shadowOffsetY = 22;
    const body = actx.createLinearGradient(px, py, px + phone.w, py + phone.h);
    body.addColorStop(0, '#34476E');
    body.addColorStop(1, '#14213A');
    roundRect(actx, px, py, phone.w, phone.h, phone.w * 0.13);
    actx.fillStyle = body;
    actx.fill();
    actx.restore();
    actx.fillStyle = '#0D1628';
    roundRect(actx, ps.x, ps.y, ps.w, ps.h, phone.w * 0.09);
    actx.fill();
    actx.fillStyle = 'rgba(255,255,255,0.12)';
    actx.fillRect(px + phone.w, py + phone.h * 0.22, 2.5, phone.h * 0.12);
    actx.fillStyle = 'rgba(240,176,94,0.07)';
    const cols = Math.floor((ps.w - 24) / lp);
    const ox = ps.x + (ps.w - cols * lp) / 2;
    const oy = ps.y + 40;
    for (let r = 0; r < TALL + 4; r++) {
      for (let c = 0; c < cols; c++) actx.fillRect(ox + c * lp, oy + r * lp, lp - 1, lp - 1);
    }
    drawLabel(actx, copy.android, px + phone.w / 2, py + phone.h + 18);
    androidSprite = ac;

    const iw = iphone.w + SHADOW_PAD * 2;
    const ih = iphone.h + SHADOW_PAD * 2 + 24;
    const [ic, ictx] = makeOffscreen(iw, ih);
    const ix = SHADOW_PAD, iy = SHADOW_PAD;
    const is = { x: ix + 7, y: iy + 7, w: iphone.w - 14, h: iphone.h - 14 };
    ictx.save();
    ictx.shadowColor = 'rgba(16,32,56,0.35)';
    ictx.shadowBlur = 50;
    ictx.shadowOffsetY = 26;
    roundRect(ictx, ix, iy, iphone.w, iphone.h, iphone.w * 0.17);
    ictx.fillStyle = '#1C1F27';
    ictx.fill();
    ictx.restore();
    ictx.save();
    roundRect(ictx, is.x, is.y, is.w, is.h, iphone.w * 0.14);
    ictx.clip();
    const wall = ictx.createLinearGradient(0, is.y, 0, is.y + is.h);
    wall.addColorStop(0, '#22345A');
    wall.addColorStop(0.55, '#3E5A8C');
    wall.addColorStop(1, '#C98A5A');
    ictx.fillStyle = wall;
    ictx.fillRect(is.x, is.y, is.w, is.h);
    ictx.fillStyle = 'rgba(255,255,255,0.06)';
    for (let y = is.y + 4; y < is.y + is.h; y += 7) {
      for (let x = is.x + 4; x < is.x + is.w; x += 7) ictx.fillRect(x, y, 1.4, 1.4);
    }
    roundRect(ictx, is.x + is.w * 0.33, is.y + 9, is.w * 0.34, Math.max(14, is.w * 0.085), 10);
    ictx.fillStyle = '#05070B';
    ictx.fill();
    ictx.fillStyle = 'rgba(255,255,255,0.7)';
    roundRect(ictx, is.x + is.w * 0.33, is.y + is.h - 12, is.w * 0.34, 4, 2);
    ictx.fill();
    ictx.restore();
    drawLabel(ictx, copy.bark, ix + iphone.w / 2, iy + iphone.h + 18);
    iphoneSprite = ic;
  }

  function prepareMsgCache() {
    const s = L.phone.screen;
    const list = L.iphone.list;
    const ic = list.h * 0.56;
    const tw = list.w - 8 - ic - 16;
    const fs = clamp(list.h * 0.2, 9, 12);
    ctx.font = `400 ${fs - 1}px ${SANS}`;
    const nw = ctx.measureText(copy.now).width + 6;
    ctx.font = `500 10px ${SANS}`;
    L.pillW = ctx.measureText(copy.status).width + 30;
    L.cardFs = fs;

    msgCache = copy.msgs.map(m => {
      ctx.font = `500 10px ${MONO}`;
      const header = fit(ctx, `SIM ${m.sim} · ${m.from}`, s.w - 40);
      ctx.font = `400 10.5px ${SANS}`;
      const bodyLines = wrap(ctx, m.body, s.w - 28, 3);
      ctx.font = `500 9.5px ${MONO}`;
      const logLine = fit(ctx, `SIM ${m.sim} ${m.token} → Bark`, s.w - 70);
      const latency = `${(0.3 + (m.token.length % 5) * 0.1).toFixed(1)}s`;
      ctx.font = `600 ${fs}px ${SANS}`;
      const cardTitle = fit(ctx, m.title, tw - nw);
      ctx.font = `400 ${fs}px ${SANS}`;
      const cardBody = fit(ctx, m.body, tw);
      return { m, header, bodyLines, totalChars: bodyLines.join('').length, logLine, latency, cardTitle, cardBody };
    });
  }

  function layout() {
    const cr = canvas.getBoundingClientRect();
    const tr = textEl.getBoundingClientRect();
    const narrow = W < 900;
    const text = { x: tr.left - cr.left, y: tr.top - cr.top, w: tr.width, h: tr.height };
    let S;
    if (narrow) {
      const top = text.y + text.h + 30;
      S = { x: 16, y: top, w: W - 32, h: Math.max(260, H - top - 16) };
    } else {
      const x = text.x + text.w + 36;
      const right = Math.min(W - 24, x + 700);
      S = { x, y: 28, w: right - x, h: H - 56 };
    }
    P = narrow ? 8 : 9;
    let iw = clamp(S.w * (narrow ? 0.42 : 0.37), 140, 250);
    let ih = iw * 2.02;
    if (ih > S.h - 38) { ih = S.h - 38; iw = ih / 2.02; }
    let aw = clamp(S.w * (narrow ? 0.44 : 0.36), 140, 240);
    let ah = aw * 1.58;
    if (ah > S.h * 0.72) { ah = S.h * 0.72; aw = ah / 1.58; }
    const gap = narrow ? clamp(S.w * 0.08, 20, 44) : clamp(S.w * 0.12, 52, 96);
    const gx = S.x + Math.max(0, (S.w - aw - gap - iw) / 2);
    const phone = { x: gx, y: S.y + S.h - ah - 32, w: aw, h: ah };
    const iphone = { x: gx + aw + gap, y: Math.max(S.y, phone.y + ah - ih - (narrow ? 12 : 46)), w: iw, h: ih };
    phone.screen = { x: phone.x + 9, y: phone.y + 9, w: aw - 18, h: ah - 18 };
    iphone.screen = { x: iphone.x + 7, y: iphone.y + 7, w: iw - 14, h: ih - 14 };
    const sc = iphone.screen;
    const cardH = clamp(iw * 0.25, 42, 60);
    const listY = sc.y + iw * 0.62;
    iphone.list = { x: sc.x + 8, y: listY, w: sc.w - 16, h: cardH, gap: 7 };
    const maxCols = Math.max(...copy.msgs.map(m => glyphs(m.token).cols));
    const lp = Math.max(2, Math.min(5, Math.floor((phone.screen.w - 28) / maxCols)));
    const start = { x: phone.x + aw * 0.5, y: phone.y - 34 };
    const end = { x: iphone.list.x + 6 + cardH * 0.28, y: listY + cardH / 2 };
    const ctrl = { x: (start.x + end.x) / 2 - aw * 0.1, y: Math.max(S.y + 10, Math.min(start.y, end.y) - S.h * 0.3) };
    const gate = bezier([start, ctrl, end], 0.5);
    L = { narrow, S, text, phone, iphone, lp, path: [start, ctrl, end], gate, bs: narrow ? 5 : 7, maxCards: narrow ? 2 : 3 };
    seedAmbient();
    prepareMsgCache();
    bakeStatic();
  }

  function seedAmbient() {
    const bandTop = L.narrow ? L.S.y - 12 : 30;
    const bandBot = L.narrow ? L.S.y + 70 : H * 0.55;
    ambient = [0, 1, 2, 3].map(i => ({
      y: bandTop + (bandBot - bandTop) * (0.15 + i * 0.22),
      speed: 26 + i * 9, offset: i * 377, bs: 2 + (i % 2) * 0.8, phase: i * 0.7
    }));
    clouds = [0, 1, 2].map(i => ({
      cells: CLOUDS[i], x: W * (0.2 + i * 0.33), y: 30 + i * (H * 0.18), cell: L.narrow ? 6 : 8, speed: 4 + i * 2
    }));
  }

  function tokenOrigin(g) {
    const s = L.phone.screen;
    return { x: s.x + (s.w - g.cols * L.lp) / 2, y: s.y + 40 };
  }

  function resize() {
    const r = canvas.getBoundingClientRect();
    W = Math.max(280, r.width);
    H = Math.max(320, r.height);
    dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    layout();
    for (const f of flights) {
      const o = tokenOrigin(f.g);
      f.o = o;
      const bs = L.bs, p0 = L.path[0];
      for (let i = 0; i < f.parts.length; i++) {
        const pt = f.parts[i], [cx, cy] = f.g.cells[i], target = BIRD[0][i % BIRD[0].length];
        pt.hx = o.x + cx * L.lp;
        pt.hy = o.y + cy * L.lp;
        pt.tx = p0.x - 7 * bs + target[0] * bs;
        pt.ty = p0.y - 5 * bs + target[1] * bs;
      }
    }
    if (cards.length > L.maxCards) cards.length = L.maxCards;
  }

  function spawn(at) {
    const idx = nextIndex % copy.msgs.length;
    nextIndex++;
    const m = copy.msgs[idx];
    const g = glyphs(m.token);
    const o = tokenOrigin(g);
    const bs = L.bs, p0 = L.path[0];
    const parts = g.cells.map(([cx, cy], i) => {
      const target = BIRD[0][i % BIRD[0].length];
      return {
        hx: o.x + cx * L.lp, hy: o.y + cy * L.lp, col: cx,
        tx: p0.x - 7 * bs + target[0] * bs, ty: p0.y - 5 * bs + target[1] * bs,
        delay: (cx / g.cols) * 260 + Math.random() * 120
      };
    });
    flights.push({ idx, m, g, o, parts, start: at, gated: false, landed: false, trail: [] });
  }

  function drawHotDots(hot) {
    if (!hot.length) return;
    const t = L.text;
    const cols = Math.ceil((W - P / 2) / P);
    const rows = Math.ceil((H - P / 2) / P);
    const regions = [];
    if (hot.length === 2) {
      const a = hot[0], b = hot[1];
      const dx = a.x - b.x, dy = a.y - b.y;
      if (dx * dx + dy * dy < (a.r + b.r) * (a.r + b.r)) {
        regions.push({
          x0: Math.min(a.x - a.r, b.x - b.r), x1: Math.max(a.x + a.r, b.x + b.r),
          y0: Math.min(a.y - a.r, b.y - b.r), y1: Math.max(a.y + a.r, b.y + b.r),
          list: hot
        });
      }
    }
    if (!regions.length) {
      for (const h of hot) regions.push({ x0: h.x - h.r, x1: h.x + h.r, y0: h.y - h.r, y1: h.y + h.r, list: [h] });
    }
    ctx.fillStyle = C.navy2;
    for (const reg of regions) {
      const c0 = Math.max(0, Math.floor((reg.x0 - P / 2) / P));
      const c1 = Math.min(cols - 1, Math.ceil((reg.x1 - P / 2) / P));
      const r0 = Math.max(0, Math.floor((reg.y0 - P / 2) / P));
      const r1 = Math.min(rows - 1, Math.ceil((reg.y1 - P / 2) / P));
      for (let ri = r0; ri <= r1; ri++) {
        const y = P / 2 + ri * P;
        for (let ci = c0; ci <= c1; ci++) {
          const x = P / 2 + ci * P;
          let addA = 0, addS = 0;
          for (let hi = 0; hi < reg.list.length; hi++) {
            const h = reg.list[hi];
            const dx = x - h.x, dy = y - h.y;
            const d2 = dx * dx + dy * dy;
            if (d2 < h.r2) {
              const k = 1 - Math.sqrt(d2) / h.r;
              addA += k * h.a;
              addS += k * h.s;
            }
          }
          if (addA <= 0) continue;
          const inText = x > t.x - 10 && x < t.x + t.w + 10 && y > t.y - 10 && y < t.y + t.h + 10;
          const baseA = inText ? 0.05 : 0.09;
          const s = 1.3 + addS;
          ctx.globalAlpha = Math.min(baseA + addA, 0.6);
          ctx.fillRect(x - s / 2, y - s / 2, s, s);
        }
      }
    }
    ctx.globalAlpha = 1;
  }

  function drawAmbient() {
    ctx.fillStyle = 'rgba(44,62,98,0.055)';
    const cSpan = W + 240;
    for (const c of clouds) {
      const x = ((c.x - (clock / 1000) * c.speed) % cSpan + cSpan) % cSpan - 120;
      const sz = c.cell - 1;
      for (const [rx, ry] of c.cells) ctx.fillRect(x + rx * c.cell, c.y + ry * c.cell, sz, sz);
    }
    const bSpan = W + 200;
    ctx.globalAlpha = L.narrow ? 0.14 : 0.22;
    for (const b of ambient) {
      const x = ((clock / 1000) * b.speed + b.offset) % bSpan - 100;
      const y = b.y + Math.sin(clock / 900 + b.phase) * 8;
      drawBird(x, y, b.bs, Math.floor(clock / 220 + b.phase * 3) % 2, C.navy2, 0, 1);
    }
    ctx.globalAlpha = 1;
  }

  function drawAndroid(active) {
    const { phone } = L;
    const s = phone.screen;
    ctx.drawImage(androidSprite, phone.x - SHADOW_PAD, phone.y - SHADOW_PAD, androidSprite.width / dpr, androidSprite.height / dpr);

    const f = active ? active.f : null;
    const t = active ? active.t : 1e9;
    const idx = f ? f.idx : (flights.length ? flights[flights.length - 1].idx : 0);
    const mc = msgCache[idx];
    const tone = tint(mc.m.sim);
    const wake = f ? 1 - span(t, 0, 900) : 0;
    if (wake > 0) {
      const glow = ctx.createRadialGradient(s.x + s.w / 2, s.y + s.h * 0.3, 4, s.x + s.w / 2, s.y + s.h * 0.3, s.w);
      glow.addColorStop(0, `rgba(240,176,94,${0.28 * wake})`);
      glow.addColorStop(1, 'rgba(240,176,94,0)');
      ctx.fillStyle = glow;
      roundRect(ctx, s.x, s.y, s.w, s.h, phone.w * 0.09);
      ctx.fill();
    }
    ctx.textBaseline = 'middle';
    ctx.font = `500 10px ${MONO}`;
    ctx.fillStyle = tone;
    ctx.fillText(mc.header, s.x + 14, s.y + 20);
    ctx.fillStyle = C.red;
    ctx.globalAlpha = f && t < T.hold ? (Math.floor(clock / 180) % 2 ? 1 : 0.3) : 0.3;
    ctx.beginPath();
    ctx.arc(s.x + s.w - 16, s.y + 20, 3.2, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1;

    const tokenBottom = s.y + 40 + (TALL + 4) * L.lp;
    if (!f || t >= T.gather) {
      ctx.font = `500 10px ${MONO}`;
      ctx.fillStyle = tone;
      ctx.globalAlpha = 0.75;
      ctx.textAlign = 'center';
      ctx.fillText(`✓ ${copy.sent}`, s.x + s.w / 2, s.y + 40 + (TALL + 4) * L.lp / 2);
      ctx.textAlign = 'left';
      ctx.globalAlpha = 1;
    }
    ctx.font = `400 10.5px ${SANS}`;
    ctx.fillStyle = 'rgba(233,225,210,0.72)';
    const reveal = f ? span(t, 300, T.type + 300) : 1;
    let shown = Math.floor(mc.totalChars * reveal);
    for (let i = 0; i < mc.bodyLines.length; i++) {
      const ln = mc.bodyLines[i];
      if (shown > 0) ctx.fillText(shown >= ln.length ? ln : ln.slice(0, shown), s.x + 14, tokenBottom + 18 + i * 16);
      shown -= ln.length;
    }
    const py = s.y + s.h - 24;
    const rowsTop = tokenBottom + 18 + mc.bodyLines.length * 16 + 10;
    const rows = Math.max(0, Math.min(cards.length, Math.floor((py - 22 - rowsTop) / 22)));
    ctx.font = `500 9.5px ${MONO}`;
    for (let i = 0; i < rows; i++) {
      const c = msgCache[cards[i].idx];
      const ry = py - 30 - i * 22;
      ctx.globalAlpha = 1 - i * 0.25;
      ctx.strokeStyle = 'rgba(255,255,255,0.06)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(s.x + 14, ry - 11);
      ctx.lineTo(s.x + s.w - 14, ry - 11);
      ctx.stroke();
      ctx.fillStyle = C.ok;
      ctx.fillText('✓', s.x + 14, ry);
      ctx.fillStyle = tint(c.m.sim);
      ctx.fillText(c.logLine, s.x + 28, ry);
      ctx.fillStyle = '#7D8AA3';
      ctx.textAlign = 'right';
      ctx.fillText(c.latency, s.x + s.w - 14, ry);
      ctx.textAlign = 'left';
    }
    ctx.globalAlpha = 1;
    ctx.font = `500 10px ${SANS}`;
    const pw = L.pillW;
    roundRect(ctx, s.x + (s.w - pw) / 2, py - 11, pw, 22, 11);
    ctx.fillStyle = 'rgba(255,255,255,0.07)';
    ctx.fill();
    ctx.fillStyle = C.ok;
    ctx.globalAlpha = 0.6 + 0.4 * Math.sin(clock / 300);
    ctx.beginPath();
    ctx.arc(s.x + (s.w - pw) / 2 + 12, py, 3, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1;
    ctx.fillStyle = '#C9D2E3';
    ctx.fillText(copy.status, s.x + (s.w - pw) / 2 + 21, py);

    if (f && t < 900) {
      const cx = phone.x + phone.w / 2, cy = phone.y - 6;
      ctx.lineWidth = 2;
      ctx.lineCap = 'round';
      for (let i = 0; i < 3; i++) {
        const k = span(t, i * 150, 600 + i * 150);
        if (k <= 0 || k >= 1) continue;
        ctx.strokeStyle = `rgba(240,176,94,${1 - k})`;
        ctx.beginPath();
        ctx.arc(cx, cy, 10 + k * 34, Math.PI * 1.2, Math.PI * 1.8);
        ctx.stroke();
      }
    }
  }

  function bell(x, y, s) {
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = Math.max(1.2, s * 0.09);
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(x - s * 0.32, y + s * 0.2);
    ctx.lineTo(x + s * 0.32, y + s * 0.2);
    ctx.moveTo(x - s * 0.24, y + s * 0.2);
    ctx.quadraticCurveTo(x - s * 0.24, y - s * 0.34, x, y - s * 0.34);
    ctx.quadraticCurveTo(x + s * 0.24, y - s * 0.34, x + s * 0.24, y + s * 0.2);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(x, y + s * 0.3, s * 0.08, 0, Math.PI);
    ctx.stroke();
  }

  function drawIphone() {
    const { iphone } = L;
    const s = iphone.screen, list = iphone.list;
    ctx.drawImage(iphoneSprite, iphone.x - SHADOW_PAD, iphone.y - SHADOW_PAD, iphoneSprite.width / dpr, iphoneSprite.height / dpr);

    ctx.save();
    roundRect(ctx, s.x, s.y, s.w, s.h, iphone.w * 0.14);
    ctx.clip();
    const pulse = 1 - span(clock - lastLanding, 0, 900);
    if (pulse > 0) {
      ctx.fillStyle = `rgba(255,255,255,${0.16 * pulse})`;
      ctx.fillRect(s.x, s.y, s.w, s.h);
    }
    const [dateStr, timeStr] = nowStrings();
    ctx.fillStyle = 'rgba(255,255,255,0.85)';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = `500 ${Math.max(10, iphone.w * 0.05)}px ${SANS}`;
    ctx.fillText(dateStr, s.x + s.w / 2, s.y + iphone.w * 0.24);
    ctx.fillStyle = '#fff';
    ctx.font = `600 ${iphone.w * 0.24}px ${GEO}`;
    ctx.fillText(timeStr, s.x + s.w / 2, s.y + iphone.w * 0.42);
    ctx.textAlign = 'left';

    const newest = cards[0];
    const slide = newest ? ease(span(clock - newest.at, 0, 500)) : 1;
    const fs = L.cardFs;
    for (let i = 0; i < cards.length; i++) {
      const c = msgCache[cards[i].idx];
      const k = i === 0 ? slide : 1;
      const y = list.y + i * (list.h + list.gap) - (1 - slide) * (i === 0 ? 10 : list.h + list.gap);
      ctx.globalAlpha = k * (1 - i * 0.12);
      roundRect(ctx, list.x, y, list.w, list.h, 13);
      ctx.fillStyle = 'rgba(250,250,252,0.88)';
      ctx.fill();
      const ic = list.h * 0.56, ix = list.x + 8, iy = y + (list.h - ic) / 2;
      roundRect(ctx, ix, iy, ic, ic, ic * 0.26);
      ctx.fillStyle = C.navy;
      ctx.fill();
      bell(ix + ic / 2, iy + ic / 2, ic * 0.62);
      const tx = ix + ic + 8;
      ctx.textBaseline = 'alphabetic';
      ctx.font = `400 ${fs - 1}px ${SANS}`;
      ctx.fillStyle = '#8A8A90';
      ctx.textAlign = 'right';
      ctx.fillText(copy.now, list.x + list.w - 8, y + list.h * 0.42);
      ctx.textAlign = 'left';
      ctx.font = `600 ${fs}px ${SANS}`;
      ctx.fillStyle = '#111';
      ctx.fillText(c.cardTitle, tx, y + list.h * 0.42);
      ctx.font = `400 ${fs}px ${SANS}`;
      ctx.fillStyle = '#2A2F3A';
      ctx.fillText(c.cardBody, tx, y + list.h * 0.75);
    }
    ctx.globalAlpha = 1;
    ctx.restore();
    if (pulse > 0) {
      ctx.strokeStyle = `rgba(240,176,94,${pulse})`;
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(L.path[2].x, L.path[2].y, 16 + (1 - pulse) * 44, 0, Math.PI * 2);
      ctx.stroke();
    }
  }

  function drawRoute() {
    const p = L.path;
    ctx.save();
    ctx.setLineDash([2, 8]);
    ctx.strokeStyle = 'rgba(44,62,98,0.28)';
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    ctx.moveTo(p[0].x, p[0].y);
    ctx.quadraticCurveTo(p[1].x, p[1].y, p[2].x, p[2].y);
    ctx.stroke();
    ctx.restore();

    const g = L.gate;
    const hot = flights.find(f => { const t = clock - f.start; return t > T.gather && t < T.fly; });
    let pulse = 0;
    if (hot) {
      const u = sine(span(clock - hot.start, T.gather, T.fly));
      pulse = Math.max(0, 1 - Math.abs(u - 0.5) / 0.12);
    }
    const r = 28 + pulse * 8;
    ctx.save();
    ctx.fillStyle = 'rgba(246,242,235,0.9)';
    ctx.beginPath();
    ctx.arc(g.x, g.y, r, 0, Math.PI * 2);
    ctx.fill();
    ctx.setLineDash([3, 5]);
    ctx.lineWidth = 1.3;
    ctx.strokeStyle = `rgba(44,62,98,${0.4 + pulse * 0.4})`;
    ctx.beginPath();
    ctx.arc(g.x, g.y, r, 0, Math.PI * 2);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = pulse > 0.2 ? C.navy : `rgba(240,176,94,${0.18 + pulse * 0.3})`;
    ctx.beginPath();
    ctx.arc(g.x, g.y, r - 7, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = pulse > 0.2 ? C.amber : C.navy2;
    ctx.lineWidth = 1.8;
    ctx.lineCap = 'round';
    roundRect(ctx, g.x - 8, g.y - 2, 16, 12, 3);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(g.x, g.y - 2, 5, Math.PI, 0);
    ctx.stroke();
    ctx.restore();
    drawLabel(ctx, 'HTTPS + AES', g.x, g.y - r - 14);
  }

  function drawBird(x, y, bs, frame, color, angle, scale) {
    ctx.save();
    ctx.translate(x, y);
    if (angle) ctx.rotate(angle);
    if (scale !== 1) ctx.scale(scale, scale);
    const ox = -7 * bs, oy = -5 * bs, sz = bs - 0.5;
    ctx.fillStyle = color;
    const cells = BIRD[frame];
    for (let i = 0; i < cells.length; i++) ctx.fillRect(ox + cells[i][0] * bs, oy + cells[i][1] * bs, sz, sz);
    if (BIRD_HAS_EYE[frame]) {
      ctx.fillStyle = C.paper;
      ctx.fillRect(ox + EYE[0] * bs, oy + EYE[1] * bs, sz, sz);
    }
    ctx.restore();
  }

  function drawSourceDots(f, t) {
    const reveal = span(t, 0, T.type);
    const color = tint(f.m.sim);
    const size = L.lp - 0.8;
    ctx.fillStyle = color;
    for (let i = 0; i < f.parts.length; i++) {
      const p = f.parts[i];
      if (p.col / f.g.cols > reveal) continue;
      const k = ease(span(t, T.hold + p.delay, T.gather - 120 + p.delay * 0.2));
      if (k >= 1) continue;
      const x = p.hx + (p.tx - p.hx) * k;
      const y = p.hy + (p.ty - p.hy) * k - Math.sin(k * Math.PI) * 50;
      const sz = size + (L.bs - size) * k;
      if (k > 0) {
        ctx.globalAlpha = 0.25;
        ctx.fillRect(x - 1.5, y - 1.5, sz + 3, sz + 3);
      }
      ctx.globalAlpha = 1;
      ctx.fillRect(x, y, sz, sz);
    }
    ctx.globalAlpha = 1;
    if (t < T.type + 300 && (Math.floor(clock / 260) & 1)) {
      const cx = f.o.x + Math.min(f.g.cols, reveal * f.g.cols) * L.lp + 2;
      ctx.fillRect(cx, f.o.y + 2, L.lp, f.g.rows * L.lp - 6);
    }
  }

  function drawFlight(f, t) {
    if (t < T.gather || t >= T.land) return;
    const u = sine(span(t, T.gather, T.fly));
    const pos = bezier(L.path, u);
    const ahead = bezier(L.path, Math.min(1, u + 0.02));
    const angle = clamp(Math.atan2(ahead.y - pos.y, ahead.x - pos.x), -0.35, 0.35);
    if (!f.gated && u >= 0.5) {
      f.gated = true;
      for (let i = 0; i < 14; i++) {
        sparks.push({
          x: pos.x, y: pos.y,
          vx: (Math.random() - 0.3) * 80, vy: (Math.random() - 0.5) * 80,
          ch: CIPHER[(Math.random() * CIPHER.length) | 0], born: clock
        });
      }
    }
    const trail = f.trail;
    trail.push({ x: pos.x, y: pos.y, at: clock, locked: f.gated });
    while (trail.length && clock - trail[0].at > 1800) trail.shift();
    let prev = null;
    const simColor = tint(f.m.sim);
    for (let i = 0; i < trail.length; i++) {
      const p = trail[i];
      if (prev) {
        const dx = p.x - prev.x, dy = p.y - prev.y;
        if (dx * dx + dy * dy < 121) continue;
      }
      prev = p;
      ctx.globalAlpha = (1 - (clock - p.at) / 1800) * 0.7;
      ctx.fillStyle = p.locked ? C.navy2 : simColor;
      ctx.fillRect(p.x - 2, p.y - 2, 4, 4);
    }
    const land = span(t, T.fly, T.land);
    const frame = Math.floor(clock / 140) & 1;
    const color = f.gated ? C.navy : (f.m.sim === 2 ? '#7F9AD0' : '#D9962E');
    ctx.globalAlpha = 1 - land;
    if (land > 0) drawBird(L.path[2].x, L.path[2].y, L.bs, frame, color, 0, 1 - land * 0.7);
    else drawBird(pos.x, pos.y, L.bs, frame, color, angle, 1);
    ctx.globalAlpha = 1;
  }

  function drawSparks() {
    if (!sparks.length) return;
    let write = 0;
    ctx.font = `600 12px ${MONO}`;
    ctx.textBaseline = 'middle';
    ctx.fillStyle = C.navy2;
    for (let i = 0; i < sparks.length; i++) {
      const s = sparks[i];
      const age = clock - s.born;
      if (age >= 1200) continue;
      const k = age / 1200;
      ctx.globalAlpha = 1 - k;
      ctx.fillText(s.ch, s.x + s.vx * k, s.y + s.vy * k);
      sparks[write++] = s;
    }
    sparks.length = write;
    ctx.globalAlpha = 1;
  }

  function render() {
    ctx.clearRect(0, 0, W, H);
    ctx.drawImage(fieldCanvas, 0, 0, W, H);
    const hot = [];
    for (let i = 0; i < flights.length; i++) {
      const f = flights[i], t = clock - f.start;
      if (t >= T.gather && t < T.land) {
        const p = bezier(L.path, sine(span(t, T.gather, T.fly)));
        hot.push({ x: p.x, y: p.y, r: 120, r2: 14400, a: 0.3, s: 1.6 });
      }
    }
    if (pointer) hot.push({ x: pointer.x, y: pointer.y, r: 130, r2: 16900, a: 0.35, s: 2.2 });
    drawHotDots(hot);
    drawAmbient();
    drawRoute();
    const activeF = flights.find(f => clock - f.start < T.gather) || flights[flights.length - 1];
    drawAndroid(activeF ? { f: activeF, t: clock - activeF.start } : null);
    drawIphone();
    let write = 0;
    for (let i = 0; i < flights.length; i++) {
      const f = flights[i], t = clock - f.start;
      if (t < T.gather) drawSourceDots(f, t);
      drawFlight(f, t);
      if (t >= T.land && !f.landed) {
        f.landed = true;
        lastLanding = clock;
        cards.unshift({ idx: f.idx, at: clock });
        if (cards.length > L.maxCards) cards.length = L.maxCards;
      }
      if (t < T.cycle + T.land) flights[write++] = f;
    }
    flights.length = write;
    drawSparks();
  }

  function step(now) {
    const dt = last ? Math.min(64, now - last) : 16;
    last = now;
    clock += dt;
    if (clock >= nextSpawn) {
      spawn(clock);
      nextSpawn = clock + T.cycle;
    }
    render();
    if (running) raf = requestAnimationFrame(step);
  }

  function staticFrame() {
    flights = [];
    sparks = [];
    nextIndex = 0;
    clock = 20000;
    cards = [{ idx: 2, at: 0 }, { idx: 1, at: 0 }];
    lastLanding = -1e9;
    spawn(clock - (T.gather + (T.fly - T.gather) * 0.62));
    flights[0].gated = true;
    render();
  }

  function start() {
    if (running || reduce) return;
    running = true;
    last = 0;
    raf = requestAnimationFrame(step);
  }

  function stop() {
    running = false;
    cancelAnimationFrame(raf);
  }

  function syncRun() {
    if (inView && !document.hidden && !reduce) start();
    else stop();
  }

  function init() {
    stop();
    flights = [];
    sparks = [];
    nextIndex = 0;
    nextSpawn = 0;
    clock = 0;
    lastLanding = -1e9;
    cards = [{ idx: 2, at: -1e4 }, { idx: 1, at: -1e4 }];
    resize();
    if (reduce) staticFrame();
    else syncRun();
  }

  const ro = new ResizeObserver(() => {
    resize();
    if (!running) render();
  });
  ro.observe(canvas);
  ro.observe(textEl);

  const io = new IntersectionObserver(([entry]) => {
    inView = entry.isIntersecting;
    syncRun();
  });
  io.observe(canvas);

  document.addEventListener('visibilitychange', syncRun);
  host.addEventListener('pointermove', e => {
    const r = canvas.getBoundingClientRect();
    pointer = { x: e.clientX - r.left, y: e.clientY - r.top };
  }, { passive: true });
  host.addEventListener('pointerleave', () => { pointer = null; });

  init();
}
