/* ==========================================================================
   Herbarium — documentation-site behaviour  (v1.0.0)

   Owns the doc site only: sidebar generation, current-page marking, on-page
   table of contents, version display, theme switching. It knows nothing
   about product components — that is system.js.

   The NAV registry below is the site map, and it mirrors `constitution.json`
   exactly: the foundations set is fixed, and the component / pattern /
   guideline entries are the slugs that manifest declares. Every page later
   batches write is already listed here; a page declares which entry it is via
   `body[data-page]`, and its depth via `body[data-root]` ("" at the root,
   "../" inside a section folder). Page authors never edit this file.
   ========================================================================== */

(function (global) {
  "use strict";

  var VERSION = "1.0.0";
  var SYSTEM_NAME = "Herbarium";
  var TAGLINE = "PlantPal design system";
  var THEME_KEY = "hbm-docs-theme";

  var NAV = [
    {
      title: "Overview",
      items: [
        { id: "home", label: "Home", href: "index.html" }
      ]
    },
    {
      title: "Foundations",
      items: [
        { id: "colors",      label: "Colors",       href: "foundations/colors.html" },
        { id: "spacing",     label: "Spacing",      href: "foundations/spacing.html" },
        { id: "typography",  label: "Typography",   href: "foundations/typography.html" },
        { id: "elevation",   label: "Elevation",    href: "foundations/elevation.html" },
        { id: "motion",      label: "Motion",       href: "foundations/motion.html" },
        { id: "iconography", label: "Iconography",  href: "foundations/iconography.html" }
      ]
    },
    {
      title: "Components",
      items: [
        { id: "sheet",             label: "Sheet",                href: "components/sheet.html" },
        { id: "plant-tag",         label: "Plant tag",            href: "components/plant-tag.html" },
        { id: "plate",             label: "Plate",                href: "components/plate.html" },
        { id: "health-confidence", label: "Health & confidence",  href: "components/health-confidence.html" },
        { id: "edges",             label: "Edges",                href: "components/edges.html" },
        { id: "actions",           label: "Actions",              href: "components/actions.html" },
        { id: "care-log",          label: "Care log",             href: "components/care-log.html" },
        { id: "world-and-nodes",   label: "World & nodes",        href: "components/world-and-nodes.html" }
      ]
    },
    {
      title: "Patterns",
      items: [
        { id: "traversal",   label: "Traversal",   href: "patterns/traversal.html" },
        { id: "uncertainty", label: "Uncertainty", href: "patterns/uncertainty.html" },
        { id: "due-today",   label: "Due today",   href: "patterns/due-today.html" }
      ]
    },
    {
      title: "Guidelines",
      items: [
        { id: "rtl",   label: "RTL & locale", href: "guidelines/rtl.html" },
        { id: "voice", label: "Voice & copy", href: "guidelines/voice.html" }
      ]
    }
  ];

  function root() {
    return document.body.getAttribute("data-root") || "";
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  /* ---------------------------------------------------------------- theme */

  function storedTheme() {
    try { return global.localStorage.getItem(THEME_KEY); } catch (e) { return null; }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    try { global.localStorage.setItem(THEME_KEY, theme); } catch (e) { /* private mode */ }
    var toggle = document.querySelector(".ds-theme-toggle");
    if (toggle) {
      toggle.textContent = theme === "light" ? "Paper · switch to dusk" : "Dusk · switch to paper";
      toggle.setAttribute("aria-pressed", theme === "light" ? "true" : "false");
    }
  }

  /* -------------------------------------------------------------- sidebar */

  function buildSidebar() {
    if (document.querySelector(".ds-sidebar")) return;

    var shell = document.querySelector(".ds-shell");
    if (!shell) return;

    var base = root();
    var current = document.body.getAttribute("data-page");

    var aside = el("aside", "ds-sidebar");
    aside.setAttribute("aria-label", "Site");

    var brand = el("a", "ds-brand");
    brand.href = base + "index.html";
    brand.appendChild(el("span", "ds-brand__name", SYSTEM_NAME));
    brand.appendChild(el("span", "ds-brand__tagline", TAGLINE));
    aside.appendChild(brand);

    var version = el("p", "ds-version");
    version.appendChild(el("span", "hbm-dot"));
    version.appendChild(document.createTextNode("v" + VERSION));
    aside.appendChild(version);

    var nav = el("nav", "ds-nav");
    nav.setAttribute("aria-label", "Sections");

    NAV.forEach(function (group) {
      nav.appendChild(el("p", "ds-nav__group-title", group.title));
      var list = el("ul", "ds-nav__list");
      group.items.forEach(function (item) {
        var li = el("li");
        var link = el("a", "ds-nav__link", item.label);
        link.href = base + item.href;
        if (item.id === current) link.setAttribute("aria-current", "page");
        li.appendChild(link);
        list.appendChild(li);
      });
      nav.appendChild(list);
    });

    aside.appendChild(nav);

    var toggle = el("button", "ds-theme-toggle");
    toggle.type = "button";
    toggle.addEventListener("click", function () {
      var now = document.documentElement.getAttribute("data-theme");
      applyTheme(now === "light" ? "dusk" : "light");
    });
    aside.appendChild(toggle);

    shell.insertBefore(aside, shell.firstChild);
  }

  /* ------------------------------------------------------------------ TOC */

  function slug(text) {
    return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  }

  function buildToc() {
    var article = document.querySelector(".ds-article");
    if (!article) return;

    var headings = article.querySelectorAll(".ds-section > h2");
    if (headings.length < 2) return;

    var toc = el("nav", "ds-toc");
    toc.setAttribute("aria-label", "On this page");
    toc.appendChild(el("p", "ds-toc__title", "On this page"));

    var list = el("ul", "ds-toc__list");
    Array.prototype.forEach.call(headings, function (h) {
      if (!h.id) h.id = slug(h.textContent);
      var li = el("li");
      var link = el("a", null, h.textContent);
      link.href = "#" + h.id;
      li.appendChild(link);
      list.appendChild(li);
    });
    toc.appendChild(list);

    var header = article.querySelector(".ds-page-header");
    if (header && header.nextSibling) article.insertBefore(toc, header.nextSibling);
    else article.insertBefore(toc, article.firstChild);
  }

  /* --------------------------------------------------------------- footer */

  function buildFooter() {
    var article = document.querySelector(".ds-article");
    if (!article || article.querySelector(".ds-footer")) return;
    var footer = el("footer", "ds-footer");
    footer.appendChild(el("span", null, SYSTEM_NAME + " · PlantPal design system"));
    footer.appendChild(el("span", null, "v" + VERSION));
    article.appendChild(footer);
  }

  function init() {
    applyTheme(storedTheme() || document.documentElement.getAttribute("data-theme") || "light");
    buildSidebar();
    applyTheme(document.documentElement.getAttribute("data-theme"));
    buildToc();
    buildFooter();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }

  global.herbariumDocs = { version: VERSION, nav: NAV, setTheme: applyTheme };

})(window);
