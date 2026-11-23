# Baritone+

An advanced addon for [Baritone](https://github.com/cabaletta/baritone) adding autonomous crafting pipelines, task automation, and intelligent resource handling for Minecraft (Fabric).

---

## Features

- **Automated Crafting Pipelines:** Recursive crafting tree resolution (crafting prerequisites, placing crafting tables, gathering ingredients).
- **Smelting Support:** Automated furnace interaction and fuel/ingredient management.
- **Task Scheduling & Management:** Tick-based action queues with graceful error recovery.
- **Typed JSON Configuration & Presets:** Central `config/baritone-plus.json` with `VANILLA` and `STRICT` anti-cheat profiles and automatic migration from legacy beta config.
- **Native Config GUI & Client Commands:** Built-in client-side `/bp` and `/craft` commands with auto-completion, plus a lightweight native Vanilla settings screen (`Right Shift` or `/bp gui`).
- **Optional AI Bridge Settings:** Configurable (disabled by default) settings for connecting an external AI orchestrator without adding runtime overhead when unused.

---

## Commands

Baritone+ registers native Fabric client commands (`/`) with tab auto-completion:

| Command | Description |
| :--- | :--- |
| `/bp gui` | Opens the native Baritone+ settings screen (also bound to `Right Shift`). |
| `/bp config <key> [value]` | Reads or updates any setting in `config/baritone-plus.json` live. |
| `/bp profile <vanilla\|strict>` | Applies a preset anti-cheat profile (`VANILLA` or `STRICT`). |
| `/bp combat <on\|off>` | Toggles the combat controller configuration flag. |
| `/bp ai <on\|off\|status>` | Toggles or inspects the optional AI bridge configuration (disabled by default). |
| `/bp stop` | Cancels the current Baritone+ task queue and stops Baritone. |
| `/craft <item>` | Starts autocrafting 1 of the specified item (e.g., `/craft iron_pickaxe`). |
| `/craft <amount> <item>` | Starts autocrafting the specified amount (1–64) of an item (e.g., `/craft 64 oak_planks`). |
| `/cancel` *(or `/c`)* | Immediately cancels the current task queue and stops Baritone. |
| `/allowBaritonePlusBetaFeatures` | Toggles experimental beta features (e.g., automatic Nether portal construction). |

---

## Requirements

- **Minecraft:** `26.2+` (built against `26.3`)
- **Fabric Loader:** `>=0.16.0`
- **Fabric API**
- **Java:** `25+`
- **Baritone:** Required at runtime (Fabric `>=1.18.0`, built against `v1.20.0`).

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

This project is licensed under the [GNU General Public License v3.0](LICENSE).
