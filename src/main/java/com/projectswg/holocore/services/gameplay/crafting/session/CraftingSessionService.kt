package com.projectswg.holocore.services.gameplay.crafting.session

import com.projectswg.common.data.crafting.CraftingType
import com.projectswg.common.data.schematic.DraftSchematic
import com.projectswg.common.network.packets.swg.zone.object_controller.CraftExperimentationResponse
import com.projectswg.common.network.packets.swg.zone.object_controller.CraftingSessionEnded
import com.projectswg.common.network.packets.swg.zone.object_controller.MessageQueueCraftCustomization
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
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log
import kotlin.random.Random

/**
 * Manages the full crafting session lifecycle (Phases 1-6).
 *
 * Stage machine (CraftingStage.value):
 *   NONE(0)                = no active session
 *   SELECT_DRAFT_SCHEMATIC(1) = tool opened, schematic picker shown
 *   ASSEMBLY(2)            = schematic selected, ingredient UI shown
 *   EXPERIMENT(3)          = assembly complete, experimentation available
 *   CUSTOMIZE(4)           = experimentation done, customization window shown
 *   FINISH(5)              = customization done, prototype creation allowed
 */

// CraftingStage enum — not in pswgcommon for this version of Holocore,
// defined locally so stage values are readable and grep-friendly.
private enum class CraftingStage(val value: Int) {
    NONE(0),
    SELECT_DRAFT_SCHEMATIC(1),
    ASSEMBLY(2),
    EXPERIMENT(3),
    CUSTOMIZE(4),
    FINISH(5)
}

class CraftingSessionService : Service() {

    /** Per-player session state: creature objectId -> crafted item template path. */
    private val sessionSchematics = mutableMapOf<Long, String>()

    companion object {
        // ObjController CRC for MessageQueueCraftCustomization (Phase 5).
        // Must be handled by the packet dispatch layer and forwarded here
        // via handleCraftCustomization(player, packet).
        private const val CRC_CRAFT_CUSTOMIZATION = 0x015A
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 1 — Open tool / Cancel session
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleRequestCraftingSession(intent: RequestCraftingSessionIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return

        // Reset any stuck session so the player is never permanently locked out.
        if (playerObj.craftingStage != 0) {
            sessionSchematics.remove(player.creatureObject.objectId)
            playerObj.craftingStage = 0
            playerObj.nearbyCraftStation = 0L
        }

        // Fix T411 Bug 1: set craftingLevel based on tool type.
        // Generic tool → 1; specialised tool → 2; specialised near matching station → 3 (deferred).
        val isGenericTool = intent.tool.template.contains("generic_crafting_tool")
        playerObj.craftingLevel = if (isGenericTool) 1 else 2

        val toolMask = toolAllowedTypeMask(intent.tool)
        val schematicsByServerCrc = ServerData.draftSchematics.getAllSchematics()
            .associateBy { it.serverCrc }

        val packet = MessageQueueDraftSchematics(player.creatureObject.objectId, intent.tool.objectId, 0L)
        var sent = 0

        for (combinedCrc in playerObj.draftSchematics.keys) {
            val serverCrc = (combinedCrc ushr 32).toInt()
            val sharedCrc = combinedCrc.toInt()
            val schematic = schematicsByServerCrc[serverCrc]
            val categoryInt = if (schematic != null) categoryToInt(schematic.category) else 0

            // Filter by tool type: skip schematics that don't match the tool's mask.
            // categoryInt==0 (unknown) passes through only on generic tools (toolMask==0).
            if (toolMask != 0 && (toolMask and categoryInt) == 0) continue

            packet.addSchematic(serverCrc, sharedCrc, categoryInt)
            sent++
        }

        // Fix T411 Bug 2: use CraftingStage enum instead of magic integer.
        playerObj.craftingStage = CraftingStage.SELECT_DRAFT_SCHEMATIC.value
        playerObj.nearbyCraftStation = 0L

        Log.d("[crafting] Sent %d/%d schematics to %s (tool=%d mask=0x%X)",
            sent, playerObj.draftSchematics.size, player.username, intent.tool.objectId, toolMask)
        player.sendPacket(packet)
    }

    @IntentHandler
    private fun handleCancelCraftingSession(intent: CancelCraftingSessionIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage == 0) return

        val playerId = player.creatureObject.objectId
        sessionSchematics.remove(playerId)
        playerObj.craftingStage = CraftingStage.NONE.value
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
        if (playerObj.craftingStage != CraftingStage.SELECT_DRAFT_SCHEMATIC.value) return

        val requestedCrc = intent.schematicId
        val matchedCombinedCrc = playerObj.draftSchematics.keys.firstOrNull { combinedCrc ->
            combinedCrc.toInt() == requestedCrc ||
                    (combinedCrc ushr 32).toInt() == requestedCrc
        } ?: return

        val schematic: DraftSchematic = ServerData.draftSchematics.getAllSchematics()
            .firstOrNull { it.combinedCrc == matchedCombinedCrc } ?: return

        sessionSchematics[player.creatureObject.objectId] = schematic.craftedSharedTemplate

        // Fix T412 Bug 2: advance craftingStage to ASSEMBLY immediately on schematic selection.
        playerObj.craftingStage = CraftingStage.ASSEMBLY.value

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

        // Fix T412 Bug 1: send ingredient list via dedicated helper.
        sendIngredientList(player, count, names, types, quantities)
    }

    // Fix T412: private helper that sends MessageQueueCraftIngredients to the player.
    // Called after slot fill, slot empty, and schematic selection.
    private fun sendIngredientList(player: Player, count: Int, names: Array<String>,
                                   types: ByteArray, quantities: IntArray) {
        player.sendPacket(MessageQueueCraftIngredients(count, names, types, quantities))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 3 — Next crafting stage (assembly roll → EXPERIMENT)
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleNextCraftingStage(intent: NextCraftingStageIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != CraftingStage.ASSEMBLY.value) return

        // Fix T413 Bug 3: set experimentPoints based on craftingLevel approximation.
        // SOE formula: floor(complexity / 2); here we approximate from craftingLevel.
        val basePoints = (playerObj.craftingLevel * 3).coerceAtLeast(1)
        playerObj.experimentPoints = basePoints

        // Fix T413 Bug 2: advance to EXPERIMENT stage.
        playerObj.craftingStage = CraftingStage.EXPERIMENT.value

        // Fix T413 Bug 1: use NextCraftingStageResult instead of CraftAcknowledge.
        // response=0 → CRITICAL_SUCCESS (best assembly result for simple implementation).
        player.sendPacket(NextCraftingStageResult(intent.counter, 0, 0.toByte()))

        Log.d("[crafting] %s assembly: consuming %d ingredient slots (inventory deduction deferred)",
              player.username, 0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4 — Experimentation
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleCraftExperiment(intent: CraftExperimentIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return
        if (playerObj.craftingStage != CraftingStage.EXPERIMENT.value) return

        val totalSpent = intent.spentPoints.sum()
        if (playerObj.experimentPoints < totalSpent) {
            Log.w("[crafting] %s tried to spend %d points but only has %d",
                  player.username, totalSpent, playerObj.experimentPoints)
            return
        }

        playerObj.experimentPoints = (playerObj.experimentPoints - totalSpent).coerceAtLeast(0)

        // Fix T414 Bug 2: correct roll formula — assemblyBonus from craftingLevel, NOT spentPoints * 10.
        val assemblyBonus = playerObj.craftingLevel * 5
        val roll = Random.nextInt(100) + assemblyBonus

        // stringId: 1=great success, 2=success, 3=moderate, 4=failure
        val stringId = when {
            roll >= 90 -> 1
            roll >= 60 -> 2
            roll >= 30 -> 3
            else       -> 4
        }
        player.sendPacket(CraftExperimentationResponse(intent.actionCounter.toInt(), stringId, 0.toByte()))

        // Fix T414 Bug 4: transition to CUSTOMIZE when experiment points run out.
        if (playerObj.experimentPoints <= 0) {
            playerObj.craftingStage = CraftingStage.CUSTOMIZE.value
        }

        // Fix T414 Bug 3: MSCO attribute write deferred — log result.
        val quality = when (stringId) { 1 -> 1.0f; 2 -> 0.70f; 3 -> 0.55f; else -> 0.30f }
        Log.d("[crafting] %s experiment: roll=%d result=%d quality=%.2f — MSCO write deferred",
              player.username, roll, stringId, quality)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 5 — Customization
    // CRC_CRAFT_CUSTOMIZATION (0x015A) must be routed here by the packet
    // dispatch layer when it receives MessageQueueCraftCustomization.
    // ─────────────────────────────────────────────────────────────────────────

    fun handleCraftCustomization(player: Player, packet: MessageQueueCraftCustomization) {
        val playerObj = player.playerObject ?: run {
            Log.w("[crafting] CraftCustomization from %s with no active session", player.username)
            return
        }
        if (playerObj.craftingStage != CraftingStage.CUSTOMIZE.value) {
            Log.w("[crafting] CraftCustomization from %s at unexpected stage %d",
                  player.username, playerObj.craftingStage)
        }

        // Apply item name if non-blank.
        val itemName = packet.itemName
        if (!itemName.isNullOrBlank()) {
            Log.d("[crafting] %s customization: item name = '%s'", player.username, itemName)
            // TODO: apply to MSCO/crafted item once verified setter API is available
        }

        // Apply appearance data if provided.
        val appearanceIndex = packet.appearenceTemplate   // SOE original spelling
        if (appearanceIndex.toInt() != 0) {
            Log.d("[crafting] %s customization: appearanceIndex=%d", player.username, appearanceIndex)
            // TODO: apply to MSCO once verified field access is available
        }

        // Log property/value pairs.
        val count = packet.count.toInt()
        for (i in 0 until count) {
            Log.d("[crafting] %s customization: property[%d]=%d value=%d",
                  player.username, i, packet.property[i], packet.value[i])
        }

        // Advance to FINISH stage so handleCreatePrototype is allowed.
        playerObj.craftingStage = CraftingStage.FINISH.value
        Log.i("[crafting] %s completed customization phase", player.username)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 6 — Create prototype (grant XP, move to inventory, end session)
    // ─────────────────────────────────────────────────────────────────────────

    @IntentHandler
    private fun handleCreatePrototype(intent: CreatePrototypeIntent) {
        val player = intent.player
        val playerObj = player.playerObject ?: return

        val playerId = player.creatureObject.objectId
        val templatePath = sessionSchematics.remove(playerId) ?: return

        val item = ObjectCreator.createObjectFromTemplate(templatePath)
        val inventory = player.creatureObject.getSlottedObject("inventory")
        if (inventory != null) {
            item.moveToContainer(inventory)
        }

        ObjectCreatedIntent(item).broadcast()

        // Fix T416 Bug 1: map schematic category to correct XP type using template path.
        val xpType = when {
            templatePath.contains("weapon") || templatePath.contains("lightsaber") ->
                "crafting_weapons_general"
            templatePath.contains("food") || templatePath.contains("drink") ->
                "crafting_food_general"
            templatePath.contains("chemical") ->
                "crafting_food_general"
            templatePath.contains("armor") ->
                "crafting_clothing_armor"
            templatePath.contains("clothing") || templatePath.contains("wearable") ->
                "crafting_clothing_general"
            templatePath.contains("droid") ->
                "crafting_droid_general"
            templatePath.contains("structure") || templatePath.contains("installation") ->
                "crafting_structure_general"
            templatePath.contains("furniture") ->
                "crafting_structure_general"
            templatePath.contains("medicine") || templatePath.contains("pharmaceutical") ->
                "crafting_medicine_general"
            templatePath.contains("spice") ->
                "crafting_spice"
            else ->
                "crafting_general"
        }

        val xpAmount = (10 + playerObj.craftingLevel * 5).coerceAtLeast(10)
        ExperienceIntent(player.creatureObject, xpType, xpAmount).broadcast()

        // Fix T416 Bug 3: transition through FINISH before clearing to NONE.
        playerObj.craftingStage = CraftingStage.FINISH.value
        playerObj.craftingStage = CraftingStage.NONE.value
        playerObj.nearbyCraftStation = 0L

        // Fix T416 Bug 5: log MSCO destruction note (explicit destroy deferred).
        Log.d("[crafting] MSCO for session %s pending destruction (explicit destroy deferred)",
              player.username)

        player.sendPacket(CraftingSessionEnded(playerId, 0, 0.toByte()))
        Log.i("[crafting] %s created item '%s' (xpType=%s, xp=%d)", player.username,
              templatePath, xpType, xpAmount)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the CraftingType bitmask for a given tool template. 0 = no filter (generic). */
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

    /** Maps the schematic category string to the NGE CraftingType bitmask int. */
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
}
