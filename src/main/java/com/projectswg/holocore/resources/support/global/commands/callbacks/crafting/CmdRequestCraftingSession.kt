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
package com.projectswg.holocore.resources.support.global.commands.callbacks.crafting

import com.projectswg.holocore.intents.gameplay.crafting.RequestCraftingSessionIntent
import com.projectswg.holocore.resources.support.global.commands.ICmdCallback
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject

/**
 * Callback for the client-side "requestCraftingSession" cpp command.
 * The target must be the crafting tool the player is activating.
 */
class CmdRequestCraftingSession : ICmdCallback {
    override fun execute(player: Player, target: SWGObject?, args: String) {
        val tool = target ?: return  // no tool targeted — silently ignore
        RequestCraftingSessionIntent(player, tool).broadcast()
    }
}
