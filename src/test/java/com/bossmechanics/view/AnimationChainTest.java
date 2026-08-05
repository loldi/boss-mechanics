package com.bossmechanics.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.bossmechanics.data.ChainSegment;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * The clock behind chained preview animations (issue #66, docs/DECISIONS.md D37): which segment's
 * animation id is showing at a given number of elapsed client ticks, wrapping so the whole chain
 * loops rather than holding on its last segment. Kept RuneLite-free and pure like
 * {@link WindowDrag}/{@link ScrollThumbDrag}: {@code ui.MechanicsDetail} owns the widget and the
 * clock, this only answers "given this many elapsed ticks, which animation id is showing right
 * now."
 */
public class AnimationChainTest
{
	/** The worked example from the plan: three segments, 60/30/60 cycles. */
	private static final AnimationChain WORKED_EXAMPLE = AnimationChain.of(Arrays.asList(
		new ChainSegment(12412, 60),
		new ChainSegment(12413, 30),
		new ChainSegment(12414, 60)));

	@Test
	public void firstSegmentPlaysAtItsStartAndThroughItsLastTick()
	{
		assertEquals(12412, WORKED_EXAMPLE.animationAt(0));
		assertEquals(12412, WORKED_EXAMPLE.animationAt(59));
	}

	@Test
	public void secondSegmentPlaysAcrossItsOwnWindow()
	{
		assertEquals(12413, WORKED_EXAMPLE.animationAt(60));
		assertEquals(12413, WORKED_EXAMPLE.animationAt(89));
	}

	@Test
	public void thirdSegmentPlaysAcrossItsOwnWindow()
	{
		assertEquals(12414, WORKED_EXAMPLE.animationAt(90));
		assertEquals(12414, WORKED_EXAMPLE.animationAt(149));
	}

	/** docs/DECISIONS.md D37: the whole chain loops, it does not hold on the last segment. */
	@Test
	public void wrapsBackToTheFirstSegmentAfterTheWholeChain()
	{
		assertEquals(12412, WORKED_EXAMPLE.animationAt(150));
	}

	@Test
	public void wrapsAcrossMultiplePasses()
	{
		assertEquals(12413, WORKED_EXAMPLE.animationAt(150 + 60));
	}

	@Test
	public void nullChainDataResolvesToNoChain()
	{
		assertNull(AnimationChain.of(null));
	}

	@Test
	public void emptyChainDataResolvesToNoChain()
	{
		assertNull(AnimationChain.of(Collections.emptyList()));
	}
}
