package com.projectswg.holocore.services.gameplay.bots

import com.projectswg.holocore.resources.support.global.player.Player
import me.joshlarson.jlcommon.control.Service
import java.util.concurrent.ConcurrentHashMap

class BotCompanionService : Service() {

	companion object {
		@Volatile
		var instance: BotCompanionService? = null
			private set
	}

	override fun start(): Boolean {
		instance = this
		return super.start()
	}

	override fun stop(): Boolean {
		if (instance === this) {
			instance = null
		}
		return super.stop()
	}

	private val companionOwnerByBot = ConcurrentHashMap<String, Long>()

	fun recruitCompanion(player: Player, botId: String): Boolean {
		val ownerId = player.creatureObject.objectId
		val previous = companionOwnerByBot.putIfAbsent(botId, ownerId)
		return previous == null
	}

	fun releaseCompanion(player: Player, botId: String): Boolean {
		val ownerId = player.creatureObject.objectId
		return companionOwnerByBot.remove(botId, ownerId)
	}

	fun isCompanionAssigned(botId: String): Boolean = companionOwnerByBot.containsKey(botId)
}
