function FionHero(canvas, options) {
  const ctx = canvas.getContext('2d');
  const host = options.host;
  const textEl = options.textEl;
  const C = {
    paper: '#F6F2EB', navy: '#14213A', navy2: '#2C3E62', sub: '#55627A', blue: '#4A6596',
    amber: '#F0B05E', ice: '#A9C1EE', gold: '#B07A1E', red: '#C23A30', ok: '#5FBF85'
  };
  const COPY = {
    zh: {
      android: '你的 Android', bark: '你的 iPhone · Bark', now: '现在',
      status: '飞鸿 · 转发中', sent: '已加密送出', locale: 'zh-CN',
      msgs: [
        { sim: 1, from: '95588', token: '839201', title: '短信 · 95588', body: '【银行】验证码 839201' },
        { sim: 2, from: '138 0013 8000', token: '未接 ×2', title: '未接来电 · 138 0013 8000', body: '呼叫 SIM 2，今日第 2 次未接' },
        { sim: 1, from: '10690', token: '0213', title: '短信 · 10690', body: '【快递柜】取件码 0213' }
      ]
    },
    en: {
      android: 'Your Android', bark: 'Your iPhone · Bark', now: 'now',
      status: 'Fion · Active', sent: 'Locked & sent', locale: 'en-US',
      msgs: [
        { sim: 1, from: '72975', token: '839201', title: 'SMS · 72975', body: 'Bank code: 839201' },
        { sim: 2, from: '415-555-0132', token: '×2 CALL', title: 'Missed call · 415-555-0132', body: 'Called SIM 2, call #2 today' },
        { sim: 1, from: 'Parcel', token: '0213', title: 'SMS · Parcel', body: 'Locker code: 0213' }
      ]
    }
  };
  const BIRD = [
    ['..XXX.........', '..XXXXX.......', '...XXXXX......', '...XXXXXX.XX..', '....XXXXXXXXX.', 'XXXXXXXXXXXXXX', '.XXXXXXXXXXX..', '..XXXXXXXXX...', '....XXXXX.....', '..............'],
    ['..............', '..............', '..........XX..', '.........XXXX.', '....XXXXXXXXX.', 'XXXXXXXXXXXXXX', '.XXXXXXXXXXX..', '...XXXXXX.....', '....XXXXX.....', '.....XXX......']
  ].map(rows => {
    const cells = [];
    rows.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === 'X') cells.push([x, y]); }));
    return cells;
  });
  const EYE = [11, 4];
  const CLOUDS = [
    ['...XXX......', '.XXXXXXX.XX.', 'XXXXXXXXXXXX'],
    ['....XX....', '..XXXXXX..', 'XXXXXXXXXX'],
    ['..XXX.......', 'XXXXXXX.XXX.', 'XXXXXXXXXXXX', '.XXXXXXXXX..']
  ];
  const CIPHER = 'k8mQ7x2pL4vR9nB3cT6wY';
  const SANS = '"PingFang SC","Noto Sans SC",-apple-system,"Segoe UI",sans-serif';
  const MONO = 'ui-monospace,"SF Mono",Menlo,monospace';
  const GEO = 'Futura,"Avenir Next",Jost,"PingFang SC",sans-serif';
  const T = { type: 1100, hold: 1700, gather: 2500, fly: 4900, land: 5500, cycle: 6400 };
  const TALL = 11;

  let lang = options.lang || 'zh';
  let W = 0, H = 0, dpr = 1, P = 9, L = null;
  let flights = [], cards = [], sparks = [], nextIndex = 0, nextSpawn = 0, lastLanding = -1e9;
  let pointer = null, raf = 0, running = false, clock = 0, last = 0;
  let ambient = [], clouds = [];
  const glyphCache = new Map();
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const ease = t => t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  const sine = t => -(Math.cos(Math.PI * t) - 1) / 2;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const span = (t, a, b) => clamp((t - a) / (b - a), 0, 1);
  const msgs = () => COPY[lang].msgs;
  const tint = sim => sim === 2 ? C.ice : C.amber;

  function glyphs(str) {
    if (glyphCache.has(str)) return glyphCache.get(str);
    const off = document.createElement('canvas');
    const octx = off.getContext('2d', { willReadFrequently: true });
    octx.font = `700 ${TALL}px ${SANS}`;
    const w = Math.ceil(octx.measureText(str).width) + 2;
    off.width = w; off.height = TALL + 4;
    octx.font = `700 ${TALL}px ${SANS}`;
    octx.textBaseline = 'top';
    octx.fillText(str, 1, 1);
    const data = octx.getImageData(0, 0, w, TALL + 4).data;
    const cells = [];
    for (let y = 0; y < TALL + 4; y++) for (let x = 0; x < w; x++) if (data[(y * w + x) * 4 + 3] > 120) cells.push([x, y]);
    const out = { cells, cols: w, rows: TALL + 4 };
    glyphCache.set(str, out);
    return out;
  }

  function bezier(p, t) {
    const u = 1 - t;
    return { x: u * u * p[0].x + 2 * u * t * p[1].x + t * t * p[2].x, y: u * u * p[0].y + 2 * u * t * p[1].y + t * t * p[2].y };
  }

  function layout() {
    const cr = canvas.getBoundingClientRect();
    const tr = textEl.getBoundingClientRect();
    const narrow = W < 900;
    const text = { x: tr.left - cr.left, y: tr.top - cr.top, w: tr.width, h: tr.height };
    let S;
    if (narrow) {
      const top = text.y + text.h + 34;
      S = { x: 16, y: top, w: W - 32, h: H - top - 16 };
    } else {
      const x = text.x + text.w + 36;
      const right = Math.min(W - 24, x + 700);
      S = { x, y: 28, w: right - x, h: H - 56 };
    }
    P = narrow ? 8 : 9;
    let iw = clamp(S.w * (narrow ? 0.42 : 0.37), 140, 250);
    let ih = iw * 2.02;
    if (ih > S.h - 34) { ih = S.h - 34; iw = ih / 2.02; }
    let aw = clamp(S.w * (narrow ? 0.44 : 0.36), 140, 240);
    let ah = aw * 1.58;
    if (ah > S.h * 0.74) { ah = S.h * 0.74; aw = ah / 1.58; }
    const gap = narrow ? clamp(S.w * 0.1, 24, 48) : clamp(S.w * 0.12, 52, 96);
    const gx = S.x + Math.max(0, (S.w - aw - gap - iw) / 2);
    const phone = { x: gx, y: S.y + S.h - ah - 30, w: aw, h: ah };
    const iphone = { x: gx + aw + gap, y: Math.max(S.y, phone.y + ah - ih - (narrow ? 20 : 46)), w: iw, h: ih };
    phone.screen = { x: phone.x + 9, y: phone.y + 9, w: aw - 18, h: ah - 18 };
    iphone.screen = { x: iphone.x + 7, y: iphone.y + 7, w: iw - 14, h: ih - 14 };
    const sc = iphone.screen;
    const cardH = clamp(iw * 0.25, 42, 60);
    const listY = sc.y + iw * 0.62;
    iphone.list = { x: sc.x + 8, y: listY, w: sc.w - 16, h: cardH, gap: 7 };
    const maxCols = Math.max(...msgs().map(m => glyphs(m.token).cols));
    const lp = Math.max(2, Math.min(5, Math.floor((phone.screen.w - 28) / maxCols)));
    const start = { x: phone.x + aw * 0.5, y: phone.y - 34 };
    const end = { x: iphone.list.x + 6 + cardH * 0.28, y: listY + cardH / 2 };
    const ctrl = { x: (start.x + end.x) / 2 - aw * 0.1, y: Math.max(S.y + 10, Math.min(start.y, end.y) - S.h * 0.3) };
    L = { narrow, S, text, phone, iphone, lp, path: [start, ctrl, end], bs: narrow ? 5 : 7 };
    seedAmbient();
  }

  function seedAmbient() {
    const band = L.narrow ? L.S.y : H * 0.55;
    ambient = [0, 1, 2, 3].map(i => ({
      y: 30 + (band - 30) * (0.15 + i * 0.22), speed: 26 + i * 9, offset: i * 377, bs: 2 + (i % 2) * 0.8, phase: i * 0.7
    }));
    clouds = [0, 1, 2].map(i => ({ pattern: CLOUDS[i], x: (W * (0.2 + i * 0.33)), y: 30 + i * (H * 0.18), cell: L.narrow ? 6 : 8, speed: 4 + i * 2 }));
  }

  function resize() {
    const r = canvas.getBoundingClientRect();
    W = Math.max(280, r.width); H = Math.max(320, r.height);
    dpr = Math.min(2, window.devicePixelRatio || 1);
    canvas.width = Math.round(W * dpr); canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    layout();
  }

  function tokenOrigin(g) {
    const s = L.phone.screen;
    return { x: s.x + (s.w - g.cols * L.lp) / 2, y: s.y + 40 };
  }

  function spawn(at) {
    const m = msgs()[nextIndex % msgs().length];
    nextIndex++;
    const g = glyphs(m.token);
    const o = tokenOrigin(g);
    const bs = L.bs, path = L.path;
    const parts = g.cells.map(([cx, cy], i) => {
      const target = BIRD[0][i % BIRD[0].length];
      return {
        hx: o.x + cx * L.lp, hy: o.y + cy * L.lp, col: cx,
        tx: path[0].x - 7 * bs + target[0] * bs, ty: path[0].y - 5 * bs + target[1] * bs,
        delay: (cx / g.cols) * 260 + Math.random() * 120
      };
    });
    flights.push({ m, g, o, parts, start: at, gated: false, landed: false, trail: [] });
  }

  function roundRect(x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function fit(text, maxW) {
    if (ctx.measureText(text).width <= maxW) return text;
    let s = text;
    while (s.length > 1 && ctx.measureText(s + '…').width > maxW) s = s.slice(0, -1);
    return s + '…';
  }

  function wrap(text, maxW, maxLines) {
    const out = [];
    let line = '';
    for (const ch of text) {
      if (ctx.measureText(line + ch).width > maxW && line) { out.push(line); line = ''; if (out.length === maxLines) return out; }
      line += ch;
    }
    if (line && out.length < maxLines) out.push(line);
    return out;
  }

  function drawField(hot) {
    const t = L.text;
    for (let y = P / 2; y < H; y += P) {
      for (let x = P / 2; x < W; x += P) {
        const inText = x > t.x - 10 && x < t.x + t.w + 10 && y > t.y - 10 && y < t.y + t.h + 10;
        let a = inText ? 0.05 : 0.09, s = 1.3;
        for (const h of hot) {
          const d = Math.hypot(x - h.x, y - h.y);
          if (d < h.r) { const k = 1 - d / h.r; a += k * h.a; s += k * h.s; }
        }
        ctx.fillStyle = `rgba(44,62,98,${Math.min(a, 0.6).toFixed(3)})`;
        ctx.fillRect(x - s / 2, y - s / 2, s, s);
      }
    }
  }

  function drawAmbient() {
    for (const c of clouds) {
      const span = W + 240;
      const x = ((c.x - (clock / 1000) * c.speed) % span + span) % span - 120;
      ctx.fillStyle = 'rgba(44,62,98,0.055)';
      c.pattern.forEach((row, ry) => [...row].forEach((ch, rx) => { if (ch === 'X') ctx.fillRect(x + rx * c.cell, c.y + ry * c.cell, c.cell - 1, c.cell - 1); }));
    }
    for (const b of ambient) {
      const span = W + 200;
      const x = ((clock / 1000) * b.speed + b.offset) % span - 100;
      const y = b.y + Math.sin(clock / 900 + b.phase) * 8;
      ctx.globalAlpha = 0.22;
      drawBird(x, y, b.bs, Math.floor(clock / 220 + b.phase * 3) % 2, C.navy2, 0, 1);
    }
    ctx.globalAlpha = 1;
  }

  function label(text, x, y) {
    ctx.font = `500 11px ${MONO}`; ctx.fillStyle = C.sub; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(text, x, y);
    ctx.textAlign = 'left';
  }

  function drawAndroid(active) {
    const { phone } = L;
    const s = phone.screen;
    ctx.save();
    ctx.shadowColor = 'rgba(16,32,56,0.38)'; ctx.shadowBlur = 44; ctx.shadowOffsetY = 22;
    const body = ctx.createLinearGradient(phone.x, phone.y, phone.x + phone.w, phone.y + phone.h);
    body.addColorStop(0, '#34476E'); body.addColorStop(1, '#14213A');
    roundRect(phone.x, phone.y, phone.w, phone.h, phone.w * 0.13); ctx.fillStyle = body; ctx.fill();
    ctx.restore();
    ctx.fillStyle = '#0D1628';
    roundRect(s.x, s.y, s.w, s.h, phone.w * 0.09); ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,0.12)';
    ctx.fillRect(phone.x + phone.w, phone.y + phone.h * 0.22, 2.5, phone.h * 0.12);

    const f = active ? active.f : null;
    const t = active ? active.t : 1e9;
    const m = f ? f.m : (flights.length ? flights[flights.length - 1].m : msgs()[0]);
    const tone = tint(m.sim);
    const wake = f ? 1 - span(t, 0, 900) : 0;
    if (wake > 0) {
      const glow = ctx.createRadialGradient(s.x + s.w / 2, s.y + s.h * 0.3, 4, s.x + s.w / 2, s.y + s.h * 0.3, s.w);
      glow.addColorStop(0, `rgba(240,176,94,${0.28 * wake})`); glow.addColorStop(1, 'rgba(240,176,94,0)');
      ctx.fillStyle = glow; roundRect(s.x, s.y, s.w, s.h, phone.w * 0.09); ctx.fill();
    }
    ctx.textBaseline = 'middle';
    ctx.font = `500 10px ${MONO}`; ctx.fillStyle = tone;
    ctx.fillText(fit(`SIM ${m.sim} · ${m.from}`, s.w - 40), s.x + 14, s.y + 20);
    ctx.fillStyle = C.red; ctx.globalAlpha = f && t < T.hold ? (Math.floor(clock / 180) % 2 ? 1 : 0.3) : 0.3;
    ctx.beginPath(); ctx.arc(s.x + s.w - 16, s.y + 20, 3.2, 0, Math.PI * 2); ctx.fill();
    ctx.globalAlpha = 1;

    const g = glyphs(m.token), o = tokenOrigin(g), lp = L.lp;
    ctx.fillStyle = 'rgba(240,176,94,0.07)';
    const cols = Math.floor((s.w - 24) / lp), ox = s.x + (s.w - cols * lp) / 2;
    for (let r = 0; r < g.rows; r++) for (let c = 0; c < cols; c++) ctx.fillRect(ox + c * lp, o.y + r * lp, lp - 1, lp - 1);
    const tokenBottom = o.y + g.rows * lp;
    if (!f || t >= T.gather) {
      ctx.font = `500 10px ${MONO}`; ctx.fillStyle = tone; ctx.globalAlpha = 0.75; ctx.textAlign = 'center';
      ctx.fillText(`✓ ${COPY[lang].sent}`, s.x + s.w / 2, o.y + g.rows * lp / 2);
      ctx.textAlign = 'left'; ctx.globalAlpha = 1;
    }
    ctx.font = `400 10.5px ${SANS}`; ctx.fillStyle = 'rgba(233,225,210,0.72)';
    const reveal = f ? span(t, 300, T.type + 300) : 1;
    const lines = wrap(m.body, s.w - 28, 3);
    const total = lines.join('').length;
    let shown = Math.floor(total * reveal);
    lines.forEach((ln, i) => {
      const part = ln.slice(0, Math.max(0, shown));
      shown -= ln.length;
      ctx.fillText(part, s.x + 14, tokenBottom + 18 + i * 16);
    });
    const py = s.y + s.h - 24;
    const rowsTop = tokenBottom + 18 + lines.length * 16 + 10;
    const rows = Math.max(0, Math.min(cards.length, Math.floor((py - 22 - rowsTop) / 22)));
    ctx.font = `500 9.5px ${MONO}`;
    for (let i = 0; i < rows; i++) {
      const c = cards[i], ry = py - 30 - i * 22;
      ctx.globalAlpha = 1 - i * 0.25;
      ctx.strokeStyle = 'rgba(255,255,255,0.06)'; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(s.x + 14, ry - 11); ctx.lineTo(s.x + s.w - 14, ry - 11); ctx.stroke();
      ctx.fillStyle = C.ok; ctx.fillText('✓', s.x + 14, ry);
      ctx.fillStyle = tint(c.m.sim); ctx.fillText(fit(`SIM ${c.m.sim} ${c.m.token} → Bark`, s.w - 70), s.x + 28, ry);
      ctx.fillStyle = '#7D8AA3'; ctx.textAlign = 'right';
      ctx.fillText(`${(0.3 + (c.m.token.length % 5) * 0.1).toFixed(1)}s`, s.x + s.w - 14, ry);
      ctx.textAlign = 'left';
    }
    ctx.globalAlpha = 1;
    ctx.font = `500 10px ${SANS}`;
    const pill = COPY[lang].status, pw = ctx.measureText(pill).width + 30;
    roundRect(s.x + (s.w - pw) / 2, py - 11, pw, 22, 11); ctx.fillStyle = 'rgba(255,255,255,0.07)'; ctx.fill();
    ctx.fillStyle = C.ok; ctx.globalAlpha = 0.6 + 0.4 * Math.sin(clock / 300);
    ctx.beginPath(); ctx.arc(s.x + (s.w - pw) / 2 + 12, py, 3, 0, Math.PI * 2); ctx.fill();
    ctx.globalAlpha = 1; ctx.fillStyle = '#C9D2E3';
    ctx.fillText(pill, s.x + (s.w - pw) / 2 + 21, py);

    if (f && t < 900) {
      const cx = phone.x + phone.w / 2, cy = phone.y - 6;
      ctx.lineWidth = 2; ctx.lineCap = 'round';
      for (let i = 0; i < 3; i++) {
        const k = span(t, i * 150, 600 + i * 150);
        if (k <= 0 || k >= 1) continue;
        ctx.strokeStyle = `rgba(240,176,94,${1 - k})`;
        ctx.beginPath(); ctx.arc(cx, cy, 10 + k * 34, Math.PI * 1.2, Math.PI * 1.8); ctx.stroke();
      }
    }
    label(COPY[lang].android, phone.x + phone.w / 2, phone.y + phone.h + 18);
  }

  function bell(x, y, s) {
    ctx.strokeStyle = '#fff'; ctx.lineWidth = Math.max(1.2, s * 0.09); ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(x - s * 0.32, y + s * 0.2); ctx.lineTo(x + s * 0.32, y + s * 0.2);
    ctx.moveTo(x - s * 0.24, y + s * 0.2);
    ctx.quadraticCurveTo(x - s * 0.24, y - s * 0.34, x, y - s * 0.34);
    ctx.quadraticCurveTo(x + s * 0.24, y - s * 0.34, x + s * 0.24, y + s * 0.2);
    ctx.stroke();
    ctx.beginPath(); ctx.arc(x, y + s * 0.3, s * 0.08, 0, Math.PI); ctx.stroke();
  }

  function drawIphone() {
    const { iphone } = L;
    const s = iphone.screen, list = iphone.list;
    ctx.save();
    ctx.shadowColor = 'rgba(16,32,56,0.35)'; ctx.shadowBlur = 50; ctx.shadowOffsetY = 26;
    roundRect(iphone.x, iphone.y, iphone.w, iphone.h, iphone.w * 0.17); ctx.fillStyle = '#1C1F27'; ctx.fill();
    ctx.restore();
    ctx.save();
    roundRect(s.x, s.y, s.w, s.h, iphone.w * 0.14); ctx.clip();
    const wall = ctx.createLinearGradient(0, s.y, 0, s.y + s.h);
    wall.addColorStop(0, '#22345A'); wall.addColorStop(0.55, '#3E5A8C'); wall.addColorStop(1, '#C98A5A');
    ctx.fillStyle = wall; ctx.fillRect(s.x, s.y, s.w, s.h);
    ctx.fillStyle = 'rgba(255,255,255,0.06)';
    for (let y = s.y + 4; y < s.y + s.h; y += 7) for (let x = s.x + 4; x < s.x + s.w; x += 7) ctx.fillRect(x, y, 1.4, 1.4);
    const pulse = 1 - span(clock - lastLanding, 0, 900);
    if (pulse > 0) { ctx.fillStyle = `rgba(255,255,255,${0.16 * pulse})`; ctx.fillRect(s.x, s.y, s.w, s.h); }
    roundRect(s.x + s.w * 0.33, s.y + 9, s.w * 0.34, Math.max(14, s.w * 0.085), 10); ctx.fillStyle = '#05070B'; ctx.fill();
    const now = new Date();
    const loc = COPY[lang].locale;
    ctx.fillStyle = 'rgba(255,255,255,0.85)'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.font = `500 ${Math.max(10, iphone.w * 0.05)}px ${SANS}`;
    ctx.fillText(new Intl.DateTimeFormat(loc, { month: 'long', day: 'numeric', weekday: 'long' }).format(now), s.x + s.w / 2, s.y + iphone.w * 0.24);
    ctx.fillStyle = '#fff'; ctx.font = `600 ${iphone.w * 0.24}px ${GEO}`;
    ctx.fillText(new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' }).format(now), s.x + s.w / 2, s.y + iphone.w * 0.42);
    ctx.textAlign = 'left';

    const newest = cards[0];
    const slide = newest ? ease(span(clock - newest.at, 0, 500)) : 1;
    cards.forEach((c, i) => {
      const k = i === 0 ? slide : 1;
      const y = list.y + i * (list.h + list.gap) - (1 - slide) * (i === 0 ? 10 : list.h + list.gap);
      ctx.globalAlpha = k * (1 - i * 0.12);
      roundRect(list.x, y, list.w, list.h, 13); ctx.fillStyle = 'rgba(250,250,252,0.88)'; ctx.fill();
      const ic = list.h * 0.56, ix = list.x + 8, iy = y + (list.h - ic) / 2;
      roundRect(ix, iy, ic, ic, ic * 0.26); ctx.fillStyle = C.navy; ctx.fill();
      bell(ix + ic / 2, iy + ic / 2, ic * 0.62);
      const tx = ix + ic + 8, tw = list.x + list.w - tx - 8;
      const fs = clamp(list.h * 0.2, 9, 12);
      ctx.textBaseline = 'alphabetic';
      ctx.font = `400 ${fs - 1}px ${SANS}`; ctx.fillStyle = '#8A8A90'; ctx.textAlign = 'right';
      ctx.fillText(COPY[lang].now, list.x + list.w - 8, y + list.h * 0.42);
      const nw = ctx.measureText(COPY[lang].now).width + 6;
      ctx.textAlign = 'left'; ctx.font = `600 ${fs}px ${SANS}`; ctx.fillStyle = '#111';
      ctx.fillText(fit(c.m.title, tw - nw), tx, y + list.h * 0.42);
      ctx.font = `400 ${fs}px ${SANS}`; ctx.fillStyle = '#2A2F3A';
      ctx.fillText(fit(c.m.body, tw), tx, y + list.h * 0.75);
    });
    ctx.globalAlpha = 1;
    ctx.fillStyle = 'rgba(255,255,255,0.7)';
    roundRect(s.x + s.w * 0.33, s.y + s.h - 12, s.w * 0.34, 4, 2); ctx.fill();
    ctx.restore();
    if (pulse > 0) {
      ctx.strokeStyle = `rgba(240,176,94,${pulse})`; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(L.path[2].x, L.path[2].y, 16 + (1 - pulse) * 44, 0, Math.PI * 2); ctx.stroke();
    }
    label(COPY[lang].bark, iphone.x + iphone.w / 2, iphone.y + iphone.h + 18);
  }

  function drawRoute() {
    const p = L.path;
    ctx.save(); ctx.setLineDash([2, 8]); ctx.strokeStyle = 'rgba(44,62,98,0.28)'; ctx.lineWidth = 1.6;
    ctx.beginPath(); ctx.moveTo(p[0].x, p[0].y); ctx.quadraticCurveTo(p[1].x, p[1].y, p[2].x, p[2].y); ctx.stroke(); ctx.restore();
    const g = bezier(p, 0.5);
    const hot = flights.find(f => { const t = clock - f.start; return t > T.gather && t < T.fly; });
    let pulse = 0;
    if (hot) { const u = sine(span(clock - hot.start, T.gather, T.fly)); pulse = Math.max(0, 1 - Math.abs(u - 0.5) / 0.12); }
    const r = 28 + pulse * 8;
    ctx.save();
    ctx.fillStyle = `rgba(246,242,235,0.9)`;
    ctx.beginPath(); ctx.arc(g.x, g.y, r, 0, Math.PI * 2); ctx.fill();
    ctx.setLineDash([3, 5]); ctx.lineWidth = 1.3; ctx.strokeStyle = `rgba(44,62,98,${0.4 + pulse * 0.4})`;
    ctx.beginPath(); ctx.arc(g.x, g.y, r, 0, Math.PI * 2); ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = pulse > 0.2 ? C.navy : `rgba(240,176,94,${0.18 + pulse * 0.3})`;
    ctx.beginPath(); ctx.arc(g.x, g.y, r - 7, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = pulse > 0.2 ? C.amber : C.navy2; ctx.lineWidth = 1.8; ctx.lineCap = 'round';
    roundRect(g.x - 8, g.y - 2, 16, 12, 3); ctx.stroke();
    ctx.beginPath(); ctx.arc(g.x, g.y - 2, 5, Math.PI, 0); ctx.stroke();
    ctx.restore();
    label('HTTPS + AES', g.x, g.y - r - 14);
  }

  function drawBird(x, y, bs, frame, color, angle, scale) {
    ctx.save();
    ctx.translate(x, y); ctx.rotate(angle); ctx.scale(scale, scale);
    const ox = -7 * bs, oy = -5 * bs;
    ctx.fillStyle = color;
    for (const [cx, cy] of BIRD[frame]) ctx.fillRect(ox + cx * bs, oy + cy * bs, bs - 0.5, bs - 0.5);
    if (BIRD[frame].some(([cx, cy]) => cx === EYE[0] && cy === EYE[1])) { ctx.fillStyle = C.paper; ctx.fillRect(ox + EYE[0] * bs, oy + EYE[1] * bs, bs - 0.5, bs - 0.5); }
    ctx.restore();
  }

  function drawSourceDots(f, t) {
    const reveal = span(t, 0, T.type);
    const color = tint(f.m.sim);
    const size = L.lp - 0.8;
    for (const p of f.parts) {
      if (p.col / f.g.cols > reveal) continue;
      const k = ease(span(t, T.hold + p.delay, T.gather - 120 + p.delay * 0.2));
      if (k >= 1) continue;
      const x = p.hx + (p.tx - p.hx) * k, y = p.hy + (p.ty - p.hy) * k - Math.sin(k * Math.PI) * 50;
      const sz = size + (L.bs - size) * k;
      if (k > 0) { ctx.globalAlpha = 0.25; ctx.fillStyle = color; ctx.fillRect(x - 1.5, y - 1.5, sz + 3, sz + 3); }
      ctx.globalAlpha = 1; ctx.fillStyle = color; ctx.fillRect(x, y, sz, sz);
    }
    ctx.globalAlpha = 1;
    if (t < T.type + 300) {
      const cx = f.o.x + Math.min(f.g.cols, reveal * f.g.cols) * L.lp + 2;
      if (Math.floor(clock / 260) % 2) { ctx.fillStyle = color; ctx.fillRect(cx, f.o.y + 2, L.lp, f.g.rows * L.lp - 6); }
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
      for (let i = 0; i < 14; i++) sparks.push({ x: pos.x, y: pos.y, vx: (Math.random() - 0.3) * 80, vy: (Math.random() - 0.5) * 80, ch: CIPHER[(Math.random() * CIPHER.length) | 0], born: clock });
    }
    f.trail.push({ x: pos.x, y: pos.y, at: clock, locked: f.gated });
    let prev = null;
    for (const p of f.trail) {
      const age = clock - p.at;
      if (age > 1800) continue;
      if (prev && Math.hypot(p.x - prev.x, p.y - prev.y) < 11) continue;
      prev = p;
      ctx.globalAlpha = (1 - age / 1800) * 0.7;
      ctx.fillStyle = p.locked ? C.navy2 : tint(f.m.sim);
      ctx.fillRect(p.x - 2, p.y - 2, 4, 4);
    }
    const land = span(t, T.fly, T.land);
    const frame = Math.floor(clock / 140) % 2;
    const color = f.gated ? C.navy : (f.m.sim === 2 ? '#7F9AD0' : '#D9962E');
    ctx.globalAlpha = 1 - land;
    if (land > 0) drawBird(L.path[2].x, L.path[2].y, L.bs, frame, color, 0, 1 - land * 0.7);
    else drawBird(pos.x, pos.y, L.bs, frame, color, angle, 1);
    ctx.globalAlpha = 1;
  }

  function drawSparks() {
    sparks = sparks.filter(s => clock - s.born < 1200);
    ctx.font = `600 12px ${MONO}`; ctx.textBaseline = 'middle';
    for (const s of sparks) {
      const k = (clock - s.born) / 1200;
      ctx.globalAlpha = 1 - k;
      ctx.fillStyle = C.navy2;
      ctx.fillText(s.ch, s.x + s.vx * k, s.y + s.vy * k);
    }
    ctx.globalAlpha = 1;
  }

  function render() {
    ctx.clearRect(0, 0, W, H);
    const hot = [];
    for (const f of flights) {
      const t = clock - f.start;
      if (t >= T.gather && t < T.land) { const p = bezier(L.path, sine(span(t, T.gather, T.fly))); hot.push({ x: p.x, y: p.y, r: 120, a: 0.3, s: 1.6 }); }
    }
    if (pointer) hot.push({ x: pointer.x, y: pointer.y, r: 130, a: 0.35, s: 2.2 });
    drawField(hot);
    drawAmbient();
    drawRoute();
    const activeF = flights.find(f => clock - f.start < T.gather) || flights[flights.length - 1];
    drawAndroid(activeF ? { f: activeF, t: clock - activeF.start } : null);
    drawIphone();
    for (const f of flights) {
      const t = clock - f.start;
      if (t < T.gather) drawSourceDots(f, t);
      drawFlight(f, t);
      if (t >= T.land && !f.landed) {
        f.landed = true; lastLanding = clock;
        cards.unshift({ m: f.m, at: clock });
        if (cards.length > 3) cards.length = 3;
      }
    }
    drawSparks();
    flights = flights.filter(f => clock - f.start < T.cycle + T.land);
  }

  function step(now) {
    const dt = last ? Math.min(64, now - last) : 16;
    last = now; clock += dt;
    if (clock >= nextSpawn) { spawn(clock); nextSpawn = clock + T.cycle; }
    render();
    if (running) raf = requestAnimationFrame(step);
  }

  function staticFrame() {
    flights = []; sparks = []; nextIndex = 0;
    clock = 20000;
    cards = [{ m: msgs()[2], at: 0 }, { m: msgs()[1], at: 0 }];
    lastLanding = -1e9;
    spawn(clock - (T.gather + (T.fly - T.gather) * 0.62));
    flights[0].gated = true;
    render();
  }

  function start() {
    if (running || reduce) return;
    running = true; last = 0; raf = requestAnimationFrame(step);
  }
  function stop() { running = false; cancelAnimationFrame(raf); }
  function restart() {
    stop(); flights = []; sparks = []; nextIndex = 0; nextSpawn = 0; clock = 0; lastLanding = -1e9;
    cards = [{ m: msgs()[2], at: -1e4 }, { m: msgs()[1], at: -1e4 }];
    resize();
    if (reduce) staticFrame(); else start();
  }

  const ro = new ResizeObserver(() => { resize(); if (!running) render(); });
  ro.observe(canvas);
  ro.observe(textEl);
  const io = new IntersectionObserver(([e]) => { if (e.isIntersecting && !document.hidden) start(); else stop(); });
  io.observe(canvas);
  document.addEventListener('visibilitychange', () => { if (document.hidden) stop(); else start(); });
  host.addEventListener('pointermove', e => { const r = canvas.getBoundingClientRect(); pointer = { x: e.clientX - r.left, y: e.clientY - r.top }; });
  host.addEventListener('pointerleave', () => { pointer = null; });

  restart();

  return {
    setLang(l) { lang = l; glyphCache.clear(); restart(); }
  };
}
