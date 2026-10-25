# Naturality

**The Minecraft I envisioned.**

Naturality is a Fabric mod that leans into Minecraft's pixel aesthetic, with changes to lighting, atmosphere, fluids, particles, portals, fire, and snow. It is primarily intended for singleplayer; compatibility with other mods, especially visual mods, is still being tested.

## Features

- Pixel-grid lighting, dynamic lights, atmospheric fog, and configurable darkness.
- Layered clouds, weather, rainbows, auroras, and meteor showers.
- Procedural water and lava textures, splashes, ripples, and waterfall effects.
- Emissive portals, animated opening effects, and custom portal particles.
- Fire and snow fitted to supporting block shapes.
- Configurable visual effects and server gameplay options.

Visual design is by Gargan; implementation was developed with AI assistance.

## Requirements and installation

This project is for **Minecraft 26.3**, **Fabric Loader 0.19.5 or newer**, **Fabric API**, and **Java 25**. Exact development dependency versions are in [`gradle.properties`](gradle.properties).

Place the built `naturality-1.0.0.jar` and a compatible Fabric API JAR in the Fabric instance's `mods` folder. The `-sources.jar` is for developers. [Mod Menu](https://modrinth.com/mod/modmenu) is optional and provides access to the configuration screen.

## Configuration

Open **Mods > Naturality > Configure > Config Presets** from the title screen:

- **Default** restores all shipped settings, including numeric defaults and features that normally start disabled.
- **Clientside Only** keeps locally derived visuals and disables added content, gameplay changes, custom menus, server networking and changes to vanilla block/entity data. This is a strict startup mode; restart to finish applying the preset.
- **No Visual Overhaul** keeps content and gameplay with optional visual and audio changes disabled, including heat waves, lens flare, and mob-death effects. It also disables server changes coupled to those visuals, such as portal entry animation, fire/snow wrapping, ghast physics, and impact-event broadcasts.

Presets apply ordinary settings once. Every setting remains independently editable; no persistent mode overrides later choices. The menu is organized into:

- **Visuals:** lighting/shadows, sky/atmosphere, water/lava, portals, fire/heat, snow, particles, entity animation, and glint.
- **Content and World Generation:** added content, End terrain/biomes, chorus plants, status effects, and Starfall.
- **Gameplay:** villagers/reputation, mobs/fishing, weather/sky rules, movement/portal travel, and block/snow rules.
- **Audio:** ambience, muffling, wind, splashes, and portal sound volumes.

Startup switches sit beside related live controls and are marked as requiring a restart. Global search works from every page and shows the setting's category path. Existing JSON keys and saved choices are preserved by the menu reorganization.

Search by estimated FPS impact with `#Low`, `#Medium`, `#High`, or `#ExtraHigh` (`#Extra High` also works). Tags ignore capitalization and combine with setting names, such as `#High heat`. Multiple tags match either tier, such as `#Low #Medium`. Only rated settings appear when filtering by impact, whether currently enabled or disabled.

Fifteen selected visual toggles now include a colored **Estimated FPS impact** in their tooltip: **Low**, **Medium**, **High**, or **Extra High**. These are rough relative estimates at default settings while the effect is active, not measured average FPS losses or fixed percentages. Each rated option explains what increases its cost; unrated options have no implied rating. For example, moving dynamic lights are rated Extra High, while ghast animation is rated Low.

Restart Minecraft after changing startup features or applying a preset that changes them. Content and server feature switches are editable from the title screen. Client and server must agree on added content, chorus block states, and entity-data changes. Turning off content does not convert blocks or items already saved in a world.

For manual editing while the game is closed, client settings are in `config/naturality.json`, existing server settings in `config/naturality-server.json`, and startup feature switches in `config/naturality-features.json`.

## Setup

Water rendering, animated fire, and foliage wind support Sodium **0.9.3-alpha.1 for Minecraft 26.3**, including
classic and improved transparency, without disabling Sodium settings. Other
visual features and shader-pack combinations may still need compatibility work.

Put texture PNGs in [`resources/textures`](resources/textures/) and Ogg Vorbis audio clips in [`resources/sounds`](resources/sounds/). Use mono audio for positional sounds. Edit [`resources/sounds.json`](resources/sounds.json) to connect clips to sound events. Gradle packages these resources automatically for builds and development launches. See [`resources/README.txt`](resources/README.txt) for folder locations and a portal sound replacement example.

See [`AGENTS.md`](AGENTS.md) for the current project layout, versions, working rules, feature map, and resource workflow. Read its MUST READ section first, then only the relevant optional sections.

Use **JDK 25** and the included Gradle wrapper to build the mod; no separate Gradle installation is needed.

### Build in VS Code

1. Open this folder in VS Code (the folder containing `build.gradle`).
2. Install the recommended **Extension Pack for Java** and **Gradle for Java** extensions if prompted.
3. Run **Developer: Reload Window** from the command palette, and close any old terminals so new terminals pick up the Java settings.
4. Let the Java extension import the Gradle project. Press **Ctrl+Shift+B** to run **Fabric: build**.

The mod JAR is written to `build/libs/naturality-1.0.0.jar`. The `-sources.jar` file contains source code and is not the mod to install.

This machine's workspace settings use a local JDK in `.tools/jdk-25`. Both the JDK and `.vscode/settings.json` are ignored by Git because they are machine-specific. If you move the folder, update the absolute paths in `.vscode/settings.json`.

On another machine, install [JDK 25](https://adoptium.net/temurin/releases/?version=25), set `JAVA_HOME` to its installation folder, add its `bin` folder to `PATH`, and restart VS Code. If Gradle import uses another Java version, set **Java > Import > Gradle > Java: Home** to the JDK 25 folder in workspace settings. Verify `java -version` and `./gradlew --version` in a new terminal.

### Run and debug

Use **Terminal > Run Task > Fabric: run client** to start the development game. For breakpoints, run **Fabric: generate VS Code launch configurations**, then select the generated Minecraft client target in **Run and Debug** and press **F5**. These launch configurations are generated by Fabric Loom and are ignored by Git. See the [Fabric VS Code guide](https://docs.fabricmc.net/develop/getting-started/vscode/launching-the-game).

You can also build from a new VS Code PowerShell terminal:

```powershell
.\gradlew.bat build
```

On macOS or Linux, use `./gradlew build`. The first build downloads dependencies and requires internet access.

## Checks and GitHub builds

`build` runs the portal geometry checks as well as compiling and packaging the mod. For a relevant in-game regression suite, use, for example:

```powershell
.\gradlew.bat listClientTests
.\gradlew.bat runClientGameTest "-PclientTests=cloud-geometry,fluid-simulation"
```

Choose small checks focused on the changed behavior; the shared [test catalog](src/gametest/resources/naturality-tests.json) lists their fixture requirements. Agents need explicit user authorization to create game tests or run existing ones; authorization to create a test includes running it, and authorized tests may be rerun freely. Ordinary test worlds and menu previews use the same no-save API; persistence tests opt in explicitly. See [`AGENTS.md`](AGENTS.md) for adding checks and [`PORTAL_GLOW.txt`](PORTAL_GLOW.txt) for portal details. Selected checks load independently, but normal game/content initialization still runs; a more selective content-loading pipeline is planned.

For Java warnings, run **Terminal > Run Task > Fabric: audit Java warnings**, or:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-java-warnings.ps1 -Offline
```

The audit checks all Gradle Java source sets, including game tests, portal tests and generated models. It saves [`summary.json`](build/java-audit/all/summary.json) and [`problems.json`](build/java-audit/all/problems.json), so agents can read the warning list without a chat paste. Any warning/error makes the audit fail. Omit `-Offline` if dependencies need downloading; `-CompilerJar <path>` selects an Eclipse compiler supporting Java 25 when the VS Code Java extension is unavailable. The narrower `-Scope Production` check is not a workspace-wide pass. This is a saved-source batch audit; unsaved editor buffers, other extensions and editor defaults can produce different Problems-panel counts.

GitHub Actions builds pushes and pull requests with Java 25. You can also start it manually from the **Actions** tab. Successful runs provide the JARs as an **Artifacts** download; these are development builds, not published releases. In-game client suites are run separately.

Local worlds, logs, generated builds, Gradle caches, and the local JDK are excluded from Git. Shared VS Code tasks and the Gradle wrapper are included so a fresh clone can be built on another machine.

## License

Naturality is available under [CC0-1.0](LICENSE).
