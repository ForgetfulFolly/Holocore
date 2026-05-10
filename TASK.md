# Task 407 -- PD7-G: Wire companion recruit command (currently stub)

Scope: /data/serverdata/Holocore/src/main/java/com/projectswg/holocore/services/support/global/admin/AdminBotService.kt
Worker-Role: code
Repo: /data/serverdata/Holocore (git pull + commit + push)

VERIFY:
  /qatool companion:recruit:bot_001
  Expected: "[BOT] Companion bot_001 recruited." (not the Phase D placeholder message)

---

## CONTEXT

File: AdminBotService.kt

Current handleCompanion() method (exact code):

    private fun handleCompanion(admin: CreatureObject, args: List<String>): Boolean {
        val subcommand = args.getOrElse(0) { "recruit" }

        when (subcommand) {
            "recruit" -> {
                sendMessage(admin, "[BOT] Companion wiring requires world-object support (Phase D). Usage: /companion recruit <bot_id>")
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
                        val released = botCompanionService.releaseCompanion(player, botIdArg)
                        if (released) sendMessage(admin, "[BOT] Companion $botIdArg released.")
                        else sendMessage(admin, "[BOT] No companion $botIdArg is assigned to you.")
                    }
                }
            }
            else -> {
                sendMessage(admin, "[BOT] Usage: /companion {recruit|release} <bot_id>")
                return false
            }
        }
        return true
    }

BotCompanionService (available via botCompanionService field) exposes:
    fun recruitCompanion(player: Player, botId: String): Boolean
    fun releaseCompanion(player: Player, botId: String): Boolean
    fun isCompanionAssigned(botId: String): Boolean

---

## SPEC

### Problem
The "recruit" branch of handleCompanion is a placeholder that never calls
botCompanionService.recruitCompanion(). The release branch already works correctly.
Phase D has shipped; the placeholder message is stale and misleading.

### Required fix
Replace the entire "recruit" branch:

BEFORE:
    "recruit" -> {
        sendMessage(admin, "[BOT] Companion wiring requires world-object support (Phase D). Usage: /companion recruit <bot_id>")
    }

AFTER:
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

No other changes to the file.

### Commit message
    Fix PD7-G: wire companion recruit command -- was placeholder, now calls recruitCompanion()

### Acceptance criteria
1. /qatool companion:recruit:bot_001 -> "[BOT] Companion bot_001 recruited."
2. /qatool companion:recruit:bot_001 (again, already owned) -> "[BOT] bot_001 already has an owner."
3. /qatool companion:release:bot_001 -> "[BOT] Companion bot_001 released."
4. ./gradlew test -- BUILD SUCCESSFUL
5. git diff main..HEAD --name-only shows AdminBotService.kt

Team: bravo
