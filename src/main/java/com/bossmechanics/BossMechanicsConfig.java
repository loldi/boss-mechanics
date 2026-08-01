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
		keyName = "logUnmatchedTriggers",
		name = "Log boss trigger ids",
		description = "Curation aid: log every animation, projectile and graphic id a tracked boss produces, and which mechanic claims it. Each id logs once per session",
		position = 2
	)
	default boolean logUnmatchedTriggers()
	{
		return false;
	}

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
		description = "Model widget zoom level. 3000 frames the Sire well; 1000 is too far out to read",
		position = 94
	)
	default int spikeZoom()
	{
		return 3000;
	}

	@ConfigItem(
		keyName = "spikeParentIndex",
		name = "(Spike) Parent index",
		description = "Child index of the top-level interface to parent into. 1 is the layer that actually renders; -1 auto-picks the largest visible layer (unreliable, it picks 0 which does not draw); -2 uses the chatbox container",
		position = 95
	)
	default int spikeParentIndex()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "spikeRotationX",
		name = "(Spike) Rotation X",
		description = "Model pitch, 0-2047. Try ~150 if the model renders edge-on",
		position = 96
	)
	default int spikeRotationX()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "spikeRotationY",
		name = "(Spike) Rotation Y",
		description = "Model yaw, 0-2047",
		position = 97
	)
	default int spikeRotationY()
	{
		return 0;
	}
}
