## How Radial Menus Work in Holocore

Holocore uses radial menus to provide players with quick access to actions related to objects they interact with. These menus are dynamically generated based on the context of the object and the player's current state. The core components involved in radial menu handling are `RadialHandler`, `ObjectMenuRequest`, `RadialOption`, and `ObjectMenuResponse`.

- **RadialHandler**: This is responsible for generating the radial menu options for a given object. It processes the request and constructs the response with the available options.
- **ObjectMenuRequest**: This object contains information about the object and the player requesting the menu. It is passed to the `RadialHandler` to generate the menu.
- **RadialOption**: Represents an individual option in the radial menu. Each option has a label and an associated action.
- **ObjectMenuResponse**: Contains the list of `RadialOption` objects that make up the radial menu. This response is sent back to the client to display the menu.

## Existing RadialHandler Examples

Several examples of `RadialHandler` implementations can be found in the Holocore source code. Here are a few notable ones:

- **ItemRadialHandler**: Handles radial menu options for items.
- **VehicleRadialHandler**: Handles radial menu options for vehicles.
- **BuildingRadialHandler**: Handles radial menu options for buildings.

Example snippet from `ItemRadialHandler`:
```java
public class ItemRadialHandler extends RadialHandler {
    @Override
    public void getOptions(ObjectMenuRequest request, ObjectMenuResponse response) {
        // Add options based on item type and player permissions
        response.addRadialOption(new RadialOption("Use", "use_item"));
        response.addRadialOption(new RadialOption("Examine", "examine_item"));
    }
}
```

## Crafting Tool — Current Radial Entries (if any)

After searching the Holocore source code, no existing radial menu entries were found specifically for crafting tools. However, there are some generic entries related to crafting actions:

- **USE_ITEM**: This action might be used for initiating crafting sessions, but it is not specific to crafting tools.

## Required New Entries

To add a radial menu entry for the crafting tool, we need to create a new `RadialHandler` that handles the `RequestCraftingSessionIntent`. This handler should add a specific option to the radial menu for initiating a crafting session.

## Where To Register

The new `RadialHandler` should be registered in the `RadialHandlerManager` class. This manager is responsible for mapping object types to their corresponding radial handlers.

Example registration:
```java
public class RadialHandlerManager {
    public void registerHandlers() {
        // Register other handlers
        registerHandler(Item.class, new ItemRadialHandler());
        registerHandler(Vehicle.class, new VehicleRadialHandler());

        // Register the new crafting tool handler
        registerHandler(CraftingTool.class, new CraftingToolRadialHandler());
    }
}
```

## Example Code Skeleton

Here is a skeleton implementation of the `CraftingToolRadialHandler`:

```java
public class CraftingToolRadialHandler extends RadialHandler {
    @Override
    public void getOptions(ObjectMenuRequest request, ObjectMenuResponse response) {
        // Add the option to request a crafting session
        response.addRadialOption(new RadialOption("Craft", "request_crafting_session"));
    }
}
```

## Recommendation

To implement the radial menu entry for the crafting tool, follow these steps:

1. Create a new `CraftingToolRadialHandler` class that extends `RadialHandler`.
2. Override the `getOptions` method to add a "Craft" option that triggers the `RequestCraftingSessionIntent`.
3. Register the new handler in the `RadialHandlerManager` class.
4. Test the new radial menu entry to ensure it appears correctly and triggers the intended action.

This approach ensures that the crafting tool has a dedicated radial menu entry, improving the user experience and making it easier for players to initiate crafting sessions.