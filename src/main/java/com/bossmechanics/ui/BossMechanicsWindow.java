package com.bossmechanics.ui;

import com.bossmechanics.data.Boss;
import com.bossmechanics.view.MechanicsView;
import com.bossmechanics.view.Selection;
import java.awt.event.KeyEvent;
import java.util.HashSet;
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
import net.runelite.api.ScriptEvent;
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
	 * Issue #48, Slice 1 (the probe): a title-bar drag handle, wide enough to stop left of the
	 * WIKI button rather than hardcoded, so it stays correct if the button's own geometry ever
	 * moves. {@code WIKI_X + WIKI_WIDTH} is 75px measured from the right; this adds a further 6px
	 * margin so the handle never overlaps the button's own hit area.
	 */
	private static final int DRAG_HANDLE_MARGIN = 6;
	private static final int DRAG_HANDLE_WIDTH = WINDOW_WIDTH - (WIKI_X + WIKI_WIDTH) - DRAG_HANDLE_MARGIN;
	private static final int DRAG_DEAD_ZONE = 8;
	private static final int DRAG_DEAD_TIME = 10;

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

	private MechanicsList mechanicsList;
	private MechanicsDetail mechanicsDetail;

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
	 * Issue #48, Slice 1 (the probe): a shared counter across {@code setOnDragListener} and
	 * {@code setOnDragCompleteListener} so the log shows their relative firing order and rate,
	 * which is one of the four unknowns this slice exists to answer.
	 */
	private int dragProbeSequence;

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

		// D22 correction of D14: revalidate() lays out only the receiver, immediately, against
		// its parent's *current* computed size — it never recurses into children. A fresh root's
		// computed size is 0x0 until this runs, so it must run before any ABSOLUTE_CENTER or
		// ABSOLUTE_RIGHT child is built against it (client.log, 2026-08-02 09:29:01, 09:29:04).
		root.revalidate();
		host.revalidate();

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
			mechanicsList = null;
			mechanicsDetail = null;
			view = null;
		}
	}

	private Widget host()
	{
		int componentId = WindowHost.componentId(client.getTopLevelInterfaceId());
		return componentId == -1 ? null : client.getWidget(componentId);
	}

	/**
	 * Sits the root exactly over the collection log. See {@link WindowPlacement} for why
	 * {@code ABSOLUTE_CENTER} on the host is not the same thing and shipped 125px off.
	 *
	 * @return true if anything moved, so callers can skip the revalidate when nothing did
	 */
	private boolean place(Widget host)
	{
		Widget collectionLog = client.getWidget(InterfaceID.Collection.UNIVERSE);
		if (collectionLog == null)
		{
			// Nothing to cover (the window can outlive a log rebuild for a tick). Centring on the
			// host is wrong by the sidebar/chatbox delta, but it is on screen and it is temporary.
			return move(WidgetPositionMode.ABSOLUTE_CENTER, 0, 0);
		}

		int x = WindowPlacement.origin(WindowPlacement.offsetInRoot(collectionLog, false),
			collectionLog.getWidth(), WindowPlacement.offsetInRoot(host, false), WINDOW_WIDTH);
		int y = WindowPlacement.origin(WindowPlacement.offsetInRoot(collectionLog, true),
			collectionLog.getHeight(), WindowPlacement.offsetInRoot(host, true), WINDOW_HEIGHT);

		// The steel-chrome root is CHROME px larger on every edge than the 512x334 logical window
		// this origin covers the log with (docs/DECISIONS.md D27, G2): the root's own origin has
		// to sit CHROME px up-left of it, so the logical window inside the root still lands here.
		return move(WidgetPositionMode.ABSOLUTE_LEFT,
			WindowPlacement.withChrome(x, CHROME), WindowPlacement.withChrome(y, CHROME));
	}

	private boolean move(int positionMode, int x, int y)
	{
		if (root.getXPositionMode() == positionMode && root.getOriginalX() == x
			&& root.getOriginalY() == y)
		{
			return false;
		}

		root.setOriginalX(x);
		root.setOriginalY(y);
		root.setXPositionMode(positionMode);
		// ABSOLUTE_LEFT and ABSOLUTE_TOP are both 0, so one mode value serves both axes.
		root.setYPositionMode(positionMode);
		return true;
	}

	/**
	 * Resizing the client moves the collection log, so the window has to follow it. There is no
	 * resize event on the event bus, and the log's rectangle is only readable on the client
	 * thread, so this polls — but it writes nothing unless the answer actually changed.
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
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
		if (root == null)
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
			if (child == root)
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
		// -- later children win the draw order (issue #48, Slice 1: the probe).
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

		wikiButton(header);
		closeButton(header);
	}

	/**
	 * Issue #48, Slice 1: the probe. Instrumentation only -- nothing here moves the window.
	 * Flags a header-spanning strip as draggable and logs every {@code setOnDragListener}/
	 * {@code setOnDragCompleteListener} firing, to answer in the live client the one thing no
	 * offline analysis can settle: whether the engine's drag listener family fires at all for a
	 * widget whose only draggable-flagging is {@code setClickMask(... | WidgetConfig.DRAG)}, and
	 * if it does, what coordinate system {@code event.getMouseX()/getMouseY()} carry for a drag
	 * event -- the existing scroll-wheel listener ({@link MechanicsScrollbar#listenForWheel})
	 * proves {@code getMouseY()} can mean wheel rotation instead of a position depending on event
	 * type, so this logs {@code client.getMouseCanvasPosition()} alongside rather than assuming.
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
		handle.setOnDragListener((JavaScriptCallback) event -> logDragProbe("drag", event));
		handle.setOnDragCompleteListener((JavaScriptCallback) event -> logDragProbe("dragComplete", event));
	}

	/** @see #dragHandle */
	private void logDragProbe(String phase, ScriptEvent event)
	{
		Point canvasPosition = client.getMouseCanvasPosition();
		log.debug("Boss Mechanics: drag probe #{} {} event.getMouseX()={} event.getMouseY()={} "
				+ "client.getMouseCanvasPosition()=({},{})",
			++dragProbeSequence, phase, event.getMouseX(), event.getMouseY(),
			canvasPosition == null ? "null" : canvasPosition.getX(),
			canvasPosition == null ? "null" : canvasPosition.getY());
	}

	/**
	 * The full steel CA chrome (docs/DECISIONS.md D27, G2 fork resolved: full): background, four
	 * corners, four tiled edges, all direct children of the enlarged {@code root} in root-local
	 * coordinates so the edges' overhang (script 228's own −15 offsets) never clips against
	 * anything outside our own tree. Purely visual — verified in the live pass, matching D26's own
	 * precedent for the dim rectangle and the nine-slice frame, not with a color-pinning test.
	 */
	private void steelChrome(Widget root)
	{
		Widgets.sprite(root, SPRITE_STEEL_BACKGROUND, CHROME + 1, CHROME + 1,
			WINDOW_WIDTH - 2, WINDOW_HEIGHT - 2, false);

		Widgets.sprite(root, SPRITE_STEEL_CORNER_TL, CHROME, CHROME,
			STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);
		Widgets.sprite(root, SPRITE_STEEL_CORNER_TR, ROOT_WIDTH - CHROME - STEEL_CORNER_WIDTH, CHROME,
			STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);
		Widgets.sprite(root, SPRITE_STEEL_CORNER_BL, CHROME, ROOT_HEIGHT - CHROME - STEEL_CORNER_HEIGHT,
			STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);
		Widgets.sprite(root, SPRITE_STEEL_CORNER_BR, ROOT_WIDTH - CHROME - STEEL_CORNER_WIDTH,
			ROOT_HEIGHT - CHROME - STEEL_CORNER_HEIGHT, STEEL_CORNER_WIDTH, STEEL_CORNER_HEIGHT, false);

		int horizontalEdgeSpan = ROOT_WIDTH - (2 * (CHROME + STEEL_CORNER_WIDTH));
		Widgets.sprite(root, SPRITE_STEEL_EDGE_TOP, CHROME + STEEL_CORNER_WIDTH, 0,
			horizontalEdgeSpan, STEEL_EDGE_THICKNESS, true);
		Widgets.sprite(root, SPRITE_STEEL_EDGE_BOTTOM, CHROME + STEEL_CORNER_WIDTH,
			ROOT_HEIGHT - STEEL_EDGE_THICKNESS, horizontalEdgeSpan, STEEL_EDGE_THICKNESS, true);

		int verticalEdgeSpan = ROOT_HEIGHT - (2 * (CHROME + STEEL_CORNER_HEIGHT));
		Widgets.sprite(root, SPRITE_STEEL_EDGE_LEFT, 0, CHROME + STEEL_CORNER_HEIGHT,
			STEEL_EDGE_THICKNESS, verticalEdgeSpan, true);
		Widgets.sprite(root, SPRITE_STEEL_EDGE_RIGHT, ROOT_WIDTH - STEEL_EDGE_THICKNESS,
			CHROME + STEEL_CORNER_HEIGHT, STEEL_EDGE_THICKNESS, verticalEdgeSpan, true);
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

		mechanicsList = new MechanicsList(listHeader, list, view, this::select, this::toggleReveal);
		mechanicsList.build();

		mechanicsDetail = new MechanicsDetail(detail, this::modelForNpc, spriteIdForName);
		mechanicsDetail.build();
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
