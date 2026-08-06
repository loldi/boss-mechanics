package com.bossmechanics;

import com.bossmechanics.data.Boss;
import com.bossmechanics.data.BossDataLoader;
import com.bossmechanics.data.BossPageIndex;
import com.bossmechanics.data.LoadResult;
import com.bossmechanics.data.Mechanic;
import com.bossmechanics.data.Preview;
import com.bossmechanics.detection.DetectionEngine;
import com.bossmechanics.detection.Discovery;
import com.bossmechanics.data.TriggerType;
import com.bossmechanics.detection.DiscoveryState;
import com.bossmechanics.ui.BossMechanicsWindow;
import com.bossmechanics.ui.CollectionLogButton;
import com.bossmechanics.view.MechanicsView;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.SpritePixels;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

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
	private BossDataLoader bossDataLoader;

	@Inject
	private CollectionLogButton collectionLogButton;

	@Inject
	private BossMechanicsWindow bossMechanicsWindow;

	private List<Boss> bosses = Collections.emptyList();
	private DiscoveryState discoveryState;
	private DetectionEngine detectionEngine;

	// Ids already reported by the unmatched-trigger log, so a projectile in flight
	// doesn't reprint every cycle.
	private final Set<String> loggedUnmatched = new HashSet<>();

	// Same idea for collection log pages: the log redraws its page on every tab switch,
	// selection and search keystroke, so without this one page would print dozens of lines.
	private final Set<String> loggedUnmatchedPages = new HashSet<>();

	private static final Color MECHANIC_NAME_COLOR = new Color(0xCC0000);

	// A plugin field, not a local, so #5's reveal UI can reach isRevealed/setRevealed.
	private ProfileStateStore profileStateStore;

	private PerfRecorder perfRecorder;

	/**
	 * Cached from {@link BossMechanicsConfig#perfInstrumentation()} in {@link #startUp} and kept in
	 * sync by {@link #onConfigChanged} (issue #81). Read directly on every handler's fast path
	 * instead of {@code config.perfInstrumentation()}, which would route through {@code
	 * ConfigManager.getConfiguration} and concatenate a key string per call -- this field is the
	 * entire cost of the flag being off: one volatile read, one branch, straight into the unwrapped
	 * handler body. Volatile because {@link ConfigChanged} can arrive off the client thread (the
	 * config panel's own EDT); a {@code reset()} racing an in-flight {@code record()} on the client
	 * thread can tear one scenario's boundary, which is acceptable for a debug tool and not worth a
	 * lock on this path.
	 */
	private volatile boolean perfEnabled;

	/**
	 * Sprite previews (docs/DECISIONS.md D27): reserved base for the negative ids every bundled
	 * preview sprite registers under in {@code client.getSpriteOverrides()}, well clear of any
	 * real (non-negative) cache sprite id and of the resource-pack-style negative ids other
	 * plugins commonly use.
	 */
	private static final int SPRITE_BASE = -3_517_000;

	/** {@link BossMechanicsWindow#setSpriteIdForName}'s "nothing registered" sentinel. */
	private static final int UNKNOWN_SPRITE = -1;

	/** Every id this plugin registered, so {@link #unregisterSprites()} only ever removes its own. */
	private Map<String, Integer> spriteIdByName = Collections.emptyMap();

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
		detectionEngine = new DetectionEngine(bosses, discoveryState, client::getVarpValue);

		// Issue #81: the allocation supplier is null on a JVM that cannot answer
		// getThreadAllocatedBytes (see PerfRecorder.defaultAllocationSupplier), in which case the
		// recorder itself degrades to timing-only rather than the plugin needing to know.
		perfRecorder = new PerfRecorder(System::nanoTime, PerfRecorder.defaultAllocationSupplier());
		perfEnabled = config.perfInstrumentation();
		perfRecorder.setEnabled(perfEnabled);

		// The RS profile is not yet known this early (login screen); reload() here is a no-op
		// today and the real load happens on RuneScapeProfileChanged below. Kept for the case
		// where the plugin is toggled on mid-session, after the profile is already resolved.
		discoveryState.reload();

		// Toggling the plugin on mid-fight fires no spawn events for NPCs already on screen,
		// so presence has to be seeded from what's already there.
		clientThread.invokeLater(this::seedPresence);

		collectionLogButton.setBossIndex(new BossPageIndex(bosses));
		collectionLogButton.setOnOpen(this::openBossMechanics);
		collectionLogButton.setOnUnmatchedPage(this::logUnmatchedPage);
		eventBus.register(collectionLogButton);
		collectionLogButton.onPluginStart();

		// The override map and the widget sprite cache are read by the render loop, so both the
		// register here and the unregister in shutDown() hop to the client thread rather than
		// mutating them from the EDT that runs these two methods.
		clientThread.invokeLater(this::registerSprites);

		bossMechanicsWindow.setOnRevealToggled(this::setRevealed);
		bossMechanicsWindow.setOnWikiOpened(this::openWiki);
		bossMechanicsWindow.setOnMechanicSelected(mechanicId -> log.debug("Mechanic selected: {}", mechanicId));
		bossMechanicsWindow.setSpriteIdForName(this::spriteIdForName);
		bossMechanicsWindow.setPerfRecorder(perfRecorder);
		eventBus.register(bossMechanicsWindow);
		bossMechanicsWindow.onPluginStart();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Boss Mechanics stopped");

		collectionLogButton.onPluginStop();
		eventBus.unregister(collectionLogButton);

		bossMechanicsWindow.onPluginStop();
		eventBus.unregister(bossMechanicsWindow);

		clientThread.invokeLater(this::unregisterSprites);
	}

	/**
	 * Registers every distinct {@code preview.sprite} name found in the loaded boss data (docs/
	 * DECISIONS.md D27) -- data-driven, never classpath scanning (D16) -- as a custom sprite under
	 * a negative id in {@code client.getSpriteOverrides()}. {@code ImageUtil} and the override map
	 * stay here, in the plugin, so {@code com.bossmechanics.ui} never imports either.
	 */
	private void registerSprites()
	{
		Set<String> spriteNames = new LinkedHashSet<>();
		for (Boss boss : bosses)
		{
			for (Mechanic mechanic : boss.getMechanics())
			{
				Preview preview = mechanic.getPreview();
				if (preview != null && preview.getSprite() != null)
				{
					spriteNames.add(preview.getSprite());
				}
			}
		}

		Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		Map<String, Integer> idByName = new HashMap<>();
		int nextId = SPRITE_BASE;
		for (String name : spriteNames)
		{
			BufferedImage image = ImageUtil.loadImageResource(BossMechanicsPlugin.class, "/sprites/" + name);
			if (image == null)
			{
				log.warn("Boss Mechanics: bundled sprite resource not found: {}", name);
				continue;
			}

			int spriteId = nextId--;
			overrides.put(spriteId, ImageUtil.getImageSpritePixels(image, client));
			idByName.put(name, spriteId);
		}

		spriteIdByName = idByName;
		log.info("Boss Mechanics: registered {} sprite preview(s)", spriteIdByName.size());
	}

	/** Undoes {@link #registerSprites()}: removes only the ids this plugin added, by identity of name. */
	private void unregisterSprites()
	{
		Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		for (int spriteId : spriteIdByName.values())
		{
			overrides.remove(spriteId);
		}
		client.getWidgetSpriteCache().reset();
		spriteIdByName = Collections.emptyMap();
	}

	private int spriteIdForName(String name)
	{
		return spriteIdByName.getOrDefault(name, UNKNOWN_SPRITE);
	}

	@Provides
	BossMechanicsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossMechanicsConfig.class);
	}

	/**
	 * Issue #81: every live-gameplay handler below wraps its unchanged body in the same shape --
	 * skip straight to {@code handleX} when {@link #perfEnabled} is false (the only cost: one
	 * volatile read, one branch), otherwise bracket it with {@link PerfRecorder#allocStart()}/
	 * {@link PerfRecorder#timeStart()} before and {@link PerfRecorder#record} after. The body has to
	 * live in a separate method rather than inline: {@code onAnimationChanged} and
	 * {@code onProjectileMoved} both have early returns that would otherwise skip the end-of-call
	 * {@code record}.
	 */
	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (!perfEnabled)
		{
			handleNpcSpawned(event);
			return;
		}
		long a0 = perfRecorder.allocStart();
		long t0 = perfRecorder.timeStart();
		handleNpcSpawned(event);
		perfRecorder.record(PerfRecorder.Handler.NPC_SPAWNED, t0, a0);
	}

	private void handleNpcSpawned(NpcSpawned event)
	{
		handleNpcSpawn(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (!perfEnabled)
		{
			handleNpcChanged(event);
			return;
		}
		long a0 = perfRecorder.allocStart();
		long t0 = perfRecorder.timeStart();
		handleNpcChanged(event);
		perfRecorder.record(PerfRecorder.Handler.NPC_CHANGED, t0, a0);
	}

	private void handleNpcChanged(NpcChanged event)
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
		if (!perfEnabled)
		{
			handleNpcDespawned(event);
			return;
		}
		long a0 = perfRecorder.allocStart();
		long t0 = perfRecorder.timeStart();
		handleNpcDespawned(event);
		perfRecorder.record(PerfRecorder.Handler.NPC_DESPAWNED, t0, a0);
	}

	private void handleNpcDespawned(NpcDespawned event)
	{
		detectionEngine.npcDespawned(event.getNpc().getIndex());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!perfEnabled)
		{
			handleAnimationChanged(event);
			return;
		}
		long a0 = perfRecorder.allocStart();
		long t0 = perfRecorder.timeStart();
		handleAnimationChanged(event);
		perfRecorder.record(PerfRecorder.Handler.ANIMATION_CHANGED, t0, a0);
	}

	private void handleAnimationChanged(AnimationChanged event)
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
		if (!perfEnabled)
		{
			handleProjectileMoved(event);
			return;
		}
		long a0 = perfRecorder.allocStart();
		long t0 = perfRecorder.timeStart();
		handleProjectileMoved(event);
		perfRecorder.record(PerfRecorder.Handler.PROJECTILE_MOVED, t0, a0);
	}

	private void handleProjectileMoved(ProjectileMoved event)
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
		if (!perfEnabled)
		{
			handleGraphicsObjectCreated(event);
			return;
		}
		long a0 = perfRecorder.allocStart();
		long t0 = perfRecorder.timeStart();
		handleGraphicsObjectCreated(event);
		perfRecorder.record(PerfRecorder.Handler.GRAPHICS_OBJECT_CREATED, t0, a0);
	}

	private void handleGraphicsObjectCreated(GraphicsObjectCreated event)
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

	/**
	 * The config panel has no button widget in this client version, so "Clear discoveries" and
	 * "Dump perf stats" (issue #81) are both checkboxes that perform an action and immediately
	 * untick themselves; setting either back to "false" re-enters here and falls through harmlessly.
	 * "Measure handler cost" is a plain toggle instead -- both directions matter, so it keeps
	 * {@link #perfEnabled} (and {@link #perfRecorder}'s own enabled state) synced to whatever it is
	 * currently set to, not just reacting to "true".
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BossMechanicsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		switch (event.getKey())
		{
			case "clearDiscoveries":
				if ("true".equals(event.getNewValue()))
				{
					clearDiscoveries();
				}
				break;
			case "perfInstrumentation":
				perfEnabled = "true".equals(event.getNewValue());
				perfRecorder.setEnabled(perfEnabled);
				break;
			case "dumpPerfStats":
				if ("true".equals(event.getNewValue()))
				{
					dumpPerfStats();
				}
				break;
			default:
				break;
		}
	}

	private void clearDiscoveries()
	{
		int forgotten = 0;
		for (Boss boss : bosses)
		{
			forgotten += discoveryState.clearDiscovered(boss.getId());
		}

		configManager.setConfiguration(BossMechanicsConfig.GROUP, "clearDiscoveries", false);
		log.info("Cleared {} discovered mechanic(s)", forgotten);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append("Boss Mechanics: forgot ")
				.append(MECHANIC_NAME_COLOR, String.valueOf(forgotten))
				.append(ChatColorType.NORMAL)
				.append(" discovered mechanic(s).")
				.build())
			.build());
	}

	/**
	 * "Dump perf stats" (issue #81): logs the table accumulated since instrumentation was last
	 * enabled or last dumped, resets the counters so the next scenario is measured independently,
	 * and unticks the checkbox that triggered it -- the same self-unticking shape
	 * {@link #clearDiscoveries()} already uses.
	 */
	private void dumpPerfStats()
	{
		log.info("Boss Mechanics perf dump:\n{}", perfRecorder.dump());
		perfRecorder.reset();
		configManager.setConfiguration(BossMechanicsConfig.GROUP, "dumpPerfStats", false);
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

	/**
	 * What the injected collection log button does: resolve this boss's rows and open the window
	 * (issue #5). This is the swap {@link CollectionLogButton#setOnOpen} was designed for, so it
	 * is the only thing that changed there.
	 *
	 * Runs on the client thread, via the widget's op listener.
	 */
	private void openBossMechanics(Boss boss)
	{
		log.debug("Boss Mechanics button clicked: {}", boss.getId());
		bossMechanicsWindow.open(boss, viewFor(boss));
	}

	/**
	 * "View All" / "Hide All". Persists per boss per character (D18, D20) and rebuilds, so the
	 * reveal survives closing the window, logging out and switching characters.
	 *
	 * Runs on the client thread, via the widget's op listener, which is the condition D18 named
	 * as necessary for the store's read-modify-write to be safe.
	 */
	private void setRevealed(String bossId, boolean revealed)
	{
		profileStateStore.setRevealed(bossId, revealed);

		Boss boss = bossById(bossId);
		if (boss != null)
		{
			bossMechanicsWindow.open(boss, viewFor(boss));
		}
	}

	/**
	 * The window's WIKI button. The URL open lives here rather than in the window because
	 * {@code com.bossmechanics.ui} is meant to be RuneLite *interface* code only, and
	 * {@code LinkBrowser} is a desktop-integration utility.
	 */
	private void openWiki(String bossId)
	{
		Boss boss = bossById(bossId);
		if (boss != null)
		{
			LinkBrowser.browse(boss.getWikiUrl());
		}
	}

	private MechanicsView viewFor(Boss boss)
	{
		return MechanicsView.of(boss, discoveryState, profileStateStore.isRevealed(boss.getId()));
	}

	private Boss bossById(String bossId)
	{
		for (Boss boss : bosses)
		{
			if (boss.getId().equals(bossId))
			{
				return boss;
			}
		}
		return null;
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

	/**
	 * Curation aid for the trap that hid "The Mad Angel": a boss whose {@code name} does not
	 * match its collection log page title gets no button and no error anywhere, so the only
	 * signal is a human opening the page and noticing nothing appeared.
	 *
	 * Prints the normalized title, which is the exact string the data has to fold to, so the
	 * fix is copy-paste rather than guesswork. Every non-boss page reports too, on purpose:
	 * whether a miss is a curation bug or just the Raids tab is a judgement only the reader
	 * can make, and pre-filtering it here would need the very list of page titles we don't have.
	 */
	private void logUnmatchedPage(String pageTitle)
	{
		if (!config.logUnmatchedTriggers())
		{
			return;
		}

		String key = BossPageIndex.pageTitleKey(pageTitle);
		if (key == null || !loggedUnmatchedPages.add(key))
		{
			return;
		}

		log.info("Collection log page has no boss data: \"{}\"", key);
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
