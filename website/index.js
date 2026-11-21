(() => {
  const heroEl = document.getElementById('hero');
  const hero = FionHero(document.getElementById('hero-canvas'), { lang: 'zh', host: heroEl, textEl: document.getElementById('hero-copy') });

  const aes = document.getElementById('aes');
  const out = document.getElementById('aes-out');
  const CIPHER = 'k8mQ7x2pL4vR9nB3cT6wY0hZsJ5eD1gF';
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let dict = FION_I18N.zh;
  let scrambleTimer = 0;

  function packetLines(mode) {
    return mode === 'locked'
      ? [['device_key: ', '"aB3x…"'], ['id: ', '"9f4c…"'], ['ciphertext: ', '"k8mQ7x2pL4vR9nB3cT6wY…"', true]]
      : [['device_key: ', '"aB3x…"'], ['body: ', `"${dict['aes.sample']}"`, true]];
  }

  function renderPacket(lines, progress) {
    out.textContent = '';
    lines.forEach(([k, v, hot], i) => {
      out.append(k);
      const span = document.createElement('span');
      if (hot) span.className = 'hot';
      span.textContent = hot && progress < 1
        ? [...v].map((ch, j) => (j / v.length < progress || ch === '"' ? ch : CIPHER[(Math.random() * CIPHER.length) | 0])).join('')
        : v;
      out.append(span);
      if (i < lines.length - 1) out.append('\n');
    });
  }

  function setMode(mode, animate) {
    aes.dataset.mode = mode;
    const toggle = document.getElementById('pause-toggle');
  const lane = document.getElementById('lane');
  toggle.addEventListener('click', () => {
    const on = toggle.getAttribute('aria-checked') !== 'true';
    toggle.setAttribute('aria-checked', String(on));
    lane.dataset.on = String(on);
  });
  aes.querySelectorAll('.seg button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.mode === mode)));
    document.getElementById('aes-step').textContent = dict[`aes.step.${mode}`];
    document.getElementById('aes-out-label').textContent = dict[`aes.out.${mode}`];
    document.getElementById('aes-verdict-text').textContent = dict[`aes.v.${mode}`];
    const verdict = document.getElementById('aes-verdict');
    verdict.className = `aes-verdict ${mode === 'locked' ? 'yes' : 'no'}`;
    verdict.querySelector('use').setAttribute('href', mode === 'locked' ? '#i-check' : '#i-x');
    const lines = packetLines(mode);
    clearInterval(scrambleTimer);
    if (!animate || reduce) { renderPacket(lines, 1); return; }
    const t0 = performance.now();
    scrambleTimer = setInterval(() => {
      const p = Math.min(1, (performance.now() - t0) / 700);
      renderPacket(lines, p);
      if (p >= 1) clearInterval(scrambleTimer);
    }, 40);
  }
  aes.querySelectorAll('.seg button').forEach(b => b.addEventListener('click', () => setMode(b.dataset.mode, true)));

  fionLang((lang, d) => {
    dict = d;
    hero.setLang(lang);
    setMode(aes.dataset.mode, false);
    document.title = lang === 'zh'
      ? '飞鸿 Fion · 将 Android 短信与未接来电推送到 iPhone'
      : 'Fion · Forward Android SMS & Missed Calls to iPhone';
  });

  const links = [...document.querySelectorAll('.journey a')];
  const stations = links.map(a => document.querySelector(a.getAttribute('href')));
  const story = document.getElementById('story');
  const bird = document.getElementById('thread-bird');
  let ticking = false;

  function onScroll() {
    ticking = false;
    const probe = window.innerHeight * 0.38;
    let current = -1;
    stations.forEach((s, i) => { if (s.getBoundingClientRect().top <= probe) current = i; });
    links.forEach((a, i) => { if (i === current) a.setAttribute('aria-current', 'step'); else a.removeAttribute('aria-current'); });
    const r = story.getBoundingClientRect();
    const travel = Math.max(0, r.height - 200);
    const y = Math.min(travel, Math.max(0, probe - r.top - 26));
    bird.style.transform = `translateY(${y}px) rotate(90deg)`;
  }
  window.addEventListener('scroll', () => { if (!ticking) { ticking = true; requestAnimationFrame(onScroll); } }, { passive: true });
  window.addEventListener('resize', onScroll);
  onScroll();

  document.getElementById('year').textContent = new Date().getFullYear();
})();
