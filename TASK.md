# PD7-G: Wire companion recruit command (currently a stub)
## Worker-Role: code
## Desk: 3
## Scope: src/main/java/com/projectswg/holocore/services/support/global/admin/AdminBotService.kt (EDIT ALLOWED)

## Description
The "recruit" branch of handleCompanion() is a placeholder. Replace it with a real call to botCompanionService.recruitCompanion().

## File Changes
| Action | Path | Description |
|--------|------|-------------|
| MODIFY | src/main/java/com/projectswg/holocore/services/support/global/admin/AdminBotService.kt | Replace placeholder recruit stub in handleCompanion() with real botCompanionService.recruitCompanion() call |

## Embedded context

Find this code in handleCompanion():

    "recruit" -> {
        sendMessage(admin, "[BOT] Companion wiring requires world-object support (Phase D). Usage: /companion recruit <bot_id>")
    }

Replace ONLY that branch with:

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

Do NOT change the "release" branch or any other code.

## Constraints
- Change ONLY the "recruit" branch of the when block
- No new imports needed

## Acceptance
- [ ] "recruit" branch calls botCompanionService.recruitCompanion(player, botIdArg)
- [ ] "release" branch is unchanged

## Commit message
Fix PD7-G: wire companion recruit command

Team: alpha
Worker-Role: team-alpha-code
Retry-Count: 0
Status: implement-in-progress
