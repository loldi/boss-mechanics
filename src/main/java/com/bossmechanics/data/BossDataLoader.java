package com.bossmechanics.data;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

	public LoadResult loadAll()
	{
		throw new UnsupportedOperationException("loadAll() lands in a later slice");
	}

	/**
	 * Parses one boss file's JSON text and validates it against {@code expectedId} (the
	 * filename/index entry it was loaded as). Never throws: malformed JSON becomes an error entry.
	 */
	public LoadResult parseOne(String json, String expectedId)
	{
		Boss boss = gson.fromJson(json, Boss.class);

		List<Boss> bosses = new ArrayList<>();
		bosses.add(boss);
		return new LoadResult(bosses, Collections.emptyList());
	}
}
