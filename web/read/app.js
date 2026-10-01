// Sower web reader. Same JSON the Android app ships, same palette, same red letters.
(function () {
  'use strict';

  var S1 = '\u0001', S2 = '\u0002';        // words-of-Jesus sentinels in the source text
  var TRANSLATIONS = [
    { id: 'web', name: 'World English Bible', dir: 'bible' },
    { id: 'bsb', name: 'Berean Standard Bible', dir: 'bible_bsb' }
  ];

  var view = document.getElementById('view');
  var titleEl = document.getElementById('title');
  var backBtn = document.getElementById('back');

  var state = {
    tr: localStorage.getItem('sower.tr') || 'web',
    size: +(localStorage.getItem('sower.size') || 17),
    wj: localStorage.getItem('sower.wj') || 'red',
    highlights: new Set(JSON.parse(localStorage.getItem('sower.hl') || '[]')),
    last: JSON.parse(localStorage.getItem('sower.last') || 'null')
  };

  var indexCache = {};   // dir -> book list
  var bookCache = {};    // dir/file -> book json

  function current() {
    return TRANSLATIONS.filter(function (x) { return x.id === state.tr; })[0] || TRANSLATIONS[0];
  }
  function dir() { return current().dir; }

  function save() {
    localStorage.setItem('sower.tr', state.tr);
    localStorage.setItem('sower.size', String(state.size));
    localStorage.setItem('sower.wj', state.wj);
    localStorage.setItem('sower.hl', JSON.stringify(Array.from(state.highlights)));
    if (state.last) localStorage.setItem('sower.last', JSON.stringify(state.last));
  }
  function applyPrefs() {
    document.body.className = 'wj-' + state.wj;
    view.style.fontSize = state.size + 'px';
  }

  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // Turn the sentinel markers into spans, escaping everything else.
  function verseHTML(text) {
    var out = '', open = false, buf = '';
    for (var i = 0; i < text.length; i++) {
      var c = text[i];
      if (c === S1) { out += esc(buf); buf = ''; out += '<span class="wj">'; open = true; }
      else if (c === S2) { out += esc(buf); buf = ''; out += '</span>'; open = false; }
      else buf += c;
    }
    out += esc(buf);
    if (open) out += '</span>';
    return out;
  }
  function plain(text) {
    return text.split(S1).join('').split(S2).join('');
  }

  function get(url) {
    return fetch(url).then(function (r) {
      if (!r.ok) throw new Error(url + ' ' + r.status);
      return r.json();
    });
  }
  function loadIndex() {
    var d = dir();
    if (indexCache[d]) return Promise.resolve(indexCache[d]);
    return get(d + '/index.json').then(function (j) { indexCache[d] = j; return j; });
  }
  function loadBook(file) {
    var key = dir() + '/' + file;
    if (bookCache[key]) return Promise.resolve(bookCache[key]);
    return get(key + '.json').then(function (j) { bookCache[key] = j; return j; });
  }

  function hlKey(file, ch, v) { return state.tr + ':' + file + ':' + ch + ':' + v; }
  function go(hash) { location.hash = hash; }
  window.sowerGo = go;

  // ---- views ---------------------------------------------------------------

  function renderHome() {
    titleEl.textContent = 'Sower';
    backBtn.hidden = true;
    loadIndex().then(function (books) {
      var html = '';
      if (state.last) {
        var L = state.last;
        html += '<button class="card" style="width:100%;text-align:left;font:inherit;color:inherit;cursor:pointer" ' +
          'onclick="sowerGo(\'#/r/' + L.file + '/' + L.chapter + '\')">' +
          '<div class="label">Continue reading</div>' +
          '<div class="ref">' + esc(L.name) + ' ' + L.chapter + (L.verse > 1 ? ':' + L.verse : '') + '</div>' +
          '<div class="excerpt" id="excerpt"></div></button>';
      }
      var ot = books.filter(function (b) { return !b.nt; });
      var nt = books.filter(function (b) { return b.nt; });
      html += '<h2 class="section">Old Testament</h2><div class="books">' + tiles(ot) + '</div>';
      html += '<h2 class="section">New Testament</h2><div class="books">' + tiles(nt) + '</div>';
      view.innerHTML = html;
      if (state.last) fillExcerpt();
    }).catch(fail);
  }

  function tiles(list) {
    return list.map(function (b) {
      return '<button class="book" onclick="sowerGo(\'#/b/' + b.file + '\')">' + esc(b.name) + '</button>';
    }).join('');
  }

  function fillExcerpt() {
    var L = state.last;
    loadBook(L.file).then(function (bk) {
      var verses = bk.chapters[L.chapter - 1] || [];
      var out = [], shown = 0;
      for (var v = Math.max(1, L.verse); v <= verses.length && shown < 3; v++) {
        if (!verses[v - 1]) continue;
        out.push('<span class="n">' + v + '</span>' + verseHTML(verses[v - 1]));
        shown++;
      }
      var el = document.getElementById('excerpt');
      if (el) el.innerHTML = out.join(' ');
    }).catch(function () { /* excerpt is a nicety, never fatal */ });
  }

  function renderChapters(file) {
    backBtn.hidden = false;
    loadIndex().then(function (books) {
      var b = books.filter(function (x) { return x.file === file; })[0];
      if (!b) return fail(new Error('Unknown book ' + file));
      titleEl.textContent = b.name;
      var html = '<div class="chapters">';
      for (var c = 1; c <= b.chapters; c++) {
        html += '<button class="chapter" onclick="sowerGo(\'#/r/' + file + '/' + c + '\')">' + c + '</button>';
      }
      view.innerHTML = html + '</div>';
      window.scrollTo(0, 0);
    }).catch(fail);
  }

  function renderReader(file, ch) {
    backBtn.hidden = false;
    Promise.all([loadIndex(), loadBook(file)]).then(function (res) {
      var meta = res[0].filter(function (x) { return x.file === file; })[0] || { name: file };
      var bk = res[1];
      ch = Math.min(Math.max(1, ch), bk.chapters.length);
      titleEl.textContent = meta.name + ' ' + ch;
      state.last = { file: file, name: meta.name, chapter: ch, verse: 1 };
      save();

      var verses = bk.chapters[ch - 1] || [];
      var html = '';
      for (var i = 0; i < verses.length; i++) {
        if (!verses[i]) continue;
        var v = i + 1;
        var on = state.highlights.has(hlKey(file, ch, v));
        html += '<p class="verse' + (on ? ' hl' : '') + '" data-v="' + v + '">' +
          '<span class="n">' + v + '</span>' + verseHTML(verses[i]) + '</p>';
      }
      html += '<div class="navbar">' +
        '<button ' + (ch <= 1 ? 'disabled' : '') + ' onclick="sowerGo(\'#/r/' + file + '/' + (ch - 1) + '\')">Previous chapter</button>' +
        '<span>Chapter ' + ch + ' of ' + bk.chapters.length + '</span>' +
        '<button ' + (ch >= bk.chapters.length ? 'disabled' : '') + ' onclick="sowerGo(\'#/r/' + file + '/' + (ch + 1) + '\')">Next chapter</button>' +
        '</div>';
      view.innerHTML = html;
      window.scrollTo(0, 0);
      wireHighlighting(file, ch);
    }).catch(fail);
  }

  // Press and hold a verse to highlight it, the same gesture the app uses.
  function wireHighlighting(file, ch) {
    var timer = null, moved = false;
    Array.prototype.forEach.call(view.querySelectorAll('.verse'), function (p) {
      p.addEventListener('pointerdown', function () {
        moved = false;
        timer = setTimeout(function () { if (!moved) toggle(p); }, 450);
      });
      p.addEventListener('pointerup', function () { clearTimeout(timer); });
      p.addEventListener('pointerleave', function () { clearTimeout(timer); });
      p.addEventListener('pointermove', function () { moved = true; });
      p.addEventListener('dblclick', function () { toggle(p); });
      p.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    });
    function toggle(p) {
      var key = hlKey(file, ch, +p.getAttribute('data-v'));
      if (state.highlights.has(key)) { state.highlights['delete'](key); p.classList.remove('hl'); }
      else { state.highlights.add(key); p.classList.add('hl'); }
      save();
      if (navigator.vibrate) navigator.vibrate(12);
    }
  }

  function renderSearch() {
    backBtn.hidden = false;
    titleEl.textContent = 'Search';
    view.innerHTML =
      '<div class="searchbar"><input id="q" type="search" placeholder="Search the Bible" autocomplete="off"></div>' +
      '<div class="status" id="st">Type a word or phrase, then press enter.</div><div id="res"></div>';
    var q = document.getElementById('q');
    q.focus();
    q.addEventListener('keydown', function (e) { if (e.key === 'Enter') runSearch(q.value); });
  }

  function normalize(s) {
    return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
      .replace(/[‘’]/g, "'").replace(/[“”]/g, '"');
  }

  function runSearch(term) {
    var needle = normalize(term.trim());
    var st = document.getElementById('st'), res = document.getElementById('res');
    res.innerHTML = '';
    if (needle.length < 2) { st.textContent = 'Type at least two characters.'; return; }
    loadIndex().then(function (books) {
      var hits = [], done = 0;
      st.textContent = 'Searching...';

      function finish() {
        st.textContent = hits.length
          ? (hits.length + (hits.length === 1 ? ' verse found' : ' verses found'))
          : 'No verses found.';
        res.innerHTML = hits.map(function (h) {
          var at = normalize(h.text).indexOf(needle);
          var shown = esc(h.text);
          if (at >= 0) {
            shown = esc(h.text.slice(0, at)) + '<mark>' +
              esc(h.text.slice(at, at + needle.length)) + '</mark>' +
              esc(h.text.slice(at + needle.length));
          }
          return '<button class="result" onclick="sowerGo(\'#/r/' + h.file + '/' + h.ch + '\')">' +
            '<b>' + esc(h.name) + ' ' + h.ch + ':' + h.v + '</b>' + shown + '</button>';
        }).join('');
      }

      (function next(i) {
        if (i >= books.length || hits.length >= 300) return finish();
        loadBook(books[i].file).then(function (bk) {
          bk.chapters.forEach(function (verses, ci) {
            verses.forEach(function (text, vi) {
              if (!text || hits.length >= 300) return;
              var p = plain(text);
              if (normalize(p).indexOf(needle) >= 0) {
                hits.push({ file: books[i].file, name: books[i].name, ch: ci + 1, v: vi + 1, text: p });
              }
            });
          });
          done++;
          st.textContent = 'Searching ' + Math.round(done / books.length * 100) + '%';
          next(i + 1);
        }).catch(function () { next(i + 1); });
      })(0);
    }).catch(fail);
  }

  // ---- reading options sheet ----------------------------------------------

  function openSettings() {
    var wrap = document.createElement('div');
    wrap.className = 'sheet';
    wrap.innerHTML =
      '<div>' +
      '<h3>Text size</h3>' +
      '<div class="row"><span style="font-size:0.85rem">A</span>' +
      '<input id="size" type="range" min="15" max="26" step="1" value="' + state.size + '">' +
      '<span style="font-size:1.5rem">A</span></div>' +
      '<h3>Translation</h3>' +
      TRANSLATIONS.map(function (t) {
        return '<button class="opt" role="radio" aria-checked="' + (t.id === state.tr) +
          '" data-tr="' + t.id + '">' + t.name + '</button>';
      }).join('') +
      '<h3>Words of Jesus</h3>' +
      ['red:In red', 'bold:In bold', 'none:Same as other text'].map(function (o) {
        var id = o.split(':')[0];
        return '<button class="opt" role="radio" aria-checked="' + (id === state.wj) +
          '" data-wj="' + id + '">' + o.split(':')[1] + '</button>';
      }).join('') +
      '<h3>Offline</h3>' +
      '<button class="opt" id="precache">Save the whole Bible for offline use</button>' +
      '<button class="done">Done</button>' +
      '</div>';
    document.body.appendChild(wrap);

    function close() { wrap.parentNode.removeChild(wrap); }
    wrap.addEventListener('click', function (e) { if (e.target === wrap) close(); });
    wrap.querySelector('.done').addEventListener('click', close);

    wrap.querySelector('#size').addEventListener('input', function (e) {
      state.size = +e.target.value; applyPrefs(); save();
    });
    Array.prototype.forEach.call(wrap.querySelectorAll('[data-tr]'), function (b) {
      b.addEventListener('click', function () {
        state.tr = b.getAttribute('data-tr'); save(); close(); route();
      });
    });
    Array.prototype.forEach.call(wrap.querySelectorAll('[data-wj]'), function (b) {
      b.addEventListener('click', function () {
        state.wj = b.getAttribute('data-wj'); applyPrefs(); save();
        Array.prototype.forEach.call(wrap.querySelectorAll('[data-wj]'), function (x) {
          x.setAttribute('aria-checked', String(x === b));
        });
      });
    });
    wrap.querySelector('#precache').addEventListener('click', function () {
      var btn = this;
      loadIndex().then(function (books) {
        var i = 0;
        (function step() {
          if (i >= books.length) { btn.textContent = 'Saved. The whole Bible works offline now.'; return; }
          btn.textContent = 'Saving ' + Math.round(i / books.length * 100) + '%';
          loadBook(books[i].file).then(function () { i++; step(); })
            .catch(function () { i++; step(); });
        })();
      });
    });
  }

  // ---- routing -------------------------------------------------------------

  function fail(e) {
    view.innerHTML = '<p class="empty">Could not load that. Check your connection and try again.</p>';
    if (window.console) console.error(e);
  }

  function route() {
    var h = location.hash.replace(/^#\/?/, '');
    var parts = h.split('/').filter(Boolean);
    if (parts[0] === 'b' && parts[1]) return renderChapters(parts[1]);
    if (parts[0] === 'r' && parts[1]) return renderReader(parts[1], +(parts[2] || 1));
    if (parts[0] === 'search') return renderSearch();
    return renderHome();
  }

  window.addEventListener('hashchange', route);
  backBtn.addEventListener('click', function () { history.back(); });
  document.getElementById('searchBtn').addEventListener('click', function () { go('#/search'); });
  document.getElementById('settingsBtn').addEventListener('click', openSettings);

  applyPrefs();
  route();

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(function (e) {
      if (window.console) console.warn('service worker', e);
    });
  }
})();
