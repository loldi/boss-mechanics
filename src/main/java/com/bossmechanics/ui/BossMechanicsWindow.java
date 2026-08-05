package com.bossmechanics.ui;

import com.bossmechanics.data.Boss;
import com.bossmechanics.view.MechanicsView;
import com.bossmechanics.view.Selection;
import com.bossmechanics.view.WindowDrag;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.NPCComposition;
import net.runelite.api.Point;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

/**
 * The Boss Mechanics window: a 512x334 native-styled screen drawn <b>over</b> the collection log,
 * shaped like the Combat Achievements boss screen it is reached the same way as
 * (docs/DECISIONS.md D3, D20, D21).
 *
 * <p>This class owns the shell — the frame, the title bar with its WIKI and close buttons, the
 * progress bar, the three column layers and the selection — and hands the columns to
 * {@link MechanicsList} and {@link MechanicsDetail}.
 *
 * <p>Deliberately dumb, like {@link CollectionLogButton}. It positions rectangles and copies
 * strings; every decision that could be wrong about what a mechanic *says* was already made in
 * {@link MechanicsView} and {@link Selection}, both unit tested. In particular nothing here counts
 * discoveries.
 *
 * <p>Every geometry constant below was dumped from the real cache (Combat Achievements group 717
 * and script 4782, collection log group 621), not eyeballed.
 */
@Slf4j
public class BossMechanicsWindow
{
	/**
	 * The Combat Achievements boss screen, 717 child 0: 512x334, which is the fixed-mode viewport
	 * size (548 child 10 is 512x334 at (4,4)). It is deliberately larger than the collection log's
	 * own 500x314 UNIVERSE — the screen covers the log rather than fitting inside it (D21).
	 */
	private static final int WINDOW_WIDTH = 512;
	private static final int WINDOW_HEIGHT = 334;

	/**
	 * The full steel CA chrome (docs/DECISIONS.md D27, G2 fork resolved: full) draws its edge
	 * sprites straddling the logical window's own border rather than sitting inside it (script
	 * 228's own −15 offsets), so the root has to be this many pixels larger on every edge than the
	 * 512x334 logical window, or the overhang would clip against the host. Nothing outside the
	 * chrome ({@link #header}, {@link #progressBar}, {@link #columns}) ever sees this: they still
	 * parent to the inner, exactly-512x334 {@code window} layer at coordinates unchanged from
	 * before this decision.
	 */
	private static final int CHROME = 15;
	private static final int ROOT_WIDTH = WINDOW_WIDTH + (2 * CHROME);
	private static final int ROOT_HEIGHT = WINDOW_HEIGHT + (2 * CHROME);

	/**
	 * Script 228's own chrome: a stretched background, four corners, four tiled edges. Corner
	 * sprite-to-corner assignment (TL/TR/BL/BR) and the edges' exact tiled span are this class's
	 * own reasonable read of the plan's measured numbers, not independently re-verified pixel by
	 * pixel — flagged for the live pass (docs/DECISIONS.md D27), same as D26's own open hedge.
	 */
	private static final int SPRITE_STEEL_BACKGROUND = 297;
	private static final int SPRITE_STEEL_CORNER_TL = 310;
	private static final int SPRITE_STEEL_CORNER_TR = 311;
	private static final int SPRITE_STEEL_CORNER_BL = 312;
	private static final int SPRITE_STEEL_CORNER_BR = 313;
	private static final int SPRITE_STEEL_EDGE_TOP = 314;
	private static final int SPRITE_STEEL_EDGE_RIGHT = 315;
	private static final int SPRITE_STEEL_EDGE_LEFT = 172;
	private static final int SPRITE_STEEL_EDGE_BOTTOM = 173;
	private static final int STEEL_CORNER_WIDTH = 25;
	private static final int STEEL_CORNER_HEIGHT = 30;
	private static final int STEEL_EDGE_THICKNESS = 36;

	/**
	 * Close button, script 228: 26x23 at (3,6) from the logical top-right, sprites 535 (resting) /
	 * 536 (hover) -- RuneLite's own {@code WINDOW_CLOSE_BUTTON}/{@code _HOVERED} (docs/
	 * DECISIONS.md D28). Script 4769/sprites 2289-2290, used here previously, is the Combat
	 * Achievements screen's BURGER button (D21 explicitly dropped it) and was copied by mistake.
	 */
	private static final int SPRITE_CLOSE = 535;
	private static final int SPRITE_CLOSE_HOVER = 536;
	private static final int CLOSE_WIDTH = 26;
	private static final int CLOSE_HEIGHT = 23;
	private static final int CLOSE_X = 3;
	private static final int CLOSE_Y = 6;

	/**
	 * The WIKI button, cache sprites 2420 (resting) and 2421 (hover), both 40x14 and both
	 * literally reading "WIKI". Moved into the title bar, beside the close button (docs/
	 * DECISIONS.md D25, Fork 1 resolved: Option B) — the right column's own header band it used
	 * to sit in is gone as of the same change. Inset far enough from the right that it never
	 * overlaps the close button, and vertically centred in the {@link #HEADER_HEIGHT}-tall band.
	 */
	private static final int SPRITE_WIKI = 2420;
	private static final int SPRITE_WIKI_HOVER = 2421;
	private static final int WIKI_WIDTH = 40;
	private static final int WIKI_HEIGHT = 14;
	private static final int WIKI_X = CLOSE_X + CLOSE_WIDTH + 6;

	/**
	 * The title-bar drag handle (docs/DECISIONS.md D29), wide enough to stop left of the WIKI
	 * button rather than hardcoded, so it stays correct if the button's own geometry ever moves.
	 * {@code WIKI_X + WIKI_WIDTH} is 75px measured from the right; this adds a further 6px margin
	 * so the handle never overlaps the button's own hit area.
	 */
	private static final int DRAG_HANDLE_MARGIN = 6;
	private static final int DRAG_HANDLE_WIDTH = WINDOW_WIDTH - (WIKI_X + WIKI_WIDTH) - DRAG_HANDLE_MARGIN;
	/**
	 * The collection log's own values (script 2240), not the probe's (D31). These gate when the
	 * engine starts emitting drag events at all: nothing fires until the cursor has travelled
	 * {@code DRAG_DEAD_ZONE} px AND {@code DRAG_DEAD_TIME} client cycles have passed. The probe's 8
	 * and 10 meant the window sat still for the first 8px and ~200ms of every gesture and then
	 * started tracking from wherever the cursor had already reached, so it trailed the pointer by
	 * the dead zone for the rest of the drag. 1 and 5 are what the interface we are imitating uses.
	 */
	private static final int DRAG_DEAD_ZONE = 1;
	private static final int DRAG_DEAD_TIME = 5;

	/**
	 * The drag handle's hover tint (docs/DECISIONS.md D30), read from the collection log's own
	 * chrome (script 2240 builds invisible tiled overlays over it; script 2601 sets their sprite to
	 * 1040, the same steel texture D26 already uses; script 244 flips opacity on
	 * {@code onmouserepeat}/{@code onmouseleave}). RuneLite opacity is inverted (D27), so a higher
	 * number is more transparent -- {@link #TINT_IDLE} is fully invisible and {@link #TINT_HOVER} is
	 * the CL's own faint value.
	 */
	private static final int SPRITE_HANDLE_TINT = 1040;
	private static final int TINT_IDLE = 255;
	private static final int TINT_HOVER = 200;

	/**
	 * The pressed tint (docs/DECISIONS.md D30). <b>No cache source</b> -- the collection log's own
	 * chrome (script 244) only ever sets {@link #TINT_HOVER}; this is our own invention, a live-pass
	 * tunable to confirm reads right against the hover value, not a Jagex-measured number.
	 */
	private static final int TINT_PRESSED = 160;

	/**
	 * The drag outline (docs/DECISIONS.md D29, D30): 4 concentric unfilled rectangles, insets 0-3px,
	 * colour {@link Widgets#GREY} (0x9F9F9F), opacity stepping 100/110/120/130 -- the collection
	 * log's own feathered-grey drag outline, script 2801's real drag mechanism (component 621:89).
	 * Logical-window sized (512x334, {@link #WINDOW_WIDTH}/{@link #WINDOW_HEIGHT}), not the
	 * chrome-inflated root size: it tracks where the window itself will land, not its steel frame.
	 */
	private static final int OUTLINE_RECT_COUNT = 4;
	private static final int OUTLINE_OPACITY_BASE = 100;
	private static final int OUTLINE_OPACITY_STEP = 10;

	private static final int CONTENT_X = Widgets.FRAME;
	private static final int CONTENT_Y = Widgets.FRAME;
	private static final int CONTENT_WIDTH = WINDOW_WIDTH - (2 * Widgets.FRAME);
	private static final int CONTENT_HEIGHT = WINDOW_HEIGHT - (2 * Widgets.FRAME);

	/**
	 * 717's bands, measured from the content origin: the title bar runs to y 39, the progress bar
	 * sits on it, the column headers start at 75 and the columns themselves at 98. Their bottom
	 * margin is 6, which is what fixes the column height rather than a MINUS mode.
	 */
	private static final int HEADER_HEIGHT = 39;
	private static final int COLUMN_HEADER_Y = 75;
	private static final int COLUMN_HEADER_HEIGHT = 23;
	private static final int COLUMN_Y = 98;
	private static final int COLUMN_INSET = 6;
	private static final int COLUMN_HEIGHT = CONTENT_HEIGHT - COLUMN_Y - COLUMN_INSET;

	/**
	 * The full steel CA chrome drops the old filled title band (docs/DECISIONS.md D27, G2 fork
	 * resolved: full): the title text sits directly on the steel background instead, orange,
	 * matching script 228/4836's own recipe.
	 */
	private static final int HEADER_TITLE_Y = 6;
	private static final int HEADER_TITLE_HEIGHT = 24;
	private static final int HEADER_TITLE_INSET = 6;

	/**
	 * The header divider (docs/DECISIONS.md D30), group 713's own child 10: sprite 2546, x centred,
	 * y 14, width {@code parent - 10}, height 26, tiled. Read directly from script 228 (called from
	 * 713's onLoad, script 4835, with flags bit 2 clear).
	 *
	 * <p><b>The tiling trap that makes these numbers work.</b> Sprite 2546's raster is 36x6, but its
	 * declared canvas is 36x36 with the raster drawn at {@code offsetY=15} inside it -- and the
	 * engine tiles by the sprite's CANVAS size, not its trimmed raster size, which
	 * {@code DumpSprites} prints only the latter of. A 26px-tall tiled band therefore shows exactly
	 * one 36px canvas tile, clipped, and the 6px groove sits wherever {@code offsetY} put it inside
	 * that tile -- here, window-space y 29-35 (14 + 15 through 14 + 21): below the title (which ends
	 * at y 30) and level with the close button's own bottom edge (y 29), well above the progress bar
	 * (y 48). Nothing existing moves; this only adds a line.
	 */
	private static final int SPRITE_HEADER_DIVIDER = 2546;
	private static final int DIVIDER_X = 5;
	private static final int DIVIDER_Y = 14;
	private static final int DIVIDER_HEIGHT = 26;

	/**
	 * The Combat Achievements progress bar, script 4782, in draw order: an inner border, the
	 * empty track, the fill, the label, then the outer border. Sprites 3391 (fill) and 3392
	 * (track) are both 1x27 and get tiled.
	 */
	private static final int PROGRESS_HEIGHT = 33;
	private static final int PROGRESS_INNER_BORDER = 0x474645;
	private static final int PROGRESS_OUTER_BORDER = 0x0E0E0C;
	private static final int SPRITE_PROGRESS_FILL = 3391;
	private static final int SPRITE_PROGRESS_TRACK = 3392;

	/** {@link #modelForNpc}'s result when the npc has no cache model to preview (issue #6). */
	private static final int NO_MODEL = -1;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	/**
	 * Our one dynamic child of the host component. Kept across a close (hidden, emptied) rather than
	 * recreated, so repeatedly opening and closing the window cannot pile up abandoned hidden
	 * layers on a component nothing else ever tears down. Dropped without being touched on the
	 * transitions that destroy the interface tree, and re-checked by identity before reuse
	 * (D19: identity scan, never a null check), so a stale reference can never be drawn into.
	 */
	private Widget root;

	/**
	 * The drag outline (docs/DECISIONS.md D29, D30): a second persistent host child, sibling to
	 * {@link #root} and built once with the exact same identity-scan reuse idiom (see
	 * {@link #isAttached}) -- created hidden, then only ever shown/moved/hidden by
	 * {@link #onDrag}/{@link #onDragComplete}/{@link #close}, never recreated (D22). Tracks the
	 * gesture in flight so the window itself can stay put until the drag completes, matching Andrew's
	 * approved fork: our own grey outline, not the collection log's hide-the-window-content half.
	 */
	private Widget outline;

	/**
	 * Every leaf content widget the window is made of (docs/DECISIONS.md D33, Path B): the chrome
	 * layer, the progress bar, the list header/list/detail column layers, and the header's own
	 * title/divider/WIKI/close/tint children -- everything the player can see except {@link #root},
	 * {@code window}, {@code header} and the drag handle itself, which the probe (D28's slice 6,
	 * PR #56/#57) proved must never be hidden mid-gesture or the engine's drag events stop firing.
	 * Rebuilt fresh by {@link #open} every time (cleared, then repopulated as each widget below is
	 * built), so a stale reference from a previous build can never be mutated; {@link #onDrag}/
	 * {@link #onDragComplete}/{@link #close} only ever call {@code setHidden}/{@code revalidate} on
	 * widgets already in this list, never {@code createChild}, keeping the ~7Hz drag-event path
	 * D22-clean.
	 */
	private final List<Widget> dragHidden = new ArrayList<>();

	private MechanicsList mechanicsList;
	private MechanicsDetail mechanicsDetail;

	/**
	 * The drag handle's hover-tint overlay (docs/DECISIONS.md D30), mutated (never recreated) by
	 * {@link #onDragHandleMouseRepeat}/mouse-leave. Rebuilt fresh every {@link #header}, the same
	 * lifetime as {@link #mechanicsList}/{@link #mechanicsDetail} rather than {@link #root}'s -- the
	 * whole header is rebuilt on every open() (D22).
	 */
	private Widget dragHandleTint;

	/**
	 * Whether {@code setOnHoldListener} fired since the last mouse-repeat (D30). No cache source has
	 * a pressed state to model this against; it exists purely so {@link #onDragHandleMouseRepeat}
	 * can tell a held repeat apart from a plain hovering one.
	 */
	private boolean holdSeen;

	private MechanicsView view;
	private String selectedMechanicId;

	/**
	 * The boss the selection above belongs to. Held separately because the id alone cannot say
	 * whether it is still relevant: two bosses can use the same mechanic id.
	 */
	private String previousBossId;

	/**
	 * Whether the window is currently showing. Read from the AWT thread by the Escape
	 * listener, which cannot call {@link Widget#isHidden()} because that getter asserts it
	 * is on the client thread.
	 */
	private volatile boolean windowOpen;

	private BiConsumer<String, Boolean> onRevealToggled;
	private Consumer<String> onMechanicSelected;
	private Consumer<String> onWikiOpened;

	/**
	 * Resolves a bundled sprite resource name to its registered (negative) sprite id (docs/
	 * DECISIONS.md D27). Defaults to "nothing registered" so a window built before the plugin
	 * calls {@link #setSpriteIdForName} (or in a test) never NPEs; the plugin sets the real lookup
	 * once at startup, after registering every sprite via {@code ImageUtil} +
	 * {@code client.getSpriteOverrides()} -- kept out of this package, which stays ImageUtil-free.
	 */
	private ToIntFunction<String> spriteIdForName = name -> -1;

	private final KeyListener escapeListener = new EscapeToClose();
	private boolean escapeListenerRegistered;

	/** Npc ids already logged by {@link #modelForNpc}, so a fight-length session logs once. */
	private final Set<Integer> warnedModelIds = new HashSet<>();

	/**
	 * The window's own absolute logical origin, in host coordinates (docs/DECISIONS.md D32, which
	 * unanchors D29/D30's offset model). Session state, not a config key: same lifetime as
	 * {@link #selectedMechanicId} (D29 fork 1, unchanged) -- survives close/reopen, a boss switch
	 * and a "View All" rebuild, forgotten only when the plugin instance itself does not survive (a
	 * client restart), which needs no explicit reset here since that starts a fresh
	 * {@link BossMechanicsWindow} with fresh fields anyway.
	 *
	 * <p>Seeded from the collection log's own rectangle by {@link #open} every time
	 * {@link #draggedThisSession} is still false, so an undragged window always opens covering
	 * wherever the log currently is; once the player drags, the log is never consulted again this
	 * session (D32, fork 1 resolved: unanchor ALWAYS).
	 */
	private int windowX;
	private int windowY;

	/**
	 * Whether the player has completed a drag this session (docs/DECISIONS.md D32). Set once, by
	 * {@link #onDragComplete}, and never cleared: it is the switch between {@link #open}'s two
	 * seeding behaviours for {@link #windowX}/{@link #windowY}, described there.
	 */
	private boolean draggedThisSession;

	/**
	 * Transient drag-gesture state, live only between a gesture's first {@code setOnDragListener}
	 * event and its {@code setOnDragCompleteListener} (D29). {@link #dragging} tells the first
	 * event of a gesture apart from every one after it: the first only captures
	 * {@link #dragMouseStartX}/{@link #dragMouseStartY} and {@link #dragStartWindowX}/
	 * {@link #dragStartWindowY} (what {@link #windowX}/{@link #windowY} already held before this
	 * gesture began); every later event derives the new candidate origin from the delta against
	 * those.
	 */
	private boolean dragging;
	private int dragMouseStartX;
	private int dragMouseStartY;
	private int dragStartWindowX;
	private int dragStartWindowY;

	/**
	 * The current gesture's un-committed candidate origin (docs/DECISIONS.md D30, D32) -- an
	 * ABSOLUTE origin, what {@link #windowX}/{@link #windowY} would become <b>if</b> the gesture
	 * ended right now, not an offset. Deliberately a separate pair of fields, never read by
	 * {@link #place}/{@link #replaceIfChanged}: the whole point of the outline fork is that the
	 * window itself does not move mid-gesture, so nothing that positions the window may consult
	 * these. {@link #onDrag} writes them every event after the first (to show/move the outline);
	 * {@link #onDragComplete} reads them once, to commit the final clamped value into
	 * {@link #windowX}/{@link #windowY}, and does not clear them afterward -- {@link #dragging}
	 * alone gates whether they mean anything.
	 */
	private int dragLiveX;
	private int dragLiveY;

	/**
	 * Whether {@link #dragHidden} is currently hidden for the in-progress gesture (docs/
	 * DECISIONS.md D33). A flag, deliberately, not a read of {@code widget.isHidden()}: guards
	 * {@link #setContentsHidden} so it runs once per gesture rather than on every ~7Hz drag event,
	 * the same register {@link #dragging} already uses for the outline itself.
	 */
	private boolean contentsHiddenForDrag;

	/** What "View All" / "Hide All" does: persist the new reveal state and rebuild (D18, D20). */
	public void setOnRevealToggled(BiConsumer<String, Boolean> onRevealToggled)
	{
		this.onRevealToggled = onRevealToggled;
	}

	/** Fires with the selected mechanic id, so #6 can render its preview without touching layout. */
	public void setOnMechanicSelected(Consumer<String> onMechanicSelected)
	{
		this.onMechanicSelected = onMechanicSelected;
	}

	/**
	 * Fires with the open boss's id when the WIKI button is clicked. Opening the URL is the
	 * plugin's job, deliberately: keeping {@code LinkBrowser} out of this package keeps
	 * {@code ui} a RuneLite-interface-only package.
	 */
	public void setOnWikiOpened(Consumer<String> onWikiOpened)
	{
		this.onWikiOpened = onWikiOpened;
	}

	/**
	 * @see #spriteIdForName
	 */
	public void setSpriteIdForName(ToIntFunction<String> spriteIdForName)
	{
		this.spriteIdForName = spriteIdForName;
	}

	public void onPluginStart()
	{
		// Deliberately keeps any existing root. Nothing rebuilds the host, so dropping the
		// reference here would strand one hidden layer per plugin restart; the identity scan
		// in stillAttached() makes reusing a stale reference safe.
		mechanicsList = null;
		mechanicsDetail = null;
		windowOpen = false;
	}

	public void onPluginStop()
	{
		clientThread.invokeLater(this::close);
	}

	/**
	 * Builds (or rebuilds in place) the window. Must run on the client thread; both callers, the
	 * button's op listener and the reveal toggle's op listener, already do.
	 */
	public void open(Boss boss, MechanicsView view)
	{
		Widget host = host();
		if (host == null)
		{
			log.warn("Boss Mechanics: no known window host for layout {}, window not opened",
				client.getTopLevelInterfaceId());
			return;
		}

		// Resolved before the rebuild, so a "View All" flip keeps the row the player was reading.
		this.selectedMechanicId = Selection.resolve(view, previousBossId, selectedMechanicId);
		this.previousBossId = view.getBossId();
		this.view = view;
		this.windowOpen = true;

		// The unanchor rule (docs/DECISIONS.md D32): an undragged window re-seeds from the log's
		// current position on every open, so it always opens covering wherever the log now is; once
		// draggedThisSession flips true, the log is never consulted again for the rest of the
		// session and windowX/Y are left exactly as the player last dragged them.
		if (!draggedThisSession)
		{
			seedWindowPosition(host);
		}

		if (!stillAttached(host))
		{
			root = host.createChild(-1, WidgetType.LAYER);
		}
		else
		{
			// Surgical, not deleteAllChildren() (D22, Bug B): root is a nested dynamic widget, so
			// deleteAllChildren() only nulls its own (always-empty) array and is a no-op here — the
			// real children live flat on host. See deleteChildrenOf() for the idiom that works.
			deleteChildrenOf(host, root);
		}

		// The root is now the full steel-chrome box, CHROME px larger on every edge than the
		// 512x334 logical window (docs/DECISIONS.md D27, G2): it no longer carries noClickThrough
		// itself, since the CHROME gutter around the logical window must pass clicks through to
		// the game world exactly as the area outside our old window always did.
		root.setOriginalWidth(ROOT_WIDTH);
		root.setOriginalHeight(ROOT_HEIGHT);
		root.setWidthMode(WidgetSizeMode.ABSOLUTE);
		root.setHeightMode(WidgetSizeMode.ABSOLUTE);
		root.setHidden(false);

		place(host);

		// A second, independent persistent host child (D30), built AFTER root so it is the
		// most-recently-created child of host and therefore draws over the window. Order matters
		// here: we deliberately keep the window visible during a drag (D30's sub-fork, unlike the
		// collection log which hides its content), so an outline underneath would be occluded by
		// the very window it is positioning -- for a short drag almost all of it would sit behind
		// the window and the affordance would be pointless. Built once, same reuse idiom as root,
		// and otherwise untouched by the rest of open(): only a gesture shows, moves or hides it.
		ensureOutline(host);

		// D22 correction of D14: revalidate() lays out only the receiver, immediately, against
		// its parent's *current* computed size — it never recurses into children. A fresh root's
		// computed size is 0x0 until this runs, so it must run before any ABSOLUTE_CENTER or
		// ABSOLUTE_RIGHT child is built against it (client.log, 2026-08-02 09:29:01, 09:29:04).
		root.revalidate();
		host.revalidate();

		// D33: every leaf content widget below is rebuilt fresh on every open() (D22), so any
		// widget references from a previous build are stale the moment this runs. Also drops the
		// gesture-hidden guard regardless of whatever state it was left in -- open() must leave
		// everything visible, unconditionally, per D33's contract.
		dragHidden.clear();
		contentsHiddenForDrag = false;

		// The steel chrome first, so the window's real content below draws over its inward
		// overhang rather than the frame drawing over the content — the same "frame, then
		// content" order Widgets.frame()/header()/progressBar()/columns() already relied on.
		steelChrome(root);

		// The exactly-512x334 logical window, offset by CHROME inside the enlarged root: every
		// coordinate below this point (header/progressBar/columns) is unchanged from before D27.
		Widget window = root.createChild(-1, WidgetType.LAYER);
		window.setOriginalX(CHROME);
		window.setOriginalY(CHROME);
		window.setOriginalWidth(WINDOW_WIDTH);
		window.setOriginalHeight(WINDOW_HEIGHT);
		window.setWidthMode(WidgetSizeMode.ABSOLUTE);
		window.setHeightMode(WidgetSizeMode.ABSOLUTE);
		// Without this the whole window is click-through and every click lands on the game world.
		window.setNoClickThrough(true);
		window.revalidate();

		header(window, view);
		progressBar(window, view);
		columns(window, view);

		registerEscape();

		// The two origins are the whole of the "beside the log, not over it" fix, so they are worth
		// one line: if the window is ever off again, this says whether the maths or the host moved.
		log.debug("Boss Mechanics: opened for {} on host {} ({}x{}), {}x{} at origin ({},{})",
			boss.getId(), host.getId(), host.getWidth(), host.getHeight(),
			root.getWidth(), root.getHeight(), root.getOriginalX(), root.getOriginalY());

		select(selectedMechanicId);
	}

	/**
	 * Hide and empty, never delete. There is no single-child delete, and the reference is kept
	 * so the next open reuses this layer rather than leaking another one (D19: hide, never deleteAllChildren on a component we do not own).
	 */
	public void close()
	{
		windowOpen = false;
		unregisterEscape();
		mechanicsList = null;
		mechanicsDetail = null;
		view = null;
		// selectedMechanicId and previousBossId deliberately survive: reopening the same boss's
		// screen puts you back on the row you were reading, and Selection resets it for any other.

		// windowX/Y and draggedThisSession survive too (D29 fork 1, D32, session lifetime), but the
		// in-progress flag must not: a gesture interrupted by Esc or by the log closing never gets
		// its completion event, and a stale `dragging` would make the next gesture's first event
		// continue from a dead baseline and jump the window.
		dragging = false;
		// Same reasoning for the press tint: a hold that was never released would otherwise paint
		// the first hover after reopening at the pressed level.
		holdSeen = false;
		// A gesture interrupted this way never reaches onDragComplete either, so the outline (D30)
		// could otherwise be left visible, floating, after the window it belongs to is gone.
		hideOutline();
		// Same reasoning, for the leaf content itself (D33): root gets hidden entirely just below,
		// but restoring the individual widgets too means a stale hidden flag can never survive
		// onto whatever open() reuses or rebuilds next.
		setContentsHidden(false);
		contentsHiddenForDrag = false;

		if (root != null)
		{
			root.setHidden(true);

			Widget host = host();
			if (host != null)
			{
				// Not root.deleteAllChildren() (D22, Bug B): that is a no-op on a nested dynamic
				// widget. Legal to mutate host's array here in a way it is not for Jagex's own
				// components, because every entry this removes is one of ours, by identity.
				deleteChildrenOf(host, root);
			}
		}
	}

	/** Closing the collection log takes the window with it: it is a panel *of* that log. */
	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			close();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			// The interface tree is torn down on these transitions, so the reference is already
			// invalid. Drop it without touching it, but still give the key listener back.
			unregisterEscape();
			windowOpen = false;
			root = null;
			outline = null;
			mechanicsList = null;
			mechanicsDetail = null;
			view = null;
			// D33: the widgets in dragHidden are just as invalid as root itself here, so they are
			// dropped the same way -- without being touched -- rather than mutated; only the
			// bookkeeping (the list itself, the gesture-hidden guard) needs resetting so the next
			// open() starts clean.
			dragHidden.clear();
			contentsHiddenForDrag = false;
			// The gesture itself dies with the tree too. close() has always cleared these (D29,
			// D30); this path never did, so hopping worlds mid-drag left `dragging` true and the
			// first event of the next gesture skipped its baseline capture and jumped the window
			// from a dead reference point.
			dragging = false;
			holdSeen = false;
		}
	}

	private Widget host()
	{
		int componentId = WindowHost.componentId(client.getTopLevelInterfaceId());
		return componentId == -1 ? null : client.getWidget(componentId);
	}

	/**
	 * Seeds {@link #windowX}/{@link #windowY} from the collection log's current on-screen rectangle
	 * (docs/DECISIONS.md D32), or from the host's own centre when there is no log to seed from (the
	 * window can outlive a log rebuild for a tick; centring is wrong by the sidebar/chatbox delta,
	 * but on screen and temporary, matching D20's old no-log fallback). Only ever called from
	 * {@link #open}, and only while {@link #draggedThisSession} is false -- this is the ONE place
	 * the log's rectangle is read at all; every other placement below uses the stored origin as-is.
	 */
	private void seedWindowPosition(Widget host)
	{
		Widget collectionLog = client.getWidget(InterfaceID.Collection.UNIVERSE);
		if (collectionLog == null)
		{
			windowX = (host.getWidth() - WINDOW_WIDTH) / 2;
			windowY = (host.getHeight() - WINDOW_HEIGHT) / 2;
			return;
		}

		windowX = computedOriginX(host, collectionLog);
		windowY = computedOriginY(host, collectionLog);
	}

	/**
	 * Draws the root at the session's stored absolute origin (docs/DECISIONS.md D32), clamped to
	 * whatever the host's current size is. The collection log is never read here -- {@link #open}
	 * via {@link #seedWindowPosition} is the only place that happens -- so a client resize or a
	 * collection-log move can only ever re-clamp {@link #windowX}/{@link #windowY} for drawing;
	 * neither ever mutates the stored value itself.
	 *
	 * @return true if anything moved, so callers can skip the revalidate when nothing did
	 */
	private boolean place(Widget host)
	{
		int x = WindowDrag.visibleOrigin(windowX, WINDOW_WIDTH, host.getWidth());
		int y = WindowDrag.visibleOrigin(windowY, WINDOW_HEIGHT, host.getHeight());

		// The steel-chrome root is CHROME px larger on every edge than the 512x334 logical window
		// this origin covers the log with (docs/DECISIONS.md D27, G2): the root's own origin has
		// to sit CHROME px up-left of it, so the logical window inside the root still lands here.
		return move(WindowPlacement.withChrome(x, CHROME), WindowPlacement.withChrome(y, CHROME));
	}

	/**
	 * Where {@link WindowPlacement#origin} would put the window on the x axis, used only by
	 * {@link #seedWindowPosition} (docs/DECISIONS.md D32).
	 */
	private int computedOriginX(Widget host, Widget collectionLog)
	{
		return WindowPlacement.origin(WindowPlacement.offsetInRoot(collectionLog, false),
			collectionLog.getWidth(), WindowPlacement.offsetInRoot(host, false), WINDOW_WIDTH);
	}

	/**
	 * Where {@link WindowPlacement#origin} would put the window on the y axis, used only by
	 * {@link #seedWindowPosition} (docs/DECISIONS.md D32).
	 */
	private int computedOriginY(Widget host, Widget collectionLog)
	{
		return WindowPlacement.origin(WindowPlacement.offsetInRoot(collectionLog, true),
			collectionLog.getHeight(), WindowPlacement.offsetInRoot(host, true), WINDOW_HEIGHT);
	}

	/**
	 * The window's position mode is always {@code ABSOLUTE_LEFT} now (docs/DECISIONS.md D32): there
	 * is no longer an {@code ABSOLUTE_CENTER} fallback mode to juggle, since {@link #place} always
	 * has a stored origin to draw at.
	 */
	private boolean move(int x, int y)
	{
		if (root.getXPositionMode() == WidgetPositionMode.ABSOLUTE_LEFT && root.getOriginalX() == x
			&& root.getOriginalY() == y)
		{
			return false;
		}

		root.setOriginalX(x);
		root.setOriginalY(y);
		root.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		// ABSOLUTE_LEFT and ABSOLUTE_TOP are both 0, so one mode value serves both axes.
		root.setYPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		return true;
	}

	/**
	 * Resizing the client moves the collection log, so the window has to follow it. There is no
	 * resize event on the event bus, and the log's rectangle is only readable on the client
	 * thread, so this polls — but it writes nothing unless the answer actually changed.
	 *
	 * <p>Also drives {@link MechanicsDetail#tick()} (issue #66, docs/DECISIONS.md D37), which is
	 * how a chained preview animation advances -- {@code mechanicsDetail} is null whenever nothing
	 * is open (D22: it is discarded on {@link #close}/a world hop, same as {@link #root}), so a
	 * tick arriving after that touches nothing.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		replaceIfChanged();

		if (mechanicsDetail != null)
		{
			mechanicsDetail.tick();
		}
	}

	/**
	 * Re-places the root against the host if anything about the answer changed, and only then
	 * revalidates. Shared by {@link #onClientTick} (a client resize) and the drag handle's
	 * {@code setOnDragListener} (docs/DECISIONS.md D29): both are "something that might move the
	 * window changed, recompute" — a client-thread poll and a client-thread script callback are the
	 * same register.
	 */
	private void replaceIfChanged()
	{
		if (!windowOpen || root == null)
		{
			return;
		}

		Widget host = host();
		if (host == null || !place(host))
		{
			return;
		}

		// D22 correction of D14: this only relays root out against host, and only root's own
		// origin moved, not its size or any child's geometry relative to it, so no child of root
		// needs re-laying-out here.
		root.revalidate();
		host.revalidate();
	}

	/** @see CollectionLogButton#stillAttached(Widget) — same self-healing identity scan. */
	private boolean stillAttached(Widget host)
	{
		return isAttached(host, root);
	}

	/**
	 * Whether {@code widget} is still one of {@code host}'s dynamic children, by identity, the way
	 * D19's {@link CollectionLogButton#stillAttached(Widget)} first established: a Jagex rebuild can
	 * drop our child without telling us, so a stale field reference is never trusted on its own.
	 * Shared by {@link #stillAttached} ({@link #root}) and the outline ({@link #outline}, D30) --
	 * two independent persistent host children with the same reuse idiom.
	 */
	private static boolean isAttached(Widget host, Widget widget)
	{
		if (widget == null)
		{
			return false;
		}

		Widget[] children = host.getDynamicChildren();
		if (children == null)
		{
			return false;
		}

		for (Widget child : children)
		{
			if (child == widget)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The surgical rebuild-time delete (D22, Bug B): {@code root.deleteAllChildren()} only nulls
	 * the receiver's own child array, which is always empty for a nested dynamic widget — every
	 * child {@code createChild} ever built lives flat on the static host component instead, linked
	 * by childIndex, not by parentage. Rebuilding without freeing them appended a full new copy on
	 * every "View All" flip or reopen (~70 widgets, O(n^2)), and once the flat indices reached the
	 * host interface's own component count, {@code MechanicsScrollbar}'s (now-deleted)
	 * {@code revalidateScroll()} call indexed past it and crashed (client.log, 2026-08-02 09:07:30).
	 *
	 * <p>{@code host.getChildren()} returns the client's own live array, so nulling one of its
	 * entries by identity is what the client's own {@code cc_deleteall} does, and the freed slot is
	 * reused by {@code createChild}'s append-after-last-non-null scan. This only ever nulls
	 * identities collected from {@code root}'s own subtree: {@code root} itself is never touched
	 * (it survives to be reused on the next open), and nothing that isn't ours is ever at risk,
	 * because nothing outside our subtree can be {@code contains}-equal to one of our widgets.
	 */
	private static void deleteChildrenOf(Widget host, Widget root)
	{
		Set<Widget> subtree = new HashSet<>();
		collectDynamicDescendants(root, subtree);
		if (subtree.isEmpty())
		{
			return;
		}

		Widget[] hostChildren = host.getChildren();
		if (hostChildren == null)
		{
			return;
		}

		for (int i = 0; i < hostChildren.length; i++)
		{
			if (subtree.contains(hostChildren[i]))
			{
				hostChildren[i] = null;
			}
		}
	}

	private static void collectDynamicDescendants(Widget widget, Set<Widget> into)
	{
		Widget[] children = widget.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			if (child != null && into.add(child))
			{
				collectDynamicDescendants(child, into);
			}
		}
	}

	private void header(Widget parent, MechanicsView view)
	{
		// Spans the whole logical window rather than the 9px-inset content box, so the title's y 6
		// and the close button's (7,7) are the *logical* offsets script 228/4769 use. Insetting the
		// band would push both 9px in from where the real CA screen puts them.
		Widget header = Widgets.layer(parent, 0, 0, WINDOW_WIDTH, HEADER_HEIGHT);

		// Created first, so everything else in the header (the title, WIKI, close) draws over it
		// -- later children win the draw order (docs/DECISIONS.md D29).
		dragHandle(header);

		// No filled band (docs/DECISIONS.md D27, G2 fork resolved: full steel chrome): the title
		// sits directly on the steel background (sprite 297), the same way script 228/4836's own
		// CA title does. Centred across the header the same as before.
		Widget title = Widgets.text(header, view.title(), FontID.BOLD_12, Widgets.ORANGE);
		title.setOriginalX(HEADER_TITLE_INSET);
		title.setOriginalY(HEADER_TITLE_Y);
		title.setOriginalWidth(WINDOW_WIDTH - (2 * HEADER_TITLE_INSET));
		title.setOriginalHeight(HEADER_TITLE_HEIGHT);
		title.setXTextAlignment(WidgetTextAlignment.CENTER);
		title.setYTextAlignment(WidgetTextAlignment.CENTER);
		title.revalidate();
		dragHidden.add(title);

		// The CA header divider (docs/DECISIONS.md D30): see SPRITE_HEADER_DIVIDER for the
		// canvas-vs-raster tiling trap that makes these numbers land at window-space y 29-35.
		Widget divider = Widgets.sprite(header, SPRITE_HEADER_DIVIDER, DIVIDER_X, DIVIDER_Y,
			WINDOW_WIDTH - 10, DIVIDER_HEIGHT, true);
		dragHidden.add(divider);

		wikiButton(header);
		closeButton(header);
	}

	/**
	 * A header-spanning drag handle (docs/DECISIONS.md D29, promoting issue #48 Slice 1's probe):
	 * the probe verified in the live client that {@code setClickMask(... | WidgetConfig.DRAG)}
	 * alone makes the engine's drag listener family fire, continuously, for the whole gesture --
	 * {@code setDragParent}/{@code DRAG_ON} proved unnecessary and are not used here.
	 *
	 * <p>Stops left of the WIKI button ({@link #DRAG_HANDLE_WIDTH}) so the title text and the
	 * WIKI/close buttons, built after this returns, draw over it and stay clickable.
	 */
	private void dragHandle(Widget header)
	{
		Widget handle = Widgets.layer(header, 0, 0, DRAG_HANDLE_WIDTH, HEADER_HEIGHT);
		handle.setHasListener(true);
		handle.setNoClickThrough(true);
		handle.setClickMask(handle.getClickMask() | WidgetConfig.DRAG);
		handle.setDragDeadZone(DRAG_DEAD_ZONE);
		handle.setDragDeadTime(DRAG_DEAD_TIME);
		handle.setOnDragListener((JavaScriptCallback) event -> onDrag());
		handle.setOnDragCompleteListener((JavaScriptCallback) event -> onDragComplete());

		// One overlay, created once (D22), mutated by hover/leave rather than recreated -- the CL's
		// own recipe (script 244), sprite 1040 (D26/D30), idle at TINT_IDLE (fully invisible).
		dragHandleTint = Widgets.sprite(handle, SPRITE_HANDLE_TINT, 0, 0, DRAG_HANDLE_WIDTH,
			HEADER_HEIGHT, true, TINT_IDLE);
		// D33: a child of the handle, not the handle itself, so hiding it during a drag is legal --
		// only the handle's own ancestors are forbidden to hide.
		dragHidden.add(dragHandleTint);
		handle.setOnMouseRepeatListener((JavaScriptCallback) event -> onDragHandleMouseRepeat());
		handle.setOnMouseLeaveListener((JavaScriptCallback) event -> setDragHandleTint(TINT_IDLE));
		handle.setOnHoldListener((JavaScriptCallback) event -> onDragHandleHold());
	}

	/**
	 * Mouse-repeat fires every frame the cursor sits over the handle (D30). {@link #holdSeen} tells
	 * a held repeat apart from a plain hovering one -- set by {@link #onDragHandleHold}, cleared
	 * here every repeat, so a hold has to keep firing to keep the pressed tint alive.
	 */
	private void onDragHandleMouseRepeat()
	{
		setDragHandleTint(holdSeen ? TINT_PRESSED : TINT_HOVER);
		holdSeen = false;
	}

	/**
	 * Unverified in the live client whether an op-less widget ever fires {@code setOnHoldListener}
	 * at all (D30) -- if it turns out inert, {@link #onDragHandleMouseRepeat} simply never sees
	 * {@link #holdSeen} set and slice 2's hover tint alone still works.
	 */
	private void onDragHandleHold()
	{
		holdSeen = true;
	}

	/** Guards the actual {@code setOpacity} call behind a changed-value check (mouse-repeat is per-frame). */
	private void setDragHandleTint(int opacity)
	{
		if (dragHandleTint.getOpacity() != opacity)
		{
			dragHandleTint.setOpacity(opacity);
		}
	}

	/**
	 * Builds the drag outline exactly once (docs/DECISIONS.md D22, D30), reusing {@link #outline}
	 * across opens via the same identity-scan idiom {@link #stillAttached} uses for {@link #root}.
	 * 4 concentric unfilled rectangles, insets 0-3px, {@link Widgets#GREY}, opacity stepping
	 * {@link #OUTLINE_OPACITY_BASE} by {@link #OUTLINE_OPACITY_STEP} -- the collection log's own
	 * feathered-grey drag outline (script 2801). Created hidden: nothing is dragging yet.
	 */
	private void ensureOutline(Widget host)
	{
		if (isAttached(host, outline))
		{
			return;
		}

		outline = host.createChild(-1, WidgetType.LAYER);
		outline.setOriginalWidth(WINDOW_WIDTH);
		outline.setOriginalHeight(WINDOW_HEIGHT);
		outline.setWidthMode(WidgetSizeMode.ABSOLUTE);
		outline.setHeightMode(WidgetSizeMode.ABSOLUTE);
		outline.setHidden(true);
		outline.revalidate();

		for (int i = 0; i < OUTLINE_RECT_COUNT; i++)
		{
			Widget rect = Widgets.outline(outline, i, i, WINDOW_WIDTH - (2 * i), WINDOW_HEIGHT - (2 * i),
				Widgets.GREY);
			rect.setOpacity(OUTLINE_OPACITY_BASE + (i * OUTLINE_OPACITY_STEP));
			rect.revalidate();
		}
	}

	/**
	 * Every {@code setOnDragListener} firing of one gesture, continuous rather than snap-on-release
	 * (the probe measured seven events inside one second of a single drag). The first event of a
	 * gesture only captures a baseline; every one after composes a candidate absolute origin from
	 * the delta since that baseline and shows the outline at it -- the window itself stays put
	 * until {@link #onDragComplete} (docs/DECISIONS.md D30, Fork A(a) resolved; D32): {@link
	 * #windowX}/{@link #windowY}, which {@link #place}/{@link #replaceIfChanged} actually read, are
	 * untouched here.
	 *
	 * <p>Reads {@code client.getMouseCanvasPosition()}, never {@code event.getMouseX()/getMouseY()}
	 * (docs/DECISIONS.md D29): the event's own coordinates are relative to the handle widget's own
	 * origin, which moves as the window does, so using them would feed back on itself and the
	 * window would accelerate or judder. The canvas position is absolute and immune.
	 */
	private void onDrag()
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return;
		}

		if (!dragging)
		{
			dragging = true;
			dragMouseStartX = mouse.getX();
			dragMouseStartY = mouse.getY();
			dragStartWindowX = windowX;
			dragStartWindowY = windowY;
			// Seeded, not left over: a gesture short enough to fire one event and then complete
			// would otherwise commit the PREVIOUS gesture's live origin. Clamping usually makes
			// that idempotent, but not after a clamped-at-edge release followed by a resize, where
			// a tap would jump the window back toward the old raw origin.
			dragLiveX = dragStartWindowX;
			dragLiveY = dragStartWindowY;
			return;
		}

		dragLiveX = dragStartWindowX + (mouse.getX() - dragMouseStartX);
		dragLiveY = dragStartWindowY + (mouse.getY() - dragMouseStartY);

		// D33: the first event that actually shows the outline (the second event of the gesture
		// overall, since the branch above returns on the first) is also the one that hides the
		// window's leaf content -- guarded so this runs once per gesture, not every ~7Hz event.
		if (!contentsHiddenForDrag)
		{
			setContentsHidden(true);
			contentsHiddenForDrag = true;
		}

		showOutlineAtLiveOffset();
	}

	/**
	 * Hides or restores every widget in {@link #dragHidden} (docs/DECISIONS.md D33, Path B): the
	 * window's own content, minus root/{@code window}/header/the drag handle, which must stay
	 * visible and unhidden for the whole gesture or the engine's drag events stop firing (the
	 * verified fact D33 records). Every widget here already exists -- built once by {@link #open}
	 * -- so this only ever calls {@code setHidden}/{@code revalidate} on it, matching every other
	 * mutate-not-recreate helper on this ~7Hz path ({@link #showOutlineAtLiveOffset}, D22).
	 */
	private void setContentsHidden(boolean hidden)
	{
		for (Widget widget : dragHidden)
		{
			widget.setHidden(hidden);
			widget.revalidate();
		}
	}

	/**
	 * Moves and shows the outline at the clamped candidate origin (docs/DECISIONS.md D30, D32).
	 * Deliberately <b>no</b> {@link WindowPlacement#withChrome}: the outline is logical-window sized
	 * (512x334, not the chrome-inflated root), so it lands at the same coordinates the window's own
	 * content will once {@link #onDragComplete} places {@code root} there. Never reads the
	 * collection log (D32): {@link #dragLiveX}/{@link #dragLiveY} are already absolute. Every widget
	 * here was created once by {@link #ensureOutline} (D22); this only ever calls {@code
	 * setOriginalX/Y}/{@code setHidden}/{@code revalidate} on it, so the 7Hz drag-event rate this
	 * fires at can never leak a widget.
	 */
	private void showOutlineAtLiveOffset()
	{
		Widget host = host();
		if (host == null || outline == null)
		{
			return;
		}

		int x = WindowDrag.visibleOrigin(dragLiveX, WINDOW_WIDTH, host.getWidth());
		int y = WindowDrag.visibleOrigin(dragLiveY, WINDOW_HEIGHT, host.getHeight());

		outline.setOriginalX(x);
		outline.setOriginalY(y);
		outline.setHidden(false);
		outline.revalidate();
	}

	/**
	 * Commits the gesture: clamps the candidate origin to whatever it actually landed at, rather
	 * than storing the raw accumulated delta -- otherwise a release past an edge would leave a
	 * phantom off-screen origin that the next drag has to silently "unwind" before the window
	 * visibly moves at all (docs/DECISIONS.md D29) -- then places the window there and hides the
	 * outline (D30): the window itself never moved during the gesture, so this is the one point
	 * that actually relays it. Also flips {@link #draggedThisSession} (D32): the log is never
	 * consulted again this session once this runs.
	 *
	 * <p>Restores the leaf content (D33) before {@link #replaceIfChanged()}: the window is about to
	 * become visible again at its landed position, so the content has to already be back before
	 * that happens, not after.
	 */
	private void onDragComplete()
	{
		if (!dragging)
		{
			return;
		}
		dragging = false;

		Widget host = host();
		if (host != null)
		{
			windowX = WindowDrag.visibleOrigin(dragLiveX, WINDOW_WIDTH, host.getWidth());
			windowY = WindowDrag.visibleOrigin(dragLiveY, WINDOW_HEIGHT, host.getHeight());
			draggedThisSession = true;
		}

		setContentsHidden(false);
		contentsHiddenForDrag = false;

		replaceIfChanged();
		hideOutline();
	}

	/** @see #ensureOutline -- mutation only, never a new child, matching every other outline call. */
	private void hideOutline()
	{
		if (outline != null)
		{
			outline.setHidden(true);
			outline.revalidate();
		}
	}

	/**
	 * The full steel CA chrome (docs/DECISIONS.md D27, G2 fork resolved: full): background, four
	 * corners, four tiled edges, all children of one wrapping {@code chrome} LAYER (D33) rather
	 * than direct children of {@code root} -- so a drag gesture can hide the whole frame with one
	 * {@code setHidden} instead of eleven. The wrapper is created here, in exactly the position in
	 * {@code root}'s own child order the sprites used to occupy (before the {@code window} layer
	 * built right after this returns), so draw order is unchanged when visible: the sprites'
	 * coordinates are unchanged too, since they were always root-local and the wrapper sits at
	 * root's own origin. Purely visual — verified in the live pass, matching D26's own precedent
	 * for the dim rectangle and the nine-slice frame, not with a color-pinning test.
	 */
	private void steelChrome(Widget root)
	{
		Widget chrome = Widgets.layer(root, 0, 0, ROOT_WIDTH, ROOT_HEIGHT);

		Widgets.sprite(chrome, SPRITE_STEEL_BACKGROUND, CHROME + 1, CHROME + 1,
			WINDOW_WIDTH - 2, WINDOW_HEIGHT - 2, false);

		Widgets.sprite(chrome, SPRITE_STEEL_CORNER_TL, CHROME, CHROME,
			STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);
		Widgets.sprite(chrome, SPRITE_STEEL_CORNER_TR, ROOT_WIDTH - CHROME - STEEL_CORNER_WIDTH, CHROME,
			STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);
		Widgets.sprite(chrome, SPRITE_STEEL_CORNER_BL, CHROME, ROOT_HEIGHT - CHROME - STEEL_CORNER_HEIGHT,
			STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);
		Widgets.sprite(chrome, SPRITE_STEEL_CORNER_BR, ROOT_WIDTH - CHROME - STEEL_CORNER_WIDTH,
			ROOT_HEIGHT - CHROME - STEEL_CORNER_HEIGHT, STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);

		int horizontalEdgeSpan = ROOT_WIDTH - (2 * (CHROME + STEEL_CORNER_WIDTH));
		Widgets.sprite(chrome, SPRITE_STEEL_EDGE_TOP, CHROME + STEEL_CORNER_WIDTH, 0,
			horizontalEdgeSpan, STEEL_EDGE_THICKNESS, true);
		Widgets.sprite(chrome, SPRITE_STEEL_EDGE_BOTTOM, CHROME + STEEL_CORNER_WIDTH,
			ROOT_HEIGHT - STEEL_EDGE_THICKNESS, horizontalEdgeSpan, STEEL_EDGE_THICKNESS, true);

		int verticalEdgeSpan = ROOT_HEIGHT - (2 * (CHROME + STEEL_CORNER_HEIGHT));
		Widgets.sprite(chrome, SPRITE_STEEL_EDGE_LEFT, 0, CHROME + STEEL_CORNER_HEIGHT,
			STEEL_EDGE_THICKNESS, verticalEdgeSpan, true);
		Widgets.sprite(chrome, SPRITE_STEEL_EDGE_RIGHT, ROOT_WIDTH - STEEL_EDGE_THICKNESS,
			CHROME + STEEL_CORNER_HEIGHT, STEEL_EDGE_THICKNESS, verticalEdgeSpan, true);

		dragHidden.add(chrome);
	}

	private void toggleReveal()
	{
		if (view != null && onRevealToggled != null)
		{
			onRevealToggled.accept(view.getBossId(), !view.isRevealed());
		}
	}

	/**
	 * Script 4782's five children, in its draw order, fed only by {@link MechanicsView}. The
	 * container is {@code parentWidth - 18} wide and centred, exactly as the Combat Achievements
	 * bar is, which is why the width is a constant and not a measurement.
	 */
	private void progressBar(Widget parent, MechanicsView view)
	{
		int width = CONTENT_WIDTH - 18;
		Widget bar = Widgets.layer(parent, 0, CONTENT_Y + HEADER_HEIGHT, width, PROGRESS_HEIGHT);
		bar.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		bar.revalidate();

		Widgets.outline(bar, 1, 1, width - 2, 29, PROGRESS_INNER_BORDER);
		Widgets.sprite(bar, SPRITE_PROGRESS_TRACK, 2, 2, width - 4, 27, true);

		int fill = view.progressFillWidth(width - 4);
		if (fill > 0)
		{
			Widgets.sprite(bar, SPRITE_PROGRESS_FILL, 2, 2, fill, 27, true);
		}

		Widget label = Widgets.text(bar, view.progressLabel(), FontID.PLAIN_12, Widgets.WHITE);
		label.setOriginalWidth(width);
		label.setOriginalHeight(PROGRESS_HEIGHT);
		label.setXTextAlignment(WidgetTextAlignment.CENTER);
		label.setYTextAlignment(WidgetTextAlignment.CENTER);
		label.revalidate();

		Widgets.outline(bar, 0, 0, width, 31, PROGRESS_OUTER_BORDER);

		dragHidden.add(bar);
	}

	/**
	 * Three sibling layers, not four (docs/DECISIONS.md D25, Fork 1 resolved: Option B): the list
	 * keeps its own header band, since "Mechanic" and the reveal toggle still need one, but the
	 * detail column's separate header band is gone now that the WIKI button lives in the title
	 * bar instead. The detail column starts at {@link #COLUMN_HEADER_Y}, where that band used to,
	 * and its height ({@link MechanicsDetail#COLUMN_HEIGHT}) folds the band's space in rather than
	 * leaving it empty.
	 */
	private void columns(Widget parent, MechanicsView view)
	{
		Widget listHeader = band(parent, COLUMN_HEADER_Y, MechanicsList.COLUMN_WIDTH,
			COLUMN_HEADER_HEIGHT, false);
		Widget list = band(parent, COLUMN_Y, MechanicsList.COLUMN_WIDTH, COLUMN_HEIGHT, false);
		Widget detail = band(parent, COLUMN_HEADER_Y, MechanicsDetail.COLUMN_WIDTH,
			MechanicsDetail.COLUMN_HEIGHT, true);

		mechanicsList = new MechanicsList(listHeader, list, view, this::select, this::toggleReveal,
			client::getMouseCanvasPosition);
		mechanicsList.build();

		mechanicsDetail = new MechanicsDetail(detail, this::modelForNpc, spriteIdForName,
			client::getMouseCanvasPosition);
		mechanicsDetail.build();

		// D33: all three column layers are visible content, hidden as whole units during a drag --
		// none of them sit on the drag handle's ancestor chain (that is root/window/header/handle
		// only), so hiding each in full is legal and is what D22's "single setHidden" idiom prefers
		// over hiding every row/scrollbar/model widget individually.
		dragHidden.add(listHeader);
		dragHidden.add(list);
		dragHidden.add(detail);
	}

	/**
	 * Resolves an npc id to the cache model id the animated preview renders (issue #6). Warns
	 * once per npc: the npc has no model at all, or (docs/DECISIONS.md D14's "verify this per
	 * boss" note) it has more than one and only {@code models[0]} is ever shown.
	 */
	private int modelForNpc(int npcId)
	{
		NPCComposition definition = client.getNpcDefinition(npcId);
		int[] models = definition == null ? null : definition.getModels();

		if (models == null || models.length == 0)
		{
			if (warnedModelIds.add(npcId))
			{
				log.warn("Boss Mechanics: npc {} has no model to preview", npcId);
			}
			return NO_MODEL;
		}

		if (models.length > 1 && warnedModelIds.add(npcId))
		{
			log.warn("Boss Mechanics: npc {} has {} models; the preview shows only models[0]",
				npcId, models.length);
		}

		return models[0];
	}

	/** @param fromRight measures x from the parent's right edge, which is how 717 places c5/c13. */
	private Widget band(Widget parent, int y, int width, int height, boolean fromRight)
	{
		Widget layer = Widgets.layer(parent, CONTENT_X + COLUMN_INSET, CONTENT_Y + y, width, height);
		if (fromRight)
		{
			layer.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
			layer.revalidate();
		}
		return layer;
	}

	/**
	 * Moves the selection. Repaints the left column's highlight and the right column's text, then
	 * tells #6, which is the only part of this that leaves the window.
	 */
	private void select(String mechanicId)
	{
		selectedMechanicId = mechanicId;

		if (mechanicsList != null)
		{
			mechanicsList.highlight(mechanicId);
		}
		if (mechanicsDetail != null && view != null)
		{
			mechanicsDetail.show(Selection.rowFor(view, mechanicId));
		}

		if (mechanicId != null && onMechanicSelected != null)
		{
			onMechanicSelected.accept(mechanicId);
		}
	}

	/** The WIKI button. The plugin owns the URL open, so this package stays LinkBrowser-free. */
	private void openWiki()
	{
		if (view != null && onWikiOpened != null)
		{
			onWikiOpened.accept(view.getBossId());
		}
	}

	private void closeButton(Widget header)
	{
		Widget button = Widgets.sprite(header, SPRITE_CLOSE, CLOSE_X, CLOSE_Y, CLOSE_WIDTH, CLOSE_HEIGHT, false);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		button.setAction(0, "Close");
		button.setNoClickThrough(true);
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) e -> close());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setSpriteId(SPRITE_CLOSE_HOVER));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setSpriteId(SPRITE_CLOSE));
		button.revalidate();
		dragHidden.add(button);
	}

	/**
	 * Lives in the title bar beside the close button (docs/DECISIONS.md D25, Fork 1 resolved:
	 * Option B); the right-column header band it used to occupy is gone, its 23px folded into
	 * the model box. {@code openWiki()} is the same plugin seam the old button used, just wired
	 * directly rather than through a constructor-supplied {@code Runnable}.
	 */
	private void wikiButton(Widget header)
	{
		// Centred on the close button's own row rather than on the band, so the two read as one
		// group of header buttons the way the CA screen's do.
		int y = CLOSE_Y + ((CLOSE_HEIGHT - WIKI_HEIGHT) / 2);
		Widget button = Widgets.sprite(header, SPRITE_WIKI, WIKI_X, y, WIKI_WIDTH, WIKI_HEIGHT, false);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		button.setAction(0, "Open");
		button.setNoClickThrough(true);
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) e -> openWiki());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setSpriteId(SPRITE_WIKI_HOVER));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setSpriteId(SPRITE_WIKI));
		button.revalidate();
		dragHidden.add(button);
	}

	private void registerEscape()
	{
		if (!escapeListenerRegistered)
		{
			keyManager.registerKeyListener(escapeListener);
			escapeListenerRegistered = true;
		}
	}

	private void unregisterEscape()
	{
		if (escapeListenerRegistered)
		{
			keyManager.unregisterKeyListener(escapeListener);
			escapeListenerRegistered = false;
		}
	}

	/**
	 * FORK, resolved: our window consumes Escape. One press closes the window, a second closes
	 * the collection log, which is how nested game interfaces behave. Registered only while the
	 * window is open, so Escape is the client's the rest of the time.
	 *
	 * <p>Key events arrive on the AWT thread, so the close has to hop to the client thread.
	 */
	private class EscapeToClose implements KeyListener
	{
		@Override
		public void keyTyped(KeyEvent event)
		{
		}

		@Override
		public void keyPressed(KeyEvent event)
		{
			// Deliberately a plain field rather than root.isHidden(): that getter asserts it is
			// on the client thread, and this runs on AWT, so reading it here threw and Escape
			// never reached the close below.
			if (event.getKeyCode() == KeyEvent.VK_ESCAPE && windowOpen)
			{
				event.consume();
				clientThread.invoke(BossMechanicsWindow.this::close);
			}
		}

		@Override
		public void keyReleased(KeyEvent event)
		{
		}
	}
}
