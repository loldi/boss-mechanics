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
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Issue #1 spike: prove/disprove whether a custom RuneLite widget can render the
 * Abyssal Sire model playing an animation (docs/DECISIONS.md D15). Delete-or-promote.
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

	private static final int PARENT_CHATBOX = -2;
	private static final int MAX_PARENT_SCAN = 40;
	private static final int MAX_BUILD_RETRIES = 10;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BossMechanicsConfig config;

	// Widgets we created, so teardown never touches anything we don't own.
	private final List<Widget> spikeWidgets = new ArrayList<>();

	private int buildRetries;

	/**
	 * Called from the plugin's startUp. Without this, toggling the plugin off and on
	 * while already logged in leaves nothing built: no config change and no game state
	 * change fires, so neither subscriber runs.
	 */
	public void onPluginStart()
	{
		clientThread.invokeLater(this::rebuild);
	}

	/** Called from the plugin's shutDown so disabling the plugin doesn't strand widgets on screen. */
	public void onPluginStop()
	{
		clientThread.invokeLater(this::teardown);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BossMechanicsConfig.GROUP.equals(event.getGroup()) || !event.getKey().startsWith("spike"))
		{
			return;
		}

		log.info("Sire widget spike: config changed ({}={}), rebuilding on client thread", event.getKey(), event.getNewValue());
		buildRetries = 0;
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

	/**
	 * The interface tree is not always ready when the spike is switched on or the
	 * player logs in, which is why the first build silently produced nothing and the
	 * spike had to be toggled off and on by hand. Retry on tick until the build
	 * actually yields widgets, then stop.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.spikeEnabled() || !spikeWidgets.isEmpty() || buildRetries >= MAX_BUILD_RETRIES)
		{
			return;
		}

		buildRetries++;
		clientThread.invokeLater(this::rebuild);
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
	 * Where our widgets get parented. The top-level interface has many children and
	 * most are not drawable layers, so index -1 picks the largest visible one from
	 * the live tree rather than assuming index 0 works. -2 is the chatbox container,
	 * which core RuneLite (ChatboxPanelManager) proves accepts dynamic children.
	 */
	private Widget resolveParent()
	{
		int index = config.spikeParentIndex();

		if (index == PARENT_CHATBOX)
		{
			return client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		}

		int topLevel = client.getTopLevelInterfaceId();
		if (topLevel < 0)
		{
			// No top-level interface yet (still loading in). Passing this to getWidget
			// indexes the client's group array with -1 and throws.
			log.info("Sire widget spike: no top-level interface yet, skipping build");
			return null;
		}

		if (index >= 0)
		{
			return client.getWidget(topLevel, index);
		}

		return largestVisibleChild(topLevel);
	}

	/**
	 * Logs every child of the top-level interface and returns the biggest visible
	 * one. The log is the point: it tells us which indices are real layers, so a
	 * failed render can be retargeted live via the parent-index config.
	 */
	private Widget largestVisibleChild(int topLevel)
	{
		Widget best = null;
		long bestArea = 0;

		log.info("Sire widget spike: top-level interface {} candidate children:", topLevel);

		for (int i = 0; i < MAX_PARENT_SCAN; i++)
		{
			Widget candidate = client.getWidget(topLevel, i);
			if (candidate == null)
			{
				continue;
			}

			long area = (long) candidate.getWidth() * candidate.getHeight();
			log.info("  [{}] id={} type={} hidden={} {}x{} at ({},{}) area={}",
				i, candidate.getId(), candidate.getType(), candidate.isHidden(),
				candidate.getWidth(), candidate.getHeight(),
				candidate.getRelativeX(), candidate.getRelativeY(), area);

			if (!candidate.isHidden() && area > bestArea)
			{
				best = candidate;
				bestArea = area;
			}
		}

		if (best == null)
		{
			log.warn("Sire widget spike: no visible child found under top-level {}", topLevel);
		}
		else
		{
			log.info("Sire widget spike: auto-picked parent index {} (id={}, {}x{})",
				best.getIndex(), best.getId(), best.getWidth(), best.getHeight());
		}

		return best;
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

		// Deliberately loud: this backdrop is the diagnostic that separates "parenting
		// is broken" from "the model is broken". If a magenta square appears and the
		// boss doesn't, parenting works and the problem is the model or its animation.
		// Opacity is inverted in RuneLite: 0 is fully opaque, 255 is invisible.
		Widget backdrop = parent.createChild(-1, WidgetType.RECTANGLE);
		backdrop.setFilled(true);
		backdrop.setOpacity(0);
		backdrop.setTextColor(0xFF00FF);
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
		model.setRotationX(clampRotation(config.spikeRotationX()));
		model.setRotationY(clampRotation(config.spikeRotationY()));
		model.setRotationZ(0);
		model.setOriginalX(WIDGET_X);
		model.setOriginalY(WIDGET_Y);
		model.setOriginalWidth(WIDGET_WIDTH);
		model.setOriginalHeight(WIDGET_HEIGHT);
		model.revalidate();
		spikeWidgets.add(model);

		// Revalidating a child computes nothing on its own: the first attempt left both
		// widgets at canvas (-1,-1), built and unhidden but never laid out. Position is
		// assigned by the parent layer's layout pass, so the parent is what must
		// revalidate once the children exist.
		parent.revalidate();

		log.info("Sire widget spike: built parent id={} ({}x{}) npcId={} modelId={} animationId={} zoom={}",
			parent.getId(), parent.getWidth(), parent.getHeight(), npcId, modelId, animationId, zoom);
		logPlacement("backdrop", backdrop);
		logPlacement("model", model);
	}

	/** Post-revalidate geometry: zero size or an off-screen origin means it will never be seen. */
	private void logPlacement(String label, Widget widget)
	{
		// Note: canvas location reads (-1,-1) for these dynamic children even when they
		// render on screen, so it is not a usable signal. Trust relative coords.
		log.info("Sire widget spike: {} hidden={} selfHidden={} {}x{} relative=({},{}) canvas={} parentId={}",
			label, widget.isHidden(), widget.isSelfHidden(),
			widget.getWidth(), widget.getHeight(),
			widget.getRelativeX(), widget.getRelativeY(),
			widget.getCanvasLocation(), widget.getParentId());
	}

	private static int clampRotation(int value)
	{
		return Math.max(0, Math.min(2047, value));
	}
}
