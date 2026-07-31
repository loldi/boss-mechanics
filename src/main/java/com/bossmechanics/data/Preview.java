package com.bossmechanics.data;

import lombok.Value;

/**
 * What the animation column shows (see data/SCHEMA.md "Preview"). {@code staticFallback} is a
 * primitive so an absent JSON field defaults to false, per SCHEMA.md.
 */
@Value
public class Preview
{
	Integer animationId;
	Integer npcId;
	boolean staticFallback;
}
