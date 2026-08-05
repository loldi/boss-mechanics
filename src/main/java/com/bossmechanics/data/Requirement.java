package com.bossmechanics.data;

import lombok.Value;

/**
 * A game-state gate on a mechanic's discoverability (see data/SCHEMA.md "Requirement", docs/
 * DECISIONS.md D38). {@code type} stays a raw String deliberately, mirroring {@link Trigger}:
 * Gson maps an unknown enum value to a silent null and can't distinguish "missing" from "typo",
 * so validation resolves it via {@link #requirementType()} and can report the offending value by
 * name.
 */
@Value
public class Requirement
{
	String type;
	Integer id;
	Integer min;

	public RequirementType requirementType()
	{
		return RequirementType.fromJson(type);
	}
}
