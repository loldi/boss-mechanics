package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.bossmechanics.data.Boss;
import com.bossmechanics.detection.DiscoveryState;
import com.bossmechanics.view.MechanicsView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.ScriptEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import org.junit.Test;

/**
 * Bug A (docs/DECISIONS.md D22): {@code open()} used to build every child of a fresh root before
 * ever laying it out, so a fresh root's computed size was 0x0 while its {@code ABSOLUTE_CENTER}
 * and {@code ABSOLUTE_RIGHT} children were positioned against it (client.log, 2026-08-02 09:29:01,
 * 09:29:04). The fix is purely an ordering one — {@code revalidate()} has to run on a fresh root
 * before its first child is built — which is what this pins down, without a live widget tree
 * behind either call.
 */
public class BossMechanicsWindowLayoutTest
{
	@Test
	public void freshRootIsLaidOutBeforeItsFirstChildIsBuilt() throws Exception
	{
		Widget host = RecordingWidget.create();
		Client client = fakeClient(host);

		BossMechanicsWindow window = new BossMechanicsWindow();
		inject(window, "client", client);
		inject(window, "clientThread", new ClientThread());
		inject(window, "keyManager", fakeKeyManager(client));

		window.open(emptyBoss(), MechanicsView.of(emptyBoss(), new DiscoveryState(), false));

		Widget root = RecordingWidget.childrenOf(host).get(0);
		List<String> rootCalls = RecordingWidget.callsOf(root);

		assertTrue("a fresh root must be laid out before its first child is built (D22, Bug A)",
			rootCalls.indexOf("revalidate") >= 0
				&& rootCalls.indexOf("revalidate") < rootCalls.indexOf("createChild"));
	}

	/**
	 * The WIKI button moved into the title bar (Fork 1, resolved: Option B, docs/DECISIONS.md D25)
	 * beside the close button, when the now-empty right-column header band it used to live in was
	 * removed. This is currently unpinned: nothing asserted the click ever reaches the plugin seam.
	 */
	@Test
	public void wikiClickReportsTheBossId() throws Exception
	{
		Widget host = RecordingWidget.create();
		Client client = fakeClient(host);

		BossMechanicsWindow window = new BossMechanicsWindow();
		inject(window, "client", client);
		inject(window, "clientThread", new ClientThread());
		inject(window, "keyManager", fakeKeyManager(client));

		List<String> reportedBossIds = new ArrayList<>();
		window.setOnWikiOpened(reportedBossIds::add);

		Boss boss = emptyBoss();
		window.open(boss, MechanicsView.of(boss, new DiscoveryState(), false));

		Widget wikiButton = findWidgetWithAction(host, "Open");
		assertNotNull("expected a widget somewhere in the tree carrying the WIKI button's "
			+ "action string (\"Open\", BossMechanicsWindow.wikiButton())", wikiButton);

		JavaScriptCallback listener =
			(JavaScriptCallback) RecordingWidget.listenerOf(wikiButton, "setOnOpListener");
		listener.run(null);

		assertEquals("the WIKI click must report the open boss's id through setOnWikiOpened",
			Collections.singletonList(boss.getId()), reportedBossIds);
	}

	/**
	 * The full steel CA chrome (docs/DECISIONS.md D27, G2 fork resolved: full) drops the title's
	 * old filled band and paints the text directly on the steel background, orange, matching
	 * script 228/4836's own title recipe rather than the old white-on-0x585040.
	 */
	@Test
	public void titleTextIsSteelChromeOrange() throws Exception
	{
		Widget host = RecordingWidget.create();
		Client client = fakeClient(host);

		BossMechanicsWindow window = new BossMechanicsWindow();
		inject(window, "client", client);
		inject(window, "clientThread", new ClientThread());
		inject(window, "keyManager", fakeKeyManager(client));

		Boss boss = emptyBoss();
		MechanicsView view = MechanicsView.of(boss, new DiscoveryState(), false);
		window.open(boss, view);

		Widget title = findWidgetWithText(host, view.title());
		assertNotNull("expected a widget somewhere in the tree carrying the window's own title "
			+ "text (\"" + view.title() + "\")", title);

		assertEquals("the title must be steel-chrome orange (docs/DECISIONS.md D27, G2), not the "
				+ "old white-on-filled-band",
			Widgets.ORANGE,
			((Integer) RecordingWidget.lastArgsOf(title, "setTextColor")[0]).intValue());
	}

	/**
	 * Issue #48, Slice 1 (the probe): pins the drag handle's wiring, which is the one unverified
	 * engine assumption the whole feature rests on -- that a widget flagged draggable via
	 * {@code setClickMask(... | WidgetConfig.DRAG)} is what actually makes the engine's drag
	 * listener family fire, independent of {@code setOnDragListener} itself. Nothing about
	 * placement is asserted here; that is deliberately deferred past this slice.
	 */
	@Test
	public void dragHandleIsWiredForDragging() throws Exception
	{
		Widget host = RecordingWidget.create();
		Client client = fakeClient(host);

		BossMechanicsWindow window = new BossMechanicsWindow();
		inject(window, "client", client);
		inject(window, "clientThread", new ClientThread());
		inject(window, "keyManager", fakeKeyManager(client));

		Boss boss = emptyBoss();
		window.open(boss, MechanicsView.of(boss, new DiscoveryState(), false));

		List<Widget> dragWidgets = new ArrayList<>();
		collectWidgetsWithDragListener(host, dragWidgets);

		assertEquals("expected exactly one widget wired with setOnDragListener "
				+ "(BossMechanicsWindow.dragHandle())",
			1, dragWidgets.size());

		Widget dragHandle = dragWidgets.get(0);
		Object[] clickMaskArgs = RecordingWidget.lastArgsOf(dragHandle, "setClickMask");
		assertNotNull("the drag handle must call setClickMask", clickMaskArgs);

		int clickMask = (Integer) clickMaskArgs[0];
		assertTrue("the drag handle's click mask must include WidgetConfig.DRAG, or the engine's "
				+ "drag listener family will very likely never fire",
			(clickMask & WidgetConfig.DRAG) != 0);
	}

	/**
	 * Issue #48, Slice 3: a zero drag offset must reproduce today's placement exactly (docs/
	 * DECISIONS.md D29). {@code 113 = withChrome(origin(134,500,0,512), 15)}: the collection log
	 * starts at x 134, is 500 wide; the host starts at x 0 and is 765 wide.
	 */
	@Test
	public void placementWithoutADragIsUnchanged() throws Exception
	{
		Widget host = hostWidget();
		Widget collectionLog = collectionLogWidget();
		Client client = fakeClient(host, collectionLog, new Point[1]);

		BossMechanicsWindow window = new BossMechanicsWindow();
		inject(window, "client", client);
		inject(window, "clientThread", new ClientThread());
		inject(window, "keyManager", fakeKeyManager(client));

		window.open(emptyBoss(), MechanicsView.of(emptyBoss(), new DiscoveryState(), false));

		Widget root = lastChildOf(host);
		assertEquals("a zero drag offset must reproduce today's placement exactly (D29)",
			113, ((Integer) RecordingWidget.lastArgsOf(root, "setOriginalX")[0]).intValue());
	}

	/**
	 * Issue #48, Slice 3: dragging moves the window live (every {@code setOnDragListener} event
	 * after the first moves it, not just the release), and the resulting offset is session state
	 * that survives a "View All" rebuild (fork 1, resolved: same lifetime as
	 * {@code selectedMechanicId}). The first event of a gesture only captures a baseline; this
	 * drives it twice so the second event is the one that actually composes a delta.
	 *
	 * <p>{@code client.getMouseCanvasPosition()} is the position source, deliberately never
	 * {@code event.getMouseX()/getMouseY()} (D29): the latter is measured relative to the handle's
	 * own origin, which moves as the window does, so it would feed back on itself.
	 */
	@Test
	public void draggedOffsetSurvivesARebuild() throws Exception
	{
		Widget host = hostWidget();
		Widget collectionLog = collectionLogWidget();
		Point[] mousePosition = new Point[1];
		Client client = fakeClient(host, collectionLog, mousePosition);

		BossMechanicsWindow window = new BossMechanicsWindow();
		inject(window, "client", client);
		inject(window, "clientThread", new ClientThread());
		inject(window, "keyManager", fakeKeyManager(client));

		Boss boss = emptyBoss();
		window.open(boss, MechanicsView.of(boss, new DiscoveryState(), false));

		JavaScriptCallback onDrag = dragListenerOf(host);
		mousePosition[0] = new Point(300, 200);
		onDrag.run(fakeScriptEvent());
		mousePosition[0] = new Point(340, 225);
		onDrag.run(fakeScriptEvent());

		Widget draggedRoot = lastChildOf(host);
		assertEquals("computed origin 128 + delta 40, in bounds, minus 15 chrome",
			153, ((Integer) RecordingWidget.lastArgsOf(draggedRoot, "setOriginalX")[0]).intValue());

		// The "View All" rebuild path: open() again, same offset, same math.
		window.open(boss, MechanicsView.of(boss, new DiscoveryState(), false));

		Widget rebuiltRoot = lastChildOf(host);
		assertEquals("a dragged offset must survive a View All rebuild (fork 1, session lifetime)",
			153, ((Integer) RecordingWidget.lastArgsOf(rebuiltRoot, "setOriginalX")[0]).intValue());
	}

	/**
	 * Issue #48, Slice 3: releasing past the edge must normalize the stored offset to the clamped
	 * position actually reached, not the raw (off-screen) accumulated delta -- otherwise the next
	 * drag has to silently "unwind" the phantom off-screen offset before the window visibly moves
	 * at all. Drags +400 (clamps hard against the right edge), releases, then drags -10 and expects
	 * the window to move by the full 10px immediately.
	 */
	@Test
	public void releasingPastTheEdgeStoresTheClampedOffset() throws Exception
	{
		Widget host = hostWidget();
		Widget collectionLog = collectionLogWidget();
		Point[] mousePosition = new Point[1];
		Client client = fakeClient(host, collectionLog, mousePosition);

		BossMechanicsWindow window = new BossMechanicsWindow();
		inject(window, "client", client);
		inject(window, "clientThread", new ClientThread());
		inject(window, "keyManager", fakeKeyManager(client));

		Boss boss = emptyBoss();
		window.open(boss, MechanicsView.of(boss, new DiscoveryState(), false));

		Widget root = lastChildOf(host);
		JavaScriptCallback onDrag = dragListenerOf(host);
		JavaScriptCallback onDragComplete = dragCompleteListenerOf(host);

		// Drag +400: clamps hard against the right edge (computed origin 128, host 765 wide,
		// window 512 wide -- max origin is 253).
		mousePosition[0] = new Point(300, 200);
		onDrag.run(fakeScriptEvent());
		mousePosition[0] = new Point(700, 200);
		onDrag.run(fakeScriptEvent());
		assertEquals("clamped hard against the right edge before release",
			238, ((Integer) RecordingWidget.lastArgsOf(root, "setOriginalX")[0]).intValue());

		onDragComplete.run(fakeScriptEvent());

		// Drag -10: if the stored offset were the raw (400) delta rather than the clamped one
		// (125), this would still compute a clamped 253 and the window would not move at all.
		mousePosition[0] = new Point(700, 200);
		onDrag.run(fakeScriptEvent());
		mousePosition[0] = new Point(690, 200);
		onDrag.run(fakeScriptEvent());

		assertEquals("the window must respond immediately to the small drag back, not silently "
				+ "unwind a phantom off-screen offset first",
			228, ((Integer) RecordingWidget.lastArgsOf(root, "setOriginalX")[0]).intValue());
	}

	/** Host: 765x503, at the coordinate-space origin, with no parent (D29's slice 3 fixture). */
	private static Widget hostWidget()
	{
		Widget host = RecordingWidget.create();
		RecordingWidget.returning(host, "getWidth", 765);
		RecordingWidget.returning(host, "getHeight", 503);
		return host;
	}

	/** The collection log's own UNIVERSE widget (D21), stubbed per the slice 3 fixture. */
	private static Widget collectionLogWidget()
	{
		Widget collectionLog = RecordingWidget.create();
		RecordingWidget.returning(collectionLog, "getRelativeX", 134);
		RecordingWidget.returning(collectionLog, "getRelativeY", 94);
		RecordingWidget.returning(collectionLog, "getWidth", 500);
		RecordingWidget.returning(collectionLog, "getHeight", 314);
		return collectionLog;
	}

	/** The most recently created immediate child of {@code host} -- the current window root. */
	private static Widget lastChildOf(Widget host)
	{
		List<Widget> children = RecordingWidget.childrenOf(host);
		return children.get(children.size() - 1);
	}

	private static JavaScriptCallback dragListenerOf(Widget host)
	{
		List<Widget> dragWidgets = new ArrayList<>();
		collectWidgetsWithDragListener(host, dragWidgets);
		return (JavaScriptCallback) RecordingWidget.listenerOf(dragWidgets.get(0), "setOnDragListener");
	}

	private static JavaScriptCallback dragCompleteListenerOf(Widget host)
	{
		List<Widget> dragWidgets = new ArrayList<>();
		collectWidgetsWithDragListener(host, dragWidgets);
		return (JavaScriptCallback) RecordingWidget.listenerOf(dragWidgets.get(0), "setOnDragCompleteListener");
	}

	/**
	 * A dummy stand-in, the {@code MechanicsScrollbarTest} idiom: production code reads the drag
	 * position from {@code client.getMouseCanvasPosition()}, deliberately never from the event
	 * itself (D29), so nothing here needs to answer any particular method.
	 */
	private static ScriptEvent fakeScriptEvent()
	{
		return (ScriptEvent) Proxy.newProxyInstance(ScriptEvent.class.getClassLoader(),
			new Class<?>[] { ScriptEvent.class },
			(proxy, method, args) -> RecordingWidget.defaultFor(method.getReturnType()));
	}

	/** Depth-first search for every widget in the tree wired with {@code setOnDragListener}. */
	private static void collectWidgetsWithDragListener(Widget widget, List<Widget> into)
	{
		if (RecordingWidget.listenerOf(widget, "setOnDragListener") != null)
		{
			into.add(widget);
		}
		for (Widget child : RecordingWidget.childrenOf(widget))
		{
			collectWidgetsWithDragListener(child, into);
		}
	}

	/** Depth-first search for the widget whose most recent {@code setAction(0, action)} matches. */
	private static Widget findWidgetWithAction(Widget widget, String action)
	{
		Object[] args = RecordingWidget.lastArgsOf(widget, "setAction");
		if (args != null && args.length == 2 && action.equals(args[1]))
		{
			return widget;
		}
		for (Widget child : RecordingWidget.childrenOf(widget))
		{
			Widget found = findWidgetWithAction(child, action);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	/** Depth-first search for the widget whose most recent {@code setText(...)} matches. */
	private static Widget findWidgetWithText(Widget widget, String text)
	{
		Object[] args = RecordingWidget.lastArgsOf(widget, "setText");
		if (args != null && args.length == 1 && text.equals(args[0]))
		{
			return widget;
		}
		for (Widget child : RecordingWidget.childrenOf(widget))
		{
			Widget found = findWidgetWithText(child, text);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}

	private static Boss emptyBoss()
	{
		return new Boss("empty", "Empty", Collections.singletonList(1), "https://example.com",
			Collections.emptyList());
	}

	/** Only {@code getTopLevelInterfaceId} and {@code getWidget(int)} are on {@code open()}'s path. */
	private static Client fakeClient(Widget host)
	{
		// No collection log: place()'s no-log-found branch, which never touches the host's
		// geometry -- unaffected by issue #48's drag offset either, since there is nothing to drag
		// relative to yet.
		return fakeClient(host, null, new Point[1]);
	}

	/**
	 * Issue #48, Slice 3: also resolves the collection log widget ({@code InterfaceID.Collection
	 * .UNIVERSE}, D21) and answers {@code getMouseCanvasPosition()} from a mutable single-element
	 * array, so a test can move the "mouse" between successive drag events the way the real client
	 * would.
	 */
	private static Client fakeClient(Widget host, Widget collectionLog, Point[] mouseCanvasPosition)
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] { Client.class },
			(proxy, method, args) -> {
				switch (method.getName())
				{
					case "getTopLevelInterfaceId":
						return InterfaceID.TOPLEVEL_OSRS_STRETCH;
					case "getWidget":
						if (args != null && args.length == 1 && args[0] instanceof Integer)
						{
							int componentId = (Integer) args[0];
							if (componentId == InterfaceID.ToplevelOsrsStretch.UI_HIGHLIGHTS)
							{
								return host;
							}
							if (componentId == InterfaceID.Collection.UNIVERSE)
							{
								return collectionLog;
							}
						}
						return null;
					case "getMouseCanvasPosition":
						return mouseCanvasPosition[0];
					default:
						return RecordingWidget.defaultFor(method.getReturnType());
				}
			});
	}

	/** A real {@link KeyManager}: its constructor is private, but nothing about it needs faking. */
	private static KeyManager fakeKeyManager(Client client) throws Exception
	{
		Constructor<KeyManager> constructor = KeyManager.class.getDeclaredConstructor(Client.class, EventBus.class);
		constructor.setAccessible(true);
		return constructor.newInstance(client, new EventBus());
	}

	private static void inject(Object target, String fieldName, Object value) throws Exception
	{
		Field field = BossMechanicsWindow.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
