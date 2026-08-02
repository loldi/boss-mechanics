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

	private final Widget list;
	private final Widget bar;
	private final int barHeight;
	private final int viewportHeight;
	private final int contentHeight;

	private Widget thumbTop;
	private Widget thumbMiddle;
	private Widget thumbBottom;
	private int thumbHeight;

	MechanicsScrollbar(Widget list, Widget bar, int barHeight, int viewportHeight, int contentHeight)
	{
		this.list = list;
		this.bar = bar;
		this.barHeight = barHeight;
		this.viewportHeight = viewportHeight;
		this.contentHeight = contentHeight;
	}

	/** Builds the track, arrows and thumb, and wires the list's scroll behaviour. */
	void build()
	{
		list.setScrollHeight(contentHeight);
		list.revalidateScroll();
		list.setScrollY(0);
		listenForWheel(list);

		int trackHeight = Math.max(0, barHeight - (2 * ARROW_SIZE));

		sprite(SPRITE_TRACK, 0, ARROW_SIZE, WIDTH, trackHeight, true);
		arrow(SPRITE_ARROW_UP, 0, -ARROW_STEP);
		arrow(SPRITE_ARROW_DOWN, barHeight - ARROW_SIZE, ARROW_STEP);

		thumbHeight = maxScroll() == 0
			? trackHeight
			: Math.max(MIN_THUMB_HEIGHT, trackHeight * viewportHeight / Math.max(1, contentHeight));
		thumbHeight = Math.min(thumbHeight, trackHeight);

		if (thumbHeight >= MIN_THUMB_HEIGHT)
		{
			thumbTop = sprite(SPRITE_THUMB_TOP, 0, ARROW_SIZE, WIDTH, CAP_HEIGHT, false);
			thumbMiddle = sprite(SPRITE_THUMB_MIDDLE, 0, ARROW_SIZE + CAP_HEIGHT, WIDTH,
				thumbHeight - (2 * CAP_HEIGHT), true);
			thumbBottom = sprite(SPRITE_THUMB_BOTTOM, 0, ARROW_SIZE + thumbHeight - CAP_HEIGHT, WIDTH,
				CAP_HEIGHT, false);
		}

		bar.revalidate();
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
		list.revalidateScroll();
		positionThumb(target);
	}

	private void positionThumb(int scrollY)
	{
		if (thumbTop == null)
		{
			return;
		}

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

	private void arrow(int spriteId, int y, int step)
	{
		Widget button = sprite(spriteId, 0, y, ARROW_SIZE, ARROW_SIZE, false);
		button.setAction(0, "Scroll");
		button.setNoClickThrough(true);
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) event -> scrollBy(step));
	}

	private Widget sprite(int spriteId, int x, int y, int width, int height, boolean tiled)
	{
		return Widgets.sprite(bar, spriteId, x, y, width, height, tiled);
	}
}
