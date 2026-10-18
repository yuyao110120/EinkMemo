(() => {
  'use strict';

  var PAGE_SIZE = 10;
  var STORE_KEY = 'elinkmemo.proto.v2';

  function $(sel) { return document.querySelector(sel); }
  function now() { return Date.now(); }
  function uid() { return 'i' + now().toString(36) + Math.random().toString(36).slice(2, 6); }
  function pad(n) { return String(n).padStart(2, '0'); }

  function dayKey(t) {
    var d = new Date(t);
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }
  function fmtDay(key) {
    var p = key.split('-');
    return Number(p[1]) + '月' + Number(p[2]) + '日';
  }
  function fmtTime(t) {
    var d = new Date(t);
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
  }
  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  // ---------- seed ----------
  function seedItems() {
    var t = now();
    function item(text, done, agoMin) {
      return {
        id: uid(), text: text, done: !!done,
        createdAt: t - agoMin * 60000,
        doneAt: done ? t - agoMin * 30000 : null,
        archivedAt: null,
        updatedAt: t - agoMin * 60000
      };
    }
    function arch(text, daysAgo) {
      return {
        id: uid(), text: text, done: true,
        createdAt: t - (daysAgo * 1440 + 300) * 60000,
        doneAt: t - (daysAgo * 1440 + 200) * 60000,
        archivedAt: t - daysAgo * 86400000,
        updatedAt: t - daysAgo * 86400000
      };
    }
    return [
      item('给猫铲屎', false, 600),
      item('买 7 号电池', false, 590),
      item('回复周一的邮件', true, 580),
      item('预约牙医', false, 570),
      item('整理桌面', false, 560),
      item('给妈妈打电话', false, 550),
      item('取快递', false, 540),
      item('交电费', false, 530),
      item('看完那篇论文', true, 520),
      item('买洗衣液补充装', false, 510),
      item('更新 NAS 固件', false, 500),
      item('修厨房的灯', false, 490),
      arch('退掉不用的订阅', 1),
      arch('换手机膜', 3)
    ];
  }

  // ---------- state ----------
  function freshState() {
    return {
      version: 2,
      items: seedItems(),
      sync: {
        url: 'http://192.168.1.10:5005/',
        user: 'demo',
        pass: 'demo',
        path: 'ElinkMemo/notes.json',
        lastSyncAt: null,
        lastOk: null
      },
      seenHint: false,
      view: 'main',
      page: 0,
      editing: null,
      archSel: null,
      testResult: ''
    };
  }

  function loadState() {
    try {
      var raw = localStorage.getItem(STORE_KEY);
      if (!raw) return null;
      var saved = JSON.parse(raw);
      var s = freshState();
      if (Array.isArray(saved.items)) s.items = saved.items;
      if (saved.sync) {
        Object.keys(s.sync).forEach(function (k) { if (saved.sync[k] !== undefined) s.sync[k] = saved.sync[k]; });
      }
      s.seenHint = !!saved.seenHint;
      return s;
    } catch (e) { return null; }
  }

  var state = loadState() || freshState();

  if (new URLSearchParams(location.search).get('reset') === '1') {
    try { localStorage.removeItem(STORE_KEY); } catch (e) {}
    state = freshState();
  }

  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify({
        version: state.version, items: state.items, sync: state.sync, seenHint: state.seenHint
      }));
    } catch (e) {}
  }

  // ---------- helpers ----------
  function activeItems() { return state.items.filter(function (i) { return !i.archivedAt; }); }
  function archivedItems() {
    return state.items.filter(function (i) { return i.archivedAt; })
      .sort(function (a, b) { return b.archivedAt - a.archivedAt; });
  }
  function findItem(id) {
    for (var i = 0; i < state.items.length; i++) { if (state.items[i].id === id) return state.items[i]; }
    return null;
  }
  function pageCount() { return Math.max(1, Math.ceil(activeItems().length / PAGE_SIZE)); }

  // Optical vertical centering for CJK ink: flex centers the line box, but the
  // visible glyph ink sits off-center because font ascent / descent differ.
  // line-height is 1, so nudge = lineBox/2 - inkCenterWithinLineBox.
  var measureCtx = null;
  function textVerticalNudge(fontPx) {
    try {
      if (!measureCtx) {
        measureCtx = document.createElement('canvas').getContext('2d');
      }
      var family = getComputedStyle(document.body).fontFamily;
      measureCtx.font = fontPx + 'px ' + family;
      var m = measureCtx.measureText('国');
      if (m.fontBoundingBoxAscent == null || m.actualBoundingBoxAscent == null) return 0;
      var halfLeading = (fontPx - (m.fontBoundingBoxAscent + m.fontBoundingBoxDescent)) / 2;
      var baseline = halfLeading + m.fontBoundingBoxAscent;
      var inkCenter = baseline + (m.actualBoundingBoxDescent - m.actualBoundingBoxAscent) / 2;
      return Math.round(fontPx / 2 - inkCenter);
    } catch (e) { return 0; }
  }

  // ---------- render ----------
  function render() {
    // 先让底部操作栏/输入条的显隐就位，再计算行高；
    // 否则从二级页返回时 #view 仍是"全高"，第 10 行会被底栏压住。
    updateChrome();
    if (state.view === 'main') {
      renderMain();
    } else {
      renderSecondary();
      var fail = $('#fail');
      fail.classList.remove('show');
      fail.innerHTML = '';
    }
  }

  function renderMain() {
    var items = activeItems();
    var pages = pageCount();
    if (state.page >= pages) state.page = pages - 1;
    if (state.page < 0) state.page = 0;
    var start = state.page * PAGE_SIZE;
    var slice = items.slice(start, start + PAGE_SIZE);

    var html = '<div class="rows">';
    slice.forEach(function (it) {
      html += '<div class="row' + (it.done ? ' done' : '') + '" data-row="' + it.id + '">' +
        '<span class="txt">' + esc(it.text) + '</span></div>';
    });
    html += '</div>';
    if (!state.seenHint) html += '<div id="hint">右滑划掉<br>点击编辑</div>';

    var view = $('#view');
    view.innerHTML = html;

    var fail = $('#fail');
    var showFail = state.sync.lastOk === false;
    fail.classList.toggle('show', showFail);
    fail.innerHTML = showFail ? '<span data-action="fail">同步失败 · 点此查看</span>' : '';

    var rows = view.querySelector('.rows');
    var h = view.clientHeight;
    if (rows) {
      var rowH = h / PAGE_SIZE;
      rows.style.setProperty('--row-h', rowH + 'px');
      var f = Math.min(38, Math.max(20, Math.round(rowH * 0.4)));
      rows.style.setProperty('--row-font', f + 'px');
      rows.style.setProperty('--row-nudge', textVerticalNudge(f) + 'px');
    }

    var pn = $('#pageno');
    if (pn) pn.textContent = pages > 1 ? ((state.page + 1) + '/' + pages) : '';
  }

  function renderSecondary() {
    var view = $('#view');
    if (state.view === 'settings') view.innerHTML = settingsHtml();
    else if (state.view === 'webdav') { view.innerHTML = webdavHtml(); bindFields(); }
    else if (state.view === 'archive') view.innerHTML = archiveHtml();
    else if (state.view === 'about') view.innerHTML = aboutHtml();
  }

  function settingsHtml() {
    var pending = 0;
    var archCount = 0;
    state.items.forEach(function (i) {
      if (i.done && !i.archivedAt) pending++;
      if (i.archivedAt) archCount++;
    });
    var s = state.sync;
    var last = s.lastSyncAt
      ? ('上次同步：' + fmtTime(s.lastSyncAt) + ' · ' + (s.lastOk ? '成功' : '失败'))
      : '尚未同步';
    return '<div class="navrow" data-action="back">← 返回</div>' +
      '<div class="menurow" data-action="webdav"><span>WebDAV 设置</span><span class="chev">›</span></div>' +
      '<div class="menurow tall" data-action="sync"><span>立即同步</span><span class="sub">' + esc(last) + '</span></div>' +
      '<div class="menurow tall" data-action="archive"><span>归档已完成 (' + pending + ')</span><span class="sub">把划掉的条目移入归档记录</span></div>' +
      '<div class="menurow" data-action="archives"><span>归档记录 (' + archCount + ')</span><span class="chev">›</span></div>' +
      '<div class="menurow" data-action="about"><span>关于</span><span class="chev">›</span></div>';
  }

  function webdavHtml() {
    var s = state.sync;
    return '<div class="navrow" data-action="back">← 返回</div>' +
      '<div class="field"><label>地址</label><input id="f-url" type="text" autocomplete="off" spellcheck="false" value="' + esc(s.url) + '"></div>' +
      '<div class="field"><label>用户名</label><input id="f-user" type="text" autocomplete="off" value="' + esc(s.user) + '"></div>' +
      '<div class="field"><label>密码</label><input id="f-pass" type="password" value="' + esc(s.pass) + '"></div>' +
      '<div class="field"><label>远端文件</label><input id="f-path" type="text" autocomplete="off" value="' + esc(s.path) + '"></div>' +
      '<div class="btnrow" data-action="test"><span>测试连接</span>' +
        '<span class="sub" id="testres">' + (state.testResult ? esc(state.testResult) : '未测试') + '</span></div>' +
      '<div class="btnrow" data-action="save"><span>保存</span></div>';
  }

  function bindFields() {
    var map = [['f-url', 'url'], ['f-user', 'user'], ['f-pass', 'pass'], ['f-path', 'path']];
    map.forEach(function (pair) {
      var el = document.getElementById(pair[0]);
      if (!el) return;
      el.addEventListener('input', function () { state.sync[pair[1]] = el.value; });
    });
  }

  function archiveHtml() {
    var list = archivedItems();
    var html = '<div class="navrow" data-action="back">← 返回</div>';
    if (!list.length) return html + '<div class="emptyhint">暂无归档</div>';
    var curKey = '';
    list.forEach(function (it) {
      var k = dayKey(it.archivedAt);
      if (k !== curKey) {
        curKey = k;
        html += '<div class="grouphead">' + fmtDay(k) + '</div>';
      }
      html += '<div class="row2" data-arch="' + it.id + '"><span class="txt">' + esc(it.text) + '</span></div>';
    });
    return html;
  }

  function aboutHtml() {
    return '<div class="navrow" data-action="back">← 返回</div>' +
      '<div class="about">' +
      '<div class="appname">ElinkMemo</div>' +
      '<div class="aline">版本 0.1 · 高保真交互原型</div>' +
      '<div class="aline">目标设备：文石 Boox Leaf2（7 英寸 / 300ppi / Android 11）</div>' +
      '<div class="aline">同步为模拟演示，不发送真实网络请求。</div>' +
      '<div class="aline">测试失败态：清空地址或密码后同步。</div>' +
      '<div class="aline">零动画 / 纯黑白 / 单页 10 条分页</div>' +
      '<div class="btnrow" data-action="diag">打开设备诊断页</div>' +
      '</div>';
  }

  function updateChrome() {
    var bottom = $('#bottom');
    var main = state.view === 'main';
    bottom.hidden = !main;
    if (!main) {
      $('#bar').hidden = false;
      $('#inputbar').hidden = true;
      return;
    }
    var editing = !!state.editing;
    $('#bar').hidden = editing;
    $('#inputbar').hidden = !editing;
    if (editing) {
      var inp = $('#note-input');
      if (document.activeElement !== inp) {
        inp.value = state.editing.mode === 'edit'
          ? ((findItem(state.editing.id) || {}).text || '')
          : '';
        inp.placeholder = state.editing.mode === 'edit' ? '修改…' : '添加一条…';
        inp.focus();
        inp.setSelectionRange(inp.value.length, inp.value.length);
      }
    }
  }

  // ---------- commands ----------
  function goBack() {
    if (state.view === 'settings') state.view = 'main';
    else if (state.view === 'webdav' || state.view === 'archive' || state.view === 'about') state.view = 'settings';
    else return;
    state.editing = null;
    state.testResult = '';
    render();
  }

  function startAdd() { state.editing = { mode: 'add' }; render(); }

  function startEdit(id) {
    var it = findItem(id);
    if (!it || it.archivedAt) return;
    state.editing = { mode: 'edit', id: id };
    render();
  }

  function toggleDone(id) {
    var it = findItem(id);
    if (!it || it.archivedAt) return;
    it.done = !it.done;
    it.doneAt = it.done ? now() : null;
    it.updatedAt = now();
    save();
    render();
  }

  function commitInput() {
    if (!state.editing) return;
    var inp = $('#note-input');
    var text = (inp.value || '').trim();
    if (state.editing.mode === 'edit') {
      var it = findItem(state.editing.id);
      if (it && text) {
        it.text = text;
        it.updatedAt = now();
        save();
      }
      state.editing = null;
      render();
      return;
    }
    if (!text) {
      state.editing = null;
      render();
      return;
    }
    state.items.push({
      id: uid(), text: text, done: false,
      createdAt: now(), doneAt: null, archivedAt: null, updatedAt: now()
    });
    state.page = pageCount() - 1;
    save();
    render();
    var inp2 = $('#note-input');
    if (inp2) { inp2.value = ''; inp2.focus(); }
  }

  function cancelInput() { state.editing = null; render(); }

  function syncNow() {
    var s = state.sync;
    var ok = !!(s.url && s.user && s.pass);
    s.lastSyncAt = now();
    s.lastOk = ok;
    save();
    render();
  }

  function testConnection() {
    var s = state.sync;
    var msg;
    if (!s.url) msg = '测试失败：地址为空';
    else if (!/^https?:\/\//i.test(s.url)) msg = '测试失败：地址需以 http:// 或 https:// 开头';
    else if (!s.user || !s.pass) msg = '测试失败：用户名或密码为空';
    else if (s.url.indexOf('fail') >= 0) msg = '测试失败：无法连接（模拟）';
    else msg = '测试成功（模拟）';
    state.testResult = msg;
    render();
  }

  function saveWebdav() {
    save();
    state.testResult = '';
    state.view = 'settings';
    render();
  }

  function archiveDone() {
    var ts = now();
    var n = 0;
    state.items.forEach(function (it) {
      if (it.done && !it.archivedAt) { it.archivedAt = ts; it.updatedAt = ts; n++; }
    });
    if (n) save();
    render();
  }

  function openSheet(id) {
    state.archSel = id;
    $('#sheet').hidden = false;
  }
  function hideSheet() {
    state.archSel = null;
    $('#sheet').hidden = true;
  }
  function restoreItem() {
    var it = findItem(state.archSel);
    if (it) {
      it.archivedAt = null;
      it.done = false;
      it.doneAt = null;
      it.updatedAt = now();
      state.items = state.items.filter(function (x) { return x.id !== it.id; }).concat([it]);
      save();
    }
    hideSheet();
    render();
  }
  function deleteItem() {
    state.items = state.items.filter(function (x) { return x.id !== state.archSel; });
    save();
    hideSheet();
    render();
  }

  function turnPage(delta) {
    var pages = pageCount();
    var next = Math.min(pages - 1, Math.max(0, state.page + delta));
    if (next !== state.page) { state.page = next; render(); }
  }

  // ---------- handlers ----------
  var device = $('#device');
  var screen = $('#screen');

  device.addEventListener('pointerdown', function () {
    if (!state.seenHint) {
      state.seenHint = true;
      save();
      var h = $('#hint');
      if (h) h.remove();
    }
  }, true);

  var drag = null;

  screen.addEventListener('pointerdown', function (e) {
    if (state.editing || state.archSel) return;
    var el = e.target.closest('[data-row],[data-arch]');
    if (!el) return;
    drag = { key: el.getAttribute('data-row') || el.getAttribute('data-arch'), type: el.hasAttribute('data-row') ? 'row' : 'arch', x: e.clientX, y: e.clientY };
  });

  window.addEventListener('pointerup', function (e) {
    if (!drag) return;
    var d = drag;
    drag = null;
    var dx = e.clientX - d.x;
    var dy = e.clientY - d.y;
    if (Math.abs(dx) < 8 && Math.abs(dy) < 8) {
      if (d.type === 'row') startEdit(d.key);
      else openSheet(d.key);
      return;
    }
    if (Math.abs(dx) > 36 && Math.abs(dx) > Math.abs(dy) * 1.4) {
      if (d.type === 'row') { if (dx > 0) toggleDone(d.key); else startEdit(d.key); }
      return;
    }
    if (Math.abs(dy) > 48 && Math.abs(dy) > Math.abs(dx) * 1.4) {
      if (state.view === 'main') turnPage(dy < 0 ? 1 : -1);
    }
  });

  window.addEventListener('pointercancel', function () { drag = null; });

  device.addEventListener('click', function (e) {
    var el = e.target.closest('[data-action]');
    if (!el) return;
    var a = el.getAttribute('data-action');
    if (a === 'add') startAdd();
    else if (a === 'settings') { state.editing = null; state.view = 'settings'; render(); }
    else if (a === 'back') goBack();
    else if (a === 'webdav') { state.testResult = ''; state.view = 'webdav'; render(); }
    else if (a === 'sync') syncNow();
    else if (a === 'archive') archiveDone();
    else if (a === 'archives') { state.view = 'archive'; render(); }
    else if (a === 'about') { state.view = 'about'; render(); }
    else if (a === 'diag') location.href = 'diag.html';
    else if (a === 'test') testConnection();
    else if (a === 'save') saveWebdav();
    else if (a === 'restore') restoreItem();
    else if (a === 'delete') deleteItem();
    else if (a === 'cancel') hideSheet();
    else if (a === 'cancelinput') cancelInput();
    else if (a === 'fail') { state.view = 'settings'; render(); }
  });

  document.addEventListener('keydown', function (e) {
    if (state.editing) {
      if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); commitInput(); }
      else if (e.key === 'Escape') { e.preventDefault(); cancelInput(); }
      return;
    }
    if (state.archSel) {
      if (e.key === 'Escape') { e.preventDefault(); hideSheet(); }
      return;
    }
    if (state.view === 'main') {
      if (e.key === 'PageDown' || e.key === 'ArrowDown') { e.preventDefault(); turnPage(1); }
      else if (e.key === 'PageUp' || e.key === 'ArrowUp') { e.preventDefault(); turnPage(-1); }
      return;
    }
    if (e.key === 'Escape') { e.preventDefault(); goBack(); }
  });

  // ---------- frame / resize ----------
  function applyFrame() {
    var framed = window.innerWidth >= 1000;
    document.getElementById('stage').classList.toggle('framed', framed);
    document.body.classList.toggle('framed', framed);
  }

  var resizeTimer = null;
  window.addEventListener('resize', function () {
    if (resizeTimer) cancelAnimationFrame(resizeTimer);
    resizeTimer = requestAnimationFrame(function () {
      resizeTimer = null;
      applyFrame();
      render();
    });
  });

  applyFrame();
  render();

  window.__proto = {
    state: state,
    render: render,
    toggleDone: toggleDone,
    startEdit: startEdit,
    commitInput: commitInput,
    turnPage: turnPage,
    syncNow: syncNow
  };
})();
