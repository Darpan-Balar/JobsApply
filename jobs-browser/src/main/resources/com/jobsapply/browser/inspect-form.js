// Read-only inspection of one frame. Never modifies the page. Returns the addressable form controls and signals that
// require the human (password, one-time code, CAPTCHA, challenge page).
() => {
  const out = { fields: [], password: false, otp: false, captcha: false, challenge: false, title: document.title || '' };
  const clean = t => (t || '').replace(/\s+/g, ' ').trim();
  const stripMark = t => clean(t).replace(/\s*\*+\s*$/, '').trim();
  const visible = el => {
    if (!el.isConnected) return false;
    const s = getComputedStyle(el);
    if (s.display === 'none' || s.visibility === 'hidden') return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  const textOf = el => clean(el ? (el.innerText || el.textContent) : '');
  const labelOf = el => {
    const by = el.getAttribute('aria-labelledby');
    if (by) {
      const t = by.split(/\s+/).map(id => document.getElementById(id)).filter(Boolean).map(textOf).join(' ');
      if (clean(t)) return clean(t);
    }
    if (el.getAttribute('aria-label')) return clean(el.getAttribute('aria-label'));
    if (el.id) {
      const l = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
      if (l && textOf(l)) return textOf(l);
    }
    const wrap = el.closest('label');
    if (wrap && textOf(wrap)) return textOf(wrap);
    if (el.placeholder) return clean(el.placeholder);
    return null;
  };
  const groupLabelOf = el => {
    const fs = el.closest('fieldset');
    if (fs && fs.querySelector('legend')) return textOf(fs.querySelector('legend'));
    const g = el.closest('[role=radiogroup],[role=group]');
    if (g) {
      if (g.getAttribute('aria-label')) return clean(g.getAttribute('aria-label'));
      const by = g.getAttribute('aria-labelledby');
      if (by && document.getElementById(by)) return textOf(document.getElementById(by));
    }
    return null;
  };
  // A selector plus the element's position among its matches, so the adapter can address it without touching the DOM.
  const address = el => {
    const tag = el.tagName.toLowerCase();
    const selector = el.id ? '[id="' + CSS.escape(el.id) + '"]' : tag + '[name="' + CSS.escape(el.name) + '"]';
    return { selector, index: Array.prototype.indexOf.call(document.querySelectorAll(selector), el) };
  };
  const required = (el, label) => el.required || el.getAttribute('aria-required') === 'true' || /\*\s*$/.test(label || '');

  const groups = new Map();
  for (const el of document.querySelectorAll('input, select, textarea')) {
    const type = (el.getAttribute('type') || (el.tagName === 'INPUT' ? 'text' : el.tagName)).toLowerCase();
    const shown = visible(el);
    if (type === 'password') { if (shown) out.password = true; continue; }
    if (['hidden', 'submit', 'button', 'reset', 'image'].includes(type)) continue;
    if (!shown && type !== 'file') continue;
    const rawLabel = labelOf(el);
    if (el.getAttribute('autocomplete') === 'one-time-code'
        || /verification code|one[- ]time (pass)?code|security code|authentication code|two[- ]factor|2fa/i.test(rawLabel || '')) {
      out.otp = true;
    }
    if (type === 'radio' || (type === 'checkbox' && el.name && document.querySelectorAll('input[type=checkbox][name="' + CSS.escape(el.name) + '"]').length > 1)) {
      if (!el.name) continue;
      const g = groups.get(el.name) || { key: el.name, control: type === 'radio' ? 'radio' : 'checkboxes', label: groupLabelOf(el),
        required: false, hasValue: false, options: [], selector: null, index: 0 };
      g.options.push({ label: stripMark(rawLabel || el.value || ''), ...address(el) });
      g.required = g.required || required(el, rawLabel);
      g.hasValue = g.hasValue || el.checked;
      groups.set(el.name, g);
      continue;
    }
    const key = el.id || el.name;
    if (!key) continue;
    let control, options = [];
    if (el.tagName === 'SELECT') {
      control = el.multiple ? 'multiselect' : 'select';
      options = Array.from(el.options).filter(o => o.value !== '' && clean(o.text)).map(o => ({ label: clean(o.text) }));
    } else if (el.tagName === 'TEXTAREA') {
      control = 'textarea';
    } else if (el.getAttribute('role') === 'combobox' || el.getAttribute('aria-autocomplete')) {
      control = 'combobox';
    } else if (type === 'checkbox') {
      control = 'checkbox';
    } else if (type === 'file') {
      control = 'file';
    } else {
      control = ['number', 'date', 'email', 'tel', 'url'].includes(type) ? type : 'text';
    }
    const hasValue = control === 'checkbox' ? el.checked : control === 'file' ? el.files.length > 0
      : control === 'multiselect' ? el.selectedOptions.length > 0 : (el.value || '') !== '';
    out.fields.push({ key, control, label: stripMark(rawLabel || key), required: required(el, rawLabel), hasValue, options, ...address(el) });
  }
  for (const g of groups.values()) {
    out.fields.push({ ...g, label: stripMark(g.label || g.key) });
  }
  out.captcha = !!document.querySelector('.g-recaptcha, .h-captcha, .cf-turnstile, [data-sitekey], '
    + 'iframe[src*="recaptcha"], iframe[src*="hcaptcha"], iframe[src*="turnstile"], iframe[src*="challenges.cloudflare.com"]');
  out.challenge = /just a moment|attention required|verify you are human|are you a robot/i.test(document.title || '')
    || !!document.querySelector('#challenge-form, #cf-challenge-running, .cf-browser-verification');
  return out;
}
