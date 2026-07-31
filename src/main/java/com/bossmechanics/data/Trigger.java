package com.bossmechanics.data;

import lombok.Value;

/**
 * One detection trigger (see data/SCHEMA.md "Trigger"). {@code type} stays a raw String
 * deliberately: Gson maps unknown enum values to a silent null and can't distinguish
 * "missing" from "typo", so validation resolves it via {@link #triggerType()} and can
 * report the offending value by name.
 */
@Value
public class Trigger
{
	String type;
	Integer id;

	public TriggerType triggerType()
	{
		return TriggerType.fromJson(type);
	}
}
