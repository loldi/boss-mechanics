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

	// --- Issue #1 spike: Abyssal Sire widget animation (delete-or-promote) ---

	@ConfigItem(
		keyName = "spikeEnabled",
		name = "(Spike) Enabled",
		description = "Toggle the Abyssal Sire widget-animation spike on/off",
		position = 90
	)
	default boolean spikeEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "spikeNpcId",
		name = "(Spike) NPC id",
		description = "NPC id to render, e.g. Abyssal Sire (stasis awake) = 5887",
		position = 91
	)
	default int spikeNpcId()
	{
		return 5887;
	}

	@ConfigItem(
		keyName = "spikeAnimationId",
		name = "(Spike) Animation id",
		description = "Animation id to play on the model widget, e.g. SIRE_IDLE_ONE = 4529",
		position = 92
	)
	default int spikeAnimationId()
	{
		return 4529;
	}

	@ConfigItem(
		keyName = "spikeModelIndex",
		name = "(Spike) Model index",
		description = "Index into NPCComposition.getModels() to render",
		position = 93
	)
	default int spikeModelIndex()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "spikeZoom",
		name = "(Spike) Zoom",
		description = "Model widget zoom level",
		position = 94
	)
	default int spikeZoom()
	{
		return 1000;
	}
}
