package com.bossmechanics.data;

import lombok.Value;

/**
 * One segment of a chained preview animation (see data/SCHEMA.md "Preview", docs/DECISIONS.md
 * D37): an animation id and how many client cycles (20ms each) it plays before the chain advances
 * to the next segment. An object per segment, not two parallel lists (one of ids, one of
 * durations) -- an id and its curated duration are one fact together, and a length mismatch
 * between two lists is a silent bug class this shape makes unrepresentable.
 */
@Value
public class ChainSegment
{
	Integer animationId;
	Integer cycles;
}
