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

import com.projectswg.common.data.crafting.CraftingResult
import com.projectswg.common.data.crafting.CraftingType
import com.projectswg.common.network.packets.swg.zone.object_controller.CraftAcknowledge
import com.projectswg.common.network.packets.swg.zone.object_controller.CraftExperimentationResponse
import com.projectswg.common.network.packets.swg.zone.object_controller.CraftingSessionEnded
import com.projectswg.common.network.packets.swg.zone.object_controller.DraftSlotsQueryResponse
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueCraftEmptySlot
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueCraftExperiment
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueCraftFillSlot
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueDraftSchematics
import com.projectswg.holocore.intents.gameplay.crafting.CancelCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.CraftExperimentIntent
import com.projectswg.holocore.intents.gameplay.crafting.CreatePrototypeIntent
import com.projectswg.holocore.intents.gameplay.crafting.NextCraftingStageIntent
import com.projectswg.holocore.intents.gameplay.crafting.RequestCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.SelectDraftSchematicIntent
import com.projectswg.holocore.intents.gameplay.player.experience.ExperienceIntent
import com.projectswg.holocore.intents.support.global.network.InboundPacketIntent
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent
import com.projectswg.holocore.intents.support.objects.ObjectCreatedIntent
import com.projectswg.holocore.resources.support.data.server_info.loader.ServerData
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.global.player.PlayerEvent
import com.projectswg.holocore.resources.support.objects.ObjectCreator
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import com.projectswg.holocore.resources.support.objects.swg.manufacture.ManufactureSchematicObject
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Handles all crafting session phases D through G per IMPLEMENTATION-PLAN.md.
 *
 * Phase D: send schematic list (filtered by tool type)
 * Phase E: select schematic → MSCO creation, slot fill/empty, assembly roll
 * Phase F: experimentation (stub)
 * Phase G: prototype creation (stub)
 */
class CraftingSessionService : Service() {

    /** Per-player crafting session state.  Key = creature object ID. */
    private val activeSessions = ConcurrentHashMap<Long, ActiveCraftingSession>()

    // ------------------------------------------------------------------
    // CraftAcknowledge.acknowledgeId protocol constants (NGE)
    // ------------------------------------------------------------------
    private companion object {
        const val ACK_FILL_SLOT  = 0   // ingredient placed into slot
        const val ACK_EMPTY_SLOT = 1   // slot cleared
        const val ACK_ASSEMBLY   = 2   // nextCraftingStage result (assembly done)
        const val ACK_EXPERIMENT = 3   // experimentation result
        const val ACK_SUCCESS    = 0   // no error (errorId)

        /** MSCO IFF template — the default manufacture schematic intangible object */
        const val MSCO_TEMPLATE = "object/manufacture_schematic/base/shared_manufacture_schematic_default.iff"
    }

    // ------------------------------------------------------------------
    // Phase D — schematic list dispatch
    // ------------------------------------------------------------------

    @IntentHandler
    private fun handleRequestCraftingSession(intent: RequestCraftingSessionIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        val toolId = intent.tool.objectId
        val toolMask = toolAllowedTypeMask(intent.tool)

        val schematicsByServerCrc = ServerData.draftSchematics.getAllSchematics()
            .associateBy { it.serverCrc }

        val packet = MessageQueueDraftSchematics(player.creatureObject.objectId, toolId, 0L)
        var sent = 0

        for (combinedCrc in playerObj.draftSchematics.keys) {
            val serverCrc = (combinedCrc ushr 32).toInt()
            val sharedCrc = combinedCrc.toInt()
            val schematic = schematicsByServerCrc[serverCrc]
            val categoryInt = if (schematic != null) categoryToInt(schematic.category) else 0

            if (toolMask != 0 && (toolMask and categoryInt) == 0) continue

            packet.addSchematic(serverCrc, sharedCrc, categoryInt)
            sent++
        }

        Log.i(
            "[crafting] Sent %d/%d schematics to %s (tool=%d mask=0x%X)",
            sent, playerObj.draftSchematics.size, player.username, toolId, toolMask
        )
        player.sendPacket(packet)
    }

    // ------------------------------------------------------------------
    // Phase E — schematic selection + MSCO creation
    // ------------------------------------------------------------------

    @IntentHandler
    private fun handleSelectDraftSchematic(intent: SelectDraftSchematicIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        val creatureId = player.creatureObject.objectId

        val serverCrc = intent.schematicId
        val combinedCrc = playerObj.draftSchematics.keys
            .firstOrNull { crc -> (crc ushr 32).toInt() == serverCrc }
        if (combinedCrc == null) {
            Log.w("[crafting] Player %s selected unknown schematic serverCrc=0x%X", player.username, serverCrc)
            return
        }

        val allSchematics = ServerData.draftSchematics.getAllSchematics()
        val schematic = allSchematics.firstOrNull { it.combinedCrc == combinedCrc }
        if (schematic == null) {
            Log.w("[crafting] No loaded schematic for combinedCrc=%d", combinedCrc)
            return
        }

        // Tear down any previous session before starting a new one
        tearDownSession(creatureId)

        // Create the MSCO object and add it to the player's datapad
        val msco = ObjectCreator.createObjectFromTemplate(MSCO_TEMPLATE) as? ManufactureSchematicObject
        if (msco == null) {
            Log.e("[crafting] Failed to create MSCO from template %s", MSCO_TEMPLATE)
            return
        }
        msco.draftSchematicTemplate = schematic.craftedSharedTemplate ?: ""
        msco.itemsPerContainer = schematic.itemsPerContainer
        msco.isCrafting = true
        msco.moveToContainer(player.creatureObject.datapad)

        activeSessions[creatureId] = ActiveCraftingSession(
            schematic   = schematic,
            msco        = msco,
            filledSlots = HashMap(),
            sequenceId  = 0
        )

        // Send slot info so the client renders the ingredient slots
        val clientCrc = combinedCrc.toInt()
        player.sendPacket(DraftSlotsQueryResponse(schematic, creatureId, clientCrc, serverCrc))

        Log.d(
            "[crafting] Player %s selected schematic 0x%X, MSCO=%d",
            player.username, serverCrc, msco.objectId
        )
    }

    // ------------------------------------------------------------------
    // Phase E — slot fill / empty
    // ------------------------------------------------------------------

    @IntentHandler
    private fun handleInboundPacketIntent(intent: InboundPacketIntent) {
        val player = intent.player ?: return
        when (val packet = intent.packet) {
            is MessageQueueCraftFillSlot  -> handleCraftFillSlot(player, packet)
            is MessageQueueCraftEmptySlot -> handleCraftEmptySlot(player, packet)
            is MessageQueueCraftExperiment -> handleCraftExperimentPacket(player, packet)
        }
    }

    private fun handleCraftFillSlot(player: Player, packet: MessageQueueCraftFillSlot) {
        val creatureId = player.creatureObject.objectId
        val session = activeSessions[creatureId] ?: run {
            Log.w("[crafting] CraftFillSlot from %s with no active session", player.username)
            return
        }

        session.filledSlots[packet.slotId] = packet.resourceId
        session.sequenceId++

        player.sendPacket(CraftAcknowledge(ACK_FILL_SLOT, ACK_SUCCESS, session.sequenceId.toByte()))
        Log.d("[crafting] %s filled slot %d with ingredient %d", player.username, packet.slotId, packet.resourceId)
    }

    private fun handleCraftEmptySlot(player: Player, packet: MessageQueueCraftEmptySlot) {
        val creatureId = player.creatureObject.objectId
        val session = activeSessions[creatureId] ?: return

        session.filledSlots.remove(packet.slot)
        session.sequenceId++

        player.sendPacket(CraftAcknowledge(ACK_EMPTY_SLOT, ACK_SUCCESS, session.sequenceId.toByte()))
        Log.d("[crafting] %s emptied slot %d", player.username, packet.slot)
    }

    private fun handleCraftExperimentPacket(player: Player, packet: MessageQueueCraftExperiment) {
        val creatureId = player.creatureObject.objectId
        val session = activeSessions[creatureId] ?: run {
            Log.w("[crafting] CraftExperiment from %s with no active session", player.username)
            return
        }
        val playerObj = player.playerObject ?: return

        val totalSpent = packet.spentPoints.sum()
        if (totalSpent <= 0) return

        // Deduct experimentation points (floor at 0)
        playerObj.experimentPoints = (playerObj.experimentPoints - totalSpent).coerceAtLeast(0)

        // Roll: d100 + 10 per point spent + assembly quality bonus
        val assemblyBonus = when (session.assemblyResult) {
            CraftingResult.CRITICAL_SUCCESS -> 30
            CraftingResult.GREAT_SUCCESS    -> 20
            CraftingResult.GOOD_SUCCESS     -> 10
            CraftingResult.MODERATE_SUCCESS -> 5
            else                            -> 0
        }
        val roll = Random.nextInt(100) + totalSpent * 10 + assemblyBonus

        // stringId per NGE experimentation feedback strings: 1=great, 2=success, 3=moderate, 4=failure
        val stringId = when {
            roll >= 100 -> 1  // great success
            roll >= 70  -> 2  // success
            roll >= 40  -> 3  // moderate
            else        -> 4  // failure
        }

        player.sendPacket(CraftExperimentationResponse(0, stringId, packet.actionCounter))
        Log.d(
            "[crafting] %s experimented: spent=%d roll=%d → stringId=%d (expPts remaining=%d)",
            player.username, totalSpent, roll, stringId, playerObj.experimentPoints
        )
    }

    // ------------------------------------------------------------------
    // Phase E — assembly (NextCraftingStage)
    // ------------------------------------------------------------------

    @IntentHandler
    private fun handleNextCraftingStage(intent: NextCraftingStageIntent) {
        val player = intent.player
        val creatureId = player.creatureObject.objectId
        val session = activeSessions[creatureId] ?: run {
            Log.w("[crafting] NextCraftingStage from %s with no active session", player.username)
            return
        }

        val schematic = session.schematic
        val requiredSlots = schematic.ingridientSlot.count { !it.isOptional }

        if (session.filledSlots.size < requiredSlots) {
            Log.d(
                "[crafting] %s attempted assembly with %d/%d required slots",
                player.username, session.filledSlots.size, requiredSlots
            )
            player.sendPacket(CraftAcknowledge(ACK_ASSEMBLY, CraftingResult.FAILURE.value, 0))
            return
        }

        // Simplified assembly roll: d100 minus complexity penalty.
        // Full attribute-quality math follows in Phase F per IMPLEMENTATION-PLAN.md.
        val rawRoll      = Random.nextInt(100)
        val complexity   = schematic.complexity.coerceIn(0, 100)
        val adjustedRoll = (rawRoll - complexity / 4).coerceIn(0, 99)

        val result = when {
            adjustedRoll >= 90 -> CraftingResult.CRITICAL_SUCCESS
            adjustedRoll >= 75 -> CraftingResult.GREAT_SUCCESS
            adjustedRoll >= 55 -> CraftingResult.GOOD_SUCCESS
            adjustedRoll >= 35 -> CraftingResult.SUCCESS
            adjustedRoll >= 20 -> CraftingResult.FAILURE
            adjustedRoll >= 10 -> CraftingResult.MODERATE_FAILURE
            else               -> CraftingResult.BIG_FAILURE
        }

        session.assemblyResult = result
        session.sequenceId++

        player.sendPacket(CraftAcknowledge(ACK_ASSEMBLY, result.value, session.sequenceId.toByte()))
        Log.d(
            "[crafting] %s assembled 0x%X: roll=%d → %s",
            player.username, schematic.serverCrc, adjustedRoll, result.name
        )
    }

    // ------------------------------------------------------------------
    // Cancel + logout cleanup
    // ------------------------------------------------------------------

    @IntentHandler
    private fun handleCancelCraftingSession(intent: CancelCraftingSessionIntent) {
        tearDownSession(intent.player.creatureObject.objectId)
    }

    @IntentHandler
    private fun handlePlayerEventIntent(intent: PlayerEventIntent) {
        if (intent.event == PlayerEvent.PE_LOGGED_OUT) {
            val creatureId = intent.player.creatureObject?.objectId ?: return
            tearDownSession(creatureId)
        }
    }

    private fun tearDownSession(creatureId: Long) {
        val session = activeSessions.remove(creatureId) ?: return
        try {
            session.msco.moveToContainer(null)
        } catch (e: Exception) {
            Log.w("[crafting] Error removing MSCO during teardown: %s", e.message)
        }
    }

    // ------------------------------------------------------------------
    // Phase F — experimentation intent handler
    // (Routed primarily via MessageQueueCraftExperiment ObjController in handleInboundPacketIntent)
    // ------------------------------------------------------------------

    @IntentHandler
    private fun handleCraftExperiment(intent: CraftExperimentIntent) {
        // No-op: experiment packets arrive as ObjController 0x0106, handled in handleCraftExperimentPacket.
        // This handler remains for forward-compat if craftExperiment is ever added to commands_global.sdb.
    }

    // ------------------------------------------------------------------
    // Phase G — prototype creation
    // ------------------------------------------------------------------

    @IntentHandler
    private fun handleCreatePrototype(intent: CreatePrototypeIntent) {
        val player = intent.player
        val creatureId = player.creatureObject.objectId
        val session = activeSessions[creatureId] ?: run {
            Log.w("[crafting] CreatePrototype from %s with no active session", player.username)
            return
        }

        val templatePath = session.schematic.craftedSharedTemplate
        if (templatePath.isNullOrEmpty()) {
            Log.e("[crafting] Schematic 0x%X has no craftedSharedTemplate", session.schematic.serverCrc)
            return
        }

        // Create the crafted item
        val item = ObjectCreator.createObjectFromTemplate(templatePath)
        val inventory = player.creatureObject.getSlottedObject("inventory")
        if (inventory != null) {
            item.moveToContainer(inventory)
        } else {
            Log.w("[crafting] No inventory slot for %s, item %d unparented", player.username, item.objectId)
        }
        ObjectCreatedIntent(item).broadcast()

        // Grant crafting XP: 5 XP per complexity point, minimum 10
        val xpAmount = (session.schematic.complexity * 5).coerceAtLeast(10)
        ExperienceIntent(player.creatureObject, "crafting_general", xpAmount).broadcast()

        Log.d(
            "[crafting] %s created prototype of %s (xp=%d)",
            player.username, templatePath, xpAmount
        )

        // Tear down session and notify client
        tearDownSession(creatureId)
        player.sendPacket(CraftingSessionEnded(creatureId, 0, 1.toByte()))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun toolAllowedTypeMask(tool: SWGObject): Int {
        val t = tool.template
        return when {
            t.contains("weapon_tool")    -> CraftingType.WEAPON
            t.contains("clothing_tool")  -> CraftingType.CLOTHING or CraftingType.ARMOR
            t.contains("food_tool")      -> CraftingType.FOOD or CraftingType.CHEMICAL
            t.contains("structure_tool") -> CraftingType.FURNITURE or CraftingType.INSTALLATION
            t.contains("jedi_tool")      -> CraftingType.LIGHTSABER
            t.contains("space_tool")     -> CraftingType.SPACE or CraftingType.REVERSE_ENGINEERING or CraftingType.SPACE_COMPONENT
            else                         -> 0
        }
    }

    private fun categoryToInt(category: String?): Int = when (category) {
        "CT_weapon"             -> CraftingType.WEAPON
        "CT_armor"              -> CraftingType.ARMOR
        "CT_food"               -> CraftingType.FOOD
        "CT_clothing"           -> CraftingType.CLOTHING
        "CT_vehicle"            -> CraftingType.VEHICLE
        "CT_droid"              -> CraftingType.DROID
        "CT_chemical"           -> CraftingType.CHEMICAL
        "CT_plantBreeding"      -> CraftingType.PLANT_BREEDING
        "CT_animalBreeding"     -> CraftingType.ANIMAL_BREEDING
        "CT_furniture"          -> CraftingType.FURNITURE
        "CT_installation"       -> CraftingType.INSTALLATION
        "CT_lightsaber"         -> CraftingType.LIGHTSABER
        "CT_genericItem"        -> CraftingType.GENERIC_ITEM
        "CT_genetics"           -> CraftingType.GENETICS
        "CT_space"              -> CraftingType.SPACE
        "CT_reverseEngineering" -> CraftingType.REVERSE_ENGINEERING
        "CT_misc"               -> CraftingType.MISC
        "CT_spaceComponent"     -> CraftingType.SPACE_COMPONENT
        else                    -> 0
    }

    // ------------------------------------------------------------------
    // Session state
    // ------------------------------------------------------------------

    private data class ActiveCraftingSession(
        val schematic: com.projectswg.common.data.schematic.DraftSchematic,
        val msco: ManufactureSchematicObject,
        val filledSlots: MutableMap<Int, Long>,
        var sequenceId: Int,
        var assemblyResult: CraftingResult? = null
    )
}
