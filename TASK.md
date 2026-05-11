# Task 506 — Crafting R07: Inventory TangibleObject TANO3/TANO6 delta setters
## Worker-Role: research
## Team: alpha
## Desk: 1
## Scope: research/crafting/07-tano3-deltas.md (NEW FILE)

## Description
Read TangibleObject.java in full. Map all setters that encode into TANO3 and TANO6 baselines.
Confirm which setters auto-emit deltas.
Write findings to research/crafting/07-tano3-deltas.md on this branch.

## File Changes
| Action | Path | Description |
|--------|------|-------------|
| CREATE | research/crafting/07-tano3-deltas.md | Research output |

## Embedded Context
File to read:
  src/main/java/com/projectswg/holocore/resources/support/objects/swg/tangible/TangibleObject.java

Phase 3 (assembly) needs to set on prototype TangibleObject:
  - complexity (likely TANO3)
  - maxCondition (likely TANO3)
  - attributes (TANO6, attribute SWGMap)

Reference pattern from PlayerObjectOwnerNP.kt uses IndirectBaselineDelegate for auto-delta.
TangibleObject may use the same pattern or manual sendDelta().

## Output Format
Create research/crafting/07-tano3-deltas.md with sections:
- ## Class-Level Delegate Pattern
- ## TANO3 Field Map (table: index, field, setter, auto-delta?)
- ## TANO6 Field Map (table)
- ## Available Setters Phase 3 Will Use
- ## Pattern To Follow For New TANO Updates
- ## Gaps

## Acceptance
- [ ] research/crafting/07-tano3-deltas.md exists with content
- [ ] ## Available Setters section present
- [ ] git diff main..HEAD --name-only shows only research/crafting/07-tano3-deltas.md

## Commit message
Research R07: TangibleObject TANO3/TANO6 delta setter inventory

Status: queued
Retry-Count: 0
