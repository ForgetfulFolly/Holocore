package com.projectswg.holocore.services.gameplay.bots.model

data class BotState(
	val botId: String,
	var tier: BotSimulationTier = BotSimulationTier.DIRECTORY,
	var activity: String = "idle",
	var planet: String,
	var groupId: Long = 0,
	var mood: String = "neutral",
)
