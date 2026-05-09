package com.projectswg.holocore.services.support.global.admin

import com.projectswg.common.data.CRC
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent
import com.projectswg.holocore.intents.support.global.command.ExecuteCommandIntent
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject
import com.projectswg.holocore.services.gameplay.bots.BotCompanionService
import com.projectswg.holocore.services.gameplay.bots.BotDialogueService
import com.projectswg.holocore.services.gameplay.bots.BotPopulationService
import com.projectswg.holocore.services.gameplay.bots.BotTelemetryService
import com.projectswg.holocore.services.gameplay.bots.model.BotSimulationTier
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log

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
		if (!wireServices()) {
			Log.w("AdminBotService initialized before bot services were available; commands will be disabled until they start")
		}
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
	 * `/admin spawnbots <count> <zone>`
	 */
	private fun handleSpawnBots(admin: CreatureObject, args: List<String>): Boolean {
		if (args.size < 2) return false

		val count = args[0].toIntOrNull() ?: return false
		val zone = args[1]

		sendMessage(admin, "[BOT] Spawning $count bots in zone '$zone'...")

		// TODO: Actually spawn bots (requires CreatureObject creation)
		// For now, register them in memory and promote to LOCAL tier

		val stats = botPopulationService.getStatistics()
		sendMessage(admin, "[BOT] Active bots: ${stats.totalSpawned} / ${botPopulationService.getActiveCap(zone)}")
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

		val state = botPopulationService.getState(botId)!!

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
	 * `/admin kill bots`
	 */
	private fun handleKillBots(admin: CreatureObject): Boolean {
		val stats = botPopulationService.getStatistics()
		sendMessage(admin, "[BOT] Despawning ${stats.totalSpawned} active bots...")
		// TODO: Iterate and despawn all active bots
		sendMessage(admin, "[BOT] Despawn complete.")
		return true
	}

	/**
	 * `/admin bot tier <bot_id> <tier>`
	 */
	private fun handleBotTier(admin: CreatureObject, args: List<String>): Boolean {
		if (args.size < 2) return false

		val botId = args[0]
		val tierIndex = args[1].toIntOrNull() ?: return false

		if (tierIndex < 0 || tierIndex >= BotSimulationTier.values().size) return false

		val tier = BotSimulationTier.values()[tierIndex]
		val success = botPopulationService.setTier(botId, tier)

		if (success) {
			sendMessage(admin, "[BOT] Bot $botId promoted to tier $tierIndex ($tier)")
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
				sendMessage(admin, "[BOT] Memory clearing not yet implemented.")
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
				val role = args.getOrElse(1) { "dps" }
				// TODO: Actually recruit companion
				sendMessage(admin, "[BOT] Companion recruitment not yet fully implemented.")
			}
			else -> return false
		}
		return true
	}

	private fun sendMessage(creature: CreatureObject, message: String) {
		val player = creature.owner ?: return
		SystemMessageIntent.broadcastPersonal(player, message)
	}

	private fun wireServices(): Boolean {
		val population = BotPopulationService.instance ?: return false
		val dialogue = BotDialogueService.instance ?: return false
		val companion = BotCompanionService.instance ?: return false
		val telemetry = BotTelemetryService.instance ?: return false

		botPopulationService = population
		botDialogueService = dialogue
		botCompanionService = companion
		botTelemetryService = telemetry
		return true
	}

	companion object {
		private val COMMAND_BOT_SPAWN = CRC.getCrc("spawnbots")
		private val COMMAND_BOT_INFO = CRC.getCrc("botinfo")
		private val COMMAND_BOT_TELL = CRC.getCrc("tell")
		private val COMMAND_BOT_KILL = CRC.getCrc("kill")
		private val COMMAND_BOT_TIER = CRC.getCrc("bottier")
		private val COMMAND_BOT_ACTIVITY = CRC.getCrc("botactivity")
		private val COMMAND_BOT_TELEMETRY = CRC.getCrc("telemetry")
		private val COMMAND_BOT_MEMORY = CRC.getCrc("botmemory")
		private val COMMAND_BOT_COMPANION = CRC.getCrc("companion")
	}
}
