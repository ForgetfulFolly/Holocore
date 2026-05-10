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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BotPopulationService] — verifies PC1-A zone counter rebuild
 * after server restart (bots restored from persistence with LOCAL tier must be
 * counted immediately so further promotions do not exceed caps).
 */
class BotPopulationServiceTest {

	private var service: BotPopulationService? = null

	@AfterEach
	fun cleanup() {
		service?.stop()
		service = null
		BotServiceHub.testRepositoryOverride = null
		BotServiceHub.populationService = null
		// Reset singleton repository so subsequent tests in the same JVM see a clean state.
		BotServiceHub.repository = InMemoryBotRepository()
	}

	/**
	 * PC1-A: after restarting with a pre-seeded LOCAL bot in the repository,
	 * [BotPopulationService.getActiveCount] must reflect that bot immediately.
	 */
	@Test
	fun zoneCounterRebuiltFromRestoredLocalBots() {
		val repo = InMemoryBotRepository()
		repo.saveBotProfile(botProfile("bot1", "tatooine"))
		repo.saveBotState(BotState("bot1", tier = BotSimulationTier.LOCAL, planet = "tatooine"))

		BotServiceHub.testRepositoryOverride = repo
		val svc = BotPopulationService().also { service = it }
		svc.start()

		assertEquals(1, svc.getActiveCount("tatooine"), "LOCAL bot should be counted in zone on start")
	}

	/**
	 * PC1-A: when a zone is already at cap due to restored bots, further promotion
	 * attempts must be rejected.
	 */
	@Test
	fun restoredLocalBotsPreventsPromotionOverCap() {
		val repo = InMemoryBotRepository()
		// Fill zone cap (tatooine cap = 10) via persisted LOCAL bots
		for (i in 1..10) {
			val id = "bot$i"
			repo.saveBotProfile(botProfile(id, "tatooine"))
			repo.saveBotState(BotState(id, tier = BotSimulationTier.LOCAL, planet = "tatooine"))
		}
		// One more DIRECTORY bot that we try to promote
		repo.saveBotProfile(botProfile("botExtra", "tatooine"))
		repo.saveBotState(BotState("botExtra", tier = BotSimulationTier.DIRECTORY, planet = "tatooine"))

		BotServiceHub.testRepositoryOverride = repo
		val svc = BotPopulationService().also { service = it }
		svc.start()

		assertEquals(10, svc.getActiveCount("tatooine"), "All 10 LOCAL bots should be counted")
		val promoted = svc.promoteToLocal("botExtra", "tatooine")
		assertFalse(promoted, "Promotion should fail when zone is at cap")
		assertEquals(10, svc.getActiveCount("tatooine"), "Count should remain 10 after rejected promotion")
	}

	private fun botProfile(id: String, planet: String) = BotProfile(
		botId = id,
		name = "Test_$id",
		species = "human",
		profession = "brawler",
		faction = "neutral",
		homePlanet = planet,
	)
}
