/***********************************************************************************
 * Copyright (c) 2026 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.bots.persistence

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.projectswg.holocore.services.gameplay.bots.model.BotMemory
import com.projectswg.holocore.services.gameplay.bots.model.BotProfile
import com.projectswg.holocore.services.gameplay.bots.model.BotSimulationTier
import com.projectswg.holocore.services.gameplay.bots.model.BotState
import com.projectswg.holocore.services.gameplay.bots.model.ConversationEntry
import me.joshlarson.jlcommon.log.Log
import org.bson.Document
import java.time.Instant

/**
 * MongoDB-backed implementation of [BotRepository].
 * Uses three collections: bot_profiles, bot_states, bot_memories.
 * Falls back gracefully — construct only when MongoDB is confirmed available.
 */
class MongoBotRepository(database: MongoDatabase) : BotRepository {

	private val profiles = database.getCollection("bot_profiles")
	private val states   = database.getCollection("bot_states")
	private val memories = database.getCollection("bot_memories")

	init {
		profiles.createIndex(Indexes.ascending("botId"),     IndexOptions().unique(true))
		profiles.createIndex(Indexes.ascending("homePlanet"), IndexOptions().unique(false))
		profiles.createIndex(Indexes.ascending("profession"), IndexOptions().unique(false))

		states.createIndex(Indexes.ascending("botId"),  IndexOptions().unique(true))
		states.createIndex(Indexes.ascending("tier"),   IndexOptions().unique(false))
		states.createIndex(Indexes.ascending("planet"), IndexOptions().unique(false))

		memories.createIndex(
			Indexes.compoundIndex(Indexes.ascending("botId"), Indexes.ascending("playerId")),
			IndexOptions().unique(true)
		)
		memories.createIndex(Indexes.ascending("playerId"), IndexOptions().unique(false))
		Log.i("MongoBotRepository: indexes created on bot_profiles, bot_states, bot_memories")
	}

	// ─── Profile operations ───────────────────────────────────────────────────

	override fun saveBotProfile(profile: BotProfile): Boolean {
		profiles.replaceOne(
			Filters.eq("botId", profile.botId),
			profileToDoc(profile),
			ReplaceOptions().upsert(true)
		)
		return true
	}

	override fun loadBotProfile(botId: String): BotProfile? =
		profiles.find(Filters.eq("botId", botId)).first()?.let { docToProfile(it) }

	override fun getAllBotProfiles(): List<BotProfile> =
		profiles.find().map { docToProfile(it) }.toList()

	override fun deleteBotProfile(botId: String): Boolean {
		profiles.deleteOne(Filters.eq("botId", botId))
		states.deleteOne(Filters.eq("botId", botId))
		memories.deleteMany(Filters.eq("botId", botId))
		return true
	}

	// ─── State operations ─────────────────────────────────────────────────────

	override fun saveBotState(state: BotState): Boolean {
		states.replaceOne(
			Filters.eq("botId", state.botId),
			stateToDoc(state),
			ReplaceOptions().upsert(true)
		)
		return true
	}

	override fun loadBotState(botId: String): BotState? =
		states.find(Filters.eq("botId", botId)).first()?.let { docToState(it) }

	override fun updateBotState(botId: String, state: BotState): Boolean = saveBotState(state)

	// ─── Memory operations ────────────────────────────────────────────────────

	override fun saveBotMemory(memory: BotMemory): Boolean {
		memories.replaceOne(
			Filters.and(Filters.eq("botId", memory.botId), Filters.eq("playerId", memory.playerId)),
			memoryToDoc(memory),
			ReplaceOptions().upsert(true)
		)
		return true
	}

	override fun loadBotMemory(botId: String, playerId: Long): BotMemory? =
		memories.find(
			Filters.and(Filters.eq("botId", botId), Filters.eq("playerId", playerId))
		).first()?.let { docToMemory(it) }

	override fun getAllBotMemoryForBot(botId: String): List<BotMemory> =
		memories.find(Filters.eq("botId", botId)).map { docToMemory(it) }.toList()

	override fun getAllBotMemoryForPlayer(playerId: Long): List<BotMemory> =
		memories.find(Filters.eq("playerId", playerId)).map { docToMemory(it) }.toList()

	override fun deleteBotMemory(botId: String, playerId: Long): Boolean {
		memories.deleteOne(
			Filters.and(Filters.eq("botId", botId), Filters.eq("playerId", playerId))
		)
		return true
	}

	// ─── Batch operations ─────────────────────────────────────────────────────

	override fun saveBotProfiles(profileList: List<BotProfile>): Boolean {
		profileList.forEach { saveBotProfile(it) }
		return true
	}

	override fun saveBotStates(stateList: List<BotState>): Boolean {
		stateList.forEach { saveBotState(it) }
		return true
	}

	// ─── Query operations ─────────────────────────────────────────────────────

	override fun getBotsByPlanet(planet: String): List<BotProfile> =
		profiles.find(Filters.eq("homePlanet", planet)).map { docToProfile(it) }.toList()

	override fun getBotsByProfession(profession: String): List<BotProfile> =
		profiles.find(Filters.eq("profession", profession)).map { docToProfile(it) }.toList()

	override fun getExpiredMemories(beforeEpochSeconds: Long): List<BotMemory> {
		val cutoffMs = beforeEpochSeconds * 1_000L
		return memories.find(Filters.lt("expiresAt", cutoffMs)).map { docToMemory(it) }.toList()
	}

	// ─── Administrative ───────────────────────────────────────────────────────

	override fun clearAllBots(): Int {
		val count = profiles.countDocuments().toInt()
		profiles.drop()
		states.drop()
		return count
	}

	override fun clearAllBotMemory(): Int {
		val count = memories.countDocuments().toInt()
		memories.drop()
		return count
	}

	// ─── Serialization ────────────────────────────────────────────────────────

	private fun profileToDoc(p: BotProfile): Document = Document()
		.append("botId",           p.botId)
		.append("name",            p.name)
		.append("species",         p.species)
		.append("profession",      p.profession)
		.append("faction",         p.faction)
		.append("homePlanet",      p.homePlanet)
		.append("personalityTags", p.personalityTags)
		.append("speechStyle",     p.speechStyle)

	private fun docToProfile(d: Document): BotProfile = BotProfile(
		botId           = d.getString("botId"),
		name            = d.getString("name"),
		species         = d.getString("species"),
		profession      = d.getString("profession"),
		faction         = d.getString("faction"),
		homePlanet      = d.getString("homePlanet"),
		personalityTags = stringList(d, "personalityTags"),
		speechStyle     = d.getString("speechStyle") ?: "neutral",
	)

	private fun stateToDoc(s: BotState): Document = Document()
		.append("botId",    s.botId)
		.append("tier",     s.tier.name)
		.append("activity", s.activity)
		.append("planet",   s.planet)
		.append("groupId",  s.groupId)
		.append("mood",     s.mood)

	private fun docToState(d: Document): BotState = BotState(
		botId    = d.getString("botId"),
		tier     = BotSimulationTier.valueOf(d.getString("tier") ?: BotSimulationTier.DIRECTORY.name),
		activity = d.getString("activity") ?: "idle",
		planet   = d.getString("planet")   ?: "",
		groupId  = d.getLong("groupId")    ?: 0L,
		mood     = d.getString("mood")     ?: "neutral",
	)

	private fun memoryToDoc(m: BotMemory): Document {
		val history = m.conversationHistory.map { e ->
			Document()
				.append("timestamp",     e.timestamp.toEpochMilli())
				.append("playerMessage", e.playerMessage)
				.append("botReply",      e.botReply)
				.append("intent",        e.intent)
		}
		return Document()
			.append("botId",               m.botId)
			.append("playerId",            m.playerId)
			.append("playerName",          m.playerName)
			.append("affinityScore",       m.affinityScore)
			.append("lastInteractionTime", m.lastInteractionTime.toEpochMilli())
			.append("interactionCount",    m.interactionCount)
			.append("conversationHistory", history)
			.append("notes",               m.notes)
			.append("tags",               m.tags)
			.append("expiresAt",           m.expiresAt?.toEpochMilli())
	}

	@Suppress("UNCHECKED_CAST")
	private fun docToMemory(d: Document): BotMemory {
		val historyDocs = (d["conversationHistory"] as? List<*>)
			?.filterIsInstance<Document>() ?: emptyList()
		val history = historyDocs.map { e ->
			ConversationEntry(
				timestamp     = Instant.ofEpochMilli(e.getLong("timestamp") ?: 0L),
				playerMessage = e.getString("playerMessage") ?: "",
				botReply      = e.getString("botReply")      ?: "",
				intent        = e.getString("intent")        ?: "general",
			)
		}
		val expiresAtMs = d.getLong("expiresAt")
		return BotMemory(
			botId               = d.getString("botId"),
			playerId            = d.getLong("playerId")         ?: 0L,
			playerName          = d.getString("playerName")     ?: "",
			affinityScore       = d.getInteger("affinityScore") ?: 0,
			lastInteractionTime = Instant.ofEpochMilli(d.getLong("lastInteractionTime") ?: System.currentTimeMillis()),
			interactionCount    = d.getInteger("interactionCount") ?: 0,
			conversationHistory = history.toMutableList(),
			notes               = d.getString("notes") ?: "",
			tags                = stringList(d, "tags").toMutableList(),
			expiresAt           = if (expiresAtMs != null) Instant.ofEpochMilli(expiresAtMs) else null,
		)
	}

	@Suppress("UNCHECKED_CAST")
	private fun stringList(d: Document, key: String): List<String> =
		(d[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}
