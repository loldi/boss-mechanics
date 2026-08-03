package com.bossmechanics.ui;

import net.runelite.api.widgets.Widget;

/**
 * Where the window goes so that it covers the collection log, in the host's own coordinates.
 *
 * <p><b>Why this exists.</b> The window is hosted on {@code UI_HIGHLIGHTS} (D20), which in every
 * top-level layout is a root spanning the whole client canvas. The collection log is not: in the
 * resizable layout it is centred inside {@code 161 c15}, which is {@code parentWidth MINUS 250}
 * by {@code parentHeight MINUS 165} — the sidebar and the chatbox taken out. So the log's centre
 * is {@code ((W-250)/2, (H-165)/2)} while the host's centre is {@code (W/2, H/2)}, and
 * {@code ABSOLUTE_CENTER} on the host lands the window 125px right and 82px below the log. That
 * is exactly what shipped, and exactly what the screenshots showed.
 *
 * <p>The offset is not a constant to hardcode: fixed mode (548) nests the log differently and has
 * a different delta. It has to be measured from the log's real rectangle every time the window
 * opens, which is what {@link #origin} turns into a position.
 *
 * <p>{@link #origin} is pure arithmetic and is unit tested; {@link #offsetInRoot} walks a live
 * parent chain and is not. Both are static, and neither keeps state.
 */
final class WindowPlacement
{
	private WindowPlacement()
	{
	}

	/**
	 * The {@code ABSOLUTE} origin, relative to the host, that centres a {@code windowSize}-long
	 * window over a target starting at {@code targetStart}. All four arguments are on one axis and
	 * measured from the same root, so this is called once per axis.
	 *
	 * <p>Deliberately the client's own centring idiom, {@code (parentSize - size) / 2}, rather than
	 * two independent halvings, so an odd-sized log rounds the way the client would.
	 *
	 * <p>May legitimately return a negative number: a 512-wide window over a 500-wide log overhangs
	 * it by 6px on each side, which is what the Combat Achievements screen does.
	 */
	static int origin(int targetStart, int targetSize, int hostStart, int windowSize)
	{
		return targetStart - hostStart + ((targetSize - windowSize) / 2);
	}

	/**
	 * A widget's x (or y) in its root's coordinates, summed up the parent chain.
	 *
	 * <p>{@code getCanvasLocation()} would be the obvious call and is the wrong one: it reads
	 * {@code (-1,-1)} for hand-created dynamic children even while they render (D14, from the
	 * issue #1 spike). {@code getRelativeX/Y} is the getter that works, and it is relative to the
	 * parent, so the chain has to be walked.
	 *
	 * @param vertical true sums {@code getRelativeY}, false sums {@code getRelativeX}
	 */
	static int offsetInRoot(Widget widget, boolean vertical)
	{
		int total = 0;
		for (Widget node = widget; node != null; node = node.getParent())
		{
			total += vertical ? node.getRelativeY() : node.getRelativeX();
		}
		return total;
	}

	/**
	 * The full steel chrome (docs/DECISIONS.md D27, G2 fork resolved: full) makes the root
	 * {@code chrome} pixels larger on every edge than the 512x334 logical window {@link #origin}
	 * placed, so the root's own origin has to sit {@code chrome} pixels up and to the left of the
	 * logical origin for the logical window inside it to still land exactly there.
	 */
	static int withChrome(int logicalOrigin, int chrome)
	{
		return logicalOrigin - chrome;
	}
}
