package com.bossmechanics.ui;

import com.bossmechanics.view.ScrollThumbDrag;
import java.util.function.Supplier;
import net.runelite.api.Point;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;

/**
 * The mechanics list's scrollbar, built by hand from the collection log's own scrollbar sprites
 * (track 792, thumb 789/790/791, arrows 773/788 — all 16 wide, dumped from the cache).
 *
 * <p><b>Why by hand.</b> The collection log builds its scrollbar with
 * {@code runScript(31, scrollbarId, listId, ...)}, and that is unusable here. Disassembling the
 * injected client's {@code createChild} shows a dynamic child's {@code id} field is copied
 * straight from its parent, so {@code Widget.getId()} on anything in our tree returns the
 * FLOATER's component id, not an address for our scrollbar. Handing that to script 31 would make
 * Jagex's script delete and rebuild the children of the FLOATER itself, destroying our own window
 * root (and anything else floating). The plan named this fallback for exactly this risk.
 *
 * <p><b>The thumb is draggable (docs/DECISIONS.md D35)</b> via a static drag-capture LAYER built
 * over the track, never listeners on the moving thumb sprites: D33 established that the engine
 * re-hit-tests under the cursor every frame, so hiding or moving the drag source mid-gesture kills
 * it, and a capture surface that never moves and never hides is safe by construction, the same
 * shape as the title-bar drag handle (docs/DECISIONS.md D29). The thumb sprites stay pure visuals,
 * repositioned by {@link #positionThumb} on every drag event; the arrows and the mouse wheel still
 * do the rest of the scrolling exactly as before.
 *
 * <p><b>{@code Widget.revalidateScroll()} is forbidden on this list, permanently (docs/DECISIONS.md
 * D22).</b> It indexes the STATIC group array of the host's top-level interface, but bounds that
 * indexing with the DYNAMIC widget's own flat-array child-index watermark — an upstream RuneLite
 * mixin bug. Once a rebuild's watermark passes the group's own component count it throws
 * {@code ArrayIndexOutOfBoundsException} and aborts {@code open()} mid-build (client.log,
 * 2026-08-02 09:07:30), which is provably reachable even on a clean first open for any boss with
 * enough mechanics. {@code setScrollHeight}/{@code setScrollY} are unaffected and stay.
 */
final class MechanicsScrollbar
{
	/** 621 BOSS_SCROLLBAR child 13. */
	static final int WIDTH = 16;

	private static final int SPRITE_TRACK = 792;
	private static final int SPRITE_THUMB_TOP = 789;
	private static final int SPRITE_THUMB_MIDDLE = 790;
	private static final int SPRITE_THUMB_BOTTOM = 791;
	private static final int SPRITE_ARROW_UP = 773;
	private static final int SPRITE_ARROW_DOWN = 788;

	private static final int ARROW_SIZE = 16;
	/** The track and thumb sprites are 16x5. */
	private static final int CAP_HEIGHT = 5;
	private static final int MIN_THUMB_HEIGHT = 3 * CAP_HEIGHT;

	/** One arrow click. Roughly a locked row. */
	static final int ARROW_STEP = 24;
	/** One wheel notch. */
	static final int WHEEL_STEP = 36;

	/**
	 * The collection log's own drag dead zone/time (script 2240, docs/DECISIONS.md D31), reused
	 * here for the thumb's drag capture layer for the same reason D31 corrected the title-bar
	 * handle's own values: nothing fires until the cursor has travelled this many pixels and this
	 * many client cycles have passed, so a larger value trails the cursor by that much for the rest
	 * of the gesture.
	 */
	private static final int DRAG_DEAD_ZONE = 1;
	private static final int DRAG_DEAD_TIME = 5;

	/** What the track and arrows do once the content already fits the viewport. */
	enum Chrome
	{
		/**
		 * Keep them drawn. The list column's bar: the CA screen we mirror draws its list scrollbar
		 * unconditionally, and the column reads as a cut-off box without it.
		 */
		ALWAYS,
		/**
		 * Hide them with the thumb. The text box's bar: it sits inside the copy in a ~93px band,
		 * where an inert scrollbar is noise rather than affordance.
		 */
		ONLY_WHEN_SCROLLABLE
	}

	private final Widget list;
	private final Widget bar;
	private final int barHeight;
	private final int viewportHeight;
	private final Chrome chrome;
	private final Supplier<Point> mouseCanvasPosition;

	/**
	 * Mutable (docs/DECISIONS.md D28, issue #47 follow-up): the text scroll box re-derives this on
	 * every mechanic selection, since each mechanic's stacked text takes a different height.
	 * {@link #setContentHeight} is the only thing that changes it after {@link #build}.
	 */
	private int contentHeight;

	private Widget track;
	private Widget arrowUp;
	private Widget arrowDown;
	private Widget thumbTop;
	private Widget thumbMiddle;
	private Widget thumbBottom;
	private int thumbHeight;

	/** The drag-capture LAYER over the track (docs/DECISIONS.md D35). See {@link #build}. */
	private Widget dragCapture;

	/**
	 * Live only between a thumb-drag gesture's first {@code setOnDragListener} event and its
	 * {@code setOnDragCompleteListener} (docs/DECISIONS.md D35, the D29 pattern): the first event
	 * of a gesture captures {@link #dragMouseStartY}/{@link #dragScrollStart} and returns; every
	 * later event recomputes the target scroll from that baseline via {@link ScrollThumbDrag}.
	 */
	private boolean thumbDragging;
	private int dragMouseStartY;
	private int dragScrollStart;

	/**
	 * @param mouseCanvasPosition {@code client.getMouseCanvasPosition()}, supplied as a lambda so
	 *     this package stays testable without a real {@code Client} (docs/DECISIONS.md D29: canvas
	 *     position, never the drag event's own coordinates, which are relative to the drag source
	 *     and would feed back on themselves as it moves)
	 */
	MechanicsScrollbar(Widget list, Widget bar, int barHeight, int viewportHeight, int contentHeight,
		Chrome chrome, Supplier<Point> mouseCanvasPosition)
	{
		this.list = list;
		this.bar = bar;
		this.barHeight = barHeight;
		this.viewportHeight = viewportHeight;
		this.contentHeight = contentHeight;
		this.chrome = chrome;
		this.mouseCanvasPosition = mouseCanvasPosition;
	}

	/**
	 * Builds the track, arrows and thumb, and wires the list's scroll behaviour. Every widget this
	 * scrollbar will ever show is created here, once, and only here: {@link #setContentHeight}
	 * mutates rects and visibility on these same widgets, never {@code createChild}s a new one
	 * (docs/DECISIONS.md D22 -- a post-build {@code createChild} is exactly the rebuild-leak shape
	 * that decision fixed).
	 */
	void build()
	{
		listenForWheel(list);

		int trackHeight = trackHeight();

		track = sprite(SPRITE_TRACK, 0, ARROW_SIZE, WIDTH, trackHeight, true);
		arrowUp = arrow(SPRITE_ARROW_UP, 0, -ARROW_STEP);
		arrowDown = arrow(SPRITE_ARROW_DOWN, barHeight - ARROW_SIZE, ARROW_STEP);

		// Always created, regardless of whether the content this instance opens with actually
		// needs to scroll: hiding is layout()'s job, from here on.
		thumbTop = sprite(SPRITE_THUMB_TOP, 0, ARROW_SIZE, WIDTH, CAP_HEIGHT, false);
		thumbMiddle = sprite(SPRITE_THUMB_MIDDLE, 0, ARROW_SIZE + CAP_HEIGHT, WIDTH, CAP_HEIGHT, true);
		thumbBottom = sprite(SPRITE_THUMB_BOTTOM, 0, ARROW_SIZE + CAP_HEIGHT, WIDTH, CAP_HEIGHT, false);

		// docs/DECISIONS.md D35: created once, after the thumb sprites, spanning exactly the
		// track's own rect (ARROW_SIZE to barHeight - ARROW_SIZE) so it can never swallow the
		// arrows. A static LAYER, never moved and never hidden mid-gesture (D33), is the drag
		// SOURCE; the thumb sprites above stay pure visuals, repositioned by positionThumb().
		dragCapture = Widgets.layer(bar, 0, ARROW_SIZE, WIDTH, trackHeight);
		dragCapture.setHasListener(true);
		dragCapture.setNoClickThrough(true);
		dragCapture.setClickMask(dragCapture.getClickMask() | WidgetConfig.DRAG);
		dragCapture.setDragDeadZone(DRAG_DEAD_ZONE);
		dragCapture.setDragDeadTime(DRAG_DEAD_TIME);
		dragCapture.setOnDragListener((JavaScriptCallback) event -> onThumbDrag());
		dragCapture.setOnDragCompleteListener((JavaScriptCallback) event -> onThumbDragComplete());

		layout();
		bar.revalidate();
	}

	/**
	 * Rebinds the scrollbar to a new content height, e.g. a different mechanic's stacked text
	 * block (docs/DECISIONS.md D28). Rect mutation plus {@code setScrollHeight}/{@code setScrollY}
	 * only -- never {@code createChild}, never {@code revalidateScroll()} (D22's permanent ban).
	 */
	void setContentHeight(int contentHeight)
	{
		this.contentHeight = contentHeight;
		layout();
		bar.revalidate();
	}

	/**
	 * Lays out (or re-lays-out) the track, arrows and thumb against the current
	 * {@link #contentHeight}. The thumb always hides when there is nothing to scroll; whether the
	 * track and arrows go with it is this instance's {@link Chrome} policy.
	 */
	private void layout()
	{
		list.setScrollHeight(contentHeight);
		list.setScrollY(0);

		boolean scrollable = maxScroll() > 0;
		int trackHeight = trackHeight();

		thumbHeight = scrollable
			? Math.min(trackHeight, Math.max(MIN_THUMB_HEIGHT, trackHeight * viewportHeight / Math.max(1, contentHeight)))
			: 0;
		boolean showThumb = scrollable && thumbHeight >= MIN_THUMB_HEIGHT;
		boolean showTrack = scrollable || chrome == Chrome.ALWAYS;

		setHidden(track, !showTrack);
		setHidden(arrowUp, !showTrack);
		setHidden(arrowDown, !showTrack);
		setHidden(thumbTop, !showThumb);
		setHidden(thumbMiddle, !showThumb);
		setHidden(thumbBottom, !showThumb);
		// An inert bar must not start a dead gesture (docs/DECISIONS.md D35): scrollable, not
		// showThumb, gates the capture layer, and a re-layout (setContentHeight) drops whatever
		// gesture was in flight against the content height that no longer applies.
		setHidden(dragCapture, !scrollable);
		thumbDragging = false;

		if (showThumb)
		{
			thumbMiddle.setOriginalHeight(thumbHeight - (2 * CAP_HEIGHT));
			thumbMiddle.revalidate();
			positionThumb(0);
		}
	}

	private int trackHeight()
	{
		return Math.max(0, barHeight - (2 * ARROW_SIZE));
	}

	private static void setHidden(Widget widget, boolean hidden)
	{
		widget.setHidden(hidden);
		widget.revalidate();
	}

	/**
	 * Makes the wheel scroll the list while the pointer is over {@code widget}. Rows need this
	 * too: a wheel event over a row is not guaranteed to reach the list behind it.
	 *
	 * <p>{@code ScriptEvent.getMouseY()} carries the wheel rotation for a scroll-wheel trigger,
	 * which is how core RuneLite's bank tag tabs read it.
	 */
	void listenForWheel(Widget widget)
	{
		widget.setHasListener(true);
		widget.setNoScrollThrough(true);
		widget.setOnScrollWheelListener((JavaScriptCallback) event -> scrollBy(event.getMouseY() * WHEEL_STEP));
	}

	private void scrollBy(int delta)
	{
		int target = Math.max(0, Math.min(maxScroll(), list.getScrollY() + delta));
		if (target == list.getScrollY())
		{
			return;
		}

		list.setScrollY(target);
		positionThumb(target);
	}

	/**
	 * Every {@code setOnDragListener} firing of one thumb-drag gesture (docs/DECISIONS.md D35). The
	 * first event only captures a baseline (the mouse's canvas Y and the scroll position at that
	 * moment) and returns; every event after recomputes the target scroll from that baseline via
	 * {@link ScrollThumbDrag}, so a fast flick or a release past the track end never loses the
	 * gesture or accumulates error -- each event is absolute, not relative to the last one.
	 *
	 * <p>Reads the supplied canvas position, never the event's own coordinates (docs/DECISIONS.md
	 * D29): the event's coordinates are relative to the drag source itself, which would feed back
	 * on itself if the source ever moved (this one never does, D33/D35, but the seam is shared).
	 */
	private void onThumbDrag()
	{
		Point mouse = mouseCanvasPosition.get();
		if (mouse == null)
		{
			return;
		}

		int travel = trackHeight() - thumbHeight;
		int maxScroll = maxScroll();
		if (maxScroll == 0 || travel <= 0)
		{
			return;
		}

		if (!thumbDragging)
		{
			thumbDragging = true;
			dragMouseStartY = mouse.getY();
			dragScrollStart = list.getScrollY();
			return;
		}

		int target = ScrollThumbDrag.scrollY(dragScrollStart, mouse.getY() - dragMouseStartY, travel, maxScroll);
		if (target == list.getScrollY())
		{
			return;
		}

		list.setScrollY(target);
		positionThumb(target);
	}

	/**
	 * Clears the in-flight flag (docs/DECISIONS.md D35). Scroll state is clamped and applied on
	 * every drag event above, not just on completion, so there is no committed value left to
	 * relay here -- unlike the window's own drag (D29/D30), a thumb drag interrupted by Esc or the
	 * window closing leaves nothing stranded: {@link MechanicsList}/{@link MechanicsDetail} (and
	 * this scrollbar with them) are discarded on close, taking {@link #thumbDragging} with them.
	 */
	private void onThumbDragComplete()
	{
		thumbDragging = false;
	}

	/** Only ever called while the thumb is shown (docs/DECISIONS.md D28): {@link #layout} gates it. */
	private void positionThumb(int scrollY)
	{
		int travel = trackHeight() - thumbHeight;
		int y = ARROW_SIZE + (maxScroll() == 0 ? 0 : travel * scrollY / maxScroll());

		thumbTop.setOriginalY(y);
		thumbMiddle.setOriginalY(y + CAP_HEIGHT);
		thumbBottom.setOriginalY(y + thumbHeight - CAP_HEIGHT);

		thumbTop.revalidate();
		thumbMiddle.revalidate();
		thumbBottom.revalidate();
		bar.revalidate();
	}

	private int maxScroll()
	{
		return Math.max(0, contentHeight - viewportHeight);
	}

	private Widget arrow(int spriteId, int y, int step)
	{
		Widget button = sprite(spriteId, 0, y, ARROW_SIZE, ARROW_SIZE, false);
		button.setAction(0, "Scroll");
		button.setNoClickThrough(true);
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) event -> scrollBy(step));
		return button;
	}

	private Widget sprite(int spriteId, int x, int y, int width, int height, boolean tiled)
	{
		return Widgets.sprite(bar, spriteId, x, y, width, height, tiled);
	}
}
