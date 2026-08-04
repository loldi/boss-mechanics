package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The clamp math behind the draggable window's unanchoring (docs/DECISIONS.md D32, which replaces
 * D29's {@code clampedOrigin}). Kept RuneLite-free and pure like every other decision in this
 * package. {@link WindowDrag#visibleOrigin} answers one question only: given an absolute origin
 * the window wants to sit at, where does it actually land so it stays on screen -- it no longer
 * has any notion of a "computed origin" to never clamp tighter than, because the window's origin
 * is absolute session state now, not re-derived from the collection log every tick.
 */
public class WindowDragTest
{
	@Test
	public void inBoundsOriginIsIdentity()
	{
		assertEquals(128, WindowDrag.visibleOrigin(128, 512, 765));
	}

	@Test
	public void clampsAtTheRightEdge()
	{
		assertEquals(253, WindowDrag.visibleOrigin(700, 512, 765));
	}

	@Test
	public void clampsAtTheLeftEdge()
	{
		assertEquals(0, WindowDrag.visibleOrigin(-400, 512, 765));
	}

	@Test
	public void hostSmallerThanTheWindowPinsToTheHostsOwnOrigin()
	{
		// The bounds invert via the min/max pair (min(0, 400-512) = -112, max(0, 400-512) = 0), so
		// the window pins to the host's origin edge rather than throwing.
		assertEquals(0, WindowDrag.visibleOrigin(50, 512, 400));
	}
}
