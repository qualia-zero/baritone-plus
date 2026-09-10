# Baritone+

An advanced addon for [Baritone](https://github.com/cabaletta/baritone) adding autonomous crafting pipelines, task automation, and intelligent resource handling for Minecraft (Fabric).

---

## Features

- **Automated Crafting Pipelines:** Recursive crafting tree resolution (crafting prerequisites, placing crafting tables, gathering ingredients).
- **Smelting Support:** Automated furnace interaction and fuel/ingredient management.
- **Task Scheduling & Management:** Tick-based action queues with graceful error recovery.
- **Client Commands:** Built-in client-side commands with auto-completion.

---

## Commands

Baritone+ registers native Fabric client commands (`/`) with tab auto-completion:

| Command | Description |
| :--- | :--- |
| `/craft <item>` | Starts autocrafting 1 of the specified item (e.g., `/craft iron_pickaxe`). |
| `/craft <amount> <item>` | Starts autocrafting the specified amount (1–64) of an item (e.g., `/craft 64 oak_planks`). |
| `/cancel` *(or `/c`)* | Immediately cancels the current task queue and stops Baritone. |
| `/allowBaritonePlusBetaFeatures` | Toggles experimental beta features (e.g., automatic Nether portal construction). |

---

## Requirements

- **Minecraft:** `26.2+`
- **Fabric Loader:** `>=0.16.0`
- **Fabric API**
- **Java:** `25+`
- **Baritone:** Required at runtime.

---

## Building from Source

To build the mod jar:

```bash
./gradlew build
```

The resulting jar will be located in `build/libs/`.

---

## Disclaimer & Credits

> [!NOTE]
> **Baritone+ is an unofficial community addon.** It is not officially affiliated with or endorsed by the original Baritone development team or Mojang Studios.

- **Original Baritone:** Created by [cabaletta](https://github.com/cabaletta) and contributors ([Baritone GitHub Repository](https://github.com/cabaletta/baritone)), licensed under LGPL-3.0.
- **Minecraft:** Copyright Mojang AB / Microsoft.

---

## License

This project is licensed under the [MIT License](LICENSE).
