## Current MSCO3 Implementation
The current implementation of ManufactureSchematicObject (MSCO) in `src/main/java/com/projectswg/holocore/resources/support/objects/swg/manufacture/ManufactureSchematicObject.java` supports baseline 3. This baseline includes basic properties such as the schematic's name, ingredients, and other essential attributes required for the initial phases of crafting.

## Current MSCO6 Implementation
Baseline 6 is also implemented in the current version of MSCO. It introduces additional features such as the ability to handle more complex schematics, including those with multiple steps and more intricate ingredient requirements. This baseline enhances the functionality of the crafting system but still lacks some advanced features required for later phases.

## What Phase 1-3 Needs From MSCO Baselines
### Phase 1 (Open Tool, Send Schematic List)
- **Requirement**: No MSCO is created yet, so baselines are irrelevant for this phase.
- **Implementation**: The client can function with the existing MSCO3/6 implementations to send a list of schematics to the player.

### Phase 2 (Select Schematic, Show Ingredient UI)
- **Question**: Does the client need MSCO7 to render ingredient slots, or is the slot UI driven entirely by MessageQueueCraftIngredients (CRC 0x0105)?
- **Investigation Needed**: Determine if MSCO7 provides necessary information for rendering ingredient slots or if the MessageQueueCraftIngredients packet is sufficient.

### Phase 3 (Assemble)
- **Question**: Does the client need MSCO7 deltas to update slot fill state, or are OC 0x0107/0x0108 (FillSlot/EmptySlot) sufficient?
- **Investigation Needed**: Evaluate whether MSCO7 deltas are required for updating the slot fill state during assembly or if the ObjectController packets are adequate.

## Investigation Notes
- **MSCO7**: Contains customizationStrings, customizationOptionsCount, ingredientSlots[], and experimentalProps[]. These properties might be necessary for rendering and managing ingredient slots.
- **MSCO8**: Includes readyDuration, complexity, and schematicQuantity, which are factory-related properties. These may not be critical for Phases 1-3.
- **MSCO9**: Contains customizationCount, customizations[], crafterName, schematicName, and manufactureLimit. These properties are more relevant for advanced customization and tracking, which may not be needed until later phases.

## Recommendation
Based on the investigation notes, it appears that MSCO7 might be required for Phase 2-3 due to its involvement in rendering and managing ingredient slots. However, further investigation is needed to confirm whether MSCO7 deltas are necessary for updating slot fill state during assembly or if the ObjectController packets are sufficient.

**Option**: MSCO7 only required for Phase 2-3 — can defer 8/9

## If Required: Implementation Estimate
If MSCO7 is required for Phase 2-3, the implementation estimate would be approximately 2-3 weeks to integrate the necessary changes and ensure compatibility with the existing crafting system.