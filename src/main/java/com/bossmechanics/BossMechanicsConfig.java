package com.bossmechanics;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BossMechanicsConfig.GROUP)
public interface BossMechanicsConfig extends Config
{
	String GROUP = "bossmechanics";

	@ConfigItem(
		keyName = "discoveryMessages",
		name = "Discovery chat messages",
		description = "Announce newly discovered mechanics in the chatbox",
		position = 1
	)
	default boolean discoveryMessages()
	{
		return true;
	}

	// Note: discovered/revealed state is stored per-character via ConfigManager
	// RS-profile keys, not exposed here as config items.

	@ConfigItem(
		keyName = "clearDiscoveries",
		name = "Clear discoveries",
		description = "Forget every discovered mechanic for this character so they can be found again. Unticks itself once done",
		position = 3
	)
	default boolean clearDiscoveries()
	{
		return false;
	}

	@ConfigItem(
		keyName = "logUnmatchedTriggers",
		name = "Log boss trigger ids",
		description = "Curation aid: log every animation, projectile and graphic id a tracked boss produces, and which mechanic claims it, plus any collection log page title no boss data matches. Each id and title logs once per session",
		position = 2
	)
	default boolean logUnmatchedTriggers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "perfInstrumentation",
		name = "Measure handler cost",
		description = "Debug tool. Tracks call count, wall time (p50/p99/max) and bytes allocated per "
			+ "live-gameplay event handler. Off costs nothing: a single boolean check, no timing, no "
			+ "allocation",
		position = 4
	)
	default boolean perfInstrumentation()
	{
		return false;
	}

	@ConfigItem(
		keyName = "dumpPerfStats",
		name = "Dump perf stats",
		description = "Debug tool. Logs the perf table collected since instrumentation was last "
			+ "enabled (or last dumped) and resets its counters. Unticks itself once done",
		position = 5
	)
	default boolean dumpPerfStats()
	{
		return false;
	}
}
