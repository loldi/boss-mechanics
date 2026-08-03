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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
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

	private static Boss emptyBoss()
	{
		return new Boss("empty", "Empty", Collections.singletonList(1), "https://example.com",
			Collections.emptyList());
	}

	/** Only {@code getTopLevelInterfaceId} and {@code getWidget(int)} are on {@code open()}'s path. */
	private static Client fakeClient(Widget host)
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] { Client.class },
			(proxy, method, args) -> {
				switch (method.getName())
				{
					case "getTopLevelInterfaceId":
						return InterfaceID.TOPLEVEL_OSRS_STRETCH;
					case "getWidget":
						// The collection log lookup also lands here; returning null for it takes
						// place()'s no-log-found branch, which never touches the host's geometry.
						return args != null && args.length == 1 && args[0] instanceof Integer
							&& (Integer) args[0] == InterfaceID.ToplevelOsrsStretch.UI_HIGHLIGHTS
							? host
							: null;
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
