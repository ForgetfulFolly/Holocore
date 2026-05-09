package com.projectswg.holocore.services.gameplay.bots.model

/**
 * Bot seed data generator for Phase 1 testing.
 * Creates 50 social bots for Mos Eisley (Tatooine).
 */
object BotSeedData {

	private val firstNames = listOf(
		"Kael", "Lyra", "Shan", "Mara", "Tycho", "Carth", "Bastila", "Mission",
		"Vax", "Nira", "Rax", "Vera", "Korben", "Aria", "Zax", "Cara",
		"Lorn", "Kestra", "Dren", "Jisa", "Thorne", "Ayla", "Vex", "Lira",
		"Kren", "Sera", "Tyr", "Vera", "Xan", "Rella", "Vorn", "Kara",
		"Sax", "Mira", "Dern", "Petra", "Ryn", "Lena", "Kaix", "Rin",
		"Ven", "Tara", "Xer", "Nara", "Torr", "Sera", "Kar", "Vale",
		"Lix", "Nova"
	)

	private val lastNames = listOf(
		"Sunstrider", "Bloodfist", "Darkheart", "Starfury", "Shadowblade",
		"Ironhand", "Stormborn", "Nightshade", "Fireborn", "Silvanus",
		"Redmane", "Goldleaf", "Stonewall", "Wildborne", "Swiftbow",
		"Dragonborn", "Windrunner", "Starseeker", "Moonlight", "Ravencroft",
		"Thornheart", "Swiftblade", "Frostbane", "Sharpshooter", "Trueblade",
		"Wanderer", "Guardian", "Sentinel", "Vanguard", "Zealot"
	)

	private val professions = listOf(
		"Combat Medic",
		"Smuggler",
		"Bounty Hunter",
		"Commando",
		"Rifleman",
		"Sniper",
		"Scout",
		"Ranger",
		"Soldier"
	)

	private val species = listOf(
		"Human",
		"Zabrak",
		"Twi'lek",
		"Mon Calamari",
		"Wookiee",
		"Ithorian",
		"Trandoshan",
		"Sullustan",
		"Rodian"
	)

	private val factions = listOf("Rebel", "Empire", "Neutral")

	private val personalityTags = listOf(
		"friendly", "gruff", "sarcastic", "mysterious", "cheerful",
		"brooding", "energetic", "calm", "adventurous", "cautious"
	)

	/**
	 * Generate 50 random bot profiles for testing.
	 */
	fun generatePhase1Bots(count: Int = 50, zone: String = "tatooine"): List<BotProfile> {
		val bots = mutableListOf<BotProfile>()
		val random = java.util.Random(42) // Fixed seed for reproducibility

		repeat(count) { index ->
			val botId = "bot_${String.format("%03d", index + 1)}"
			val firstName = firstNames[random.nextInt(firstNames.size)]
			val lastName = lastNames[random.nextInt(lastNames.size)]
			val name = "$firstName $lastName"
			val species = species[random.nextInt(species.size)]
			val profession = professions[random.nextInt(professions.size)]
			val faction = factions[random.nextInt(factions.size)]
			val tags = listOf(personalityTags[random.nextInt(personalityTags.size)])

			bots.add(
				BotProfile(
					botId = botId,
					name = name,
					species = species,
					profession = profession,
					faction = faction,
					homePlanet = zone,
					personalityTags = tags,
					speechStyle = "neutral"
				)
			)
		}

		return bots
	}

	/**
	 * Get sample bot names for debugging.
	 */
	fun getSampleBotNames(count: Int = 5): List<String> {
		return generatePhase1Bots(count).map { it.name }
	}
}
