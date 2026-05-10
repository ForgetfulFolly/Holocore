/***********************************************************************************
 * Copyright (c) 2024 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.bots

import com.projectswg.holocore.services.gameplay.bots.model.BotProfile
import com.projectswg.holocore.services.gameplay.bots.model.BotSimulationTier
import com.projectswg.holocore.services.gameplay.bots.model.BotState
import com.projectswg.holocore.services.gameplay.bots.persistence.InMemoryBotRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BotWorldSpawnService] — verifies guard-rail behaviour that
 * does NOT require the full game-world infrastructure (ServerData, ObjectCreator).
 *
 * Full-stack spawn integration tests belong in an AcceptanceTest subclass.
 */
class BotWorldSpawnServiceTest {

	private var service: BotWorldSpawnService? = null

	@AfterEach
	fun cleanup() {
		BotServiceHub.worldSpawnService = null
		BotServiceHub.populationService = null
		BotServiceHub.testRepositoryOverride = null
		BotServiceHub.repository = InMemoryBotRepository()
		service = null
	}

	/**
	 * [BotWorldSpawnService.spawnBotObject] must return null immediately when
	 * [BotProfile.npcId] is blank — without touching ServerData or ObjectCreator.
	 */
	@Test
	fun `spawnBotObject returns null when npcId is blank`() {
		val svc = BotWorldSpawnService().also { service = it }
		// Manually register so the service can read BotServiceHub.worldSpawnService
		BotServiceHub.worldSpawnService = svc

		val profile = BotProfile(
			botId = "bot_blank_npc",
			name = "Test Bot",
			species = "human",
			profession = "brawler",
			faction = "neutral",
			homePlanet = "tatooine",
			npcId = "",
		)
		val state = BotState("bot_blank_npc", planet = "tatooine")

		val result = svc.spawnBotObject(profile, state)

		assertNull(result, "blank npcId must return null without attempting world spawn")
	}

	/**
	 * [BotWorldSpawnService.despawnBotObject] must be a no-op (no exception) when
	 * the given botId has no tracked world object.
	 */
	@Test
	fun `despawnBotObject is silent no-op for untracked bot`() {
		val svc = BotWorldSpawnService().also { service = it }
		BotServiceHub.worldSpawnService = svc

		// Should complete without throwing
		svc.despawnBotObject("nonexistent_bot_id")
	}
}
