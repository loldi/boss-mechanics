package com.bossmechanics.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Parses and validates the bundled boss data files. Runtime and tests share this one class so
 * there is exactly one parse+validate path: {@link #loadAll()} discovers and reads every bundled
 * file; {@link #parseOne(String, String)} is the shared seam both {@link #loadAll()} and tests
 * use to turn one file's JSON text into a {@link Boss} (or errors).
 */
public class BossDataLoader
{
	private static final String DEFAULT_RESOURCE_ROOT = "/data/bosses";

	private final Gson gson;
	private final String resourceRoot;

	@Inject
	public BossDataLoader(Gson gson)
	{
		this(gson, DEFAULT_RESOURCE_ROOT);
	}

	/** Package-visible so tests can point at fixture roots instead of the bundled data. */
	BossDataLoader(Gson gson, String resourceRoot)
	{
		this.gson = gson;
		this.resourceRoot = resourceRoot;
	}

	/**
	 * Discovers every bundled boss via {@code <resourceRoot>/index.json} and parses+validates
	 * each one. A missing/malformed index, an unreadable file, or a duplicate id in the index
	 * becomes an error entry; nothing here throws.
	 */
	public LoadResult loadAll()
	{
		List<String> ids;
		try
		{
			ids = readIndex();
		}
		catch (IOException | JsonParseException e)
		{
			return new LoadResult(Collections.emptyList(), Collections.singletonList(
				resourceRoot + "/index.json: " + e.getMessage()));
		}

		List<Boss> bosses = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		Set<String> seenIds = new HashSet<>();

		for (String id : ids)
		{
			if (!seenIds.add(id))
			{
				errors.add("index.json: duplicate id '" + id + "'");
				continue;
			}

			String path = resourceRoot + "/" + id + ".json";
			String json;
			try
			{
				json = readResource(path);
			}
			catch (IOException e)
			{
				errors.add(id + ": could not read " + path + " (" + e.getMessage() + ")");
				continue;
			}

			LoadResult parsed = parseOne(json, id);
			bosses.addAll(parsed.getBosses());
			errors.addAll(parsed.getErrors());
		}

		return new LoadResult(bosses, errors);
	}

	/**
	 * Parses one boss file's JSON text and validates it against {@code expectedId} (the
	 * filename/index entry it was loaded as). Never throws: malformed JSON becomes a single
	 * error entry instead of propagating Gson's parse exception. A boss with any validation
	 * error is omitted from the result's bosses (skipped), not partially included.
	 */
	public LoadResult parseOne(String json, String expectedId)
	{
		Boss boss;
		try
		{
			boss = gson.fromJson(json, Boss.class);
		}
		catch (JsonParseException e)
		{
			return new LoadResult(Collections.emptyList(), Collections.singletonList(
				expectedId + ": malformed JSON (" + e.getMessage() + ")"));
		}

		List<String> errors = BossDataValidator.validate(boss, expectedId);
		List<Boss> bosses = new ArrayList<>();
		if (errors.isEmpty())
		{
			bosses.add(boss);
		}

		return new LoadResult(bosses, errors);
	}

	private List<String> readIndex() throws IOException
	{
		String json = readResource(resourceRoot + "/index.json");
		String[] ids = gson.fromJson(json, String[].class);
		return ids == null ? Collections.emptyList() : Arrays.asList(ids);
	}

	private String readResource(String path) throws IOException
	{
		try (InputStream in = getClass().getResourceAsStream(path))
		{
			if (in == null)
			{
				throw new IOException("resource not found: " + path);
			}

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) != -1)
			{
				out.write(buffer, 0, read);
			}
			return out.toString(StandardCharsets.UTF_8.name());
		}
	}
}
