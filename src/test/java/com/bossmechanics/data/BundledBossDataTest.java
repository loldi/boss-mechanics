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
