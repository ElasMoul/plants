# PlantPal Design Implementation Progress

## Design Tokens (extracted from 6 screens)
```
--color-primary:           #1a3c2a   (deep forest green — headings, buttons, toolbar text)
--color-primary-light:     #4e8c64   (sage green — accents, chips)
--color-background:        #f2ede6   (warm cream — page bg)
--color-surface:           #ffffff   (white — cards)
--color-surface-secondary: #e5efe9   (light sage — secondary chips, care tip bg)
--color-text-primary:      #1a2e20   (dark heading)
--color-text-secondary:    #7a8c80   (grey subtitle)
--color-error:             #e05252   (coral — ISSUES/URGENT badges)
--color-warning:           #f0a030   (amber)
--color-success:           #5bbf6e   (bright mint — HEALTHY badge)
--radius-card:             16px
--radius-chip:             50px
--shadow-card:             0 2px 12px rgba(0,0,0,0.08)
--font-heading:            'Lora', Georgia, serif
--font-body:               'Inter', 'Roboto', sans-serif
```

## File Checklist
- [x] index.html — add Lora + Inter Google Fonts
- [x] styles.scss — tokens + mat theme (green-900 primary, light-green-400 accent)
- [x] app.component.ts — navLinks: "Garden" icon=local_florist, "Identify" icon=document_scanner
- [x] app.component.html — removed color="primary" from toolbar, pill bottom nav
- [x] app.component.scss — cream toolbar, pill active bottom nav
- [x] plant.model.ts — added healthStatus? + nextWaterDays? optional fields
- [x] plant-list.component.html — "My Garden" heading + FAB + subtitle
- [x] plant-list.component.scss — grid, empty state, FAB
- [x] plant-card.component.html — health badge overlay, next water chip, menu
- [x] plant-card.component.scss — new card design with tokens
- [x] identification-page.component.html — analyzing state redesign
- [x] identification-page.component.scss — analyzing styles
- [x] photo-upload.component.html — upload idle state redesign
- [x] photo-upload.component.scss — upload styles
- [x] preview-card.component.html — results redesign
- [x] preview-card.component.scss — results styles
- [x] plant-detail.component.html — hero + care tab redesign (already done on disk)
- [x] plant-detail.component.scss — hero/tabs/status-banner styles with tokens
- [x] care-card.component.scss — tokens applied (radius-card, shadow-card, surface-secondary tip)
- [x] care-plan.component.scss — tokens applied (summary bar, warnings, skeleton)
- [x] reminder-list.component.html — FULL STATIC UI built (ts mock data + html, groups: today/tomorrow/nextWeek, custom check-toggle, FAB)
- [x] reminder-list.component.scss — reminder styles
- [x] chat-home.component.html — FULL STATIC UI built (ts mock messages, header, bubbles, probability chip, care tip, quick chips, input bar)
- [x] chat-home.component.scss — chat styles
- [x] chat.module.ts — added FormsModule for ngModel on chat input (not in original checklist but required)
- [x] login.component.scss — cream bg, centered card header (icon/title/subtitle stacked via ::ng-deep), pill submit button, sage link
- [x] register.component.scss — same auth theme treatment as login
- [x] plant-form.component.scss — radius-card/shadow-card on form-card, font-heading title, pill submit button
- [x] shared.module.ts — added MatCheckboxModule + FormsModule (exported); removed now-redundant explicit FormsModule import from chat.module.ts
- [x] model-selector.component.scss — toggle group restyled as dark-green pill (var(--color-primary) bg, white text, translucent-white checked state) for cream toolbar

ALL FILES COMPLETE.

## Key Design Notes Per Screen

### Toolbar
- Cream bg (#f2ede6), NOT green. Remove color="primary". ✅ DONE
- "PlantPal" dark forest green bold brand, local_florist icon
- Right: model selector pill + user menu

### Bottom Nav
- White bg, 4 items: Garden/Identify/Reminders/Chat
- Active = large mint green oval pill behind icon+label ✅ DONE
- Icons: local_florist / document_scanner / notifications / chat_bubble_outline

### Garden (plant-list)
- "My Garden" in large heading (font-heading)
- Subtitle: "You have N plants under your care."
- 2-column grid mobile, auto-fill desktop
- FAB (+) fixed bottom-right, dark green

### Plant Card
- Full-bleed photo top, height 200px
- Health badge (HEALTHY=mint/ISSUES=coral) overlay top-left of photo
- Name: bold dark font-heading, species: italic grey small
- Next water chip: water_drop icon (only if nextWaterDays present)
- Large border radius 16px, shadow-card
- Clicking photo navigates to detail

### Analyzing State
- Full cream page, no mat-spinner
- Plant icon in mint green circle
- "Identifying species…" serif heading
- Animated progress bar (CSS) + percentage counter
- 3 status chips (Image Quality / Lighting / Neural Analysis)

### Identification Results (preview-card)
- Photo annotator full-width at top
- Species name: large serif heading below photo
- Common name italic subtitle
- Health badge row (HEALTHY / AFFECTED)
- DIAGNOSIS section: coral badge + white card
- Buttons: "Ask for cure" (outline) + "Add to plan" (filled dark green)

### Plant Detail
- Hero: full-width photo, gradient overlay, SPECIES small-caps + name overlaid
- Tabs: OVERVIEW / CARE HISTORY / LAST SCAN (underline active style)
- Care cards 2×2 grid with colored icon circles
- Current Status section with ATTENTION NEEDED badge

### Reminders
- "Care Schedule" heading + subtitle
- Groups: TODAY (red dot) / TOMORROW / NEXT WEEK
- White cards: checkbox + care icon + plant name + species + badge
- FAB (+) dark green, fixed bottom-right

### AI Chat
- Header: bot avatar + "PlantPal AI Assistant" + green dot "EXPERT MODE ACTIVE"
- User bubbles: dark forest green, right-aligned, white text
- AI messages: white card, left-aligned, bot avatar circle
- PROBABILITY chip at bottom of AI message (mint bg)
- PlantPal Care Tip: light green bg card, lightbulb icon
- Input bar: (+) | text field | camera | send (dark green circle)
- Quick chips below input

### Auth
- Cream background, white card centered (radius-card)
- PlantPal plant icon + "PlantPal" heading centered at top of card
- Full-width pill submit button (dark green)
- Sage green text link for secondary action

## Status: DONE — full design implementation complete across all 28 checklist files.
