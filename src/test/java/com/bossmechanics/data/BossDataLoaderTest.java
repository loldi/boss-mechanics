package com.bossmechanics.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class BossDataLoaderTest
{
	private static final String MINIMAL_VALID_JSON = "{"
		+ "\"id\": \"vorkath\","
		+ "\"name\": \"Vorkath\","
		+ "\"npcIds\": [8059],"
		+ "\"wikiUrl\": \"https://oldschool.runescape.wiki/w/Vorkath\","
		+ "\"mechanics\": ["
		+ "  {"
		+ "    \"id\": \"zombified-spawn\","
		+ "    \"name\": \"Zombified Spawn\","
		+ "    \"description\": \"Vorkath summons a spawn.\","
		+ "    \"counterplay\": \"Kill it fast.\","
		+ "    \"detection\": [ { \"type\": \"npc-spawn\", \"id\": 8063 } ],"
		+ "    \"preview\": { \"animationId\": 7960, \"staticFallback\": true }"
		+ "  }"
		+ "]"
		+ "}";

	private BossDataLoader loader;

	@Before
	public void setUp()
	{
		// resourceRoot is unused by parseOne; loadAll-focused tests live in BundledBossDataTest.
		loader = new BossDataLoader(new Gson(), "unused-in-this-test");
	}

	@Test
	public void parsesValidBossJson()
	{
		LoadResult result = loader.parseOne(MINIMAL_VALID_JSON, "vorkath");

		assertTrue(result.getErrors().isEmpty());
		assertEquals(1, result.getBosses().size());

		Boss boss = result.getBosses().get(0);
		assertEquals("Vorkath", boss.getName());
		assertEquals(Collections.singletonList(8059), boss.getNpcIds());
		assertEquals(1, boss.getMechanics().size());

		Trigger trigger = boss.getMechanics().get(0).getDetection().get(0);
		assertEquals(TriggerType.NPC_SPAWN, trigger.triggerType());
	}

	@Test
	public void missingNameIsError()
	{
		String json = MINIMAL_VALID_JSON.replace("\"name\": \"Vorkath\",", "");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "name"));
	}

	@Test
	public void unknownTriggerTypeIsErrorNamingTheBadValue()
	{
		String json = MINIMAL_VALID_JSON.replace("\"npc-spawn\"", "\"projectil\"");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "projectil"));
	}

	@Test
	public void idNotMatchingFilenameIsError()
	{
		LoadResult result = loader.parseOne(MINIMAL_VALID_JSON, "not-vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "vorkath"));
		assertTrue(errorContaining(result, "not-vorkath"));
	}

	@Test
	public void duplicateMechanicIdsIsError()
	{
		String mechanic = "{"
			+ "\"id\": \"zombified-spawn\","
			+ "\"name\": \"Zombified Spawn\","
			+ "\"description\": \"Vorkath summons a spawn.\","
			+ "\"counterplay\": \"Kill it fast.\","
			+ "\"detection\": [ { \"type\": \"npc-spawn\", \"id\": 8063 } ],"
			+ "\"preview\": { \"animationId\": 7960, \"staticFallback\": true }"
			+ "}";
		String json = "{"
			+ "\"id\": \"vorkath\","
			+ "\"name\": \"Vorkath\","
			+ "\"npcIds\": [8059],"
			+ "\"wikiUrl\": \"https://oldschool.runescape.wiki/w/Vorkath\","
			+ "\"mechanics\": [" + mechanic + "," + mechanic + "]"
			+ "}";

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "duplicate"));
		assertTrue(errorContaining(result, "zombified-spawn"));
	}

	@Test
	public void missingTriggerIdIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(", \"id\": 8063", "");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "id"));
	}

	/**
	 * Issue #6, docs/DECISIONS.md D23: an absent animationId with staticFallback false (or
	 * absent) has nothing for the model box to play, so it is caught here rather than at runtime.
	 */
	@Test
	public void missingAnimationIdWithoutStaticFallbackIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"staticFallback\": false }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "zombified-spawn"));
		assertTrue(errorContaining(result, "animationId"));
	}

	@Test
	public void malformedJsonYieldsErrorNotException()
	{
		LoadResult result = loader.parseOne("{not json", "x");

		assertTrue(result.getBosses().isEmpty());
		assertEquals(1, result.getErrors().size());
	}

	@Test
	public void invalidBossIdSlugIsError()
	{
		// expectedId matches the (invalid) boss id so only the slug rule fires, not the
		// "id does not match expected" rule.
		String json = MINIMAL_VALID_JSON.replace("\"id\": \"vorkath\",", "\"id\": \"Bad Id\",");

		LoadResult result = loader.parseOne(json, "Bad Id");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "Bad Id"));
	}

	@Test
	public void invalidMechanicIdSlugIsError()
	{
		// A comma in a mechanic id would corrupt the CSV persistence format (#8), not just
		// look ugly, so this is the case worth locking down explicitly.
		String json = MINIMAL_VALID_JSON.replace("\"id\": \"zombified-spawn\",", "\"id\": \"a,b\",");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "a,b"));
	}

	private static boolean errorContaining(LoadResult result, String substring)
	{
		for (String error : result.getErrors())
		{
			if (error.contains(substring))
			{
				return true;
			}
		}
		return false;
	}
}
