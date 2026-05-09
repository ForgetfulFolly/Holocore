package com.projectswg.holocore.services.gameplay.bots.persistence

import com.projectswg.holocore.services.gameplay.bots.model.BotProfile
import com.projectswg.holocore.services.gameplay.bots.model.BotState
import com.projectswg.holocore.services.gameplay.bots.model.BotMemory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of BotRepository for Phase 1 testing.
 * This can be replaced with a MongoDB implementation later.
 */
class InMemoryBotRepository : BotRepository {

	private val profiles = ConcurrentHashMap<String, BotProfile>()
	private val states = ConcurrentHashMap<String, BotState>()
	private val memory = ConcurrentHashMap<String, BotMemory>() // Key: "${botId}_${playerId}"

	override fun saveBotProfile(profile: BotProfile): Boolean {
		profiles[profile.botId] = profile
		return true
	}

	override fun loadBotProfile(botId: String): BotProfile? = profiles[botId]

	override fun getAllBotProfiles(): List<BotProfile> = profiles.values.toList()

	override fun deleteBotProfile(botId: String): Boolean {
		profiles.remove(botId)
		states.remove(botId)
		// Also remove all memories for this bot
		memory.keys.filter { it.startsWith("${botId}_") }.forEach { memory.remove(it) }
		return true
	}

	override fun saveBotState(state: BotState): Boolean {
		states[state.botId] = state
		return true
	}

	override fun loadBotState(botId: String): BotState? = states[botId]

	override fun updateBotState(botId: String, state: BotState): Boolean {
		return saveBotState(state)
	}

	override fun saveBotMemory(memory: BotMemory): Boolean {
		val key = "${memory.botId}_${memory.playerId}"
		this.memory[key] = memory
		return true
	}

	override fun loadBotMemory(botId: String, playerId: Long): BotMemory? {
		val key = "${botId}_${playerId}"
		return memory[key]
	}

	override fun getAllBotMemoryForBot(botId: String): List<BotMemory> {
		return memory.filter { it.key.startsWith("${botId}_") }.map { it.value }
	}

	override fun getAllBotMemoryForPlayer(playerId: Long): List<BotMemory> {
		return memory.filter { it.value.playerId == playerId }.map { it.value }
	}

	override fun deleteBotMemory(botId: String, playerId: Long): Boolean {
		val key = "${botId}_${playerId}"
		return memory.remove(key) != null
	}

	override fun saveBotProfiles(profiles: List<BotProfile>): Boolean {
		profiles.forEach { profile ->
			this.profiles[profile.botId] = profile
		}
		return true
	}

	override fun saveBotStates(states: List<BotState>): Boolean {
		states.forEach { state ->
			this.states[state.botId] = state
		}
		return true
	}

	override fun getBotsByPlanet(planet: String): List<BotProfile> {
		return profiles.values.filter { it.homePlanet == planet }
	}

	override fun getBotsByProfession(profession: String): List<BotProfile> {
		return profiles.values.filter { it.profession == profession }
	}

	override fun getExpiredMemories(beforeEpochSeconds: Long): List<BotMemory> {
		val before = Instant.ofEpochSecond(beforeEpochSeconds)
		return memory.values.filter { m ->
			m.expiresAt != null && m.expiresAt!!.isBefore(before)
		}
	}

	override fun clearAllBots(): Int {
		val count = profiles.size
		profiles.clear()
		states.clear()
		return count
	}

	override fun clearAllBotMemory(): Int {
		val count = memory.size
		memory.clear()
		return count
	}

	/**
	 * Get statistics for debugging.
	 */
	fun getStatistics(): RepositoryStatistics {
		return RepositoryStatistics(
			profileCount = profiles.size,
			stateCount = states.size,
			memoryEntryCount = memory.size,
			totalMemoryBytes = memory.values.sumOf { estimateMemorySize(it) }
		)
	}

	private fun estimateMemorySize(memory: BotMemory): Long {
		// Rough estimate: 100 bytes base + conversation history
		var size = 100L
		size += memory.conversationHistory.size * 200 // ~200 bytes per conversation entry
		size += memory.notes.length
		size += memory.tags.sumOf { it.length }
		return size
	}
}

data class RepositoryStatistics(
	val profileCount: Int,
	val stateCount: Int,
	val memoryEntryCount: Int,
	val totalMemoryBytes: Long,
)
