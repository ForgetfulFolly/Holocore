# PD7-G: Wire companion recruit command (currently a stub)
## Worker-Role: code
## Desk: 3
## Scope: src/main/java/com/projectswg/holocore/services/support/global/admin/AdminBotService.kt (EDIT ALLOWED)

## Description
The "recruit" branch of handleCompanion() is a placeholder that never calls
botCompanionService.recruitCompanion(). Phase D shipped BotCompanionService. Replace the
placeholder stub with a real implementation mirroring the already-working "release" branch.

## File Changes
| Action | Path | Description |
|--------|------|-------------|
| MODIFY | src/main/java/com/projectswg/holocore/services/support/global/admin/AdminBotService.kt | Replace placeholder recruit branch in handleCompanion() with real botCompanionService.recruitCompanion() call |

## Context files
- src/main/java/com/projectswg/holocore/services/support/global/admin/AdminBotService.kt

## Embedded context

Current "recruit" branch (the ONLY thing to change):

    "recruit" -> {
        sendMessage(admin, "[BOT] Companion wiring requires world-object support (Phase D). Usage: /companion recruit <bot_id>")
    }

Replace it with this implementation (mirrors the "release" branch structure):

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

The "release" branch is already correct — do NOT touch it.
botCompanionService field is already available in the class.

## Constraints
- Change ONLY the "recruit" branch of the when block inside handleCompanion()
- Do NOT touch the "release" branch, "else" branch, or any other method
- No new imports needed — botCompanionService is already a field

## Acceptance
- [ ] "recruit" branch calls botCompanionService.recruitCompanion(player, botIdArg)
- [ ] Null checks for botIdArg and player are present
- [ ] "release" branch is unchanged
- [ ] git diff main..HEAD --name-only shows only AdminBotService.kt

## Commit message
Fix PD7-G: wire companion recruit command -- was placeholder, now calls recruitCompanion()

Team: alpha
Worker-Role: team-alpha-code
Retry-Count: 0
Status: implement-in-progress
