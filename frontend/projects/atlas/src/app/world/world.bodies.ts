/**
 * The 25 node bodies of the round-9 prototype, extracted VERBATIM from
 * frontend-atlas/theme-a/index.html (each article's .n__body inner HTML:
 * recap, per-node staleness copy, per-card skeleton shapes, and the full
 * focus material - plate, sections, rows, tags, confidence, stakes, hops,
 * state panels, feeds). Trusted static content from the pinned design
 * artifact; rendered with innerHTML and styled by rhizome.css. Regenerate by
 * re-running the extraction against the pin - never hand-edit.
 */
export const NODE_BODIES: Record<string, string> = {
  "n-species": `<div class="n__recap">
              <p class="n__recap-line">28 species</p>
              <p class="n__recap-note">Everything you have identified or added by hand.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--sub"></div><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section class="state" data-brief-item="action:/api/v1/species/**" data-component="card-action-api-v1-species">
                <div class="state__head">
                  <h4 class="state__title">The species index</h4>
                  <span class="state__id">action · /api/v1/species/**</span>
                </div>
                <p class="state__note">Search it, read one, add one that is missing. A species is a reference thing, not one of your plants, so nothing here is ever overdue and nothing here can be watered.</p>
                <dl class="rows">
                  <div class="row"><dt>In the index</dt><dd>28 · 3 added by you</dd></div>
                  <div class="row"><dt>Last added</dt><dd>Calathea orbifolia · Jul 22</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Add a species</button>
                  <button class="stake stake--quiet" type="button">Search the index</button>
                </div>
              </section>

              <section>
                <h3 class="sec">28 species in your collection</h3>
                <p>Two are drawn beside this card as their own nodes — the two that want you most. The other twenty-six are one node away, and travelling into them does not move anything already on screen.</p>
                <dl class="rows">
                  <div class="row"><dt>Fiddle-leaf Fig</dt><dd>12 plants · 1 overdue</dd></div>
                  <div class="row"><dt>Monstera</dt><dd>3 plants · 1 wants repotting</dd></div>
                  <div class="row"><dt>The other 26</dt><dd class="v mono">one hop</dd></div>
                </dl>
                <button class="hop hop--block" type="button" data-goto="n-species-more" style="margin-top:var(--hbm-space-4)">Reach the other 26 species <small>+26 more</small></button>
              </section>
            </div>`,
  "n-monstera": `<div class="n__recap">
              <p class="n__recap-line">3 plants · wants repotting</p>
              <p class="n__recap-note">Monstera deliciosa · ranked 2nd by what is owed</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--plate"></div><div class="sk sk--line"></div></div>
            <div class="n__full">
              <p>Monstera deliciosa. The splits in the leaves arrive with age and light, not with feeding. One of your three has outgrown its pot.</p>
            </div>`,
  "n-species-more": `<div class="n__recap"><p class="n__recap-line">+26 more</p><p class="n__recap-note">Everything below the two highest-ranked, drawn as one node.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section>
                <h3 class="sec">26 more species</h3>
                <p>Nothing here is hidden — it is grouped. The board draws two members plus this node for every collection of four or more, so the geography stays the same size as your garden grows.</p>
                <dl class="rows">
                  <div class="row"><dt>Snake Plant</dt><dd>4 plants</dd></div>
                  <div class="row"><dt>Pothos</dt><dd>2 plants</dd></div>
                  <div class="row"><dt>ZZ Plant</dt><dd>1 plant</dd></div>
                </dl>
                <p class="state__note" style="margin-top:var(--hbm-space-3)">Opening one of these makes it the focus, and this node stays where it is.</p>
              </section>
            </div>`,
  "n-fig": `<div class="n__recap">
              <p class="n__recap-line">12 plants · 1 overdue</p>
              <p class="n__recap-note">Ficus lyrata · one plant needs water</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12 · one plant's health may be older</div>
            <div class="n__skel" aria-hidden="true">
              <div class="sk--grid" style="grid-template-columns:9.5rem 1fr">
                <div class="sk sk--plate" style="height:12rem"></div>
                <div style="display:grid;gap:var(--hbm-space-3)">
                  <div class="sk sk--name"></div><div class="sk sk--sub"></div>
                  <div class="sk sk--line"></div><div class="sk sk--line is-short"></div>
                </div>
              </div>
              <div class="sk--grid">
                <div class="sk sk--cell"></div><div class="sk sk--cell"></div>
                <div class="sk sk--cell"></div><div class="sk sk--cell"></div>
              </div>
              <div class="sk sk--row"></div><div class="sk sk--row"></div>
            </div>

            <div class="n__full">
              <div class="plate">
                <div class="plate__specimen"><span class="plate__score">82</span></div>
                <div>
                  <h2 class="plate__name">Fiddle-leaf Fig</h2>
                  <p class="plate__binomial">Ficus lyrata</p>
                  <div class="plate__meta">
                    <p class="n__recap-line"><span class="tag tag--watch">Needs water</span> Last watered Jul 12 · overdue 3 days</p>
                  </div>
                </div>
              </div>

              <!-- C19: the focused node answers all four questions in place. -->
              <section>
                <h3 class="sec">This node</h3>
                <dl class="rows">
                  <div class="row"><dt>What is this?</dt><dd>A species record — one row in your collection.</dd></div>
                  <div class="row"><dt>Where am I?</dt><dd>Species › Fiddle-leaf Fig</dd></div>
                  <div class="row"><dt>What can I do here?</dt><dd>Water, fertilize, note, photo, scan a leaf.</dd></div>
                  <div class="row"><dt>Where can I go?</dt><dd class="v mono">6 veins out</dd></div>
                </dl>
              </section>

              <section>
                <h3 class="sec">About</h3>
                <!-- ROUND-10 § 4: ordinary prose with ordinary links in it.
                     These are real anchors; clicking one travels the graph,
                     and the pan layer never swallows the click. -->
                <p>Native to West Africa. Large violin-shaped leaves and a strong architectural presence. It wants bright, indirect light and consistent moisture — the <a class="doc-link" href="#n-care" data-goto="n-care">care guide</a> has the watering rhythm, and the two plants of yours that are struggling are in <a class="doc-link" href="#n-garden" data-goto="n-garden">my garden</a>.</p>
                <div class="pair">
                  <dl class="rows">
                    <div class="row"><dt>Family</dt><dd>Moraceae</dd></div>
                    <div class="row"><dt>Native to</dt><dd>West Africa</dd></div>
                    <div class="row"><dt>Growth habit</dt><dd>Tree</dd></div>
                  </dl>
                  <dl class="rows">
                    <div class="row"><dt>Light</dt><dd>Bright, indirect</dd></div>
                    <div class="row"><dt>Water</dt><dd>Top 2–3 cm dry</dd></div>
                    <div class="row"><dt>Humidity</dt><dd>40–60%</dd></div>
                  </dl>
                </div>
              </section>

              <section class="conf">
                <p class="label" style="margin:0">Confidence · medium</p>
                <div class="conf__bar"><i style="width:82%"></i></div>
                <p class="state__note">82% is a pattern match, not certainty. Ask “why” to hear the reasoning in plain words.</p>
              </section>

              <section>
                <h3 class="sec">Recent activity</h3>
                <div class="feed">
                  <div class="feed__row"><span class="feed__when">Jul 18</span><span>Photo logged</span><span class="feed__val">·</span></div>
                  <div class="feed__row"><span class="feed__when">Jul 15</span><span>Watered</span><span class="feed__val">·</span></div>
                  <div class="feed__row"><span class="feed__when">Jul 10</span><span>Leaf cleaned</span><span class="feed__val">·</span></div>
                </div>
                <p class="state__note" style="margin-top:var(--hbm-space-3)">Showing 3 of 14 entries.</p>
                <button class="hop hop--block" type="button" data-goto="n-journal">View the whole journal <small>14 entries</small></button>
              </section>
            </div>`,
  "n-platform": `<div class="n__recap">
              <p class="n__recap-line"><span class="tag tag--thriving">Up</span> 2 feeds out</p>
              <p class="n__recap-note">What PlantPal tells the platform about itself.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Health last checked 09:12 · feeds may be older</div>
            <div class="n__skel" aria-hidden="true">
              <div class="sk sk--sub"></div><div class="sk sk--row"></div><div class="sk sk--row"></div><div class="sk sk--row"></div>
            </div>
            <div class="n__full">

              <section class="state" data-brief-item="data:dimension.event" data-component="card-data-dimension-event">
                <div class="state__head">
                  <h4 class="state__title">Dimension events</h4>
                  <span class="state__id">data · dimension.event</span>
                </div>
                <p class="state__note">Your plant count, each time it changes. Scoped to you. 20 shown, 500 kept.</p>
                <div class="feed">
                  <div class="feed__row"><span class="feed__when">09:04</span><span>plant_count</span><span class="feed__val">12</span></div>
                  <div class="feed__row"><span class="feed__when">Jul 28</span><span>plant_count</span><span class="feed__val">11</span></div>
                  <div class="feed__row"><span class="feed__when">Jul 24</span><span>plant_count</span><span class="feed__val">10</span></div>
                </div>
              </section>

              <section class="state" data-brief-item="data:state.event" data-component="card-data-state-event">
                <div class="state__head">
                  <h4 class="state__title">State events</h4>
                  <span class="state__id">data · state.event</span>
                </div>
                <p class="state__note">App-global. One on each boot, one per completed identification. 20 shown, 200 kept.</p>
                <div class="feed">
                  <div class="feed__row"><span class="feed__when">08:58</span><span>app.status</span><span class="feed__val">UP</span></div>
                  <div class="feed__row"><span class="feed__when">08:41</span><span>activity.count</span><span class="feed__val">7</span></div>
                </div>
              </section>

              <section class="state" data-brief-item="action:\`app.health\`" data-component="card-action-app-health">
                <div class="state__head">
                  <h4 class="state__title">Health check</h4>
                  <span class="state__id">action · app.health</span>
                </div>
                <p class="state__note"><span class="tag tag--thriving">Up</span> Checked 09:12. Database up, gateway up, PlantNet up.</p>
                <button class="stake" type="button">Check again</button>
              </section>

              <section class="state">
                <div class="state__head">
                  <h4 class="state__title">Sign-in</h4>
                  <span class="state__id">action · none · own JWT issuance</span>
                </div>
                <p class="state__note">PlantPal issues its own token. The platform is not asked, and no platform action is exposed here — there is nothing to press, so nothing is drawn as a control.</p>
                <dl class="rows">
                  <div class="row"><dt>Issuer</dt><dd class="v mono">plantpal</dd></div>
                  <div class="row"><dt>Session</dt><dd>Valid until 18:40</dd></div>
                </dl>
              </section>

              <section class="state">
                <div class="state__head">
                  <h4 class="state__title">Internal API</h4>
                  <span class="state__id">action · none · app-internal REST</span>
                </div>
                <p class="state__note">Every screen in PlantPal reads its own JWT-authenticated REST API. This is not a platform action either; it is stated so you can see where the data came from.</p>
                <dl class="rows">
                  <div class="row"><dt>Base</dt><dd class="v mono">/api/v1</dd></div>
                  <div class="row"><dt>Auth</dt><dd class="v mono">bearer JWT</dd></div>
                </dl>
              </section>

              <section class="state">
                <div class="state__head">
                  <h4 class="state__title">What PlantPal emits</h4>
                  <span class="state__id">action · dimension.event, state.event</span>
                </div>
                <p class="state__note">Two emitters, both read-only from your side. Nothing you do on this screen writes to either.</p>
                <dl class="rows">
                  <div class="row"><dt>dimension.event</dt><dd>on plant create / archive</dd></div>
                  <div class="row"><dt>state.event</dt><dd>on boot, on identification</dd></div>
                </dl>
              </section>


            </div>`,
  "n-garden": `<div class="n__recap">
              <p class="n__recap-line">12 plants · 2 need water</p>
              <p class="n__recap-note">Your own plants of every species.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12 · watering counts may be stale</div>
            <div class="n__skel" aria-hidden="true">
              <div class="sk sk--sub"></div><div class="sk sk--row"></div><div class="sk sk--row"></div><div class="sk sk--row"></div>
            </div>
            <div class="pending">
              <p class="label" style="margin:0">Still arriving</p>
              <div class="sk sk--row"></div><div class="sk sk--row"></div>
            </div>
            <div class="n__full">
              <section class="state" data-brief-item="action:/api/v1/plants/**" data-component="card-action-api-v1-plants">
                <div class="state__head">
                  <h4 class="state__title">Your plants</h4>
                  <span class="state__id">action · /api/v1/plants/**</span>
                </div>
                <p class="state__note">Add one, rename one, move one to another room, retire one. Retiring keeps it — its journal, its photographs and its history stay readable; it simply stops asking anything of you.</p>
                <dl class="rows">
                  <div class="row"><dt>Alive</dt><dd>12 · 2 want water</dd></div>
                  <div class="row"><dt>Retired</dt><dd>3 · still readable</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Add a plant</button>
                  <button class="stake stake--quiet" type="button">Show retired plants</button>
                </div>
              </section>

              <section>
                <h3 class="sec">12 plants</h3>
                <p>Twelve is four or more, so the board draws the two that want you most and one node for the rest. Ranked by what is overdue, then by what is being treated, then by what you open most.</p>
                <dl class="rows">
                  <div class="row"><dt>Office Fig <span class="tag tag--watch">Needs water</span></dt><dd class="v mono">65</dd></div>
                  <div class="row"><dt>Studio Fig <span class="tag tag--watch">Watch</span></dt><dd class="v mono">58</dd></div>
                  <div class="row"><dt>The other 10</dt><dd class="v mono">one hop</dd></div>
                </dl>
                <button class="hop hop--block" type="button" data-goto="n-garden-more" style="margin-top:var(--hbm-space-4)">Reach the other 10 plants <small>+10 more</small></button>
              </section>

              <!-- state: LOADING — a feed inside this node still arriving. -->
              <section class="state state--loading" data-brief-item="state:loading" data-component="card-state-loading">
                <div class="state__head">
                  <h4 class="state__title">Health scores</h4>
                  <span class="state__id">state · loading</span>
                </div>
                <p class="state__note">Still checking. Twelve plants, three answered.</p>
                <div class="sk sk--row"></div>
                <div class="sk sk--row"></div>
                <p class="state__note">The nodes stay where they are while this resolves.</p>
              </section>
            </div>`,
  "n-care": `<div class="n__recap">
              <p class="n__recap-line">Water · light · soil</p>
              <p class="n__recap-note">Best practice for Ficus lyrata.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Cached Jul 20 · guides change rarely</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--sub"></div><div class="sk sk--line"></div><div class="sk sk--line is-short"></div></div>
            <div class="n__full">
              <section class="state" data-brief-item="action:/api/v1/care/**" data-component="card-action-api-v1-care">
                <div class="state__head">
                  <h4 class="state__title">Care, and what you actually did</h4>
                  <span class="state__id">action · /api/v1/care/**</span>
                </div>
                <p class="state__note">The guide above is what a Ficus lyrata wants. This is the record of what it got: every watering, feeding and repotting you logged, and the schedule they are measured against.</p>
                <dl class="rows">
                  <div class="row"><dt>Watered</dt><dd>Jul 24 · 300 ml</dd></div>
                  <div class="row"><dt>Fed</dt><dd>Jul 04 · half strength</dd></div>
                  <div class="row"><dt>Next due</dt><dd>Today</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Log a watering</button>
                  <button class="stake stake--quiet" type="button">Change the schedule</button>
                </div>
              </section>

              <section>
                <h3 class="sec">Watering</h3>
                <p>Water slowly until it drains from the bottom, then let the top 2–3 cm dry before the next drink. If the leaves are drooping and the soil is dry all the way down, see <a class="doc-link" href="#n-underwater" data-goto="n-underwater">underwatering</a>.</p>
                <dl class="rows">
                  <div class="row"><dt>Ideal moisture</dt><dd>40 – 60%</dd></div>
                  <div class="row"><dt>Soil</dt><dd>Well-draining, rich</dd></div>
                  <div class="row"><dt>Pot</dt><dd>With drainage</dd></div>
                </dl>
              </section>
            </div>`,
  "n-problems": `<div class="n__recap">
              <p class="n__recap-line">3 active</p>
              <p class="n__recap-note">Underwatering · overwatering · root rot</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section>
                <h3 class="sec">3 active problems</h3>
                <p>Three is fewer than four, so every one of them is drawn as its own node beside this card. No aggregate, no truncation, nothing folded away.</p>
                <dl class="rows">
                  <div class="row"><dt>Underwatering <span class="tag tag--watch">Moderate</span></dt><dd>Detected Jul 18</dd></div>
                  <div class="row"><dt>Overwatering <span class="tag tag--thriving">Resolving</span></dt><dd>Detected Jul 02</dd></div>
                  <div class="row"><dt>Root rot <span class="tag tag--ailing">Serious</span></dt><dd>Detected Jun 28</dd></div>
                </dl>
              </section>
            </div>`,
  "n-journal": `<div class="n__recap">
              <p class="n__recap-line">14 entries</p>
              <p class="n__recap-note">Watering, notes and photos, newest first.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section>
                <h3 class="sec">14 entries</h3>
                <p>Fourteen is four or more, so the same rule applies here as to species and to plants: the two highest-ranked entries are drawn, and one node holds the other twelve.</p>
                <div class="feed">
                  <div class="feed__row"><span class="feed__when">Jul 18</span><span>Photo logged · Office Fig</span><span class="feed__val">·</span></div>
                  <div class="feed__row"><span class="feed__when">Jul 15</span><span>Watered · 4 plants</span><span class="feed__val">·</span></div>
                </div>
                <button class="hop hop--block" type="button" data-goto="n-journal-more" style="margin-top:var(--hbm-space-4)">Reach the other 12 entries <small>+12 more</small></button>
              </section>
            </div>`,
  "n-ident": `<div class="n__recap">
              <p class="n__recap-line"><span class="tag tag--ailing">Scan failed</span></p>
              <p class="n__recap-note">The last leaf scan did not come back.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last attempt 09:14</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--plate"></div><div class="sk sk--line"></div></div>
            <div class="n__full">
              <section class="state" data-brief-item="action:/api/v1/identifications/**" data-component="card-action-api-v1-identifications">
                <div class="state__head">
                  <h4 class="state__title">Your identifications</h4>
                  <span class="state__id">action · /api/v1/identifications/**</span>
                </div>
                <p class="state__note">Every scan you have run, kept with its photograph and its answer — including the ones PlantPal got wrong, because a wrong answer you corrected is worth more than a tidy list.</p>
                <dl class="rows">
                  <div class="row"><dt>Run</dt><dd>9 · 7 confirmed by you</dd></div>
                  <div class="row"><dt>This one</dt><dd>Jul 18 · confirmed</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Identify a plant</button>
                  <button class="stake stake--quiet" type="button">Correct this answer</button>
                </div>
              </section>

              <section class="state" data-brief-item="action:/api/v1/plantnet/**" data-component="card-action-api-v1-plantnet">
                <div class="state__head">
                  <h4 class="state__title">Where the answer came from</h4>
                  <span class="state__id">action · /api/v1/plantnet/**</span>
                </div>
                <p class="state__note">PlantNet, an outside service, looked at the photograph and offered candidates with a confidence each. PlantPal does not pretend that work is its own, and it does not hide the number.</p>
                <dl class="rows">
                  <div class="row"><dt>Ficus lyrata</dt><dd>0.94</dd></div>
                  <div class="row"><dt>Ficus elastica</dt><dd>0.05</dd></div>
                  <div class="row"><dt>Quota</dt><dd>17 of 50 this month</dd></div>
                </dl>
                <p class="state__note">If it is unavailable, identification by hand stays open — the error is a state inside this node, never a screen of its own.</p>
              </section>

              <section class="state state--error" data-brief-item="state:error" data-component="card-state-error">
                <div class="state__head">
                  <h4 class="state__title">The scan did not come back</h4>
                  <span class="state__id">state · error</span>
                </div>
                <p class="state__note">PlantNet answered with a 503 at 09:14. Your photo is kept. Nothing was lost and nothing moved.</p>
                <div class="btn-row">
                  <button class="stake" type="button">Try the scan again</button>
                  <button class="stake stake--quiet" type="button">Identify by hand</button>
                </div>
              </section>
              <p class="state__note">Retry sits here, in the node. The camera does not move and no other node changes.</p>
            </div>`,
  "n-office": `<div class="n__recap"><p class="n__recap-line">Needs water · 65</p><p class="n__recap-note">Overdue 2 days · PL-002 · ranked 1st</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--plate"></div></div>
            <div class="n__full"><p>Low light and a draught from the door. Two lower leaves dropped this week. Drawn as its own node because it is the most overdue thing in your garden.</p></div>`,
  "n-studio": `<div class="n__recap"><p class="n__recap-line">Watch · 58</p><p class="n__recap-note">Under treatment · PL-005 · ranked 2nd</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--plate"></div></div>
            <div class="n__full"><p>Watered three days ago and still limp. It is on the treatment plan for underwatering, which is why it ranks second rather than by score alone.</p></div>`,
  "n-garden-more": `<div class="n__recap"><p class="n__recap-line">+10 more</p><p class="n__recap-note">Drawn as one node so the geography holds.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section>
                <h3 class="sec">10 more plants</h3>
                <dl class="rows">
                  <div class="row"><dt>Living Room Fig</dt><dd class="v mono">82</dd></div>
                  <div class="row"><dt>Bedroom Fig</dt><dd class="v mono">90</dd></div>
                  <div class="row"><dt>Hallway Fig</dt><dd class="v mono">85</dd></div>
                </dl>
                <p class="state__note" style="margin-top:var(--hbm-space-3)">All ten are reachable from here; each opens as its own node beside this one, and this card keeps its cell.</p>
                <button class="hop hop--block" type="button" data-goto="n-unknown">Travel into the unloaded region <small>not yet fetched</small></button>
              </section>
            </div>`,
  "n-reminders": `<div class="n__recap"><p class="n__recap-line">Nothing due today</p><p class="n__recap-note">Next check-in tomorrow, 09:00.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section class="state" data-brief-item="action:/api/v1/reminders/**" data-component="card-action-api-v1-reminders">
                <div class="state__head">
                  <h4 class="state__title">What PlantPal will remind you of</h4>
                  <span class="state__id">action · /api/v1/reminders/**</span>
                </div>
                <p class="state__note">A reminder belongs to a plant and to a kind of care. Snoozing one moves that reminder and nothing else; it never silently reschedules the rest of your garden.</p>
                <dl class="rows">
                  <div class="row"><dt>Water · Office Fig</dt><dd>Overdue 2 days</dd></div>
                  <div class="row"><dt>Water · Studio Fig</dt><dd>Today, 18:00</dd></div>
                  <div class="row"><dt>Feed · all figs</dt><dd>Aug 04</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Add a reminder</button>
                  <button class="stake stake--quiet" type="button">Snooze the overdue one</button>
                </div>
              </section>

              <section class="state" data-brief-item="action:/api/v1/notifications/**" data-component="card-action-api-v1-notifications">
                <div class="state__head">
                  <h4 class="state__title">How it reaches you</h4>
                  <span class="state__id">action · /api/v1/notifications/**</span>
                </div>
                <p class="state__note">A reminder is the thing; a notification is the knock. They are separate on purpose — you can keep every reminder and turn every knock off.</p>
                <dl class="rows">
                  <div class="row"><dt>Push</dt><dd>On · not during quiet hours</dd></div>
                  <div class="row"><dt>Email</dt><dd>Weekly digest, Sundays</dd></div>
                  <div class="row"><dt>Unread</dt><dd>3</dd></div>
                </dl>
                <p class="state__note">An arrival never moves the camera. It lights the node it concerns and waits — see the vein to <a class="doc-link" href="#n-office" data-goto="n-office">Office Fig</a>.</p>
                <div class="btn-row">
                  <button class="stake stake--quiet" type="button">Mark all read</button>
                </div>
              </section>

              <section class="state state--empty" data-brief-item="state:empty" data-component="card-state-empty">
                <div class="state__head">
                  <h4 class="state__title">Nothing due today</h4>
                  <span class="state__id">state · empty</span>
                </div>
                <div class="empty-plot">
                  <span class="glyph" aria-hidden="true">◌</span>
                  <p class="state__note">No reminder is due. This is an empty plot with room in it, not a failure — the next check-in is tomorrow at 09:00.</p>
                </div>
                <button class="stake stake--quiet" type="button">Add a reminder</button>
              </section>
            </div>`,
  "n-underwater": `<div class="n__recap"><p class="n__recap-line"><span class="tag tag--watch">Moderate</span> Jul 18</p><p class="n__recap-note">Drooping leaves, dry soil, crispy edges.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--line"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section>
                <h3 class="sec">Underwatering · moderate</h3>
                <p>The plant is showing signs of not getting enough water. Recovery usually takes three to seven days once the rhythm is corrected.</p>
                <dl class="rows">
                  <div class="row"><dt>Cause</dt><dd>Infrequent watering</dd></div>
                  <div class="row"><dt>Ideal moisture</dt><dd>40 – 60%</dd></div>
                </dl>
                <button class="hop hop--block" type="button" data-goto="n-treatment" style="margin-top:var(--hbm-space-4)">Open the treatment plan <small>4 steps</small></button>
              </section>
            </div>`,
  "n-overwater": `<div class="n__recap"><p class="n__recap-line"><span class="tag tag--thriving">Resolving</span> Jul 02</p><p class="n__recap-note">Yellowing lower leaves, soil wet for days.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--line"></div></div>
            <div class="n__full"><p>Improving since the watering interval was lengthened on Jul 09. No action is needed unless the lower leaves start dropping again.</p></div>`,
  "n-rootrot": `<div class="n__recap"><p class="n__recap-line"><span class="tag tag--ailing">Serious</span> Jun 28</p><p class="n__recap-note">Soft brown roots, a sour smell at the pot.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--line"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section>
                <h3 class="sec">Root rot · serious</h3>
                <p>This one shares a treatment plan with underwatering, because both are corrected by changing how the pot drains rather than how often you water.</p>
                <button class="hop hop--block" type="button" data-goto="n-treatment">Open the treatment plan <small>4 steps</small></button>
              </section>
            </div>`,
  "n-j1": `<div class="n__recap"><p class="n__recap-line">Photo · Office Fig</p><p class="n__recap-note">Most recent entry.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--plate"></div></div>
            <div class="n__full"><p>A photo of the two lower leaves that dropped, logged so the next scan has something to compare against.</p></div>`,
  "n-j2": `<div class="n__recap"><p class="n__recap-line">Watered · 4 plants</p><p class="n__recap-note">Second most recent.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--line"></div></div>
            <div class="n__full"><p>Four plants watered in one round: Living Room, Bedroom, Hallway and Terrace.</p></div>`,
  "n-journal-more": `<div class="n__recap"><p class="n__recap-line">+12 more</p><p class="n__recap-note">The rest of the journal, as one node.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div></div>
            <div class="n__full"><p>Twelve older entries, oldest Jun 02. Opening one makes it the focus; this node keeps its cell.</p></div>`,
  "n-treatment": `<div class="n__recap"><p class="n__recap-line">4 steps · active</p><p class="n__recap-note">Shared by underwatering and root rot.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Last synced 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section class="state" data-brief-item="action:/api/v1/treatment-plans/**" data-component="card-action-api-v1-treatment-plans">
                <div class="state__head">
                  <h4 class="state__title">The course you are running</h4>
                  <span class="state__id">action · /api/v1/treatment-plans/**</span>
                </div>
                <p class="state__note">A plan is a sequence with an end, not a setting. You can start one, mark a step done, pause it while you are away, or abandon it — and abandoning it says so in the journal rather than deleting the fortnight you spent.</p>
                <dl class="rows">
                  <div class="row"><dt>Running</dt><dd>Root rot · day 6 of 14</dd></div>
                  <div class="row"><dt>Next step</dt><dd>Tomorrow · check the crown</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Mark today done</button>
                  <button class="stake stake--quiet" type="button">Pause this course</button>
                </div>
              </section>

              <section>
                <h3 class="sec">Four steps</h3>
                <dl class="rows">
                  <div class="row"><dt>1 · Deep water</dt><dd>Today</dd></div>
                  <div class="row"><dt>2 · Monitor soil moisture</dt><dd>Daily</dd></div>
                  <div class="row"><dt>3 · Improve environment</dt><dd>This week</dd></div>
                  <div class="row"><dt>4 · Evaluate recovery</dt><dd>In 1 week</dd></div>
                </dl>
                <button class="stake" type="button" style="margin-top:var(--hbm-space-4)">Mark step 1 as done</button>
              </section>
            </div>`,
  "n-account": `<div class="n__recap">
              <p class="n__recap-line">Signed in · 12 plants</p>
              <p class="n__recap-note">Who PlantPal thinks you are, and how it knows.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Token refreshed 08:41 · valid until 18:40</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--sub"></div><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">

              <section class="state" data-brief-item="action:POST /api/v1/auth/register" data-component="card-action-post-api-v1-auth-register">
                <div class="state__head">
                  <h4 class="state__title">Make an account</h4>
                  <span class="state__id">action · POST /api/v1/auth/register</span>
                </div>
                <p class="state__note">You already have one, so this is shown as it stands rather than as a form: an email, a password, and a name PlantPal will greet you by. Nothing else is asked for and nothing else is kept.</p>
                <dl class="rows">
                  <div class="row"><dt>Registered</dt><dd>14 March</dd></div>
                  <div class="row"><dt>Email</dt><dd class="v mono">you@example.org</dd></div>
                </dl>
                <p class="state__note">Registering signs you in on the same answer — one round trip, not two.</p>
              </section>

              <section class="state" data-brief-item="action:POST /api/v1/auth/login" data-component="card-action-post-api-v1-auth-login">
                <div class="state__head">
                  <h4 class="state__title">Signing in</h4>
                  <span class="state__id">action · POST /api/v1/auth/login</span>
                </div>
                <p class="state__note">Email and password, back a token that lasts a working day. When it lapses PlantPal asks again in place — it does not throw you out of where you were standing.</p>
                <dl class="rows">
                  <div class="row"><dt>This session</dt><dd>Since 08:41 · this device</dd></div>
                  <div class="row"><dt>Expires</dt><dd>18:40</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Sign in on another device</button>
                  <button class="stake stake--quiet" type="button">Sign out here</button>
                </div>
              </section>

              <section class="state" data-brief-item="action:/api/v1/users/**" data-component="card-action-api-v1-users">
                <div class="state__head">
                  <h4 class="state__title">You, as PlantPal holds you</h4>
                  <span class="state__id">action · /api/v1/users/**</span>
                </div>
                <p class="state__note">Your name, your email, your units and your quiet hours. Everything on this list is editable and everything on it can be exported or deleted — if a field is not here, PlantPal is not keeping it.</p>
                <dl class="rows">
                  <div class="row"><dt>Display name</dt><dd>Sam</dd></div>
                  <div class="row"><dt>Units</dt><dd>Metric · °C</dd></div>
                  <div class="row"><dt>Quiet hours</dt><dd>21:00 – 07:30</dd></div>
                </dl>
                <div class="btn-row">
                  <button class="stake" type="button">Edit your details</button>
                  <button class="stake stake--quiet" type="button">Export everything</button>
                </div>
              </section>

            </div>`,
  "n-today": `<div class="n__recap">
              <p class="n__recap-line"><span class="tag tag--watch">2 due</span> 1 overdue</p>
              <p class="n__recap-note">What your garden wants from you before this evening.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Counted 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--sub"></div><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">

              <section class="state" data-brief-item="action:/api/v1/dashboard/**" data-component="card-action-api-v1-dashboard">
                <div class="state__head">
                  <h4 class="state__title">Today's summary</h4>
                  <span class="state__id">action · /api/v1/dashboard/**</span>
                </div>
                <p class="state__note">A count, not a feed. Every line here is a door: pressing one travels to the plant it is about, and the plant is already drawn beside this card, so you can see where you are going before you go.</p>
                <dl class="rows">
                  <div class="row"><dt>Water</dt><dd><a class="doc-link" href="#n-office" data-goto="n-office">Office Fig</a> · 2 days overdue</dd></div>
                  <div class="row"><dt>Water</dt><dd><a class="doc-link" href="#n-studio" data-goto="n-studio">Studio Fig</a> · today</dd></div>
                  <div class="row"><dt>Check on</dt><dd><a class="doc-link" href="#n-rootrot" data-goto="n-rootrot">Root rot</a> · day 6 of 14</dd></div>
                </dl>
                <p class="state__note">Counts move; nothing on the plane moves with them (C9).</p>
              </section>

            </div>`,
  "n-ask": `<div class="n__recap">
              <p class="n__recap-line">Ask about this fig</p>
              <p class="n__recap-note">The knowledgeable friend, not the botanist.</p>
            </div>
            <div class="staleness"><span aria-hidden="true">◷</span> Answers use your garden as it stood at 09:12</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--sub"></div><div class="sk sk--line"></div><div class="sk sk--line is-short"></div></div>
            <div class="n__full">

              <section class="state" data-brief-item="action:/api/v1/chat/**" data-component="card-action-api-v1-chat">
                <div class="state__head">
                  <h4 class="state__title">Ask about this fig</h4>
                  <span class="state__id">action · /api/v1/chat/**</span>
                </div>
                <p class="state__note">A conversation, kept per plant, so it starts already knowing which one you mean. It reads your garden; it never writes to it — anything it suggests arrives as a proposal you press, over on the plant itself.</p>
                <div class="feed">
                  <div class="feed__row"><span class="feed__when">09:06</span><span>you</span><span class="feed__val">why are the low leaves going?</span></div>
                  <div class="feed__row"><span class="feed__when">09:06</span><span>PlantPal</span><span class="feed__val">draught, most likely</span></div>
                </div>
                <div class="btn-row">
                  <button class="stake" type="button">Ask something</button>
                  <button class="stake stake--quiet" type="button">Read the whole thread</button>
                </div>
                <p class="state__note">If it is not sure, it says so. It would rather be honest than confident.</p>
              </section>

            </div>`,
  "n-unknown": `<div class="n__recap"><p class="n__recap-line"><span class="tag tag--unknown">Unknown</span></p><p class="n__recap-note">Ten plants and their journals live here. Not fetched yet.</p></div>
            <div class="staleness"><span aria-hidden="true">◷</span> Never fetched</div>
            <div class="n__skel" aria-hidden="true"><div class="sk sk--row"></div><div class="sk sk--row"></div></div>
            <div class="n__full">
              <section class="state state--unknown">
                <div class="state__head">
                  <h4 class="state__title">Not fetched yet</h4>
                  <span class="state__id">region · unknown</span>
                </div>
                <p class="state__note">This is not an empty region — it is one we have not read. Ten plants, their journals and their problems are in here. Travelling in fetches it; the nodes already drawn will not move when it arrives.</p>
                <button class="stake stake--quiet" type="button">Fetch this region</button>
              </section>
            </div>`,
};
