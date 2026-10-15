# Naturality agent reference

## MUST READ

Read this section for every task. Below the STOP marker, read only the headings relevant to the work. This file describes the current project and durable working rules; update it in place, never append chat histories or build diaries. If requirements or intended behavior cannot be established from the user and current source, ask before making the dependent change.

### Project and versions

Naturality is a Fabric mod for Minecraft's pixel aesthetic, with visual, environmental, content and gameplay changes. Mod ID/namespace: `naturality`; current mod version: `1.0.0`. It is primarily intended for singleplayer; do not infer multiplayer or renderer compatibility from a successful build.

| Setting | Current value | Source of truth |
| --- | --- | --- |
| Minecraft | 26.3 | `gradle.properties` |
| Java | JDK 25 | `build.gradle` |
| Fabric Loader / Fabric API | 0.19.5 / 0.161.0+26.3 | `gradle.properties` |
| Fabric Loom / Gradle wrapper | 1.18-SNAPSHOT / 9.7.1 | `gradle.properties`; `gradle/wrapper/gradle-wrapper.properties` |
| Optional Mod Menu | 21.0.0 | `gradle.properties` |

Keep runtime declarations in `src/main/resources/fabric.mod.json` consistent with these files. Use the wrapper and the project's dependency versions; inspect the matching game/API sources before adopting APIs from another version.

### Current layout and code placement

All paths below are relative to the repository root. The feature map uses these aliases:

| Root / alias | Responsibility |
| --- | --- |
| `M` = `src/main/java/naturality/` | Common/server logic, registration, networking and gameplay. Entry: `Naturality.java`. |
| `C` = `src/client/java/naturality/client/` | Client rendering, audio and screens. Entry: `NaturalityClient.java`; datagen: `NaturalityDataGenerator.java`. |
| `M/mixin/`, `C/mixin/` | Common/client hooks. Lists: `src/main/resources/naturality.mixins.json`, `src/client/resources/naturality.client.mixins.json`; gating: `M/config/FeatureMixinPlugin.java`. |
| `src/main/resources/` | Metadata, data packs and shared assets. Mod data: `data/naturality/`; vanilla overrides use `data/minecraft/` or `assets/minecraft/`. |
| `src/client/resources/` | Client assets and shader overrides, under `assets/naturality/` and `assets/minecraft/`. |
| `resources/` | Artist inputs: textures, sounds and model exports. Packaging/generation is defined in `build.gradle`. |
| `src/gametest/java/naturality/test/` | Fabric client integration tests; selector: `SelectedClientGameTests.java`. |
| `src/portalGlowTest/java/` | Small executable checks; wired through `checkPortalGlow` in `build.gradle`. |
| `scripts/`, `.vscode/tasks.json` | Maintenance/audit scripts and shared development tasks. |

Place behavior in its owning feature package; use mixins to apply that behavior and initialization for registration/wiring. Follow the file boundaries below. Keep client dependencies out of common code. Match neighboring formatting and descriptive names. Use lowercase resource IDs/filenames with underscores. Edit source inputs, never generated `build/` or IDE `bin/` copies. Keep temporary diagnostics in `build/`, not source folders or this reference.

### Feature file boundaries: consolidate related code

Prefer fewer, larger, coherent feature files so the directory tree is easy to read and organize. **Always combine directly related code unless a boundary below or a concrete technical requirement makes separate files necessary.** Consolidation means grouping code, not minifying it: retain readable formatting, descriptive methods and clear internal sections. Do not split a feature into tiny helper, state, controller or utility files merely because each could be a separate class; use methods or nested types when appropriate.

| Boundary | Required organization |
| --- | --- |
| One feature or small change | Keep its directly related behavior, state and feature-specific helpers together in one implementation file. For example, smoke sprite selection and smoke behavior belong together. Do not combine unrelated features just to reduce the file count. |
| Repeated functionality / shared library or API | When the same functionality is repeated across features, or implementing the current work would repeat it extensively, extract it into a focused library/API file and reuse it. Examples include a shared mathematical formula or a common shader-application method. Keep one authoritative implementation with a clear interface; leave feature-specific choices in its callers. Base extraction on concrete reuse, not hypothetical future needs, and group related shared functions rather than creating a separate file for every helper. |
| Shaders and visual data versus their use | Keep shaders and code that produces sprite/texture/visual data separate from the code that places and uses it. For example, aurora sprite-data generation belongs separately from sky-plane geometry, positioning and application. Consolidate related work within each side of this boundary. |
| System framework versus system items | Give large systems such as sky events and weather a clear base framework, with each individual event/channel/item in its own implementation file registered into that framework. For example, the rain slider's specific behavior belongs to a rain item, not hard-coded into the custom weather setup. The framework owns shared orchestration and registration; each item owns its specific behavior. |
| Small vanilla changes versus injection wiring | Keep one implementation file per change, and consolidate the compatible hooks that apply changes into larger category mixins, such as `ParticleChangesMixin`, with separate groups for block changes, menu changes and other categories. The category mixin delegates to the feature implementations; it must not absorb all their behavior. These are organization examples, not claims that those mixins already exist. |
| Substantial system overhauls | A thorough change such as the weather system may keep dedicated system mixins instead of being forced into a general category mixin. This exception concerns integration hooks; the framework/item and visual-data boundaries still apply. |

Separate files remain necessary where Java/Mixin requirements, incompatible target classes, common/client source sets, or independent startup/optional-mod gates demand them. Preserve gate behavior when combining hooks: disabling one feature must not disable unrelated changes or resolve an absent optional dependency. Keep genuinely shared infrastructure at its shared owner; do not duplicate it inside several consolidated feature files. State the concrete reason for additional separation in the change explanation; file length or abstract preference for more classes is not sufficient by itself.

Apply these rules to new work and agreed restructuring. The current feature map remains a description of existing files, not a claim that consolidation is already complete. Production-file consolidation does not override the small, focused test rules below. Extend this boundary table in place when the user adds rules; avoid accumulating competing organization instructions.

### Maintaining the overview through rewrites

- Describe what exists now. Verify paths, feature gates and entry points against source; do not copy obsolete claims from older notes.
- A feature-map row contains a short name, one sentence of observable behavior, and its owning package plus a few entry files or hook prefixes. Group related behavior; omit constants, tuning histories, exhaustive file lists and test logs.
- Update the affected row in the same change that adds, removes, renames or moves a feature. Search for old paths/names in registrations, mixin lists, resources, tests, build wiring and documentation. Remove stale entries instead of adding a contradictory update.
- Keep universal rules in MUST READ; put feature details and situational workflows in READ WHEN RELEVANT. Add a warning rule only for a demonstrated recurring issue, with its cause and concrete fix; merge duplicates.
- For a major rewrite, first document the agreed target and responsibility boundaries using the migration template below. Until migrated, show the current location separately from the proposed destination. Update the current map as each part moves; remove the temporary migration map when complete.
- No new target directory layout is chosen yet. This reference supports a later redesign; it is not an instruction to reorganize the current code now.

### Warnings: check the whole workspace and fix the cause

`assemble` and the production compiler do not cover every editor warning. The old audit skipped game tests, portal tests and generated model sources. Never describe a production-only pass as a warning-free project.

After Java or build/generator changes, run `scripts/check-java-warnings.ps1` (command below). Its default **All** scope discovers Gradle Java source sets, including tests and generated Java, and saves a categorized report. Fix warnings introduced by the task and warnings in code you change. If unrelated warnings remain, report the scope/count and report path explicitly; do not hide them as a clean pass or launch an unrelated mass cleanup. A previous report is not evidence about the current files.

Preserve `-Xlint:deprecation,unchecked,cast`, `-Werror` and `.settings/org.eclipse.jdt.core.prefs`. Do not turn down diagnostics, exclude tests, or add broad suppressions to make a check pass. A narrow suppression needs a specific external contract or compiler limitation and a comment explaining it. Do not remove runtime checks merely because null analysis cannot see reflection or injection.

| Warning / recurring mistake | Fix or prevention |
| --- | --- |
| Nullable world/player/connection/camera/render state | Capture the value once into a local, check it, then use that local. Production code must handle lifecycle absence; tests must fail clearly when a required fixture is missing. |
| Nullable entity/path/lookup/result; null unboxing | Check factory/lookup results before casting, dereferencing or unboxing. In tests, use `TestFixtures.require(value, "fixture description")` when capturing a required fixture; use its `level`, `player` and `screen` accessors for client setup. Keep nullable polling/optional results nullable until the expected state arrives; do not turn a wait predicate into a premature failure. |
| Non-null field left uninitialized / conflicting null contracts | Initialize required state; mark genuinely optional state with JSpecify `@Nullable`. Match inherited method and generic contracts. Retain/add `@NullMarked` package contracts deliberately; they do not automatically cover subpackages. |
| Mixin shadow fields / legacy SPI contracts | Respect the injected field or documented SPI contract. Only unavoidable compiler false positives justify a suppression at the smallest practical scope. Never initialize a shadow with a fake runtime object. |
| Gson checks reported as redundant/dead | Reflective JSON loading can still assign null. Keep defensive sanitization; document the narrow suppression at the deserialization boundary. |
| Deprecated methods/atlas constants | Inspect the current replacement and preserve behavior. Reuse `C/AtlasLocations.java` for APIs needing texture paths. Use `M/util/LoadedChunks.java` for non-loading availability checks, not a chunk request that may wait or generate. |
| Raw types / unchecked casts | Preserve generic types; avoid casting through `Object` to evade checking. Isolate unavoidable renderer/mixin bridges and explain why their cast is valid. |
| Unused imports, locals, fields or lambda parameters | Remove genuinely unused code; use Java 25 `_` for unused lambda parameters. Preserve side effects when removing a binding. Fix generated-code warnings in the generator/export, never its generated output. |
| Comparing identical expressions in tests | Test observable behavior with independently derived expectations. Constant-folded comparisons can be tautologies; preserve a meaningful regression assertion rather than suppressing or deleting coverage. |
| Optional Sodium target/class warnings | Gate compatibility mixins before target resolution in `FeatureMixinPlugin`. Validate the relevant renderer path; an absent-Sodium run proves nothing about the installed-Sodium path. |

### Game tests: small, focused and selectively runnable

- Give each test one named behavior or regression and the smallest fixture that proves it. Keep boundary cases together only when they test that same behavior. Do not grow a feature-wide scenario to test one small change.
- Make focused checks independently selectable. Keep selectors synchronized between `build.gradle` and `SelectedClientGameTests.java`; inspect the current dispatcher rather than guessing a selector. Split a required narrow check out of a broad suite when the current selector cannot isolate it.
- Use small non-game checks for pure logic/geometry when they can prove the behavior. Reserve Minecraft integration tests for actual registration, hooks, rendering, networking, persistence or world behavior. Share fixture setup without implicitly running unrelated assertions.
- Run only the checks needed for the changed behavior and affected dependencies. Do not use `-PclientTests=all` or a broad feature suite for a tiny subcase just because it is convenient. Explain any broader regression run by the changed dependency or risk.
- Required fixtures must be asserted, not assumed. Bound asynchronous waits by the condition being tested; clean up test worlds/resources. Never weaken assertions, suppress warnings or return early on a missing fixture to obtain a pass.
- Keep test code/assets out of the released mod. Automated audio muting must remain behind the explicit test flag; loading test classes in an IDE session must not mute normal play.
- **Planned, not implemented:** a test pipeline that skips unnecessary content loading. Keep new tests' setup dependencies explicit and separable so that pipeline can select only required content later. Do not claim the current suite selector skips normal game/content initialization.

The all-source Java warning audit is static analysis; it does not launch the game or run all game tests. Broad warning coverage and narrow runtime testing are separate requirements.

### Basic validation commands

PowerShell from the repository root (use an installed JDK 25 if the local one is absent):

```powershell
$env:JAVA_HOME = (Resolve-Path .tools/jdk-25).Path
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
.\gradlew.bat assemble
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-java-warnings.ps1 -Offline
```

`-Offline` requires cached dependencies; omit it when downloads are needed. The execution-policy option applies only to that PowerShell process. Report actual commands, scope and outcomes; docs-only changes need path/link verification, not a game launch. Consult the relevant testing section before selecting runtime checks.

**STOP: The mandatory section ends here. Do not read further unless your task needs a section below. Search for the relevant heading or feature row; do not read every feature by default.**

## READ WHEN RELEVANT

### Feature map

Paths use `M` and `C` from the layout table. Named hook prefixes refer to the matching `mixin/` directories. Settings/gates and assets are cross-cutting; consult the next two sections when changing them.

| Feature | Minimal current behavior | Start here |
| --- | --- | --- |
| Configuration | Live options, startup feature switches, one-shot presets, search and FPS guidance. | `M/config/`; `C/config/ConfigLayout.java`, `ConfigSearch.java`, `VisualPerformance.java` |
| Blocks, items and plants | Adds End materials, Echo Coral, Lyren plants, moss, clovers and a Four Leaf Clover Luck ingredient. | `M/NaturalityBlocks.java`, `NaturalityItems.java`, `block/`; `src/main/resources/data/naturality/` |
| Overworld decoration | Clover patches, connected tree/floor moss, mountain lakes and pre-flowing waterfalls in newly generated chunks. | `M/worldgen/CloverPatchesFeature.java`, `TreeMossFeature.java`, `MountainWaterfallFeature.java`, `WaterfallFlowPlanner.java` |
| End terrain and biomes | Custom outer-End terrain, layered materials, Lirestone Crags, End Wilds, deposits, boulders and plants. | `M/worldgen/EndTerrainDensity.java`, `EndStoneLayers.java`, `LirestoneCrags.java`, `EndWilds.java`; worldgen data |
| Chorus | Directional inherited flower variants, crowns and sprouts with generated model assets. | `M/chorus/`; `Chorus*` hooks; `resources/block models/` |
| Maddened mobs | Custom Creeper and Zombie variants with combat, rage/cloud behavior, custom models and slowed non-explosion sounds. | `M/entity/Maddened*`; `C/entity/Maddened*`; `M/entity/MaddenedVariants.java` |
| Insanity | Observer-local mob appearance/voice hallucinations with proximity reveal; actual mob identity stays unchanged. | `M/NaturalityEffects.java`; `C/insanity/`; `Insanity*` hooks |
| Confusion | Changes inventory presentation and input mapping while preserving stored stack identities. | `M/confusion/`, `C/confusion/`; `Confusion*` hooks |
| Distortion and End gravity | Gravity inversion, movement, interactions, ceiling navigation and matching camera/entity rendering. | `M/weather/EndGravity.java`, `DistortionState.java`, `CeilingPathNavigation.java`; `EndGravity*`, `Distortion*` hooks |
| Weather and wind | Saved per-dimension weather, temperature/thaw/accumulation, wind physics, foliage motion and particle precipitation. | `M/weather/`, `C/weather/`; `Weather*`, `Wind*` hooks; `PARTICLE_WEATHER.txt` |
| Starfall and Rise | End weather with falling stars/fizzles and rising particles/gravity-related presentation. | `M/starfall/`, `M/weather/EndWeatherSystem.java`; `C/weather/RiseWeather.java`, `C/entity/FallingStarRenderer.java` |
| Sky events | Meteors, rainbows, auroras, Proximity, Blood Moon and End flashes; Blood Moon also changes natural spawning. | `M/sky/`, `C/sky/`; `BloodMoon*` hooks |
| Sky and clouds | Custom atmospheres, stars, sun beams/flare, sunset palette and layered clouds. | `C/sky/`, `C/cloud/`; sky/cloud shaders |
| Lighting and shadows | Pixel-grid terrain lighting, moving lights, configurable darkness and block-grid entity shadows. | `C/lighting/`, `C/shadow/`; `pixel_lighting.glsl` and terrain/lightmap shader overrides |
| Fog and distant detail | Environmental fog, conservative culling, distant texture/detail changes and shader preparation. | `C/fog/`, `C/render/`; `Fog*`, `TerrainFog*`, `SodiumFog*` hooks |
| Water and lava | Procedural textures, surface optics, model intersections, ripples, entry/fall spray, visual tails and heat waves. | `C/fluid/`; fluid/water/lava hooks and shaders |
| Fire | Surface-fitted fire, procedural animation, entity fire and soul-fire tracking. | `M/fire/`, `C/fire/`; `Fire*`, `EntityFire*`, `SoulFire*` hooks |
| Snow | Surface-fitted layers, preserved foliage, overlays, lighting, collision, compaction and worldgen/weather snow. | `M/snow/`, `C/snow/`; `Snow*` hooks; `data/naturality/snow_models.json` |
| Portals | Nether opening/travel, emissive surfaces, glow and model clipping; End portal depth/borders and eye glow. | `M/portal/`, `C/portal/`, `C/portal/end/`; `PORTAL_GLOW.txt` |
| Particles and breaking | Pixel breaking, smoke/flames/leaves/bubbles, impact particles and explosion/potion-cloud presentation. | `M/NaturalityParticles.java`, `M/particle/`, `C/particle/`, `C/breaking/` |
| Glint | Pixel enchantment glint and model/item contours across world, hand, preview and inventory contexts. | `C/glint/`; `Glint*`, `InventoryGlint*` hooks |
| Entity motion and effects | Fish swimming, zombie/floating-mob tilt, ghast physics/deaths, elytra motion, arrows, fireballs and End crystals. | `M/fish/`, `M/ghast/`, `M/entity/`; `C/entity/`; corresponding entity hooks |
| Villager work and fishing | Profession work, animal care, bread, gates/shelter/sleep, doorway coordination and fishing/rope visuals. | `M/villager/`; `C/villager/`; `Villager*`, `Butcher*`, `Fishing*` hooks |
| Reputation and panic | Persistent player reputation, gifts/theft/raid reactions, gossip integration and peaceful-mob crowd panic. | `M/villager/Reputation.java`, `ReputationData.java`, `GossipReactions.java`; `Reputation*`, `CrowdPanic*` hooks |
| Sleep | Accelerates celestial clocks without multiplying world/entity simulation, with synchronized visuals/audio. | `M/sleep/`, `C/sleep/`; `Sleep*` hooks |
| Navigation | Clock/compass HUD and server-provided navigation targets. | `M/navigation/`, `C/navigation/`; `Navigation*` hooks |
| Camera and HUD motion | Airborne camera tilt, elastic hand/item/map motion and inventory pickup/cursor effects. | `C/camera/`; `Airborne*`, `Elastic*`, `HotbarPickup*`, `CarriedItemScale*` hooks |
| Menu previews | Interactive title/world-selection/creation previews with cancellable loading and disposable worlds. | `C/menu/MenuWorld.java`, `MenuPreview.java`, `MenuPreviewLoader.java`, `MenuSurface.java`; `Menu*`, `Title*` hooks |
| Ambience and horror | Cave/End ambience, weather/underwater audio, and local eyes/walker/sprinter impressions. | `C/sound/`, `C/horror/`; weather audio in `C/particle/` and `C/weather/` |

### Configuration, registration and feature gates

Client settings: `M/config/NaturalityConfig.java` -> `config/naturality.json`. Server settings: `NaturalityServerConfig.java` -> `naturality-server.json`. Startup switches: `Feature.java`, `FeatureConfig.java`, `FeatureMixinPlugin.java` -> `naturality-features.json`. Development config lives under `run/config/`. Floating mob tilt has `FloatingMobTiltConfig.java` and its own JSON.

Startup gates are snapshotted before structural mixins/registries load and require restart; live UI edits cannot undo those changes. Preserve parent gates, preset semantics, search/reset support, saved JSON keys and server/client authority. `ConfigPreset` applies ordinary settings once. Weather and sky-event world overrides belong to their SavedData, not installation-wide visual settings. Added content/block-state/entity-data changes need client/server agreement. Disabling content does not migrate existing saves.

### Resources and generated models

Gradle maps `resources/` to `assets/naturality/`, excluding READMEs, placeholders and model-export folders handled by generators. Use one authoritative PNG/OGG source; do not duplicate it under another source set at the same packaged path. Registration, blockstates/models, language, loot, recipes and tags still need their own definitions. Keep intentional vanilla overrides separate.

Read `resources/README.txt` for packaging/audio (Ogg Vorbis; mono for positional sounds), and `resources/entity models/README.txt` for Maddened model hierarchies. Chorus JSON and Maddened Java exports are converted by tasks in `build.gradle`. Other exports are not automatically converted merely because they are in that folder. Regenerate/rebuild after export edits; resource reload alone does not run generators. Check packaged paths for missing/duplicate assets.

### World loading, rendering and saved-world work

Use non-loading chunk availability checks. Mesh workers read bounded snapshots, not mutable client-world fallbacks; unknown/out-of-bounds cells need safe results. Keep worldgen within region write/build-height bounds and bounded work budgets. Clear world-specific caches on world changes and resources on reload/shutdown. Do not synchronously wait on a worker pool whose tasks need render-thread uploads; see `C/render/ShaderPreparation.java`.

Menu previews use temporary/disposable state. Preserve cancellation, stale-result rejection and save-access cleanup even when another close operation fails. Do not test destructive operations against the user's saved worlds. Worldgen changes normally affect newly generated chunks; do not introduce retrofits unless requested.

### Selecting and running checks

The current runtime selector is `SelectedClientGameTests.java`, with accepted names also in `build.gradle`. Example only: `.\gradlew.bat runClientGameTest "-PclientTests=fog,lighting"`. Select the actual narrow checks relevant to the edit; `all` is explicit full regression. `assemble` packages; `build` also runs `checkPortalGlow`. Focused existing subchecks are selectable as `cloud-geometry`, `darkness-config` and `portal-metadata`, without running their enclosing visual/performance suites. No selective content-loading pipeline exists yet.

For renderer changes, distinguish native/Fabric and Sodium-present validation. Optional test dependency: `-PsodiumTestJar=<path>`; do not bundle it. Tests are muted using `naturality.test.muteAudio`; manual launches must remain audible. Report what was actually observed; a successful automated assertion is not a visual inspection or proof that a user's saved-world freeze is fixed.

After build-output/launch changes, run `.\gradlew.bat prepareDebugLaunch`; regenerate VS Code targets with `.\gradlew.bat vscode` as needed. This checks Fabric classpath groups and processed shaders/assets. Restart the client to use new classes. Avoid concurrent Gradle jobs writing the same outputs; alternate build directories still share Loom launch metadata.

### Reading or sharing warning reports

Run **Terminal > Run Task > Fabric: audit Java warnings**, or the MUST READ audit command. Agents can run it themselves. Read `build/java-audit/all/summary.json` first, then filter `problems.json` by `sourceSet`, `path` or `id`; each entry includes line, severity, message and source context. Per-source-set XML/log files remain beside them. Check `completed`, timestamp and `passed`; failed setup must not be mistaken for a clean or current result.

This is a reproducible Eclipse batch audit of saved Gradle Java sources, not a live export of VS Code's Problems panel. Unsaved buffers, other extensions and editor/batch defaults can differ. For example, the batch compiler can report unused lambda parameters that the editor omits. Keep that distinction visible instead of adjusting diagnostics just to match a count. Runtime dependency messages (such as JOML's deprecated Unsafe call) are separate: identify the emitting dependency and a compatible fix rather than silencing logs. `-Scope Production` is a limited diagnostic option, not the final workspace check. Older reports directly under `build/java-audit/` are historical and are not refreshed by the current script.

Sharing a report's path (or attaching its JSON on another machine) replaces pasting hundreds of messages. If an editor-only warning remains unreproduced, request its source/code and one example location before diagnosing it. The policy file `.settings/org.eclipse.jdt.core.prefs` must stay versioned; machine-specific `.vscode/settings.json` should point `java.settings.url` to it.

### Future layout migration template

Use only when a restructuring task is agreed. Keep this temporary block short, separate from the current feature map:

```text
Target layout: <agreed design, or explicitly undecided>
Responsibility boundaries: <common/server, client, consolidated features, framework/items, category or dedicated hooks, visual-data producers/consumers, tests>
Feature | Current owner/path | Target owner/path | State (planned/migrating/current)
Integration updates: <entrypoints, mixin gates/lists, build/source sets, resource IDs>
Compatibility: <saved data/config/network contracts to preserve or migrate>
Verification: <narrow behavior checks plus full Java audit>
```

After migration, replace the current map with verified destinations, remove retired paths and the temporary block, and preserve only continuing behavior/contracts. A rewritten implementation does not need its development history retained here.
