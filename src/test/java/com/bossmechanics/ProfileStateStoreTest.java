package com.bossmechanics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Tests only the static CSV codec ({@link ProfileStateStore#parseCsv} /
 * {@link ProfileStateStore#toCsv}), which holds all the logic. The adapter bodies around
 * {@code ConfigManager} are deliberately logic-free (humble object) and not unit-tested here.
 */
public class ProfileStateStoreTest
{
	@Test
	public void parseCsvOfNullIsEmpty()
	{
		assertTrue(ProfileStateStore.parseCsv(null).isEmpty());
	}

	@Test
	public void parseCsvOfEmptyStringIsEmpty()
	{
		assertTrue(ProfileStateStore.parseCsv("").isEmpty());
	}

	@Test
	public void parseCsvTrimsAndSkipsBlankEntries()
	{
		Set<String> expected = new HashSet<>();
		expected.add("miasma-pools");
		expected.add("spawn-summon");

		assertEquals(expected, ProfileStateStore.parseCsv(" miasma-pools, ,spawn-summon , "));
	}

	@Test
	public void toCsvSortsOutput()
	{
		Set<String> values = new HashSet<>();
		values.add("spawn-summon");
		values.add("miasma-pools");

		assertEquals("miasma-pools,spawn-summon", ProfileStateStore.toCsv(values));
	}

	@Test
	public void toCsvOfEmptySetIsEmptyString()
	{
		assertEquals("", ProfileStateStore.toCsv(new HashSet<>()));
	}

	@Test
	public void roundtripIsStable()
	{
		Set<String> original = new HashSet<>();
		original.add("miasma-pools");
		original.add("spawn-summon");
		original.add("scions");

		Set<String> roundtripped = ProfileStateStore.parseCsv(ProfileStateStore.toCsv(original));

		assertEquals(original, roundtripped);
	}
}
