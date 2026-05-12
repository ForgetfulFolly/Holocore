/***********************************************************************************
 * Copyright (c) 2026 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.structures.housing

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of admins/guilds for structures spawned via /qatool deed.
 * Keyed by building objectId. Entries are arbitrary strings tagged with their
 * kind (PLAYER name or GUILD name/abbreviation).
 *
 * Storage-only for now — no entry/drop permission gating is wired up yet. This
 * matches the current scope where /qatool-spawned buildings don't persist
 * across server restart anyway. When persistence is added, this should move
 * into PlayerStructureInfo (which is MongoPersistable) or a sibling document.
 */
object StructureAdminRegistry {

	enum class EntryKind { PLAYER, GUILD }

	data class Entry(val kind: EntryKind, val name: String)

	private val byBuilding: MutableMap<Long, MutableSet<Entry>> = ConcurrentHashMap()

	fun add(buildingId: Long, kind: EntryKind, name: String): Boolean {
		val trimmed = name.trim()
		if (trimmed.isEmpty()) return false
		val set = byBuilding.computeIfAbsent(buildingId) { ConcurrentHashMap.newKeySet() }
		return set.add(Entry(kind, trimmed))
	}

	fun remove(buildingId: Long, entry: Entry): Boolean {
		val set = byBuilding[buildingId] ?: return false
		val removed = set.remove(entry)
		if (set.isEmpty()) byBuilding.remove(buildingId)
		return removed
	}

	fun list(buildingId: Long): List<Entry> {
		val set = byBuilding[buildingId] ?: return emptyList()
		return set.sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))
	}

	fun clear(buildingId: Long) {
		byBuilding.remove(buildingId)
	}
}
