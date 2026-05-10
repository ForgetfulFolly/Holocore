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

import com.projectswg.holocore.resources.support.data.server_info.mongodb.PswgDatabase
import com.projectswg.holocore.services.gameplay.bots.persistence.BotRepository
import com.projectswg.holocore.services.gameplay.bots.persistence.InMemoryBotRepository
import com.projectswg.holocore.services.gameplay.bots.persistence.MongoBotRepository
import me.joshlarson.jlcommon.log.Log

/**
 * Central hub for shared bot infrastructure.
 * Each service registers on start() and deregisters on stop().
 * AdminBotService reads service refs from here rather than per-service companion objects.
 * The shared [repository] ensures BotPopulationService and BotDialogueService operate on
 * the same backing store (Phase C: MongoDB when available, in-memory fallback).
 */
object BotServiceHub {
	@Volatile var repository: BotRepository = InMemoryBotRepository()
		internal set

	/**
	 * Called once by [BotPopulationService.start] before any service accesses the repository.
	 * Swaps in [MongoBotRepository] when MongoDB is available; keeps in-memory otherwise.
	 */
	internal fun initRepository() {
		val db = PswgDatabase.mongoDatabase()
		repository = if (db != null) {
			Log.i("BotServiceHub: MongoDB available — using MongoBotRepository")
			MongoBotRepository(db)
		} else {
			Log.w("BotServiceHub: MongoDB not available — using InMemoryBotRepository (non-persistent)")
			InMemoryBotRepository()
		}
	}


	@Volatile var populationService: BotPopulationService? = null
		internal set
	@Volatile var dialogueService: BotDialogueService? = null
		internal set
	@Volatile var companionService: BotCompanionService? = null
		internal set
	@Volatile var telemetryService: BotTelemetryService? = null
		internal set
}
