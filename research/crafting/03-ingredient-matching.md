## IngridientSlot Class Source

```java
package com.projectswg.common.data.swgfile.visitors.objects.data.schematic;

public class IngridientSlot {
    private int index;
    private IngridientType type;
    private String ingredientName;
    private int ingredientCount;
    private SlotType slotType;

    // Getters and setters
}
```

## DraftSlotDataOption Class Source

```java
package com.projectswg.common.data.swgfile.visitors.objects.data.schematic;

public class DraftSlotDataOption {
    private String ingredientName;
    private int ingredientCount;

    // Getters and setters
}
```

## IngridientType Enum (verbatim)

```java
public enum IngridientType {
    IT_NONE(0),
    IT_ITEM(1),
    IT_TEMPLATE(2),
    IT_RESOURCE_TYPE(3),
    IT_RESOURCE_CLASS(4),
    IT_TEMPLATE_GENERIC(5),
    IT_SCHEMATIC(6),
    IT_SCHEMATIC_GENERIC(7);

    private final int value;

    IngridientType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
```

## SlotType Enum (verbatim)

```java
public enum SlotType {
    ST_NONE(0),
    ST_TEMPLATE(1),
    ST_RESOURCE(2),
    ST_ITEM(3),
    ST_SCHEMATIC(4);

    private final int value;

    SlotType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
```

## Matching Rules Per IngridientType (table)

| IngridientType         | Matching Logic                                                                                      |
|------------------------|---------------------------------------------------------------------------------------------------|
| IT_NONE                | No matching required                                                                              |
| IT_ITEM                | Item template path or originating schematic CRC                                                   |
| IT_TEMPLATE            | `tano.serverTemplateCRC == hash(option.ingredientName)`                                           |
| IT_RESOURCE_TYPE       | `tano.spawnName == option.ingredientName`                                                         |
| IT_RESOURCE_CLASS      | `tano.resourceType` walks parent chain for `option.ingredientName`                                |
| IT_TEMPLATE_GENERIC    | `tano.serverTemplateCRC == hash(option.ingredientName)`                                           |
| IT_SCHEMATIC           | Item template path or originating schematic CRC                                                   |
| IT_SCHEMATIC_GENERIC   | Item template path or originating schematic CRC                                                   |

## Holocore Resource Class Walking

The `RawResource` class in Holocore has a method to walk the parent chain of resource classes. This is used to match `IT_RESOURCE_CLASS` with `option.ingredientName`.

```kotlin
fun RawResource.walkParentChain(target: String): Boolean {
    var current: RawResource? = this
    while (current != null) {
        if (current.resourceType == target) {
            return true
        }
        current = current.parent
    }
    return false
}
```

## Sample Validator Pseudocode

```pseudocode
function validateIngredient(slot: IngridientSlot, option: DraftSlotDataOption) -> boolean:
    switch slot.type:
        case IT_NONE:
            return true
        case IT_ITEM:
            return slot.ingredientName == option.ingredientName
        case IT_TEMPLATE:
            return tano.serverTemplateCRC == hash(option.ingredientName)
        case IT_RESOURCE_TYPE:
            return tano.spawnName == option.ingredientName
        case IT_RESOURCE_CLASS:
            return tano.resourceType.walkParentChain(option.ingredientName)
        case IT_TEMPLATE_GENERIC:
            return tano.serverTemplateCRC == hash(option.ingredientName)
        case IT_SCHEMATIC:
            return slot.ingredientName == option.ingredientName
        case IT_SCHEMATIC_GENERIC:
            return slot.ingredientName == option.ingredientName
        default:
            throw IllegalArgumentException("Unknown IngridientType")
```

## Open Questions

- Are there any additional constraints or conditions for matching `IT_ITEM` and `IT_SCHEMATIC`?
- How is `hash(option.ingredientName)` calculated for `IT_TEMPLATE` and `IT_TEMPLATE_GENERIC`?
- Are there any special cases or exceptions to the matching rules?
- How is the `parent` relationship defined in the `RawResource` class?