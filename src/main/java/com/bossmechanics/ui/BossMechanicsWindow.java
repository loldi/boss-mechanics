package com.bossmechanics.ui;

import com.bossmechanics.data.Boss;
import com.bossmechanics.view.MechanicsView;
import com.bossmechanics.view.Selection;
import java.awt.event.KeyEvent;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
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
 * The Boss Mechanics window: a 512x334 native-styled screen drawn <b>over</b> the collection log,
 * shaped like the Combat Achievements boss screen it is reached the same way as
 * (docs/DECISIONS.md D3, D20, D21).
 *
 * <p>This class owns the shell — the frame, the title bar, the progress bar, the four column
 * layers and the selection — and hands each column to {@link MechanicsList} and
 * {@link MechanicsDetail}.
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

	/** Collection log close button, script 2240: 26x23 at (2,6) from the right, sprites 535/536. */
	private static final int SPRITE_CLOSE = 535;
	private static final int SPRITE_CLOSE_HOVER = 536;
	private static final int CLOSE_WIDTH = 26;
	private static final int CLOSE_HEIGHT = 23;
	private static final int CLOSE_X = 2;
	private static final int CLOSE_Y = 6;

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

	/** 621 HEADER_RECT1 child 22 fills the title bar with 0x585040. */
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
	 * The empty box #6 fills: the 291x110 model box at the top of the right-hand column. Null
	 * while the window is closed. Stays valid across a selection change, because the right column
	 * mutates its text in place rather than rebuilding.
	 */
	public Widget previewContainer()
	{
		return mechanicsDetail == null ? null : mechanicsDetail.modelBox();
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
			root.deleteAllChildren();
		}

		root.setOriginalWidth(WINDOW_WIDTH);
		root.setOriginalHeight(WINDOW_HEIGHT);
		root.setWidthMode(WidgetSizeMode.ABSOLUTE);
		root.setHeightMode(WidgetSizeMode.ABSOLUTE);
		// Without this the whole window is click-through and every click lands on the game world.
		root.setNoClickThrough(true);
		root.setHidden(false);

		place(host);

		Widgets.frame(root, WINDOW_WIDTH, WINDOW_HEIGHT);
		header(root, view);
		progressBar(root, view);
		columns(root, view);

		root.revalidate();
		// D14: a child computes nothing on its own; the parent layer runs the layout pass.
		host.revalidate();

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

		return move(WidgetPositionMode.ABSOLUTE_LEFT, x, y);
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

		root.revalidate();
		// D14: the child computes nothing on its own; the parent layer runs the layout pass.
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

		closeButton(header);
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
	 * 717's two columns and their two header bands: the mechanics list on the left, the selected
	 * mechanic's detail on the right. Four sibling layers rather than two nested ones, because
	 * that is how the Combat Achievements screen is built and it keeps each header band's
	 * right-aligned button measured from its own column's edge.
	 */
	private void columns(Widget parent, MechanicsView view)
	{
		Widget listHeader = band(parent, COLUMN_HEADER_Y, MechanicsList.COLUMN_WIDTH,
			COLUMN_HEADER_HEIGHT, false);
		Widget list = band(parent, COLUMN_Y, MechanicsList.COLUMN_WIDTH, COLUMN_HEIGHT, false);
		Widget detailHeader = band(parent, COLUMN_HEADER_Y, MechanicsDetail.COLUMN_WIDTH,
			COLUMN_HEADER_HEIGHT, true);
		Widget detail = band(parent, COLUMN_Y, MechanicsDetail.COLUMN_WIDTH, COLUMN_HEIGHT, true);

		mechanicsList = new MechanicsList(listHeader, list, view, this::select, this::toggleReveal);
		mechanicsList.build();

		mechanicsDetail = new MechanicsDetail(detailHeader, detail, this::openWiki);
		mechanicsDetail.build();
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
