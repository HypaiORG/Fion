(() => {
  const PAGE = window.__FION__;
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  FionHero(document.getElementById('hero-canvas'), {
    host: document.getElementById('hero'),
    textEl: document.getElementById('hero-copy'),
    copy: PAGE.hero
  });

  const toggle = document.getElementById('pause-toggle');
  const lane = document.getElementById('lane');
  toggle.addEventListener('click', () => {
    const on = toggle.getAttribute('aria-checked') !== 'true';
    toggle.setAttribute('aria-checked', String(on));
    lane.dataset.on = String(on);
  });

  const aes = document.getElementById('aes');
  const out = document.getElementById('aes-out');
  const stepEl = document.getElementById('aes-step');
  const outLabel = document.getElementById('aes-out-label');
  const verdict = document.getElementById('aes-verdict');
  const verdictText = document.getElementById('aes-verdict-text');
  const verdictUse = verdict.querySelector('use');
  const segButtons = [...aes.querySelectorAll('.seg button')];
  const CIPHER = 'k8mQ7x2pL4vR9nB3cT6wY0hZsJ5eD1gF';
  let scrambleRaf = 0;

  function packetLines(mode) {
    return mode === 'locked'
      ? [['device_key: ', '"aB3x…"'], ['id: ', '"9f4c…"'], ['ciphertext: ', '"k8mQ7x2pL4vR9nB3cT6wY…"', true]]
      : [['device_key: ', '"aB3x…"'], ['body: ', `"${PAGE.aes.sample}"`, true]];
  }

  function renderPacket(lines, progress) {
    out.textContent = '';
    for (let i = 0; i < lines.length; i++) {
      const [k, v, hot] = lines[i];
      out.append(k);
      const span = document.createElement('span');
      if (hot) span.className = 'hot';
      span.textContent = hot && progress < 1
        ? [...v].map((ch, j) => (j / v.length < progress || ch === '"' ? ch : CIPHER[(Math.random() * CIPHER.length) | 0])).join('')
        : v;
      out.append(span);
      if (i < lines.length - 1) out.append('\n');
    }
  }

  function setMode(mode) {
    if (aes.dataset.mode === mode) return;
    aes.dataset.mode = mode;
    for (const b of segButtons) b.setAttribute('aria-pressed', String(b.dataset.mode === mode));
    const copy = PAGE.aes[mode];
    stepEl.textContent = copy.step;
    outLabel.textContent = copy.out;
    verdictText.textContent = copy.verdict;
    verdict.className = `aes-verdict ${mode === 'locked' ? 'yes' : 'no'}`;
    verdictUse.setAttribute('href', mode === 'locked' ? '#i-check' : '#i-x');
    const lines = packetLines(mode);
    cancelAnimationFrame(scrambleRaf);
    if (reduce) { renderPacket(lines, 1); return; }
    const t0 = performance.now();
    const tick = now => {
      const p = Math.min(1, (now - t0) / 560);
      renderPacket(lines, p);
      if (p < 1) scrambleRaf = requestAnimationFrame(tick);
    };
    scrambleRaf = requestAnimationFrame(tick);
  }

  for (const b of segButtons) b.addEventListener('click', () => setMode(b.dataset.mode));

  const links = [...document.querySelectorAll('.journey a')];
  const stations = links.map(a => document.querySelector(a.getAttribute('href')));
  const story = document.getElementById('story');
  const bird = document.getElementById('thread-bird');
  let lastCurrent = -2;
  let ticking = false;

  function onScroll() {
    ticking = false;
    const probe = window.innerHeight * 0.38;
    let current = -1;
    for (let i = 0; i < stations.length; i++) {
      if (stations[i].getBoundingClientRect().top <= probe) current = i;
    }
    if (current !== lastCurrent) {
      lastCurrent = current;
      for (let i = 0; i < links.length; i++) {
        if (i === current) links[i].setAttribute('aria-current', 'step');
        else links[i].removeAttribute('aria-current');
      }
    }
    const r = story.getBoundingClientRect();
    const travel = Math.max(0, r.height - 200);
    const y = Math.min(travel, Math.max(0, probe - r.top - 26));
    bird.style.transform = `translate3d(0,${y.toFixed(1)}px,0) rotate(90deg)`;
  }

  window.addEventListener('scroll', () => {
    if (!ticking) { ticking = true; requestAnimationFrame(onScroll); }
  }, { passive: true });
  window.addEventListener('resize', onScroll, { passive: true });
  onScroll();

  if ('IntersectionObserver' in window) {
    const stationIO = new IntersectionObserver(entries => {
      for (const e of entries) e.target.dataset.inview = e.isIntersecting ? 'true' : 'false';
    }, { rootMargin: '120px 0px' });
    for (const s of stations) stationIO.observe(s);
  } else {
    for (const s of stations) s.dataset.inview = 'true';
  }

  const dlg = document.getElementById('lightbox');
  if (dlg) {
    const dlgImg = dlg.querySelector('img');
    const dlgCap = dlg.querySelector('figcaption');
    document.querySelectorAll('.shot-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const img = btn.querySelector('img');
        dlgImg.src = img.currentSrc || img.src;
        dlgImg.alt = img.alt;
        dlgCap.textContent = img.alt;
        dlg.showModal();
      });
    });
    dlg.addEventListener('click', e => { if (e.target === dlg) dlg.close(); });
    dlg.querySelector('.lightbox-close').addEventListener('click', () => dlg.close());
  }
})();
