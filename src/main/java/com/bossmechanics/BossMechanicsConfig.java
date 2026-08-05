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
}
