/***********************************************************************************
 * Copyright (c) 2024 /// Project SWG /// www.projectswg.com                      *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.crafting.session

import com.projectswg.holocore.intents.gameplay.crafting.CancelCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.CraftExperimentIntent
import com.projectswg.holocore.intents.gameplay.crafting.CreatePrototypeIntent
import com.projectswg.holocore.intents.gameplay.crafting.NextCraftingStageIntent
import com.projectswg.holocore.intents.gameplay.crafting.RequestCraftingSessionIntent
import com.projectswg.holocore.intents.gameplay.crafting.SelectDraftSchematicIntent
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service
import me.joshlarson.jlcommon.log.Log

/**
 * Crafting session service stub pending NGE port (Phase D).
 * See docs/design/crafting/IMPLEMENTATION-PLAN.md.
 */
class CraftingSessionService : Service() {

    @IntentHandler
    private fun handleRequestCraftingSession(intent: RequestCraftingSessionIntent) {
        Log.i("[crafting] RequestCraftingSession from %s - NGE port pending (Phase D)", intent.player.username)
    }

    @IntentHandler
    private fun handleCancelCraftingSession(intent: CancelCraftingSessionIntent) {
    }

    @IntentHandler
    private fun handleSelectDraftSchematic(intent: SelectDraftSchematicIntent) {
    }

    @IntentHandler
    private fun handleNextCraftingStage(intent: NextCraftingStageIntent) {
    }

    @IntentHandler
    private fun handleCraftExperiment(intent: CraftExperimentIntent) {
    }

    @IntentHandler
    private fun handleCreatePrototype(intent: CreatePrototypeIntent) {
    }
}
