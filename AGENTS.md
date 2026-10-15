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
| Feature `mixins/`; small categories in `mixins/<category>/` | Hooks are grouped in inert outer classes, with independently gated nested mixin types. Ordered registry: `src/main/mixins.json`; gating: `M/config/startup/FeatureMixinPlugin.java`. Gradle generates the packaged configurations and metadata. |
| `src/main/resources/` | Metadata, data packs and shared assets. Mod data: `data/naturality/`; vanilla overrides use `data/minecraft/` or `assets/minecraft/`. |
| `src/client/resources/` | Client assets and shader overrides, under `assets/naturality/` and `assets/minecraft/`. |
| `resources/` | Artist inputs: textures, sounds and model exports. Packaging/generation is defined in `build.gradle`. |
| `src/gametest/java/` | All test Java sources. Fabric integration tests live in `naturality/test/`, selected by `SelectedClientGameTests.java`; plain Java checks retain their feature packages and run through `checkPortalGlow`. |
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
| Small vanilla changes versus injection wiring | Keep one implementation file per change, and consolidate application hooks into category containers, such as `ParticleChangesMixin` and `ItemChangesMixin`. Each nested hook retains its target and gate, and delegates to the feature implementation. Add other categories only when needed. |
| Substantial system overhauls | A thorough change such as the weather system may keep dedicated system mixins instead of being forced into a general category mixin. This exception concerns integration hooks; the framework/item and visual-data boundaries still apply. |

Use separate nested hook types for incompatible targets and independent gates. Separate files require a boundary above or a concrete technical need, such as common/client source sets, generated code or an optional external SPI. Preserve gate behavior when combining hooks: disabling one feature must not disable unrelated changes or resolve an absent optional dependency. Keep shared infrastructure at its shared owner. State the concrete reason for additional separation; file length or a preference for more classes is not sufficient by itself.

Mixin source containers must remain inert: use static nested hook classes, retain separate targets/gates, and register their binary names (`Container$Hook`) in `src/main/mixins.json`. Preserve registration order when moving hooks: equal-priority injections can depend on it. The build emits configurations for consecutive package groups; do not hand-edit generated configs or replace their ordering with new priorities. Keep ordinary implementation classes outside configured hook packages.

Apply these rules to new work and agreed restructuring, and keep the feature map synchronized with the actual files. Production-file consolidation does not override the small, focused test rules below. Extend this boundary table in place when the user adds rules; avoid accumulating competing organization instructions.

### Maintaining the overview through rewrites

- Describe what exists now. Verify paths, feature gates and entry points against source; do not copy obsolete claims from older notes.
- A feature-map row contains a short name, one sentence of observable behavior, and its owning package plus a few entry files or hook prefixes. Group related behavior; omit constants, tuning histories, exhaustive file lists and test logs.
- Update the affected row in the same change that adds, removes, renames or moves a feature. Search for old paths/names in registrations, mixin lists, resources, tests, build wiring and documentation. Remove stale entries instead of adding a contradictory update.
- Keep universal rules in MUST READ; put feature details and situational workflows in READ WHEN RELEVANT. Add a warning rule only for a demonstrated recurring issue, with its cause and concrete fix; merge duplicates.
- For a major rewrite, first document the agreed target and responsibility boundaries using the migration template below. Until migrated, show the current location separately from the proposed destination. Update the current map as each part moves; remove the temporary migration map when complete.
- The current layout is feature-owned; see **Layout ownership and future rewrites** below. Keep `M` and `C` separate, with matching relative feature paths where both sides exist. Client-only features need no empty common implementation.
- Put dedicated feature hooks in `<category>/<feature>/mixins/`. Use `mixins/<category>/` only for small, general vanilla changes without a dedicated feature owner; the behavior still belongs to its feature implementation. A hook's target class does not determine its owner: a sun-beam hook into `SkyRenderer` belongs to `sky/overworld/sunbeams/mixins/`.
- Use dimensions where they explain ownership (`sky/overworld/`, `sky/end/`, `worldgen/end/`); put truly cross-dimension code under `shared/`, without duplicating it. System frameworks live under `systems/<system>/framework/` when they have separate items. Feature-specific rendering, audio, networking and helpers follow their feature; reusable APIs go under `api/`.
- Migrate and consolidate one coherent feature at a time into its final folder. Empty destination folders do not mean a feature has moved. Do not bulk-move source files, change resource IDs, or add a new build/source-set layout merely to match the scaffold.

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
| Deprecated methods/atlas constants | Inspect the current replacement and preserve behavior. Reuse `C/api/rendering/AtlasLocations.java` for APIs needing texture paths. Use `M/api/worlds/LoadedChunks.java` for non-loading availability checks, not a chunk request that may wait or generate. |
| Raw types / unchecked casts | Preserve generic types; avoid casting through `Object` to evade checking. Isolate unavoidable renderer/mixin bridges and explain why their cast is valid. |
| Unused imports, locals, fields or lambda parameters | Remove genuinely unused code; use Java 25 `_` for unused lambda parameters. Preserve side effects when removing a binding. Fix generated-code warnings in the generator/export, never its generated output. |
| Comparing identical expressions in tests | Test observable behavior with independently derived expectations. Constant-folded comparisons can be tautologies; preserve a meaningful regression assertion rather than suppressing or deleting coverage. |
| Optional Sodium target/class warnings | Gate compatibility mixins before target resolution in `FeatureMixinPlugin`. Validate the relevant renderer path; an absent-Sodium run proves nothing about the installed-Sodium path. |

### Game tests: small, focused and selectively runnable

- **Creation requires explicit user authorization.** Never create a new game test unless the user specifically requests it or accepts a proposal to create it. If a test seems necessary, explain the behavior it would verify and ask before implementing it. A general feature/fix request is not permission to create game tests; putting a new test in an existing file does not bypass this rule.
- **Authorization to create a game test also authorizes running that test freely.** For an existing game test, ask before running it unless the user has already authorized that test. Once authorized, rerun the same test as often as needed without asking again; honor any later user limits or revocation. Keep authorization scoped to the agreed tests, not other checks or an enclosing suite.
- Give each test one named behavior or regression and the smallest fixture that proves it. Keep boundary cases together only when they test that same behavior. Do not grow a feature-wide scenario to test one small change.
- Make authorized focused checks independently selectable. Register each check once in `src/gametest/resources/naturality-tests.json`, shared by Gradle and `SelectedClientGameTests.java`. Use `listClientTests` to discover IDs and fixture requirements. Groups only name existing checks; do not repeat their assertions inside enclosing suites. If isolating a narrow check requires creating a new game test, obtain creation authorization first.
- Use small non-game checks for pure logic/geometry when they can prove the behavior. Reserve Minecraft integration tests for actual registration, hooks, rendering, networking, persistence or world behavior. Share fixture setup without implicitly running unrelated assertions.
- Run only authorized checks needed for the changed behavior and affected dependencies. Do not use `-PclientTests=all` or a broad feature suite for a tiny subcase just because it is convenient. Explain the need for any broader regression run when requesting authorization for its additional tests.
- Required fixtures must be asserted, not assumed. Bound asynchronous waits by the condition being tested; clean up test worlds/resources. Never weaken assertions, suppress warnings or return early on a missing fixture to obtain a pass.
- Use `TestWorlds.disposable(context)` for ordinary worlds; saving is disabled before creation and through shutdown by the shared `M/api/worlds/DisposableWorlds.java` API. Use `TestWorlds.persistent(context)` explicitly for save/reopen or unload/reload persistence checks. Do not bypass the fixture with a raw `context.worldBuilder()`. Disposable changes disappear on chunk unload; they are not an in-memory save cache.
- Remove obsolete/duplicate checks and simple screenshot-only scenarios that are easy to inspect in game, including their presentation setup and waits. Retain numerical/behavioral regressions and substantial screenshot matrices where manual repetition would be costly. A screenshot by itself is not an automated assertion.
- Keep test code/assets out of the released mod. Automated audio muting must remain behind the explicit test flag; loading test classes in an IDE session must not mute normal play.
- **Planned, not implemented:** a test pipeline that skips unnecessary content loading. Keep new tests' setup dependencies explicit and separable so that pipeline can select only required content later. Do not claim the current suite selector skips normal game/content initialization.

Reading test code, listing selectors, compiling sources and running the all-source Java warning audit do not execute game tests and do not require game-test run authorization. The audit is static analysis; broad warning coverage and authorized narrow runtime testing are separate requirements.

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

Paths use `M` and `C` from the layout table. Each feature’s hooks live in its `mixins/` folder; nested type names retain their individual targets and gates. Settings/gates and assets are cross-cutting; consult the next two sections when changing them.

| Feature | Minimal current behavior | Start here |
| --- | --- | --- |
| Configuration | Live settings, restart-only feature gates, presets, search and FPS guidance. | `M/config/live/NaturalityConfig.java`, `config/startup/FeatureConfig.java`, `config/gameplay/GameplaySettings.java` (nested data/helpers); `C/ui/settings/NaturalityCategoryScreen.java`; `C/config/shaders/FeatureShaderSettings.java` |
| Blocks, items and plants | Adds End materials, Echo Coral, Lyren plants, moss, clovers and a Four Leaf Clover Luck ingredient. | `M/bootstrap/ContentRegistration.java` (independent nested registration groups); `M/blocks/lyrenheath/`, `blocks/chorus/`; `src/main/resources/data/naturality/` |
| Overworld decoration | Clover patches, connected tree/floor moss, mountain lakes and pre-flowing waterfalls in newly generated chunks. | `M/worldgen/overworld/clover/CloverPatchesFeature.java`, `moss/TreeMossFeature.java`, `waterfalls/MountainWaterfallFeature.java` (nested flow planner) |
| End terrain and biomes | Custom outer-End terrain, layered materials, Lirestone Crags, End Wilds, deposits, boulders and plants. | `M/worldgen/end/terrain/EndTerrainDensity.java`, `materials/EndStoneLayers.java`, `lirestonecrags/LirestoneCrags.java`, `endwilds/EndWilds.java` (nested feature helpers); feature `mixins/` and shared `end/mixins/` biome selection; worldgen data |
| Chorus | Directional inherited flower variants, crowns and sprouts with generated model assets. | `M/blocks/chorus/ChorusFlowers.java` (nested sprouts/block implementation); feature `mixins/`; `resources/block models/` |
| Maddened mobs | Custom Creeper and Zombie variants retain their independent registration, combat, models and shared sound/spawn policy. | `M/entities/maddened/MaddenedVariants.java`, `MaddenedCreepers.java`, `MaddenedZombies.java` (nested helpers); `C/entities/maddened/` renderers and separate authored/generated geometry; feature `mixins/` |
| Insanity | Observer-local mob appearance/voice hallucinations with proximity reveal; actual mob identity stays unchanged. | `M/bootstrap/ContentRegistration.java`; `C/effects/insanity/Insanity.java` (nested state/sound contracts), feature `mixins/` |
| Confusion | Changes inventory presentation and input mapping while preserving stored stack identities. | `M/effects/confusion/ConfusionOffhand.java`, `C/effects/confusion/ConfusionSlots.java` (nested animation helpers); feature `mixins/` |
| Distortion and End gravity | Gravity inversion, movement, interactions, ceiling navigation and matching camera/entity rendering. | `M/systems/gravity/EndGravity.java` (nested navigation/teleport helpers), `C/systems/gravity/GravityPreviewContext.java`; gravity `mixins/`; `M/effects/distortion/` state contract and effect `mixins/` |
| Weather framework | Saved per-dimension weather, commands, networking, climate queries and shared client context. | `M/systems/weather/framework/WeatherSystem.java`, `C/systems/weather/framework/WeatherClient.java` (nested framework helpers); each side's `mixins/` |
| Wind | Foliage deformation, collision/pushing, particle motion and crossfading wind audio. | `M/systems/weather/wind/WindPush.java`, `C/systems/weather/wind/WindRendering.java` (nested feature helpers); each side's `mixins/` |
| Precipitation | Bounded rain/snow particle cards share spawning, motion and sway. | `C/systems/weather/overworld/precipitation/ParticleWeather.java` (nested particle/sway), feature `mixins/`; shared `C/api/geometry/ParticleGeometry.java` |
| Snow weather | Rain-channel accumulation thresholds, temperature thaw and bounded natural snowfall writes. | `M/systems/weather/overworld/snow/WeatherSnow.java` (nested thaw/budget), feature `mixins/` |
| Lightning | Distant strikes, flash rendering and listener-local delayed thunder. | `M/systems/weather/overworld/lightning/DistantLightning.java`, `C/systems/weather/overworld/lightning/DistantLightningClient.java` (nested feature helpers); each side's `mixins/` |
| Rain | Rain cycle registration, heavy-rain fog and separate particle-tint shaders. | `M/systems/weather/overworld/rain/RainWeather.java`; `C/systems/weather/overworld/rain/HeavyRainFog.java`, `RainParticleTint.java`, feature `mixins/`; `PARTICLE_WEATHER.txt` |
| End-weather framework | Synchronizes and saves End gravity strength, Starfall and Rise cycles. | `M/systems/weather/end/framework/EndWeatherSystem.java` (nested cycle, state and payload) |
| Rise | End weather emits a bounded volume of rising, collision-tested particles. | `C/systems/weather/end/rise/RiseWeather.java` (nested particle), `RiseRenderLayer.java` |
| Starfall | End weather spawns falling stars and fizzles. | `M/systems/weather/end/starfall/StarfallWeather.java` (nested entities/registration); `C/systems/weather/end/starfall/FallingStarRenderer.java` (nested trail particle) |
| Aurora | Regional Overworld/End curtains with climate-dependent cycles and Blood Moon palettes. | `M/sky/shared/aurora/AuroraEvents.java` (nested cycle); `C/sky/shared/aurora/AuroraGeometry.java`, `AuroraRenderer.java`; shared fog-composite render hook |
| Blood Moon | Nightfall event tints moon/skylight and increases natural spawning. | `M/sky/overworld/bloodmoon/BloodMoonCycle.java` (nested spawning policy), `C/sky/overworld/bloodmoon/BloodMoonClient.java`; feature `mixins/` |
| Proximity | A persistent multi-night planet appearance with synchronized position and dusk/dawn fading. | `M/sky/overworld/proximity/ProximityCycle.java`, `C/sky/overworld/proximity/ProximityRenderer.java`; shared sky-event render hook |
| Rainbow | Rain-dependent daytime event with a paletted sprite opposite the sun. | `M/sky/overworld/rainbow/RainbowCycle.java`, `C/sky/overworld/rainbow/RainbowRenderer.java`; shared sky-event render hook |
| End flashes | Shared End flash state drives sky pulses, lightmap contribution and sound. | `C/sky/end/flashes/EndFlashes.java`, feature `mixins/`; shared sky-event cycle |
| Meteors | Nighttime sprite trails with occasional stronger showers. | `C/sky/overworld/meteors/MeteorShower.java`; shared sky-event cycle/render hook |
| Sky-event framework | Synchronizes per-dimension cycles, commands and saved world overrides. | `M/systems/skyevents/framework/SkyEvents.java` (nested descriptors, cycles, settings, save data, payload and commands); `C/systems/skyevents/framework/SkyEventsClient.java`, framework `mixins/` |
| Sun beams and lens flare | Sun-aligned beams and camera flare with source-texture color and occlusion. | `C/sky/overworld/sunbeams/SunBeams.java` (nested `SunLensFlare`); feature `mixins/` |
| Clouds | Layered cloud shells, moon lighting and sun-flare occlusion. | `C/sky/shared/clouds/CloudLayerRenderer.java` (nested lighting/occlusion helpers), `CloudPipelines.java`, feature `mixins/` |
| Sky atmosphere | Custom atmospheres, stars and sunset palette. | `C/sky/shared/atmosphere/SkyGradientRenderer.java`, feature `mixins/`; `C/sky/overworld/sunset/SunsetPalette.java`; `C/sky/end/atmosphere/EndSkyRenderer.java`; `C/sky/overworld/stars/StarGeometry.java`; sky shaders |
| Fluid textures | Shared water/lava procedural texture simulation and terrain shader integration. | `C/fluids/shared/ProceduralFluids.java` (nested simulation), feature `mixins/`; shared atlas lifecycle hook in `C/api/rendering/mixins/` |
| Liquid geometry | Shared connected-face, surface-contact and posed-model intersection geometry for water and lava. | `C/fluids/shared/FluidGeometry.java` (nested geometry helpers) |
| Liquid fall API | Shared bounded fall scanning, spray budgets, stream-following tails and droplet contact. | `C/fluids/shared/FallingFluidTail.java` (nested helpers/contracts) |
| Water | Water optics, depth maps, surface foam, ripples, entry/fall spray and underwater particles. | `C/fluids/water/WaterVisuals.java` (map producer), `WaterShaders.java` (nested visual data/pipelines), `WaterComposite.java` and `WaterIntersection.java` (nested consumers); feature `mixins/` |
| Lava | Lava surface contacts, ripples and molten entry/fall spray with separate color/geometry/shader definitions. | `C/fluids/lava/LavaEffects.java` (nested visual data/pipelines), `LavaIntersection.java` (nested consumers); feature `mixins/` |
| Heat waves | Screen distortion from nearby lava and fire, with bounded heat-volume scanning. | `C/rendering/heatwaves/HeatWaves.java`, separate `HeatWavePipeline.java`; shared render integration |
| Fire | Surface-fitted fire, procedural animation, entity fire and soul-fire tracking. | `M/blocks/fire/FireGeometry.java` (nested surface geometry); `C/blocks/fire/SurfaceFireModel.java` (nested entity/connection helpers), `ProceduralFire.java` (nested simulation/palette); feature `mixins/` |
| Snow | Surface-fitted layers, preserved foliage, overlays, lighting, collision, compaction and worldgen/weather snow. | `M/blocks/snow/SnowGeometry.java`, `SnowBlockModels.java`; `C/blocks/snow/SurfaceSnowModel.java`, `SnowTextureAtlas.java` (nested helpers); feature `mixins/`; `data/naturality/snow_models.json` |
| Nether opening | Synchronizes ignition, opening geometry and timing. | `M/portals/nether/opening/PortalOpeningManager.java`, `C/portals/nether/opening/PortalOpeningClient.java` (nested state/payload helpers), separate `PortalOpeningRenderLayer.java`; feature `mixins/` |
| Nether travel | Physical portal crossing, arrival, clipped models and overlays. | `M/portals/nether/travel/PortalCrossing.java`, `C/portals/nether/travel/PortalCrossingClient.java` (nested travel/clip helpers), separate `PortalEntityGlowLayer.java`; feature `mixins/` |
| Nether glow | Emissive portal surfaces, palette-based rim glow and velocity-aligned motes. | `C/portals/nether/glow/PortalGlowController.java` (nested particles), `PortalGlowGeometry.java` (nested data/pipelines); feature `mixins/`; `PORTAL_GLOW.txt` |
| Portal audio | Positional portal audio, mono buffers and sound lifecycle. | `C/portals/nether/audio/PortalSoundInstance.java`; feature `mixins/` |
| End portals | Parallax borders, smoke and inserted-eye glow. | `C/portals/end/EndPortalBorderState.java` (nested consumers), `EndPortalBorderLayers.java` (nested visual data); feature `mixins/` |
| Smoke | Long-lived animated smoke with buoyant drift and collisions. | `C/particles/smoke/SmokeParticle.java` |
| Flame | Animated flame particles with their existing provider fallback. | `C/particles/flame/AnimatedFlameParticle.java` |
| Leaves | World-oriented drifting leaves that settle on ground and water; state remains with the particle target’s protected fields. | `C/particles/leaves/mixins/FallingLeavesMixin.java` |
| Sand | Placement dust and sand sprite state; terrain-fragment hooks apply the shared particle changes. | `C/particles/sand/SandPlacementBurst.java` (nested sprite state), client event hook; shared `M/mixins/particles/` and `C/mixins/particles/` |
| Explosions | Flat explosion visuals retain server strength and End ritual context. | `M/particles/explosions/EndExplosionContext.java`, `C/particles/explosions/FlatExplosionParticle.java`; particle category hooks |
| Potion clouds | Flat tinted smoke for lingering potions and dragon clouds. | `C/particles/potionclouds/FlatPotionSmokeParticle.java`; particle category hook |
| Death smoke | Corpse-shaped poof coverage and pixel death state, with separate posed-model sampling. | `C/particles/deathsmoke/DeathSmoke.java` (nested particle/state), `DeathSmokeLayer.java`; independently gated feature `mixins/` |
| Dynamic lighting | Moving virtual light sources use a separate occluded fractional light-field producer. | `C/rendering/lighting/dynamic/DynamicLighting.java` (nested bell wrapper), `FractionalLightField.java` (nested placement); feature `mixins/` |
| Hardcore lighting | Shared moonlight and darkness controls across sky, clouds, fog and the lightmap. | `C/rendering/lighting/hardcore/HardcoreDarkness.java`; feature `mixins/` |
| Distant detail | Section detail hysteresis and distance-aware model wrapping use a separate texture producer. | `C/rendering/distantdetail/RenderDetail.java` (nested model), `DistantTextures.java` |
| Culling | Fog and conservative whole-box occlusion culling with optional renderer hooks. | `C/rendering/culling/FogCulling.java` (nested occlusion); feature `mixins/` |
| Fog | Terrain-to-atmosphere composition, distance spans and pixel fog retain separate pipeline definitions. | `C/rendering/fog/EndFogComposite.java` (nested rules/span), `FogPipeline.java`; feature `mixins/` |
| Glint | Visible silhouette glint for items, equipment, hands and inventory previews, with separate shaders. | `C/rendering/glint/GlintContours.java` (nested inventory consumer), `GlintPipelines.java`; feature `mixins/` |
| Shadows | Block-grid shadow texture generation is separate from surface placement. | `C/rendering/shadows/BlockGridShadow.java`, `ShadowRendering.java`; feature `mixins/` |
| Breaking | Position-specific breaking textures and texel fragment masks with separate data generation and application. | `C/rendering/breaking/BreakingTextures.java`, `BreakingEffects.java`, nested fragment contract; feature `mixins/` and particle category hooks also apply sand fragment behavior |
| Creeper biomes | Remembers a Creeper’s spawn biome and applies the matching texture/tint. | `M/entities/creepers/CreeperBiome.java`, `C/entities/creepers/CreeperTintState.java`; feature `mixins/` |
| Fish motion | Burst/idle swimming and matching client body motion. | `M/entities/fish/FishMoveControl.java`, `C/entities/fish/FishSwim.java` (nested state); feature `mixins/` |
| Shared entity motion | Shared movement easing/state used by ghast physics, rider attachment and configurable floating-mob tilt. | `M/api/motion/GhastMotion.java` |
| Ghasts | Ghast squash, tentacle dynamics, rider lean and authoritative death flight with deferred loot. | `M/entities/ghasts/GhastCrash.java`, `C/entities/ghasts/GhastSquash.java` (nested helpers/renderers); feature `mixins/`; shared motion API |
| Zombie motion | Client zombie walking pose and state. | `C/entities/zombies/ZombieWalk.java` (nested state), feature `mixins/` |
| Floating-mob tilt | Configurable movement lean for floating living entities. | `C/entities/floatingmobtilt/FloatingMobTilt.java` (nested state), feature `mixins/`; shared motion API |
| Embedded arrows | Synchronizes impact positions and attaches embedded arrows to posed mob models. | `M/entities/arrows/ArrowImpactPayload.java`, `C/entities/arrows/MobArrowLayer.java` (nested impacts/state); feature `mixins/` |
| Fireballs | Solid fireball cores, separate scrolling shell visuals and synchronized impact metadata. | `M/entities/fireballs/FireballImpactPayload.java`, feature `mixins/`; `C/entities/fireballs/FireballModelRenderer.java` (nested impact receiver), `FireballShell.java` |
| End crystals | Crystal bobbing and orbiting text with separate emissive core geometry. | `C/entities/endcrystals/CrystalVisuals.java` (nested text particle), `CrystalCoreModel.java`, feature `mixins/` |
| Crowd panic | Shares the vanilla damage stimulus among nearby matching peaceful mobs without extra damage. | `M/entities/crowdpanic/mixins/CrowdPanicMixin.java` (requires access to injected damage state) |
| Elytra motion | Elytra momentum, matching FOV and cape motion with independent gates. | `M/entities/elytra/mixins/`, `C/entities/elytra/mixins/` |
| Villager professions | Separate farmer/fisherman, butcher and animal-care work items, with shared farmer supplies nested in ProfessionWork. | `M/systems/villagerwork/professions/ProfessionWork.java`, `ButcherWork.java`, `AnimalCareWork.java`; feature `mixins/` |
| Villager framework | Registers profession work and shares doorway coordination and held-tool render state. | `M/systems/villagerwork/framework/VillagerWork.java` (nested visuals/doorway coordination), `C/systems/villagerwork/framework/VillagerVisualState.java`; feature `mixins/` |
| Villager bread | Evening bread sharing and saved activity state; its shared hook also ticks snow shelter. | `M/systems/villagerwork/bread/VillagerBread.java`; associated feature hooks |
| Villager gates | Villager gate traversal and safe closing. | `M/systems/villagerwork/gates/VillagerGates.java`; associated feature hooks |
| Villager shelter | Villagers seek shelter during snow; invoked by the shared bread/shelter hook. | `M/systems/villagerwork/shelter/VillagerSnowShelter.java`; associated feature hooks |
| Villager sleep | Villager sleep scheduling using shared reputation state and its existing tick hook. | `M/systems/villagerwork/sleep/VillagerSleep.java`; associated feature hooks |
| Fishing | Player/villager bobbers, fishing spots, returning catches and rod/line placement using the shared rope API. | `M/systems/fishing/VillagerBobbers.java` (nested entities/spot search), `C/systems/fishing/FishingLineRendering.java` (nested renderers/rod state), separate `FishingBobberModel.java`; feature `mixins/` |
| Reputation | Persistent player reputation, gifts/theft/raid reactions and gossip integration. | `M/systems/reputation/Reputation.java` (nested storage/state/reactions/command), feature `mixins/` |
| Sleep | Accelerates celestial clocks without multiplying world/entity simulation, with synchronized visuals/audio. | `M/systems/sleep/SleepAcceleration.java`, `C/systems/sleep/SleepVisuals.java` (nested feature helpers); each side's `mixins/`; `naturality.sleep*.mixins.json` |
| Navigation | Clock/compass HUD and server-provided navigation targets. | `M/systems/navigation/NavigationInstruments.java` (nested target payload), `C/systems/navigation/NavigationClient.java`, client feature `mixins/` |
| Camera and HUD motion | Airborne tilt, elastic hands and inventory pickup/cursor scaling. | `C/ui/camera/airborne/AirborneTilt.java`, `ui/camera/hands/ElasticMotion.java` (nested state/pose); feature `mixins/`; small implementations in `C/ui/items/`, hooks in `C/mixins/items/` |
| Menu previews | Interactive title/world-selection/creation previews with cancellable loading and disposable worlds. | `C/ui/menus/previews/MenuPreview.java` (nested loader/camera/sky), `MenuSurface.java` (nested saved-region/fog data); feature `mixins/`; logo application in `C/ui/menus/title/TitleLogo.java` |
| Shared rope API | Persistent Verlet chains and camera-facing line emission reused by fishing and leads. | `C/api/ropes/RopeChain.java` (nested solver), `RopeRendering.java`, separate `RopePipelines.java`; lead consumer `C/entities/leads/PhysicsLeadRendering.java`, feature `mixins/` |
| Shared model geometry | Posed model capture, alpha-cut flat cubes and planar clipping reused by portals and liquid intersections; particle bases preserve each caller’s axis convention. | `C/api/geometry/ModelCapture.java` (nested alpha sampler), `PlanarGeometry.java` (nested section rasterizer), `ParticleGeometry.java`; API `mixins/` |
| Shared rendering API | Atlas texture paths and an independent shader-preparation executor reused across visual features. | `C/api/rendering/AtlasLocations.java`, `ShaderPreparation.java`, API `mixins/` |
| Shared world queries | Non-loading chunk checks and conservative raycast shape-presence queries reused by fire, snow and wind. | `M/api/worlds/LoadedChunks.java`, `M/api/geometry/RaycastShapePresence.java`, `ShapeRecursionGuard.java`, geometry `mixins/` |
| Shared environment state | Stores environment snapshots and supplies preview clock policy across weather and sky systems. | `M/api/worlds/EnvironmentWorldData.java`, `PreviewEnvironment.java` |
| Shared randomness | Persistable random sequences and the stable block-coordinate hash used across features. | `M/api/random/SavedRandom.java` (nested `BlockSeed`) |
| Disposable world storage | Explicit temporary-world scopes discard save writes for previews and tests while ordinary saves remain persistent. | `M/api/worlds/DisposableWorlds.java`; nested targets in `M/api/worlds/mixins/DisposableWorldMixin.java`; test adapter `src/gametest/java/naturality/test/TestWorlds.java` |
| Ambient horror | Local eyes, walker and sprinter impressions with client commands. | `C/effects/ambienthorror/AmbientHorror.java` (nested commands), `HorrorEyesLayer.java` (nested eye variants); shared particle render hook |
| Ambience | Cave/End ambience and underwater muffling; shared audio hooks also apply weather and sleep filtering. | `C/audio/ambience/caves/CaveAmbience.java`, `audio/ambience/end/EndAmbience.java`, `audio/underwater/UnderwaterAudio.java`; `C/api/audio/`; wind audio stays with wind |

### Configuration, registration and feature gates

Client settings: `M/config/live/NaturalityConfig.java` -> `config/naturality.json`; its nested presets and floating-mob configuration retain their existing JSON. Server authority, server config and its network snapshot live in `M/config/gameplay/GameplaySettings.java` -> `naturality-server.json`. Startup switches and resource policy live in `M/config/startup/FeatureConfig.java`; its nested `Feature` enum and the separate `FeatureMixinPlugin.java` use `naturality-features.json`. Development config lives under `run/config/`.

Startup gates are snapshotted before structural mixins/registries load and require restart; live UI edits cannot undo those changes. Preserve parent gates, preset semantics, search/reset support, saved JSON keys and server/client authority. `ConfigPreset` applies ordinary settings once. Weather and sky-event world overrides belong to their SavedData, not installation-wide visual settings. Added content/block-state/entity-data changes need client/server agreement. Disabling content does not migrate existing saves.

### Resources and generated models

Gradle maps `resources/` to `assets/naturality/`, excluding READMEs, placeholders and model-export folders handled by generators. Use one authoritative PNG/OGG source; do not duplicate it under another source set at the same packaged path. Registration, blockstates/models, language, loot, recipes and tags still need their own definitions. Keep intentional vanilla overrides separate.

Read `resources/README.txt` for packaging/audio (Ogg Vorbis; mono for positional sounds), and `resources/entity models/README.txt` for Maddened model hierarchies. Chorus JSON and Maddened Java exports are converted by tasks in `build.gradle`. Other exports are not automatically converted merely because they are in that folder. Regenerate/rebuild after export edits; resource reload alone does not run generators. Check packaged paths for missing/duplicate assets.

### World loading, rendering and saved-world work

Use non-loading chunk availability checks. Mesh workers read bounded snapshots, not mutable client-world fallbacks; unknown/out-of-bounds cells need safe results. Keep worldgen within region write/build-height bounds and bounded work budgets. Clear world-specific caches on world changes and resources on reload/shutdown. Do not synchronously wait on a worker pool whose tasks need render-thread uploads; see `C/api/rendering/ShaderPreparation.java`.

Menu previews use temporary/disposable state. Preserve cancellation, stale-result rejection and save-access cleanup even when another close operation fails. Do not test destructive operations against the user's saved worlds. Worldgen changes normally affect newly generated chunks; do not introduce retrofits unless requested.

### Selecting and running checks

Run `.\gradlew.bat listClientTests` without launching Minecraft, then obtain any missing run authorization under the MUST READ rules before executing exact IDs, e.g. `.\gradlew.bat runClientGameTest "-PclientTests=cloud-geometry,fluid-simulation"`. Command examples are not authorization. `SelectedClientGameTests.java` lazily loads selected check classes, expands groups and deduplicates IDs; `all` is explicit full regression. `assemble` packages; `build` also runs `checkPortalGlow`. No selective content-loading pipeline exists yet: fixture-free checks in this client runner still pay normal game startup; use non-game checks where possible.

The catalog records `id`, implementation `class` (in `naturality.test`) and `fixture` (`none`, `client`, `disposable`, `persistent` or `preview`). Default invocation is a `FabricClientGameTest`; optional `invoke` and `method` select a static no-argument check (`static`), a no-argument check on the client thread (`client`), a method accepting `ClientGameTestContext` (`context`), or a method accepting `Minecraft` on the client thread with a disposable world (`world`). Keep method checks beside their owning test rather than creating tiny files. Fixture metadata describes setup requirements; each test must use the corresponding fixture API. Preserve compatibility group names only as aliases of independently runnable checks. Plain Java checks share the `gametest` source set but remain outside this Minecraft-launching catalog: `checkPortalGlow` runs their existing entrypoint directly. `gametestClasses` compiles both kinds without running either.

`DisposableWorlds.open(root)` must run before opening that world's storage. Hold its scope until the server and storage workers have stopped; only then close it. The API blocks metadata, player, saved-data, chunk/entity/POI and Fabric registry save paths; native directories/session locks can still be created. Menu previews register their temporary copy, never the user's source save. Normal worlds are not registered. Startup failure must retain protection while workers may still exist. Dedicated checks: `disposable-world` verifies absence of saved data through shutdown, `world-persistence` saves/reopens with explicit opt-in, and `menu-no-save` exercises preview integration. Maintain these hooks against matching Minecraft/Fabric sources when upgrading.

For renderer changes, distinguish native/Fabric and Sodium-present validation. Optional test dependency: `-PsodiumTestJar=<path>`; do not bundle it. Tests are muted using `naturality.test.muteAudio`; manual launches must remain audible. Report what was actually observed; a successful automated assertion is not a visual inspection or proof that a user's saved-world freeze is fixed.

After build-output/launch changes, run `.\gradlew.bat prepareDebugLaunch`; regenerate VS Code targets with `.\gradlew.bat vscode` as needed. This checks Fabric classpath groups and processed shaders/assets. Restart the client to use new classes. Avoid concurrent Gradle jobs writing the same outputs; alternate build directories still share Loom launch metadata.

### Reading or sharing warning reports

Run **Terminal > Run Task > Fabric: audit Java warnings**, or the MUST READ audit command. Agents can run it themselves. Read `build/java-audit/all/summary.json` first, then filter `problems.json` by `sourceSet`, `path` or `id`; each entry includes line, severity, message and source context. Per-source-set XML/log files remain beside them. Check `completed`, timestamp and `passed`; failed setup must not be mistaken for a clean or current result.

This is a reproducible Eclipse batch audit of saved Gradle Java sources, not a live export of VS Code's Problems panel. Unsaved buffers, other extensions and editor/batch defaults can differ. For example, the batch compiler can report unused lambda parameters that the editor omits. Keep that distinction visible instead of adjusting diagnostics just to match a count. Runtime dependency messages (such as JOML's deprecated Unsafe call) are separate: identify the emitting dependency and a compatible fix rather than silencing logs. `-Scope Production` is a limited diagnostic option, not the final workspace check. Older reports directly under `build/java-audit/` are historical and are not refreshed by the current script.

Sharing a report's path (or attaching its JSON on another machine) replaces pasting hundreds of messages. If an editor-only warning remains unreproduced, request its source/code and one example location before diagnosing it. The policy file `.settings/org.eclipse.jdt.core.prefs` must stay versioned; machine-specific `.vscode/settings.json` should point `java.settings.url` to it.

### Layout ownership and future rewrites

Common/server code stays under `M`; client code stays under `C`. Relative ownership paths mirror one another where applicable. Entrypoints remain at their source roots; `bootstrap/` contains shared registration wiring. Real source files now preserve the layout, so no empty-folder scaffold is needed.

| Parent | Ownership |
| --- | --- |
| `sky/` | Sky visuals and their individual event behavior, grouped by dimension or `shared/`. |
| `systems/` | Weather, sky-event orchestration, gravity, villager work, reputation, sleep and navigation. |
| `worldgen/` | Dimension-specific terrain, biomes and decoration placement. |
| `blocks/` | Coherent block features such as chorus, fire and snow. |
| `entities/` | Mob/projectile features, their behavior and presentation. |
| `fluids/` | Water/lava features and their shared liquid code. |
| `particles/` | Independent particle changes; particles belonging to weather, portals or another feature follow that owner. |
| `portals/` | Nether and End portal features, including their particles/audio. |
| `effects/` | Status effects and local hallucination/horror features. |
| `rendering/` | General lighting, shadows, fog, culling, distance detail, glint and breaking visuals. |
| `ui/` | Menus, preview worlds, settings screens, camera and HUD features. |
| `audio/` | Standalone ambience/underwater audio; feature-specific sound stays with its feature. |
| `api/` | Proven cross-feature libraries, including disposable worlds, chunk access, geometry and rendering helpers. |
| `bootstrap/`, `config/` | Shared mod wiring and configuration infrastructure. |
| `mixins/` | Small general application hooks: `particles/` on both sides and client `items/`. Add a category only when it has real hooks. |

For another major rewrite, add a temporary migration table here with **current owner/files**, **proposed destination**, **responsibility boundary**, and **status**. Describe both current and proposed paths until that feature moves. Migrate one coherent feature at a time, update its current feature-map row, and remove completed rows. Remove the temporary table once complete. Create folders only for real ownership; no speculative empty dimensions or generic `misc`/`utils` buckets.

Before migrating, trace imports/callers to assign shared files once. Keep data/shaders separate from their consumers and framework code separate from system items. Preserve common/client source roots and test source sets.

**Integration checklist for each migration:** update Java packages/imports, accessors, entrypoints, mixin configuration/metadata and gates, reflection/class-name strings, generators, tests and documentation. Preserve package null contracts in new packages. Generated mixin configurations must target dedicated hook packages; do not widen their package root over ordinary feature classes. Update `src/main/mixins.json` without changing hook order. Keep shaders/assets in their required resource source sets and preserve packaged identifiers; physical Java proximity does not justify relocating resources or copying them into Java folders. Keep tests in their existing source sets.

**Compatibility and validation:** preserve config keys, save formats, network/resource IDs, feature gates and client/server separation. Compile and run the all-source warning audit for code moves; execute game tests only under the MUST READ authorization rules. Mark a row migrating/current only when its real implementation changes, update the current feature map in the same change, and remove completed migration rows rather than retaining a history.
