## Existing Command Table Crafting Entries

The following entries are found in the command table related to crafting:

- `requestcraftingsession`
- `selectdraftschematic`
- `nextcraftingstage`
- `cancelcraftingsession`
- `createprototype`

## Existing Command Callback Registrations

The following command callbacks are registered for crafting actions:

- `CmdRequestCraftingSession`
- `CmdSelectDraftSchematic`
- `CmdNextCraftingStage`
- `CmdCancelCraftingSession`
- `CmdCreatePrototype`

These callbacks are typically registered in the `AdminBotService` or similar command registration services.

## Existing OC Packet Handler Registrations for Crafting CRCs

The following OC packet handlers are registered for crafting CRCs:

- `MessageQueueCraftRequestSession.java` (CRC 0x010F)
- `MessageQueueCraftSelectSchematic.java` (CRC 0x010E)
- `NextCraftingStage.java` (CRC 0x0109)

These handlers are located in the `pswgcommon` directory under `/datalake-vm/workspaces/Holocore/pswgcommon/`.

## Routing Decision Matrix

| Crafting Action            | Slash Command | OC Packet Handler | Recommendation          |
|----------------------------|---------------|-------------------|-------------------------|
| Request Crafting Session   | Yes           | Yes               | Use OC Packet Handler   |
| Select Draft Schematic     | Yes           | Yes               | Use OC Packet Handler   |
| Next Crafting Stage        | No            | Yes               | Use OC Packet Handler   |
| Cancel Crafting Session    | Yes           | Yes               | Use OC Packet Handler   |
| Create Prototype           | Yes           | No                | Use Slash Command       |

## Recommended Phase 1 Wire Approach

For Phase 1, the following approach is recommended:

- **Request Crafting Session**: Register the `MessageQueueCraftRequestSession` OC packet handler.
- **Select Draft Schematic**: Register the `MessageQueueCraftSelectSchematic` OC packet handler.
- **Next Crafting Stage**: Register the `NextCraftingStage` OC packet handler.
- **Cancel Crafting Session**: Register the `MessageQueueCraftRequestSession` OC packet handler.
- **Create Prototype**: Register the `CmdCreatePrototype` slash command.

## Open Questions

- Are there any additional crafting actions that need to be considered?
- Should we implement fallback mechanisms for unhandled crafting actions?
- How will we handle errors or exceptions in the OC packet handlers and slash commands?