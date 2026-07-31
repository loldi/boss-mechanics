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
