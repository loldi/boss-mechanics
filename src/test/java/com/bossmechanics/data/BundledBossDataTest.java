package com.bossmechanics.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The gate: fails the build the moment a bundled data/bosses/*.json file breaks the schema, or
 * data/bosses/index.json drifts from the real directory listing (docs/DECISIONS.md D7). Gradle
 * runs tests with the project directory as the working directory, so the relative path below
 * reaches the real repo-root data/bosses/, independent of {@link BossDataLoader}'s classpath root.
 */
public class BundledBossDataTest
{
	private static final Path BOSSES_DIR = Paths.get("data", "bosses");

	@Test
	public void allBundledBossFilesAreValid()
	{
		LoadResult result = new BossDataLoader(new Gson()).loadAll();

		assertTrue("expected no errors but got: " + result.getErrors(), result.getErrors().isEmpty());
		assertTrue("expected at least one bundled boss", result.getBosses().size() > 0);
	}

	@Test
	public void indexMatchesDirectoryListing() throws IOException
	{
		assertEquals(idsOnDisk(), idsInIndex());
	}

	/**
	 * {@code shiftY} is a PIXEL offset consumed directly by {@code MechanicsDetail.showModel}'s
	 * rect mutation (docs/DECISIONS.md D26) — {@code setOriginalHeight(MODEL_HEIGHT + 2*shiftY)}
	 * inside a 140px-tall box. A correctly-projected value can therefore never exceed half that
	 * box, 70px; anything larger is almost certainly a raw model-unit value (what the cachetool's
	 * {@code FitZoom} emits before the {@code shiftY_px = round(shiftY_units * 512 / zoom)}
	 * projection) baked straight into the JSON without converting it.
	 */
	@Test
	public void everyPreviewShiftYFitsInHalfTheModelBoxHeight()
	{
		// MechanicsDetail.MODEL_HEIGHT / 2 (140 / 2). Duplicated rather than referenced: that
		// constant is package-private in a different package (ui), and this guard exists
		// specifically so a stale copy here would need noticing before it could go stale.
		int maxShiftYPixels = 70;

		LoadResult result = new BossDataLoader(new Gson()).loadAll();
		for (Boss boss : result.getBosses())
		{
			for (Mechanic mechanic : boss.getMechanics())
			{
				Preview preview = mechanic.getPreview();
				int shiftY = preview == null || preview.getShiftY() == null ? 0 : preview.getShiftY();
				assertTrue(boss.getId() + "/" + mechanic.getId() + ": shiftY " + shiftY
						+ "px exceeds half the 140px model box (" + maxShiftYPixels + "px) — likely a "
						+ "raw model-unit value baked without the units-to-pixels projection",
					shiftY <= maxShiftYPixels);
			}
		}
	}

	/**
	 * Every {@code preview.sprite} name must resolve to a bundled classpath resource under
	 * {@code /sprites/} (docs/DECISIONS.md D27) -- a plain {@code getResourceAsStream} lookup, not
	 * a RuneLite API, so {@code data} stays the RuneLite-free package it always has been.
	 */
	@Test
	public void everyPreviewSpriteResolvesToABundledResource()
	{
		LoadResult result = new BossDataLoader(new Gson()).loadAll();
		for (Boss boss : result.getBosses())
		{
			for (Mechanic mechanic : boss.getMechanics())
			{
				Preview preview = mechanic.getPreview();
				String sprite = preview == null ? null : preview.getSprite();
				if (sprite == null)
				{
					continue;
				}

				String path = "/sprites/" + sprite;
				assertTrue(boss.getId() + "/" + mechanic.getId() + ": no bundled resource at "
						+ path + " (docs/DECISIONS.md D27)",
					getClass().getResourceAsStream(path) != null);
			}
		}
	}

	private static Set<String> idsOnDisk() throws IOException
	{
		try (Stream<Path> files = Files.list(BOSSES_DIR))
		{
			return files
				.map(Path::getFileName)
				.map(Path::toString)
				.filter(name -> name.endsWith(".json"))
				.filter(name -> !name.equals("index.json"))
				.map(name -> name.substring(0, name.length() - ".json".length()))
				.collect(Collectors.toSet());
		}
	}

	private static Set<String> idsInIndex() throws IOException
	{
		String json = new String(Files.readAllBytes(BOSSES_DIR.resolve("index.json")));
		String[] ids = new Gson().fromJson(json, String[].class);
		return new HashSet<>(Arrays.asList(ids));
	}
}
