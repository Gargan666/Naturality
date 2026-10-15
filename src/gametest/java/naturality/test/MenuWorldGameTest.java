package naturality.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import naturality.client.menu.MenuPreview;
import naturality.client.menu.MenuWorld;
import naturality.client.menu.MenuWorldCamera;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

public final class MenuWorldGameTest implements FabricClientGameTest {
    private static void checkCloudAngles() {
        var camera = MenuWorld.camera();
        float previousPitch = camera.pitch();
        try (var renderer = new naturality.client.cloud.CloudLayerRenderer()) {
            var settings = new naturality.config.NaturalityConfig.CloudLayer();
            settings.fadingSides = true; settings.fadePixels = 8; settings.thickness = 16; settings.heightOffset = 0;
            var caps = renderer.getClass().getDeclaredField("invisibleCaps"); caps.setAccessible(true);
            for (float pitch : new float[]{-15,-3,-.1F,0,.1F,3,15}) {
                camera.drag(0, pitch - camera.pitch(), 120, 120); camera.suspend();
                for (int y : new int[]{191,200,209}) for (int offset : new int[]{0,96}) {
                    settings.heightOffset = offset;
                    renderer.prepare(-1, net.minecraft.client.CloudStatus.FANCY, 192, 16,
                            new net.minecraft.world.phys.Vec3(0,y,0), 0, 0, settings);
                    int hidden = caps.getInt(renderer);
                    check(((hidden & 1) != 0) == (pitch <= 0), "Cloud top follows angle above the level orbit: " + pitch);
                    check(((hidden & 2) != 0) == (pitch >= 0), "Cloud bottom follows angle below the level orbit: " + pitch);
                }
            }
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        finally { camera.drag(0, previousPitch - camera.pitch(), 120, 120); camera.suspend(); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static MouseButtonEvent mouse(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)); }
    @Override public void runTest(ClientGameTestContext context) {
        int normalStarts = 0, previewStarts = 0;
        var seeds = new java.util.Random(932413);
        for (int seed = 0; seed < 10000; seed++) {
            long eventSeed = seeds.nextLong();
            var normal = new naturality.sky.BloodMoonCycle(eventSeed);
            var preview = new naturality.sky.BloodMoonCycle(eventSeed);
            normal.tick(true, false); preview.tick(true, false, 4);
            normal.tick(true, true); preview.tick(true, true, 4);
            normalStarts += normal.strength(); previewStarts += preview.strength();
        }
        check(previewStarts > normalStarts * 3.5 && previewStarts < normalStarts * 4.5, "Preview nightfall rolls have four times the start probability");
        var oldClouds = new net.minecraft.client.CloudStatus[1];
        int[] oldRange = new int[1];
        var orbit = new MenuWorldCamera();
        orbit.hold(); float start = orbit.yaw(); orbit.advance(1, 1);
        check(Math.abs(orbit.yaw() - start - 3) < .001, "Base rotation continues while held");
        orbit.drag(20, 10, 640, 400); orbit.release(); float released = orbit.yaw(); orbit.advance(.1, 0);
        check(orbit.yaw() != released, "Released drag carries momentum without automatic rotation");
        float before = orbit.yaw(); orbit.advance(.1, 0); float first = orbit.yaw() - before;
        before = orbit.yaw(); orbit.advance(.1, 0);
        check(orbit.yaw() - before < first, "Manual momentum decays");
        context.runOnClient(client -> {
            oldClouds[0] = client.options.cloudStatus().get();
            oldRange[0] = client.options.cloudRange().get();
            client.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
            client.options.cloudRange().set(16);
            MenuPreview.stop(client);
            client.gui.setScreen(new TitleScreen());
        });
        context.waitFor(_ -> MenuPreview.starting() && !MenuPreview.active(), 1200);
        var navigationScreen = new AtomicReference<net.minecraft.client.gui.screens.Screen>();
        context.runOnClient(client -> {
            check(client.gui.canInterruptScreen(), "Preview preparation leaves menu controls interruptible");
            var options = new OptionsScreen(TestFixtures.screen(client), client.options);
            navigationScreen.set(options);
            client.gui.setScreen(options);
            check(!MenuPreview.starting(), "Leaving the requesting screen cancels preparation immediately");
        });
        context.waitTicks(30);
        context.runOnClient(client -> {
            check(client.gui.screen() == navigationScreen.get(), "Cancelled preview cannot steal navigation when its worker finishes");
            check(client.getSingleplayerServer() == null, "Cancelled preparation never attaches a server");
            client.gui.setScreen(new TitleScreen());
        });
        context.waitFor(client -> MenuPreview.ready() && client.player != null, 1200);
        context.waitTicks(100);
        context.runOnClient(client -> {
            MenuSurfaceChecks.verify();
            checkCloudAngles();
            check(TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").overworld().getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
                    "Preview uses real normal world generation");
            for (var entity : TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").overworld().getAllEntities())
                check(!(entity instanceof net.minecraft.world.entity.Mob), "Preview does not spawn any mobs");
            check(TestFixtures.level(client).getChunkSource().hasChunk(MenuPreview.spawn().getX() >> 4, MenuPreview.spawn().getZ() >> 4),
                    "Preview includes the actual spawn region");
            var surface = TestFixtures.level(client).getChunk(MenuPreview.spawn().getX() >> 4, MenuPreview.spawn().getZ() >> 4);
            var lake = new net.minecraft.world.level.chunk.LevelChunk(TestFixtures.level(client), surface.getPos());
            var lakePos = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) for (int y = 0; y <= 70; y++) {
                var state = (y < 50 ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.WATER).defaultBlockState();
                lake.getSection(lake.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
            }
            var buriedChest = new net.minecraft.core.BlockPos(surface.getPos().getMinBlockX() + 8, 48, surface.getPos().getMinBlockZ() + 8);
            var chestState = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
            lake.getSection(lake.getSectionIndex(48)).setBlockState(8, 0, 8, chestState, false);
            lake.getBlockEntities().put(buriedChest, new net.minecraft.world.level.block.entity.ChestBlockEntity(buriedChest, chestState));
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(lake,
                    java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE));
            var lakeShell = naturality.client.menu.MenuSurface.snapshot(lake);
            check(!lakeShell.getBlockEntities().containsKey(buriedChest), "Buried block entities are excluded from surface packets");
            int lx = surface.getPos().getMinBlockX() + 8, lz = surface.getPos().getMinBlockZ() + 8;
            check(lakeShell.getBlockState(lakePos.set(lx, 49, lz)).is(net.minecraft.world.level.block.Blocks.STONE), "Visible lake bed is retained");
            check(lakeShell.getBlockState(lakePos.set(lx, 55, lz)).is(net.minecraft.world.level.block.Blocks.WATER), "Entire transparent water column is retained");
            check(lakeShell.getBlockState(lakePos.set(lx, 22, lz)).isAir(), "Lake shell excludes buried geometry");
            // A tall cliff crosses the packet boundary while its lower neighbor is
            // available to lighting but not yet a FULL/player-visible chunk.
            var cliff = new net.minecraft.world.level.chunk.LevelChunk(TestFixtures.level(client), surface.getPos());
            var valleyPos = new net.minecraft.world.level.ChunkPos(surface.getPos().x() + 1, surface.getPos().z());
            var valley = new net.minecraft.world.level.chunk.LevelChunk(TestFixtures.level(client), valleyPos);
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                for (int y = 0; y <= 100; y++)
                    cliff.getSection(cliff.getSectionIndex(y)).setBlockState(x, y & 15, z, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), false);
                for (int y = 0; y <= 50; y++)
                    valley.getSection(valley.getSectionIndex(y)).setBlockState(x, y & 15, z, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), false);
            }
            for (var chunk : java.util.List.of(cliff, valley))
                net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk,
                        java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE));
            // LightChunk deliberately avoids depending on LevelChunk/FULL status.
            net.minecraft.world.level.chunk.LightChunk lowerTerrain = valley;
            var cliffShell = naturality.client.menu.MenuSurface.snapshot(cliff,
                    (x, z) -> x == valleyPos.x() && z == valleyPos.z() ? lowerTerrain : null);
            int edgeX = cliff.getPos().getMinBlockX() + 15, edgeZ = cliff.getPos().getMinBlockZ() + 8;
            for (int y = 51; y <= 100; y++)
                check(cliffShell.getBlockState(lakePos.set(edgeX, y, edgeZ)).is(net.minecraft.world.level.block.Blocks.STONE),
                        "Exposed chunk-edge cliff survives trimming at Y=" + y);
            check(cliffShell.getBlockState(lakePos.set(edgeX, 23, edgeZ)).isAir(), "Cliff trim still excludes buried terrain");
            check(cliffShell.getBlockState(lakePos.set(edgeX - 3, 70, edgeZ)).isAir(), "Cliff interior is not loaded");
            check(cliffShell.getBlockState(lakePos.set(edgeX - 1, 70, edgeZ)).is(net.minecraft.world.level.block.Blocks.STONE),
                    "Recessed cliff support is retained behind the outer edge");
            // A wide roof / thin overhang must not become a ground cutoff. Its
            // center is deliberately beyond the neighbor envelope's reach.
            var roofed = new net.minecraft.world.level.chunk.LevelChunk(TestFixtures.level(client), surface.getPos());
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                for (int y = 0; y <= 64; y++)
                    roofed.getSection(roofed.getSectionIndex(y)).setBlockState(x, y & 15, z,
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), false);
                for (int y = 90; y <= 91; y++)
                    roofed.getSection(roofed.getSectionIndex(y)).setBlockState(x, y & 15, z,
                            net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), false);
            }
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(roofed,
                    java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE));
            var roofShell = naturality.client.menu.MenuSurface.snapshot(roofed, (_, _) -> null);
            check(naturality.client.menu.MenuSurface.surface(roofed, 8, 8) == 64,
                    "Thin roofs do not replace the supported ground surface");
            check(naturality.client.menu.MenuSurface.surface(roofShell, 8, 8) == 64,
                    "Client shader floor agrees with the source after trimming");
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                check(roofShell.getBlockState(lakePos.set(lx - 8 + x, 64, lz - 8 + z)).is(net.minecraft.world.level.block.Blocks.STONE),
                        "Ground beneath roofs/overhangs has no artificial holes");
                check(roofShell.getBlockState(lakePos.set(lx - 8 + x, 91, lz - 8 + z)).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS),
                        "Roof itself remains intact");
            }
            check(roofShell.getBlockState(lakePos.set(lx, 37, lz)).isAir(), "Deep ground below the conservative shell is still omitted");
            check(surface.getSection(0).hasOnlyAir(), "Preview payload contains no underground/bedrock section");
            int retained = 0;
            for (var section : surface.getSections()) if (!section.hasOnlyAir()) retained++;
            check(retained < surface.getSections().length / 2, "Preview stores only surface sections");
            var buried = new net.minecraft.core.BlockPos(MenuPreview.spawn().getX(), surface.getMinY() + 1, MenuPreview.spawn().getZ());
            surface.setBlockState(buried, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 0);
            check(surface.getBlockState(buried).isAir(), "Late underground updates cannot repopulate the shell");
            int cx = MenuPreview.spawn().getX() >> 4, cz = MenuPreview.spawn().getZ() >> 4;
            int emptyBoundaryChunks = 0;
            for (int z = cz - 7; z <= cz + 7; z++) for (int x = cx - 7; x <= cx + 7; x++) {
                if (MenuPreview.containsChunk(x, z) || !TestFixtures.level(client).getChunkSource().hasChunk(x, z)) continue;
                var outside = TestFixtures.level(client).getChunk(x, z);
                for (var section : outside.getSections())
                    check(section.hasOnlyAir(), "Outer neighbors must expose the fog-wall perimeter");
                emptyBoundaryChunks++;
            }
            check(emptyBoundaryChunks > 0, "Preview has an exposed outer perimeter");
            int cliffBlocks = 0;
            for (int z = cz - 7; z <= cz + 7; z++) for (int x = cx - 7; x <= cx + 7; x++)
                if (MenuPreview.containsChunk(x, z)) {
                    var received = TestFixtures.level(client).getChunk(x, z);
                    check(received.getSection(0).hasOnlyAir(),
                            "No preview payload in the fog footprint contains underground geometry at " + x + "," + z);
                    var original = TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").overworld().getChunkSource().getChunkForLighting(x, z);
                    if (!(original instanceof net.minecraft.world.level.chunk.ChunkAccess full)) continue;
                    for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                        int top = naturality.client.menu.MenuSurface.surface(full, dx, dz);
                        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            int wx = (x << 4) + dx + direction.getStepX(), wz = (z << 4) + dz + direction.getStepZ();
                            var neighbor = TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").overworld().getChunkSource().getChunkForLighting(wx >> 4, wz >> 4);
                            if (!(neighbor instanceof net.minecraft.world.level.chunk.ChunkAccess adjacent)) continue;
                            int foot = naturality.client.menu.MenuSurface.surface(adjacent, wx & 15, wz & 15);
                            for (int y = foot + 1; y <= top; y++) {
                                lakePos.set((x << 4) + dx, y, (z << 4) + dz);
                                var expected = full.getBlockState(lakePos);
                                // Fluid propagation and plant growth can change between the
                                // server snapshot and network delivery. Audit solid cliffs;
                                // the deterministic lake fixture covers fluid retention.
                                if (!expected.canOcclude() || !expected.getFluidState().isEmpty()) continue;
                                check(!received.getBlockState(lakePos).isAir(),
                                        "Generated exposed cliff missing from preview at " + lakePos + " (source " + expected + ")");
                                cliffBlocks++;
                            }
                        }
                    }
                }
            check(cliffBlocks > 0, "Generated terrain includes exposed cliff faces");
            System.out.println("MENU_CLIFF verified exposed blocks=" + cliffBlocks);
            System.out.println("MENU_SURFACE retainedSections=" + retained + "/" + surface.getSections().length);
            check(client.gui.screen() instanceof TitleScreen, "Generated preview retains the menu");
            check(client.gameRenderer.mainCamera().getFluidInCamera() == net.minecraft.world.level.material.FogType.NONE,
                    "Preview camera cannot activate underwater/lava immersion");
            check(client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forAmbientAddition(
                    net.minecraft.sounds.SoundEvents.WEATHER_RAIN)) == net.minecraft.client.sounds.SoundEngine.PlayResult.NOT_STARTED,
                    "Preview cannot play world ambience");
            var masterVolume = client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER);
            var uiVolume = client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.UI);
            double oldMasterVolume = masterVolume.get(), oldUiVolume = uiVolume.get();
            var clickSound = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1);
            try {
                masterVolume.set(1.0); uiVolume.set(1.0);
                check(client.getSoundManager().play(clickSound) == net.minecraft.client.sounds.SoundEngine.PlayResult.STARTED,
                        "Menu button sounds reach the sound engine while the preview is active");
            } finally {
                client.getSoundManager().stop(clickSound);
                masterVolume.set(oldMasterVolume); uiVolume.set(oldUiVolume);
            }
            check(client.levelRenderer.visibleSections().stream().anyMatch(section -> section.getSectionMesh().hasRenderableLayers()),
                    "Native terrain meshes must be visible from the external orbit");
            System.out.println("MENU_NATIVE spawn=" + MenuPreview.spawn() + " camera=" + client.gameRenderer.mainCamera().position()
                    + " sections=" + client.levelExtractor.sectionStatistics()
                    + " visible=" + client.gameRenderer.mainCamera().getCullFrustum().isVisible(
                            new net.minecraft.world.phys.AABB(MenuPreview.spawn()).inflate(16)));
            var screen = TestFixtures.require(client.gui.screen(), "screen");
            float yaw = MenuWorld.camera().yaw(), pitch = MenuWorld.camera().pitch();
            screen.mouseClicked(mouse(15, 15), false);
            screen.mouseDragged(mouse(75, 50), 60, 35);
            check(MenuWorld.camera().yaw() != yaw && MenuWorld.camera().pitch() > pitch, "Drag rotates both axes");
            screen.mouseReleased(mouse(75, 50));
            var button = screen.children().stream().filter(e -> e instanceof Button b && b.getMessage().getString().equals("Options..."))
                    .map(Button.class::cast).findFirst().orElseThrow();
            double x = button.getX() + 15, y = button.getY() + 10;
            screen.mouseClicked(mouse(x, y), false);
            screen.mouseDragged(mouse(x + 40, y - 30), 40, -30);
            screen.mouseReleased(mouse(x + 40, y - 30));
            check(client.gui.screen() == screen, "Dragging over buttons does not activate them");
            screen.mouseClicked(mouse(x, y), false); screen.mouseReleased(mouse(x, y));
            check(client.gui.screen() instanceof OptionsScreen, "Short clicks activate buttons");
            TestFixtures.screen(client).onClose();
            MenuWorld.camera().suspend();
        });
        context.waitTicks(10);
        context.takeScreenshot("menu-native-spawn-day");
        context.runOnClient(client -> {
            var screen = TestFixtures.require(client.gui.screen(), "screen");
            MenuWorld.camera().drag(0, (70 - MenuWorld.camera().pitch()) * screen.height / 120.0,
                    screen.width, screen.height);
            MenuWorld.camera().suspend();
            System.out.println("MENU_NATIVE clouds=" + client.options.getCloudStatus() + " height="
                    + client.gameRenderer.gameRenderState().levelRenderState.cloudHeight);
        });
        context.waitTicks(10);
        context.takeScreenshot("menu-native-cloud-orbit");
        for (float pitch : new float[]{3, 0, -3, -20}) {
            context.runOnClient(client -> {
                var screen = TestFixtures.require(client.gui.screen(), "screen");
                MenuWorld.camera().drag(0, (pitch - MenuWorld.camera().pitch()) * screen.height / 120.0, screen.width, screen.height);
                MenuWorld.camera().suspend();
            });
            context.waitTicks(8);
            context.takeScreenshot("menu-surface-side-" + pitch);
        }
        context.runOnClient(client -> {
            var screen = TestFixtures.require(client.gui.screen(), "screen");
            MenuWorld.camera().drag(0, (70 - MenuWorld.camera().pitch()) * screen.height / 120.0, screen.width, screen.height);
            MenuWorld.camera().suspend();
        });
        var reload = new AtomicReference<CompletableFuture<Void>>();
        context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
        context.waitFor(_ -> reload.get().isDone()); reload.get().join();
        context.waitFor(client -> client.gui.overlay() == null); context.waitTicks(10);
        context.takeScreenshot("menu-native-spawn-reload");
        context.runOnClient(client -> {
            var server = TestFixtures.require(client.getSingleplayerServer(), "server");
            server.execute(() -> {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 18000");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "skyevent minecraft:overworld aurora_borealis 20");
            });
        });
        context.waitTicks(40);
        context.takeScreenshot("menu-native-spawn-night");
        context.runOnClient(client -> {
            var screen = TestFixtures.require(client.gui.screen(), "screen");
            MenuWorld.camera().drag(0, (-15 - MenuWorld.camera().pitch()) * screen.height / 120.0, screen.width, screen.height);
            MenuWorld.camera().suspend();
        });
        context.waitTicks(20);
        context.runOnClient(_ -> {
            check(naturality.client.sky.AuroraRenderer.strength() > 0, "Native sky event reaches the menu world");
            check(naturality.client.sky.AuroraRenderer.drawnPanels() > 0, "Native aurora renderer submits its panels");
        });
        context.takeScreenshot("menu-native-aurora-sky");
        context.runOnClient(client -> {
            check(naturality.weather.PreviewEnvironment.rate(TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()")) == 4, "Only registered previews use boosted climate");
            var server = TestFixtures.require(client.getSingleplayerServer(), "server");
            var spawn = MenuPreview.spawn();
            server.execute(() -> {
                var profile = new naturality.weather.WeatherProfile(true);
                profile.overrideRain = profile.overrideTemperature = true; profile.rain = 50; profile.temperature = 75;
                naturality.weather.WeatherWorldData.get(server).setProfile("minecraft:overworld", profile);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fillbiome "
                        + (spawn.getX()-8)+" "+(spawn.getY()-8)+" "+(spawn.getZ()-8)+" "
                        +(spawn.getX()+8)+" "+(spawn.getY()+8)+" "+(spawn.getZ()+8)+" minecraft:plains");
            });
        });
        context.waitTicks(70);
        context.runOnClient(client -> {
            check(TestFixtures.require(naturality.weather.WeatherSystem.state(TestFixtures.level(client)), "naturality.weather.WeatherSystem.state(client.level)").rain() == 50, "Preview rain approaches its target faster");
            check(naturality.client.weather.ParticleWeather.activeCount() > 0, "Preview precipitation survives outside loaded camera chunks");
            check(naturality.client.weather.ParticleWeather.snowCount() == 0, "Warm preview displays rain");
        });
        context.takeScreenshot("menu-foreground-rain");
        context.runOnClient(client -> {
            var server = TestFixtures.require(client.getSingleplayerServer(), "server");
            server.execute(() -> naturality.weather.WeatherSystem.profile(server.overworld()).temperature = 0);
        });
        context.waitTicks(90);
        context.runOnClient(_ -> check(naturality.client.weather.ParticleWeather.snowCount() > 0, "Cold preview displays near-camera snow"));
        context.takeScreenshot("menu-foreground-snow");
        var retiring = new AtomicReference<net.minecraft.client.server.IntegratedServer>();
        var registryStamp = new AtomicReference<java.nio.file.attribute.FileTime>();
        context.runOnClient(client -> {
            retiring.set(client.getSingleplayerServer());
            try {
                // Fabric writes a registry compatibility index in the server constructor.
                registryStamp.set(java.nio.file.Files.getLastModifiedTime(retiring.get().getWorldPath(
                        net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/fabric/registry_entries.dat")));
            } catch (java.io.IOException error) { throw new RuntimeException(error); }
            long startClose = System.nanoTime();
            MenuPreview.stop(client);
            check(System.nanoTime() - startClose < 1_000_000_000L, "Closing a preview returns without waiting for shutdown");
            check(client.level == null && client.getSingleplayerServer() == null && client.gui.canInterruptScreen(),
                    "Closing detaches the preview immediately and leaves navigation enabled");
        });
        context.waitFor(_ -> retiring.get().isShutdown(), 1200);
        try (var paths = java.nio.file.Files.walk(retiring.get().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT))) {
            var persisted = paths.filter(path -> path.toString().endsWith(".dat") || path.toString().endsWith(".mca"))
                    .filter(path -> !path.endsWith(java.nio.file.Path.of("data/fabric/registry_entries.dat"))).toList();
            check(persisted.isEmpty(), "Preview startup, autosave and shutdown write no world/player/region data: " + persisted);
            check(registryStamp.get().equals(java.nio.file.Files.getLastModifiedTime(retiring.get().getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/fabric/registry_entries.dat"))),
                    "Closing the preview does not rewrite even the startup registry index");
        } catch (java.io.IOException error) { throw new RuntimeException(error); }
        context.waitFor(client -> client.level == null && client.getSingleplayerServer() == null && client.gui.screen() instanceof TitleScreen);
        context.waitTicks(20);
        context.runOnClient(_ -> check(naturality.client.menu.MenuPresetSky.frames > 0, "World-independent native preset sky rendered"));
        context.takeScreenshot("menu-native-preset-sky");
        context.runOnClient(client -> net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(client,
                () -> client.gui.setScreen(new TitleScreen())));
        context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen, 1200);
        context.runOnClient(client -> (TestFixtures.require((naturality.client.mixin.MenuCreateWorldAccess)client.gui.screen(), "(naturality.client.mixin.MenuCreateWorldAccess)client.gui.screen()")).naturality$uiState().setSeed("111"));
        context.waitFor(client -> MenuPreview.starting() && MenuPreview.seed() == 111 && client.getSingleplayerServer() != null, 1200);
        var superseded = new AtomicReference<net.minecraft.client.server.IntegratedServer>();
        context.runOnClient(client -> {
            check(client.gui.canInterruptScreen(), "World creation controls remain usable while preview generation starts");
            superseded.set(client.getSingleplayerServer());
            (TestFixtures.require((naturality.client.mixin.MenuCreateWorldAccess)client.gui.screen(), "(naturality.client.mixin.MenuCreateWorldAccess)client.gui.screen()")).naturality$uiState().setSeed("8675309");
        });
        context.waitFor(_ -> MenuPreview.ready() && MenuPreview.seed() == 8675309 && MenuPreview.frozen(), 1600);
        context.runOnClient(client -> check(client.getSingleplayerServer() != superseded.get(), "Changing the seed replaces the in-flight preview"));
        var time = new long[1];
        var frozenWeather = new naturality.weather.WeatherState[1];
        var frozenEvent = new AtomicReference<java.util.Map<String,Long>>();
        context.waitTicks(20);
        context.runOnClient(client -> {
            time[0] = TestFixtures.level(client).getGameTime();
            frozenWeather[0] = naturality.weather.WeatherSystem.state(TestFixtures.level(client));
            frozenEvent.set(naturality.weather.EnvironmentWorldData.get(TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()"))
                    .read("sky/minecraft:overworld/meteor_shower"));
        });
        context.waitTicks(30);
        context.runOnClient(client -> {
            check(TestFixtures.level(client).getGameTime() == time[0], "Creation preview is frozen");
            check(naturality.weather.PreviewEnvironment.frozen(TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()")), "Preview environment also stays frozen");
            check(java.util.Objects.equals(frozenWeather[0], naturality.weather.WeatherSystem.state(TestFixtures.level(client))), "Creation weather values stay frozen");
            check(frozenEvent.get().equals(naturality.weather.EnvironmentWorldData.get(TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()"))
                    .read("sky/minecraft:overworld/meteor_shower")), "Creation sky-event clocks stay frozen");
            check((TestFixtures.require((naturality.client.mixin.MenuCreateWorldAccess)client.gui.screen(), "(naturality.client.mixin.MenuCreateWorldAccess)client.gui.screen()")).naturality$uiState().getSettings().options().seed() == MenuPreview.seed(),
                    "Preview and actual creation share the chosen seed");
        });
        context.takeScreenshot("menu-create-seed-preview");
        String fixtureId = "naturality-preview-fixture-" + java.util.UUID.randomUUID();
        var fixture = new java.nio.file.Path[1];
        var selectedSpawn = new net.minecraft.core.BlockPos[1];
        var originalData = new byte[1][];
        context.runOnClient(client -> {
            try {
                fixture[0] = client.getLevelSource().getBaseDir().resolve(fixtureId).toAbsolutePath().normalize();
                java.nio.file.Files.createDirectories(fixture[0]);
                var root = new net.minecraft.nbt.CompoundTag();
                root.put("Data", TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").getWorldData().createTag(null));
                selectedSpawn[0] = MenuPreview.spawn().offset(8, 0, 8);
                var player = new net.minecraft.nbt.CompoundTag();
                player.put("respawn", net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                        new net.minecraft.server.level.ServerPlayer.RespawnConfig(net.minecraft.world.level.storage.LevelData.RespawnData.of(
                                net.minecraft.world.level.Level.OVERWORLD, selectedSpawn[0], 0, 0), true)).getOrThrow());
                root.getCompoundOrEmpty("Data").put("Player", player);
                net.minecraft.nbt.NbtIo.writeCompressed(root, fixture[0].resolve("level.dat"));
                var weather = new net.minecraft.nbt.CompoundTag();
                net.minecraft.nbt.NbtUtils.addCurrentDataVersion(weather);
                weather.put("data", net.minecraft.world.level.saveddata.WeatherData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,
                        new net.minecraft.world.level.saveddata.WeatherData(0, 12000, 12000, true, false)).getOrThrow());
                java.nio.file.Files.createDirectories(fixture[0].resolve("data/minecraft"));
                var generation = new net.minecraft.nbt.CompoundTag();
                net.minecraft.nbt.NbtUtils.addCurrentDataVersion(generation);
                generation.put("data", net.minecraft.world.level.levelgen.WorldGenSettings.CODEC.encodeStart(
                        TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                        TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").getWorldGenSettings()).getOrThrow());
                net.minecraft.nbt.NbtIo.writeCompressed(generation, fixture[0].resolve("data/minecraft/world_gen_settings.dat"));
                net.minecraft.nbt.NbtIo.writeCompressed(weather, fixture[0].resolve("data/minecraft/weather.dat"));
                // Use vanilla rain for this fixture; the mod's regional weather otherwise takes precedence.
                var profiles = new naturality.weather.WeatherWorldData();
                profiles.setProfile("minecraft:overworld", new naturality.weather.WeatherProfile(false));
                var weatherProfiles = new net.minecraft.nbt.CompoundTag();
                net.minecraft.nbt.NbtUtils.addCurrentDataVersion(weatherProfiles);
                weatherProfiles.put("data", naturality.weather.WeatherWorldData.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, profiles).getOrThrow());
                java.nio.file.Files.createDirectories(fixture[0].resolve("data/naturality"));
                net.minecraft.nbt.NbtIo.writeCompressed(weatherProfiles, fixture[0].resolve("data/naturality/weather.dat"));
                var events = new naturality.sky.SkyEventWorldData();
                events.override(TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").overworld(), naturality.sky.SkyEventType.AURORA_BOREALIS, true, 13);
                var savedEvents = new net.minecraft.nbt.CompoundTag();
                net.minecraft.nbt.NbtUtils.addCurrentDataVersion(savedEvents);
                savedEvents.put("data", naturality.sky.SkyEventWorldData.TYPE.codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, events).getOrThrow());
                net.minecraft.nbt.NbtIo.writeCompressed(savedEvents, fixture[0].resolve("data/naturality/sky_events.dat"));
                originalData[0] = java.nio.file.Files.readAllBytes(fixture[0].resolve("level.dat"));
            } catch (java.io.IOException error) { throw new RuntimeException(error); }
        });
        context.runOnClient(MenuPreview::stop);
        context.waitFor(client -> client.level == null && client.getSingleplayerServer() == null && client.gui.canInterruptScreen());
        context.waitTicks(10);
        context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(new TitleScreen())));
        context.waitFor(client -> TestFixtures.screen(client).children().stream().filter(e -> e instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList)
                .map(net.minecraft.client.gui.screens.worldselection.WorldSelectionList.class::cast).anyMatch(list -> list.children().stream()
                        .anyMatch(entry -> entry instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry world
                                && TestFixtures.require(world.getLevelSummary(), "world.getLevelSummary()").getLevelId().equals(fixtureId))), 1200);
        context.runOnClient(client -> {
            var list = TestFixtures.screen(client).children().stream().filter(e -> e instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList)
                    .map(net.minecraft.client.gui.screens.worldselection.WorldSelectionList.class::cast).findFirst().orElseThrow();
            list.setSelected(list.children().stream().filter(entry -> entry instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry world
                    && TestFixtures.require(world.getLevelSummary(), "world.getLevelSummary()").getLevelId().equals(fixtureId)).findFirst().orElseThrow());
        });
        context.waitFor(_ -> MenuPreview.ready() && MenuPreview.spawn().equals(selectedSpawn[0]), 1600);
        context.waitTicks(25);
        context.runOnClient(client -> {
            try { check(java.util.Arrays.equals(originalData[0], java.nio.file.Files.readAllBytes(fixture[0].resolve("level.dat"))), "Preview leaves original save byte-for-byte unchanged"); }
            catch (java.io.IOException error) { throw new RuntimeException(error); }
            check(TestFixtures.level(client).isRaining(), "Selected preview retains saved rain state");
            var event = naturality.sky.SkyEventWorldData.get(TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()")).settings(
                    TestFixtures.require(client.getSingleplayerServer(), "client.getSingleplayerServer()").overworld(), naturality.sky.SkyEventType.AURORA_BOREALIS);
            check(event.override && event.strength == 13, "Selected preview retains saved sky events");
        });
        context.takeScreenshot("menu-selected-saved-spawn");
        context.runOnClient(MenuPreview::stop);
        context.waitFor(client -> client.level == null && client.getSingleplayerServer() == null);
        context.runOnClient(client -> {
            client.options.cloudStatus().set(oldClouds[0]);
            client.options.cloudRange().set(oldRange[0]);
        });
        context.waitTicks(10);
    }
}
