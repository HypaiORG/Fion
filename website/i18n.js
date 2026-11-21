const FION_I18N = {
  zh: {
    'name': '飞鸿',
    'nav.safe': '隐私保护', 'nav.policy': '隐私政策',
    'kicker': 'Android → iPhone · 免费开源',
    'h1a': '让短信化作飞鸿', 'h1b': '落在你的 iPhone 上',
    'lede': '备用机收到新短信与未接来电，经你的 Bark 推送到 iPhone。',
    'cta.get': '下载 APK', 'cta.how': '看它怎么飞',
    'fact.server': '无飞鸿服务器', 'fact.open': '免费开源',
    'hero.aria': '动画：一条短信在 Android 手机上亮起，化作像素飞鸿，穿过加密锁，落入 iPhone 的 Bark 通知',
    'journey.aria': '页面导览',
    'st.fly': '飞什么', 'st.ask': '要权限', 'st.safe': '怎么保密', 'st.keep': '留什么', 'st.never': '不做什么', 'st.you': '你说了算', 'st.go': '开始用',
    'fly.h': '两种消息，飞到 iPhone', 'fly.p': '新短信与未接来电，各自一条通知。',
    'fly.sms': '新短信', 'fly.call': '未接来电', 'now': '现在',
    'n1.t': '短信 · 95588', 'n1.b': '【银行】验证码 839201，5 分钟有效',
    'n2.t': '未接来电 · 138 0013 8000', 'n2.b': '呼叫 SIM 2，今日第 2 次未接',
    'fly.dual': '双卡清晰区分，每条通知都标明接收的 SIM 卡。',
    'next.ask': '下一站：需要哪些权限',
    'ask.h': '仅需 4 类权限', 'ask.p': '绿色是实际用途，红色绝不触碰。',
    'p1.t': '接收新短信', 'p1.y': '只看新短信', 'p1.n': '历史短信',
    'p2.t': '读取通话记录', 'p2.y': '只挑未接', 'p2.n': '已接与拨出',
    'p3.t': '读取 SIM 卡信息', 'p3.y': '标明哪张卡', 'p3.n': '拨号与通讯录',
    'p4.t': '通知、后台与联网', 'p4.y': '只连你的 Bark', 'p4.n': '任何第三方',
    'ask.never': '这些权限，飞鸿从不申请',
    'nv.inbox': '读历史短信', 'nv.send': '发短信', 'nv.call': '打电话', 'nv.contacts': '通讯录', 'nv.mic': '麦克风', 'nv.pin': '位置',
    'next.safe': '下一站：消息如何保密',
    'safe.h': '直达你的 Bark，中间没有飞鸿', 'safe.p': '消息在手机上加密，只走你选的路。',
    'r1': '你的手机', 'r1s': '只读新消息', 'r2s': '手机端加密', 'r3': '你的 Bark', 'r3s': '你选的服务器', 'r4s': '通知亮起',
    'ghost.t': '无飞鸿服务器', 'ghost.s': '开发者收不到任何数据',
    'aes.h': '离开手机前先上锁', 'aes.aria': '切换 AES 加密', 'aes.on': '加 AES 锁', 'aes.off': '不加 AES',
    'aes.src': '手机收到的短信', 'aes.sample': '【银行】验证码 839201，5 分钟有效',
    'aes.step.locked': 'AES 密钥 + 随机 IV', 'aes.step.plain': '仅 HTTPS 传输',
    'aes.out.locked': '发出的密文', 'aes.out.plain': '发出的明文',
    'aes.v.locked': '服务器只看到密文，只有你的 iPhone 能解开。',
    'aes.v.plain': 'HTTPS 仅保护传输过程，服务器仍能看到原文。',
    'sv1.t': '默认 Bark', 'sv1.s': '官方服务，开箱即用',
    'sv2.t': '自建 Cloudflare', 'sv2.s': '免费部署，全程归你', 'sv2.tag': '完全掌控',
    'next.keep': '下一站：手机里留了什么',
    'keep.h': '手机里只留这 3 样', 'keep.p': '全部保存在飞鸿的私有空间里。',
    'k1.t': '你的配置与密钥', 'k1.s': '其他应用无法读取',
    'k2.t': '转发统计数字', 'k2.s': '只记数量与耗时，不含正文',
    'k3.t': '断网暂存队列', 'k3.s': '联网自动补发，超 24 小时自动丢弃',
    'keep.nb': '已关闭云备份与换机迁移',
    'next.never': '下一站：飞鸿绝不做的事',
    'never.h': '飞鸿绝不做的 4 件事',
    'nv1.t': '无飞鸿服务器', 'nv1.s': '开发者收不到数据',
    'nv2.t': '无广告与统计', 'nv2.s': '不埋点、不追踪',
    'nv3.t': '无静默上报', 'nv3.s': '无崩溃与更新探测',
    'nv4.t': '不卖不外传', 'nv4.s': '绝不传给第三方',
    'next.you': '下一站：一切由你掌控',
    'you.h': '一切由你掌控',
    'c1.t': '随时关', 'c1.aria': '转发开关', 'c1.s': '一键暂停转发',
    'c2.t': '随时撤', 'c2.s': '系统设置撤权',
    'c3.t': '卸载清光', 'c3.s': '本机数据全清',
    'next.go': '最后一站：三步开始使用',
    'go.h': '三步放飞',
    's1.t': '下载并安装 APK', 's1.s': '从 GitHub Releases 下载，需要 Android 15 及以上。',
    's2.t': '放行受限权限', 's2.s': '首次授权被拦截后，到系统「应用信息」右上角 ⋮ 选「允许限制性设置」。',
    's2.c1': '被拦截', 's2.c2': '点这里放行', 's2.a1': '系统已拒绝向此应用授予权限', 's2.a2': '允许限制性设置',
    's3.t': '填写 Device Key', 's3.s': '打开「设置 › Bark 推送设置」，填入服务器地址与 Device Key 即刻生效。', 's3.ok': '配置已保存，转发就绪',
    'fin.h': '让备用机安心待在抽屉里', 'fin.p': '新短信与未接来电，都会飞到你身边。',
    'policy.title': '隐私政策 · 飞鸿 Fion', 'policy.h1': '隐私政策', 'policy.sub': '飞鸿 Fion · Android', 'policy.home': '飞鸿首页'
  },
  en: {
    'name': 'Fion',
    'nav.safe': 'Privacy', 'nav.policy': 'Policy',
    'kicker': 'Android → iPhone · Free & open source',
    'h1a': 'Texts take wing,', 'h1b': 'land on your iPhone',
    'lede': 'New SMS and missed calls from your spare Android fly to iPhone via your own Bark.',
    'cta.get': 'Download APK', 'cta.how': 'See how',
    'fact.server': 'No Fion server', 'fact.open': 'Free & open source',
    'hero.aria': 'Animation: a text lights up on an Android phone, turns into a pixel bird, passes an encryption lock, and lands as a Bark notification on an iPhone.',
    'journey.aria': 'Page navigation',
    'st.fly': 'Alerts', 'st.ask': 'Permissions', 'st.safe': 'Privacy', 'st.keep': 'Storage', 'st.never': 'Never', 'st.you': 'Control', 'st.go': 'Start',
    'fly.h': 'Two kinds of alerts fly to your iPhone', 'fly.p': 'New SMS and missed calls arrive as separate alerts.',
    'fly.sms': 'New SMS', 'fly.call': 'Missed call', 'now': 'now',
    'n1.t': 'SMS · 72975', 'n1.b': 'Bank code: 839201 · 5 min',
    'n2.t': 'Missed call · 415-555-0132', 'n2.b': 'Called SIM 2, call #2 today',
    'fly.dual': 'Dual-SIM ready. Every alert shows which SIM received it.',
    'next.ask': 'Next: permissions Fion needs',
    'ask.h': 'Only 4 permission groups', 'ask.p': 'Green shows what Fion uses. Red is never touched.',
    'p1.t': 'Receive new SMS', 'p1.y': 'New SMS only', 'p1.n': 'Old inbox',
    'p2.t': 'Read call log', 'p2.y': 'Missed only', 'p2.n': 'Other calls',
    'p3.t': 'Read SIM info', 'p3.y': 'Labels SIM', 'p3.n': 'Calls & contacts',
    'p4.t': 'Background & network', 'p4.y': 'Your Bark only', 'p4.n': 'Third parties',
    'ask.never': 'Fion never asks for these',
    'nv.inbox': 'SMS inbox', 'nv.send': 'Send SMS', 'nv.call': 'Make calls', 'nv.contacts': 'Contacts', 'nv.mic': 'Microphone', 'nv.pin': 'Location',
    'next.safe': 'Next: how your data stays private',
    'safe.h': 'Straight to your Bark. No Fion in between.', 'safe.p': 'Encrypted on your phone. Sent only where you choose.',
    'r1': 'Your phone', 'r1s': 'New alerts only', 'r2s': 'Encrypted locally', 'r3': 'Your Bark', 'r3s': 'Your server', 'r4s': 'Alert arrives',
    'ghost.t': 'No Fion server', 'ghost.s': 'We never see your data',
    'aes.h': 'Lock it before it leaves', 'aes.aria': 'Toggle AES encryption', 'aes.on': 'AES on', 'aes.off': 'AES off',
    'aes.src': 'SMS on your phone', 'aes.sample': 'Bank code: 839201 · 5 min',
    'aes.step.locked': 'AES key + random IV', 'aes.step.plain': 'HTTPS only',
    'aes.out.locked': 'Sent as ciphertext', 'aes.out.plain': 'Sent as plaintext',
    'aes.v.locked': 'The server sees only ciphertext. Only your iPhone can decrypt it.',
    'aes.v.plain': 'HTTPS protects transit, but the server can still read the message.',
    'sv1.t': 'Default Bark', 'sv1.s': 'Official, ready to use',
    'sv2.t': 'Cloudflare', 'sv2.s': 'Free & self-hosted', 'sv2.tag': 'All yours',
    'next.keep': 'Next: what stays on your phone',
    'keep.h': 'Only 3 things stay on your phone', 'keep.p': 'All stored inside Fion\'s private app storage.',
    'k1.t': 'Your settings & keys', 'k1.s': 'Isolated from other apps',
    'k2.t': 'Forwarding stats', 'k2.s': 'Counts & latency, no text',
    'k3.t': 'Offline retry queue', 'k3.s': 'Retries online, drops after 24h',
    'keep.nb': 'Cloud backup and device transfer are disabled',
    'next.never': 'Next: what Fion never does',
    'never.h': '4 things Fion never does',
    'nv1.t': 'No Fion server', 'nv1.s': 'Zero data sent to Fion',
    'nv2.t': 'No trackers', 'nv2.s': 'Zero ads or analytics',
    'nv3.t': 'No telemetry', 'nv3.s': 'No crash or update pings',
    'nv4.t': 'Never shared', 'nv4.s': 'Never sent to third parties',
    'next.you': 'Next: you stay in control',
    'you.h': 'You stay in control',
    'c1.t': 'Pause', 'c1.aria': 'Forwarding switch', 'c1.s': 'One tap anytime',
    'c2.t': 'Revoke', 'c2.s': 'In system settings',
    'c3.t': 'Uninstall', 'c3.s': 'Wipes all local data',
    'next.go': 'Last stop: start in 3 steps',
    'go.h': 'Three steps to take off',
    's1.t': 'Install the APK', 's1.s': 'Download from GitHub Releases. Requires Android 15 or later.',
    's2.t': 'Allow restricted access', 's2.s': 'If Android blocks SMS or call log, open App info, tap ⋮, and choose "Allow restricted settings".',
    's2.c1': 'Blocked', 's2.c2': 'Tap to allow', 's2.a1': 'App was denied access', 's2.a2': 'Allow restricted settings',
    's3.t': 'Add your Device Key', 's3.s': 'Open Settings › Bark push settings, then enter your server and Device Key.', 's3.ok': 'Ready to forward',
    'fin.h': 'Leave the spare phone in a drawer', 'fin.p': 'New SMS and missed calls still find you.',
    'policy.title': 'Privacy Policy · Fion', 'policy.h1': 'Privacy Policy', 'policy.sub': 'Fion · Android', 'policy.home': 'Fion Home'
  }
};

function fionLang(onChange) {
  const KEY = 'fion-lang';
  let saved = null;
  try { saved = localStorage.getItem(KEY); } catch (e) {}
  let lang = saved === 'zh' || saved === 'en' ? saved : ((navigator.language || '').toLowerCase().startsWith('zh') ? 'zh' : 'en');
  const buttons = document.querySelectorAll('.lang-toggle button');

  function apply() {
    const dict = FION_I18N[lang];
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.querySelectorAll('[data-t]').forEach(el => { const v = dict[el.dataset.t]; if (v != null) el.textContent = v; });
    document.querySelectorAll('[data-t-aria]').forEach(el => { const v = dict[el.dataset.tAria]; if (v != null) el.setAttribute('aria-label', v); });
    document.querySelectorAll('[data-t-alt]').forEach(el => { const v = dict[el.dataset.tAlt]; if (v != null) el.alt = v; });
    document.querySelectorAll('[data-src-zh]').forEach(el => { el.src = el.dataset[lang === 'zh' ? 'srcZh' : 'srcEn']; });
    buttons.forEach(b => b.classList.toggle('active', b.dataset.lang === lang));
    onChange(lang, dict);
  }

  buttons.forEach(b => b.addEventListener('click', () => {
    if (b.dataset.lang === lang) return;
    lang = b.dataset.lang;
    try { localStorage.setItem(KEY, lang); } catch (e) {}
    apply();
  }));
  apply();
}
