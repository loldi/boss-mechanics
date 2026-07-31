package com.bossmechanics.data;

/** Detection trigger kinds (see data/SCHEMA.md "Trigger"). */
public enum TriggerType
{
	ANIMATION,
	PROJECTILE,
	GRAPHIC,
	NPC_SPAWN;

	/** @return the matching type, or null if {@code jsonValue} is missing/unrecognized. */
	public static TriggerType fromJson(String jsonValue)
	{
		if (jsonValue == null)
		{
			return null;
		}

		switch (jsonValue)
		{
			case "animation":
				return ANIMATION;
			case "projectile":
				return PROJECTILE;
			case "graphic":
				return GRAPHIC;
			case "npc-spawn":
				return NPC_SPAWN;
			default:
				return null;
		}
	}
}
