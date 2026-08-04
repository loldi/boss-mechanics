package com.bossmechanics.view;

/**
 * The clamp math behind the scrollbar thumb drag (docs/DECISIONS.md D35). RuneLite-free and pure
 * like {@link WindowDrag}: {@code ui.MechanicsScrollbar} owns the widgets and the mouse-position
 * seam; this only answers "given the gesture's starting scroll position and how far the mouse has
 * moved since, where should the content be scrolled to right now."
 *
 * <p>Unlike {@link WindowDrag}, there is no committed-vs-live split to protect here: scroll state
 * is clamped and applied on every drag event (D35), so a completed gesture needs nothing more
 * than clearing an in-flight flag -- there is no phantom offset a later gesture could inherit.
 *
 * <p>Static, stateless, no RuneLite import.
 */
public final class ScrollThumbDrag
{
	private ScrollThumbDrag()
	{
	}

	/**
	 * @param scrollAtStart the content's scroll position when this drag gesture began
	 * @param mouseDeltaPx how far the mouse has moved (in canvas pixels) since the gesture began,
	 *     on the scrollbar's own axis
	 * @param travelPx the pixel distance the thumb can travel along the track (track height minus
	 *     thumb height); {@code <= 0} means the thumb has nowhere to go
	 * @param maxScroll the content's own maximum scroll value; {@code <= 0} means nothing scrolls
	 * @return the scroll position to apply right now, clamped to {@code [0, maxScroll]}
	 */
	public static int scrollY(int scrollAtStart, int mouseDeltaPx, int travelPx, int maxScroll)
	{
		if (travelPx <= 0 || maxScroll <= 0)
		{
			return clamp(scrollAtStart, 0, Math.max(0, maxScroll));
		}

		// floorDiv, not /: a negative delta must round the same direction (toward the end it is
		// heading) a positive delta rounds, or a truncating division would make the thumb lag the
		// cursor by a pixel scrolling up that it never lagged by scrolling down.
		int delta = Math.floorDiv(mouseDeltaPx * maxScroll, travelPx);
		return clamp(scrollAtStart + delta, 0, maxScroll);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
