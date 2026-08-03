package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The clamp math behind issue #48's draggable window, kept RuneLite-free and pure like every other
 * decision in this package (docs/DECISIONS.md D29). A zero offset must always reproduce today's
 * placement exactly, including a legitimately negative computed origin (D20) — clamping that away
 * would be a regression, not a fix.
 */
public class WindowDragTest
{
	@Test
	public void zeroOffsetIsIdentity()
	{
		assertEquals(128, WindowDrag.clampedOrigin(128, 0, 512, 765));
	}

	@Test
	public void zeroOffsetNeverClampsALegitimateOverhang()
	{
		assertEquals(-6, WindowDrag.clampedOrigin(-6, 0, 512, 765));
	}

	@Test
	public void clampsAtTheRightEdge()
	{
		assertEquals(253, WindowDrag.clampedOrigin(128, 400, 512, 765));
	}

	@Test
	public void clampsAtTheLeftEdge()
	{
		assertEquals(0, WindowDrag.clampedOrigin(128, -400, 512, 765));
	}

	@Test
	public void anAlreadyOutOfBoundsOriginCanOnlyBeDraggedBackIn()
	{
		assertEquals(-6, WindowDrag.clampedOrigin(-6, -50, 512, 765));
	}
}
