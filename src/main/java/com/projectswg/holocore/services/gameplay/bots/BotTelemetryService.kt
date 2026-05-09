package com.projectswg.holocore.services.gameplay.bots

import me.joshlarson.jlcommon.control.Service
import java.util.concurrent.atomic.AtomicLong

class BotTelemetryService : Service() {

	companion object {
		@Volatile
		var instance: BotTelemetryService? = null
			private set
	}

	override fun start(): Boolean {
		instance = this
		return super.start()
	}

	override fun stop(): Boolean {
		if (instance === this) {
			instance = null
		}
		return super.stop()
	}

	private val promotions = AtomicLong(0)
	private val demotions = AtomicLong(0)
	private val tellsHandled = AtomicLong(0)

	fun onPromotion() {
		promotions.incrementAndGet()
	}

	fun onDemotion() {
		demotions.incrementAndGet()
	}

	fun onTellHandled() {
		tellsHandled.incrementAndGet()
	}

	fun snapshot(): BotTelemetrySnapshot {
		return BotTelemetrySnapshot(
			promotions = promotions.get(),
			demotions = demotions.get(),
			tellsHandled = tellsHandled.get(),
		)
	}
}

data class BotTelemetrySnapshot(
	val promotions: Long,
	val demotions: Long,
	val tellsHandled: Long,
)
