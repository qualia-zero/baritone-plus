# Baritone+

An advanced addon for [Baritone](https://github.com/cabaletta/baritone) adding autonomous crafting pipelines, task automation, and intelligent resource handling for Minecraft (Fabric).

---

## Features

- **Automated Crafting Pipelines:** Recursive crafting tree resolution with live inventory replanning and anti-cheat-safe container/crafting isolation (stops Baritone mining while GUIs are open).
- **Smelting Support:** Automated furnace interaction and fuel/ingredient management.
- **Task Scheduling & Management:** Tick-based action queues with graceful error recovery.
- **Typed JSON Configuration & Presets:** Central `config/baritone-plus.json` with `VANILLA` and `STRICT` anti-cheat profiles and automatic migration from legacy beta config.
- **Native Config & Control GUI + Unified `/bp` Commands:** Built-in client-side `/bp` commands with auto-completion and a lightweight native Vanilla screen (`Right Shift` or `/bp gui`) featuring a `Control` tab and a `Baritone` tab (common settings + live modified settings viewer/reset).
- **Built-in Combat & Self-Preservation (Independent of AI):** Meteor-grade `AutoEatController` (food priority, threshold modes, blacklist, inventory search, Baritone pause/resume), independent `AutoShield`, and `CombatController` with `RotationManager` — always active on their own and toggleable via GUI, `/bp`, or AI.
- **Optional Embedded Gemini Atomic Orchestrator:** Disabled by default (`aiBridge.enabled = false`). When enabled with a `GEMINI_API_KEY` (or `/bp ai key <key>`), uses the Gemini `v1/interactions` REST API directly inside the mod JAR as a pure atomic step-by-step orchestrator (no hardcoded `/craft` shortcut): plans and verifies every step from live game state using `get_game_state`, `baritone_command`, `craft_grid`, `place_block`, `equip_item`, `interact_nearby`, `click_gui_slot`, `close_gui`, `set_combat_defense`, `send_chat`, and `stop_all`.

---

## Commands

Baritone+ registers all client-side commands under the unified `/bp` namespace with tab auto-completion:

| Command | Description |
| :--- | :--- |
| `/bp gui` | Opens the native Baritone+ Control & Settings screen (also bound to `Right Shift`). |
| `/bp craft <item>` | Starts autocrafting 1 of the specified item (e.g., `/bp craft iron_pickaxe`). |
| `/bp craft <amount> <item>` | Starts autocrafting the specified amount (1–64) of an item (e.g., `/bp craft 64 oak_planks`). |
| `/bp mine <blocks>` | Starts Baritone mining for the given block(s) (e.g., `/bp mine diamond_ore`). |
| `/bp goto <target>` | Navigates with Baritone to coordinates or a block/goal (e.g., `/bp goto 100 64 -200`). |
| `/bp follow <target>` | Follows players or a specific entity/player with Baritone. |
| `/bp explore` / `/bp farm` | Starts Baritone exploration or auto-farming. |
| `/bp pause` / `/bp resume` | Pauses or resumes active Baritone pathing. |
| `/bp run <command>` | Executes any raw Baritone command. |
| `/bp stop` | Cancels the current Baritone+ task queue and stops Baritone. |
| `/bp config <key> [value]` | Reads or updates any setting in `config/baritone-plus.json` live (e.g., `general.betaFeatures`). |
| `/bp profile <vanilla\|strict>` | Applies a preset anti-cheat profile (`VANILLA` or `STRICT`). |
| `/bp combat <on\|off>` | Toggles the combat attacker. |
| `/bp autoeat <on\|off>` | Toggles standalone Meteor-grade Auto-Eat. |
| `/bp shield <on\|off>` | Toggles standalone offhand Auto-Shield. |
| `/bp ai <on\|off\|status>` | Toggles or inspects the optional embedded Gemini AI agent (disabled by default). |
| `/bp ai ask <prompt>` | Sends a task/prompt to the Gemini agent with live game state and function calling. |
| `/bp ai key <apiKey>` | Saves your Gemini API key locally to `config/baritone-plus-secrets.txt` (or use `GEMINI_API_KEY` env var). |
| `/bp ai model <model>` | Sets the Gemini model (e.g., `gemini-4-hydrogen`, `gemini-3.5-flash-lite`, `gemini-4-neon`). |
| `/bp ai thinking <level>` | Sets Gemini thinking level (`default`, `minimal`, `low`, `medium`, `high`). |
| `/bp ai reset` | Clears the current Gemini conversation memory (`previous_interaction_id`). |

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
