/***********************************************************************************
 * Copyright (c) 2024 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.bots

import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.services.gameplay.bots.model.BotMemory
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles tell routing, dialogue generation, player memory tracking, and rate limiting.
 */
class BotDialogueService : Service() {

	private val repository get() = BotServiceHub.repository
	private val playerMemories = ConcurrentHashMap<String, BotMemory>() // Key: "${botId}_${playerId}"
	
	// Rate limiting: track last tell time per player-bot pair
	private val lastTellTime = ConcurrentHashMap<String, Long>() // Key: "${botId}_${playerId}"
	private val tellRateLimitSeconds = 5 // Allow 1 tell per 5 seconds per pair
	
	// Telemetry
	private val tellsHandled = AtomicLong(0)

	override fun start(): Boolean {
		BotServiceHub.dialogueService = this
		Log.i("BotDialogueService started with rate limit: %d seconds", tellRateLimitSeconds)
		return true
	}

	override fun stop(): Boolean {
		if (BotServiceHub.dialogueService === this) {
			BotServiceHub.dialogueService = null
		}
		// Save all memories back to persistence
		playerMemories.values.forEach { memory ->
			repository.saveBotMemory(memory)
		}
		playerMemories.clear()
		lastTellTime.clear()
		return super.stop()
	}

	fun tryHandleTell(sender: Player, botId: String, botName: String, message: String): Boolean {
		if (message.isBlank() || botId.isEmpty() || botName.isEmpty()) {
			return false
		}

		val playerId = sender.creatureObject.objectId
		val rateKey = "${botId}_${playerId}"

		// Check rate limiting
		if (isRateLimited(rateKey)) {
			Log.d("Tell from player %d to bot %s rate-limited", playerId, botId)
			return false
		}

		// Load or create memory
		var memory = playerMemories[rateKey] ?: loadOrCreateMemory(botId, playerId, sender.characterName)
		
		// Detect intent from message
		val intent = detectIntent(message)
		
		// Build response
		val response = buildResponse(botName, message, intent, memory)
		if (response == null) {
			return false
		}

		// Record the interaction
		memory.recordConversation(message, response, intent)
		playerMemories[rateKey] = memory
		repository.saveBotMemory(memory)
		
		// Update rate limit timestamp
		lastTellTime[rateKey] = System.currentTimeMillis()

		// Send response (placeholder until instant-message system is available)
		SystemMessageIntent.broadcastPersonal(sender, response)
		tellsHandled.incrementAndGet()
		BotServiceHub.telemetryService?.onTellHandled()
		
		return true
	}

	/**
	 * Load memory from persistence or create new.
	 */
	private fun loadOrCreateMemory(botId: String, playerId: Long, playerName: String): BotMemory {
		val key = "${botId}_${playerId}"
		val existing = repository.loadBotMemory(botId, playerId)
		if (existing != null) {
			playerMemories[key] = existing
			return existing
		}

		val newMemory = BotMemory(
			botId = botId,
			playerId = playerId,
			playerName = playerName,
			affinityScore = 0,
			interactionCount = 0,
			expiresAt = Instant.now().plusSeconds(86400 * 30) // 30 days TTL
		)
		playerMemories[key] = newMemory
		return newMemory
	}

	/**
	 * Check if a player-bot tell pair is rate-limited.
	 */
	private fun isRateLimited(key: String): Boolean {
		val lastTime = lastTellTime[key] ?: return false
		val elapsedSeconds = (System.currentTimeMillis() - lastTime) / 1000
		return elapsedSeconds < tellRateLimitSeconds
	}

	/**
	 * Detect intent from message for contextual responses.
	 */
	private fun detectIntent(message: String): String {
		return when {
			Regex("hello|hi|greet", RegexOption.IGNORE_CASE).containsMatchIn(message) -> "greeting"
			Regex("mission|quest|help", RegexOption.IGNORE_CASE).containsMatchIn(message) -> "mission_help"
			Regex("craft|make|build", RegexOption.IGNORE_CASE).containsMatchIn(message) -> "crafting"
			Regex("thank|thanks|appreciate", RegexOption.IGNORE_CASE).containsMatchIn(message) -> "gratitude"
			Regex("sorry|excuse|apolog", RegexOption.IGNORE_CASE).containsMatchIn(message) -> "apology"
			else -> "general"
		}
	}

	/**
	 * Build a response based on templates, affinity, and conversation history.
	 */
	private fun buildResponse(botName: String, playerMessage: String, intent: String, memory: BotMemory): String? {
		val affinity = memory.affinityScore
		val prefix = when {
			affinity > 50 -> "$botName says happily:"
			affinity > 20 -> "$botName says:"
			affinity < -50 -> "$botName says coldly:"
			affinity < -20 -> "$botName says curtly:"
			else -> "$botName says:"
		}

		val responseText = when (intent) {
			"greeting" -> selectResponse(
				affinity,
				listOf(
					"Good to see you!",
					"Well met, friend.",
					"Hello there.",
					"Greetings.",
					"It's nice to see a familiar face."
				)
			)
			"mission_help" -> selectResponse(
				affinity,
				listOf(
					"I'd be happy to help with a mission. What do you need?",
					"Missions, eh? I can assist.",
					"Let's see what contracts are available.",
					"I'm available if you need an extra hand.",
					"Missions are my specialty."
				)
			)
			"crafting" -> selectResponse(
				affinity,
				listOf(
					"Crafting is a valuable skill.",
					"I can help with crafting needs.",
					"My crafting services are available.",
					"I'm not much of a crafter myself.",
					"Crafting work takes time and patience."
				)
			)
			"gratitude" -> {
				memory.updateAffinity(10) // Boost affinity for thanks
				selectResponse(
					affinity,
					listOf(
						"You're very welcome!",
						"Happy to help.",
						"It's what I'm here for.",
						"Anytime.",
						"You're welcome."
					)
				)
			}
			"apology" -> {
				memory.updateAffinity(5) // Small boost for apology
				selectResponse(
					affinity,
					listOf(
						"No harm done.",
						"Don't worry about it.",
						"We all make mistakes.",
						"Water under the bridge.",
						"It's forgotten."
					)
				)
			}
			else -> selectResponse(
				affinity,
				listOf(
					"Understood.",
					"I hear you.",
					"Interesting.",
					"Tell me more.",
					"That's noted."
				)
			)
		}

		return "$prefix $responseText"
	}

	/**
	 * Select a response from a list based on affinity or randomly.
	 */
	private fun selectResponse(affinity: Int, options: List<String>): String {
		if (options.isEmpty()) return "..."
		
		val index = when {
			affinity > 50 && options.size > 2 -> options.size - 1 // Pick most enthusiastic
			affinity < -50 && options.size > 1 -> 0 // Pick most curt
			else -> (Math.random() * options.size).toInt()
		}
		return options[index]
	}

	/**
	 * Get all memories for a bot (admin query).
	 */
	fun getBotMemories(botId: String): List<BotMemory> {
		return playerMemories.filter { it.key.startsWith("${botId}_") }.map { it.value }
	}

	/**
	 * Clear memory for a specific player-bot pair (admin command).
	 */
	fun clearPlayerMemory(botId: String, playerId: Long): Boolean {
		val key = "${botId}_${playerId}"
		playerMemories.remove(key)
		repository.deleteBotMemory(botId, playerId)
		return true
	}

	/**
	 * Get dialogue statistics.
	 */
	fun getStatistics(): DialogueStatistics {
		return DialogueStatistics(
			totalMemoryEntries = playerMemories.size,
			tellsHandledThisSession = tellsHandled.get(),
			rateLimitSeconds = tellRateLimitSeconds,
		)
	}
}

data class DialogueStatistics(
	val totalMemoryEntries: Int,
	val tellsHandledThisSession: Long,
	val rateLimitSeconds: Int,
)
