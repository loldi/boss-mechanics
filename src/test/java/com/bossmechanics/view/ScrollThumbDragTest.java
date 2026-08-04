package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The clamp math behind the scrollbar thumb drag (docs/DECISIONS.md D35), kept RuneLite-free and
 * pure like {@link WindowDrag} and every other decision in this package. Unlike the window's own
 * drag math, there is no "committed vs. live" split to protect (docs/DECISIONS.md D35): scroll
 * state is clamped and applied on every drag event, so this only ever answers one question --
 * given the gesture's starting scroll position and how far the mouse has moved since, where
 * should the content be scrolled to right now.
 */
public class ScrollThumbDragTest
{
	@Test
	public void zeroDeltaIsIdentity()
	{
		assertEquals(20, ScrollThumbDrag.scrollY(20, 0, 45, 90));
	}

	@Test
	public void scrollsProportionallyToTheMouseDelta()
	{
		assertEquals(20, ScrollThumbDrag.scrollY(0, 10, 45, 90));
	}

	@Test
	public void clampsAtTheBottomOfTheScrollRange()
	{
		assertEquals(90, ScrollThumbDrag.scrollY(0, 1000, 45, 90));
	}

	@Test
	public void clampsAtTheTopOfTheScrollRange()
	{
		assertEquals(0, ScrollThumbDrag.scrollY(20, -1000, 45, 90));
	}

	/**
	 * {@code floorDiv}, not {@code /}, so a negative delta rounds toward negative infinity the same
	 * direction a positive delta would round toward positive infinity -- a plain truncating
	 * division would round -45/90 toward zero (0), not down (-1), which would make the thumb lag
	 * the cursor by a pixel on the way up that it never lagged by on the way down.
	 */
	@Test
	public void negativeDeltaRoundsViaFloorDivNotTruncation()
	{
		assertEquals(29, ScrollThumbDrag.scrollY(30, -1, 90, 45));
	}

	@Test
	public void zeroTravelFallsBackToClampingTheStartingScroll()
	{
		assertEquals(20, ScrollThumbDrag.scrollY(20, 10, 0, 90));
	}

	@Test
	public void zeroMaxScrollFallsBackToClampingTheStartingScrollToZero()
	{
		assertEquals(0, ScrollThumbDrag.scrollY(5, 10, 45, 0));
	}

	@Test
	public void travelOrMaxScrollGuardStillClampsAnOutOfRangeStartingScroll()
	{
		assertEquals(0, ScrollThumbDrag.scrollY(-5, 10, 0, 90));
	}
}
