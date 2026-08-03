package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;
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

	/**
	 * The mutable-content refactor (docs/DECISIONS.md D28, issue #47 follow-up): the text scroll
	 * box calls {@code setContentHeight} every time the selected mechanic's stacked text height
	 * changes, on a scrollbar whose widgets were already built once. Neither a new child nor a
	 * {@code revalidateScroll()} call may ever come from that, for the exact reason Bug B (D22)
	 * banned {@code revalidateScroll()} in the first place: the thumb/track/arrows are created
	 * once in {@code build()}, and {@code setContentHeight} only ever mutates their rects.
	 */
	@Test
	public void setContentHeightCreatesNoChildrenAndNeverCallsRevalidateScroll()
	{
		List<String> calls = new ArrayList<>();
		Widget list = RecordingWidget.create(calls);
		Widget bar = RecordingWidget.create(calls);

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 200, 200, 400);
		scrollbar.build();
		long createChildCallsAfterBuild = countCreateChild(calls);

		scrollbar.setContentHeight(600);

		assertEquals("setContentHeight must never create a new child (D22): the thumb/track/"
				+ "arrows are all created once, in build()",
			createChildCallsAfterBuild, countCreateChild(calls));
		assertFalse("revalidateScroll must never be called on any widget of ours (D22)",
			calls.contains("revalidateScroll"));
	}

	/**
	 * When the new content fits the viewport entirely (no scrolling needed), the thumb and arrows
	 * hide rather than draw a pointless full-height thumb (docs/DECISIONS.md D28).
	 */
	@Test
	public void setContentHeightHidesTheThumbWhenContentNowFits()
	{
		Widget list = RecordingWidget.create();
		Widget bar = RecordingWidget.create();

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 200, 200, 400);
		scrollbar.build();

		scrollbar.setContentHeight(100);

		Widget thumbMiddle = widgetNamed(bar, SPRITE_THUMB_MIDDLE_SPRITE_ID);
		assertEquals("the thumb must hide once content no longer needs to scroll", Boolean.TRUE,
			RecordingWidget.lastArgsOf(thumbMiddle, "setHidden")[0]);
	}

	private static final int SPRITE_THUMB_MIDDLE_SPRITE_ID = 790;

	/** The thumb-middle widget is the only child whose {@code setSpriteId} is 790 (tiled). */
	private static Widget widgetNamed(Widget bar, int spriteId)
	{
		for (Widget child : RecordingWidget.childrenOf(bar))
		{
			Object[] args = RecordingWidget.lastArgsOf(child, "setSpriteId");
			if (args != null && ((Integer) args[0]) == spriteId)
			{
				return child;
			}
		}
		return null;
	}

	private static long countCreateChild(List<String> calls)
	{
		return calls.stream().filter("createChild"::equals).count();
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
