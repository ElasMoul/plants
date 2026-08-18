/** The prototype overview/settings overlay, extracted VERBATIM from the pin
 * (frontend-atlas/theme-a/index.html 2152-2281). Regenerate from the pin —
 * never hand-edit. Rendered as the #overview host innerHTML; behavior is wired
 * by delegation in the Chrome component. */
export const OVERVIEW_HTML = `<div id="app-caption">
    <span class="label">PlantPal application</span>
    <p class="state__note">You are in overview mode. This is the entire PlantPal application, still laid out exactly as you left it — including its own particle field, which now covers the app card and nothing beyond it. Click the app card to dive back in.</p>
    <button class="hop" type="button" id="dive-back" style="justify-self:center">Return to the app</button>
  </div>

  <div id="overview-bar">
    <button class="ch-btn" type="button">✥ Move</button>
    <button class="ch-btn" type="button">⬚ Select</button>
    <button class="ch-btn" type="button">⌕ Zoom</button>
    <button class="ch-btn" type="button">◎ Recentre</button>
  </div>

  <section id="settings" aria-labelledby="settings-h">
    <header>
      <h2 id="settings-h">Settings</h2>
      <button class="ch-btn ch-btn--square" type="button" id="close-settings" title="Close settings">✕</button>
    </header>
    <div class="cols">
      <nav aria-label="Settings sections">
        <button type="button">General</button>
        <button type="button">Profile</button>
        <button type="button">Notifications</button>
        <button type="button" aria-current="true">Appearance</button>
        <button type="button">Data &amp; Sync</button>
        <button type="button">AI Preferences</button>
        <button type="button">Privacy &amp; Security</button>
        <button type="button">Integrations</button>
        <button type="button">Advanced</button>
      </nav>
      <div class="pane">
        <h3 class="sec">Appearance · interface</h3>
        <p class="state__note" style="margin-bottom:var(--vs-gutter-wide)">Two readings of the same world. They share every lattice cell, every vein and every timing — what changes is how close the sheets lie, how the type is pitched, and what the chrome is made of. Choosing one applies it at once, behind this panel as well as on it. No node changes cell, so nothing you know about where things are stops being true.</p>
        <div class="palettes" id="interfaces" role="group" aria-label="Interface">
          <button class="palette" type="button" data-ui="sill-line" aria-pressed="true">
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-ink-700)"></i><i style="background:var(--hbm-sill-500)"></i>
              <i style="background:var(--hbm-sill-200)"></i><i style="background:var(--hbm-terra-300)"></i>
            </span>
            <span class="palette__name">Sill line</span>
            <span class="palette__note">default · instrument density, 300×180, machined chrome, compressed type</span>
          </button>
          <button class="palette" type="button" data-ui="glasshouse-table" aria-pressed="false">
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-paper-200)"></i><i style="background:var(--hbm-paper-50)"></i>
              <i style="background:var(--hbm-leaf-600)"></i><i style="background:var(--hbm-terra-500)"></i>
            </span>
            <span class="palette__name">Glasshouse table</span>
            <span class="palette__note">airy sheets, 380×210, gauze chrome, serif plate names</span>
          </button>
        </div>

        <h3 class="sec" style="margin-top:var(--vs-gutter-wide)">Appearance · palette</h3>
        <p class="state__note" style="margin-bottom:var(--hbm-space-5)">The keys that belong to the reading you have chosen. Sill line offers five; the Glasshouse table offers its own two, because that interface and those two keys were drawn together. Choosing one applies it immediately, to the world behind this panel as well as to the panel itself. Nothing moves; only the colour changes.</p>
        <div class="palettes" id="palettes" role="group" aria-label="Colour palette">
          <button class="palette" type="button" data-palette="first-light" aria-pressed="true" data-for-ui="sill-line">
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-ink-700)"></i><i style="background:var(--hbm-sill-500)"></i>
              <i style="background:var(--hbm-sill-200)"></i><i style="background:var(--hbm-terra-300)"></i>
            </span>
            <span class="palette__name">First light</span>
            <span class="palette__note">default · sill light; green kept for health</span>
          </button>
          <button class="palette" type="button" data-palette="night-canopy" aria-pressed="false" data-for-ui="sill-line">
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-humus-700)"></i><i style="background:var(--hbm-humus-500)"></i>
              <i style="background:var(--hbm-leaf-300)"></i><i style="background:var(--hbm-terra-300)"></i>
            </span>
            <span class="palette__name">Night canopy</span>
            <span class="palette__note">theme-a's key, at this density</span>
          </button>
          <button class="palette" type="button" data-palette="terrarium" aria-pressed="false" data-for-ui="sill-line">
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-ink-700)"></i><i style="background:var(--hbm-ink-500)"></i>
              <i style="background:var(--hbm-sill-200)"></i><i style="background:var(--hbm-terra-200)"></i>
            </span>
            <span class="palette__name">Terrarium</span>
            <span class="palette__note">glass and condensation</span>
          </button>
          <button class="palette" type="button" data-palette="potting-shed" aria-pressed="false" data-for-ui="sill-line">
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-humus-600)"></i><i style="background:var(--hbm-humus-400)"></i>
              <i style="background:var(--hbm-terra-300)"></i><i style="background:var(--hbm-leaf-300)"></i>
            </span>
            <span class="palette__name">Potting shed</span>
            <span class="palette__note">terracotta at dusk</span>
          </button>
          <button class="palette" type="button" data-palette="pressed-sheet" aria-pressed="false" data-for-ui="sill-line">
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-paper-200)"></i><i style="background:var(--hbm-paper-50)"></i>
              <i style="background:var(--hbm-leaf-500)"></i><i style="background:var(--hbm-terra-500)"></i>
            </span>
            <span class="palette__name">Pressed sheet</span>
            <span class="palette__note">the herbarium sheet, in daylight</span>
          </button>
          <button class="palette" type="button" data-palette="late-bench" aria-pressed="false" data-for-ui="glasshouse-table" hidden>
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-humus-500)"></i><i style="background:var(--hbm-humus-300)"></i>
              <i style="background:var(--hbm-leaf-200)"></i><i style="background:var(--hbm-terra-300)"></i>
            </span>
            <span class="palette__name">Late bench</span>
            <span class="palette__note">the same glasshouse an hour after sunset</span>
          </button>
          <button class="palette" type="button" data-palette="glasshouse-table" aria-pressed="true" data-for-ui="glasshouse-table" hidden>
            <span class="palette__swatches" aria-hidden="true">
              <i style="background:var(--hbm-paper-200)"></i><i style="background:var(--hbm-paper-50)"></i>
              <i style="background:var(--hbm-leaf-600)"></i><i style="background:var(--hbm-terra-500)"></i>
            </span>
            <span class="palette__name">Glasshouse table</span>
            <span class="palette__note">default for this reading · daylight on the bench</span>
          </button>
        </div>

        <h3 class="sec" style="margin-top:var(--hbm-space-7)">Appearance · motion</h3>
        <dl class="rows">
          <div class="row"><dt>Travel duration</dt><dd class="v mono">300ms · one timing everywhere</dd></div>
          <div class="row"><dt>Follow the system's reduced-motion setting</dt><dd class="v mono">on</dd></div>
        </dl>
      </div>
    </div>
    <footer>
      <button class="hop" type="button">Reset to defaults</button>
      <span style="display:flex;gap:var(--hbm-space-3)">
        <button class="stake stake--quiet" type="button" id="cancel-settings">Cancel</button>
        <button class="stake" type="button" id="save-settings">Save</button>
      </span>
    </footer>
  </section>
`;
