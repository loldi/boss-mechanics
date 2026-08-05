package com.bossmechanics.data;

/** Discovery-gate requirement kinds (see data/SCHEMA.md "Requirement"). */
public enum RequirementType
{
	VARP;

	/** @return the matching type, or null if {@code jsonValue} is missing/unrecognized. */
	public static RequirementType fromJson(String jsonValue)
	{
		if (jsonValue == null)
		{
			return null;
		}

		switch (jsonValue)
		{
			case "varp":
				return VARP;
			default:
				return null;
		}
	}
}
