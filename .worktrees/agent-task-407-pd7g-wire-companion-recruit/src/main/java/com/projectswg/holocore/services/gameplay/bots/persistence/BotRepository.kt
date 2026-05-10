package com.projectswg.holocore.services.gameplay.bots.persistence

import com.projectswg.holocore.services.gameplay.bots.model.BotProfile
import com.projectswg.holocore.services.gameplay.bots.model.BotState
import com.projectswg.holocore.services.gameplay.bots.model.BotMemory

/**
 * Repository interface for bot persistence.
 * Abstracts away database implementation details.
 */
interface BotRepository {
	
	// Profile operations
	fun saveBotProfile(profile: BotProfile): Boolean
	fun loadBotProfile(botId: String): BotProfile?
	fun getAllBotProfiles(): List<BotProfile>
	fun deleteBotProfile(botId: String): Boolean
	
	// State operations
	fun saveBotState(state: BotState): Boolean
	fun loadBotState(botId: String): BotState?
	fun updateBotState(botId: String, state: BotState): Boolean
	fun getAllBotStates(): List<BotState>
	
	// Memory operations
	fun saveBotMemory(memory: BotMemory): Boolean
	fun loadBotMemory(botId: String, playerId: Long): BotMemory?
	fun getAllBotMemoryForBot(botId: String): List<BotMemory>
	fun getAllBotMemoryForPlayer(playerId: Long): List<BotMemory>
	fun deleteBotMemory(botId: String, playerId: Long): Boolean
	
	// Batch operations
	fun saveBotProfiles(profileList: List<BotProfile>): Boolean
	fun saveBotStates(stateList: List<BotState>): Boolean
	
	// Query operations
	fun getBotsByPlanet(planet: String): List<BotProfile>
	fun getBotsByProfession(profession: String): List<BotProfile>
	fun getExpiredMemories(beforeEpochSeconds: Long): List<BotMemory>
	
	// Administrative
	fun clearAllBots(): Int
	fun clearAllBotMemory(): Int
}
