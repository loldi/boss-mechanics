package com.bossmechanics.ui;

import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;

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
 * <p>Consequence: the thumb indicates position but is not draggable. The arrows and the mouse
 * wheel do the scrolling, which is what people use anyway.
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

	MechanicsScrollbar(Widget list, Widget bar, int barHeight, int viewportHeight, int contentHeight,
		Chrome chrome)
	{
		this.list = list;
		this.bar = bar;
		this.barHeight = barHeight;
		this.viewportHeight = viewportHeight;
		this.contentHeight = contentHeight;
		this.chrome = chrome;
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

		int trackHeight = Math.max(0, barHeight - (2 * ARROW_SIZE));

		track = sprite(SPRITE_TRACK, 0, ARROW_SIZE, WIDTH, trackHeight, true);
		arrowUp = arrow(SPRITE_ARROW_UP, 0, -ARROW_STEP);
		arrowDown = arrow(SPRITE_ARROW_DOWN, barHeight - ARROW_SIZE, ARROW_STEP);

		// Always created, regardless of whether the content this instance opens with actually
		// needs to scroll: hiding is layout()'s job, from here on.
		thumbTop = sprite(SPRITE_THUMB_TOP, 0, ARROW_SIZE, WIDTH, CAP_HEIGHT, false);
		thumbMiddle = sprite(SPRITE_THUMB_MIDDLE, 0, ARROW_SIZE + CAP_HEIGHT, WIDTH, CAP_HEIGHT, true);
		thumbBottom = sprite(SPRITE_THUMB_BOTTOM, 0, ARROW_SIZE + CAP_HEIGHT, WIDTH, CAP_HEIGHT, false);

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
		int trackHeight = Math.max(0, barHeight - (2 * ARROW_SIZE));

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

		if (showThumb)
		{
			thumbMiddle.setOriginalHeight(thumbHeight - (2 * CAP_HEIGHT));
			thumbMiddle.revalidate();
			positionThumb(0);
		}
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

	/** Only ever called while the thumb is shown (docs/DECISIONS.md D28): {@link #layout} gates it. */
	private void positionThumb(int scrollY)
	{
		int trackHeight = Math.max(0, barHeight - (2 * ARROW_SIZE));
		int travel = trackHeight - thumbHeight;
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
