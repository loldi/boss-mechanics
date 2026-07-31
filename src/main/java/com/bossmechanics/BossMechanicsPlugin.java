package com.bossmechanics;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.BossDataLoader;
import com.bossmechanics.data.LoadResult;
import com.bossmechanics.spike.SireWidgetSpike;
import com.google.inject.Provides;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Boss Mechanics",
	description = "Learn boss mechanics in-game with a discovery progress bar and animated move previews",
	tags = {"boss", "pvm", "mechanics", "collection log", "combat"}
)
public class BossMechanicsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BossMechanicsConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private SireWidgetSpike sireWidgetSpike;

	@Inject
	private BossDataLoader bossDataLoader;

	private List<Boss> bosses = Collections.emptyList();

	@Override
	protected void startUp() throws Exception
	{
		log.info("Boss Mechanics started");

		LoadResult result = bossDataLoader.loadAll();
		for (String error : result.getErrors())
		{
			log.warn("Boss Mechanics data error: {}", error);
		}
		bosses = result.getBosses();
		log.info("Loaded {} boss(es)", bosses.size());

		// TODO: subscribe detection listeners (AnimationChanged, ProjectileMoved, GraphicsObjectCreated)
		// TODO: inject Boss Mechanics button into the collection log on WidgetLoaded

		// Issue #1 spike (delete-or-promote): see com.bossmechanics.spike.SireWidgetSpike
		eventBus.register(sireWidgetSpike);
		sireWidgetSpike.onPluginStart();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Boss Mechanics stopped");
		// TODO: remove injected button, close our interface if open

		sireWidgetSpike.onPluginStop();
		eventBus.unregister(sireWidgetSpike);
	}

	@Provides
	BossMechanicsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossMechanicsConfig.class);
	}
}
