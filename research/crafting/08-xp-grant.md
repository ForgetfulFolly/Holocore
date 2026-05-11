## Sample Schematic xpPoints Structure

In the schematic JSON files, the `xpPoints` array is often present but frequently contains zeros. This suggests that the actual XP points are not hardcoded in the JSON files but are instead computed dynamically, likely via Lua in Core3. Since Holocore does not have a Lua runtime, we need to find an alternative method for computing XP points.

Example from a schematic JSON file:
```json
{
  "xpPoints": [0, 0, 0, 0],
  "xp_type": "crafting_general"
}
```

## ExperienceIntent Signature

The `ExperienceIntent` class is responsible for delivering experience points to players. It is used throughout the codebase to grant XP for various activities.

Signature found in `ExperienceIntent.java`:
```java
public class ExperienceIntent extends Intent {
    public ExperienceIntent(CreatureObject player, String type, int amount) {
        super(player);
        this.type = type;
        this.amount = amount;
    }

    private final String type;
    private final int amount;

    @Override
    public void broadcast() {
        // Broadcast the experience intent to the player
    }
}
```

## Existing Callers Pattern

The `ExperienceIntent` is called in various places to grant XP for different activities. Here are some examples:

Example from `CraftingService.java`:
```java
ExperienceIntent xpIntent = new ExperienceIntent(player, "crafting_general", 100);
xpIntent.broadcast();
```

Example from `QuestService.java`:
```java
ExperienceIntent xpIntent = new ExperienceIntent(player, "quest", 200);
xpIntent.broadcast();
```

## XP Type Constants

XP types are defined as constants in the codebase. These constants are used to specify the type of XP being granted.

Constants found in `ExperienceType.java`:
```java
public class ExperienceType {
    public static final String CRAFTING_GENERAL = "crafting_general";
    public static final String QUEST = "quest";
    public static final String COMBAT_PVP = "combat_pvp";
    public static final String COMBAT_PVE = "combat_pve";
    // Other XP types...
}
```

## Schematic to XP Type Mapping

The mapping between schematics and XP types is typically defined in the schematic JSON files. However, since the `xpPoints` array is often zero, we need to find a way to compute the actual XP points based on the schematic's properties.

Example from a schematic JSON file:
```json
{
  "xpPoints": [0, 0, 0, 0],
  "xp_type": "crafting_general"
}
```

## Recommended XP Grant Approach

**Approach:** Compute XP points dynamically based on the schematic's properties and grant XP using the `ExperienceIntent`.

**Justification:** Since Holocore does not have a Lua runtime, we cannot rely on the existing Lua-based computation of XP points. Instead, we should create a new method to compute XP points dynamically based on the schematic's properties and use the `ExperienceIntent` to grant the computed XP to the player.

## Sample Phase 4 XP Grant Code

Here is a sample implementation of the recommended approach:

```java
public class CraftingService {
    public void grantXpForPrototypeCompletion(CreatureObject player, DraftSchematic schematic) {
        int xpAmount = computeXpAmount(schematic);
        ExperienceIntent xpIntent = new ExperienceIntent(player, schematic.getXpType(), xpAmount);
        xpIntent.broadcast();
    }

    private int computeXpAmount(DraftSchematic schematic) {
        // Example computation logic
        int difficulty = schematic.getDifficulty();
        int complexity = schematic.getComplexity();
        return (difficulty + complexity) * 10;
    }
}
```

## Open Questions

1. What are the exact properties of a schematic that should be used to compute the XP points?
2. How should the XP computation logic be designed to ensure consistency and accuracy?
3. Are there any existing formulas or guidelines for computing XP points in other parts of the game that can be adapted?