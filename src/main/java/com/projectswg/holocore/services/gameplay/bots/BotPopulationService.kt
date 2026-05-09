package com.projectswg.holocore.services.gameplay.bots

import com.projectswg.holocore.services.gameplay.bots.model.BotProfile
import com.projectswg.holocore.services.gameplay.bots.model.BotSeedData
import com.projectswg.holocore.services.gameplay.bots.model.BotSimulationTier
import com.projectswg.holocore.services.gameplay.bots.model.BotState
import com.projectswg.holocore.services.gameplay.bots.persistence.BotRepository
import com.projectswg.holocore.services.gameplay.bots.persistence.InMemoryBotRepository
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages bot population: loading/saving profiles, tracking simulation tiers,
 * enforcing active caps, and promoting/demoting bots based on player proximity.
 */
class BotPopulationService : Service() {

	companion object {
		@Volatile
		var instance: BotPopulationService? = null
			private set
	}

	private val repository: BotRepository = InMemoryBotRepository()
	private val profiles = ConcurrentHashMap<String, BotProfile>()
	private val states = ConcurrentHashMap<String, BotState>()
	
	// Track active bot counts per zone
	private val activeBotCountPerZone = ConcurrentHashMap<String, AtomicInteger>()
	
	// Per-zone caps for active simulation (Phase 1: 10 active bots per zone)
	private val zoneActiveCaps = ConcurrentHashMap<String, Int>()
	
	// Track spawned world objects (botId -> worldObjectId)
	private val spawnedWorldObjects = ConcurrentHashMap<String, Long>()

	override fun start(): Boolean {
		instance = this
		// Set up default zone caps (can be overridden per zone)
		zoneActiveCaps["tatooine"] = 10
		zoneActiveCaps.putIfAbsent("default", 5)
		
		// Load all bot profiles from persistence
		val profiles = repository.getAllBotProfiles()
		profiles.forEach { profile ->
			registerProfile(profile, BotSimulationTier.DIRECTORY)
		}
		
		// If no bots loaded, initialize seed data for testing
		if (profiles.isEmpty()) {
			Log.i("No persisted bots found. Loading Phase 1 seed data (50 test bots)...")
			initializePhase1SeedBots()
		}
		
		Log.i("BotPopulationService started with %d bot profiles loaded", this.profiles.size)
		return true
	}

	override fun stop(): Boolean {
		if (instance === this) {
			instance = null
		}
		// Save all state back to persistence
		states.values.forEach { state ->
			repository.saveBotState(state)
		}
		profiles.clear()
		states.clear()
		spawnedWorldObjects.clear()
		return super.stop()
	}

	fun registerProfile(profile: BotProfile, initialTier: BotSimulationTier = BotSimulationTier.DIRECTORY) {
		profiles[profile.botId] = profile
		repository.saveBotProfile(profile)
		
		states.computeIfAbsent(profile.botId) {
			BotState(
				botId = profile.botId,
				tier = initialTier,
				planet = profile.homePlanet,
			)
		}
	}

	fun getProfile(botId: String): BotProfile? = profiles[botId]

	fun getState(botId: String): BotState? = states[botId]

	fun setTier(botId: String, tier: BotSimulationTier): Boolean {
		val state = states[botId] ?: return false
		state.tier = tier
		repository.saveBotState(state)
		return true
	}

	/**
	 * Promote a bot from DIRECTORY to LOCAL tier (spawn as world object).
	 * Respects per-zone active caps.
	 */
	fun promoteToLocal(botId: String, zoneName: String): Boolean {
		val profile = profiles[botId] ?: return false
		val state = states[botId] ?: return false
		
		if (state.tier != BotSimulationTier.DIRECTORY && state.tier != BotSimulationTier.REGIONAL) {
			return false // Already promoted or in companion tier
		}

		// Check zone capacity
		val activeCap = zoneActiveCaps.getOrDefault(zoneName, zoneActiveCaps["default"]!!)
		val activeCount = activeBotCountPerZone.getOrPut(zoneName) { AtomicInteger(0) }
		
		if (activeCount.get() >= activeCap) {
			Log.w("Cannot promote bot %s to %s: zone at capacity (%d/%d)", 
				botId, zoneName, activeCount.get(), activeCap)
			return false
		}

		// Promote the bot
		state.tier = BotSimulationTier.LOCAL
		state.planet = zoneName // Update current zone
		activeCount.incrementAndGet()
		repository.saveBotState(state)
		
		Log.d("Bot %s promoted to LOCAL in zone %s (%d/%d active)", 
			botId, zoneName, activeCount.get(), activeCap)
		return true
	}

	/**
	 * Demote a bot from LOCAL back to DIRECTORY tier (despawn from world).
	 */
	fun demoteToBackground(botId: String): Boolean {
		val profile = profiles[botId] ?: return false
		val state = states[botId] ?: return false
		
		if (state.tier != BotSimulationTier.LOCAL && state.tier != BotSimulationTier.COMPANION) {
			return false // Not in an active tier
		}

		val zoneName = state.planet
		val activeCount = activeBotCountPerZone[zoneName]
		if (activeCount != null && activeCount.get() > 0) {
			activeCount.decrementAndGet()
		}

		// Despawn world object if tracked
		spawnedWorldObjects.remove(botId)

		// Demote the bot
		state.tier = BotSimulationTier.DIRECTORY
		repository.saveBotState(state)
		
		Log.d("Bot %s demoted to DIRECTORY (was in %s)", botId, zoneName)
		return true
	}

	/**
	 * Track a spawned world object for a bot.
	 */
	fun trackWorldObject(botId: String, worldObjectId: Long) {
		spawnedWorldObjects[botId] = worldObjectId
	}

	/**
	 * Get the world object ID if this bot is spawned.
	 */
	fun getWorldObject(botId: String): Long? = spawnedWorldObjects[botId]

	/**
	 * Get all bots in a particular zone at a given tier.
	 */
	fun getBotsInZoneByTier(zone: String, tier: BotSimulationTier): List<BotState> {
		return states.values.filter { it.planet == zone && it.tier == tier }
	}

	/**
	 * Get active bot count for a zone.
	 */
	fun getActiveCount(zone: String): Int = activeBotCountPerZone.getOrDefault(zone, AtomicInteger(0)).get()

	/**
	 * Get active bot cap for a zone.
	 */
	fun getActiveCap(zone: String): Int = zoneActiveCaps.getOrDefault(zone, zoneActiveCaps["default"]!!)

	/**
	 * Get remaining capacity in a zone.
	 */
	fun getRemainingCapacity(zone: String): Int {
		val cap = getActiveCap(zone)
		val active = getActiveCount(zone)
		return maxOf(0, cap - active)
	}

	/**
	 * Set the active bot cap for a zone (admin command).
	 */
	fun setZoneActiveCap(zone: String, cap: Int) {
		zoneActiveCaps[zone] = cap
	}

	/**
	 * Get all spawned world object IDs.
	 */
	fun getAllSpawnedWorldObjects(): Map<String, Long> = spawnedWorldObjects.toMap()

	/**
	 * Initialize 50 seed bots for Phase 1 testing (Tatooine, Mos Eisley).
	 */
	private fun initializePhase1SeedBots() {
		val seedBots = BotSeedData.generatePhase1Bots(50, "tatooine")
		seedBots.forEach { profile ->
			registerProfile(profile, BotSimulationTier.DIRECTORY)
		}
		Log.i("Initialized %d Phase 1 seed bots", seedBots.size)
	}

	/**
	 * Get population statistics.
	 */
	fun getStatistics(): PopulationStatistics {
		val totalByTier = BotSimulationTier.values().associateWith { tier ->
			states.values.count { it.tier == tier }
		}
		val totalByZone = states.values.groupingBy { it.planet }.eachCount()
		
		return PopulationStatistics(
			totalBots = profiles.size,
			totalByTier = totalByTier,
			totalByZone = totalByZone,
			totalSpawned = spawnedWorldObjects.size,
		)
	}
}

data class PopulationStatistics(
	val totalBots: Int,
	val totalByTier: Map<BotSimulationTier, Int>,
	val totalByZone: Map<String, Int>,
	val totalSpawned: Int,
)
