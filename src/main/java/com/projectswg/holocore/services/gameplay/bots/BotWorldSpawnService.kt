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

import com.projectswg.common.data.location.Location
import com.projectswg.common.data.location.Terrain
import com.projectswg.holocore.intents.support.objects.DestroyObjectIntent
import com.projectswg.holocore.intents.support.objects.ObjectCreatedIntent
import com.projectswg.holocore.resources.support.data.server_info.loader.ServerData
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcStaticSpawnLoader.SpawnerFlag
import com.projectswg.holocore.resources.support.npc.spawn.NPCCreator
import com.projectswg.holocore.resources.support.npc.spawn.SimpleSpawnInfo
import com.projectswg.holocore.resources.support.npc.spawn.Spawner
import com.projectswg.holocore.resources.support.npc.spawn.SpawnerType
import com.projectswg.holocore.resources.support.objects.ObjectCreator
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureDifficulty
import com.projectswg.holocore.resources.support.objects.swg.custom.AIBehavior
import com.projectswg.holocore.resources.support.objects.swg.custom.AIObject
import com.projectswg.holocore.services.gameplay.bots.model.BotProfile
import com.projectswg.holocore.services.gameplay.bots.model.BotSimulationTier
import com.projectswg.holocore.services.gameplay.bots.model.BotState
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the physical presence of bots in the game world.
 *
 * On [start], re-spawns any bots that were persisted in LOCAL or COMPANION tier —
 * if no valid [BotProfile.npcId] is configured, the bot is auto-demoted back to
 * DIRECTORY rather than leaving a ghost entry (PC1-G).
 *
 * Callers (e.g. [BotPopulationService.promoteToLocal]) should use [spawnBotObject]
 * and [despawnBotObject] to keep world state in sync with simulation tier changes.
 *
 * START-ORDER REQUIREMENT: This service must start AFTER [BotPopulationService] so
 * that [BotServiceHub.populationService] is registered when [start] runs. The order
 * is enforced by [BotManager]'s [ManagerStructure] children list — do not reorder it.
 */
class BotWorldSpawnService : Service() {

	/** Tracks both the spawner egg and the live AIObject for each bot in the world. */
	private data class SpawnedBot(val egg: SWGObject, val aiObject: AIObject)

	/** botId → live objects in the game world */
	private val spawnedBots = ConcurrentHashMap<String, SpawnedBot>()

	override fun start(): Boolean {
		BotServiceHub.worldSpawnService = this

		val pop = BotServiceHub.populationService
		if (pop == null) {
			Log.w("BotWorldSpawnService: BotPopulationService not registered — skipping world restore")
			return true
		}

		// Collect all LOCAL/COMPANION states across all known planets
		val activeStates = BotServiceHub.repository.getAllBotStates().filter {
			it.tier == BotSimulationTier.LOCAL || it.tier == BotSimulationTier.COMPANION
		}

		var spawned = 0
		var demoted = 0
		for (state in activeStates) {
			val profile = pop.getProfile(state.botId) ?: continue
			if (spawnBotObject(profile, state) != null) {
				spawned++
			} else {
				// No valid npcId — demote to prevent phantom active-counter entries
				pop.demoteToBackground(state.botId)
				demoted++
			}
		}

		Log.i("BotWorldSpawnService: restored %d bot(s), auto-demoted %d (un-spawnable)", spawned, demoted)
		return true
	}

	override fun stop(): Boolean {
		// Sync live AIObject positions back into BotState before destroying world objects,
		// so BotPopulationService.stop() (which runs after in reverse order) saves fresh coords.
		val pop = BotServiceHub.populationService
		spawnedBots.forEach { (botId, spawned) ->
			if (pop != null) {
				val state = pop.getState(botId)
				if (state != null) {
					val loc = spawned.aiObject.worldLocation
					state.x = loc.x
					state.y = loc.y
					state.z = loc.z
					state.heading = loc.yaw
				}
			}
			DestroyObjectIntent(spawned.aiObject).broadcast()
			DestroyObjectIntent(spawned.egg).broadcast()
		}
		spawnedBots.clear()
		if (BotServiceHub.worldSpawnService === this) BotServiceHub.worldSpawnService = null
		return super.stop()
	}

	/**
	 * Spawn a world object for [profile] at the position recorded in [state].
	 * Returns the new [AIObject], or null if spawning was not possible
	 * (e.g. unknown npcId or ObjectCreationException).
	 */
	fun spawnBotObject(profile: BotProfile, state: BotState): AIObject? {
		val npcId = profile.npcId.takeIf { it.isNotBlank() } ?: return null

		// Verify the NPC ID is loaded before attempting creation
		if (ServerData.npcs[npcId] == null) {
			Log.w("BotWorldSpawnService: unknown npcId '%s' for bot '%s' — cannot spawn", npcId, profile.botId)
			return null
		}

		val terrain = terrainForPlanet(state.planet) ?: run {
			Log.w("BotWorldSpawnService: unknown terrain for planet '%s' (bot '%s')", state.planet, profile.botId)
			return null
		}

		val location = Location.builder()
			.setTerrain(terrain)
			.setX(state.x)
			.setY(state.y)
			.setZ(state.z)
			.setHeading(state.heading)
			.build()

		if (!state.hasLocation) {
			Log.w("BotWorldSpawnService: bot '%s' has no recorded location — spawning at (%.1f, %.1f, %.1f); use promoteToLocal with explicit coords", profile.botId, state.x, state.y, state.z)
		}

		var createdEgg: SWGObject? = null
		return try {
			val egg = ObjectCreator.createObjectFromTemplate(SpawnerType.WAYPOINT_AUTO_SPAWN.objectTemplate)
			createdEgg = egg
			egg.moveToContainer(null, location)
			ObjectCreatedIntent(egg).broadcast()

			val spawnInfo = SimpleSpawnInfo.builder()
				.withNpcId(npcId)
				.withDifficulty(try { CreatureDifficulty.valueOf(profile.difficulty) } catch (e: Exception) { CreatureDifficulty.NORMAL })
				.withMinLevel(profile.level)
				.withMaxLevel(profile.level)
				.withSpawnerFlag(if (profile.invulnerable) SpawnerFlag.INVULNERABLE else SpawnerFlag.ATTACKABLE)
				.withBehavior(try { AIBehavior.valueOf(profile.behavior) } catch (e: Exception) { AIBehavior.LOITER })
				.withLocation(location)
				.withAmount(1)
				.build()

			val aiObject = NPCCreator.createSingleNpc(Spawner(spawnInfo, egg))
			spawnedBots[profile.botId] = SpawnedBot(egg, aiObject)
			Log.i("BotWorldSpawnService: spawned bot '%s' (%s) at %s", profile.botId, npcId, location)
			aiObject
		} catch (e: Exception) {
			createdEgg?.let { DestroyObjectIntent(it).broadcast() }
			Log.e("BotWorldSpawnService: failed to spawn bot '%s': %s: %s", profile.botId, e.javaClass.simpleName, e.message)
			null
		}
	}

	/**
	 * Remove a bot's world object and clean up tracking state.
	 */
	fun despawnBotObject(botId: String) {
		val spawned = spawnedBots.remove(botId) ?: return
		DestroyObjectIntent(spawned.aiObject).broadcast()
		DestroyObjectIntent(spawned.egg).broadcast()
		Log.d("BotWorldSpawnService: despawned bot '%s'", botId)
	}

	/** Number of bots currently tracked in the world. */
	fun getSpawnedCount(): Int = spawnedBots.size

	/** Return the set of botIds currently tracked in the world. */
	fun getSpawnedBotIds(): Set<String> = spawnedBots.keys.toSet()

	/**
	 * Return the [AIObject.objectId] of a spawned bot, or null if not tracked.
	 * Single source of truth — replaces the now-removed [BotPopulationService.spawnedWorldObjects].
	 */
	fun getSpawnedObjectId(botId: String): Long? = spawnedBots[botId]?.aiObject?.objectId

	/** Map planet name (as stored in BotState) to a [Terrain] value. Uses the enum's own lookup table. */
	private fun terrainForPlanet(planet: String): Terrain? = Terrain.getTerrainFromName(planet)
}
