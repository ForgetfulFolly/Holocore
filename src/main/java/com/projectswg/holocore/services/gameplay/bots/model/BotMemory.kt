package com.projectswg.holocore.services.gameplay.bots.model

import java.time.Instant

/**
 * Represents a bot's memory of interactions with a specific player.
 * Tracks affinity, conversation history, and behavioral context.
 */
class BotMemory(
	val botId: String,
	val playerId: Long,
	val playerName: String,
	var affinityScore: Int = 0, // -100 to +100
	var lastInteractionTime: Instant = Instant.now(),
	var interactionCount: Int = 0,
	val conversationHistory: MutableList<ConversationEntry> = mutableListOf(),
	var notes: String = "",
	val tags: MutableList<String> = mutableListOf(),
	var expiresAt: Instant? = null, // TTL for auto-cleanup if unused
) {
	/**
	 * Add a conversation entry to the memory buffer.
	 * Keeps only the last [maxEntries] conversations to limit memory usage.
	 */
	fun recordConversation(playerMessage: String, botReply: String, intent: String = "general") {
		val entry = ConversationEntry(
			timestamp = Instant.now(),
			playerMessage = playerMessage,
			botReply = botReply,
			intent = intent
		)
		conversationHistory.add(entry)
		interactionCount++
		lastInteractionTime = Instant.now()

		// Keep only last 10 entries
		if (conversationHistory.size > 10) {
			conversationHistory.removeAt(0)
		}
	}

	/**
	 * Update affinity based on player interaction quality.
	 * Clamped to [-100, +100].
	 */
	fun updateAffinity(delta: Int) {
		affinityScore = (affinityScore + delta).coerceIn(-100, 100)
	}

	/**
	 * Get a summary of the conversation history as a single string.
	 * Useful for context when deciding replies.
	 */
	fun getConversationSummary(): String {
		if (conversationHistory.isEmpty()) {
			return "No prior conversation history."
		}
		return conversationHistory.takeLast(3).joinToString("\n") {
			"[${it.intent}] Player: ${it.playerMessage}\nBot: ${it.botReply}"
		}
	}

	/**
	 * Check if enough time has passed to reset rate-limiting.
	 */
	fun hasPassedRateLimit(secondsSince: Long): Boolean {
		return Instant.now().epochSecond - lastInteractionTime.epochSecond > secondsSince
	}
}

/**
 * A single conversation exchange between a player and bot.
 *
 * Remains a [data class] intentionally — all fields are [val], giving stable
 * equals/hashCode semantics appropriate for this immutable value type.
 * Contrast with [BotMemory] which is a plain class because its mutable list
 * field would make data-class equality incorrect.
 */
data class ConversationEntry(
	val timestamp: Instant,
	val playerMessage: String,
	val botReply: String,
	val intent: String = "general" // e.g., "greeting", "mission_help", "complaint", "gratitude"
)
