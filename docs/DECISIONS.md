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
      recomputing. `shiftY` is a new, separate quantity, and — like `zoom` itself — needs a model-
      units-to-pixels projection: `shiftY_units = -(minY + maxY) / 2` across the same per-frame
      extents `zoom` is computed from (the model's own combined vertical envelope across the whole
      animation, or the static pose), then `shiftY = round(shiftY_units * 512 / zoom)` using the
      preview's own zoom, the same `units * 512 / zoom` projection the fit-zoom recipe already
      uses. **The baked value is in pixels, not model units** — `MechanicsDetail.showModel`
      consumes it directly as `MODEL_HEIGHT + 2*shiftY` inside the 140px box, so a correctly
      projected value can never exceed 70 (half the box); `BundledBossDataTest` now guards this.
      The cachetool's `FitZoom` emits `shiftY_units` (the model-unit quantity, not yet projected)
      alongside zoom; a curator does the `* 512 / zoom` conversion before pasting the value in, the
      same way `zoom` itself already needed no such step because `FitZoom` projects zoom for you.
      All 15 preview blocks across both launch bosses carry a curated, pixel-projected `shiftY`,
      landing between 35 and 69px — comfortably inside the 70px ceiling.
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

27. **Text inset fix, full steel Combat Achievements chrome, and the preview upgrades that came out
    of curating the last two launch bosses (issues #6/#15 follow-ups).** Six slices: a text-geometry
    bug fix, a chrome upgrade, curated data tweaks, a `modelId` override, bundled sprite previews,
    and secondary models.

    - **G1, text inset (bug).** `MechanicsDetail.text()` placed every name/description/counterplay
      widget at `x=0`, width 291, while `Widgets.sectionBorder` draws its two 1px outlines at `x=0`
      and `x=1` *after* the text — overdrawing the first two glyph columns of every line. The
      border's top also coincided with the name's own top row. Fix: the text area's own section
      border moves to `y=140` (one pixel above the old `NAME_Y`), all three text widgets get
      `x=4`, `width=283`, and `NAME_Y=143`/`DESCRIPTION_Y=159`/`COUNTERPLAY_Y=197` (all h=36 except
      the 15-tall name) keep the block ending at the border's own interior (233), not just the raw
      column height (235).
    - **G2, full steel CA chrome (fork resolved: full, not the minimal option first proposed).**
      Recipe read from clientscripts 228 (frame/title), 4769 (close button) and 4836 (title text):
      background sprite 297 stretched with a 1px inset; corners 310/311/312/313 (25x30); edges
      172/173/314/315 (36 thick) tiled, straddling the logical window's border with a 15px overhang
      on both sides (script 228's own −15 offsets) rather than sitting inside it; no filled title
      band at all, the title text (BOLD_12, `0xFF981F`, shadowed, centred) sits directly on the
      steel background; close button is sprites 2289/2290, 21x21 at (7,7) from the top-right. The
      overhang is solved by growing the root, not by clipping or insetting: a new `CHROME = 15`
      constant makes the root 542x364, `WindowPlacement` still computes the unchanged 512x334
      logical placement, and the root's own applied origin subtracts `CHROME` on both axes
      (`WindowPlacement.withChrome`, pure arithmetic, unit tested). The root itself no longer
      carries `setNoClickThrough`; a new exactly-512x334 `window` LAYER at `(CHROME, CHROME)` inside
      it carries that instead, so the 15px gutter around the window still passes clicks to the game
      world. `header()`/`progressBar()`/`columns()` parent to that inner layer at coordinates
      unchanged from before this decision; the chrome sprites are direct children of the enlarged
      root, built (in root-local coordinates) before the inner window layer, so the window's own
      content still draws over the frame's inward bleed, matching the "frame, then content" order
      the old nine-slice frame already used. `Widgets.frame` (913-920) is untouched and still used
      by `CollectionLogButton`; the window no longer calls it.
    - **Data tweaks (no code).** `spawn-summon` shiftY nudged up 10px (62→52); `apocalypse`
      zoom/shiftY tightened to a 0-margin fit (5350/43→4875/47); `deadly-dragonfire` and
      `acid-phase` (Vorkath) moved off the shared idle animation (7948) back onto their own
      mechanic animations (7960, 7957 respectively) with fit-zoom-recipe zoom/shiftY (3250/59 for
      both) — both sequences have the same proven cache loop-shape as the other looping previews
      already shipped, so the one-shot-vanish risk (D14) is low.
    - **The Vent finding.** `respiratory-systems` (Abyssal Sire) detects on npc-spawn 5914, but
      that npc ("Respiratory system") is an 8-vertex, group-less clickbox placeholder with
      `standingAnim=-1` — genuinely nothing in the cache can animate it. The thing that actually
      breathes in the fight is npc 5915 ("Vent", model 29429, standing anim 7103), confirmed against
      the cache's own `NpcID` gameval names. The preview now targets 5915/7103; detection correctly
      stays on the 5914 spawn trigger, since that is the id the game actually spawns. **The preview
      npc need not be the trigger npc** — curate whichever variant actually carries the visible
      model/animation (data/SCHEMA.md's preference-ladder section says this explicitly now).
    - **`preview.modelId` override (code).** `Preview`/`PreviewSpec` gain an optional explicit cache
      model id. When present, `MechanicsDetail`'s model-widget resolution uses it directly and never
      calls the npc→model lookup lambda at all — some models (a base spotanim model, a secondary
      model) have no npc to look them up from. No boss data sets it directly yet; secondary models
      (below) are the first real consumer, through their own modelId/npcId field.
    - **Bundled sprite previews, replacing an earlier plan to render distinctly-coloured 3D
      projectiles.** Investigated first: the entire dragonfire family — regular, toxic, ice,
      lightning, venomous, corrupting — shares one base model (17550) and animation (1990); colour
      is entirely a spotanim-level recolor table, which the `Widget` API has no path to apply (full
      surface enumerated for the #15 upstream-gap list: `setModelId` takes a cache id only,
      `setModelType` is one of `NULL`/`MODEL`/`NPC_CHATHEAD`/`LOCAL_PLAYER_CHATHEAD`/`ITEM`/`PLAYER`/
      `NPC_INDEX_CHATHEAD`, and `client.loadModelData(id).recolor(...)`'s output only ever attaches
      to a scene `RuneLiteObject`, never a widget). Evidence, read directly from the cache
      (`SpotAnimDefinition`): every dragonfire variant shares `recolorToFind = [3755, 6055, 5931,
      5807, 5935]` against base model 17550/anim 1990, and only `recolorToReplace` differs per
      variant — venomous (spotanim 1470) replaces with `[26512, 26386, 26388, 26264, 25114]` (deep
      greens), corrupting (1471) with `[56105, 56233, 56107, 56235, 57261]` (pinks — corrupting
      dragonfire is pink in game, not white, correcting an earlier assumption), and the ice/freeze
      variant (396) with `[43955, 43709, 43592, 43474, 43228]` (icy blues). With no runtime path to
      recolor a widget's model, Andrew exported the three variants from the cache himself with each
      one's recolors applied and supplied color-true rendered images. `Preview` gains an optional
      `sprite` field (a bundled resource name); the preference ladder is now **sprite > modelId >
      npcId+animationId > the npc's own idle > a raw static pose (`staticFallback`)** — when
      `sprite` is present every model field is ignored, no validator error. `venomous-dragonfire`,
      `corrupting-dragonfire` and `zombified-spawn` (Vorkath) now use their curated sprite images.
      **Sprite pipeline**: `client.createSpritePixels`/`getSpriteOverrides`/`getWidgetSpriteCache`
      and `ImageUtil.loadImageResource`/`getImageSpritePixels` all verified present and unchanged in
      runelite-api/-client 1.12.33. `BossMechanicsPlugin.startUp` scans loaded boss data (never the
      classpath, D16) for distinct `preview.sprite` names, loads each bundled PNG via `ImageUtil`,
      and registers it in `client.getSpriteOverrides()` under a reserved negative id
      (`SPRITE_BASE = -3_517_000`, allocated downward); `shutDown` removes exactly the ids it added
      and calls `client.getWidgetSpriteCache().reset()`. The plugin hands `BossMechanicsWindow` a
      `name -> id` lookup lambda (`setSpriteIdForName`, same seam style as `modelForNpc`), which
      forwards it to `MechanicsDetail`, keeping `com.bossmechanics.ui` `ImageUtil`-free.
      `ImageUtil.getImageSpritePixels` flattens to RGB where pixel value 0 is transparent at draw
      time, so every bundled PNG must avoid opaque `#000000` (bump to `#010101`) or it becomes a
      hole; each is exactly 287x136 (the model box's own interior inside its 2px section border)
      with binary alpha (fully opaque or fully transparent, no soft edges). The sprite widget is a
      GRAPHIC child of the model box, pool-keyed by resolved sprite id exactly like the model pool
      is keyed by animation id — `setSpriteId` is called once, at creation; GRAPHIC widgets carry no
      frame counter so D24's ban doesn't technically bind, but the same set-once shape was kept for
      consistency rather than reasoning about a second pattern. **Open, to confirm in the live
      pass**: sprite overrides live in a RuneLite-side map, not game state, so they are expected to
      survive a world hop without re-registering — if the live pass shows otherwise, the fallback is
      a one-line re-register on `GameStateChanged HOPPING`/`LOGIN_SCREEN`, mirroring the pattern
      `DiscoveryState.reload()` already uses for its own post-transition rebuild (D18).
    - **Secondary models (code + data, shape (a)).** `Preview` gains an optional `secondary` object
      (`modelId|npcId, animationId, zoom, shiftX, shiftY`) for the two demonstrated two-model cases:
      Miasma Pools (the Sire's body plus the pool's own model, spotanim 1275 → model 29475/anim
      7115) and Scions (the grown scion plus the spawn npc — 5916, anim 4524 — it matures from). The
      rejected alternative, a `models[]` array, would have restructured every existing preview for a
      case that only ever needs two. The primary preview also gains `shiftX`, a horizontal analogue
      of D26's vertical `shiftY`: the pool widget's rect grows by `2 * abs(shiftX)` and its origin
      moves by `shiftX - abs(shiftX)`, so the model's own centre moves sideways without the rect ever
      losing coverage of the box. `MechanicsDetail`'s single model pool is refactored into a small
      `ModelSlot` (pool-plus-visible-widget) used identically for the primary and a new, independent
      secondary slot, so the two never share a widget even when they curate the same animation id —
      the existing tree-wide invariant (no widget ever receives `setAnimationId` twice) guards both.
      `miasma-pools` ships `shiftX: -50` (primary) / secondary `{modelId:29475, animationId:7115,
      zoom:1100, shiftX:90, shiftY:33}`; `scions` ships `shiftX: -60` / secondary `{npcId:5916,
      animationId:4524, zoom:650, shiftX:70, shiftY:50}` (its zoom is close to the spawn's own
      curated zoom, so the pair reads at near-true relative scale). Layering between the two is
      pool-creation order (primary first); they are curated not to overlap once shifted apart, not
      enforced by any z-index mechanism.

28. **In-game live-pass fixes from PR #47's shell/chrome/preview work: the wrong close button,
    Vent's real breathing animation, a recolor-table rule for model previews, per-preview
    rotation, and a real text scroll box.** Five findings, one slice each; the movable/resizable
    window Andrew also flagged is deliberately deferred to issue #48, not built here.

    - **The close button was the Combat Achievements screen's BURGER menu, not its close button.**
      Script 4769 sets `if_setop(1, "Show Menu")` on sprite 2289 — that is CA's burger, which D21
      explicitly dropped as having no menu of our own to promise. It was copied here by a
      cache-reading mistake, not a design choice. The real close button is script 228's own
      sprites 535 (resting) / 536 (hover) — RuneLite's own `WINDOW_CLOSE_BUTTON`/`_HOVERED` gameval
      names — 26x23 at (3,6) from the logical top-right. `BossMechanicsWindow`'s `WIKI_X` already
      derives from `CLOSE_WIDTH`, so it self-adjusted with no further change.
    - **The Vent finding extends D27's preview-npc-need-not-be-the-trigger-npc principle one step
      further: the animation itself may live on a different cache entity than the one being
      previewed, provided they share a model.** D27 already moved `respiratory-systems`'s preview
      off the unanimatable spawn npc (5914) onto npc 5915 ("Vent", model 29429). What D27 missed:
      anim 7103 (Vent's own `standingAnim`) is a **one-frame sequence**, and model 29429 carries
      zero vertex groups — no in-game motion is possible from that pair at all, one-frame sequences
      being static by definition. What actually breathes in the fight is scenery **object 26953**,
      also literally named "Vent", sharing the same model 29429 but playing **anim 7105**: 12
      frames, 60 cycles, looping, whose face labels (max index 26) exactly cover the model's 27
      transparency groups — a genuine alpha pulse. The fix stays entirely inside the existing
      `npcId`/`animationId` fields: `respiratory-systems`'s preview keeps `npcId: 5915` (the model
      lookup only needs a model, and 5915's model **is** 29429) but its `animationId` moves to
      7105, borrowed from the object that actually plays it. No schema or code change was needed —
      an animation id is just a sequence to apply to whatever model is loaded, with no enforced tie
      to the entity gameval names suggest it "belongs" to. Re-baked with the cachetool's `FitZoom`
      against the corrected animation: zoom 1267 → 1866, `shiftY` unchanged at 64.
    - **The recolor-table rule (extends D27's sprite-preview precedent): an npc whose recolor table
      covers its entire model cannot be model-previewed colour-true — bundle a sprite instead.**
      Vorkath's zombified spawn (npc 8063, model 35025) is the case: its recolor table replaces
      100% of the model's faces, and D27 already established there is no Widget API path to apply
      an npc's own recolor table to a live model widget (`setModelId` takes a bare cache id;
      `client.loadModelData(id).recolor(...)`'s output only ever attaches to a scene
      `RuneLiteObject`, never a widget). The three replacement colours are `30123`/`30238`/`29590`
      (`#307B69`/`#1A5D4D`/`#02553A` — the icy-teal zombie skin), confirming a raw model preview
      would have rendered whatever base texture colour ships in the cache, not this. The composite
      sprite D27 already called for is now shipped (icy breath flying in from the left into the
      teal spawn on the right, 287x136, binary alpha, no opaque `#000000`), replacing the earlier
      placeholder with no data or code change (same filename).
    - **D27's open world-hop hedge is resolved: custom sprite overrides CONFIRMED surviving a world
      hop, live pass 2026-08-02.** `venomous-dragonfire`/`corrupting-dragonfire`/`zombified-spawn`
      kept rendering correctly after a world hop with no re-registration observed, so the mirrored
      `GameStateChanged HOPPING`/`LOGIN_SCREEN` fallback D27 flagged is not needed. This line exists
      so the question does not get re-litigated: sprite overrides live in a RuneLite-side map, not
      transient game state, exactly as expected.
    - **Per-preview rotation (`Preview`/`SecondaryPreview` gain optional `rotationX`/`rotationY`/
      `rotationZ`).** D23 fixed rotation at a constant 0/0/0 (what the issue #1 spike validated);
      Miasma Pools' secondary (the pool model) needed 90° (`rotationX: 512`, of 2047 per full turn)
      so its top faces the viewer, which the constant made impossible. Resolved the same
      null-means-default way as `zoom`/`shiftX` (`view.PreviewSpec`/`SecondaryPreviewSpec` gain
      resolved `int` getters), validated 0-2047 by `BossDataValidator` — a value outside that range
      crashes the client (D23) — and applied in `MechanicsDetail.ModelSlot.show()` on every
      `show()` rather than fixed at creation in `create()`: rect and rotation mutation on a live
      pool widget is D24-legal, only `setAnimationId` is banned there, and rotation is not it.
      **Live-pass tunable, flagged in the PR body:** `FitZoom`'s own extents assume rotation 0, so
      a rotated secondary's zoom (and the primary's own re-centring shiftX alongside it) is
      eyeballed, not derived — Miasma Pools' primary zoom was also lowered from the ship+10%-margin
      fit to the raw zero-margin `FitZoom` value (Andrew asked to zoom in on the Sire), and both
      models' `shiftX` nudged toward centre.
    - **A real text scroll box, replacing the fixed-height name/description/counterplay boxes.**
      Long counterplay text (Abyssal Sire's "Tentacle Guard") clipped against the fixed
      `COUNTERPLAY_HEIGHT`. New `view.LineWrap.lines(text, widthOf, maxWidth)` (RuneLite-free, same
      `ToIntFunction<String>` measurement seam `Ellipsize` already uses) greedily word-wraps and
      reports a line count; `MechanicsDetail.show()` now stacks the three text widgets top to
      bottom by `LineWrap.lines(...) * LINE_HEIGHT` against the widget's own real font metrics,
      instead of three widgets pinned at fixed y-offsets. The stacked total becomes the scrollable
      content height, handed to a `MechanicsScrollbar` via its new `setContentHeight(int)` — rect
      mutation plus `setScrollHeight`/`setScrollY` only, never `createChild`, never
      `revalidateScroll()` (D22's permanent ban still holds). The scrollbar's track/arrows/thumb
      are now always created once in `build()`, never conditionally and never later, so
      `setContentHeight` can only mutate and hide/show them. **What they do when the content fits
      is per-instance policy, `MechanicsScrollbar.Chrome`, not one global rule.** The text box
      passes `ONLY_WHEN_SCROLLABLE` and hides the whole bar — an inert scrollbar inside a ~90px
      band of copy is noise. The list column passes `ALWAYS` and keeps its track and arrows,
      hiding only the thumb: the CA screen we mirror (D21) draws that chrome unconditionally, and
      a boss whose rows happen to fit would otherwise lose it and read as a cut-off box. Making
      the text box's auto-hide global would have silently changed the list column, which nobody
      asked for. Text width is fixed
      (`COLUMN_WIDTH - 2*TEXT_X - MechanicsScrollbar.WIDTH`, ~267px) to reserve the bar's own width
      whether or not it ends up showing, since wrapping against a width that depends on the very
      thing it's computing (does this content need to scroll?) would be circular. **This box's
      visual feel in a ~90px viewport is the one thing only the in-game pass can judge** — the unit
      tests pin the stacking mechanism (blocks never overlap, content height matches the real
      stack), not how comfortable it reads at that height.
    - **Deferred, not built here: a movable/resizable window (issue #48).** Andrew raised it in the
      same pass; the Combat Achievements boss screen this window mirrors (D21) is itself fixed-size,
      and D22's third symptom already resolved "should this resize" as a non-fix for the same
      reason. Filed as its own spike rather than folded in here.

29. **A draggable window, promoting issue #48's probe (D28's deferred item) to the real feature.**
    The window can be dragged by its title bar to anywhere on screen, never fully off it, and the
    dragged position survives a "View All" rebuild, a boss switch, and a close/reopen for the
    current session.

    - **The probe's five findings are now verified engine facts, not assumptions**, and this slice
      builds on them rather than re-deriving them: (1) the drag listener family fires, and fires
      continuously — seven `setOnDragListener` events inside one second of a single drag gesture,
      so the window tracks the cursor live and needs no snap-on-release fallback; (2)
      `setClickMask(getClickMask() | WidgetConfig.DRAG)` is what makes it fire — the logged mask
      was exactly `131072` = `WidgetConfig.DRAG`, no other bit set, and `setDragParent`/`DRAG_ON`
      were never needed, so neither is used; (3) there is no engine-rendered drag ghost for an
      empty `LAYER`, so the "build a ghost ourselves" fork from planning is dead; (4) clicks on the
      rows, WIKI and close still work with `setDragDeadZone(8)`/`setDragDeadTime(10)` on the handle,
      so the handle can sit over draggable chrome without swallowing ordinary clicks.
    - **CRITICAL: the position source is `client.getMouseCanvasPosition()`, never
      `event.getMouseX()/getMouseY()`.** The probe logged both, every event, and they differ by a
      *constant* offset (x was exactly 237 across every event) — which means the event's own
      coordinates are measured **relative to the drag handle widget's own origin**, not the canvas.
      Once a drag starts moving the window, the handle moves with it, so reading position from the
      event would be reading a coordinate space that is itself sliding under the cursor: each
      event's "delta" would already include however far the previous event moved the window, and
      the window would accelerate or judder rather than track the cursor 1:1. The canvas position
      is absolute and does not move when the window does, so it is immune. **Do not "simplify" this
      back to `event.getMouseX/Y` later** — it looks equivalent in a single static screenshot of the
      log and is not.
    - **The mechanism.** The drag handle's `setOnDragListener` fires an `onDrag()` on every event of
      a gesture. The first event of a gesture (a `dragging` flag distinguishes it) only captures a
      baseline — the current mouse canvas position and the offset already in force — and returns;
      every event after computes `dragOffset = offsetAtStart + (mouseNow - mouseAtStart)` on each
      axis and calls `place(host)` (through the same `replaceIfChanged()` helper `onClientTick`
      already used for a client resize, since both are "something that might move the window
      changed, recompute" on the client thread). `setOnDragCompleteListener` fires an
      `onDragComplete()` that normalizes the stored offset to `clampedFinalOrigin - computedOrigin`
      and clears the transient state — without this, releasing past an edge would leave a phantom
      off-screen offset that the *next* drag has to silently "unwind" before the window visibly
      moves at all, since the raw accumulated delta could be far larger than the clamp ever let it
      act on.
    - **The clamp itself is `view.WindowDrag.clampedOrigin`**, RuneLite-free like every other
      decision in that package (the same split D21 used for `view.Selection`): pure, static, one
      axis at a time, matching `WindowPlacement.origin`'s own idiom. Bounds are
      `[min(0, computedOrigin), max(hostSize - windowSize, computedOrigin)]` — never clamped
      tighter than `computedOrigin` itself, so a zero offset reproduces today's placement exactly,
      including a legitimately negative computed origin (D20's overhang); an offset can only ever
      move an out-of-bounds origin back toward on-screen, never push it further out.
    - **The clamp runs in logical-window coordinates (512x334 against the host's current
      width/height), before `withChrome`.** `place()` composes `WindowDrag.clampedOrigin(x,
      dragOffsetX, WINDOW_WIDTH, host.getWidth())` per axis, then still applies `withChrome` (D27)
      afterward. The 15px chrome overhang is allowed to go off-canvas — it always has been, since
      D27 grew the root outward from the logical window rather than the other way around — but the
      512x334 window itself may not. The no-collection-log fallback branch (`ABSOLUTE_CENTER`,
      D20) deliberately ignores the drag offset: it is a one-tick transient, not a placement worth
      clamping against.
    - **Fork 1, resolved by Andrew: session-only offset lifetime.** The dragged offset survives a
      close/reopen, a boss switch, and a "View All" rebuild — the same lifetime as
      `selectedMechanicId` — and forgets on a client restart. There is no config key for it: unlike
      `selectedMechanicId` (which is genuinely per-character reading state, D18) or "View All"
      (persisted per D20 because D10 calls reveal a reading mode), a screen position has no
      player-visible reason to outlive the session, so persisting it would be a config key nobody
      asked for. It forgets on client restart with no code to make that happen: a restart is a
      fresh plugin instance with fresh, zero-valued fields, the same reason `selectedMechanicId`
      needs no explicit reset there either. The **in-progress** flag is the one piece of drag state
      that does NOT get that lifetime: `close()` clears `dragging` explicitly, because a gesture
      interrupted by Esc or by the collection log closing never receives its completion event, and a
      stale flag would make the next gesture's first event continue from a dead baseline and jump
      the window. Offsets persist; a half-finished gesture does not.
    - **Resize remains out.** D22's third symptom already resolved "should the window resize" as a
      non-fix, since the Combat Achievements screen this window mirrors (D21) is itself fixed-size;
      this slice only repositions the fixed-size window, and issue #48 is where that evidence and
      this decision both live.
    - Pinned with `view.WindowDragTest` (five cases: identity at zero offset, a legitimate negative
      computed origin left unclamped at zero offset, the right edge, the left edge, and an
      already-out-of-bounds origin only ever draggable back in) and three cases added to
      `BossMechanicsWindowLayoutTest`: a zero-offset placement is unchanged; a drag composes and
      survives a rebuild; releasing past an edge stores the clamped offset rather than the raw one,
      proven by a second, small drag responding immediately rather than first unwinding a phantom
      off-screen delta. The last two drive the handle's `setOnDragListener`/
      `setOnDragCompleteListener` directly with fake `ScriptEvent` proxies (the
      `MechanicsScrollbarTest` idiom) and a mutable stubbed `client.getMouseCanvasPosition()`, which
      needed one additive `RecordingWidget` helper, `returning(widget, methodName, value)` — a stub
      map so a test can pin a getter's return value, following the same additive pattern
      `lastArgsOf`/`allArgsOf` were added by. Fixing it uncovered that `RecordingWidget`'s existing
      "any setter returning `Widget` returns the proxy itself" default also silently applied to
      `getParent()`, which is not a setter: unstubbed, it returned the same widget every time, so
      `WindowPlacement.offsetInRoot`'s parent-chain walk never terminated. The default now only
      applies to `set*` methods; every other `Widget`-returning getter defaults to null like any
      other unstubbed getter, which is what `getParent()` should have done regardless of this
      issue's needs.
    - **The probe's own per-event and "armed" log lines are gone.** They were deliberately at info
      so the probe's silence would be unambiguous; that job is done, and per-drag-event info logging
      would spam `client.log` on every real gesture. `dragHandleIsWiredForDragging` (D28's probe
      test) is unchanged: it still pins the one real-feature invariant the whole thing rests on.

30. **Title-bar chrome for D29's draggable window: the header divider, a hover/pressed tint on the
    drag handle, and the drag itself now moves a grey outline rather than the window (issue #48,
    Andrew's approved Fork A(a) plus the sub-fork: outline visible, window stays visible too).**
    Five slices, all landing in `ui/BossMechanicsWindow`; no `view` code was added, since every
    drag-time decision is already `WindowDrag.clampedOrigin`, composed differently, not re-derived.

    - **The header divider, read from group 713's own onLoad (script 4835 -> script 228, flags bit
      2 clear): sprite 2546, x centred, y 14, width `parent - 10`, height 26, tiled.** Purely visual
      chrome, verified in the live pass rather than color-pinned, matching D26/D27's own precedent
      for the dim rectangle and nine-slice frame.
      **The sprite-canvas-vs-raster tiling trap, worth recording for the next chrome job:**
      sprite 2546's raster is 36x6, but its declared canvas is 36x36 with the raster drawn at
      `offsetY=15` inside it, and the engine tiles by the sprite's CANVAS size, not its trimmed
      raster -- which `DumpSprites` prints only the latter of. A 26px-tall tiled band therefore
      shows exactly one 36px canvas tile, clipped, and the 6px groove lands wherever `offsetY` put
      it inside that tile: window-space y 29-35 here, below the title (ends y 30) and level with the
      close button's own bottom edge (y 29), well above the progress bar (y 48). The numbers look
      wrong (a 26-tall sprite drawing a line 15px into itself) until you know to check canvas size
      and offsets, not just the trimmed dimensions `DumpSprites` reports.
    - **The drag handle's hover tint, read from the collection log's own chrome.** Script 2240
      builds invisible tiled `GRAPHIC` overlays over the log's own draggable chrome; script 2601 sets
      their sprite to 1040 (the same steel texture D26 already uses); script 244 flips opacity on
      `onmouserepeat` -> 200 / `onmouseleave` -> 255 (RuneLite opacity is inverted, D27, so 255 is
      fully invisible and 200 is the CL's own faint tint). One overlay, created once as a child of
      the handle layer and mutated (`setOpacity`, guarded behind a changed-value check since
      mouse-repeat is per-frame), never recreated (D22).
    - **No pressed state exists anywhere in the CL's own scripts -- `TINT_PRESSED = 160` is ours,
      not Jagex's.** Andrew asked for a brighter tint while held; `onHold` sets a `holdSeen` flag,
      the next `onMouseRepeat` reads it (pressed) and clears it (so a repeat with no intervening
      hold reverts to plain hover). **Open, for the live pass:** whether `setOnHoldListener` even
      fires on an op-less widget is unverified beyond `javap` proving the method exists; if it turns
      out inert, slice 2's hover tint alone still works unmodified, since the pressed logic only
      extends `onMouseRepeat`'s body and never touched the hover wiring itself. Whether 160 reads
      right against 200 is also only judgeable in game.
    - **The drag fork, resolved: our own outline, not the CL's hide-window-plus-outline mechanism.**
      The collection log's real drag (script 2801) hides its window content (component 621:88),
      shows a separate outline component (621:89) and moves it live, clamped, landing off a 3-frame
      timer (script 2802). We need no timer hack -- `setOnDragCompleteListener` is D29-verified --
      and we deliberately skip the hide half: our drag handle lives inside the window tree itself
      (D29), and hiding that tree mid-gesture might kill the engine's drag events entirely, which is
      unverified and buys nothing visually since the window's own content isn't what's confusing to
      look at mid-drag. The outline is 4 concentric unfilled rectangles, colour 0x9F9F9F
      (`Widgets.GREY`), insets 0-3px, opacity stepping 100/110/120/130 -- lifted directly from
      621:89's own recipe.
    - **The outline is a second persistent host child, sibling to `root`, with the exact same
      identity-scan reuse idiom (D19)**: built once by `ensureOutline`, hidden at creation, nulled in
      `onGameStateChanged`, hidden again in `close()` (a gesture interrupted by Esc or the log
      closing never reaches `onDragComplete`, so the outline could otherwise be left floating).
    - **It must be created AFTER `root`, and that ordering is load-bearing, not incidental.** A
      parent's dynamic children draw in creation order, so the last one created draws on top.
      Because we keep the window visible during a gesture (the sub-fork above, unlike the collection
      log which hides its content), an outline created first sits *underneath* the very window it is
      positioning: for a short drag the offset is smaller than the window, so almost the entire
      outline hides behind it and only a sliver protrudes. The affordance would be worth nothing
      precisely when it is needed most. `BossMechanicsWindowLayoutTest` therefore identifies the
      root by shape (the host child that is not four rectangles), never as "the last child" -- a
      positional helper silently returns the outline the moment this order changes, which is exactly
      how it was caught.
      Logical-window sized (512x334), **not** the chrome-inflated root's 542x364: it tracks where the
      window's own content will land, not its steel frame, so its position never goes through
      `WindowPlacement.withChrome`.
    - **Contract change: `onDrag` no longer mutates `dragOffsetX`/`dragOffsetY` mid-gesture.**
      Under D29's original wiring, `onClientTick` -> `replaceIfChanged()` reading a
      transient offset every event would have moved the window itself, which is exactly what this
      slice needed to stop. A new pair of fields, `dragLiveOffsetX`/`dragLiveOffsetY`, holds the
      gesture's un-committed candidate instead; `onDrag` writes them and shows/moves the outline at
      `WindowDrag.clampedOrigin(...)` (no `withChrome`); `onDragComplete` reads them once to commit
      the final clamped value into `dragOffsetX`/`dragOffsetY` (D29's existing phantom-offset
      normalization, unchanged), then calls `replaceIfChanged()` itself -- the one point that
      actually relays the window now that `onDrag` no longer does -- and hides the outline. Every
      outline widget already exists from `ensureOutline`, so both `onDrag` and `onDragComplete` only
      ever call `setOriginalX/Y`/`setHidden`/`revalidate` on it: the 7Hz D22 leak trap is
      structurally impossible here, the same guarantee D24's model-widget pool relies on.
    - Pinned by reworking `BossMechanicsWindowLayoutTest`'s existing drag cases (`draggedOffsetSurvivesARebuild`
      now drives `onDragComplete` before asserting, since the window no longer moves mid-gesture) and
      two new ones: `draggingMovesTheOutlineNotTheWindow` (mid-gesture the root's origin is
      unchanged and the outline sits at the clamped logical origin, visible) and
      `releasingLandsTheWindowAndHidesTheOutline` (release lands the root at the clamped,
      chrome-adjusted origin, hides the outline, and a small drag right after responds immediately --
      D29's phantom-offset pin, re-proven under the new mechanism). `hoveringTheDragHandleTintsIt`,
      `holdingTheDragHandleBrightensTheTint` and `dragOutlineIsBuiltOnceAndHidden` are new; the
      divider is unpinned (purely visual, per the same precedent D21/D26 already set).

31. **The drag dead zone and dead time drop to the collection log's own 1 and 5, correcting D29's
    probe values and D30's decision to keep them.** Andrew's live pass on D30: "there is a lag
    between clicking and dragging and when the box actually moves... the box is actually away from
    your mouse now."

    - **The cause is arithmetic, not a bug.** `setDragDeadZone`/`setDragDeadTime` gate when the
      engine begins emitting drag events at all: none fire until the cursor has travelled the dead
      zone in pixels *and* the dead time in client cycles (20ms each) has elapsed. At the probe's 8
      and 10 that is 8px and ~200ms of a gesture in which the window is motionless. Worse, it is not
      just a delay: `onDrag`'s first event captures the baseline (D29), and by then the cursor is
      already 8px past where the player actually pressed, so the window trails the pointer by the
      dead zone for the *rest* of the gesture rather than catching up. Both halves of Andrew's
      report are the same constant.
    - **1 and 5 are cache-verified, not tuned by feel**: script 2240 sets exactly those on the
      collection log, the interface this window is imitating and the one Andrew compared it against.
      D30 recorded them and then kept ours anyway — the probe picked 8/10 to be certain a dead zone
      could not swallow clicks on the rows/WIKI/close (D29 finding 4), which was the right caution
      for a probe and the wrong default to ship. The handle is a bare LAYER carrying no op of its
      own, so a micro-drag across it does nothing, and Jagex runs 1/5 on a header hosting real
      buttons.
    - **Residual offset is now ≤1px** and no longer perceptible. Zeroing it entirely would need the
      press position, which needs `setOnHoldListener` to fire — still unverified in the live client
      (D30), and not worth chasing for one pixel.
    - Unpinned deliberately: two engine-tuning constants whose only real test is the live pass, same
      precedent as the divider in D30.

32. **Unanchoring the draggable window: an absolute position, seeded from the collection log only
    at open (issue #48 follow-up).** Andrew's live pass on D29-31's shipped drag feature: the
    window still moved when you dragged or resized the collection log underneath it. The cause is
    that D20's `place()`/`onClientTick` re-derived the window's origin from the log's live
    rectangle every client tick, with D29's drag offset merely composed on top of that
    recomputation rather than replacing it — dragging the window and dragging the log both fed the
    same "computed origin" input, so the log could always yank the window regardless of the drag
    offset. This decision supersedes D20's "re-places every tick to follow the log" and D29's
    offset model outright: the window's position is now `windowX`/`windowY`
    (`ui.BossMechanicsWindow`), absolute host-coordinate state, and `place()` never reads the
    collection log at all — only `open()` does, once, to seed it.

    - **Fork 1, resolved by Andrew: option (a), unanchor ALWAYS.** Position is seeded from the
      collection log at open and is absolute thereafter; while the window has never been dragged,
      *each* open re-seeds from the log's current position so it always opens covering the log;
      after the first drag, the dragged position wins for the rest of the session. **Rejected:
      option (b), unanchor only after the first drag** — keep `place()`/`onClientTick` tracking the
      log's live rectangle every tick, exactly as D20 always has, for as long as the window has
      never been dragged, and only switch to the absolute model once the player drags it once. That
      would have reproduced the reported bug for the entire pre-drag lifetime of every single
      session — the window would still visibly chase the log around right up until the moment the
      player happened to drag it — which is *exactly* the behaviour Andrew reported as wrong, merely
      deferred rather than fixed. (a) instead stops tracking the log the instant the window opens,
      every time, with no dependency on drag history for that part: the log's rectangle is read
      exactly once per `open()` call, never from `onClientTick` or a drag gesture.
    - **The seed-on-every-open-until-first-drag rule.** `open()` re-seeds `windowX`/`windowY` from
      the log's current rectangle whenever `!draggedThisSession`; once `onDragComplete` sets
      `draggedThisSession = true` (never cleared), no later `open()` this session reads the log
      again — same session lifetime D29 fork 1 already gave the dragged position. An undragged
      window therefore still opens covering wherever the log currently is (a first-time player's
      expectation, and what a "View All" flip or boss switch before any drag still needs), but no
      longer chases the log around while it stays open.
    - **`view.WindowDrag.clampedOrigin` is replaced, not extended, by `visibleOrigin(origin,
      windowSize, hostSize)`.** `clampedOrigin`'s "never clamp tighter than `computedOrigin`" bound
      existed purely because `computedOrigin` was itself re-derived from the log every tick (D29);
      under the absolute model there is no such reference left to protect, so the clamp collapses to
      an ordinary two-sided bound, `[min(0, hostSize - windowSize), max(0, hostSize - windowSize)]`,
      which also correctly pins the window to the host's own origin edge when the host is smaller
      than the window (the pair inverts to both-non-positive in that case, rather than throwing or
      silently doing nothing). `WindowPlacement.origin`/`offsetInRoot`/`withChrome` are untouched and
      still do the open-time seeding and the chrome offset; `com.bossmechanics.view` stays
      RuneLite-free (D19's split).
    - **Accepted edge case, recorded so it is not re-litigated:** a seeded origin that
      `WindowPlacement.origin` computes as negative (the log within 6px of the host edge, D20's
      legitimate overhang) now clamps to 0 instead of reproducing exactly. D29's `clampedOrigin`
      deliberately protected this case (`min(0, computedOrigin)` folded the negative value into its
      own lower bound); `visibleOrigin`'s lower bound is `min(0, hostSize - windowSize)`, which for
      any host at least as wide as the window is exactly 0, clamping a small negative overhang away.
      Accepted because no shipped layout (D20's six top-level layouts) puts the log within 6px of
      `UI_HIGHLIGHTS`' own edge — a ≤6px shift in a configuration no launch client actually produces.
    - Pinned by `view.WindowDragTest`, rewritten (in-bounds identity, right-edge clamp, left-edge
      clamp, and host-smaller-than-window pinning to the host's own origin), and four cases added to
      `BossMechanicsWindowLayoutTest`: `movingTheCollectionLogDoesNotMoveTheWindow`,
      `undraggedReopenSeedsFromTheLogsCurrentPosition`, `draggedReopenIgnoresTheLog`, and
      `shrinkingTheClientClampsTheWindowAndGrowingItBackRestoresIt` (the last proving the clamp only
      changes what gets drawn and never mutates the stored origin — shrinking and regrowing the host
      restores the exact pre-shrink position). D30's four drag tests are unchanged, exact pinned
      numbers and all (168 / 253 / 238 / 228 / 153): they were written against the root's observable
      origin, and that observable behaviour is identical under the new model.
    - **Deliberately not built here: hiding the window mid-drag, and the in-game probe that would
      gate it.** Andrew's live pass also asked whether the window should hide (or ghost) while being
      dragged, matching some of the collection log's own chrome. That is a separate PR, gated behind
      its own probe, same precedent as D28 deferring this whole feature past its own live pass.

33. **Hiding the window while dragging, Path B, promoting D32's deferred probe (issue #48) to the
    real feature.** Slice 6's probe (PR #56, reverted in #57) hid `root` mid-gesture and logged
    every subsequent drag event; the client log showed the arm-and-hide line once per gesture and
    then silence -- no further `onDrag`, no `onDragComplete` -- across two separate live gestures:
    ```
    [Client] PROBE armed ... handle 431x39
    [Client] PROBE hid the window for this drag gesture, mouse canvas=(381,125)
    ```
    Confirmed externally too: the window never returned on release, only on close/reopen.

    - **Verified engine fact: the drag machinery re-hit-tests under the cursor every frame rather
      than latching the drag target at press, so hiding any ancestor of the drag handle kills the
      in-flight gesture.** This is not an assumption to re-litigate later; it is the reason Path A
      (`root.setHidden(true)` for the whole gesture, which is what the probe tried) is dead, and the
      reason every widget this slice hides is chosen specifically to avoid the handle's own
      ancestor chain (`root -> window -> header -> handle`).
    - **This also explains Jagex's own design.** The collection log genuinely can hide its window
      content (component 621:88) during its own drag (script 2801, D30) -- but its drag handling
      does not live inside that component; the log's outline-drag script owns the gesture from
      outside the content it hides. Ours does live inside the tree it draws (the handle is a
      descendant of the window it drags), so the same trick is unavailable to us, verified fact
      above.
    - **Path B: hide the leaves, keep the spine.** `root`, the inner `window` layer, `header` and
      the drag handle stay unhidden for the whole gesture; everything the player can actually see
      is hidden instead. `BossMechanicsWindow.dragHidden`, a `List<Widget>` populated fresh by
      every `open()` (chosen over named fields per-widget: simpler to keep correct as the window
      grows, and every consumer only ever needs "hide/show all of these," never one by name) holds:
      the chrome layer (below), the progress bar layer, and the list header/list/detail column
      layers -- none of those three sit on the handle's ancestor chain, so hiding each whole is
      legal and is what D22's "one `setHidden` instead of eleven" idiom prefers over reaching into
      every row/scrollbar/model widget individually. The header's own children (title, the divider
      sprite, the WIKI button, the close button, and the drag handle's own tint overlay) are added
      individually instead, because `header` itself is on the ancestor chain and can never be
      hidden wholesale; the tint overlay is a child of the handle, not the handle itself, so hiding
      it is legal by the same rule.
    - **`steelChrome()`'s ~11 sprites (background, four corners, four tiled edges) are now built
      inside one wrapping `chrome` LAYER** rather than as direct children of `root`, so the whole
      frame hides with a single `setHidden` call. The wrapper is created in exactly the position in
      `root`'s own child order the sprites used to occupy -- immediately, before the `window` layer
      built right after `steelChrome()` returns -- so draw order is unchanged when visible: D27's
      "frame, then content" ordering and D30's after-root outline ordering are both about root's
      position among *its own siblings* and root's children relative to each other, neither of
      which this touches. Sprite coordinates are untouched too, since they were always root-local
      and the wrapper sits flush at root's own origin.
    - **The guard is a boolean, not a read of `widget.isHidden()`.** `contentsHiddenForDrag` flips
      true on the first `onDrag` event that actually shows the outline (the *second* event of a
      gesture overall, since the first only captures a baseline and returns, D29) and flips false
      again on `onDragComplete`/`close`/`open`, matching the register `dragging` already uses for
      the outline itself. Every widget in `dragHidden` already exists -- built once by `open()` -- so
      `setContentsHidden` only ever calls `setHidden`/`revalidate` on it, never `createChild`,
      keeping the ~7Hz drag-event path exactly as D22-clean as the outline's own mutation-only
      handling already is.
    - **The restore paths, and why each one matters.** `onDragComplete` restores the content
      *before* `replaceIfChanged()`, since the window is about to become visible again at its
      landed position and the content has to already be back before that happens. `close()`
      restores it too, even though `root` gets hidden entirely right after: a gesture interrupted
      by Esc or by the collection log closing never reaches `onDragComplete`, and this is the exact
      failure mode the probe just cost a client load to demonstrate is possible when the wrong
      widget gets left hidden mid-gesture. `onGameStateChanged`'s teardown (LOGIN_SCREEN/HOPPING/
      CONNECTION_LOST) resets the guard and clears `dragHidden` without touching the widgets
      themselves, matching the existing "drop `root`/`outline` without touching them" precedent
      there -- the interface tree is already gone on those transitions, so mutating widgets that
      may no longer be valid would be the wrong instinct, not the safe one. `open()` unconditionally
      clears `dragHidden` and resets the guard before rebuilding, regardless of whatever state a
      previous gesture left things in, since every widget below that point is rebuilt fresh anyway
      (D22) and a stale guard would otherwise suppress hiding on a legitimate next gesture.
    - Pinned by three new `BossMechanicsWindowLayoutTest` cases:
      `draggingHidesTheWindowContentsButNotTheDragHandle` (after the second drag event, every
      `dragHidden` widget's last `setHidden` call is `true`, and root/the window layer/header/the
      handle -- found by walking the tree for the drag-handle's actual parent chain, not assumed by
      position -- never receive a `true` `setHidden` call at all: the regression guard for the
      verified engine fact above), `releasingRestoresTheWindowContents` (release restores every
      `dragHidden` widget to `false` and still lands the root at the clamped, chrome-adjusted origin
      D30 already pinned), and `anInterruptedGestureDoesNotStrandAHiddenWindow` (two drag events,
      `close()`, then a fresh `open()` -- nothing is left hidden at any point). The pinned drag
      numbers (168 / 253 / 238 / 228 / 153) are unedited; a pre-existing D30 test
      (`dragOutlineIsBuiltOnceAndHidden`) needed its own final assertion rewritten from a
      child-count-of-4 heuristic to a direct host-child-count comparison, because collapsing the
      chrome sprites into one wrapper widget incidentally made a reused root's accumulated child
      count in that test's fixture equal 4 too -- a fixture artifact (`RecordingWidget` never trims a
      reused root's child list the way the real client's `deleteChildrenOf` does), not a behaviour
      change; the rewritten assertion checks the same "no duplicate top-level child" invariant
      without depending on any widget's internal shape.
    - **Issue #58 (the pressed tint, `TINT_PRESSED`/`holdSeen`/`onDragHandleHold`) is open and
      deliberately untouched here.** The probe saw no `setOnHoldListener` firings; Andrew is
      re-checking whether he actually saw the tint work during the live pass for this slice. Nothing
      about that code changed.

34. **A text-descent pad for the description panel's clipped last line.** From the same
    playtesting pass as D35 below: Tentacle Guard's counterplay text clipped a hair short of its
    own bottom edge even scrolled fully down.

    - **Root cause: `restackText` handed the raw stacked block height straight to
      `setContentHeight`, with zero allowance for the font's own extent below the last baseline.**
      A rendered PLAIN_12/BOLD_12 line is taller than its 12px advance -- descenders (g/y/p) plus
      `setTextShadowed`'s own 1px drop extend past it. Between stacked blocks that overhang falls
      harmlessly into the 12px `TEXT_LINE_GAP`; the FINAL block's overhang has nowhere to go, so
      the viewport clips it even at max scroll. The other three candidates (scroll range, viewport
      geometry, a `LineWrap`-vs-client wrap disagreement) were all ruled out: the symptom is a
      horizontal cut through the last line's glyph bottoms, not a missing whole line or a
      geometry mismatch, which only a below-baseline pad explains.
    - **`MechanicsDetail.TEXT_DESCENT` is a labelled ESTIMATE, not a measured constant, and that
      distinction is deliberate.** `FontTypeFace` exposes only `getTextWidth`/`getBaseline`
      (javap-verified against runelite-api, no descent getter). Offline, `cachetool`'s
      `DumpFontMetrics` (new) confirmed the underlying cache resource can't supply it either:
      `p12_full`/`b12_full` (Djb2-hash-matched against the FONTS index, archives 495/496) load
      through the same 257-byte format as every other font there -- 256 per-glyph advance widths
      plus one scalar labelled "ascent" that equals the font's own nominal line height (12 for
      both) rather than a true baseline-relative ascent -- and carry no glyph bitmap or bounding
      box at all. There is nowhere left, offline or in the client API, to read a real below-
      baseline extent from. `TEXT_DESCENT = 5`: 4px of raw descender allowance (typical for g/y/p
      at a 12px em) plus 1px for the shadow, cited in the constant's own javadoc with this same
      provenance so a future reader doesn't mistake it for a measured cache fact. If the live pass
      (below) still shows clipping, this is the one number to bump.
    - **Consequence, not a regression: content that used to "fit" at exactly ~90px now scrolls by
      a few pixels and the bar appears.** Those pixels always genuinely contained glyphs; the old
      behaviour was silently clipping them.
    - Pinned by `MechanicsDetailPreviewTest.textScrollContentReservesTheFontsDescentBelowTheLastLine`
      (asserts the scrollbar's `setScrollHeight` equals the stacked bottom plus `TEXT_DESCENT`, and
      that `TEXT_DESCENT > 0`) and an amended
      `textBlocksStackWithoutOverlappingAndScrollContentMatchesTheStackedHeight`, which now expects
      the same `+ TEXT_DESCENT` pad rather than the raw stacked height.

35. **Draggable thumbs for both scrollbars.** From the same playtesting pass as D34 above: neither
    scrollbar's thumb could be grabbed, only indicated (D20's original limitation).

    - **Both scrollbars get thumb dragging**, not just one -- they share `build()`, and
      the Chrome policy (D28) does not interact with dragging, so splitting would need a second
      policy enum for no user benefit. Shipped directly, no probe PR: D29's probe already validated
      this exact listener family (`setClickMask(... | WidgetConfig.DRAG)`), click mask and
      coordinate source (`client.getMouseCanvasPosition()`, never event coordinates) on the
      title-bar handle, and the one new question -- does a gesture survive the cursor drifting off
      a 16px-wide capture layer sideways -- is already answered by that same shipped handle, whose
      own narrow dimension (39px tall) every real drag routinely leaves while still tracking
      continuously.
    - **A static drag-capture LAYER over the track is the load-bearing design choice, not listeners
      on the moving thumb sprites.** D33 established the engine re-hit-tests under the cursor every
      frame, so a capture surface that never moves and never hides mid-gesture is safe BY
      CONSTRUCTION -- the same shape as the title-bar handle (stays put while the outline moves).
      The thumb sprites stay pure visuals, repositioned by the existing `positionThumb`. Rejected:
      listeners on the thumb sprites themselves, a moving and sometimes-clamped drag source, which
      is precisely what D33 warns against. A fast flick cannot outrun the gesture either way: each
      event recomputes from the ABSOLUTE mouse position against the gesture's own fixed baseline,
      so the thumb converges every event with no lost gesture and no accumulated error.
    - **The math is `view.ScrollThumbDrag.scrollY`, RuneLite-free like `WindowDrag`** (the D29
      precedent), but the scrollbar's own gesture is NOT `WindowDrag`'s pattern reused as code:
      the window's is entangled with the outline and D33's content-hiding; the scrollbar's has no
      phantom-offset problem, because scroll state is clamped and applied on every drag event
      rather than only committed on release, so `onDragComplete` only clears a flag. A shared
      `DragGesture` helper between the two was rejected as YAGNI. `scrollY(scrollAtStart,
      mouseDeltaPx, travelPx, maxScroll) = clamp(scrollAtStart + floorDiv(mouseDeltaPx * maxScroll,
      travelPx), 0, maxScroll)`, `floorDiv` rather than `/` so a negative delta rounds the same
      direction a positive one does; `travelPx <= 0` or `maxScroll <= 0` falls back to clamping the
      starting scroll alone. Pinned by `ScrollThumbDragTest`.
    - **`MechanicsScrollbar`'s constructor gains `Supplier<net.runelite.api.Point>
      mouseCanvasPosition`**, threaded through unchanged by `MechanicsList` and `MechanicsDetail`'s
      own constructors; `BossMechanicsWindow.columns()` supplies `client::getMouseCanvasPosition`
      to both. No plugin-facing contract changed.
    - **The capture layer is built once in `build()`, after the thumb sprites, spanning exactly the
      track's own rect (`ARROW_SIZE` to `barHeight - ARROW_SIZE`) so it can never swallow the
      arrows.** Reuses D31's dead zone/time (1px, 5 cycles) for the same reason D31 corrected the
      title-bar handle's own values. `layout()` hides the capture whenever the bar isn't scrollable
      (an inert bar must not start a dead gesture) and clears the in-flight flag, since a re-layout
      mid-gesture means the content height the gesture started against no longer applies. `onDrag`
      null-guards the mouse point, guards `maxScroll == 0 || travel <= 0`, captures the baseline on
      the gesture's first event and returns, then on every later event goes through
      `ScrollThumbDrag` and applies `setScrollY` + `positionThumb` behind a changed-value check, the
      same idiom `scrollBy` (wheel/arrows) already used.
    - **A thumb drag cannot trigger the window's own drag-and-hide (D33).** The window's `onDrag` is
      wired only to the title-bar handle's own listener; a press on a scrollbar's capture layer
      starts that scrollbar's own gesture, and `setNoClickThrough(true)` stops it reaching anything
      behind. An interrupted thumb gesture needs no cleanup beyond the flag: `MechanicsList`/
      `MechanicsDetail` (and their scrollbars) are discarded and rebuilt on every window `open()`/
      close, taking `thumbDragging` with them -- unlike the window's own drag state, there is no
      committed value that could be left stranded.
    - Pinned by three new `MechanicsScrollbarTest` cases: `draggingTheThumbScrollsProportionally`
      (the worked example: `barHeight` 122 -> `trackHeight` 90, content 180 over a 90px viewport ->
      `thumbHeight` 45, `travel` 45, `maxScroll` 90; a 10px drag scrolls to 20 and repositions the
      thumb sprite to `ARROW_SIZE(16) + 10 = 26`),
      `draggingPastTheTrackEndClampsAndNeverLeavesAPhantom`, and
      `thumbDragCreatesNoChildrenAndNeverCallsRevalidateScroll` (the D22 pin extended to the drag
      path). The class comment's old "not draggable" paragraph is rewritten.
    - **The title-bar handle is now resolved by identity in the tests, not by tree order.** These
      capture layers are the first widgets other than that handle to carry drag wiring, and the
      wiring is byte-for-byte identical (`clickMask | DRAG`, dead zone 1, dead time 5), so
      `BossMechanicsWindowLayoutTest`'s "first drag-wired widget in creation order" helper would
      have been correct only for as long as `header()` kept building before `columns()`. That is the
      idiom D30 records being burned by, and it fails worse here: a reorder keeps the count at three
      and every gesture test in the file silently drives a scrollbar instead of the window, so the
      pinned 168/253/238/228/153 numbers break in five confusing places rather than one clear one.
      The handle is now found by its `setOnHoldListener` — the only drag-wired widget that has one
      (it drives the pressed tint, D30), and a property of what the handle *is* rather than where it
      was built.

36. **Curating a boss the cache can name but the tools cannot measure (Doom of Mokhaiotl, issue
    #61).** The first boss whose own body animations are stored in the newer **skeletal** format:
    sequence config opcode 13, zero classic frames. Everything the project had built for reading
    animations offline assumes classic frames, so this is the recipe for the ones that follow.

    - **Jagex's own symbolic names are the id source, and they are reachable even for content
      newer than the compiled API.** `runelite-api` 1.12.33 has no constant mentioning this boss;
      the cache's `GAMEVALS` index would have them, but the game client never downloads that index
      (the live cache stops at index 22). The names were read from RuneLite master's generated
      `gameval/{NpcID,AnimationID,SpotanimID}.java` and cross-checked against the ids actually
      present in the cache. This is the same standard D-note the Vorkath curation set (a gameval
      name describes one known use of an id, not an exclusive one) — it just needed a new fetch
      path. Doom's animations are prefixed `DOM_`, not `DOOM_`, which no amount of guessing finds.
    - **The framemap-compat check and `FitZoom`'s per-frame extents say nothing about a skeletal
      boss.** A skeletal sequence has no `frameIDs`, so there is no framemap to group by and no
      per-frame vertex extents to fit against; the offline fit collapses to the static bind pose.
      The shipped `zoom`/`shiftY` are that pose plus 10%, and unlike every earlier boss they are
      an *estimate that needs an in-game look*, not arithmetic. The rig substitute is
      `ModelDefinition.animayaGroups` (bone ids per vertex) where `packedVertexGroups` used to be:
      `cachetool/DumpModelRig` reports it, and matching bone sets between a model and the models a
      known-good animation plays on is the nearest offline compatibility signal available.
    - **Preview the Combat Achievements model, not the npc.** `Widget.setModelId` takes one cache
      model and the npc has five parts, of which `models[0]` is only the centre body (1515 verts,
      X +/-142) — the two large side pieces would simply be missing. Struct 4933, the CA screen's
      own entry for this boss, pins model **56455**: the merged whole boss (1901 verts, X +/-322)
      with the same 83-bone rig, alongside anim 12453 `NPC_DOOM_BOSS_IDLE_CA` and zoom 2500. That
      the CA screen — a MODEL widget like ours — plays a *skeletal* animation on a raw model id is
      also the evidence that this preview path works at all. `DumpStructs` already dumped these
      structs (param 1322 = model, 1323 = anim, 1329 = zoom); a multi-part boss should be checked
      against it before settling for `models[0]`.
    - **New cachetool mains, all of which live outside the repo:** `ScanGameVal` (GAMEVALS index,
      for a cache that has it), `NpcSearch` (npcs by name substring), `DumpSeqRaw` and
      `ScanSkeletal` (which sequences are skeletal, and their opcode-13 payload), `DumpModelRig`
      (extents + classic-vs-animaya rig), `ModelColors` (face-colour histogram and what share of
      it an npc's recolor table repaints — the D28 rule, measured instead of eyeballed; Doom's
      larvae recolor 0% of their faces, so they preview colour-true and need no sprite).
    - **Detection stays cache-derived and unverified until a delve is actually run**, exactly as
      #9 and #10 record for Sire and Vorkath. Ten mechanics, `phase` carrying the delve gating
      ("Delve 3+", "Delve 5+") that D7 anticipated for archetype-spanning bosses.

## Open questions

- Mad Angel is the newest boss; wiki/community documentation of its IDs may be thin.
  Curate it last.
- UX feel of revealed-vs-discovered needs playtesting (decision 10 is provisional).
- ~~Exact sprite for the injected button.~~ **RESOLVED (issue #4):**
  `net.runelite.api.gameval.SpriteID.ICON_SWORDS` (3029), a pair of crossed swords, as the
  closest stock combat glyph to the Combat Achievements trophy it sits beside. One
  constant, so swapping it after seeing it in game is a one-line change.
