package com.bossmechanics.ui;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ScriptEvent;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

/**
 * Bug B's crash (docs/DECISIONS.md D22): {@code revalidateScroll()} indexes the STATIC group
 * array of the top-level interface with a DYNAMIC widget's flat-array child-index watermark, an
 * upstream RuneLite mixin bug that throws once a rebuild has pushed that watermark past the
 * group's own component count (client.log, 2026-08-02 09:07:30). It must never be called on any
 * widget of ours, whether from the initial build or from the wheel scroll it wires up, regardless
 * of geometry — so this Proxy stands in for the real widget tree the injected client would supply.
 */
public class MechanicsScrollbarTest
{
	@Test
	public void buildAndWheelScrollNeverCallRevalidateScroll()
	{
		List<String> calls = new ArrayList<>();
		Widget list = RecordingWidget.create(calls);
		Widget bar = RecordingWidget.create(calls);

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 200, 200, 400);
		scrollbar.build();

		JavaScriptCallback wheel =
			(JavaScriptCallback) RecordingWidget.listenerOf(list, "setOnScrollWheelListener");
		wheel.run(scriptEventWithMouseY(1));

		assertFalse("revalidateScroll must never be called on any widget of ours (D22)",
			calls.contains("revalidateScroll"));
	}

	private static ScriptEvent scriptEventWithMouseY(int mouseY)
	{
		return (ScriptEvent) Proxy.newProxyInstance(ScriptEvent.class.getClassLoader(),
			new Class<?>[] { ScriptEvent.class },
			(proxy, method, args) -> "getMouseY".equals(method.getName())
				? mouseY
				: RecordingWidget.defaultFor(method.getReturnType()));
	}
}
