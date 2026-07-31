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
}
