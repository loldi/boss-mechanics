package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Point;
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

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 200, 200, 400,
			MechanicsScrollbar.Chrome.ONLY_WHEN_SCROLLABLE, () -> null);
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

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 200, 200, 400,
			MechanicsScrollbar.Chrome.ONLY_WHEN_SCROLLABLE, () -> null);
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

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 200, 200, 400,
			MechanicsScrollbar.Chrome.ONLY_WHEN_SCROLLABLE, () -> null);
		scrollbar.build();

		scrollbar.setContentHeight(100);

		Widget thumbMiddle = widgetNamed(bar, SPRITE_THUMB_MIDDLE_SPRITE_ID);
		assertEquals("the thumb must hide once content no longer needs to scroll", Boolean.TRUE,
			RecordingWidget.lastArgsOf(thumbMiddle, "setHidden")[0]);
	}

	/**
	 * The two bars differ (docs/DECISIONS.md D28): the text box's hides its whole scrollbar when the
	 * copy fits, but the list column's keeps its track and arrows, because the CA screen we mirror
	 * draws that chrome unconditionally and the column reads as a cut-off box without it. Only the
	 * thumb goes. Andrew's in-game pass on #47 is the evidence for the list side.
	 */
	@Test
	public void chromeAlwaysKeepsTheTrackAndArrowsWhenContentFits()
	{
		Widget list = RecordingWidget.create();
		Widget bar = RecordingWidget.create();

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 200, 200, 400,
			MechanicsScrollbar.Chrome.ALWAYS, () -> null);
		scrollbar.build();

		scrollbar.setContentHeight(100);

		assertEquals("the list column's track must stay drawn when its rows fit", Boolean.FALSE,
			RecordingWidget.lastArgsOf(widgetNamed(bar, SPRITE_TRACK_ID), "setHidden")[0]);
		assertEquals("the list column's arrows must stay drawn when its rows fit", Boolean.FALSE,
			RecordingWidget.lastArgsOf(widgetNamed(bar, SPRITE_ARROW_UP_ID), "setHidden")[0]);
		assertEquals("the thumb still hides -- there is nothing to indicate", Boolean.TRUE,
			RecordingWidget.lastArgsOf(widgetNamed(bar, SPRITE_THUMB_MIDDLE_SPRITE_ID), "setHidden")[0]);
	}

	/**
	 * docs/DECISIONS.md D35: dragging the capture layer over the track scrolls the list
	 * proportionally. {@code barHeight} 122 -> {@code trackHeight} 90 (122 - 2*16); content 180
	 * over a 90px viewport -> {@code thumbHeight} 45 ({@code trackHeight * viewportHeight /
	 * contentHeight}), so {@code travel} is 45 and {@code maxScroll} is 90. The first drag event
	 * only captures the gesture's baseline (mouse canvas position and starting scroll) and must
	 * not scroll anything itself; the second, a 10px move, scrolls proportionally and repositions
	 * the thumb sprite to match.
	 */
	@Test
	public void draggingTheThumbScrollsProportionally()
	{
		Widget list = RecordingWidget.create();
		Widget bar = RecordingWidget.create();
		Point[] mouse = new Point[1];

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 122, 90, 180,
			MechanicsScrollbar.Chrome.ALWAYS, () -> mouse[0]);
		scrollbar.build();
		long scrollYCallsAfterBuild = countSetScrollY(list);

		JavaScriptCallback drag = dragListenerOf(bar);

		mouse[0] = new Point(0, 100);
		drag.run(fakeScriptEvent());
		assertEquals("the first drag event of a gesture must only capture a baseline, never scroll",
			scrollYCallsAfterBuild, countSetScrollY(list));

		mouse[0] = new Point(0, 110);
		drag.run(fakeScriptEvent());

		assertEquals("a 10px drag over a 45px travel / 90px maxScroll must scroll proportionally",
			20, ((Integer) RecordingWidget.lastArgsOf(list, "setScrollY")[0]).intValue());
		assertEquals("the thumb sprite must reposition to match: ARROW_SIZE (16) + travel(45) * "
				+ "scrollY(20) / maxScroll(90) = 26",
			26, ((Integer) RecordingWidget.lastArgsOf(widgetNamed(bar, SPRITE_THUMB_TOP_ID),
				"setOriginalY")[0]).intValue());
	}

	/**
	 * Dragging past either end of the track clamps rather than overshooting, and -- because scroll
	 * state is clamped and applied on every event, with no committed-vs-live split to protect
	 * (docs/DECISIONS.md D35) -- picking the cursor back up mid-gesture scrolls immediately from
	 * the gesture's original baseline rather than first "unwinding" a phantom offset.
	 */
	@Test
	public void draggingPastTheTrackEndClampsAndNeverLeavesAPhantom()
	{
		Widget list = RecordingWidget.create();
		Widget bar = RecordingWidget.create();
		Point[] mouse = new Point[1];

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 122, 90, 180,
			MechanicsScrollbar.Chrome.ALWAYS, () -> mouse[0]);
		scrollbar.build();

		JavaScriptCallback drag = dragListenerOf(bar);

		mouse[0] = new Point(0, 100);
		drag.run(fakeScriptEvent());
		mouse[0] = new Point(0, 1100);
		drag.run(fakeScriptEvent());

		assertEquals("a drag far past the track end must clamp to maxScroll, never overshoot it",
			90, ((Integer) RecordingWidget.lastArgsOf(list, "setScrollY")[0]).intValue());

		mouse[0] = new Point(0, 120);
		drag.run(fakeScriptEvent());

		assertEquals("dragging back from a clamped position must respond immediately from the "
				+ "gesture's original baseline (100), not unwind a phantom offset first: delta 20 "
				+ "over 45 travel / 90 maxScroll = 40",
			40, ((Integer) RecordingWidget.lastArgsOf(list, "setScrollY")[0]).intValue());
	}

	/** The D22 pin (revalidateScroll banned, no rebuild-shaped createChild) extended to the drag path. */
	@Test
	public void thumbDragCreatesNoChildrenAndNeverCallsRevalidateScroll()
	{
		List<String> calls = new ArrayList<>();
		Widget list = RecordingWidget.create(calls);
		Widget bar = RecordingWidget.create(calls);
		Point[] mouse = new Point[1];

		MechanicsScrollbar scrollbar = new MechanicsScrollbar(list, bar, 122, 90, 180,
			MechanicsScrollbar.Chrome.ALWAYS, () -> mouse[0]);
		scrollbar.build();
		long createChildCallsAfterBuild = countCreateChild(calls);

		JavaScriptCallback drag = dragListenerOf(bar);
		JavaScriptCallback dragComplete = dragCompleteListenerOf(bar);

		mouse[0] = new Point(0, 100);
		drag.run(fakeScriptEvent());
		mouse[0] = new Point(0, 130);
		drag.run(fakeScriptEvent());
		dragComplete.run(fakeScriptEvent());

		assertEquals("a thumb drag must never create a new child (D22): the capture layer and the "
				+ "thumb sprites it moves are all created once, in build()",
			createChildCallsAfterBuild, countCreateChild(calls));
		assertFalse("revalidateScroll must never be called on any widget of ours (D22), including "
				+ "from the drag path",
			calls.contains("revalidateScroll"));
	}

	private static final int SPRITE_THUMB_TOP_ID = 789;
	private static final int SPRITE_THUMB_MIDDLE_SPRITE_ID = 790;
	private static final int SPRITE_TRACK_ID = 792;
	private static final int SPRITE_ARROW_UP_ID = 773;

	/** The lone widget in {@code bar}'s children wired with {@code setOnDragListener}. */
	private static JavaScriptCallback dragListenerOf(Widget bar)
	{
		for (Widget child : RecordingWidget.childrenOf(bar))
		{
			Object listener = RecordingWidget.listenerOf(child, "setOnDragListener");
			if (listener != null)
			{
				return (JavaScriptCallback) listener;
			}
		}
		return null;
	}

	/** @see #dragListenerOf */
	private static JavaScriptCallback dragCompleteListenerOf(Widget bar)
	{
		for (Widget child : RecordingWidget.childrenOf(bar))
		{
			Object listener = RecordingWidget.listenerOf(child, "setOnDragCompleteListener");
			if (listener != null)
			{
				return (JavaScriptCallback) listener;
			}
		}
		return null;
	}

	/**
	 * A dummy stand-in, the {@code BossMechanicsWindowLayoutTest} idiom: production code reads the
	 * drag position from the supplied {@code Supplier<Point>}, never the event itself (D29/D35), so
	 * nothing here needs to answer any particular method.
	 */
	private static ScriptEvent fakeScriptEvent()
	{
		return (ScriptEvent) Proxy.newProxyInstance(ScriptEvent.class.getClassLoader(),
			new Class<?>[] { ScriptEvent.class },
			(proxy, method, args) -> RecordingWidget.defaultFor(method.getReturnType()));
	}

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

	private static long countSetScrollY(Widget widget)
	{
		return RecordingWidget.callsOf(widget).stream().filter("setScrollY"::equals).count();
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
