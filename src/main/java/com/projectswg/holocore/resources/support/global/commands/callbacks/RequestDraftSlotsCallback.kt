/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 * *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 * *
 * This file is part of Holocore.                                                  *
 * *
 * --------------------------------------------------------------------------------*
 * *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 * *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 * *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http:></http:>//www.gnu.org/licenses/>.               *
 */
package com.projectswg.holocore.resources.support.global.commands.callbacks

import com.projectswg.common.network.packets.swg.zone.object_controller.DraftSlotsQueryResponse
import com.projectswg.holocore.resources.support.data.server_info.loader.ServerData
import com.projectswg.holocore.resources.support.global.commands.ICmdCallback
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject

/**
 * Handles requestDraftSlots and requestDraftSlotsBatch commands.
 *
 * Args format (from client): "serverCrc0 clientCrc0 [serverCrc1 clientCrc1 ...]"
 * For each pair we look up the schematic (by serverCrc from the player's draftSchematics map)
 * and send a DraftSlotsQueryResponse containing complexity, volume, canManufacture, and
 * ingredient slots.  The response is used by the client to populate both the datapad
 * schematic detail panel and the Phase-1 crafting window list.
 */
class RequestDraftSlotsCallback : ICmdCallback {
	override fun execute(player: Player, target: SWGObject?, args: String) {
		val playerObj = player.playerObject ?: return
		val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
		if (parts.isEmpty()) return

		val creatureObjectId = player.creatureObject.objectId
		val allSchematics = ServerData.draftSchematics.getAllSchematics()

		// Process in pairs: serverCrc clientCrc [serverCrc clientCrc ...]
		// If an odd final token exists it is also tried as a lone serverCrc.
		var i = 0
		while (i < parts.size) {
			val serverCrcArg = parts[i].toIntOrNull()
			i++
			// Consume the paired clientCrc token (used only to advance past it;
			// lookup is by serverCrc since that uniquely identifies the schematic).
			if (i < parts.size && parts[i].toIntOrNull() != null) i++

			if (serverCrcArg == null) continue

			// Locate this schematic in the player's owned schematics
			val combinedCrc = playerObj.draftSchematics.keys
				.firstOrNull { crc -> (crc ushr 32).toInt() == serverCrcArg } ?: continue

			val serverCrc = (combinedCrc ushr 32).toInt()
			val clientCrc = combinedCrc.toInt()

			val schematic = allSchematics.firstOrNull { it.combinedCrc == combinedCrc } ?: continue
			player.sendPacket(DraftSlotsQueryResponse(schematic, creatureObjectId, clientCrc, serverCrc))
		}
	}
}
