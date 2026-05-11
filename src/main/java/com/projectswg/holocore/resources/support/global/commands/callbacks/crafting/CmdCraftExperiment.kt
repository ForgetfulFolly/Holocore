package com.projectswg.holocore.resources.support.global.commands.callbacks.crafting

import com.projectswg.holocore.intents.gameplay.crafting.CraftExperimentIntent
import com.projectswg.holocore.resources.support.global.commands.ICmdCallback
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject

class CmdCraftExperiment : ICmdCallback {
    override fun execute(player: Player, target: SWGObject?, args: String) {
        val parts = args.trim().split("\\s+".toRegex())
        val actionCounter = parts.getOrNull(0)?.toByte() ?: 0
        val statCount = parts.getOrNull(1)?.toInt() ?: 0
        val amounts = IntArray(statCount) { parts.getOrNull(2 + it * 2)?.toInt() ?: 1 }
        val spent = IntArray(statCount) { parts.getOrNull(3 + it * 2)?.toInt() ?: 1 }
        CraftExperimentIntent(player, actionCounter, statCount, amounts, spent).broadcast()
    }
}
