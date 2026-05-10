package com.projectswg.holocore.services.gameplay.bots

import me.joshlarson.jlcommon.control.Manager
import me.joshlarson.jlcommon.control.ManagerStructure

@ManagerStructure(children = [BotPopulationService::class, BotDialogueService::class, BotCompanionService::class, BotTelemetryService::class, BotWorldSpawnService::class])
class BotManager : Manager()
