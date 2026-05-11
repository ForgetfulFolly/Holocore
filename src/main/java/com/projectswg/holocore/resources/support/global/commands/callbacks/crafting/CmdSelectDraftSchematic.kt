package com.projectswg.holocore.resources.support.global.commands.callbacks.crafting

import com.projectswg.holocore.intents.gameplay.crafting.SelectDraftSchematicIntent
import com.projectswg.holocore.resources.support.data.server_info.StandardLog
import com.projectswg.holocore.resources.support.global.commands.ICmdCallback
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject

class CmdSelectDraftSchematic : ICmdCallback {
    override fun execute(player: Player, target: SWGObject?, args: String) {
        StandardLog.onPlayerTrace(this, player, "selectDraftSchematic cpp callback fired, args=%s", args)
        val schematicId = args.trim().toIntOrNull() ?: return
        SelectDraftSchematicIntent(player, schematicId).broadcast()
    }
}