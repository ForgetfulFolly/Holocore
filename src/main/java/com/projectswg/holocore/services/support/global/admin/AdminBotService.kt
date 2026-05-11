package com.projectswg.holocore.services.support.global.admin

import com.projectswg.holocore.resources.support.global.commands.ICmdCallback
import com.projectswg.holocore.resources.support.global.commands.ICommand
import com.projectswg.holocore.resources.support.global.commands.ICommandOwner
import com.projectswg.holocore.resources.support.global.commands.PlayerCmd
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject
import com.projectswg.holocore.services.gameplay.bots.BotCompanionService
import org.jetbrains.annotations.NotNull

class AdminBotService : ICmdCallback {

    private val botCompanionService = BotCompanionService()

    override fun initialize() {
        PlayerCmd.registerCommand("companion", this)
    }

    override fun terminate() {
        PlayerCmd.unregisterCommand("companion")
    }

    override fun getCommand(): ICommand {
        return object : ICommand {
            override fun getCommandName(): String {
                return "companion"
            }

            override fun getHelpMessage(): String {
                return "/companion <recruit/release>"
            }
        }
    }

    override fun execute(@NotNull admin: ICommandOwner, @NotNull args: List<String>) {
        if (args.size < 2) {
            sendMessage(admin, "[BOT] Usage: /companion <recruit/release>")
            return
        }

        val action = args[1].toLowerCase()
        when (action) {
            "recruit" -> {
                val botIdArg = args.getOrNull(1)
                if (botIdArg == null) {
                    sendMessage(admin, "[BOT] Usage: /companion recruit <bot_id>")
                } else {
                    val player = admin.owner
                    if (player == null) {
                        sendMessage(admin, "[BOT] Cannot recruit companion: no active player session.")
                    } else {
                        val ok = botCompanionService.recruitCompanion(player, botIdArg)
                        if (ok) sendMessage(admin, "[BOT] Companion $botIdArg recruited.")
                        else sendMessage(admin, "[BOT] $botIdArg already has an owner.")
                    }
                }
            }
            "release" -> {
                val botIdArg = args.getOrNull(1)
                if (botIdArg == null) {
                    sendMessage(admin, "[BOT] Usage: /companion release <bot_id>")
                } else {
                    val player = admin.owner
                    if (player == null) {
                        sendMessage(admin, "[BOT] Cannot release companion: no active player session.")
                    } else {
                        val ok = botCompanionService.releaseCompanion(player, botIdArg)
                        if (ok) sendMessage(admin, "[BOT] Companion $botIdArg released.")
                        else sendMessage(admin, "[BOT] $botIdArg is not owned by you.")
                    }
                }
            }
            else -> {
                sendMessage(admin, "[BOT] Unknown action: $action")
            }
        }
    }

    private fun sendMessage(owner: ICommandOwner, message: String) {
        if (owner is CreatureObject) {
            owner.sendSystemMessage(message)
        }
    }
}