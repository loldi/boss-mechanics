package com.bossmechanics;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.BossDataLoader;
import com.bossmechanics.data.LoadResult;
import com.bossmechanics.detection.DetectionEngine;
import com.bossmechanics.detection.Discovery;
import com.bossmechanics.data.TriggerType;
import com.bossmechanics.detection.DiscoveryState;
import com.bossmechanics.spike.SireWidgetSpike;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
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
	private ClientThread clientThread;

	@Inject
	private BossMechanicsConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private SireWidgetSpike sireWidgetSpike;

	@Inject
	private BossDataLoader bossDataLoader;

	private List<Boss> bosses = Collections.emptyList();
	private DiscoveryState discoveryState;
	private DetectionEngine detectionEngine;

	// Ids already reported by the unmatched-trigger log, so a projectile in flight
	// doesn't reprint every cycle.
	private final Set<String> loggedUnmatched = new HashSet<>();

	private static final Color MECHANIC_NAME_COLOR = new Color(0xCC0000);

	// A plugin field, not a local, so #5's reveal UI can reach isRevealed/setRevealed.
	private ProfileStateStore profileStateStore;

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

		List<String> bossIds = new ArrayList<>();
		for (Boss boss : bosses)
		{
			bossIds.add(boss.getId());
		}

		profileStateStore = new ProfileStateStore(configManager, bossIds);
		discoveryState = new DiscoveryState(profileStateStore);
		detectionEngine = new DetectionEngine(bosses, discoveryState);

		// The RS profile is not yet known this early (login screen); reload() here is a no-op
		// today and the real load happens on RuneScapeProfileChanged below. Kept for the case
		// where the plugin is toggled on mid-session, after the profile is already resolved.
		discoveryState.reload();

		// Toggling the plugin on mid-fight fires no spawn events for NPCs already on screen,
		// so presence has to be seeded from what's already there.
		clientThread.invokeLater(this::seedPresence);

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

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		handleNpcSpawn(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC npc = event.getNpc();
		for (Discovery discovery : detectionEngine.npcChanged(npc.getIndex(), npc.getId()))
		{
			announce(discovery);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		detectionEngine.npcDespawned(event.getNpc().getIndex());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		for (Discovery discovery : detectionEngine.animationPlayed(npc.getIndex(), npc.getAnimation()))
		{
			announce(discovery);
		}
		logIfUnmatched(TriggerType.ANIMATION, npc.getAnimation(), npc.getIndex());
	}

	// ProjectileMoved fires every game cycle a projectile is in flight; DetectionEngine's
	// idempotency makes repeat matches harmless, but this handler must stay cheap and quiet.
	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		Actor source = projectile.getSourceActor();
		Integer sourceIndex = (source instanceof NPC) ? ((NPC) source).getIndex() : null;

		for (Discovery discovery : detectionEngine.projectileFired(sourceIndex, projectile.getId()))
		{
			announce(discovery);
		}
		// A projectile with a non-NPC source is the player's own, and logging it as the
		// boss's sent Crumble Undead and Fire Surge into the curation log labelled
		// "from vorkath". Only a tracked NPC source, or genuinely no source, is worth logging.
		if (source == null || sourceIndex != null)
		{
			logUnmatchedNearBoss(TriggerType.PROJECTILE, projectile.getId(),
				sourceIndex == null ? null : detectionEngine.trackedBossId(sourceIndex));
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		int graphicId = event.getGraphicsObject().getId();
		for (Discovery discovery : detectionEngine.graphicCreated(graphicId))
		{
			announce(discovery);
		}
		logUnmatchedNearBoss(TriggerType.GRAPHIC, graphicId, null);
	}

	// The correct load trigger (docs/DECISIONS.md D18): fires whenever the profile key changes,
	// including at login once the display name resolves. GameStateChanged LOGGED_IN fires too
	// early, before that key is known. Replaces (never merges) so switching characters can't
	// leak one character's discoveries into another's.
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		discoveryState.reload();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			detectionEngine.clearPresence();
		}
	}

	/** Feeds already-on-screen NPCs into the engine, so toggling the plugin mid-fight still works. */
	private void seedPresence()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			handleNpcSpawn(npc);
		}
	}

	private void handleNpcSpawn(NPC npc)
	{
		for (Discovery discovery : detectionEngine.npcSpawned(npc.getIndex(), npc.getId()))
		{
			announce(discovery);
		}
	}

	private void announce(Discovery discovery)
	{
		if (!config.discoveryMessages())
		{
			return;
		}

		// Explicit red on the mechanic name. ChatColorType.HIGHLIGHT was tried first, but it
		// resolves to the player's configured highlight colour, which rendered the whole
		// line flat white in game and defeated the point of making it stand out.
		String message = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append(discovery.getBoss().getName() + " mechanic discovered: ")
			.append(MECHANIC_NAME_COLOR, discovery.getMechanic().getName())
			.append(ChatColorType.NORMAL)
			.append(".")
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());

		// Discoveries were previously chat-only, so nothing recorded them anywhere a log
		// could show. Fires at most once per mechanic per character, so it cannot spam.
		log.info("Discovered {} mechanic: {}", discovery.getBoss().getId(), discovery.getMechanic().getId());
	}

	/** Curation aid for events that name their source actor. */
	private void logIfUnmatched(TriggerType type, int triggerId, int npcIndex)
	{
		logTrigger(type, triggerId, detectionEngine.trackedBossId(npcIndex));
	}

	/**
	 * Same, for events that carry no reliable source actor. Falls back to "some boss is
	 * on screen", which is the same gate matching uses for them.
	 */
	private void logUnmatchedNearBoss(TriggerType type, int triggerId, String knownBossId)
	{
		logTrigger(type, triggerId, knownBossId != null ? knownBossId : detectionEngine.anyTrackedBossId());
	}

	/**
	 * Reports every distinct trigger id a tracked boss produces, claimed or not, deduped
	 * per session because these events fire every cycle.
	 *
	 * Logging only the unclaimed ids hid the case curation most needs to see: two different
	 * attacks sharing one id, which silently labels a mechanic as the wrong move. Naming the
	 * claimant makes that visible.
	 */
	private void logTrigger(TriggerType type, int triggerId, String bossId)
	{
		if (!config.logUnmatchedTriggers() || triggerId <= 0 || bossId == null)
		{
			return;
		}

		if (!loggedUnmatched.add(type + ":" + triggerId))
		{
			return;
		}

		String claimant = detectionEngine.claimedBy(type, triggerId);
		log.info("Trigger {} id {} from {} -> {}", type, triggerId, bossId,
			claimant == null ? "UNCLAIMED" : claimant);
	}
}
