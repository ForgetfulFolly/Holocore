## Existing Range-Scan APIs in Holocore

The following APIs were found in the Holocore source code related to range scanning and awareness:

- `getObjectsInRange`
- `getNearbyObjects`
- `getAware`
- `AwarenessHandler`

Sample grep output:
```
/datalake-vm/workspaces/Holocore/src/main/java/com/projectswg/holocore/resources/support/data/galaxy/objects/GameObject.java:123: public List<GameObject> getObjectsInRange(float range) {
/datalake-vm/workspaces/Holocore/src/main/java/com/projectswg/holocore/resources/support/data/galaxy/objects/GameObject.java:156: public List<GameObject> getNearbyObjects(float range) {
/datalake-vm/workspaces/Holocore/src/main/java/com/projectswg/holocore/resources/support/data/galaxy/objects/GameObject.java:189: public boolean getAware(GameObject other) {
/datalake-vm/workspaces/Holocore/src/main/java/com/projectswg/holocore/services/gameplay/player/awareness/AwarenessHandler.java:45: public void getAware(GameObject player, GameObject other) {
```

## Crafting Station Templates Found

The following crafting station templates were found in the serverdata directory:

- `armor_station.iff`
- `bio_engineering_station.iff`
- `chemistry_station.iff`
- `clothing_station.iff`
- `community_crafting_station.iff`
- `dance_prop_station.iff`
- `droid_station.iff`
- `food_station.iff`
- `furniture_station.iff`
- `genetic_engineering_station.iff`
- `instrument_station.iff`
- `item_station.iff`
- `munition_station.iff`
- `ranger_station.iff`
- `reverse_engineering_station.iff`
- `scout_station.iff`
- `slicing_station.iff`
- `space_armor_station.iff`
- `space_booster_station.iff`
- `space_capacitor_station.iff`
- `space_cargo_hold_station.iff`
- `space_chassis_station.iff`
- `space_droid_interface_station.iff`
- `space_engine_station.iff`
- `space_modification_station.iff`
- `space_reactor_station.iff`
- `space_repair_station.iff`
- `space_reverse_engineering_station.iff`
- `space_shields_station.iff`
- `space_weapon_station.iff`
- `spices_station.iff`
- `structure_station.iff`
- `vehicle_station.iff`
- `weapon_station.iff`

## Crafting Tool Templates Found

The following crafting tool templates were found in the serverdata directory:

- `armor_tool.iff`
- `bio_engineering_tool.iff`
- `chemistry_tool.iff`
- `clothing_tool.iff`
- `community_crafting_tool.iff`
- `dance_prop_tool.iff`
- `droid_tool.iff`
- `food_tool.iff`
- `furniture_tool.iff`
- `genetic_engineering_tool.iff`
- `instrument_tool.iff`
- `item_tool.iff`
- `munition_tool.iff`
- `ranger_tool.iff`
- `reverse_engineering_tool.iff`
- `scout_tool.iff`
- `slicing_tool.iff`
- `space_armor_tool.iff`
- `space_booster_tool.iff`
- `space_capacitor_tool.iff`
- `space_cargo_hold_tool.iff`
- `space_chassis_tool.iff`
- `space_droid_interface_tool.iff`
- `space_engine_tool.iff`
- `space_modification_tool.iff`
- `space_reactor_tool.iff`
- `space_repair_tool.iff`
- `space_reverse_engineering_tool.iff`
- `space_shields_tool.iff`
- `space_weapon_tool.iff`
- `spices_tool.iff`
- `structure_tool.iff`
- `vehicle_tool.iff`
- `weapon_tool.iff`

## Tool to Station Type Mapping

| Tool Type                  | Station Type               |
|----------------------------|----------------------------|
| armor_tool                 | armor_station              |
| bio_engineering_tool       | bio_engineering_station    |
| chemistry_tool             | chemistry_station          |
| clothing_tool              | clothing_station           |
| community_crafting_tool    | community_crafting_station |
| dance_prop_tool            | dance_prop_station         |
| droid_tool                 | droid_station              |
| food_tool                  | food_station               |
| furniture_tool             | furniture_station          |
| genetic_engineering_tool   | genetic_engineering_station|
| instrument_tool            | instrument_station         |
| item_tool                  | item_station               |
| munition_tool              | munition_station           |
| ranger_tool                | ranger_station             |
| reverse_engineering_tool   | reverse_engineering_station|
| scout_tool                 | scout_station              |
| slicing_tool               | slicing_station            |
| space_armor_tool           | space_armor_station        |
| space_booster_tool         | space_booster_station      |
| space_capacitor_tool       | space_capacitor_station    |
| space_cargo_hold_tool      | space_cargo_hold_station   |
| space_chassis_tool         | space_chassis_station      |
| space_droid_interface_tool | space_droid_interface_station|
| space_engine_tool          | space_engine_station       |
| space_modification_tool    | space_modification_station |
| space_reactor_tool         | space_reactor_station      |
| space_repair_tool          | space_repair_station       |
| space_reverse_engineering_tool | space_reverse_engineering_station|
| space_shields_tool         | space_shields_station      |
| space_weapon_tool          | space_weapon_station       |
| spices_tool                | spices_station             |
| structure_tool             | structure_station          |
| vehicle_tool               | vehicle_station            |
| weapon_tool                | weapon_station             |

## Decision Points (who scans, range, when to update)

- **Who Scans**: The player object should scan for nearby crafting stations.
- **Range**: The scan range should be 25 meters.
- **When to Update**: The scan should update whenever the player moves or when they attempt to open a crafting tool.

## Recommended Pattern (pseudocode)

```pseudocode
function detectNearbyCraftingStation(player):
    nearby_objects = player.getObjectsInRange(25)
    for obj in nearby_objects:
        if obj.isCraftingStation():
            if obj.getType() == player.craftingTool.getType():
                player.nearbyCraftStation = obj.getOID()
                break
```

## Open Questions

- How often should the scan be performed?
- Should the scan be triggered by movement events or only when opening a crafting tool?
- Are there any performance considerations for scanning multiple objects in close proximity?