package com.bossmechanics.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one rule set for whether a parsed {@link Boss} is well-formed. Called only from
 * {@link BossDataLoader#parseOne(String, String)}, so runtime loading and tests share exactly
 * this rule set (see docs/DECISIONS.md D7). Package-private on purpose.
 */
final class BossDataValidator
{
	// Boss and mechanic ids double as ConfigManager key fragments and CSV entries (#8), so a
	// comma or other punctuation in one would corrupt persisted state. Enforced here, once.
	private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9-]+");

	private BossDataValidator()
	{
	}

	/** @return one message per problem found, each naming the offending field/value; empty if valid. */
	static List<String> validate(Boss boss, String expectedId)
	{
		List<String> errors = new ArrayList<>();

		if (boss == null)
		{
			errors.add(expectedId + ": file did not parse to a boss object");
			return errors;
		}

		requireNonBlank(errors, expectedId, "id", boss.getId());
		requireNonBlank(errors, expectedId, "name", boss.getName());
		requireNonBlank(errors, expectedId, "wikiUrl", boss.getWikiUrl());

		if (!isBlank(boss.getId()) && !boss.getId().equals(expectedId))
		{
			errors.add(expectedId + ": id '" + boss.getId() + "' does not match expected '" + expectedId + "'");
		}

		if (!isBlank(boss.getId()) && !isValidSlug(boss.getId()))
		{
			errors.add(expectedId + ": id '" + boss.getId() + "' is not a valid slug (expected lowercase letters, digits, hyphens)");
		}

		if (isEmpty(boss.getNpcIds()))
		{
			errors.add(expectedId + ": npcIds must not be empty");
		}

		if (isEmpty(boss.getMechanics()))
		{
			errors.add(expectedId + ": mechanics must not be empty");
		}
		else
		{
			validateMechanics(errors, expectedId, boss.getMechanics());
		}

		return errors;
	}

	private static void validateMechanics(List<String> errors, String expectedId, List<Mechanic> mechanics)
	{
		Set<String> seenMechanicIds = new HashSet<>();

		for (int i = 0; i < mechanics.size(); i++)
		{
			Mechanic mechanic = mechanics.get(i);
			String label = mechanicLabel(mechanic, i);

			requireNonBlank(errors, expectedId, label + ".id", mechanic.getId());
			requireNonBlank(errors, expectedId, label + ".name", mechanic.getName());
			requireNonBlank(errors, expectedId, label + ".description", mechanic.getDescription());
			requireNonBlank(errors, expectedId, label + ".counterplay", mechanic.getCounterplay());

			if (!isBlank(mechanic.getId()) && !isValidSlug(mechanic.getId()))
			{
				errors.add(expectedId + ": " + label + ": id '" + mechanic.getId() + "' is not a valid slug (expected lowercase letters, digits, hyphens)");
			}

			if (!isBlank(mechanic.getId()) && !seenMechanicIds.add(mechanic.getId()))
			{
				errors.add(expectedId + ": duplicate mechanic id '" + mechanic.getId() + "'");
			}

			if (isEmpty(mechanic.getDetection()))
			{
				errors.add(expectedId + ": " + label + ": detection must not be empty");
			}
			else
			{
				validateTriggers(errors, expectedId, label, mechanic.getDetection());
			}

			if (mechanic.getPreview() == null)
			{
				errors.add(expectedId + ": " + label + ": preview is required");
			}
			else
			{
				Preview preview = mechanic.getPreview();

				// Issue #6, docs/DECISIONS.md D23: with no animationId and no static pose to fall
				// back to, the model box would have nothing to render. Docs/DECISIONS.md D27: a
				// sprite preview carries no model fields at all, so it is exempt -- the sprite
				// tier wins over every model field, including this requirement.
				if (preview.getSprite() == null && !preview.isStaticFallback() && preview.getAnimationId() == null
					&& isEmpty(preview.getAnimationChain()))
				{
					errors.add(expectedId + ": " + label
						+ ": preview.animationId is required when staticFallback is false");
				}

				// docs/DECISIONS.md D37: a chain replaces animationId, not sits beside it.
				if (preview.getAnimationId() != null && !isEmpty(preview.getAnimationChain()))
				{
					errors.add(expectedId + ": " + label
						+ ": preview.animationChain and preview.animationId are mutually exclusive");
				}

				if (!isEmpty(preview.getAnimationChain()))
				{
					validateAnimationChain(errors, expectedId, label, preview.getAnimationChain());
				}

				// docs/DECISIONS.md D28: a rotation value outside 0-2047 crashes the client (D23),
				// so it is caught here rather than at runtime.
				validateRotation(errors, expectedId, label + ".rotationX", preview.getRotationX());
				validateRotation(errors, expectedId, label + ".rotationY", preview.getRotationY());
				validateRotation(errors, expectedId, label + ".rotationZ", preview.getRotationZ());

				SecondaryPreview secondary = preview.getSecondary();
				if (secondary != null)
				{
					validateRotation(errors, expectedId, label + ".secondary.rotationX", secondary.getRotationX());
					validateRotation(errors, expectedId, label + ".secondary.rotationY", secondary.getRotationY());
					validateRotation(errors, expectedId, label + ".secondary.rotationZ", secondary.getRotationZ());
				}
			}
		}
	}

	private static void validateTriggers(List<String> errors, String expectedId, String mechanicLabel, List<Trigger> triggers)
	{
		for (int i = 0; i < triggers.size(); i++)
		{
			Trigger trigger = triggers.get(i);
			String label = mechanicLabel + ".detection[" + i + "]";

			if (isBlank(trigger.getType()))
			{
				errors.add(expectedId + ": " + label + ": missing required field 'type'");
			}
			else if (trigger.triggerType() == null)
			{
				errors.add(expectedId + ": " + label + ": unknown trigger type '" + trigger.getType() + "'");
			}

			if (trigger.getId() == null)
			{
				errors.add(expectedId + ": " + label + ": missing required field 'id'");
			}
		}
	}

	/**
	 * docs/DECISIONS.md D37: a chained preview needs at least 2 segments (one segment is just
	 * {@code animationId} spelled a longer way), and every segment needs both a positive
	 * {@code cycles} (the client's own animation clock, 20ms per tick -- 0 or fewer never
	 * advances) and an {@code animationId} to actually play.
	 */
	private static void validateAnimationChain(List<String> errors, String expectedId, String mechanicLabel,
		List<ChainSegment> chain)
	{
		if (chain.size() < 2)
		{
			errors.add(expectedId + ": " + mechanicLabel
				+ ": preview.animationChain must have at least 2 segments");
		}

		for (int i = 0; i < chain.size(); i++)
		{
			ChainSegment segment = chain.get(i);
			String label = mechanicLabel + ".animationChain[" + i + "]";

			if (segment.getAnimationId() == null)
			{
				errors.add(expectedId + ": " + label + ": missing required field 'animationId'");
			}

			if (segment.getCycles() == null || segment.getCycles() < 1)
			{
				errors.add(expectedId + ": " + label + ": cycles must be at least 1");
			}
		}
	}

	/**
	 * A rotation axis value must fit 0-2047 (a full turn); anything outside crashes the client
	 * (docs/DECISIONS.md D23, D28). Null (absent) is always fine -- it resolves to 0.
	 */
	private static void validateRotation(List<String> errors, String expectedId, String field, Integer value)
	{
		if (value != null && (value < 0 || value > 2047))
		{
			errors.add(expectedId + ": " + field + ": rotation " + value
				+ " is outside the valid 0-2047 range and would crash the client (docs/DECISIONS.md D23/D28)");
		}
	}

	private static String mechanicLabel(Mechanic mechanic, int index)
	{
		return isBlank(mechanic.getId()) ? "mechanics[" + index + "]" : "mechanic '" + mechanic.getId() + "'";
	}

	private static void requireNonBlank(List<String> errors, String expectedId, String field, String value)
	{
		if (isBlank(value))
		{
			errors.add(expectedId + ": missing required field '" + field + "'");
		}
	}

	private static boolean isValidSlug(String value)
	{
		return SLUG_PATTERN.matcher(value).matches();
	}

	private static boolean isBlank(String value)
	{
		return value == null || value.trim().isEmpty();
	}

	private static boolean isEmpty(List<?> list)
	{
		return list == null || list.isEmpty();
	}
}
