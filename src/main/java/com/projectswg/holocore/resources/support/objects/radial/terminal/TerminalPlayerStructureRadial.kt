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
package com.projectswg.holocore.resources.support.objects.radial.terminal

import com.projectswg.common.data.radial.RadialItem
import com.projectswg.common.data.radial.RadialOption
import com.projectswg.common.data.sui.SuiEvent
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent.Companion.broadcastPersonal
import com.projectswg.holocore.intents.support.objects.DestroyObjectIntent
import com.projectswg.holocore.intents.support.objects.ObjectCreatedIntent
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.global.zone.sui.SuiButtons
import com.projectswg.holocore.resources.support.global.zone.sui.SuiInputBox
import com.projectswg.holocore.resources.support.global.zone.sui.SuiListBox
import com.projectswg.holocore.resources.support.global.zone.sui.SuiMessageBox
import com.projectswg.holocore.resources.support.objects.ObjectCreator
import com.projectswg.holocore.resources.support.objects.radial.RadialHandlerInterface
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import com.projectswg.holocore.resources.support.objects.swg.ServerAttribute
import com.projectswg.holocore.resources.support.objects.swg.building.BuildingObject
import com.projectswg.holocore.resources.support.objects.swg.tangible.TangibleObject
import com.projectswg.holocore.services.gameplay.structures.housing.StructureAdminRegistry

/**
 * Radial menu for the player-structure management terminal that gets placed inside
 * any building spawned via /qatool deed. Provides minimal admin/destroy/pack-up
 * functionality so spawned buildings can be cleaned up or roundtripped to a deed.
 *
 * The terminal's superParent is always the BuildingObject it manages. The original
 * building template is stashed on the terminal as DEED_GEN_TEMPLATE, so Pack Up
 * can write that template into the deed it gives the player.
 *
 * Future enhancements:
 *  - Admin/Entry/Ban list management
 *  - Public/Private toggle (publicStructure flag)
 *  - Maintenance + power UI
 *  - Name/sign editing
 */
class TerminalPlayerStructureRadial : RadialHandlerInterface {

	override fun getOptions(options: MutableCollection<RadialOption>, player: Player, target: SWGObject) {
		val useOptions: MutableList<RadialOption> = ArrayList()
		useOptions.add(RadialOption.create(RadialItem.SERVER_MENU1, "Manage Structure"))
		useOptions.add(RadialOption.create(RadialItem.SERVER_MENU2, "Pack Up Structure"))
		useOptions.add(RadialOption.create(RadialItem.SERVER_MENU3, "Destroy Structure"))
		options.add(RadialOption.createSilent(RadialItem.ITEM_USE, useOptions))
		options.add(RadialOption.create(RadialItem.EXAMINE))
	}

	override fun handleSelection(player: Player, target: SWGObject, selection: RadialItem) {
		when (selection) {
			RadialItem.ITEM_USE, RadialItem.SERVER_MENU1 -> openManageMenu(player, target)
			RadialItem.SERVER_MENU2                      -> confirmAndPackUp(player, target)
			RadialItem.SERVER_MENU3                      -> confirmAndDestroy(player, target)
			else                                         -> {}
		}
	}

	private fun openManageMenu(player: Player, target: SWGObject) {
		val building = target.superParent as? BuildingObject
		if (building == null) {
			broadcastPersonal(player, "Terminal is not inside a building.")
			return
		}
		SuiListBox().run {
			title = "Structure Management"
			prompt = "Building: ${building.template.substringAfterLast('/').removeSuffix(".iff")}\nLocation: ${building.terrain.name} (${"%.1f".format(building.location.x)}, ${"%.1f".format(building.location.z)})\nObject ID: ${building.objectId}"
			buttons = SuiButtons.OK
			addListItem("Manage Admins / Guilds")
			addListItem("Pack Up Structure (give deed, then destroy)")
			addListItem("Destroy Structure")
			addCallback(SuiEvent.OK_PRESSED, "structureMgmtSelect") { _: SuiEvent, parameters: Map<String, String> ->
				val row = SuiListBox.getSelectedRow(parameters)
				when (row) {
					0    -> openAdminMenu(player, target, building)
					1    -> confirmAndPackUp(player, target)
					2    -> confirmAndDestroy(player, target)
					else -> {}
				}
			}
			display(player)
		}
	}

	private fun openAdminMenu(player: Player, target: SWGObject, building: BuildingObject) {
		val entries = StructureAdminRegistry.list(building.objectId)
		SuiListBox().run {
			title = "Structure Admins / Guilds"
			prompt = "Building: ${building.template.substringAfterLast('/').removeSuffix(".iff")}\n\nSelect [+ Add Player] / [+ Add Guild] to add an entry,\nor select an existing entry to remove it."
			buttons = SuiButtons.OK_CANCEL
			addListItem("[+ Add Player]")
			addListItem("[+ Add Guild]")
			for (e in entries) {
				addListItem("[${e.kind}] ${e.name}")
			}
			addCallback(SuiEvent.OK_PRESSED, "structureAdminSelect") { _: SuiEvent, parameters: Map<String, String> ->
				val row = SuiListBox.getSelectedRow(parameters)
				when {
					row == 0    -> promptAddAdmin(player, target, building, StructureAdminRegistry.EntryKind.PLAYER)
					row == 1    -> promptAddAdmin(player, target, building, StructureAdminRegistry.EntryKind.GUILD)
					row >= 2    -> {
						val entry = entries.getOrNull(row - 2)
						if (entry != null && StructureAdminRegistry.remove(building.objectId, entry)) {
							broadcastPersonal(player, "Removed structure admin: [${entry.kind}] ${entry.name}")
						}
						openAdminMenu(player, target, building)
					}
					else        -> {}
				}
			}
			display(player)
		}
	}

	private fun promptAddAdmin(player: Player, target: SWGObject, building: BuildingObject, kind: StructureAdminRegistry.EntryKind) {
		val label = if (kind == StructureAdminRegistry.EntryKind.PLAYER) "Player" else "Guild"
		SuiInputBox().run {
			title = "Add ${label} Admin"
			prompt = "Enter the ${label.lowercase()} name to add as a structure admin:"
			buttons = SuiButtons.OK_CANCEL
			setMaxLength(64)
			addCallback(SuiEvent.OK_PRESSED, "structureAdminAdd") { _: SuiEvent, parameters: Map<String, String> ->
				val name = SuiInputBox.getEnteredText(parameters).trim()
				if (name.isEmpty()) {
					broadcastPersonal(player, "No name entered.")
				} else if (StructureAdminRegistry.add(building.objectId, kind, name)) {
					broadcastPersonal(player, "Added structure admin: [${kind}] ${name}")
				} else {
					broadcastPersonal(player, "[${kind}] ${name} is already an admin.")
				}
				openAdminMenu(player, target, building)
			}
			display(player)
		}
	}

	private fun confirmAndPackUp(player: Player, target: SWGObject) {
		val building = target.superParent as? BuildingObject ?: return
		SuiMessageBox().run {
			title = "Pack Up Structure"
			prompt = "Pack up ${building.template.substringAfterLast('/').removeSuffix(".iff")}?\n\nA deed will be placed in your inventory and the structure will be destroyed."
			buttons = SuiButtons.YES_NO
			addCallback(SuiEvent.OK_PRESSED, "structurePackUpConfirm") { _: SuiEvent, _: Map<String, String> ->
				packUp(player, target, building)
			}
			display(player)
		}
	}

	private fun confirmAndDestroy(player: Player, target: SWGObject) {
		val building = target.superParent as? BuildingObject ?: return
		SuiMessageBox().run {
			title = "Destroy Structure"
			prompt = "Permanently destroy ${building.template.substringAfterLast('/').removeSuffix(".iff")}?\n\nNo deed will be issued. Anything inside will be lost."
			buttons = SuiButtons.YES_NO
			addCallback(SuiEvent.OK_PRESSED, "structureDestroyConfirm") { _: SuiEvent, _: Map<String, String> ->
				destroyBuilding(player, building, refundDeed = false)
			}
			display(player)
		}
	}

	companion object {
		/** Stock player-city cantina deed used as a generic visual carrier for re-issued deeds. */
		private const val DEED_CARRIER_TEMPLATE = "object/tangible/deed/city_deed/shared_cantina_tatooine_deed.iff"

		private fun packUp(player: Player, terminal: SWGObject, building: BuildingObject) {
			val buildingTemplate = terminal.getServerTextAttribute(ServerAttribute.DEED_GEN_TEMPLATE)
			if (buildingTemplate.isNullOrEmpty()) {
				broadcastPersonal(player, "Terminal has no recorded building template; cannot issue deed. Use Destroy instead.")
				return
			}
			val creature = player.creatureObject
			val inventory = creature?.inventory
			if (inventory == null) {
				broadcastPersonal(player, "Inventory not available.")
				return
			}
			val deed = ObjectCreator.createObjectFromTemplate(DEED_CARRIER_TEMPLATE) as? TangibleObject
			if (deed == null) {
				broadcastPersonal(player, "Failed to create deed carrier object.")
				return
			}
			deed.setServerAttribute(ServerAttribute.DEED_GEN_TEMPLATE, buildingTemplate)
			deed.objectName = "Structure Deed (${buildingTemplate.substringAfterLast('/').removeSuffix(".iff")})"
			deed.moveToContainer(inventory)
			ObjectCreatedIntent(deed).broadcast()

			destroyBuilding(player, building, refundDeed = true)
		}

		private fun destroyBuilding(player: Player, building: BuildingObject, refundDeed: Boolean) {
			val creature = player.creatureObject
			// Move the player out of the building before destroying it so they don't end up in a deleted cell.
			if (creature != null && creature.superParent == building) {
				creature.moveToContainer(null, building.worldLocation)
			}
			DestroyObjectIntent(building).broadcast()
			StructureAdminRegistry.clear(building.objectId)
			if (refundDeed) {
				broadcastPersonal(player, "Structure packed up. Deed placed in your inventory.")
			} else {
				broadcastPersonal(player, "Structure destroyed.")
			}
		}
	}
}
