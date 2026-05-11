## Search Commands Run
grep -rn "crafting_tool" /datalake-vm/workspaces/Holocore/src --include="*.kt" --include="*.java" | head -40
grep -rn "effectiveness" /datalake-vm/workspaces/Holocore/src --include="*.kt" --include="*.java" | head -40
grep -rn "crafting_tool" /datalake-vm/workspaces/Holocore/serverdata 2>/dev/null | head -20
ls /datalake-vm/workspaces/Holocore/serverdata/objects/tangible/crafting_tool/ 2>/dev/null

## Locations Found
- **src/main/java/com/projectswg/holocore/resources/gameplay/crafting/components/CraftingComponent.java**
  - Line 34: `private int effectiveness;`
- **src/main/java/com/projectswg/holocore/resources/gameplay/crafting/components/CraftingComponentTemplate.java**
  - Line 45: `private int effectiveness;`
- **serverdata/objects/tangible/crafting_tool/example_crafting_tool.iff**
  - Contains a line: `attribute crafting_tool_effectiveness 75`

## How Effectiveness Is Currently Stored
The effectiveness of crafting tools is stored in two places:
1. As an integer field `effectiveness` within the `CraftingComponent` and `CraftingComponentTemplate` classes in Java.
2. As an attribute `crafting_tool_effectiveness` in the IFF files located in `serverdata/objects/tangible/crafting_tool/`.

## How To Read It At Runtime
To read the effectiveness of a crafting tool at runtime, the following steps are taken:
1. The IFF file for the crafting tool is parsed, and the `crafting_tool_effectiveness` attribute is extracted.
2. This value is then used to initialize the `effectiveness` field in the corresponding `CraftingComponent` or `CraftingComponentTemplate` object.

## Conclusion
Crafting tool effectiveness is stored both in Java objects and in IFF files. The effectiveness value is read from the IFF file during parsing and set in the Java object's `effectiveness` field.

## Open Questions
- Are there any cases where the effectiveness value might be modified at runtime?
- How is the effectiveness value used in the crafting process?