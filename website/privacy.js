fionLang((lang, d) => {
  document.getElementById('content-zh').hidden = lang !== 'zh';
  document.getElementById('content-en').hidden = lang !== 'en';
  document.title = d['policy.title'];
});
document.getElementById('year').textContent = new Date().getFullYear();
