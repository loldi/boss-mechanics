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
	/** Model widget zoom; null means "use the resolved default" (docs/DECISIONS.md D23). */
	Integer zoom;
	/**
	 * Vertical anchor correction, in pixels; null means 0, i.e. no correction (docs/DECISIONS.md
	 * D26). The engine anchors an if3 MODEL widget's ground line (y=0) at the widget's own
	 * vertical center, not the centre of its animated bounds, so a model that extends mostly
	 * upward from the ground needs its widget rect grown downward by this many pixels to land
	 * centred in the box. Curated per mechanic from the model's own vertical extents (see the
	 * cachetool's {@code FitZoom}, which now emits it alongside zoom).
	 */
	Integer shiftY;
	/**
	 * Explicit cache model id to show, bypassing the npc -> model lookup entirely (docs/
	 * DECISIONS.md D27). Null means "resolve the model from {@code npcId} instead", the existing
	 * behaviour. Curated for a model that has no npc to look it up from (e.g. a base spotanim
	 * model), or that a curator otherwise wants to pin directly.
	 */
	Integer modelId;
}
