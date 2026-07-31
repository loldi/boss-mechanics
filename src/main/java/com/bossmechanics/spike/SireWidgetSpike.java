package com.bossmechanics.spike;

import com.bossmechanics.BossMechanicsConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Issue #1 spike: prove/disprove whether a custom RuneLite widget can render the
 * Abyssal Sire model playing an animation (docs/DECISIONS.md D14). Delete-or-promote.
 *
 * Not production code: no button, no real interface, no styling, no data loading.
 * All config is dev-facing (see the "(Spike)" section of BossMechanicsConfig) so the
 * NPC id / animation id / model index / zoom can be tuned live in-client.
 */
@Slf4j
public class SireWidgetSpike
{
	private static final int WIDGET_X = 40;
	private static final int WIDGET_Y = 40;
	private static final int WIDGET_WIDTH = 220;
	private static final int WIDGET_HEIGHT = 220;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BossMechanicsConfig config;

	// Widgets we created, so teardown never touches anything we don't own.
	private final List<Widget> spikeWidgets = new ArrayList<>();

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BossMechanicsConfig.GROUP.equals(event.getGroup()) || !event.getKey().startsWith("spike"))
		{
			return;
		}

		log.info("Sire widget spike: config changed ({}={}), rebuilding on client thread", event.getKey(), event.getNewValue());
		clientThread.invokeLater(this::rebuild);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			// The interface tree is torn down on these transitions; our widget
			// references are already invalid, so just drop them without touching them.
			spikeWidgets.clear();
			return;
		}

		if (state == GameState.LOGGED_IN && config.spikeEnabled())
		{
			clientThread.invokeLater(this::rebuild);
		}
	}

	/** Tear down whatever we last built, then rebuild if the spike is enabled. */
	private void rebuild()
	{
		teardown();

		if (!config.spikeEnabled())
		{
			log.info("Sire widget spike: disabled, nothing built");
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			log.info("Sire widget spike: not logged in, skipping build");
			return;
		}

		build();
	}

	private void teardown()
	{
		for (Widget widget : spikeWidgets)
		{
			widget.setHidden(true);
		}
		spikeWidgets.clear();
	}

	/**
	 * Where our widgets get parented. One method so we can swap in the chatbox
	 * container as a fallback parent if the top-level root layer misbehaves,
	 * without touching call sites.
	 */
	private Widget resolveParent()
	{
		return client.getWidget(client.getTopLevelInterfaceId(), 0);
	}

	private void build()
	{
		Widget parent = resolveParent();
		if (parent == null)
		{
			log.warn("Sire widget spike: parent widget is null, aborting build");
			return;
		}

		int npcId = config.spikeNpcId();
		NPCComposition def = client.getNpcDefinition(npcId);
		if (def == null)
		{
			log.warn("Sire widget spike: no NPC definition for id {}", npcId);
			return;
		}

		int[] models = def.getModels();
		log.info("Sire widget spike: npc {} getModels() = {}", npcId, Arrays.toString(models));

		if (models == null || models.length == 0)
		{
			log.warn("Sire widget spike: npc {} has no models, aborting build", npcId);
			return;
		}

		int modelIndex = config.spikeModelIndex();
		if (modelIndex < 0 || modelIndex >= models.length)
		{
			log.warn("Sire widget spike: model index {} out of bounds for models {}", modelIndex, Arrays.toString(models));
			return;
		}

		// Dark backdrop so the model reads against whatever's behind it. Created
		// first so the model widget (created after) renders on top of it.
		Widget backdrop = parent.createChild(-1, WidgetType.RECTANGLE);
		backdrop.setFilled(true);
		backdrop.setOpacity(180);
		backdrop.setTextColor(0x000000);
		backdrop.setOriginalX(WIDGET_X);
		backdrop.setOriginalY(WIDGET_Y);
		backdrop.setOriginalWidth(WIDGET_WIDTH);
		backdrop.setOriginalHeight(WIDGET_HEIGHT);
		backdrop.revalidate();
		spikeWidgets.add(backdrop);

		int modelId = models[modelIndex];
		int animationId = config.spikeAnimationId();
		int zoom = config.spikeZoom();

		Widget model = parent.createChild(-1, WidgetType.MODEL);
		model.setModelType(WidgetModelType.MODEL);
		model.setModelId(modelId);
		model.setAnimationId(animationId);
		model.setModelZoom(zoom);
		// Rotation must stay within 0-2047 or the client crashes.
		model.setRotationX(0);
		model.setRotationY(0);
		model.setRotationZ(0);
		model.setOriginalX(WIDGET_X);
		model.setOriginalY(WIDGET_Y);
		model.setOriginalWidth(WIDGET_WIDTH);
		model.setOriginalHeight(WIDGET_HEIGHT);
		model.revalidate();
		spikeWidgets.add(model);

		log.info("Sire widget spike: built widget id={} npcId={} modelId={} animationId={} zoom={}",
			model.getId(), npcId, modelId, animationId, zoom);
	}
}
