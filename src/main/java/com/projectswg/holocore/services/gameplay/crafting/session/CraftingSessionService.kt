/***********************************************************************************
 * Copyright (c) 2024 /// Project SWG /// www.projectswg.com
 *
 * This file is part of Holocore.
 *
 * Holocore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.crafting.session

import com.projectswg.common.network.packets.swg.zone.object_controller.CraftingSessionEnded
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueDraftSchematics
import com.projectswg.holocore.intents.gameplay.crafting.CancelCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.RequestCraftingSessionIntent
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service

/**
 * Handles the open/cancel crafting session lifecycle (Phase 1).
 *
 * Phase 1 flow:
 *  1. Player right-clicks a crafting tool → client sends requestCraftingSession command.
 *  2. [CmdRequestCraftingSession] fires [RequestCraftingSessionIntent].
 *  3. This service sets craftingStage=1 and sends [MessageQueueDraftSchematics] so the
 *     client can show the schematic picker window.
 *
 * Cancel flow:
 *  1. Player clicks Cancel in the crafting window.
 *  2. Client sends cancelCraftingSession command.
 *  3. [CmdCancelCraftingSession] fires [CancelCraftingSessionIntent].
 *  4. This service resets craftingStage=0 and sends [CraftingSessionEnded].
 */
class CraftingSessionService : Service() {

    @IntentHandler
    private fun handleRequestCraftingSession(intent: RequestCraftingSessionIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != 0) return  // already in a session — ignore

        val schematics = playerObj.draftSchematics  // Map<Long (combinedCrc), Int>
        val size = schematics.size
        val serverCrcs = IntArray(size)     // high 32 bits of combinedCrc
        val clientCrcs = IntArray(size)     // low  32 bits of combinedCrc
        val subcategories = Array(size) { ByteArray(4) }

        schematics.keys.forEachIndexed { i, combinedCrc ->
            serverCrcs[i] = (combinedCrc ushr 32).toInt()
            clientCrcs[i] = combinedCrc.toInt()
        }

        // Set crafting state: stage 1 = tool open, no station (hand-crafting)
        playerObj.craftingStage = 1
        playerObj.nearbyCraftStation = 0L

        // Send the schematic list to the client
        player.sendPacket(
            MessageQueueDraftSchematics(
                intent.tool.objectId,   // toolId
                0L,                      // craftingStationId (0 = hand-crafting)
                size,
                serverCrcs,
                clientCrcs,
                subcategories
            )
        )
    }

    @IntentHandler
    private fun handleCancelCraftingSession(intent: CancelCraftingSessionIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage == 0) return  // not in a session — ignore

        // Reset crafting state
        playerObj.craftingStage = 0
        playerObj.nearbyCraftStation = 0L

        // Notify client that the session ended
        player.sendPacket(
            CraftingSessionEnded(
                player.creatureObject.objectId,  // playerId
                0,                               // sessionId
                0.toByte()                       // count
            )
        )
    }
}
