package com.bossmechanics.detection;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.Mechanic;
import lombok.Value;

/** A mechanic newly discovered on a specific boss, returned by {@link DetectionEngine}. */
@Value
public class Discovery
{
	Boss boss;
	Mechanic mechanic;
}
