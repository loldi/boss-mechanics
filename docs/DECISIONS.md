# Boss Mechanics — design decisions

Locked during the planning grill on 2026-07-31. Revisit deliberately, not accidentally.

## Architecture

1. **External plugin, no RuneLite fork.** Built from the example-plugin template,
   compiles against published `net.runelite:client` artifacts. RuneLite source is
   reference reading only.
2. **In-game UI is the product.** No sidebar-panel version. The injected button and
   the custom interface are the whole point.
3. **Own window, not embedded.** The injected "Boss Mechanics" icon button on a
   boss's collection log page opens our own custom-widget interface styled to match
   the game. We never squat inside the collection log's widget tree (Jagex scripts
   rebuild it constantly). Only the single injected button touches their interface.
4. **Build for Plugin Hub from day one.** Public repo, BSD-2-Clause, hub-compliant
   structure. Develop and playtest sideloaded; submit when the launch set feels good.

## Data

5. **Hand-curated, wiki-researched.** No runtime scraping — wiki strategy pages are
   prose, not structured data. One JSON file per boss (see `data/SCHEMA.md`).
   Paraphrase + attribute (wiki text is CC BY-SA 3.0).
6. **Bundled at launch.** Data ships inside the plugin. Remote fetch (GitHub raw)
   only becomes worth it when boss count grows and data PRs outpace releases.
7. **Launch bosses (5):** Abyssal Sire, Zulrah, Vorkath, General Graardor,
   Mad Angel. Chosen to span boss archetypes, not maximize coverage. Raids are
   explicitly out of scope for launch. Adding boss #6 must be a pure data PR.
8. **Schema splits `description` (what the boss does) from `counterplay` (what you
   do).** Counterplay is 1-2 terse imperative sentences. No damage numbers or max
   hits — they age badly.

## Discovery

9. **Discovered = your client witnessed the trigger** (animation / projectile /
   graphic / npc-spawn fired while a boss NPC is present). You don't have to get hit.
10. **Two-state model: revealed vs discovered.** "View All" is a reversible reveal
    toggle for reading; the progress bar always shows genuine discoveries. A misclick
    can't destroy the discovery game. (Provisional — validate feel in playtesting.)
11. **Per-character state**, stored via ConfigManager RS-profile keys.
12. **Undiscovered mechanics show as locked "???" rows**, not hidden.
13. **Discovery announces itself** with a collection-log-style chatbox message:
    "Boss mechanic discovered: X." No popups mid-fight.

## Presentation

14. **Animations render live from the game cache** via a model widget playing the
    mechanic's animation ID. Known limitation: shows the boss's body animation only —
    no projectiles/AoE/adds. Mechanics that don't read from the body animation set
    `staticFallback` in the schema.
    **VALIDATED 2026-07-31 (issue #1 spike).** The Abyssal Sire renders in a custom
    widget and animations play and loop. Pre-rendered captures are no longer needed.
    What the spike established, which the UI work depends on:
    - Parent the model widget to top-level interface **child index 1**. Index 0 has
      identical dimensions and silently never draws; picking a parent by size fails.
    - Zoom ~3000 frames a large boss; 1000 is too far out to read.
    - The Sire is a **single** cache model, so no multi-model compositing is needed.
      Verify this per boss during curation — it is not guaranteed for every NPC.
    - Looping animations loop indefinitely; **one-shot animations (e.g. death) play
      once and the model then disappears.** Previews must prefer looping animation IDs,
      or re-trigger one-shots.
    - `getCanvasLocation()` reads `(-1,-1)` for hand-created dynamic children even
      while they render. Never use it as a visibility check; use `getRelativeX/Y`.
    - The interface tree is not always ready when the widget is first built; retry
      until the build yields widgets.
15. **Plugin name: "Boss Mechanics".** Injected button is a native-styled icon with a
    tooltip (not a text label). Button appears only on bosses that have data.

## Later decisions

Decision numbers are stable identifiers cited from GitHub issues and code comments,
so new decisions are appended here rather than inserted in a themed section.

16. **Boss discovery is a `data/bosses/index.json` list, not classpath directory
    scanning.** **VALIDATED 2026-07-31 (issue #2).** Scanning is unreliable across
    sideload vs Plugin Hub classloaders; a hardcoded boss enum would violate D7
    ("adding a boss is a pure data PR"). A test reconciles the index against the
    real directory listing so a forgotten index line fails the build. Belongs with
    the Data decisions above (it is the mechanism that enforces D7).
17. **Detection trigger semantics, locked with the engine (issue #7).** Animation
    triggers only match when played by an NPC currently tracked as a boss's
    presence (source gating kills player-collision false positives entirely).
    NPC transforms (`NpcChanged`) count as npc-spawn triggers, not a separate
    trigger kind: Sire phase forms transform, they don't spawn. Graphics report
    no source actor, so they gate on boss presence only — curators must pick
    graphic ids players can't produce themselves. Detection state (which
    mechanics have been witnessed) is in-memory only until persistence lands
    in #8.
18. **Discovery/reveal persistence, state layer only (issue #8).** Supersedes D17's
    closing sentence: detection state is no longer in-memory only. One key per boss per
    concern, stored via `ConfigManager` RS-profile keys (D11), CSV-valued:
    `discovered.<bossId>` holds sorted mechanic ids; `revealed.<bossId>` (the reveal half
    of D10) is present with value `"true"` iff revealed and unset (not `"false"`) when
    not. Unknown/stale ids inside a discovered CSV are simply never matched, so renaming
    or removing a mechanic id never needs a migration — deliberate, since D10 is still
    provisional. Write-through on every new discovery; load is `DiscoveryState.reload()`,
    called at the end of `startUp` and on `RuneScapeProfileChanged` (not
    `GameStateChanged LOGGED_IN`, which fires before the profile key resolves). Reload
    always replaces, never merges, so switching characters can't leak one character's
    discoveries into another's. Reveal is per-boss and persisted, not a global toggle — a
    global reveal would flip bosses the player never opened. The read-modify-write CSV
    update in `addDiscovered` is safe only because every write happens on the client
    thread; if #5 later writes from a Swing/UI thread this assumption breaks and the
    read-modify-write needs a lock or a client-thread hop. Interface consumption of this
    state (View All button, progress bar rendering) is #5's, not built here.

19. **Collection log button injection (issue #4).** The hook is `ScriptPostFired` for
    `ScriptID.COLLECTION_DRAW_LIST` (2731), which is how RuneLite core's
    `ChatCommandsPlugin` reads the collection log. `WidgetLoaded` on group 621 was
    rejected as the primary hook: it fires once when the log opens, not on tab switch,
    boss selection or search click. The button is parented to
    `InterfaceID.Collection.COMBAT_ACHIEVEMENTS`'s own parent, which sidesteps D14's
    parent-picking problem entirely: a sibling that visibly draws proves that layer
    draws, so no size heuristic is needed. No anchor means no button. Gating on the
    collection-log tab varbit was rejected because bosses do not all live under the Boss
    tab (TzTok-Jad is under Minigames); the page-title lookup *is* the tab filter. That
    lookup is `BossPageIndex`: normalized (colour tags stripped, whitespace collapsed,
    case folded) but otherwise exact against `Boss.name`, and a page we can't match
    silently gets no button. Deliberately no `collectionLogPages` alias array in the
    schema: `BossPageIndex` is the seam if a page title ever disagrees with the display
    name. Teardown is `setHidden(true)` plus dropping the reference: there is no
    single-child delete, and `deleteAllChildren()` would destroy Jagex's own children.
    Duplicate prevention is an identity scan of `parent.getDynamicChildren()` rather than
    a `button != null` check, because a Jagex rebuild of the header drops our child
    without telling us; the scan self-heals in both directions and keeps this to one
    dynamic child per collection log open. The click is a `Consumer<Boss>` supplied by the
    plugin, so the real interface (#5) replaces one lambda and not this class.

    **Confirmed in game 2026-08-01, and load-bearing for #5:**
    - Interface geometry can be read offline. The cache is at
      `~/.runelite/jagexcache/oldschool/LIVE` and `net.runelite:cache` parses it, so any
      interface's component sizes, position modes and sprites can be dumped without a
      client. This found the right-aligned position mode below before it cost a test run.
    - The Combat Achievements button uses `xPositionMode 2`, so x is measured from the
      **right** edge and a larger x sits further **left**. Absolute-left maths sends a
      widget off the opposite side of the screen.
    - What that button looks like is not in the cache: it is a bare 50x25 layer whose
      children are built at runtime. It is a nine-slice frame (corners 913/914/915/916,
      edges 917/919 and 918/920) over a stretched background fill (297), with an 18x17
      icon at (16,3). Our button reuses all nine frame sprites, so only the glyph differs.
    - **Nested dynamic children render.** Our button is a dynamic `LAYER` whose own
      dynamic `GRAPHIC` children draw correctly. #5's window can therefore be built as a
      tree rather than as a flat pile of siblings with computed offsets.
    - The cache ships no boss-mechanics glyph. Every sprite that fits 18x17 is something
      unrelated (SAVE, LOAD, WORLDSWITCHER_FILTER), so a custom sprite bundled as a plugin
      resource is the eventual answer; `SPRITE_ICON` is the single constant to change.

20. **Boss Mechanics window (issue #5).** The window is a 500x314 panel (the collection log's own
    UNIVERSE size, 621 child 88) hosted on a **top-level root the client draws after GAMEFRAME**
    (see the correction at the end of this decision), not on anything inside the collection log,
    which is what keeps D3 intact. The first attempt used FLOATER, a later sibling of MAINMODAL under
    the same parent in all six top-level layouts (548, 161, 164, 165, 601, 80), so it draws on
    top of the log, and it is `w=0 h=0` with both size modes MINUS in all six, so it always
    resolves to full parent size and cannot clip a child to nothing. `TopLevelFloater` is the
    lookup, and it is unit tested, a useful correction to `CollectionLogButton`'s claim that
    nothing in `ui` is testable. `client.openInterface` was rejected (it needs a real cache group
    we cannot add, and no class in client-1.12.33 calls it); parenting into the log's own tree was
    rejected outright as a D3 violation.

    - **`com.bossmechanics.view` is a third RuneLite-free package.** `MechanicsView.of(boss,
      state, revealed)` resolves every row's final strings up front, so the interface positions
      rectangles and copies text and decides nothing. **The D10 invariant lives here**:
      `discoveredCount()` counts rows where `discovered`, and `locked` is the separate
      `!discovered && !revealed`. The interface never counts anything, so "View All" physically
      cannot inflate the progress bar.
    - **The progress bar is the Combat Achievements bar, script 4782**, rebuilt as its five
      children in draw order: inner border rectangle `0x474645`, empty track (sprite 3392, 1x27,
      tiled), fill (sprite 3391, tiled) at `done * (w-4) / total`, centred shadowed PLAIN_12
      label, outer border rectangle `0x0E0E0C`. Rectangle colours go through `setTextColor`.
    - **The scrollbar is built by hand, not by `runScript(31, ...)`.** Disassembling the injected
      client's `createChild` shows a dynamic child's `id` field is copied straight from its
      parent, so `Widget.getId()` on anything in our tree returns the FLOATER's component id.
      Handing that to Jagex's script 31 (or to `ScriptID.UPDATE_SCROLLBAR`) would make their
      script delete and rebuild the FLOATER's children, destroying our own window root. **No
      dynamic widget of ours can be addressed by a clientscript that takes a component id**,
      which is worth remembering for #6. The cache's own scrollbar sprites are reused (track 792, thumb
      789/790/791, arrows 773/788, all 16 wide), the arrows and the mouse wheel scroll, and the
      thumb indicates position without being draggable. Wheel rotation arrives as
      `ScriptEvent.getMouseY()`, which is how core RuneLite's bank tag tabs read it.
    - **Escape is consumed by the window.** One press closes the window, a second closes the
      collection log, matching nested game interfaces. The key listener is registered only while
      the window is open. Key events are AWT-thread, so the close hops to the client thread.
    - **Locked rows show no phase tag**, just `???`. Pure "???" is cleaner and makes the reveal
      deliver something.
    - **"View All" persists** per boss per character through `ProfileStateStore` (D18), because
      D10 calls reveal a reading mode rather than a momentary peek. Recorded here since D10 is
      still provisional. The write happens in a widget op listener, which is the client thread,
      which is the condition D18 named as necessary for that store's read-modify-write.
    - Teardown hides the root and empties it, but keeps the reference so reopening reuses the one
      dynamic child instead of stranding a hidden layer on the FLOATER every time; the reference
      is still dropped untouched on the transitions that destroy the interface tree, and
      re-checked by identity scan before reuse (D19).
    - The nine-slice frame is duplicated from `CollectionLogButton` on purpose. Sharing it means
      restructuring that class, which is a follow-up once #6 lands.
    - Row heights are fixed per state (22 locked, 68 discovered) because OSRS text widgets wrap
      but expose no wrapped height; scroll height is the sum of the actual row heights.
    - **CORRECTED IN GAME 2026-08-02: the host is UI_HIGHLIGHTS, not FLOATER.** The magenta
      backdrop earned its keep on the first run: the window drew *behind* the collection log.
      The cache reasoning above was wrong in a way only the runtime tree reveals. The log's
      actual parent chain is
      `GAMEFRAME(c34) <- c94 <- c15 <- FLOATER(c18) <- 621:c87 <- 621:c88`, i.e. **the
      collection log is opened onto FLOATER itself**, and FLOATER is nested inside GAMEFRAME
      rather than being its sibling. A dynamic child of FLOATER therefore sits under the
      interface node opened onto it.
      The client's actual root draw order was `c0, GAMEFRAME(c34), c35, c36, MOUSEOVER(c37),
      UI_HIGHLIGHTS(c98)`. Anything under a root after GAMEFRAME draws over the whole collection
      log. Of those, c35 is the 300-wide sidebar and c36 is 1x1, so neither can hold a 500x314
      window; MOUSEOVER and UI_HIGHLIGHTS are both full parent size. **UI_HIGHLIGHTS is drawn
      last and only carries occasional highlight overlays, whereas MOUSEOVER backs hover
      rendering**, so UI_HIGHLIGHTS is the one to squat. It exists in all six top-level layouts.
      D3 still holds: it is not the collection log's tree.
      The general lesson, which cost one run to learn: **cache sibling order does not predict
      runtime draw order.** Only `getWidgetRoots()` and a live parent chain do.
    - **CORRECTED 2026-08-02, second run: two claims above are now wrong (issue #39, D21).**
      The window drew on top of the collection log, as the host fix intended, but *beside* it
      rather than over it.
      (a) The size is no longer the log's own 500x314. It is 512x334, the Combat Achievements
      screen's size — see D21.
      (b) The correction above says UI_HIGHLIGHTS "is full parent size", and the layout code
      quietly turned that into "so `ABSOLUTE_CENTER` lands on the log". It does not.
      **UI_HIGHLIGHTS (161 c98) has parent `-1`: it is a root spanning the whole client canvas.**
      The collection log (621 c88, `ABSOLUTE_CENTER` on both axes) is centred inside `161 c15`,
      which is `250x165` with both size modes **MINUS** at `ABSOLUTE(0,0)` — the parent's width
      minus the 250-wide sidebar and its height minus the 165-tall chatbox. So the log's centre is
      `((W-250)/2, (H-165)/2)` while the host's centre is `(W/2, H/2)`, and our window landed
      **125px right and 82px below** the log.
      The delta is **not** a constant to hardcode: fixed mode (548) nests the log differently and
      has a different one. `WindowPlacement` therefore measures the log's real on-screen rectangle
      when the window opens (summing `getRelativeX/Y` up the parent chain, never
      `getCanvasLocation()`, per D14) and computes an ABSOLUTE origin per axis, which is unit
      tested. A `ClientTick` handler recomputes it so a client resize keeps the window on the log,
      writing only when the answer changed.
      The lesson to file next to the draw-order one: **a root's coordinate space is not the
      coordinate space of the thing you are trying to cover.**

21. **The Combat Achievements boss screen shape (issue #39).** The window is now 512x334 and
    covers the collection log instead of sitting beside it. That size is not arbitrary: 717 c0 is
    512x334, which is exactly the **fixed-mode viewport** (548 c10 is 512x334 at `(4,4)`). The
    Combat Achievements boss screen is reached from the collection log the same way ours is, fully
    covers it, and already has the two-column layout the original spec described, so it is the
    reference rather than a fresh design.

    - **D3 explicitly STANDS and is not being reversed.** Embedding into the collection log's item
      pane was reconsidered and **rejected on the record**: that pane is only **280x202**, so a
      200-wide list would leave 76px for the model preview. Combat Achievements is not drawn in the
      item pane either — it is its own screen. We still touch nothing inside group 621 but the one
      injected button.
    - **717's own inner offsets are the layout source**, scaled to our 494x316 content box (a
      512x334 outer with the nine-slice `FRAME` of 9). Measured from the content origin: title bar
      to y 39; progress bar y 39, height 33, `CONTENT_WIDTH - 18` wide and centred (717 c3, size
      mode MINUS 18); column header bands y 75, height 23; columns y 98 with a 6px bottom margin,
      so height 212. Left column 190 wide at x 6 (717 c6), right column 291 wide at x 6 measured
      from the **right** (717 c13, 277 wide against their narrower 480 frame). Column headers are
      BOLD_12 (font 496) in `0xFF981F`, which is already `Widgets.ORANGE`.
    - **Rows collapse to one line, 22 tall.** The detail moved to the right column, so a row is a
      name plus a right-aligned phase tag (omitted when locked, per D20's resolved fork), every row
      is the same height, and the scroll maths stops depending on unmeasurable wrapped text.
    - **Selection lives in the window, resolved by `com.bossmechanics.view.Selection`.** The window
      rebuilds itself wholesale on every "View All" flip, so the selection survives as an id, not a
      reference: keep it if the id is still on the list *and* the boss is the same (mechanic ids are
      only unique within a boss), otherwise take the first row. `MechanicsView` is untouched, and so
      are its 12 tests — selection is the window's state, not the view model's.
    - **A locked selection keeps the right column populated and dims it**, so the panel never
      reflows. The dim is a `RECTANGLE`, filled, `0x000000`, `setOpacity(150)` over the whole
      291x212 column body, created last so it covers the box, the text and whatever #6 later draws.
      This is script 4808's own idiom for a locked Combat Achievements entry
      (`cc_setfill` / `cc_setcolour 0` / `cc_settrans 150`). Opacity is INVERTED: 150 is
      translucent, not nearly-solid.
    - **The burger menu is DROPPED (fork, resolved).** In Combat Achievements it is a real menu with
      real entries; we have none, so shipping the glyph would promise something that does not exist.
      The second list tab and the book/globe buttons are dropped for the same reason.
    - **WIKI is cache sprites 2420 (resting) and 2421 (hover)**, both 40x14 and both literally
      reading "WIKI", right-aligned in the right column's header band. **The URL is opened
      plugin-side**: `BossMechanicsWindow.setOnWikiOpened(Consumer<String> bossId)` reports the
      click and `BossMechanicsPlugin.openWiki` calls `LinkBrowser.browse(boss.getWikiUrl())`, so
      `com.bossmechanics.ui` stays RuneLite-*interface* code and never imports a desktop-integration
      utility. Same seam as `setOnRevealToggled` and `setOnMechanicSelected`.
    - `previewContainer()` now hands #6 the **291x110 model box** at the top of the right column,
      and it is a LAYER rather than the border rectangle, because nested dynamic children are only
      known to render under a LAYER (D19). It stays valid across a selection change because the
      right column **mutates its text in place and never calls `deleteAllChildren()`** — only the
      window's own root is ever emptied.

22. **Three in-game bugs found playtesting PR #40's implementation of #39: first-open bar
    misplacement, a "View All" rebuild that loses the right column and scrollbar, and the fix
    both bugs share depends on.** Reproduced in `client.log`, 2026-08-02 09:07:30 (View All),
    09:29:01 and 09:29:04 (plain open): `MechanicsScrollbar.build()` throwing
    `ArrayIndexOutOfBoundsException: Index 99 out of bounds for length 99` inside
    `Widget.revalidateScroll()`, verified against the injected client's bytecode (1.12.33).

    - **`revalidate()` is immediate and self-only; it never recurses into children.** It lays out
      the receiver, right then, against its parent's *current* computed width/height. The three
      comments in `BossMechanicsWindow` and `MechanicsList` claiming "the parent layer runs the
      layout pass" were wrong, and cost the first bug: `open()` built every child of a fresh root
      (frame, header, progress bar, columns) *before* the root's own `revalidate()` ran, so every
      `ABSOLUTE_CENTER` and `ABSOLUTE_RIGHT` child computed its position against a 0x0 root — the
      progress bar rendered off the left edge, and the WIKI header band was off-window too, just
      unnoticed because it clips invisibly. A client resize fixed it permanently only because the
      client's own resize handler does a real recursive layout pass, which `Widget.revalidate()`
      does not. **Fix: lay out the root immediately after `place()`, before any child is built.**
    - **Dynamic children live flat on the static host component, not on their nominal parent, so
      `deleteAllChildren()` is a no-op on a nested dynamic widget.** `createChild` appends every
      dynamic child — ours or anyone else's under the same host — into the host's own flat array,
      linking the tree via `childIndex` fields rather than real parent/child storage.
      `root.deleteAllChildren()` only ever nulled `root`'s own (always-empty) array. Every rebuild
      — "View All", a boss switch while open, a close and reopen — therefore appended a full new
      copy of the window (~70 widgets) onto the host's array without ever freeing the old one
      (O(n²) growth). Once the flat indices reached the host interface's own component count
      (99 for `161:98`, our `UI_HIGHLIGHTS`), `revalidateScroll()` indexed past it and threw,
      aborting `open()` mid-build: the list's rows exist (built first), but the scrollbar, wheel
      listeners, `MechanicsDetail`, `root.revalidate()` and `select()` never run — which is why the
      right column and scrollbar vanished on the first rebuild and stayed gone on every one after.
      **Fix: a surgical delete.** `BossMechanicsWindow.deleteChildrenOf(host, root)` walks
      `root`'s own subtree via recursive `getDynamicChildren()`, then nulls exactly those
      identities out of `host.getChildren()` — the mixin returns the client's own live array, and
      nulling an entry by identity is what the client's own `cc_deleteall` does. `root` itself is
      never nulled (it is reused, not recreated, on the next open — D20), and nothing outside our
      subtree is ever at risk, because nothing outside it can be identity-equal to one of our
      widgets. The freed slots are reused by `createChild`'s append-after-last-non-null scan, so
      the array stops growing without bound.
    - **`Widget.revalidateScroll()` is forbidden on our tree, permanently.** Disassembly shows it
      indexes the STATIC group array of the host's top-level interface, but bounds that indexing
      with the DYNAMIC widget's own flat-array child-index watermark — an upstream RuneLite mixin
      bug, not something curation or geometry can avoid. It would have thrown even on a clean
      first open for any boss with roughly 15+ mechanics, so it had to go regardless of the leak
      fix above. `MechanicsScrollbar.build()` and `scrollBy()` no longer call it; `setScrollHeight`
      and `setScrollY` are unaffected and stay, since scrolling worked in game before #40 while
      `revalidateScroll()` provably touched none of our widgets.
    - **Third symptom, resolved as a non-fix.** The collection log itself is movable/resizable in
      some clients; the window is not, by design. The real Combat Achievements boss screen is also
      fixed-size (D21), and the window already re-centres over the log every tick via `place()`
      (D20), so a client resize keeps it correctly placed. No code change.
    - Pinned with two `Proxy`-backed tests rather than a live client, because neither bug depends
      on real widget geometry, only on which methods get called and in what order:
      `MechanicsScrollbarTest` asserts `revalidateScroll` is never called, from either the build or
      the wheel scroll it wires up; `BossMechanicsWindowLayoutTest` asserts a fresh root's
      `revalidate()` call precedes its first `createChild()`. Both use `java.lang.reflect.Proxy`
      implementations of `Widget` (and, for the window test, `Client`) that record method names
      rather than model real layout, and inject the fakes into `BossMechanicsWindow`'s `@Inject`
      fields by reflection. Each keeps to one assertion, deliberately, so neither ossifies the
      exact build order into a contract beyond the one invariant it exists to protect.

23. **The animated preview pane, promoting the issue #1 spike (issue #6).** Selecting a mechanic
    plays its animation, looping, on the boss model in the 291x110 model box; the issue #1 spike
    (D14) is deleted, along with its six dev-facing config items and plugin wiring.

    - **`PreviewSpec`, a fourth `view` package member, resolves mechanic -> (npcId, animationId,
      zoom, visible) once**, so `com.bossmechanics.ui` decides nothing about what a locked or
      `staticFallback` mechanic's preview looks like, the same seam `MechanicsView` and
      `Selection` already keep for everything else. `MechanicRow` carries it as a non-null field.
    - **FORK, resolved (Option A): a static pose always wins over a present `animationId` when
      `staticFallback` is true**, and a missing `animationId` always falls back to a static pose
      (never a crash) regardless of `staticFallback`. A locked row resolves to `PreviewSpec.hidden()`
      unconditionally, so a "???" row can never leak a mechanic through the model.
    - **`data/Preview` gains an optional `zoom`**, defaulting to 3000 (the spike-validated value)
      when absent. A new validator rule catches `staticFallback:false` (or absent) with no
      `animationId` at curation time rather than letting it silently render a static pose in game.
    - **Vorkath's previews now name `npcId: 8061` explicitly.** `npcIds[0]` for Vorkath is 8058
      (`VORKATH_SLEEPING_NOOP`), which `PreviewSpec`'s default-to-`npcIds[0]` rule would otherwise
      render instead of the boss the mechanics belong to. Abyssal Sire needed no change: its
      per-mechanic `npcId`s were already curated (D19's tentacle/respiratory/scion child npcs).
    - **`MechanicsDetail` owns one `MODEL` widget, created once in `build()` and mutated by every
      `show()`** (`setModelId` / `setAnimationId` / `setModelZoom` / `setHidden`, then
      `revalidate()`), never recreated. This is D22's rebuild-leak lesson applied deliberately: a
      fresh `MODEL` child per selection would both glitch the animation mid-play and pile up the
      same way the whole window used to. It is created between the model-box fill and the dim,
      which must stay last so it still covers the model along with the text. Rotation is fixed at
      0/0/0, the values the spike validated; both axes must stay within 0-2047 or the client
      crashes, which a constant zero trivially satisfies.
    - **npc -> model id is resolved by a lambda the window supplies**, `models[0]` from
      `client.getNpcDefinition(npcId).getModels()`, warning once per npc on a null/empty models
      array or on more than one model (D14's "verify this per boss" note; no launch boss has
      tripped it). This is the one client-API touchpoint issue #6 needed, and it stays isolated to
      `BossMechanicsWindow`, keeping `MechanicsDetail` itself just a widget mutator.
    - **`previewContainer()` and `MechanicsDetail.modelBox()` are deleted.** Nothing outside
      `MechanicsDetail` needs a handle on the model widget now that `MechanicsDetail` renders into
      it directly instead of handing it to a caller.
    - Pinned with two single-assertion `RecordingWidget`-based tests, in the D22 style: `show()`
      called twice with different unlocked rows creates zero new children anywhere in the tree
      (mutation, not recreation), and `show()` on a locked row hides the model widget. The second
      needed `RecordingWidget.lastArgsOf(widget, methodName)`, an additive per-widget last-call-args
      map alongside the existing name-only `calls()`/`listenerOf()`, so a test can tell the model
      widget's own `setHidden` call apart from the dim's.
    - One-shot animations (a death animation, say) still play once and then vanish per D14;
      curation prefers looping ids, and re-triggering a one-shot is explicitly out of scope here.

24. **Hard client crash from D23's single mutated model widget, found in the in-game pass of
    issue #6 (PR #43); corrects D23 for the animation axis specifically.** `client.log`,
    2026-08-02 18:29:45: `ArrayIndexOutOfBoundsException: Index 48 out of bounds for length 30`
    inside the client's widget-draw loop (injected client 1.12.33, `ga.rt` line 761 — the inline
    `while (widget.frameCycle > seq.frameLengths[widget.frame])` animation advance). Reproduced by
    selecting a long animation, letting it play a few seconds, then a shorter one: e.g. Sire
    minion-surge (7096, 60 frames) or apocalypse (7098, 67 frames) followed by miasma-pools (4531,
    30 frames) — the cache's own frame counts prove the counter outruns the shorter sequence.

    - **The engine fact, verified from injected-client bytecode: the animation frame counter lives
      on the widget, and `Widget.setAnimationId` never resets it.** The widget constructor
      (`createChild`) is the only thing that zeroes it; there is no RuneLite API that does. D23's
      "one MODEL widget, created once, mutated by every `show()`" therefore corrupts the counter
      the moment two different-length animations are shown on it in sequence — a code lifecycle
      bug, not a data bug. All Sire forms sharing model 29477 and all Vorkath forms sharing 35023
      made this an eventual certainty at launch, not an edge case: every future boss only adds more
      crash pairs. **D23's rationale still holds everywhere else** — mutating `setModelId` /
      `setModelZoom` / `setHidden` on a widget whose animation id is unchanged remains fine and is
      exactly what the fix below still does; only *swapping the sequence* on a live widget is
      forbidden, the same register as D22's `revalidateScroll()` ban.
    - **Fix: a pool of MODEL widgets keyed by animation id** (`PreviewSpec.NO_ANIMATION` included,
      for static-fallback poses), one child of the model box `LAYER`, created lazily on first use.
      A pool widget's `setAnimationId` is called exactly once, at creation, and never again;
      `show()` gets-or-creates the widget for the spec's animation id, hides whichever pool widget
      was previously visible, then mutates only `setModelId`/`setModelZoom`/`setHidden` on the new
      one. The pool is bounded by the distinct animation count for one boss (single digits in
      practice), and lives on the `MechanicsDetail` instance, which `BossMechanicsWindow` discards
      and recreates — widgets included — on every rebuild (teardown, boss switch, "View All"
      flip), so a stale pool entry can never outlive the widget it points at.
    - **FORK, resolved by Andrew: the POOL variant.** Re-selecting a mechanic whose animation was
      already shown reuses that pool widget and resumes wherever its frame counter currently sits —
      it does not restart the loop from frame 0. The rejected alternative, recreate-on-change
      (a fresh widget every time the *selection* changes, even back to an animation shown before),
      would side-step needing a pool at all but re-glitches the animation on every click, the exact
      symptom D23 built the single-widget mutation to avoid; resuming mid-loop is invisible for a
      looping animation and was judged an acceptable trade for a crash fix.
    - **Upstream `runelite-api` gap, noted for Plugin Hub review (#15):** there is no
      `Widget.resetAnimation()` or equivalent next to `setAnimationId`. If one is ever added
      upstream, the pool could collapse back to D23's single-widget shape; until then the pool is
      the sanctioned pattern for any widget that plays more than one animation id over its life.
    - Pinned by replacing `MechanicsDetailPreviewTest`'s
      `selectingADifferentMechanicMutatesTheModelRatherThanRebuildingIt`, which had pinned the bug
      (it asserted zero `createChild` calls across two *different* animations — exactly the
      corrupting sequence). Two tests took its place:
      `aModelWidgetNeverPlaysTwoDifferentAnimationsAcrossItsLifetime` walks every widget in the tree
      and asserts none ever receives `setAnimationId` with two distinct values, and
      `reselectingAMechanicWhoseAnimationWasAlreadyShownCreatesNoNewChildren` asserts the pool still
      does not grow without bound when the same animation id comes back around. Both needed
      `RecordingWidget.allArgsOf(widget, methodName)`, an additive per-widget all-calls-with-args
      log alongside the existing `lastArgsOf`, because telling "set once" apart from "set twice,
      same value" from "set twice, different values" needs the full call history, not just the most
      recent call. `lockedRowHidesTheModelWidget` is unchanged.

25. **Model preview oversizing, root-caused from the cache and injected-client bytecode after D24
    shipped (PR #44); corrects D14/D23's "zoom ~3000" rough guidance with a real fit recipe.**
    Playtesting every animated preview across both launch bosses showed each one rendering
    oversized, cropped by the 291x110 box rather than framed inside it — not a mis-anchor, a wrong
    zoom for that mechanic's own animation.

    - **The engine fact, verified from injected-client bytecode: an if3 MODEL widget's draw call
      auto-centers its above-ground bounds on the widget's own center, every single frame.** There
      is no anchor point to set on our side; the client always re-centers. Projected size on
      screen is approximately `units x 512 / zoom`, so "oversized" and "off-center" were always the
      same symptom read two ways — get the zoom right for the box and the centering takes care of
      itself.
    - **Upstream `runelite-api` gap.** The cache format carries `offsetX2d`/`offsetY2d` fields for
      a model widget, but the published API exposes no setter for either. The fix is therefore
      zoom and box geometry only; there is no offset knob to reach for.
    - **The fit-zoom recipe:** for every frame of a mechanic's full animation sequence,
      `zoom = 512 * max(2 * maxAbsX / 291, (heightAbove + 2 * heightBelow) / 140)`, then take the
      worst (largest) zoom needed across the whole sequence and add a 10% margin. `291`/`140` are
      the model box's own width/height (below), so the formula moves if the box ever does.
      Computed offline, per mechanic, by a `FitZoom` `main()` that reads the same cache the rest of
      curation does; it deliberately lives OUTSIDE this repo (a cachetool script, not shipped code,
      the same runtime/curation-tooling split D5 already draws for wiki research) — a curator runs
      it once and pastes the resulting number into the JSON. `data/SCHEMA.md`'s zoom row carries
      the formula itself so a future curator doesn't have to find this decision first. All 15
      preview blocks across Abyssal Sire and Vorkath now carry an explicit, recipe-derived zoom;
      `PreviewSpec.DEFAULT_ZOOM` (3000) stays only as the absent-field fallback, not a value any
      shipped mechanic actually relies on.
    - **FORK 1, resolved by Andrew: Option B, WIKI moved to the title bar.** It sat in
      `MechanicsDetail`'s own header band since D21; that band is now removed, and the WIKI button
      lives beside the close button in the window's title bar instead, freeing the 23px the band
      used to occupy. `MechanicsDetail`'s constructor drops both the header widget and the
      `onWikiOpened` callback it used to take; `BossMechanicsWindow.header()` builds the button
      directly, wired to the same `openWiki()`/`setOnWikiOpened` plugin seam D21 established.
      Pinned by `BossMechanicsWindowLayoutTest.wikiClickReportsTheBossId`, which drives the button
      through its recorded op listener and asserts the boss id reaches the seam — previously
      unpinned.
    - **FORK 2, resolved by Andrew: accept Vorkath's wide aspect.** The model box stays a fixed
      291x140 for every boss rather than growing width per npc to fit a naturally wide silhouette
      (Vorkath's wingspan); a wide boss instead gets a wider (weaker) zoom, same recipe, same box.
      Grown from 291x110 to 291x140: Fork 1's now-removed header band freed 23px, and all of it
      went to the box rather than being split between box and text. `MechanicsDetail.COLUMN_HEIGHT`
      (235) and `MODEL_HEIGHT` (140) are both package-visible so
      `MechanicsDetailPreviewTest.textBlockFitsInsideTheColumn` can assert the name/description/
      counterplay block still fits inside the taller box's leftover space without a live widget
      tree, and `modelPoolWidgetMatchesTheModelBoxSize` asserts every pool widget's own
      `setOriginalWidth`/`setOriginalHeight` still match the box exactly — the engine only
      re-centers correctly when a widget's own declared size matches the frame it's meant to be
      centered in, so a stale size would mis-center silently rather than crash.
    - **Known limitation, not a bug: a tall animation visibly bobs a few (~10px) pixels vertically
      as it plays.** Because the engine re-centers the above-ground bounds every frame (above), and
      a sequence's silhouette height changes frame to frame (a windup crouch vs. an overhead
      strike, say), the model's vertical position shifts with it. There is no RuneLite API to pin a
      model to a fixed baseline instead of a per-frame center, so this is accepted as engine
      behavior and is explicitly not something a future PR should try to "fix" — the fit-zoom
      recipe above already accounts for the worst-case frame so the bob stays small, it does not
      eliminate it.

26. **Vertical anchor correction, idle-loop statics, and Combat Achievements section styling, from
    Andrew's live screenshots after D25 shipped; corrects D25's centering claim specifically.**
    D25 said "an if3 MODEL widget's draw
    call auto-centers its above-ground bounds on the widget's own center, every single frame."
    **That line is wrong.** The screenshot Andrew compared against was group 713 (`CA_BOSS`), not
    717 (`CA_OVERVIEW`, which genuinely has no model) — an obfuscated goto-graph in the bytecode
    read earlier had misled which branch guarded which centering path. Decisive evidence: 713's
    model box is built by clientscript 4842 — `cc_create(MODEL)` -> `cc_setmodel(param 1322)` ->
    `cc_setmodelanim(param 1323)` -> `cc_setmodelangle(params 1324..1329)` = `(offsetX, offsetY,
    rotX, rotY, rotZ, zoom)`, all per-boss struct values. Abyssal Sire (struct 3569): model 29477,
    anim 4533, `offsetY=300`, `rotX=97`, `rotY=176`, zoom 3000, in a 156x106 box. Vorkath (struct
    3576): dedicated display model 42781, anim 7948, `offsetY=280`, `rotX=25`, zoom 2500. If the
    engine auto-centered, Jagex would not curate an `offsetY` per boss for roughly 60 of them.
    **The real anchor: the model's ground line (y=0) sits at the widget's vertical center, body
    extending upward** — exactly what Andrew's screenshot showed (the top half of the box occupied,
    an empty band below).

    - **Upstream `runelite-api` gaps, noted for Plugin Hub review alongside D24's (#15):**
      `offsetY2d` has no setter (D25 already found this), and there is no standing/idle-animation
      getter on `NPCComposition` — both are cache facts Jagex's own clientscripts read directly
      that this plugin cannot, so idle animation ids and the anchor correction below are curated
      data, not code that reads them off the npc.
    - **Our compensation: move the widget rect, not the model.** We cannot set `offsetY2d` or add
      an anchor point; we can move the pool widget's own geometry. Growing a pool widget downward —
      `setOriginalY(0)` unchanged, `setOriginalHeight(MODEL_HEIGHT + 2*shiftY)` — moves its vertical
      center down by `shiftY` pixels with zero tilt, using only the rect setters D24 already
      permits (`setAnimationId` is the only banned mutation on a live pool widget; rect mutation is
      not, and rect mutation is the entire mechanism). `shiftY` is a new `Preview`/`PreviewSpec`
      field (`Integer`/`int`, default 0 when absent, resolved the same null-means-default way as
      `zoom`), applied in `MechanicsDetail.showModel` on every `show()` rather than only at
      pool-widget creation, since two specs could in principle share a pool entry (same animation
      id) but curate different `shiftY` values. `modelPoolWidgetMatchesTheModelBoxSize` (D25) is
      amended, not deleted, to assert the new height formula and the pinned y origin — it still
      catches the same box-size drift it always did.
    - **The fit-zoom recipe (D25) is unaffected.** `reqY`'s formula already treats a frame's
      below-ground extent as needing double the room (to stay conservative under the wrong
      centering assumption), which in the common case (`maxY <= 0`, no below-ground vertices) is
      already exactly the ground-anchored requirement; existing curated zoom values did not need
      recomputing. `shiftY` is a new, separate quantity: `shiftY = -(minY + maxY) / 2` across the
      same per-frame extents `zoom` is computed from (the model's own combined vertical envelope
      across the whole animation, or the static pose). The cachetool's `FitZoom` now emits both
      numbers together per npc/animation pair; all 15 preview blocks across both launch bosses
      carry a curated `shiftY`, re-verified by rerunning `FitZoom` against the live cache rather
      than trusting hand estimates (they landed within roughly 1%).
    - **Open hedge, to resolve on the next in-game pass:** whether the *animated* draw path shares
      this ground-line anchor or actually centers on animated bounds (D25's original, now-suspect
      claim) was not re-confirmed in game for this change — every shipped animated preview from PR
      #45 is itself the untested probe, since D25 shipped before this correction existed. All 15
      `shiftY` values above are baked assuming the ground-line anchor applies uniformly (static
      and animated alike), which is what the cache/script evidence supports. **If the in-game pass
      shows animated previews sitting too low (over-corrected)**, the animated anchor is the old
      bounds-centering hypothesis after all, and the fix is a one-command `FitZoom` re-bake of
      `shiftY` to 0 for animated previews only (statics keep theirs) — a data-only follow-up, no
      code change, since the mechanism is identical either way and only the values would differ.
    - **T-pose fix: `staticFallback` previews become idle loops.** The Combat Achievements screen
      always plays an animation; `staticFallback: true` was rendering the model's raw, unposed bind
      pose — a T-pose for most humanoid npcs — because a static pose is drawn without any animation
      applied at all. `data/SCHEMA.md`'s `staticFallback` row now says to prefer the npc's own
      standing/idle animation, curated the same way every other `animationId` is (there is no API
      to read it off the npc, per the upstream gap above), and to reserve a true static pose for an
      npc with no usable idle. Vorkath's five `staticFallback` previews (deadly-dragonfire,
      zombified-spawn, venomous-dragonfire, corrupting-dragonfire, acid-phase) now share anim 7948
      (Jagex's own idle, wings folded, ~127px tall looping instead of a 52px T-pose pancake), and
      the Abyssal Sire's tentacle-guard (anim 7106) and scions (anim 7125) previews likewise
      convert. Respiratory-systems keeps `staticFallback: true` (idle -1, an 8-vertex blob with no
      T-pose to fix). `staticFallback` itself is untouched as a schema field and remains legal — no
      validator or loader change, since every converted mechanic is now an ordinary animated
      preview from the loader's point of view.
    - **Combat Achievements section styling, matching D21's screen shape with D21's own measured
      recipe.** 713's statics (children 17-21, 31-35) show each section — the list column, the
      model box, and the text area — framed the same way: a 1px `0x303030` outer line, a 1px
      `0x5D5848` inner line one pixel in, and the section's own content starting 2px from its
      outside edge. `Widgets.sectionBorder(parent, x, y, width, height)` draws both lines as the
      last sibling added to a section, the same "draw the border after the content" idiom the
      model box's single border already used, so a rebuilt row or a playing model can never draw
      over its own frame. Applied to `MechanicsList`'s row column, `MechanicsDetail`'s model box,
      and a new bordered band behind the name/description/counterplay text block. The model box's
      flat `0x0E0E0C` fill / `0x474645` outline is replaced with sprite 1040, tiled, at opacity 40
      (RuneLite opacity is inverted, so 40 is mostly opaque, not barely) — also measured from 713.
      D22's build order is unaffected: the dim still goes on last, and every border widget is
      created during `build()`, never during `show()`, so no rebuild-time leak is possible. Purely
      visual, so this is verified in game rather than with color-pinning unit tests, matching
      D21's own precedent for the dim rectangle and the nine-slice frame.

## Open questions

- Mad Angel is the newest boss; wiki/community documentation of its IDs may be thin.
  Curate it last.
- UX feel of revealed-vs-discovered needs playtesting (decision 10 is provisional).
- ~~Exact sprite for the injected button.~~ **RESOLVED (issue #4):**
  `net.runelite.api.gameval.SpriteID.ICON_SWORDS` (3029), a pair of crossed swords, as the
  closest stock combat glyph to the Combat Achievements trophy it sits beside. One
  constant, so swapping it after seeing it in game is a one-line change.
