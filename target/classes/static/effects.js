/* MannVeedu — effects layer. Loaded after app.js/auth.js. Delete this file + effects.css to revert. */
(() => {
  'use strict';
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const fine = matchMedia('(hover: hover) and (pointer: fine)').matches;
  const $ = s => document.querySelector(s);
  const $$ = s => [...document.querySelectorAll(s)];
  const esc = v => (typeof escapeHtml === 'function' ? escapeHtml(v) : String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])));

  /* ---------- Scroll progress bar ---------- */
  const bar = document.createElement('div');
  bar.className = 'fx-progress';
  document.body.appendChild(bar);
  const onScroll = () => {
    const h = document.documentElement.scrollHeight - innerHeight;
    bar.style.transform = `scaleX(${h > 0 ? Math.min(1, scrollY / h) : 0})`;
  };
  addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* ---------- Hero: blobs, floating chips, 3D tilt ---------- */
  const hero = $('.hero');
  if (hero) {
    ['b1', 'b2', 'b3'].forEach(c => {
      const b = document.createElement('span');
      b.className = 'fx-blob ' + c;
      hero.appendChild(b);
    });
  }
  const art = $('.hero-art');
  if (art) {
    [['c1', 'Approved layouts'], ['c2', 'Coast to countryside'], ['c3', 'Price per cent, upfront']].forEach(([c, t]) => {
      const chip = document.createElement('span');
      chip.className = 'fx-chip ' + c;
      chip.innerHTML = `<i></i>${t}`;
      art.appendChild(chip);
    });
    if (fine && !reduce) {
      art.addEventListener('pointermove', e => {
        const r = art.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
        art.style.setProperty('--ry', (x * 12).toFixed(2) + 'deg');
        art.style.setProperty('--rx', (-y * 10).toFixed(2) + 'deg');
        art.classList.add('tilting');
      });
      art.addEventListener('pointerleave', () => {
        art.style.setProperty('--ry', '0deg');
        art.style.setProperty('--rx', '0deg');
        setTimeout(() => art.classList.remove('tilting'), 300);
      });
    }
  }

  /* ---------- Scroll reveals ---------- */
  const groups = [
    ['.search-shell'], ['.section-heading'], ['.results-panel'], ['.map-panel'],
    ['.trust-strip > div', true], ['.footer']
  ];
  const io = 'IntersectionObserver' in window ? new IntersectionObserver(entries => {
    entries.forEach(en => { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
  }, { threshold: .12, rootMargin: '0px 0px -6% 0px' }) : null;
  groups.forEach(([sel, stagger]) => $$(sel).forEach((el, i) => {
    if (!io) return;
    el.classList.add('fx-reveal');
    if (stagger) el.style.setProperty('--d', (i * .12) + 's');
    io.observe(el);
  }));
  $('.map-panel')?.style.setProperty('--d', '.15s');

  /* ---------- Listing cards: staggered entrance, tilt, marker link ---------- */
  const list = $('#listingList');
  let lastSig = '';
  if (list) {
    new MutationObserver(() => {
      const cards = $$('#listingList .listing-card');
      const sig = cards.map(c => c.dataset.id).join('|');
      if (sig === lastSig) return;          // e.g. compare-checkbox re-render: don't replay entrance
      lastSig = sig;
      cards.forEach((c, i) => { c.style.setProperty('--i', Math.min(i, 8)); c.classList.add('fx-new'); });
    }).observe(list, { childList: true });

    if (fine && !reduce) {
      list.addEventListener('pointermove', e => {
        if (e.pointerType !== 'mouse') return;
        const card = e.target.closest('.listing-card');
        if (!card) return;
        const r = card.getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
        card.style.setProperty('--ry', (x * 6).toFixed(2) + 'deg');
        card.style.setProperty('--rx', (-y * 5).toFixed(2) + 'deg');
        card.classList.add('tilting');
      });
      list.addEventListener('pointerout', e => {
        const card = e.target.closest('.listing-card');
        if (card && !card.contains(e.relatedTarget)) card.classList.remove('tilting');
      });
    }

    // Hovering a card highlights its price marker on the map.
    const setHot = title => {
      $$('.leaflet-marker-icon').forEach(m => m.classList.toggle('fx-hot', !!title && m.getAttribute('title') === title));
    };
    list.addEventListener('pointerover', e => {
      const card = e.target.closest('.listing-card');
      if (card) setHot(card.querySelector('h3')?.textContent || '');
    });
    list.addEventListener('pointerleave', () => setHot(''));
  }

  /* ---------- Map swoop: after closing a plot's detail view, fly to it ---------- */
  let viewedId = null;
  const backdrop = $('#modalBackdrop');
  if (backdrop) {
    new MutationObserver(() => {
      if (!backdrop.classList.contains('hidden') || !viewedId) return;
      const id = viewedId; viewedId = null;
      try {
        if (typeof realMap === 'undefined' || !realMap || typeof plots === 'undefined') return;
        const p = plots.find(x => x.id === id), panel = $('.map-panel');
        if (!p || !panel) return;
        const r = panel.getBoundingClientRect();
        if (r.bottom < 0 || r.top > innerHeight) return;
        realMap.flyTo([p.latitude, p.longitude], 11, { duration: reduce ? 0 : 1.5 });
      } catch {}
    }).observe(backdrop, { attributes: true, attributeFilter: ['class'] });
  }

  /* ---------- 3D plot viewer (Three.js, lazy-loaded) ---------- */
  let threePromise = null;
  const loadThree = () => threePromise || (threePromise = new Promise((res, rej) => {
    if (window.THREE) return res(window.THREE);
    const s = document.createElement('script');
    s.src = 'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js';
    s.onload = () => res(window.THREE);
    s.onerror = () => { threePromise = null; rej(new Error('three')); };
    document.head.appendChild(s);
  }));

  let viewer = null;
  const disposeViewer = () => {
    if (!viewer) return;
    cancelAnimationFrame(viewer.raf);
    removeEventListener('resize', viewer.onResize);
    try {
      viewer.scene.traverse(o => {
        o.geometry?.dispose();
        [].concat(o.material || []).forEach(m => { m.map?.dispose(); m.dispose(); });
      });
      viewer.renderer.dispose();
    } catch {}
    viewer = null;
  };

  function labelSprite(THREE, text, color = '#2f4b36') {
    const c = document.createElement('canvas'); c.width = 128; c.height = 64;
    const g = c.getContext('2d');
    g.font = '700 38px DM Sans, sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillStyle = color; g.fillText(text, 64, 34);
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(c), transparent: true, depthTest: false }));
    sp.scale.set(1.6, .8, 1); sp.renderOrder = 10;
    return sp;
  }

  function buildScene(THREE, p, canvas) {
    const W = canvas.clientWidth || 600, H = 260;
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
    renderer.setSize(W, H, false);
    renderer.shadowMap.enabled = true;
    const scene = new THREE.Scene();
    scene.fog = new THREE.Fog(0xeef1e4, 22, 40);
    const camera = new THREE.PerspectiveCamera(38, W / H, .1, 100);

    scene.add(new THREE.HemisphereLight(0xffffff, 0xb9c9a6, .85));
    const sun = new THREE.DirectionalLight(0xfff3d6, .9);
    sun.position.set(7, 12, 5); sun.castShadow = true;
    sun.shadow.mapSize.set(1024, 1024);
    Object.assign(sun.shadow.camera, { left: -14, right: 14, top: 14, bottom: -14 });
    scene.add(sun);

    // Illustrative geometry: real area, assumed 1 : 1.4 proportions.
    const area = Math.max(1, (Number(p.cents) || 1) * 435.6);
    const wFt = Math.sqrt(area * 1.4), dFt = area / wFt;
    const s = 10 / Math.max(wFt, dFt), sw = wFt * s, sd = dFt * s;
    const face = String(p.facing || '').toLowerCase();
    const side = face.includes('east') ? [1, 0] : face.includes('west') ? [-1, 0] : face.includes('south') ? [0, 1] : [0, -1];
    const roadFt = parseFloat(p.roadWidth) || 24;
    const rw = Math.max(.5, Math.min(3, roadFt * s));

    const ground = new THREE.Mesh(new THREE.CircleGeometry(15, 64), new THREE.MeshStandardMaterial({ color: 0xcfe0bd, roughness: 1 }));
    ground.rotation.x = -Math.PI / 2; ground.receiveShadow = true; scene.add(ground);

    const plotGroup = new THREE.Group(); scene.add(plotGroup);
    const slab = new THREE.Mesh(new THREE.BoxGeometry(sw, .2, sd), new THREE.MeshStandardMaterial({ color: p.type === 'Agricultural' ? 0x9bb872 : 0xe8d9a8, roughness: .95 }));
    slab.position.y = .1; slab.receiveShadow = true; slab.castShadow = true; plotGroup.add(slab);
    const edge = new THREE.LineSegments(new THREE.EdgesGeometry(slab.geometry), new THREE.LineBasicMaterial({ color: 0xc66e4f }));
    edge.position.copy(slab.position); plotGroup.add(edge);

    // Facing arrow
    const arrow = new THREE.Mesh(new THREE.ConeGeometry(.32, .95, 20), new THREE.MeshStandardMaterial({ color: 0xc66e4f }));
    arrow.position.set(0, .45, 0);
    if (side[0] === 1) arrow.rotation.z = -Math.PI / 2; else if (side[0] === -1) arrow.rotation.z = Math.PI / 2;
    else if (side[1] === -1) arrow.rotation.x = -Math.PI / 2; else arrow.rotation.x = Math.PI / 2;
    arrow.castShadow = true; plotGroup.add(arrow);

    // Road on the facing side
    const alongX = side[0] === 0;
    const roadLen = (alongX ? sw : sd) + 8;
    const road = new THREE.Mesh(new THREE.BoxGeometry(alongX ? roadLen : rw, .06, alongX ? rw : roadLen), new THREE.MeshStandardMaterial({ color: 0x5f645d, roughness: 1 }));
    const off = (alongX ? sd : sw) / 2 + rw / 2 + .15;
    road.position.set(side[0] * off, .03, side[1] * off); road.receiveShadow = true; scene.add(road);
    const line = new THREE.Mesh(new THREE.BoxGeometry(alongX ? roadLen : .07, .07, alongX ? .07 : roadLen), new THREE.MeshBasicMaterial({ color: 0xf3efe0 }));
    line.position.set(road.position.x, .045, road.position.z); scene.add(line);

    // Fence posts
    if (p.fenced) {
      const postMat = new THREE.MeshStandardMaterial({ color: 0x7a5c3e });
      const step = Math.max(.9, Math.max(sw, sd) / 12);
      const post = (x, z) => { const m = new THREE.Mesh(new THREE.BoxGeometry(.09, .5, .09), postMat); m.position.set(x, .45, z); m.castShadow = true; plotGroup.add(m); };
      for (let x = -sw / 2; x <= sw / 2 + .01; x += step) { post(x, -sd / 2); post(x, sd / 2); }
      for (let z = -sd / 2 + step; z < sd / 2 - .01; z += step) { post(-sw / 2, z); post(sw / 2, z); }
    }
    // Power pole + water patch
    if (p.electricity) {
      const pole = new THREE.Mesh(new THREE.CylinderGeometry(.06, .08, 2.2, 8), new THREE.MeshStandardMaterial({ color: 0x6b5a45 }));
      const far = off + rw / 2 + .4;
      if (side[0]) pole.position.set(side[0] * far, 1.1, sd / 2); else pole.position.set(sw / 2, 1.1, side[1] * far);
      pole.castShadow = true; scene.add(pole);
    }
    if (p.water) {
      const w = new THREE.Mesh(new THREE.CircleGeometry(.55, 28), new THREE.MeshStandardMaterial({ color: 0x6fb0d6, roughness: .2 }));
      w.rotation.x = -Math.PI / 2; w.position.set(-sw / 4, .215, -sd / 4); plotGroup.add(w);
    }

    // Trees (deterministic, away from the road)
    const trunk = new THREE.MeshStandardMaterial({ color: 0x7a5c3e }), leaf = new THREE.MeshStandardMaterial({ color: 0x4f8a55 }), leaf2 = new THREE.MeshStandardMaterial({ color: 0x6aa35c });
    const R = Math.max(sw, sd) / 2 + 3.2;
    for (let i = 0; i < 12; i++) {
      const a = i / 12 * Math.PI * 2 + .2, cx = Math.cos(a), cz = Math.sin(a);
      if (cx * side[0] + cz * side[1] > .45) continue;
      const r = R + (i % 3) * .8, g = new THREE.Group(), h = .9 + (i % 4) * .22;
      const t = new THREE.Mesh(new THREE.CylinderGeometry(.07, .1, .5, 6), trunk); t.position.y = .25;
      const c = new THREE.Mesh(new THREE.ConeGeometry(.5, h * 1.5, 8), i % 2 ? leaf : leaf2); c.position.y = .5 + h * .6;
      t.castShadow = c.castShadow = true; g.add(t, c); g.position.set(cx * r, 0, cz * r); scene.add(g);
    }

    // Compass
    const comp = new THREE.Group(); comp.position.set(-sw / 2 - 2.4, .05, sd / 2 + 2.4);
    if (Math.abs(comp.position.x) > 9) comp.position.x = -9; if (comp.position.z > 9) comp.position.z = 9;
    const ring = new THREE.Mesh(new THREE.RingGeometry(.8, .9, 40), new THREE.MeshBasicMaterial({ color: 0x2f4b36, side: THREE.DoubleSide }));
    ring.rotation.x = -Math.PI / 2; comp.add(ring);
    const north = new THREE.Mesh(new THREE.ConeGeometry(.22, .8, 4), new THREE.MeshBasicMaterial({ color: 0xc0412a }));
    north.rotation.x = -Math.PI / 2; north.position.set(0, .05, -.5); comp.add(north);
    const nl = labelSprite(THREE, 'N', '#c0412a'); nl.position.set(0, .4, -1.45); comp.add(nl);
    scene.add(comp);

    // Rise-in animation + orbit controls
    plotGroup.scale.y = .01;
    const t0 = performance.now();
    const st = { az: .6, el: .72, r: 17, auto: !reduce, drag: null };
    const pos = () => camera.position.set(
      Math.sin(st.az) * Math.cos(st.el) * st.r, Math.sin(st.el) * st.r, Math.cos(st.az) * Math.cos(st.el) * st.r);
    canvas.addEventListener('pointerdown', e => { st.drag = { x: e.clientX, y: e.clientY }; st.auto = false; canvas.setPointerCapture(e.pointerId); });
    canvas.addEventListener('pointermove', e => {
      if (!st.drag) return;
      st.az -= (e.clientX - st.drag.x) * .008;
      st.el = Math.max(.25, Math.min(1.35, st.el + (e.clientY - st.drag.y) * .006));
      st.drag = { x: e.clientX, y: e.clientY };
    });
    const end = () => { st.drag = null; };
    canvas.addEventListener('pointerup', end); canvas.addEventListener('pointercancel', end);

    const v = { scene, renderer, raf: 0, dims: { wFt, dFt }, roadFt };
    v.onResize = () => {
      const w = canvas.clientWidth; if (!w) return;
      renderer.setSize(w, H, false); camera.aspect = w / H; camera.updateProjectionMatrix();
    };
    addEventListener('resize', v.onResize);
    const loop = now => {
      const k = Math.min(1, (now - t0) / 1000), e = 1 - Math.pow(1 - k, 3);
      plotGroup.scale.y = reduce ? 1 : Math.max(.01, e);
      if (st.auto) st.az += .004;
      pos(); camera.lookAt(0, .3, 0);
      renderer.render(scene, camera);
      v.raf = requestAnimationFrame(loop);
    };
    v.raf = requestAnimationFrame(loop);
    return v;
  }

  async function mount3D(content) {
    if (content.querySelector('.fx-3d')) return;
    const anchor = content.querySelector('.detail-section-label') || content.querySelector('.owner-strip');
    if (!anchor || typeof plots === 'undefined' || typeof selectedPlot === 'undefined') return;
    const p = plots.find(x => x.id === selectedPlot);
    if (!p) return;
    viewedId = p.id;
    const box = document.createElement('div');
    box.className = 'fx-3d';
    box.innerHTML = `<div class="fx-3d-head"><span>3D plot view</span><span>Drag to rotate · illustrative layout</span></div><canvas aria-label="3D illustration of the plot, its facing direction and road"></canvas><div class="fx-3d-legend"></div>`;
    anchor.parentNode.insertBefore(box, anchor);
    try {
      const THREE = await loadThree();
      if (!box.isConnected) return;       // modal closed while loading
      disposeViewer();
      viewer = buildScene(THREE, p, box.querySelector('canvas'));
      const d = viewer.dims;
      box.querySelector('.fx-3d-legend').innerHTML =
        `<span><b>${esc(p.cents)} cents</b> ≈ ${Math.round(d.wFt)} × ${Math.round(d.dFt)} ft (assumed shape)</span><span>Facing <b>${esc(p.facing)}</b></span><span><b>${esc(p.roadWidth)}</b> road</span>${p.fenced ? '<span>Fenced</span>' : ''}${p.electricity ? '<span>Power nearby</span>' : ''}${p.water ? '<span>Water</span>' : ''}`;
    } catch {
      box.innerHTML = '<div class="fx-3d-fallback">The 3D view could not load right now. The plot details below are unaffected.</div>';
    }
  }

  const mc = $('#modalContent');
  if (mc) {
    new MutationObserver(() => {
      if (viewer && !viewer.scene) disposeViewer();
      const canvas = mc.querySelector('.fx-3d canvas');
      if (viewer && !canvas) disposeViewer();                 // modal changed or closed
      const dc = mc.querySelector('.detail-content');
      if (dc && !dc.querySelector('.fx-3d')) mount3D(dc);
    }).observe(mc, { childList: true });
  }
})();
