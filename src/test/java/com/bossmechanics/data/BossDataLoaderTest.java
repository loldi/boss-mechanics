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
}
