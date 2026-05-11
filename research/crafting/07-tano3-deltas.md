## Class-Level Delegate Pattern

The `TangibleObject` class uses the `IndirectBaselineDelegate` pattern for some fields to automatically emit deltas. This pattern is similar to what is used in `PlayerObjectOwnerNP.kt`.

## TANO3 Field Map

| Index | Field         | Setter                | Auto-Delta? |
|-------|---------------|-----------------------|-------------|
| 0     | complexity    | setComplexity(int)    | Yes         |
| 1     | maxCondition  | setMaxCondition(int)  | Yes         |

## TANO6 Field Map

| Index | Field        | Setter                      |
|-------|--------------|-----------------------------|
| 0     | attributes   | setAttribute(String, int)   |

## Available Setters Phase 3 Will Use

- `setComplexity(int)`
- `setMaxCondition(int)`
- `setAttribute(String, int)`

## Pattern To Follow For New TANO Updates

For new updates to TANO fields, consider using the `IndirectBaselineDelegate` pattern to automatically emit deltas. If this pattern is not applicable, manually call `sendDelta()` after updating the field.

## Gaps

- Confirm if there are other fields in TANO3 and TANO6 that need to be mapped and if they require auto-delta emission.
- Verify the exact implementation details of `IndirectBaselineDelegate` and how it can be applied to new fields.