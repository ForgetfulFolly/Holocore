/***********************************************************************************
 * Copyright (c) 2026 /// Project SWG /// www.projectswg.com                       *
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
package com.projectswg.holocore.resources.support.global.commands.callbacks.admin.qatool

import com.projectswg.common.data.swgfile.visitors.ObjectData.ObjectDataAttribute
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent
import com.projectswg.holocore.intents.support.objects.ObjectCreatedIntent
import com.projectswg.holocore.resources.support.data.server_info.loader.DataLoader
import com.projectswg.holocore.resources.support.data.server_info.loader.ServerData
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.ObjectCreator
import com.projectswg.holocore.resources.support.objects.swg.ServerAttribute
import com.projectswg.holocore.resources.support.objects.swg.building.BuildingObject
import com.projectswg.holocore.services.gameplay.structures.housing.StructureAdminRegistry

/**
 * /qatool deed <building_template>
 *   e.g. /qatool deed object/building/tatooine/shared_cantina_tatooine.iff
 *
 * Spawns the requested NPC building directly at the admin's current world location.
 * No deed, no client placement preview — the SWG client has no placement footprint
 * assets for NPC buildings (cantinas, banks, etc.) and crashes when sent through
 * EnterStructurePlacementModeMessage with one. By bypassing the deed flow entirely,
 * the building is materialized server-side and pushed to the client as a normal
 * world object, which the client renders fine.
 *
 * Implemented under /qatool because the SWG client only forwards command names that
 * exist in its built-in command_table.iff. Server-invented top-level commands are
 * rejected client-side with "No such command, mood, chat type". /qatool is in the
 * client TRE table and Holocore already uses it as a relay for invented subcommands
 * (see AdminBotService).
 *
 * Validation:
 *  - Template must exist in object_data.sdb (else ObjectCreator throws).
 *  - Template must have cell topology in building_cells.sdb (i.e. enterable),
 *    otherwise the building spawns but cannot be entered.
 */
object QaToolStructureDeed {

	fun createDeed(player: Player, buildingTemplate: String) {
		val template = buildingTemplate.trim()
		if (template.isEmpty()) {
			SystemMessageIntent.broadcastPersonal(player, "Usage: /qatool deed <building_template>")
			return
		}
		if (!template.startsWith("object/building/") || !template.endsWith(".iff")) {
			SystemMessageIntent.broadcastPersonal(player, "Invalid template: must be object/building/...iff")
			return
		}
		if (DataLoader.objectData().getAttributes(template) == null) {
			SystemMessageIntent.broadcastPersonal(player, "Unknown building template (not in object_data.sdb): $template")
			return
		}
		if (ServerData.buildingCells.getBuilding(template) == null) {
			SystemMessageIntent.broadcastPersonal(player, "Building has no cell topology (not enterable): $template")
			return
		}

		val creature = player.creatureObject ?: return
		if (creature.parent != null) {
			SystemMessageIntent.broadcastPersonal(player, "Cannot place a building while inside another building.")
			return
		}

		val location = creature.worldLocation
		val structure = ObjectCreator.createObjectFromTemplate(template)
		if (structure !is BuildingObject) {
			SystemMessageIntent.broadcastPersonal(player, "Template did not resolve to a BuildingObject: $template")
			return
		}
		structure.populateCells()
		// Bump every cell's container volume so admins/players can fill the building with
		// decorations. Default for an NPC building cell tends to be very small (1-15 items);
		// 1000 matches/exceeds player-housing capacity for furniture-heavy use cases.
		for (cellNumber in 1..256) {
			val cell = structure.getCellByNumber(cellNumber) ?: break
			cell.setDataAttribute(ObjectDataAttribute.CONTAINER_VOLUME_LIMIT, 1000)
		}
		structure.systemMove(null, location)
		ObjectCreatedIntent(structure).broadcast()
		for (child in structure.childObjects) {
			ObjectCreatedIntent(child).broadcast()
		}

		// Seed the placer as the initial admin so they show up in the manage list.
		StructureAdminRegistry.add(structure.objectId, StructureAdminRegistry.EntryKind.PLAYER, creature.objectName)

		val terminalCell = structure.getCellByNumber(1)
		var terminalNote = ""
		if (terminalCell != null) {
			val terminal = ObjectCreator.createObjectFromTemplate("object/tangible/terminal/shared_terminal_player_structure.iff")
			terminal.objectName = "Structure Management Terminal"
			// Stash the building template so the terminal's "Pack Up" option can issue a deed back.
			terminal.setServerAttribute(ServerAttribute.DEED_GEN_TEMPLATE, template)
			// Use moveToContainer(cell, x, y, z) — the proven pattern from DeveloperService.createCBT
			// for placing a runtime terminal inside a cell. systemMove with a world-terrain Location
			// breaks client visibility for cell-contained objects.
			terminal.moveToContainer(terminalCell, 1.0, 0.0, 1.0)
			ObjectCreatedIntent(terminal).broadcast()
			terminalNote = " (terminal placed in cell 1)"
		} else {
			terminalNote = " (no cell 1 — terminal not placed)"
		}

		SystemMessageIntent.broadcastPersonal(player, "Spawned $template at ${location.terrain.name} (${"%.1f".format(location.x)}, ${"%.1f".format(location.z)})$terminalNote")
	}
}
