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
package com.projectswg.holocore.intents.gameplay.crafting

import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import me.joshlarson.jlcommon.control.Intent

/** Fired when a player activates a crafting tool to start a session. */
class RequestCraftingSessionIntent(val player: Player, val tool: SWGObject) : Intent()

/** Fired when a player cancels an in-progress crafting session. */
class CancelCraftingSessionIntent(val player: Player) : Intent()
