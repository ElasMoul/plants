/* ==========================================================================
   RHIZOME — system.js                        PlantPal · atlas-class · v1.0.0
   --------------------------------------------------------------------------
   The runtime the law needs. Where a page describes a mechanism, THIS file
   performs it — a page that asserts a behaviour the assets cannot do is the
   failure mode this system exists to prevent.

   Carried here:
     · the edge list and its adjacency          (single source of truth, L7)
     · placement from cells                     (deterministic, L10 / C7)
     · rank by breadth-first distance           (L4 / C3)
     · route resolution + travel by arc length  (L8 / C10, C11)
     · one navigation timing, one code path     (L8)
     · fit-the-focused-card against live scale  (L6 / C17)
     · the camera scale published to CSS        (--rz-cam-k bridge)
     · world-view extent and viewport rect      (C5)
     · pan + drag with pointer precedence       (L9)
     · neighbour-by-direction, keyboard         (L17 / C18)
     · palette + theme switching, live          (L13)
     · document state attributes                (L15)
     · the deterministic particle field         (L11)

   Everything is optional: on a documentation page with no .rz-world, mount()
   wires only what is present. Exposed as window.RZ.
   ========================================================================== */

(function (global) {
  "use strict";

  /* ---- PARAMETERS. Mirror of constitution.json → parameters. ----------- */
  var PARAMS = {
    density: { threshold: 4, shown: 2, aggregate_label: "+N more" },
    nav_ms: 300,
    lattice: { pitch_x: 300, pitch_y: 180, origin_x: 200, origin_y: 80, cols: 12, rows: 11 },
    rank_count: 4,
    rank_names: ["focus", "near", "far", "fringe"],
    zoom: { min: 0.28, max: 1.9, step: 1.12 },
    slow_threshold_ms: 10000,
    card_air_px: 80,
    card_reach: 0.96,
    shell_transform: "scale(0.42) translateX(-25%)",
    particles: { area_per_mote: 11000, ceiling: 190, drift: 0.14, link_distance_sq: 15000, seed: 20260730 }
  };

  var $  = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var num = function (v) { return parseFloat(String(v).replace(/[a-z%]/gi, "")); };

  var RZ = {
    PARAMS: PARAMS,
    EDGES: [],
    nodes: {},
    adj: {},
    focus: null,
    path: [],
    travelling: false,
    arrange: false
  };

  /* ======================================================================
     0. PARAMETER AGREEMENT. constitution.json is the record; tokens.css and
        this file are checked copies. Returns [] when they agree.
     ====================================================================== */
  RZ.verifyParameters = function () {
    var cs = getComputedStyle(document.documentElement);
    var pairs = [
      ["--rz-param-density-threshold", PARAMS.density.threshold],
      ["--rz-param-density-shown", PARAMS.density.shown],
      ["--rz-param-nav-ms", PARAMS.nav_ms],
      ["--rz-param-pitch-x", PARAMS.lattice.pitch_x],
      ["--rz-param-pitch-y", PARAMS.lattice.pitch_y],
      ["--rz-param-origin-x", PARAMS.lattice.origin_x],
      ["--rz-param-origin-y", PARAMS.lattice.origin_y],
      ["--rz-param-cols", PARAMS.lattice.cols],
      ["--rz-param-rows", PARAMS.lattice.rows],
      ["--rz-param-rank-count", PARAMS.rank_count],
      ["--rz-param-zoom-min", PARAMS.zoom.min],
      ["--rz-param-zoom-max", PARAMS.zoom.max],
      ["--rz-param-zoom-step", PARAMS.zoom.step],
      ["--rz-param-slow-threshold-ms", PARAMS.slow_threshold_ms],
      ["--rz-param-card-air", PARAMS.card_air_px],
      ["--rz-param-card-reach", PARAMS.card_reach]
    ];
    var bad = [];
    pairs.forEach(function (p) {
      var css = num(cs.getPropertyValue(p[0]));
      if (isNaN(css) || Math.abs(css - p[1]) > 0.0001) bad.push({ token: p[0], css: css, js: p[1] });
    });
    var shell = cs.getPropertyValue("--rz-param-shell-transform").trim();
    if (shell && shell !== PARAMS.shell_transform) bad.push({ token: "--rz-param-shell-transform", css: shell, js: PARAMS.shell_transform });
    return bad;
  };

  /* ======================================================================
     1. THE GRAPH. Declared once; the drawn veins, the keyboard directions,
        the Navigate-to entries and every degree count come from it (L7).
     ====================================================================== */
  RZ.setEdges = function (edges) {
    RZ.EDGES = edges.slice();
    RZ.adj = {};
    Object.keys(RZ.nodes).forEach(function (id) { RZ.adj[id] = []; });
    RZ.EDGES.forEach(function (e) {
      if (!RZ.adj[e[0]] || !RZ.adj[e[1]]) return;   /* never a dangling edge */
      RZ.adj[e[0]].push(e[1]);
      RZ.adj[e[1]].push(e[0]);
    });
  };
  RZ.degree = function (id) { return (RZ.adj[id] || []).length; };

  /* ======================================================================
     2. PLACEMENT. px = origin + cell x pitch + persisted offset (C7).
        Read once from data-cell; never from DOM order, never from a
        simulation.
     ====================================================================== */
  RZ.readNodes = function (root) {
    RZ.nodes = {};
    $$(".rz-n", root).forEach(function (el) {
      var cell = (el.dataset.cell || "0,0").split(",").map(Number);
      var rec = {
        el: el, id: el.id, col: cell[0], row: cell[1],
        dx: parseFloat(el.dataset.dx || 0), dy: parseFloat(el.dataset.dy || 0),
        name: el.dataset.name || el.id,
        kind: el.dataset.kind || "",
        kindKey: el.dataset.kindKey || "",
        recap: el.dataset.recap || "",
        unknown: el.dataset.state === "unknown",
        mode: "auto"
      };
      rec.x = PARAMS.lattice.origin_x + rec.col * PARAMS.lattice.pitch_x;
      rec.y = PARAMS.lattice.origin_y + rec.row * PARAMS.lattice.pitch_y;
      RZ.nodes[rec.id] = rec;
      RZ.place(rec);
    });
    return RZ.nodes;
  };
  RZ.place = function (rec) {
    rec.el.style.left = (rec.x + rec.dx) + "px";
    rec.el.style.top  = (rec.y + rec.dy) + "px";
  };
  /* The insertion rule's free-cell search (C8): a new node never displaces. */
  RZ.freeCell = function (col) {
    var taken = {};
    Object.keys(RZ.nodes).forEach(function (id) {
      var n = RZ.nodes[id];
      if (n.col === col) taken[n.row] = true;
    });
    for (var r = 0; r < PARAMS.lattice.rows; r++) if (!taken[r]) return [col, r];
    return RZ.freeCell(col + 1);
  };

  /* ======================================================================
     3. VEINS. Redrawn from the edge list, never appended to (L7).
     ====================================================================== */
  var SVGNS = "http://www.w3.org/2000/svg";
  var veinLayer = null, veinDots = null, trail = null;

  RZ.veinPath = function (a, b) {
    var A = RZ.nodes[a], B = RZ.nodes[b];
    var ax = A.x + A.dx, ay = A.y + A.dy, bx = B.x + B.dx, by = B.y + B.dy;
    return { d: "M " + ax + " " + ay + " L " + bx + " " + by,
             mid: { x: (ax + bx) / 2, y: (ay + by) / 2 },
             a: { x: ax, y: ay }, b: { x: bx, y: by } };
  };
  RZ.drawVeins = function () {
    if (!veinLayer) return;
    veinLayer.textContent = "";
    if (veinDots) veinDots.textContent = "";
    RZ.EDGES.forEach(function (e) {
      if (!RZ.nodes[e[0]] || !RZ.nodes[e[1]]) return;
      var p = RZ.veinPath(e[0], e[1]);
      var path = document.createElementNS(SVGNS, "path");
      path.setAttribute("class", "rz-vein");
      path.setAttribute("d", p.d);
      if (RZ.nodes[e[0]].unknown || RZ.nodes[e[1]].unknown) path.setAttribute("data-unknown", "true");
      path.dataset.a = e[0]; path.dataset.b = e[1];
      veinLayer.appendChild(path);
      if (veinDots) {
        var dot = document.createElementNS(SVGNS, "circle");
        dot.setAttribute("class", "rz-vein-node");
        dot.setAttribute("cx", p.mid.x); dot.setAttribute("cy", p.mid.y); dot.setAttribute("r", 3);
        dot.dataset.a = e[0]; dot.dataset.b = e[1];
        veinDots.appendChild(dot);
      }
    });
  };
  RZ.markLiveVeins = function (id) {
    $$(".rz-vein, .rz-vein-node").forEach(function (p) {
      if (p.dataset.a === id || p.dataset.b === id) p.setAttribute("data-live", "true");
      else p.removeAttribute("data-live");
    });
  };

  /* ======================================================================
     4. CAMERA. One plane, translated and scaled. Nothing is routed to (C4).
        The live scale is published to CSS so a card can compensate for zoom
        in pure CSS — the bridge that makes "never clipped" true at any zoom.
     ====================================================================== */
  var plane = null, world = null;
  RZ.cam = { x: 0, y: 0, k: 1 };

  RZ.applyCam = function () {
    if (!plane) return;
    plane.style.transform = "translate(" + RZ.cam.x + "px," + RZ.cam.y + "px) scale(" + RZ.cam.k + ")";
    document.documentElement.style.setProperty("--rz-cam-k", String(RZ.cam.k));
    RZ.drawViewport();
  };
  RZ.centre = function (rec) { return { x: rec.x + rec.dx, y: rec.y + rec.dy }; };
  RZ.camFor = function (rec, k) {
    var c = RZ.centre(rec);
    return { x: world.clientWidth * 0.46 - c.x * k, y: world.clientHeight * 0.52 - c.y * k, k: k };
  };
  RZ.reduced = function () {
    return document.documentElement.dataset.motion === "reduced" ||
           window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  };
  RZ.clampZoom = function (k) { return Math.min(PARAMS.zoom.max, Math.max(PARAMS.zoom.min, k)); };

  /* ---- 4b. TRAVEL. ONE timing (PARAMS.nav_ms) for every hop from every
        trigger, and the camera centre follows the polyline of the REAL edges
        by arc length (C10, C11). Passed-through nodes light up; only the
        destination opens. */
  RZ.travel = function (chain, then) {
    var to = RZ.nodes[chain[chain.length - 1]];
    var target = RZ.camFor(to, 1);
    var pts = chain.map(function (id) {
      var n = RZ.nodes[id]; return { x: n.x + n.dx, y: n.y + n.dy, id: id };
    });

    if (chain.length < 2 || RZ.reduced()) {
      /* C13 — direction still shown: the trail is drawn along the real edges
         and HELD; nothing is replaced by an abrupt swap. */
      if (chain.length > 1 && trail) {
        trail.setAttribute("d", RZ.chainD(chain));
        trail.setAttribute("data-on", "true");
        setTimeout(function () { trail.removeAttribute("data-on"); }, 900);
      }
      RZ.cam.x = target.x; RZ.cam.y = target.y; RZ.cam.k = target.k;
      RZ.applyCam();
      if (then) then();
      return;
    }

    var seg = [], cum = [0], i;
    for (i = 1; i < pts.length; i++) {
      var dx = pts[i].x - pts[i - 1].x, dy = pts[i].y - pts[i - 1].y;
      var l = Math.sqrt(dx * dx + dy * dy) || 1;
      seg.push(l); cum.push(cum[i - 1] + l);
    }
    var total = cum[cum.length - 1];
    if (trail) { trail.setAttribute("d", RZ.chainD(chain)); trail.setAttribute("data-on", "true"); }
    var k0 = RZ.cam.k, announced = 1, t0 = performance.now();

    function frame(now) {
      var t = Math.min(1, (now - t0) / PARAMS.nav_ms);
      var e = 1 - Math.pow(1 - t, 3);
      var want = e * total;
      var j = 1;
      while (j < cum.length - 1 && cum[j] < want) j++;
      var f = Math.min(1, Math.max(0, (want - cum[j - 1]) / (seg[j - 1] || 1)));
      var px = pts[j - 1].x + (pts[j].x - pts[j - 1].x) * f;
      var py = pts[j - 1].y + (pts[j].y - pts[j - 1].y) * f;
      while (announced < j) {
        if (pts[announced].id !== chain[chain.length - 1]) RZ.markPassing(RZ.nodes[pts[announced].id]);
        announced++;
      }
      var k = k0 + (target.k - k0) * e;
      RZ.cam.k = k;
      RZ.cam.x = world.clientWidth * 0.46 - px * k;
      RZ.cam.y = world.clientHeight * 0.52 - py * k;
      RZ.applyCam();
      if (t < 1) requestAnimationFrame(frame);
      else {
        RZ.cam.x = target.x; RZ.cam.y = target.y; RZ.cam.k = target.k;
        RZ.applyCam();
        if (trail) trail.removeAttribute("data-on");
        if (then) then();
      }
    }
    requestAnimationFrame(frame);
  };
  RZ.chainD = function (chain) {
    var d = "";
    for (var i = 1; i < chain.length; i++) {
      var p = RZ.veinPath(chain[i - 1], chain[i]);
      d += (i === 1 ? "M " + p.a.x + " " + p.a.y + " " : "") + "L " + p.b.x + " " + p.b.y + " ";
    }
    return d.trim();
  };
  RZ.markPassing = function (rec) {
    rec.el.dataset.passing = "true";
    setTimeout(function () { rec.el.removeAttribute("data-passing"); }, PARAMS.nav_ms + 260);
  };

  /* ======================================================================
     5. RANK. Breadth-first distance from the focus, recomputed every hop.
        Never authored (L4 / C3).
     ====================================================================== */
  RZ.ranks = function (focusId) {
    var dist = {}; dist[focusId] = 0;
    var q = [focusId];
    while (q.length) {
      var cur = q.shift();
      (RZ.adj[cur] || []).forEach(function (n) {
        if (dist[n] === undefined) { dist[n] = dist[cur] + 1; q.push(n); }
      });
    }
    return dist;
  };
  RZ.rankName = function (d) {
    return d === 0 ? "focus" : d === 1 ? "near" : d === 2 ? "far" : "fringe";
  };
  RZ.present = function (focusId, expandingId) {
    var dist = RZ.ranks(focusId);
    Object.keys(RZ.nodes).forEach(function (id) {
      var rec = RZ.nodes[id], el = rec.el;
      var d = dist[id] === undefined ? 9 : dist[id];
      var isFocus = id === focusId;
      el.dataset.rank = isFocus ? "focus" : RZ.rankName(d);
      el.dataset.focus = isFocus ? "true" : "false";
      var show;
      if (rec.mode === "min") show = "recap";
      else if (rec.mode === "full") show = "full";
      else show = isFocus ? "full" : "recap";
      if (id === expandingId) show = "skel";
      if (RZ.arrange) show = "full";
      el.dataset.show = show;
      el.setAttribute("aria-current", isFocus ? "true" : "false");
      el.setAttribute("role", "group");
      el.tabIndex = -1;
      /* The label speaks the domain; no cell, row or lattice coordinate is
         ever spoken to a user. */
      el.setAttribute("aria-label",
        rec.kind + " · " + rec.name + " · " + rec.recap + " · " +
        RZ.degree(id) + (RZ.degree(id) === 1 ? " vein out" : " veins out") +
        (isFocus ? " · you are here" : ""));
    });
    RZ.markLiveVeins(focusId);
  };

  /* ======================================================================
     6. ROUTE + GO. One code path for every hop, from every trigger (L8).
        A hop requested mid-travel is REFUSED, never queued or blended (C16).
     ====================================================================== */
  RZ.route = function (a, b) {
    var prev = {}; prev[a] = null;
    var q = [a];
    while (q.length) {
      var c = q.shift();
      if (c === b) break;
      (RZ.adj[c] || []).forEach(function (n) { if (prev[n] === undefined) { prev[n] = c; q.push(n); } });
    }
    if (prev[b] === undefined) return [];        /* nothing is ever faked */
    var out = [], c2 = b;
    while (c2 !== null) { out.unshift(c2); c2 = prev[c2]; }
    return out;
  };

  RZ.go = function (toId) {
    if (RZ.arrange) return;                       /* L16 — a mode is a mode */
    if (toId === RZ.focus) return;
    if (RZ.travelling) { RZ.say("Still travelling. The hop in flight finishes first."); return; }
    var chain = RZ.route(RZ.focus, toId);
    if (chain.length < 2) return;
    var fromId = RZ.focus;
    var via = chain.slice(1, -1);

    RZ.focus = toId;
    var at = RZ.path.indexOf(toId);
    if (at >= 0) RZ.path = RZ.path.slice(0, at + 1);
    else RZ.path = RZ.path.concat(chain.slice(1));

    RZ.present(RZ.focus, RZ.focus);               /* destination shows skeleton */
    RZ.redrawChrome();
    RZ.say("Travelled " + RZ.nodes[fromId].name + " to " + RZ.nodes[toId].name +
      (via.length ? ", through " + via.map(function (v) { return RZ.nodes[v].name; }).join(", ") +
        " — none of them opened" : "") +
      ". " + RZ.degree(toId) + " veins out from here.");

    RZ.travelling = true;
    RZ.travel(chain, function () {
      RZ.travelling = false;
      RZ.present(RZ.focus, null);                 /* real content, now expanded */
      RZ.fitFocus();
      RZ.nodes[RZ.focus].el.focus({ preventScroll: true });
    });
  };

  /* The focused card is shown in full at any zoom: if it does not fit, the
     ZOOM gives way, not the card (L6). */
  RZ.fitFocus = function () {
    if (!world || !RZ.focus) return;
    var el = RZ.nodes[RZ.focus].el;
    var h = el.offsetHeight;
    var room = world.clientHeight * PARAMS.card_reach - PARAMS.card_air_px;
    if (h * RZ.cam.k > room) {
      var k = RZ.clampZoom(room / h);
      var c = RZ.centre(RZ.nodes[RZ.focus]);
      RZ.cam.k = k;
      RZ.cam.x = world.clientWidth * 0.46 - c.x * k;
      RZ.cam.y = world.clientHeight * 0.52 - c.y * k;
      RZ.applyCam();
    }
  };

  /* ======================================================================
     7. CAMERA CONTROLS — a third category: they change neither data nor
        focus. Looking around is not navigating (C16).
     ====================================================================== */
  RZ.zoomBy = function (f) {
    var k = RZ.clampZoom(RZ.cam.k * f);
    var cx = world.clientWidth / 2, cy = world.clientHeight / 2;
    RZ.cam.x = cx - (cx - RZ.cam.x) * (k / RZ.cam.k);
    RZ.cam.y = cy - (cy - RZ.cam.y) * (k / RZ.cam.k);
    RZ.cam.k = k; RZ.applyCam();
  };
  RZ.recentre = function () {
    var t = RZ.camFor(RZ.nodes[RZ.focus], 1);
    RZ.cam.x = t.x; RZ.cam.y = t.y; RZ.cam.k = t.k; RZ.applyCam();
    RZ.say("Recentred on " + RZ.nodes[RZ.focus].name + ". You did not move.");
  };
  RZ.fitAll = function () {
    var ex = RZ.extent();
    var k = RZ.clampZoom(Math.min(world.clientWidth / ex.w, world.clientHeight / ex.h));
    RZ.cam.k = k;
    RZ.cam.x = (world.clientWidth - ex.w * k) / 2 - ex.x0 * k;
    RZ.cam.y = (world.clientHeight - ex.h * k) / 2 - ex.y0 * k;
    RZ.applyCam();
  };

  /* ======================================================================
     8. WORLD VIEW. Its extent is the union of the lattice and every node's
        LIVE position, drag offset included, and it deliberately IGNORES the
        camera so panning never rescales the map under the user (L16 / C5).
     ====================================================================== */
  var MAP_W = 208, MAP_H = 104;
  RZ.extent = function () {
    var x0 = 0, y0 = 0;
    var x1 = PARAMS.lattice.origin_x + PARAMS.lattice.cols * PARAMS.lattice.pitch_x;
    var y1 = PARAMS.lattice.origin_y + PARAMS.lattice.rows * PARAMS.lattice.pitch_y;
    Object.keys(RZ.nodes).forEach(function (id) {
      var r = RZ.nodes[id], x = r.x + r.dx, y = r.y + r.dy;
      if (x < x0) x0 = x; if (x > x1) x1 = x;
      if (y < y0) y0 = y; if (y > y1) y1 = y;
    });
    var pad = 160;
    x0 -= pad; y0 -= pad; x1 += pad; y1 += pad;
    var w = x1 - x0, h = y1 - y0;
    var s = Math.min(MAP_W / w, MAP_H / h);
    return { x0: x0, y0: y0, w: w, h: h, s: s, ox: (MAP_W - w * s) / 2, oy: (MAP_H - h * s) / 2 };
  };
  RZ.mapPt = function (ex, x, y) { return { x: ex.ox + (x - ex.x0) * ex.s, y: ex.oy + (y - ex.y0) * ex.s }; };

  RZ.drawMap = function () {
    var map = $("#rz-map"); if (!map) return;
    var es = $("#rz-map-edges", map), ds = $("#rz-map-dots", map);
    if (es) es.textContent = ""; if (ds) ds.textContent = "";
    var ex = RZ.extent();
    if (es) RZ.EDGES.forEach(function (e) {
      if (!RZ.nodes[e[0]] || !RZ.nodes[e[1]]) return;
      var A = RZ.nodes[e[0]], B = RZ.nodes[e[1]];
      var p = RZ.mapPt(ex, A.x + A.dx, A.y + A.dy), q = RZ.mapPt(ex, B.x + B.dx, B.y + B.dy);
      var l = document.createElementNS(SVGNS, "line");
      l.setAttribute("class", "rz-map__edge");
      l.setAttribute("x1", p.x); l.setAttribute("y1", p.y);
      l.setAttribute("x2", q.x); l.setAttribute("y2", q.y);
      es.appendChild(l);
    });
    var dist = RZ.ranks(RZ.focus);
    if (ds) Object.keys(RZ.nodes).forEach(function (id) {
      var r = RZ.nodes[id];
      var p = RZ.mapPt(ex, r.x + r.dx, r.y + r.dy);
      var c = document.createElementNS(SVGNS, "rect");
      c.setAttribute("class", "rz-map__dot");
      if (dist[id] === 1) c.setAttribute("data-rank", "near");
      if (r.dx || r.dy) c.setAttribute("data-moved", "true");
      c.setAttribute("x", p.x - 2); c.setAttribute("y", p.y - 2);
      c.setAttribute("width", 4); c.setAttribute("height", 4); c.setAttribute("rx", 1);
      ds.appendChild(c);
    });
    var f = RZ.nodes[RZ.focus];
    var fp = RZ.mapPt(ex, f.x + f.dx, f.y + f.dy);
    ["#rz-map-you", "#rz-map-you-halo"].forEach(function (sel) {
      var el = $(sel, map);
      if (el) { el.setAttribute("cx", fp.x); el.setAttribute("cy", fp.y); }
    });
    /* C5 / L17 — rewritten on every hop, in lockstep with the visible
       you-are-here, and it never speaks a lattice coordinate. */
    map.setAttribute("aria-label",
      "World view. You are at " + f.name + ", " + (f.kind || "").toLowerCase() + ". " +
      RZ.degree(RZ.focus) + " veins out. Path: " +
      RZ.path.map(function (p) { return RZ.nodes[p].name; }).join(", then "));
    RZ.drawViewport();
  };
  RZ.drawViewport = function () {
    var r = $("#rz-map-view"); if (!r || !world) return;
    var ex = RZ.extent();
    var vw = world.clientWidth / RZ.cam.k, vh = world.clientHeight / RZ.cam.k;
    var p = RZ.mapPt(ex, -RZ.cam.x / RZ.cam.k, -RZ.cam.y / RZ.cam.k);
    r.setAttribute("x", p.x); r.setAttribute("y", p.y);
    r.setAttribute("width", Math.max(6, vw * ex.s));
    r.setAttribute("height", Math.max(4, vh * ex.s));
  };

  /* ======================================================================
     9. THE LIVE REGION.
     ====================================================================== */
  var sayTimer = null;
  RZ.say = function (msg) {
    var l = $("#rz-live"); if (!l) return;
    l.textContent = msg;
    l.setAttribute("data-on", "true");
    clearTimeout(sayTimer);
    sayTimer = setTimeout(function () { l.removeAttribute("data-on"); }, RZ.reduced() ? 2800 : 1900);
  };

  /* ======================================================================
     10. CHROME REDRAW — you-are-here, Navigate to, Actions, world view.
         Navigate to lists exactly adj[focus], so it cannot show a stale or
         invented neighbour (C3).
     ====================================================================== */
  RZ.ACTIONS = {};
  RZ.redrawChrome = function () {
    var here = $("#rz-here");
    if (here && RZ.focus) {
      here.textContent = "";
      RZ.path.forEach(function (id, i) {
        if (i) {
          var s = document.createElement("span");
          s.className = "rz-chrome__here-sep"; s.textContent = "›";
          here.appendChild(s);
        }
        var b = document.createElement("button");
        b.type = "button";
        b.className = "rz-chrome__item";
        b.dataset.means = "travel";
        b.textContent = RZ.nodes[id].name;
        if (id === RZ.focus) b.setAttribute("aria-current", "true");
        b.addEventListener("click", function () { RZ.go(id); });
        here.appendChild(b);
      });
    }

    var nav = $("#rz-navto-body");
    if (nav && RZ.focus) {
      nav.textContent = "";
      var list = (RZ.adj[RZ.focus] || []).slice();
      var deg = $("#rz-navto-degree");
      if (deg) deg.textContent = list.length + (list.length === 1 ? " vein" : " veins");
      list.forEach(function (id) {
        var b = document.createElement("button");
        b.type = "button";
        b.className = "rz-chrome__item";
        b.dataset.means = "travel";
        b.appendChild(document.createTextNode(RZ.nodes[id].name));
        var s = document.createElement("small");
        s.textContent = RZ.nodes[id].unknown ? "not fetched yet" : RZ.nodes[id].recap;
        b.appendChild(s);
        b.addEventListener("click", function () { RZ.go(id); });
        nav.appendChild(b);
      });
    }

    var act = $("#rz-actions-body");
    if (act && RZ.focus) {
      act.textContent = "";
      var scope = $("#rz-actions-scope");
      if (scope) scope.textContent = RZ.nodes[RZ.focus].name;
      (RZ.ACTIONS[RZ.focus] || []).forEach(function (a) {
        var b = document.createElement("button");
        b.type = "button";
        b.className = "rz-chrome__item";
        b.dataset.means = "mutate";
        b.textContent = a;
        b.addEventListener("click", function () {
          if (document.documentElement.dataset.net === "offline") {
            b.dataset.refused = "queued";
            RZ.say("Offline: “" + a + "” is queued. It will run when you are back.");
          } else {
            RZ.say("“" + a + "” recorded on " + RZ.nodes[RZ.focus].name + ". The camera did not move.");
          }
        });
        act.appendChild(b);
      });
    }
    RZ.drawMap();
  };

  /* ======================================================================
     11. POINTER PRECEDENCE (L9).
         1. the pan handler returns immediately if the pointer went down
            inside a card;
         2. the card's travel listener stands aside for the card's own
            controls, in-card hops, stakes and links;
         3. in Arrange mode the card IS the handle and everything else is
            inert.
     ====================================================================== */
  var STAND_ASIDE = "[data-rz-goto], .rz-stake, .rz-chip, button, a, input, textarea, select";

  RZ.wireNode = function (rec) {
    rec.el.addEventListener("click", function (ev) {
      if (RZ.arrange) return;
      if (rec.id === RZ.focus) return;
      if (ev.target.closest(STAND_ASIDE)) return;
      RZ.go(rec.id);
    });
    rec.el.addEventListener("pointerdown", function (ev) {
      if (!RZ.arrange) return;
      RZ.beginDrag(rec, rec.el, ev);
    });
  };

  RZ.beginDrag = function (rec, handle, ev) {
    ev.stopPropagation(); ev.preventDefault();
    handle.setPointerCapture(ev.pointerId);
    rec.el.dataset.dragging = "true";
    var sx = ev.clientX, sy = ev.clientY, ox = rec.dx, oy = rec.dy;
    function move(e) {
      rec.dx = ox + (e.clientX - sx) / RZ.cam.k;
      rec.dy = oy + (e.clientY - sy) / RZ.cam.k;
      RZ.place(rec);
      RZ.drawVeins(); RZ.markLiveVeins(RZ.focus); RZ.drawMap();
    }
    function up() {
      rec.el.removeAttribute("data-dragging");
      rec.el.dataset.dx = rec.dx; rec.el.dataset.dy = rec.dy;   /* persisted */
      handle.removeEventListener("pointermove", move);
      handle.removeEventListener("pointerup", up);
      handle.removeEventListener("pointercancel", up);
      /* Redrawn once from the single source of truth: nothing is appended on
         release, so dragging cannot duplicate an edge. */
      RZ.drawVeins(); RZ.markLiveVeins(RZ.focus); RZ.drawMap();
      RZ.say(rec.name + " moved. Its position is kept.");
    }
    handle.addEventListener("pointermove", move);
    handle.addEventListener("pointerup", up);
    handle.addEventListener("pointercancel", up);
  };

  RZ.setArrange = function (on) {
    if (on === RZ.arrange) return;
    RZ.arrange = on;
    var btn = $("#rz-arrange");
    if (btn) btn.setAttribute("aria-pressed", String(on));
    if (on) {
      RZ.setArrange.restore = { x: RZ.cam.x, y: RZ.cam.y, k: RZ.cam.k };
      document.documentElement.dataset.arrange = "on";
      RZ.present(RZ.focus, null);
      RZ.fitAll();
      RZ.say("Arrange mode. Every card is open and the whole world is in view. Drag any card to place it; nothing else responds until you leave.");
    } else {
      document.documentElement.removeAttribute("data-arrange");
      RZ.present(RZ.focus, null);
      var r = RZ.setArrange.restore;
      if (r) { RZ.cam.x = r.x; RZ.cam.y = r.y; RZ.cam.k = r.k; RZ.applyCam(); }
      RZ.say("Left arrange mode. Positions kept. You are still at " + RZ.nodes[RZ.focus].name + ".");
    }
    RZ.drawVeins(); RZ.markLiveVeins(RZ.focus); RZ.drawMap();
  };

  /* ======================================================================
     12. KEYBOARD. Arrow keys pick the neighbour that actually LIES that way,
         computed from real positions — not document order (C18). A direction
         with no neighbour is spoken, not ignored.
     ====================================================================== */
  RZ.neighbourInDirection = function (dir) {
    var f = RZ.nodes[RZ.focus];
    var cands = (RZ.adj[RZ.focus] || []).map(function (id) {
      var n = RZ.nodes[id];
      return { id: id, dx: (n.x + n.dx) - (f.x + f.dx), dy: (n.y + n.dy) - (f.y + f.dy) };
    }).filter(function (c) {
      if (dir === "ArrowRight") return c.dx > 40;
      if (dir === "ArrowLeft")  return c.dx < -40;
      if (dir === "ArrowDown")  return c.dy > 40 && Math.abs(c.dx) < PARAMS.lattice.pitch_x * 1.2;
      if (dir === "ArrowUp")    return c.dy < -40 && Math.abs(c.dx) < PARAMS.lattice.pitch_x * 1.2;
      return false;
    }).sort(function (a, b) { return (a.dx * a.dx + a.dy * a.dy) - (b.dx * b.dx + b.dy * b.dy); });
    return cands.length ? cands[0].id : null;
  };

  /* ======================================================================
     13. DOCUMENT STATE ATTRIBUTES (L15). One entry point, so a reviewer can
         walk every state from the probe without editing markup.
     ====================================================================== */
  RZ.setState = function (name, value) {
    var root = document.documentElement;
    if (value === null || value === "" ) root.removeAttribute("data-" + name);
    else root.dataset[name] = value;
    if (name === "palette" || name === "theme") RZ.readFieldColours();
    return root.getAttribute("data-" + name);
  };
  RZ.setPalette = function (p) { document.documentElement.dataset.palette = p; RZ.readFieldColours(); };
  RZ.setTheme   = function (t) { document.documentElement.dataset.theme = t;   RZ.readFieldColours(); };

  /* ======================================================================
     14. THE DECORATIVE FIELD (L11). Deterministic (fixed seed), bounded by
         a ceiling, and it reads its colours BACK from the tokens, so a live
         palette change re-keys the canvas too. It belongs to whichever
         surface it decorates and moves nothing.
     ====================================================================== */
  var cv = null, ctx = null, fieldW = 0, fieldH = 0, motes = [];
  var moteColour = "rgba(240,233,217,.34)", linkColour = "rgba(240,233,217,.10)";

  RZ.readFieldColours = function () {
    var cs = getComputedStyle(document.documentElement);
    moteColour = cs.getPropertyValue("--rz-mote").trim() || moteColour;
    linkColour = cs.getPropertyValue("--rz-mote-link").trim() || linkColour;
    RZ.paintField();
  };
  RZ.sizeField = function () {
    cv = document.documentElement.dataset.mode === "overview" ? $("#rz-motes-app") : $("#rz-motes");
    if (!cv) return;
    var host = cv.parentElement;
    fieldW = host ? host.offsetWidth : window.innerWidth;
    fieldH = host ? host.offsetHeight : window.innerHeight;
    var dpr = Math.min(2, window.devicePixelRatio || 1);
    cv.width = Math.floor(fieldW * dpr);
    cv.height = Math.floor(fieldH * dpr);
    ctx = cv.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    RZ.seedField();
    RZ.paintField();
  };
  RZ.seedField = function () {
    var P = PARAMS.particles;
    var n = Math.round(Math.min(P.ceiling, (fieldW * fieldH) / P.area_per_mote));
    motes = [];
    var s = P.seed;
    var rnd = function () { s = (s * 1103515245 + 12345) & 0x7fffffff; return s / 0x7fffffff; };
    for (var i = 0; i < n; i++) {
      motes.push({ x: rnd() * fieldW, y: rnd() * fieldH,
                   vx: (rnd() - 0.5) * P.drift, vy: (rnd() - 0.5) * P.drift,
                   r: 0.7 + rnd() * 1.5 });
    }
  };
  RZ.paintField = function () {
    if (!ctx) return;
    var LD = PARAMS.particles.link_distance_sq;
    ctx.clearRect(0, 0, fieldW, fieldH);
    ctx.strokeStyle = linkColour; ctx.lineWidth = 0.6;
    for (var i = 0; i < motes.length; i++) {
      for (var j = i + 1; j < motes.length; j++) {
        var dx = motes[i].x - motes[j].x, dy = motes[i].y - motes[j].y;
        var d2 = dx * dx + dy * dy;
        if (d2 < LD) {
          ctx.globalAlpha = 1 - d2 / LD;
          ctx.beginPath();
          ctx.moveTo(motes[i].x, motes[i].y);
          ctx.lineTo(motes[j].x, motes[j].y);
          ctx.stroke();
        }
      }
    }
    ctx.globalAlpha = 1; ctx.fillStyle = moteColour;
    motes.forEach(function (m) { ctx.beginPath(); ctx.arc(m.x, m.y, m.r, 0, Math.PI * 2); ctx.fill(); });
  };
  function stepField() {
    motes.forEach(function (m) {
      m.x += m.vx; m.y += m.vy;
      if (m.x < 0) m.x += fieldW; if (m.x > fieldW) m.x -= fieldW;
      if (m.y < 0) m.y += fieldH; if (m.y > fieldH) m.y -= fieldH;
    });
    RZ.paintField();
    if (!RZ.reduced()) requestAnimationFrame(stepField);
  }

  /* ======================================================================
     15. THE SETTINGS EXIT (L12 / C20). Not a modal: the app shrinks.
     ====================================================================== */
  RZ.setMode = function (m) {
    document.documentElement.dataset.mode = m;
    RZ.sizeField();
    if (m === "overview") RZ.say("You are in overview mode. This is the entire PlantPal application. Click the app card to dive back in.");
    else RZ.say("Back in the app, still at " + (RZ.focus ? RZ.nodes[RZ.focus].name : "where you were") + ".");
  };

  /* ======================================================================
     16. MOUNT. Everything is optional: a documentation page with specimens
         but no world gets the palette, theme, state and field wiring only.
     ====================================================================== */
  RZ.mount = function (opts) {
    opts = opts || {};
    document.documentElement.classList.add("rz-root");

    /* live palette + theme + probe controls, wherever they appear */
    $$("[data-rz-palette]").forEach(function (b) {
      b.addEventListener("click", function () {
        $$("[data-rz-palette]").forEach(function (x) { x.setAttribute("aria-pressed", String(x === b)); });
        RZ.setPalette(b.dataset.rzPalette);
        RZ.say("Palette applied. Nothing was saved and nothing moved.");
      });
    });
    $$("[data-rz-state]").forEach(function (b) {
      b.addEventListener("click", function () {
        var pair = b.dataset.rzState.split(":");
        var root = document.documentElement;
        var on = root.getAttribute("data-" + pair[0]) !== pair[1];
        RZ.setState(pair[0], on ? pair[1] : (pair[2] || null));
        b.setAttribute("aria-pressed", String(on));
        if (b.dataset.rzSays) RZ.say(on ? b.dataset.rzSays : "Back to normal. Nothing moved.");
      });
    });
    $$("[data-rz-mode]").forEach(function (b) {
      b.addEventListener("click", function () { RZ.setMode(b.dataset.rzMode); });
    });

    RZ.readFieldColours();
    RZ.sizeField();
    if (!RZ.reduced() && cv) requestAnimationFrame(stepField);

    world = $(opts.world || "#rz-world");
    plane = $(opts.plane || "#rz-plane");
    if (!world || !plane) { RZ.mounted = "chrome-only"; return RZ; }

    veinLayer = $("#rz-vein-group");
    veinDots  = $("#rz-vein-dots");
    trail     = $("#rz-trail");

    RZ.readNodes(plane);
    RZ.setEdges(opts.edges || []);
    RZ.ACTIONS = opts.actions || {};
    Object.keys(RZ.nodes).forEach(function (id) { RZ.wireNode(RZ.nodes[id]); });

    RZ.focus = opts.focus || Object.keys(RZ.nodes)[0];
    RZ.path  = opts.path || [RZ.focus];

    /* Alt+wheel zooms, so a bare wheel is free to scroll a node's own body. */
    world.addEventListener("wheel", function (ev) {
      if (RZ.arrange || !ev.altKey) return;
      ev.preventDefault();
      var before = RZ.cam.k;
      var k = RZ.clampZoom(RZ.cam.k * (ev.deltaY < 0 ? PARAMS.zoom.step : 1 / PARAMS.zoom.step));
      var rect = world.getBoundingClientRect();
      var mx = ev.clientX - rect.left, my = ev.clientY - rect.top;
      RZ.cam.x = mx - (mx - RZ.cam.x) * (k / before);
      RZ.cam.y = my - (my - RZ.cam.y) * (k / before);
      RZ.cam.k = k; RZ.applyCam();
    }, { passive: false });

    /* Pan. Precedence rule 1: never begins inside a card. */
    world.addEventListener("pointerdown", function (ev) {
      if (RZ.arrange) return;
      if (ev.target.closest(".rz-n")) return;
      world.dataset.panning = "true";
      world.setPointerCapture(ev.pointerId);
      var sx = ev.clientX, sy = ev.clientY, ox = RZ.cam.x, oy = RZ.cam.y;
      function move(e) { RZ.cam.x = ox + (e.clientX - sx); RZ.cam.y = oy + (e.clientY - sy); RZ.applyCam(); }
      function up() {
        world.removeAttribute("data-panning");
        world.removeEventListener("pointermove", move);
        world.removeEventListener("pointerup", up);
      }
      world.addEventListener("pointermove", move);
      world.addEventListener("pointerup", up);
    });

    var cursor = null;
    world.addEventListener("keydown", function (ev) {
      if (RZ.arrange) {
        if (ev.key === "Escape") { ev.preventDefault(); RZ.setArrange(false); }
        return;
      }
      if (["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].indexOf(ev.key) >= 0) {
        ev.preventDefault();
        var id = RZ.neighbourInDirection(ev.key);
        if (!id) { RZ.say("No vein that way from " + RZ.nodes[RZ.focus].name + "."); return; }
        cursor = id;
        RZ.say("Vein to " + RZ.nodes[id].name + " · " + RZ.nodes[id].recap + " · press Enter to travel");
      } else if (ev.key === "Enter" || ev.key === " ") {
        if (cursor) { ev.preventDefault(); var t = cursor; cursor = null; RZ.go(t); }
      } else if (ev.key === "Tab" && !ev.shiftKey) {
        var list = RZ.adj[RZ.focus] || [];
        if (list.length) {
          ev.preventDefault();
          cursor = list[(list.indexOf(cursor) + 1) % list.length];
          RZ.say("Vein " + (list.indexOf(cursor) + 1) + " of " + list.length + " · " + RZ.nodes[cursor].name);
        }
      } else if (ev.key === "Escape") {
        if (RZ.path.length > 1) RZ.go(RZ.path[RZ.path.length - 2]);
      }
    });

    /* In-card hops and ordinary in-prose links both traverse the graph. */
    $$("[data-rz-goto]").forEach(function (b) {
      b.addEventListener("click", function (ev) {
        ev.preventDefault(); ev.stopPropagation();
        RZ.go(b.dataset.rzGoto);
      });
    });

    /* Camera cluster + arrange, if the page draws them. */
    var wire = function (sel, fn) { var el = $(sel); if (el) el.addEventListener("click", fn); };
    wire("#rz-zoom-in",  function () { RZ.zoomBy(PARAMS.zoom.step); });
    wire("#rz-zoom-out", function () { RZ.zoomBy(1 / PARAMS.zoom.step); });
    wire("#rz-recentre", RZ.recentre);
    wire("#rz-fit", function () { RZ.fitAll(); RZ.say("Whole world in view. You are still at " + RZ.nodes[RZ.focus].name + "."); });
    wire("#rz-arrange", function () { RZ.setArrange(!RZ.arrange); });

    RZ.drawVeins();
    RZ.present(RZ.focus, null);
    RZ.redrawChrome();
    var t0 = RZ.camFor(RZ.nodes[RZ.focus], 1);
    RZ.cam.x = t0.x; RZ.cam.y = t0.y; RZ.cam.k = t0.k;
    RZ.applyCam();
    RZ.fitFocus();
    RZ.drawMap();

    window.addEventListener("resize", function () {
      RZ.sizeField();
      var t = RZ.camFor(RZ.nodes[RZ.focus], RZ.cam.k);
      RZ.cam.x = t.x; RZ.cam.y = t.y; RZ.applyCam();
      RZ.fitFocus(); RZ.drawMap();
    });

    RZ.mounted = "world";
    return RZ;
  };

  global.RZ = RZ;
})(window);
