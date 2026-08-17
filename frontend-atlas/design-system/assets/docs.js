/* ==========================================================================
   RHIZOME — docs.js                           PlantPal · atlas-class · v1.0.0
   --------------------------------------------------------------------------
   The documentation SITE's own behaviour: sidebar generation, current-page
   marking, version display. It knows nothing about the product components —
   that is system.js's job.

   NAV below is the page manifest from constitution.json → pages. The two
   must agree exactly: a slug here that is not there (or the reverse) is a
   dangling link, which assembly QA fails the build on. Foundations are fixed
   by the system and are listed here and in the manifest both.
   ========================================================================== */

(function (global) {
  "use strict";

  var SYSTEM = { name: "Rhizome", version: "1.0.0", regime: "atlas-class", target: "PlantPal" };

  var NAV = [
    { group: "Foundations", dir: "foundations", pages: [
      "colors", "spacing", "typography", "elevation", "motion", "iconography"
    ]},
    { group: "Components", dir: "components", pages: [
      "world", "node", "edge", "aggregate", "chrome", "focus-panel",
      "minimap", "navigate-to", "action-rail", "vitals", "treatment-course"
    ]},
    { group: "Patterns", dir: "patterns", pages: [
      "traversal", "arrivals", "degradation", "arrange-mode", "settings-exit", "day-zero"
    ]},
    { group: "Guidelines", dir: "guidelines", pages: [
      "interaction-and-cursors", "motion-and-scale", "voice-and-copy",
      "accessibility", "extending-this-system"
    ]}
  ];

  function title(slug) {
    return slug.split("-").map(function (w, i) {
      if (i && (w === "and" || w === "to" || w === "this")) return w;
      return w.charAt(0).toUpperCase() + w.slice(1);
    }).join(" ");
  }

  /* How deep is this page? index.html is at the root; every other page sits
     one directory down. The prefix is computed, never hand-written. */
  function base() {
    var d = document.body.getAttribute("data-doc-depth");
    var n = d === null ? 1 : parseInt(d, 10);
    return n > 0 ? "../" : "";
  }

  function currentSlug() {
    var f = location.pathname.split("/").pop() || "index.html";
    return f.replace(/\.html$/, "");
  }
  function currentDir() {
    var parts = location.pathname.split("/");
    return parts[parts.length - 2] || "";
  }

  var DOCS = { SYSTEM: SYSTEM, NAV: NAV, title: title };

  DOCS.buildSidebar = function (host) {
    host = host || document.querySelector("[data-doc-nav]");
    if (!host) return;
    var b = base(), slug = currentSlug(), dir = currentDir();

    var brand = document.createElement("a");
    brand.className = "doc-brand";
    brand.href = b + "index.html";
    brand.textContent = SYSTEM.name;
    var sub = document.createElement("span");
    sub.className = "doc-brand__sub";
    sub.textContent = SYSTEM.target + " · " + SYSTEM.regime;
    brand.appendChild(sub);
    host.appendChild(brand);

    var ver = document.createElement("p");
    ver.className = "doc-version";
    ver.textContent = "v" + SYSTEM.version;
    host.appendChild(ver);

    var nav = document.createElement("nav");
    nav.className = "doc-nav";
    nav.setAttribute("aria-label", "Design system sections");

    NAV.forEach(function (g) {
      var sec = document.createElement("div");
      sec.className = "doc-nav__group";
      var h = document.createElement("h2");
      h.className = "doc-nav__title";
      h.textContent = g.group;
      sec.appendChild(h);
      g.pages.forEach(function (p) {
        var a = document.createElement("a");
        a.className = "doc-nav__link";
        a.href = b + g.dir + "/" + p + ".html";
        a.textContent = title(p);
        if (p === slug && dir === g.dir) a.setAttribute("aria-current", "page");
        sec.appendChild(a);
      });
      nav.appendChild(sec);
    });
    host.appendChild(nav);
  };

  /* Any element with [data-doc-version] gets the version stamped into it. */
  DOCS.stampVersion = function () {
    Array.prototype.forEach.call(document.querySelectorAll("[data-doc-version]"), function (el) {
      el.textContent = SYSTEM.name + " v" + SYSTEM.version;
    });
  };

  /* Convenience for a page that wants to list a group as cards. */
  DOCS.groupPages = function (dir) {
    var g = NAV.filter(function (x) { return x.dir === dir; })[0];
    return g ? g.pages.slice() : [];
  };

  DOCS.init = function () {
    DOCS.buildSidebar();
    DOCS.stampVersion();
    if (global.RZ && typeof global.RZ.verifyParameters === "function") {
      var bad = global.RZ.verifyParameters();
      if (bad.length) {
        console.warn("[Rhizome] parameter drift between constitution.json, tokens.css and system.js:", bad);
      }
    }
  };

  global.DOCS = DOCS;

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", DOCS.init);
  } else {
    DOCS.init();
  }
})(window);
