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
package com.projectswg.holocore.services.support.global.admin

import com.projectswg.common.data.CRC
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent
import com.projectswg.holocore.intents.support.global.command.ExecuteCommandIntent
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject
import com.projectswg.holocore.services.gameplay.bots.BotCompanionService
import com.projectswg.holocore.services.gameplay.bots.BotServiceHub
import com.projectswg.holocore.services.gameplay.bots.BotDialogueService
import com.projectswg.holocore.services.gameplay.bots.BotPopulationService
import com.projectswg.holocore.services.gameplay.bots.BotTelemetryService
import com.projectswg.holocore.services.gameplay.bots.model.BotSimulationTier
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service

/**
 * Admin commands for bot management and testing.
 * Integrates with BotManager services.
 */
class AdminBotService : Service() {

	private lateinit var botPopulationService: BotPopulationService
	private lateinit var botDialogueService: BotDialogueService
	private lateinit var botCompanionService: BotCompanionService
	private lateinit var botTelemetryService: BotTelemetryService

	override fun initialize(): Boolean {
		return super.initialize()
	}

	@IntentHandler
	private fun handleAdminCommand(intent: ExecuteCommandIntent) {
		val admin = intent.source ?: return
		if (!wireServices()) {
			sendMessage(admin, "[BOT] Bot services are not available yet.")
			return
		}
		val command = intent.command.crc
		val args = intent.arguments.split("\\s+".toRegex(), limit = 5)

		val handled = when (command) {
			COMMAND_BOT_SPAWN -> handleSpawnBots(admin, args)
			COMMAND_BOT_INFO -> handleBotInfo(admin, args)
			COMMAND_BOT_TELL -> handleTellBot(admin, args)
			COMMAND_BOT_KILL -> handleKillBots(admin)
			COMMAND_BOT_TIER -> handleBotTier(admin, args)
			COMMAND_BOT_ACTIVITY -> handleBotActivity(admin, args)
			COMMAND_BOT_TELEMETRY -> handleTelemetry(admin, args)
			COMMAND_BOT_MEMORY -> handleBotMemory(admin, args)
			COMMAND_BOT_COMPANION -> handleCompanion(admin, args)
			else -> false
		}

		if (!handled) {
			sendMessage(admin, "[BOT] Unknown command or insufficient arguments.")
		}
	}

	/**
	 * `/admin spawnbots <bot_id>` — promote a specific bot at the admin's current location.
	 * `/admin spawnbots stats <zone>` — show population stats for a zone.
	 */
	private fun handleSpawnBots(admin: CreatureObject, args: List<String>): Boolean {
		if (args.isEmpty()) return false

		if (args[0] == "stats") {
			val zone = args.getOrElse(1) { "tatooine" }
			val stats = botPopulationService.getStatistics()
			sendMessage(admin, "[BOT] Zone: $zone — active: ${botPopulationService.getActiveCount(zone)} / ${botPopulationService.getActiveCap(zone)}")
			sendMessage(admin, "[BOT] Total bots: ${stats.totalBots}, world objects: ${stats.totalSpawned}")
			return true
		}

		val botId = args[0]
		val loc = admin.worldLocation
		val planet = loc.terrain.name.lowercase(java.util.Locale.US)
		val success = botPopulationService.promoteToLocal(botId, planet, loc.x, loc.y, loc.z, loc.yaw)

		if (success) {
			sendMessage(admin, "[BOT] Bot $botId promoted to LOCAL at (${String.format("%.1f", loc.x)}, ${String.format("%.1f", loc.z)}) on $planet")
		} else {
			sendMessage(admin, "[BOT] Failed to promote bot $botId — check zone cap, npcId, and server log")
		}
		return true
	}

	/**
	 * `/admin botinfo <bot_id>`
	 */
	private fun handleBotInfo(admin: CreatureObject, args: List<String>): Boolean {
		if (args.isEmpty()) return false

		val botId = args[0]
		val profile = botPopulationService.getProfile(botId) ?: run {
			sendMessage(admin, "[BOT] Bot not found: $botId")
			return false
		}

		val state = botPopulationService.getState(botId) ?: run {
			sendMessage(admin, "[BOT] State not found for bot: $botId")
			return false
		}

		sendMessage(admin, "=== Bot Information ===")
		sendMessage(admin, "ID: $botId")
		sendMessage(admin, "Name: ${profile.name}")
		sendMessage(admin, "Species: ${profile.species}")
		sendMessage(admin, "Profession: ${profile.profession}")
		sendMessage(admin, "Faction: ${profile.faction}")
		sendMessage(admin, "Home Zone: ${profile.homePlanet}")
		sendMessage(admin, "")
		sendMessage(admin, "=== Current State ===")
		sendMessage(admin, "Zone: ${state.planet}")
		sendMessage(admin, "Tier: ${state.tier}")
		sendMessage(admin, "Activity: ${state.activity}")
		sendMessage(admin, "Mood: ${state.mood}")
		sendMessage(admin, "")

		val memories = botDialogueService.getBotMemories(botId)
		sendMessage(admin, "=== Interactions ===")
		sendMessage(admin, "Total player memories: ${memories.size}")
		memories.forEach { mem ->
			sendMessage(admin, "  Player: ${mem.playerName} (ID: ${mem.playerId}) | Affinity: ${mem.affinityScore} | Tells: ${mem.interactionCount}")
		}

		return true
	}

	/**
	 * `/admin tell <bot_id> <message...>`
	 */
	private fun handleTellBot(admin: CreatureObject, args: List<String>): Boolean {
		if (args.size < 2) return false

		val botId = args[0]
		val message = args.drop(1).joinToString(" ")

		val profile = botPopulationService.getProfile(botId) ?: run {
			sendMessage(admin, "[BOT] Bot not found: $botId")
			return false
		}

		val player = admin.owner ?: return false
		val startTime = System.currentTimeMillis()
		val handled = botDialogueService.tryHandleTell(player, botId, profile.name, message)
		val latencyMs = System.currentTimeMillis() - startTime

		if (handled) {
			sendMessage(admin, "[BOT] Tell sent (latency: ${latencyMs}ms)")
		} else {
			sendMessage(admin, "[BOT] Tell failed (rate-limited or error)")
		}
		return true
	}

	/**
	 * `/admin botkill` — despawn all active bots and demote to DIRECTORY.
	 */
	private fun handleKillBots(admin: CreatureObject): Boolean {
		val spawnService = BotServiceHub.worldSpawnService
		if (spawnService == null) {
			sendMessage(admin, "[BOT] World spawn service unavailable.")
			return true
		}
		val ids = spawnService.getSpawnedBotIds()
		if (ids.isEmpty()) {
			sendMessage(admin, "[BOT] No active bots to kill.")
			return true
		}
		var killed = 0
		for (botId in ids) {
			if (botPopulationService.demoteToBackground(botId)) killed++
		}
		sendMessage(admin, "[BOT] Killed $killed bot(s).")
		return true
	}

	/**
	 * `/admin bot tier <bot_id> <tier>`
	 */
	private fun handleBotTier(admin: CreatureObject, args: List<String>): Boolean {
		if (args.size < 2) return false

		val botId = args[0]
		val tierArg = args[1]
		// Accept either symbolic name ("LOCAL") or ordinal index ("1")
		val tier = tierArg.toIntOrNull()?.let { idx ->
			if (idx < 0 || idx >= BotSimulationTier.entries.size) return false
			BotSimulationTier.entries[idx]
		} ?: try {
			BotSimulationTier.valueOf(tierArg.uppercase(java.util.Locale.US))
		} catch (e: IllegalArgumentException) {
			sendMessage(admin, "[BOT] Unknown tier '$tierArg'. Valid: ${BotSimulationTier.entries.joinToString()}")
			return false
		}
		val success = botPopulationService.setTier(botId, tier)

		if (success) {
			sendMessage(admin, "[BOT] Bot $botId tier set to $tier")
		} else {
			sendMessage(admin, "[BOT] Failed to set tier for bot $botId")
		}
		return true
	}

	/**
	 * `/admin bot activity <bot_id> <activity>`
	 */
	private fun handleBotActivity(admin: CreatureObject, args: List<String>): Boolean {
		if (args.size < 2) return false

		val botId = args[0]
		val activity = args[1]

		val state = botPopulationService.getState(botId) ?: run {
			sendMessage(admin, "[BOT] Bot not found: $botId")
			return false
		}

		state.activity = activity
		sendMessage(admin, "[BOT] Bot $botId activity set to '$activity'")
		return true
	}

	/**
	 * `/admin telemetry <subcommand>`
	 */
	private fun handleTelemetry(admin: CreatureObject, args: List<String>): Boolean {
		val subcommand = args.getOrElse(0) { "summary" }

		when (subcommand) {
			"summary" -> {
				val stats = botPopulationService.getStatistics()
				val dialogueStats = botDialogueService.getStatistics()
				sendMessage(admin, "=== Bot Telemetry Summary ===")
				sendMessage(admin, "Total Bots: ${stats.totalBots}")
				sendMessage(admin, "Total Spawned: ${stats.totalSpawned}")
				sendMessage(admin, "By Tier:")
				stats.totalByTier.forEach { (tier, count) ->
					sendMessage(admin, "  $tier: $count")
				}
				sendMessage(admin, "")
				sendMessage(admin, "Dialogue Stats:")
				sendMessage(admin, "  Memory Entries: ${dialogueStats.totalMemoryEntries}")
				sendMessage(admin, "  Tells Handled: ${dialogueStats.tellsHandledThisSession}")
				sendMessage(admin, "  Rate Limit: ${dialogueStats.rateLimitSeconds}s")
			}
			"zones" -> {
				val stats = botPopulationService.getStatistics()
				sendMessage(admin, "=== Bot Population by Zone ===")
				stats.totalByZone.forEach { (zone, count) ->
					val active = botPopulationService.getActiveCount(zone)
					val cap = botPopulationService.getActiveCap(zone)
					sendMessage(admin, "  $zone: $active/$cap active (Total: $count)")
				}
			}
			else -> {
				sendMessage(admin, "[BOT] Unknown telemetry subcommand: $subcommand")
				return false
			}
		}
		return true
	}

	/**
	 * `/admin bot memory <bot_id> [clear]`
	 */
	private fun handleBotMemory(admin: CreatureObject, args: List<String>): Boolean {
		if (args.isEmpty()) return false

		val botId = args[0]
		val subcommand = args.getOrElse(1) { "list" }

		val profile = botPopulationService.getProfile(botId) ?: run {
			sendMessage(admin, "[BOT] Bot not found: $botId")
			return false
		}

		when (subcommand) {
			"list" -> {
				val memories = botDialogueService.getBotMemories(botId)
				sendMessage(admin, "=== Memory for bot $botId ===")
				if (memories.isEmpty()) {
					sendMessage(admin, "No memory entries.")
				} else {
					memories.forEach { mem ->
						sendMessage(admin, "Player: ${mem.playerName}")
						sendMessage(admin, "  Affinity: ${mem.affinityScore}")
						sendMessage(admin, "  Interactions: ${mem.interactionCount}")
						sendMessage(admin, "  Notes: ${mem.notes}")
					}
				}
			}
			"clear" -> {
				val playerIdArg = args.getOrNull(2)?.toLongOrNull()
				if (playerIdArg == null) {
					sendMessage(admin, "[BOT] Usage: /botmemory <bot_id> clear <player_id>")
				} else {
					val cleared = botDialogueService.clearPlayerMemory(botId, playerIdArg)
					if (cleared) sendMessage(admin, "[BOT] Memory cleared for player $playerIdArg / bot $botId")
					else sendMessage(admin, "[BOT] No memory found for player $playerIdArg / bot $botId")
				}
			}
			else -> return false
		}
		return true
	}

	/**
	 * `/admin companion recruit <role>`
	 */
	private fun handleCompanion(admin: CreatureObject, args: List<String>): Boolean {
		val subcommand = args.getOrElse(0) { "recruit" }

		when (subcommand) {
			"recruit" -> {
				sendMessage(admin, "[BOT] Companion wiring requires world-object support (Phase D). Usage: /companion recruit <bot_id>")
			}
			"release" -> {
				val botIdArg = args.getOrNull(1)
				if (botIdArg == null) {
					sendMessage(admin, "[BOT] Usage: /companion release <bot_id>")
				} else {
					val player = admin.owner
					if (player == null) {
						sendMessage(admin, "[BOT] Cannot release companion: no active player session.")
					} else {
						val released = botCompanionService.releaseCompanion(player, botIdArg)
						if (released) sendMessage(admin, "[BOT] Companion $botIdArg released.")
						else sendMessage(admin, "[BOT] No companion $botIdArg is assigned to you.")
					}
				}
			}
			else -> {
				sendMessage(admin, "[BOT] Usage: /companion {recruit|release} <bot_id>")
				return false
			}
		}
		return true
	}

	private fun sendMessage(creature: CreatureObject, message: String) {
		val player = creature.owner ?: return
		SystemMessageIntent.broadcastPersonal(player, message)
	}

	private fun wireServices(): Boolean {
		// Short-circuit: already wired and services still live
		if (::botPopulationService.isInitialized && BotServiceHub.populationService != null) return true
		val population = BotServiceHub.populationService ?: return false
		val dialogue = BotServiceHub.dialogueService ?: return false
		val companion = BotServiceHub.companionService ?: return false
		val telemetry = BotServiceHub.telemetryService ?: return false

		botPopulationService = population
		botDialogueService = dialogue
		botCompanionService = companion
		botTelemetryService = telemetry
		return true
	}

	companion object {
		private val COMMAND_BOT_SPAWN = CRC.getCrc("spawnbots")
		private val COMMAND_BOT_INFO = CRC.getCrc("botinfo")
		private val COMMAND_BOT_TELL = CRC.getCrc("bottell")
		private val COMMAND_BOT_KILL = CRC.getCrc("botkill")
		private val COMMAND_BOT_TIER = CRC.getCrc("bottier")
		private val COMMAND_BOT_ACTIVITY = CRC.getCrc("botactivity")
		private val COMMAND_BOT_TELEMETRY = CRC.getCrc("bottelemetry")
		private val COMMAND_BOT_MEMORY = CRC.getCrc("botmemory")
		private val COMMAND_BOT_COMPANION = CRC.getCrc("botcompanion")
	}
}
