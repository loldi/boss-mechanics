package com.bossmechanics.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The arithmetic behind "the window covers the collection log". It is one line of maths, and it
 * shipped wrong once (ABSOLUTE_CENTER on a root that spans the whole canvas centres on the canvas,
 * not on the log), so it is worth pinning down offline rather than in a client run.
 */
public class WindowPlacementTest
{
	@Test
	public void centresTheWindowOverTheTarget()
	{
		// The 512-wide window over the 500-wide collection log, host and log sharing an origin:
		// 6px of window hangs off each side of the log.
		assertEquals(128, WindowPlacement.origin(134, 500, 0, 512));
	}

	@Test
	public void subtractsTheHostsOwnOffset()
	{
		// Same log, but the host does not start at the root's origin, so the answer shifts by
		// exactly that much. This is the term the shipped ABSOLUTE_CENTER had no way to include.
		assertEquals(108, WindowPlacement.origin(134, 500, 20, 512));
	}

	@Test
	public void allowsANegativeOriginWhenTheWindowOverhangs()
	{
		// A log flush against the host's left edge puts the wider window 6px off-screen. Negative
		// is the correct answer, not a bug to clamp: the CA screen overhangs the log too.
		assertEquals(-6, WindowPlacement.origin(0, 500, 0, 512));
	}

	@Test
	public void roundsAnOddSizeDifferenceTheClientsWay()
	{
		// The other cases all have an even (target - window) delta, where halving the difference
		// and differencing the halves agree. They only disagree on an odd delta, which is what
		// the implementation's choice of formula exists for, so pin the real behaviour: Java
		// truncates toward zero, so a negative delta lands a pixel nearer the target's origin.
		assertEquals(-5, WindowPlacement.origin(0, 501, 0, 512));
		assertEquals(5, WindowPlacement.origin(0, 21, 0, 10));
	}

	@Test
	public void centresOnTheVerticalAxisTheSameWay()
	{
		// One function, called twice. 314-tall log, 334-tall window.
		assertEquals(-6, WindowPlacement.origin(4, 314, 0, 334));
	}
}
