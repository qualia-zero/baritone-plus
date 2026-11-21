### ⚙️ Projekt & Minecraft-Kompatibilität
- Halte das Projekt stets auf der Basis der neuesten Baritone-Version aktuell.
- Gewährleiste dabei eine bestmögliche Abwärtskompatibilität für ältere Versionen.
- Setze auf das neue Versionsschema (ab 26.x) und die von Haus aus vollständig deobfuskierten Minecraft-JARs.
- Halte den Code übersichtlich und die Abhängigkeiten minimal.
- Lege die fertige neue Mod-Version (`baritone-plus-*.jar` ohne `-dev`/`-sources`) nach jedem Build/Release in den Mods-Ordner der Instanz **Fabric (Latest)** (`/home/mark/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Fabric (Latest)/minecraft/mods/`) – inklusive passendem `baritone-api-fabric-*.jar`, falls Baritone dort fehlt (ältere `baritone-plus-*.jar` dabei ersetzen).
