package com.projectswg.holocore.services.gameplay.bots.model

data class BotState(
	val botId: String,
	var tier: BotSimulationTier = BotSimulationTier.DIRECTORY,
	var activity: String = "idle",
	var planet: String,
	var groupId: Long = 0,
	var mood: String = "neutral",
	var x: Double = 0.0,
	var y: Double = 0.0,
	var z: Double = 0.0,
	var heading: Float = 0f,
)
