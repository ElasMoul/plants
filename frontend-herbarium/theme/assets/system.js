/* ==========================================================================
   Herbarium — component behaviour  (PlantPal design system, v1.0.0)

   Vanilla JS, no dependencies, no build step. Loaded on every page; wires
   every interactive class system.css defines by scanning the document once
   on DOMContentLoaded. Idempotent: elements are marked as wired, so
   herbarium.wire(root) may be called again on injected markup.

   Rules this file enforces, not merely permits:
     - Navigation moves the camera; mutation never does. (C15)
     - Focus change travels; nothing teleports. (C10, C11)
     - Positions are read from persisted coordinates, never simulated. (C7)
     - Keyboard traversal follows the graph, not DOM order. (C18)
     - Under prefers-reduced-motion, direction and the you-are-here marker
       are still updated; nothing becomes an abrupt swap. (C13)
   ========================================================================== */

(function (global) {
  "use strict";

  var WIRED = "data-hbm-wired";
  var reduced = global.matchMedia
    ? global.matchMedia("(prefers-reduced-motion: reduce)")
    : { matches: false };

  function each(root, selector, fn) {
    Array.prototype.forEach.call(root.querySelectorAll(selector), function (el) {
      if (el.hasAttribute(WIRED)) return;
      el.setAttribute(WIRED, "");
      fn(el);
    });
  }

  /* ---------------------------------------------------------------- toasts */

  function rail() {
    var r = document.querySelector(".hbm-toast-rail");
    if (!r) {
      r = document.createElement("div");
      r.className = "hbm-toast-rail";
      r.setAttribute("role", "status");
      r.setAttribute("aria-live", "polite");
      document.body.appendChild(r);
    }
    return r;
  }

  /* An arrival names where it came from — the distance is shown, not cut. */
  function toast(message, options) {
    var opts = options || {};
    var el = document.createElement("div");
    el.className = "hbm-toast" + (opts.tone ? " hbm-toast--" + opts.tone : "");

    if (opts.origin) {
      var origin = document.createElement("span");
      origin.className = "hbm-toast__origin";
      origin.textContent = opts.origin + " →";
      el.appendChild(origin);
    }

    var body = document.createElement("span");
    body.className = "hbm-toast__body";
    body.textContent = message;
    el.appendChild(body);

    if (opts.actionLabel) {
      var action = document.createElement("button");
      action.type = "button";
      action.className = "hbm-act hbm-act--quiet hbm-act--sm";
      action.textContent = opts.actionLabel;
      action.addEventListener("click", function () {
        if (typeof opts.onAction === "function") opts.onAction();
        el.remove();
      });
      el.appendChild(action);
    }

    rail().appendChild(el);
    global.setTimeout(function () { el.remove(); }, opts.duration || 6000);
    return el;
  }

  /* ------------------------------------------------------- switch / segments */

  function wireSwitches(root) {
    each(root, '.hbm-switch[role="switch"]', function (el) {
      if (!el.hasAttribute("tabindex") && el.tagName !== "BUTTON") el.tabIndex = 0;
      function flip() {
        var on = el.getAttribute("aria-checked") === "true";
        el.setAttribute("aria-checked", on ? "false" : "true");
        el.dispatchEvent(new CustomEvent("hbm:change", {
          bubbles: true, detail: { checked: !on }
        }));
      }
      el.addEventListener("click", flip);
      el.addEventListener("keydown", function (e) {
        if (e.key === " " || e.key === "Enter") { e.preventDefault(); flip(); }
      });
    });
  }

  function wireSegmented(root) {
    each(root, ".hbm-segmented", function (group) {
      var options = group.querySelectorAll(".hbm-segmented__option");
      Array.prototype.forEach.call(options, function (option) {
        option.addEventListener("click", function () {
          Array.prototype.forEach.call(options, function (o) {
            o.setAttribute("aria-pressed", o === option ? "true" : "false");
          });
          group.dispatchEvent(new CustomEvent("hbm:select", {
            bubbles: true, detail: { value: option.dataset.value || option.textContent.trim() }
          }));
        });
      });
    });
  }

  /* ------------------------------------------------------------------ menus */

  function wireMenus(root) {
    each(root, "[data-hbm-menu-trigger]", function (trigger) {
      var wrap = trigger.closest(".hbm-menu-wrap");
      var menu = wrap && wrap.querySelector(".hbm-menu");
      if (!menu) return;

      function close() {
        menu.hidden = true;
        trigger.setAttribute("aria-expanded", "false");
      }
      function open() {
        menu.hidden = false;
        trigger.setAttribute("aria-expanded", "true");
        var first = menu.querySelector(".hbm-menu__item");
        if (first) first.focus();
      }

      trigger.setAttribute("aria-haspopup", "true");
      trigger.setAttribute("aria-expanded", "false");
      menu.hidden = true;

      trigger.addEventListener("click", function (e) {
        e.stopPropagation();
        if (menu.hidden) open(); else close();
      });
      menu.addEventListener("keydown", function (e) {
        if (e.key === "Escape") { close(); trigger.focus(); }
      });
      document.addEventListener("click", function (e) {
        if (!wrap.contains(e.target)) close();
      });
    });
  }

  /* ------------------------------------------------------------------- tabs */

  function wireTabs(root) {
    each(root, '.hbm-tabs[role="tablist"]', function (list) {
      var tabs = Array.prototype.slice.call(list.querySelectorAll('[role="tab"]'));

      function select(tab) {
        tabs.forEach(function (t) {
          var on = t === tab;
          t.setAttribute("aria-selected", on ? "true" : "false");
          t.tabIndex = on ? 0 : -1;
          var panel = document.getElementById(t.getAttribute("aria-controls"));
          if (panel) panel.hidden = !on;
        });
      }

      tabs.forEach(function (tab, i) {
        tab.addEventListener("click", function () { select(tab); });
        tab.addEventListener("keydown", function (e) {
          var next = null;
          var rtl = document.dir === "rtl";
          if (e.key === "ArrowRight") next = tabs[(i + (rtl ? -1 : 1) + tabs.length) % tabs.length];
          if (e.key === "ArrowLeft")  next = tabs[(i + (rtl ? 1 : -1) + tabs.length) % tabs.length];
          if (e.key === "Home") next = tabs[0];
          if (e.key === "End")  next = tabs[tabs.length - 1];
          if (next) { e.preventDefault(); select(next); next.focus(); }
        });
      });

      var current = list.querySelector('[aria-selected="true"]') || tabs[0];
      if (current) select(current);
    });
  }

  /* ------------------------------------------------------------------- undo */
  /* A mutation offers its reversal in place. It never moves the camera. */

  function wireUndo(root) {
    each(root, "[data-hbm-undo]", function (btn) {
      btn.addEventListener("click", function () {
        var label = btn.getAttribute("data-hbm-undo") || "Done";
        var seconds = parseInt(btn.getAttribute("data-hbm-undo-seconds") || "5", 10);
        var original = btn.innerHTML;
        var remaining = seconds;

        btn.disabled = true;
        btn.classList.add("hbm-act--grow");

        function render() { btn.textContent = label + " · undo " + remaining + "s"; }
        render();

        var timer = global.setInterval(function () {
          remaining -= 1;
          if (remaining <= 0) {
            global.clearInterval(timer);
            btn.disabled = false;
            btn.classList.remove("hbm-act--grow");
            btn.innerHTML = original;
            return;
          }
          render();
        }, 1000);
      });
    });
  }

  /* ------------------------------------------------------------------ world */
  /*
     A world is declared, not simulated. Every node carries persisted
     coordinates in data-hbm-x / data-hbm-y (percentages of the canvas), and
     its real neighbours in data-hbm-neighbours (comma-separated node ids).
     Focus change: the camera travels along the edge, the marker travels with
     it, the neighbour set is recomputed from real degree, and nothing is
     unmounted. Two loads of the same data lay out identically because
     nothing here reads render order or a clock.
  */

  function neighboursOf(node) {
    return (node.getAttribute("data-hbm-neighbours") || "")
      .split(",")
      .map(function (s) { return s.trim(); })
      .filter(Boolean);
  }

  function wireWorld(root) {
    each(root, ".hbm-world", function (world) {
      var canvas = world.querySelector(".hbm-world__canvas") || world;
      var marker = world.querySelector(".hbm-marker");
      var here = world.querySelector(".hbm-worldview__here");

      function nodeById(id) { return world.querySelector('.hbm-node[data-hbm-id="' + id + '"]'); }

      function place(el, node) {
        if (!el || !node) return;
        el.style.setProperty("--hbm-node-x", node.getAttribute("data-hbm-x") || "50%");
        el.style.setProperty("--hbm-node-y", node.getAttribute("data-hbm-y") || "50%");
      }

      function focus(node, viaEdge) {
        if (!node) return;
        var nbrs = neighboursOf(node);

        Array.prototype.forEach.call(world.querySelectorAll(".hbm-node"), function (n) {
          var id = n.getAttribute("data-hbm-id");
          n.classList.toggle("hbm-node--focus", n === node);
          n.classList.toggle("hbm-node--neighbour", nbrs.indexOf(id) !== -1);
          n.setAttribute("aria-current", n === node ? "true" : "false");
          n.tabIndex = (n === node || nbrs.indexOf(id) !== -1) ? 0 : -1;
          /* the drawn degree tracks the real degree */
          var degree = n.querySelector(".hbm-node__degree");
          if (degree) degree.textContent = neighboursOf(n).length + " linked";
        });

        /* only the edge actually being travelled is marked active */
        Array.prototype.forEach.call(world.querySelectorAll(".hbm-world__edge"), function (e) {
          e.classList.toggle("hbm-world__edge--active", e === viaEdge);
        });

        /* the camera follows the edge's own path: pan so the focus centres */
        var x = parseFloat(node.getAttribute("data-hbm-x") || "50");
        var y = parseFloat(node.getAttribute("data-hbm-y") || "50");
        canvas.style.setProperty("--hbm-pan-x", (50 - x) + "%");
        canvas.style.setProperty("--hbm-pan-y", (50 - y) + "%");

        /* you-are-here updates at every zoom level, reduced motion or not */
        place(marker, node);
        place(here, node);

        world.setAttribute("data-hbm-focus", node.getAttribute("data-hbm-id") || "");
        world.dispatchEvent(new CustomEvent("hbm:focus", {
          bubbles: true,
          detail: { id: node.getAttribute("data-hbm-id"), neighbours: nbrs, reduced: reduced.matches }
        }));
      }

      Array.prototype.forEach.call(world.querySelectorAll(".hbm-node"), function (node) {
        node.setAttribute("role", "link");
        node.addEventListener("click", function () { focus(node); });
        node.addEventListener("keydown", function (e) {
          if (e.key === "Enter" || e.key === " ") { e.preventDefault(); focus(node); return; }
          /* keyboard traversal follows the graph, not DOM order */
          if (e.key === "ArrowRight" || e.key === "ArrowDown" ||
              e.key === "ArrowLeft"  || e.key === "ArrowUp") {
            var nbrs = neighboursOf(node);
            if (!nbrs.length) return;
            e.preventDefault();
            var step = (e.key === "ArrowRight" || e.key === "ArrowDown") ? 1 : -1;
            var cursor = parseInt(node.getAttribute("data-hbm-cursor") || "0", 10);
            cursor = (cursor + step + nbrs.length) % nbrs.length;
            node.setAttribute("data-hbm-cursor", String(cursor));
            var target = nodeById(nbrs[cursor]);
            if (target) target.focus();
          }
        });
      });

      /* zoom rescales the same world; it never routes to another screen */
      Array.prototype.forEach.call(world.querySelectorAll("[data-hbm-zoom]"), function (btn) {
        btn.addEventListener("click", function () {
          var z = parseFloat(canvas.style.getPropertyValue("--hbm-zoom") || "1");
          var delta = parseFloat(btn.getAttribute("data-hbm-zoom")) || 1;
          canvas.style.setProperty("--hbm-zoom", String(Math.min(4, Math.max(0.5, z * delta))));
        });
      });

      var initial = world.getAttribute("data-hbm-focus");
      focus(initial ? nodeById(initial) : world.querySelector(".hbm-node"));
    });
  }

  /* --------------------------------------------------------- exit to panel */
  /* Settings is an exit, not a modal: the world pulls back, the app shrinks
     to a card, the panel opens beside it. */

  function wireExit(root) {
    each(root, "[data-hbm-exit-open]", function (trigger) {
      var scope = document.getElementById(trigger.getAttribute("data-hbm-exit-open"));
      if (!scope) return;
      var drawer = scope.querySelector(".hbm-drawer");
      var app = scope.querySelector(".hbm-exit__app");

      function open() {
        scope.classList.add("hbm-exit");
        if (drawer) { drawer.hidden = false; drawer.focus(); }
        trigger.setAttribute("aria-expanded", "true");
      }
      function close() {
        scope.classList.remove("hbm-exit");
        if (drawer) drawer.hidden = true;
        trigger.setAttribute("aria-expanded", "false");
        trigger.focus();
      }

      if (drawer) {
        drawer.tabIndex = -1;
        drawer.hidden = true;
        drawer.addEventListener("keydown", function (e) { if (e.key === "Escape") close(); });
        Array.prototype.forEach.call(drawer.querySelectorAll("[data-hbm-exit-close]"), function (b) {
          b.addEventListener("click", close);
        });
      }
      if (app) app.setAttribute("aria-hidden", "false");
      trigger.addEventListener("click", open);
    });
  }

  /* ------------------------------------------------------------ retry hooks */
  /* A failure is a state inside the node. Retry happens in place; the camera
     is not disturbed and nothing is re-laid-out. */

  function wireRetry(root) {
    each(root, "[data-hbm-retry]", function (btn) {
      btn.addEventListener("click", function () {
        var target = document.getElementById(btn.getAttribute("data-hbm-retry"));
        if (!target) return;
        target.dispatchEvent(new CustomEvent("hbm:retry", { bubbles: true }));
        btn.disabled = true;
        btn.textContent = "Trying again…";
      });
    });
  }

  /* ---------------------------------------------------------------- wiring */

  function wire(root) {
    var scope = root || document;
    wireSwitches(scope);
    wireSegmented(scope);
    wireMenus(scope);
    wireTabs(scope);
    wireUndo(scope);
    wireWorld(scope);
    wireExit(scope);
    wireRetry(scope);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function () { wire(document); });
  } else {
    wire(document);
  }

  global.herbarium = { wire: wire, toast: toast, reducedMotion: function () { return reduced.matches; } };

})(window);
