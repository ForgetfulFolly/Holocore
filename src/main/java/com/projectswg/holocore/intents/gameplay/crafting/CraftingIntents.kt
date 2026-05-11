package com.projectswg.holocore.intents.gameplay.crafting

import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import me.joshlarson.jlcommon.control.Intent

/** Phase 1: Player activates a crafting tool to start a session. */
class RequestCraftingSessionIntent(val player: Player, val tool: SWGObject) : Intent()

/** Phase 1: Player cancels an in-progress crafting session. */
class CancelCraftingSessionIntent(val player: Player) : Intent()

/** Phase 2: Player selects a draft schematic from the schematic picker. */
class SelectDraftSchematicIntent(val player: Player, val schematicId: Int) : Intent()

/** Phase 3: Player advances to the assembly stage (fills all ingredients). */
class NextCraftingStageIntent(val player: Player, val counter: Int) : Intent()

/** Phase 4-5: Player spends experimentation points on schematic properties. */
class CraftExperimentIntent(
    val player: Player,
    val actionCounter: Byte,
    val statCount: Int,
    val statAmounts: IntArray,
    val spentPoints: IntArray
) : Intent()

/** Phase 6: Player finalises the crafting session and creates a prototype. */
class CreatePrototypeIntent(val player: Player, val counter: Int) : Intent()
