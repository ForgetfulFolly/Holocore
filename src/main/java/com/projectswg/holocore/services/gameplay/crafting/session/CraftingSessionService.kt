package com.projectswg.holocore.services.gameplay.crafting.session

import com.projectswg.common.data.schematic.DraftSchematic
import com.projectswg.common.network.packets.swg.zone.object_controller.CraftExperimentationResponse
import com.projectswg.common.network.packets.swg.zone.object_controller.CraftingSessionEnded
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueCraftIngredients
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueDraftSchematics
import com.projectswg.common.network.packets.swg.zone.object_controller.NextCraftingStageResult
import com.projectswg.holocore.intents.gameplay.crafting.CancelCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.CraftExperimentIntent
import com.projectswg.holocore.intents.gameplay.crafting.CreatePrototypeIntent
import com.projectswg.holocore.intents.gameplay.crafting.NextCraftingStageIntent
import com.projectswg.holocore.intents.gameplay.crafting.RequestCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.SelectDraftSchematicIntent
import com.projectswg.holocore.intents.gameplay.player.experience.ExperienceIntent
import com.projectswg.holocore.intents.support.objects.ObjectCreatedIntent
import com.projectswg.holocore.resources.support.data.server_info.loader.ServerData
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.ObjectCreator
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service

/**
 * Manages the full crafting session lifecycle (Phases 1-6).
 *
 * State machine:
 *   0 = no session
 *   1 = tool opened, schematic picker shown
 *   2 = schematic selected, ingredient UI shown
 *   4 = assembly complete, experimentation available
 */
class CraftingSessionService : Service() {

    /** Per-player session state: creature objectId -> crafted item template path. */
    private val sessionSchematics = mutableMapOf<Long, String>()

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 1 — Open tool / Cancel session
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleRequestCraftingSession(intent: RequestCraftingSessionIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != 0) return

        val schematics = playerObj.draftSchematics
        val size = schematics.size
        val serverCrcs = IntArray(size)
        val clientCrcs = IntArray(size)
        val subcategories = Array(size) { ByteArray(4) }

        schematics.keys.forEachIndexed { i, combinedCrc ->
            serverCrcs[i] = (combinedCrc ushr 32).toInt()
            clientCrcs[i] = combinedCrc.toInt()
        }

        playerObj.craftingStage = 1
        playerObj.nearbyCraftStation = 0L

        player.sendPacket(
            MessageQueueDraftSchematics(
                intent.tool.objectId,
                0L,
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
        if (playerObj.craftingStage == 0) return

        val playerId = player.creatureObject.objectId
        sessionSchematics.remove(playerId)
        playerObj.craftingStage = 0
        playerObj.nearbyCraftStation = 0L

        player.sendPacket(CraftingSessionEnded(playerId, 0, 0.toByte()))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 2 — Select schematic, show ingredient UI
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleSelectDraftSchematic(intent: SelectDraftSchematicIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != 1) return

        val requestedCrc = intent.schematicId
        val matchedCombinedCrc = playerObj.draftSchematics.keys.firstOrNull { combinedCrc ->
            combinedCrc.toInt() == requestedCrc ||
                    (combinedCrc ushr 32).toInt() == requestedCrc
        } ?: return

        val schematic: DraftSchematic = ServerData.draftSchematics.getAllSchematics()
            .firstOrNull { it.combinedCrc == matchedCombinedCrc } ?: return

        sessionSchematics[player.creatureObject.objectId] = schematic.craftedSharedTemplate

        playerObj.craftingStage = 2

        val slots = schematic.ingridientSlot
        val count = slots.size
        val names = Array(count) { "" }
        val types = ByteArray(count)
        val quantities = IntArray(count)

        slots.forEachIndexed { i, slot ->
            val option = slot.fromSlotDataOption.firstOrNull()
            if (option != null) {
                names[i] = option.ingredientName
                types[i] = option.slotType.id.toByte()
                quantities[i] = option.amount
            }
        }

        player.sendPacket(MessageQueueCraftIngredients(count, names, types, quantities))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 3 — Next crafting stage (advance to assembly)
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleNextCraftingStage(intent: NextCraftingStageIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != 2) return

        playerObj.craftingStage = 4
        player.sendPacket(NextCraftingStageResult(intent.counter, 0, 0.toByte()))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4-5 — Experimentation
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleCraftExperiment(intent: CraftExperimentIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != 4) return

        val totalSpent = intent.spentPoints.sum()
        playerObj.experimentPoints = (playerObj.experimentPoints - totalSpent).coerceAtLeast(0)

        // stringId: 1=great success, 2=success, 3=moderate, 4=failure
        val stringId = if (playerObj.craftingLevel >= 10) 1 else 2
        player.sendPacket(CraftExperimentationResponse(0, stringId, 0.toByte()))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 6 — Create prototype (create item, grant XP)
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleCreatePrototype(intent: CreatePrototypeIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != 4) return

        val playerId = player.creatureObject.objectId
        val templatePath = sessionSchematics.remove(playerId) ?: return

        val item = ObjectCreator.createObjectFromTemplate(templatePath)
        val inventory = player.creatureObject.getSlottedObject("inventory")
        if (inventory != null) {
            item.moveToContainer(inventory)
        }

        ObjectCreatedIntent(item).broadcast()

        val xpAmount = (10 + playerObj.craftingLevel * 5).coerceAtLeast(10)
        ExperienceIntent(player.creatureObject, null, "crafting_general", xpAmount, false).broadcast()

        playerObj.craftingStage = 0
        playerObj.nearbyCraftStation = 0L
        player.sendPacket(CraftingSessionEnded(playerId, 0, 0.toByte()))
    }
}
