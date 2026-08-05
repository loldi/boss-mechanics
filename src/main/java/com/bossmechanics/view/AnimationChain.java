package com.bossmechanics.view;

import com.bossmechanics.data.ChainSegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * Ordered preview animation segments played back to back on the D24 pool, looping as a whole once
 * the last one ends (issue #66, docs/DECISIONS.md D37) -- e.g. Shockwave's charge -&gt; charge
 * loop -&gt; slam. RuneLite-free and pure, like {@link WindowDrag}/{@link ScrollThumbDrag}:
 * {@code ui.MechanicsDetail} owns the pool widget and the tick clock; this only answers "given how
 * many client ticks have elapsed since the chain started, which animation id is showing right
 * now."
 */
@Value
public class AnimationChain
{
	List<ChainSegment> segments;

	/** The chain's full length in client cycles (20ms each) -- what {@link #animationAt} wraps by. */
	int totalCycles;

	/**
	 * @param chainData curated segments (see {@code data.Preview#getChain()}); null or empty means
	 *     "no chain curated" -- the ordinary single-animation preview path
	 * @return null when {@code chainData} is null or empty, matching {@code PreviewSpec}'s existing
	 *     null-means-absent convention for every other optional preview field
	 */
	public static AnimationChain of(List<ChainSegment> chainData)
	{
		if (chainData == null || chainData.isEmpty())
		{
			return null;
		}

		int total = 0;
		for (ChainSegment segment : chainData)
		{
			total += segment.getCycles();
		}

		return new AnimationChain(Collections.unmodifiableList(new ArrayList<>(chainData)), total);
	}

	/**
	 * The animation id playing at {@code elapsedTicks} client ticks since the chain started.
	 * Wraps modulo {@link #totalCycles} so the whole chain loops rather than holding on its last
	 * segment's final frame (docs/DECISIONS.md D37, the Burrow Charge freeze this feature fixes).
	 */
	public int animationAt(int elapsedTicks)
	{
		int position = Math.floorMod(elapsedTicks, totalCycles);

		int cursor = 0;
		for (ChainSegment segment : segments)
		{
			cursor += segment.getCycles();
			if (position < cursor)
			{
				return segment.getAnimationId();
			}
		}

		// Unreachable: position < totalCycles always holds, and cursor reaches totalCycles by the
		// last segment, so the loop above always returns first.
		return segments.get(segments.size() - 1).getAnimationId();
	}
}
