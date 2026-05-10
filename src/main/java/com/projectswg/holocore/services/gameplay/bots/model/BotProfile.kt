package com.projectswg.holocore.services.gameplay.bots.model

data class BotProfile(
	val botId: String,
	val name: String,
	val species: String,
	val profession: String,
	val faction: String,
	val homePlanet: String,
	val personalityTags: List<String> = emptyList(),
	val speechStyle: String = "neutral",
	val npcId: String = "",
)
