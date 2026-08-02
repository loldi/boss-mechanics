package com.bossmechanics.ui;

import com.bossmechanics.data.Boss;
import com.bossmechanics.view.MechanicsView;
import java.awt.event.KeyEvent;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

/**
 * The Boss Mechanics window: a 500x314 native-styled panel drawn over the collection log
 * (docs/DECISIONS.md D3, D20).
 *
 * <p>Deliberately dumb, like {@link CollectionLogButton}. It positions rectangles and copies
 * strings; every decision that could be wrong about what a mechanic *says* was already made in
 * {@link MechanicsView}, which is unit tested. In particular nothing here counts discoveries.
 *
 * <p>Every geometry constant below was dumped from the real cache (collection log group 621 and
 * Combat Achievements script 4782), not eyeballed.
 */
@Slf4j
public class BossMechanicsWindow
{
	/** 621 child 88 UNIVERSE: 500x314, centred on both axes. */
	private static final int WINDOW_WIDTH = 500;
	private static final int WINDOW_HEIGHT = 314;

	/**
	 * The Combat Achievements button's own nine-slice frame, reused whole. Duplicated from
	 * {@link CollectionLogButton} on purpose: sharing it means restructuring that class, which
	 * is a follow-up once the animated preview (#6) lands, not a change to make here.
	 */
	private static final int SPRITE_BACKGROUND = 297;
	private static final int SPRITE_CORNER_TL = 913;
	private static final int SPRITE_CORNER_TR = 914;
	private static final int SPRITE_CORNER_BL = 915;
	private static final int SPRITE_CORNER_BR = 916;
	private static final int SPRITE_EDGE_LEFT = 917;
	private static final int SPRITE_EDGE_TOP = 918;
	private static final int SPRITE_EDGE_RIGHT = 919;
	private static final int SPRITE_EDGE_BOTTOM = 920;

	/** Collection log close button, script 2240: 26x23 at (2,6) from the right, sprites 535/536. */
	private static final int SPRITE_CLOSE = 535;
	private static final int SPRITE_CLOSE_HOVER = 536;
	private static final int CLOSE_WIDTH = 26;
	private static final int CLOSE_HEIGHT = 23;
	private static final int CLOSE_X = 2;
	private static final int CLOSE_Y = 6;

	/**
	 * Frame thickness. The corner sprites are 9x9 so the corners are fixed; the edge sprites are
	 * 3px thick naturally and get stretched to this, exactly as the Combat Achievements button
	 * does at 50x25. If the border reads too heavy at window scale, this is the one number to
	 * tune.
	 */
	private static final int FRAME = 9;
	private static final int CONTENT_X = FRAME;
	private static final int CONTENT_Y = FRAME;
	private static final int CONTENT_WIDTH = WINDOW_WIDTH - (2 * FRAME);
	private static final int CONTENT_HEIGHT = WINDOW_HEIGHT - (2 * FRAME);

	/** 621 HEADER child 19 is 46 tall and full width; HEADER_RECT1 child 22 fills it with 0x585040. */
	private static final int HEADER_HEIGHT = 46;
	private static final int HEADER_COLOR = 0x585040;
	private static final int TITLE_X = 8;

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

	private static final int GAP = 4;
	private static final int REVEAL_WIDTH = 66;

	/**
	 * TEMPORARY DIAGNOSTIC — delete once the first in-game run confirms the window draws.
	 *
	 * <p>The FLOATER host (D20) is inferred from the cache, not observed: FLOATER is a later
	 * sibling of MAINMODAL in all six top-level layouts, so it *should* draw over the collection
	 * log. This is the issue #1 spike's move, which is the cheapest way to tell three failures
	 * apart in a single run:
	 *
	 * <ul>
	 * <li>full window draws, no magenta anywhere — hypothesis holds, delete this block;</li>
	 * <li>magenta showing through — FLOATER draws, but frame sprites are missing or mispositioned;</li>
	 * <li>nothing at all — FLOATER does not draw over MAINMODAL; fall back to MAINMODAL.</li>
	 * </ul>
	 *
	 * <p>It costs nothing while it is right: the nine-slice frame covers this rectangle exactly,
	 * so a healthy window shows no magenta at all.
	 */
	private static final boolean DIAGNOSTIC_BACKDROP = true;
	private static final int DIAGNOSTIC_COLOR = 0xFF00FF;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	/**
	 * Our one dynamic child of the FLOATER. Kept across a close (hidden, emptied) rather than
	 * recreated, so repeatedly opening and closing the window cannot pile up abandoned hidden
	 * layers on a component nothing else ever tears down. Dropped without being touched on the
	 * transitions that destroy the interface tree, and re-checked by identity before reuse
	 * (D19: identity scan, never a null check), so a stale reference can never be drawn into.
	 */
	private Widget root;

	/** #6's hook. Null while the window is closed. */
	private Widget preview;

	private MechanicsView view;
	private String selectedMechanicId;

	/**
	 * Whether the window is currently showing. Read from the AWT thread by the Escape
	 * listener, which cannot call {@link Widget#isHidden()} because that getter asserts it
	 * is on the client thread.
	 */
	private volatile boolean windowOpen;

	private BiConsumer<String, Boolean> onRevealToggled;
	private Consumer<String> onMechanicSelected;

	private final KeyListener escapeListener = new EscapeToClose();
	private boolean escapeListenerRegistered;

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

	/** The empty right-hand pane #6 fills. Null while the window is closed. */
	public Widget previewContainer()
	{
		return preview;
	}

	public void onPluginStart()
	{
		// Deliberately keeps any existing root. Nothing rebuilds the FLOATER, so dropping the
		// reference here would strand one hidden layer per plugin restart; the identity scan
		// in stillAttached() makes reusing a stale reference safe.
		preview = null;
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
		Widget floater = floater();
		if (floater == null)
		{
			log.warn("Boss Mechanics: no top-level FLOATER for layout {}, window not opened",
				client.getTopLevelInterfaceId());
			return;
		}

		this.view = view;
		this.windowOpen = true;
		this.selectedMechanicId = view.getRows().isEmpty() ? null : view.getRows().get(0).getMechanicId();

		if (!stillAttached(floater))
		{
			root = floater.createChild(-1, WidgetType.LAYER);
		}
		else
		{
			root.deleteAllChildren();
		}

		root.setOriginalWidth(WINDOW_WIDTH);
		root.setOriginalHeight(WINDOW_HEIGHT);
		root.setWidthMode(WidgetSizeMode.ABSOLUTE);
		root.setHeightMode(WidgetSizeMode.ABSOLUTE);
		root.setOriginalX(0);
		root.setOriginalY(0);
		root.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		root.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		// Without this the whole window is click-through and every click lands on the game world.
		root.setNoClickThrough(true);
		root.setHidden(false);

		if (DIAGNOSTIC_BACKDROP)
		{
			diagnosticBackdrop(root);
		}

		frame(root);
		header(root, view);
		progressBar(root, view);
		body(root, view);

		root.revalidate();
		// D14: a child computes nothing on its own; the parent layer runs the layout pass.
		floater.revalidate();

		registerEscape();

		log.debug("Boss Mechanics: opened for {} on floater {} ({}x{}), root {}x{} at ({},{})",
			boss.getId(), floater.getId(), floater.getWidth(), floater.getHeight(),
			root.getWidth(), root.getHeight(), root.getRelativeX(), root.getRelativeY());

		if (selectedMechanicId != null && onMechanicSelected != null)
		{
			onMechanicSelected.accept(selectedMechanicId);
		}
	}

	/**
	 * Hide and empty, never delete. There is no single-child delete, and the reference is kept
	 * so the next open reuses this layer rather than leaking another one (D19: hide, never deleteAllChildren on a component we do not own).
	 */
	public void close()
	{
		windowOpen = false;
		unregisterEscape();
		preview = null;
		view = null;
		selectedMechanicId = null;

		if (root != null)
		{
			root.setHidden(true);
			// Legal here in a way it is not on Jagex's components: every child is ours.
			root.deleteAllChildren();
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
			root = null;
			preview = null;
			view = null;
			selectedMechanicId = null;
		}
	}

	private Widget floater()
	{
		int componentId = TopLevelFloater.componentId(client.getTopLevelInterfaceId());
		return componentId == -1 ? null : client.getWidget(componentId);
	}

	/** @see CollectionLogButton#stillAttached(Widget) — same self-healing identity scan. */
	private boolean stillAttached(Widget floater)
	{
		if (root == null)
		{
			return false;
		}

		Widget[] children = floater.getDynamicChildren();
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

	/** See {@link #DIAGNOSTIC_BACKDROP}. First child, so everything else draws over it. */
	private void diagnosticBackdrop(Widget parent)
	{
		Widgets.filled(parent, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, DIAGNOSTIC_COLOR);
	}

	/**
	 * The Combat Achievements button's construction at window scale: background fill first, then
	 * the nine-slice border over it. Later children draw above earlier ones, so order matters.
	 */
	private void frame(Widget parent)
	{
		int right = WINDOW_WIDTH - FRAME;
		int bottom = WINDOW_HEIGHT - FRAME;
		int innerWidth = WINDOW_WIDTH - (2 * FRAME);
		int innerHeight = WINDOW_HEIGHT - (2 * FRAME);

		Widgets.sprite(parent, SPRITE_BACKGROUND, 1, 1, WINDOW_WIDTH - 2, WINDOW_HEIGHT - 2, false);

		Widgets.sprite(parent, SPRITE_CORNER_TL, 0, 0, FRAME, FRAME, false);
		Widgets.sprite(parent, SPRITE_CORNER_TR, right, 0, FRAME, FRAME, false);
		Widgets.sprite(parent, SPRITE_CORNER_BL, 0, bottom, FRAME, FRAME, false);
		Widgets.sprite(parent, SPRITE_CORNER_BR, right, bottom, FRAME, FRAME, false);
		Widgets.sprite(parent, SPRITE_EDGE_LEFT, 0, FRAME, FRAME, innerHeight, false);
		Widgets.sprite(parent, SPRITE_EDGE_RIGHT, right, FRAME, FRAME, innerHeight, false);
		Widgets.sprite(parent, SPRITE_EDGE_TOP, FRAME, 0, innerWidth, FRAME, false);
		Widgets.sprite(parent, SPRITE_EDGE_BOTTOM, FRAME, bottom, innerWidth, FRAME, false);
	}

	private void header(Widget parent, MechanicsView view)
	{
		Widget header = Widgets.layer(parent, CONTENT_X, CONTENT_Y, CONTENT_WIDTH, HEADER_HEIGHT);

		Widgets.filled(header, 0, 0, CONTENT_WIDTH, HEADER_HEIGHT, HEADER_COLOR);

		Widget title = Widgets.text(header, view.title(), FontID.BOLD_12, Widgets.WHITE);
		title.setOriginalX(TITLE_X);
		title.setOriginalWidth(CONTENT_WIDTH - TITLE_X);
		title.setOriginalHeight(HEADER_HEIGHT);
		title.setYTextAlignment(WidgetTextAlignment.CENTER);
		title.revalidate();

		revealButton(header, view);
		closeButton(header);
	}

	/**
	 * "View All" / "Hide All". The label comes from the view model, and the op listener only
	 * reports the flip; persisting it and rebuilding is the plugin's, which is what keeps the
	 * reveal state out of this class entirely.
	 */
	private void revealButton(Widget header, MechanicsView view)
	{
		Widget toggle = Widgets.text(header, view.revealActionLabel(), FontID.PLAIN_12, Widgets.ORANGE);
		toggle.setOriginalX(CLOSE_X + CLOSE_WIDTH + GAP);
		toggle.setOriginalY(CLOSE_Y);
		toggle.setOriginalWidth(REVEAL_WIDTH);
		toggle.setOriginalHeight(CLOSE_HEIGHT);
		toggle.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		toggle.setXTextAlignment(WidgetTextAlignment.RIGHT);
		toggle.setYTextAlignment(WidgetTextAlignment.CENTER);
		toggle.setAction(0, view.revealActionLabel());
		toggle.setNoClickThrough(true);
		toggle.setHasListener(true);
		toggle.setOnOpListener((JavaScriptCallback) event -> toggleReveal());
		toggle.setOnMouseOverListener((JavaScriptCallback) event -> toggle.setTextColor(Widgets.ORANGE_HOVER));
		toggle.setOnMouseLeaveListener((JavaScriptCallback) event -> toggle.setTextColor(Widgets.ORANGE));
		toggle.revalidate();
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
		Widget bar = Widgets.layer(parent, 0, CONTENT_Y + HEADER_HEIGHT + GAP, width, PROGRESS_HEIGHT);
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

	/** The mechanics list on the left, and the empty preview pane #6 fills on the right. */
	private void body(Widget parent, MechanicsView view)
	{
		int top = CONTENT_Y + HEADER_HEIGHT + GAP + PROGRESS_HEIGHT + GAP;
		int height = CONTENT_Y + CONTENT_HEIGHT - top;

		Widget column = Widgets.layer(parent, CONTENT_X, top, MechanicsList.COLUMN_WIDTH, height);
		MechanicsList.build(column, height, view, this::select);

		int width = CONTENT_WIDTH - MechanicsList.COLUMN_WIDTH - GAP;
		preview = Widgets.layer(parent, FRAME, top, width, height);
		preview.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		// TEMPORARY: proves the pane is where #6 expects it. Delete when #6 renders into it.
		Widgets.outline(preview, 0, 0, width, height, Widgets.GREY);
		preview.revalidate();
	}

	/** #6's hook. The first row is selected on open, so the preview is never empty by default. */
	private void select(String mechanicId)
	{
		selectedMechanicId = mechanicId;
		if (onMechanicSelected != null)
		{
			onMechanicSelected.accept(mechanicId);
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
