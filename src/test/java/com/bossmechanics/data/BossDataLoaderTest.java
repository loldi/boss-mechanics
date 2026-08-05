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

	/**
	 * docs/DECISIONS.md D27, sprite tier: a sprite preview carries no model fields at all, so it
	 * must not trip the "animationId required" rule above -- the sprite wins over every model
	 * field, including the requirement that one of them be present.
	 */
	@Test
	public void spritePreviewNeedsNoAnimationId()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"sprite\": \"zombified-spawn.png\" }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue("expected no errors but got: " + result.getErrors(), result.getErrors().isEmpty());
		assertEquals(1, result.getBosses().size());
	}

	/**
	 * docs/DECISIONS.md D28: curated rotation fields round-trip from JSON to {@link Preview}
	 * untouched, the same null-means-default shape as {@code zoom}/{@code shiftX}.
	 */
	@Test
	public void rotationFieldsRoundTripFromJson()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true, "
				+ "\"rotationX\": 512, \"rotationY\": 1024, \"rotationZ\": 1536 }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue("expected no errors but got: " + result.getErrors(), result.getErrors().isEmpty());
		Preview preview = result.getBosses().get(0).getMechanics().get(0).getPreview();
		assertEquals(Integer.valueOf(512), preview.getRotationX());
		assertEquals(Integer.valueOf(1024), preview.getRotationY());
		assertEquals(Integer.valueOf(1536), preview.getRotationZ());
	}

	/** docs/DECISIONS.md D28: an absent rotation field parses as null, i.e. "no rotation curated". */
	@Test
	public void rotationFieldsDefaultToNullWhenAbsent()
	{
		LoadResult result = loader.parseOne(MINIMAL_VALID_JSON, "vorkath");

		Preview preview = result.getBosses().get(0).getMechanics().get(0).getPreview();
		assertEquals(null, preview.getRotationX());
		assertEquals(null, preview.getRotationY());
		assertEquals(null, preview.getRotationZ());
	}

	/**
	 * docs/DECISIONS.md D23/D28: a rotation value outside 0-2047 crashes the client, so it is
	 * rejected at curation time instead.
	 */
	@Test
	public void rotationOutsideValidRangeIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true, \"rotationX\": 2048 }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "rotationX"));
		assertTrue(errorContaining(result, "2048"));
	}

	/** docs/DECISIONS.md D28: a negative rotation value is just as invalid as one over 2047. */
	@Test
	public void negativeRotationIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true, \"rotationZ\": -1 }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "rotationZ"));
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

	/** docs/DECISIONS.md D37: a chained preview parses, and its animationId resolves to segment 0's. */
	@Test
	public void chainedPreviewParses()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationChain\": ["
				+ "  { \"animationId\": 12412, \"cycles\": 60 },"
				+ "  { \"animationId\": 12413, \"cycles\": 30 }"
				+ "] }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue("expected no errors but got: " + result.getErrors(), result.getErrors().isEmpty());
		Preview preview = result.getBosses().get(0).getMechanics().get(0).getPreview();
		assertEquals(2, preview.getAnimationChain().size());
		assertEquals(Integer.valueOf(12412), preview.getAnimationChain().get(0).getAnimationId());
		assertEquals(Integer.valueOf(60), preview.getAnimationChain().get(0).getCycles());
	}

	/** docs/DECISIONS.md D37: chain and animationId are mutually exclusive. */
	@Test
	public void chainAndAnimationIdBothPresentIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationId\": 7960, \"animationChain\": ["
				+ "  { \"animationId\": 12412, \"cycles\": 60 },"
				+ "  { \"animationId\": 12413, \"cycles\": 30 }"
				+ "] }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "animationChain"));
		assertTrue(errorContaining(result, "animationId"));
	}

	/** docs/DECISIONS.md D37: a single-segment chain is just animationId spelled a longer way. */
	@Test
	public void oneSegmentChainIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationChain\": [ { \"animationId\": 12412, \"cycles\": 60 } ] }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "animationChain"));
	}

	/** docs/DECISIONS.md D37: cycles must be at least 1 client tick. */
	@Test
	public void chainSegmentWithCyclesLessThanOneIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationChain\": ["
				+ "  { \"animationId\": 12412, \"cycles\": 0 },"
				+ "  { \"animationId\": 12413, \"cycles\": 30 }"
				+ "] }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "cycles"));
	}

	/** docs/DECISIONS.md D37: a chain segment missing its animationId has nothing to play. */
	@Test
	public void chainSegmentWithMissingAnimationIdIsError()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationChain\": ["
				+ "  { \"cycles\": 60 },"
				+ "  { \"animationId\": 12413, \"cycles\": 30 }"
				+ "] }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue(result.getBosses().isEmpty());
		assertTrue(errorContaining(result, "animationId"));
	}

	/** docs/DECISIONS.md D37: a chain alone satisfies the "animationId required" rule. */
	@Test
	public void chainAloneSatisfiesTheAnimationIdRequiredRule()
	{
		String json = MINIMAL_VALID_JSON.replace(
			"\"preview\": { \"animationId\": 7960, \"staticFallback\": true }",
			"\"preview\": { \"animationChain\": ["
				+ "  { \"animationId\": 12412, \"cycles\": 60 },"
				+ "  { \"animationId\": 12413, \"cycles\": 30 }"
				+ "] }");

		LoadResult result = loader.parseOne(json, "vorkath");

		assertTrue("a chain must satisfy the animationId-required rule on its own: "
			+ result.getErrors(), result.getErrors().isEmpty());
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
