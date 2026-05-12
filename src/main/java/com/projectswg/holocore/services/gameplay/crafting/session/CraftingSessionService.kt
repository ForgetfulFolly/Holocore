/***********************************************************************************
 * Copyright (c) 2025 /// Project SWG /// www.projectswg.com                      *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.crafting.session

import com.projectswg.common.data.crafting.CraftingType
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueDraftSchematics
import com.projectswg.holocore.intents.gameplay.crafting.CancelCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.CraftExperimentIntent
import com.projectswg.holocore.intents.gameplay.crafting.CreatePrototypeIntent
import com.projectswg.holocore.intents.gameplay.crafting.NextCraftingStageIntent
import com.projectswg.holocore.intents.gameplay.crafting.RequestCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.SelectDraftSchematicIntent
import com.projectswg.holocore.resources.support.data.server_info.loader.ServerData
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log

/**
 * Phase D implementation: sends the schematic list packet when a player opens
 * a crafting tool.  Phases E-I (assembly, experimentation, finish, factory)
 * will be added in subsequent phases per IMPLEMENTATION-PLAN.md.
 */
class CraftingSessionService : Service() {

    @IntentHandler
    private fun handleRequestCraftingSession(intent: RequestCraftingSessionIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        val toolId = intent.tool.objectId

        // Index the loaded schematics by serverCrc for O(1) lookup
        val schematicsByServerCrc = ServerData.draftSchematics.getAllSchematics()
            .associateBy { it.serverCrc }

        // Build MessageQueueDraftSchematics — toolId from the activated tool;
        // stationId is 0 (station tracking is Phase E).
        val packet = MessageQueueDraftSchematics(player.creatureObject.objectId, toolId, 0L)

        for (combinedCrc in playerObj.draftSchematics.keys) {
            val serverCrc = (combinedCrc ushr 32).toInt()
            val sharedCrc = combinedCrc.toInt()
            val schematic = schematicsByServerCrc[serverCrc]
            val category  = if (schematic != null) categoryToInt(schematic.category) else 0
            packet.addSchematic(serverCrc, sharedCrc, category)
        }

        Log.d(
            "[crafting] Sent %d schematics to %s (tool=%d)",
            playerObj.draftSchematics.size,
            player.username,
            toolId
        )
        player.sendPacket(packet)
    }

    @IntentHandler
    private fun handleCancelCraftingSession(intent: CancelCraftingSessionIntent) {
        // Phase E: tear down active session state
    }

    @IntentHandler
    private fun handleSelectDraftSchematic(intent: SelectDraftSchematicIntent) {
        // Phase D+: send DraftSlotsQueryResponse for the selected schematic
    }

    @IntentHandler
    private fun handleNextCraftingStage(intent: NextCraftingStageIntent) {
        // Phase E: advance assembly stage
    }

    @IntentHandler
    private fun handleCraftExperiment(intent: CraftExperimentIntent) {
        // Phase F: experimentation
    }

    @IntentHandler
    private fun handleCreatePrototype(intent: CreatePrototypeIntent) {
        // Phase G: create prototype item
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Maps the string category from draft schematic JSON to the NGE CraftingType bitmask int. */
    private fun categoryToInt(category: String): Int = when (category) {
        "CT_weapon"         -> CraftingType.WEAPON
        "CT_armor"          -> CraftingType.ARMOR
        "CT_food"           -> CraftingType.FOOD
        "CT_clothing"       -> CraftingType.CLOTHING
        "CT_vehicle"        -> CraftingType.VEHICLE
        "CT_droid"          -> CraftingType.DROID
        "CT_chemical"       -> CraftingType.CHEMICAL
        "CT_plantBreeding"  -> CraftingType.PLANT_BREEDING
        "CT_animalBreeding" -> CraftingType.ANIMAL_BREEDING
        "CT_furniture"      -> CraftingType.FURNITURE
        "CT_installation"   -> CraftingType.INSTALLATION
        "CT_lightsaber"     -> CraftingType.LIGHTSABER
        "CT_genericItem"    -> CraftingType.GENERIC_ITEM
        "CT_genetics"       -> CraftingType.GENETICS
        else                -> 0
    }
}
