package naturality.test;

import naturality.client.fluid.FluidSimulation;
import naturality.client.fluid.ProceduralFluids;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Real terrain shader checks, including OIT alpha passes and resource reload. */
public final class FluidsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var previousLiquids = naturality.config.NaturalityConfig.get().liquids;
        boolean[] saved = new boolean[2];
        context.runOnClient(client -> {
            naturality.config.NaturalityConfig.get().liquids = new naturality.config.NaturalityConfig.Liquids();
            saved[0] = client.options.improvedTransparency().get();
            saved[1] = client.gui.hud.isHidden();
            if (!saved[1]) client.gui.hud.toggle();
        });
        try (var world = TestWorlds.disposable(context).create()) {
            context.runOnClient(client -> {
                client.gui.setScreen(new naturality.client.config.NaturalityConfigScreen(new net.minecraft.client.gui.screens.TitleScreen()));
                press(client.gui.screen(), "Blocks and Effects");
                press(client.gui.screen(), "Liquids");
                press(client.gui.screen(), "Water changes:");
                press(client.gui.screen(), "Lava changes:");
                press(client.gui.screen(), "Custom flowing lava:");
                var settings = naturality.config.NaturalityConfig.get().liquids;
                if (settings.water || settings.lava || settings.flowingLava) throw new AssertionError("Liquid toggles did not apply");
                resetControls(client.gui.screen());
                if (!settings.water || !settings.lava || !settings.flowingLava) throw new AssertionError("Liquid resets did not apply");
            });
            context.takeScreenshot("fluids-settings");
            context.runOnClient(client -> {
                TestFixtures.screen(client).onClose();
                TestFixtures.screen(client).onClose();
                TestFixtures.screen(client).onClose();
            });
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
            server.runCommand("time set 6000");
            server.runCommand("tp @a 0 110 -10 0 45");
            server.runOnServer(s -> {
                for (int x = -3; x <= 3; x++) for (int z = -2; z <= 3; z++) s.overworld().getChunk(x, z);
            });
            server.runCommand("fill -25 96 -5 25 99 30 stone");
            server.runCommand("fill -24 100 -4 -2 100 29 stone");
            server.runCommand("fill 2 100 -4 24 100 29 stone");
            server.runCommand("fill -23 100 -3 -3 100 28 water");
            server.runCommand("fill 3 100 -3 23 100 28 lava");
            // Elevated narrow troughs spill over the front edge, exposing flowing tops and sides.
            server.runCommand("fill -18 101 14 -10 106 19 stone");
            server.runCommand("fill 10 101 14 18 106 19 stone");
            server.runCommand("setblock -14 107 14 water");
            server.runCommand("setblock 14 107 14 lava");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(100);
            world.getConnection().waitForChunksRender();
            long[] before = new long[1];
            context.runOnClient(_ -> before[0] = ProceduralFluids.ticks());
            context.waitTicks(40);
            context.runOnClient(_ -> {
                if (ProceduralFluids.ticks() <= before[0]) throw new AssertionError("Fluid simulation is not ticking");
            });
            for (boolean oit : new boolean[]{false, true}) {
                context.runOnClient(client -> client.options.improvedTransparency().set(oit));
                server.runCommand("tp @a 0 120 3 0 60");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                context.takeScreenshot("fluids-pools-" + oit);
                server.runCommand("tp @a 0 106 4 0 8");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(10);
                context.takeScreenshot("fluids-falls-" + oit);
            }
            var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
            context.runOnClient(client -> {
                before[0] = ProceduralFluids.ticks();
                reload.set(client.reloadResourcePacks());
            });
            context.waitFor(_ -> reload.get().isDone());
            reload.get().join();
            context.waitFor(client -> client.gui.overlay() == null);
            context.runOnClient(_ -> {
                if (ProceduralFluids.ticks() < before[0]) throw new AssertionError("Reload reset the fluid simulation");
            });
            context.waitTicks(10);
            context.takeScreenshot("fluids-reloaded");
            server.runCommand("tp @a -13 103 7 0 30");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("fluids-water-close");
            server.runCommand("tp @a 13 103 7 0 30");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.takeScreenshot("fluids-lava-close");
            for (int mode = 0; mode < 3; mode++) {
                final int choice = mode;
                context.runOnClient(_ -> {
                    var settings = naturality.config.NaturalityConfig.get().liquids;
                    settings.water = choice != 0;
                    settings.lava = choice != 1;
                    settings.flowingLava = choice != 2;
                });
                server.runCommand("tp @a 0 112 5 0 35");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(3);
                context.takeScreenshot("fluids-disabled-" + mode);
            }
            context.runOnClient(_ -> naturality.config.NaturalityConfig.get().liquids = new naturality.config.NaturalityConfig.Liquids());
            server.runCommand("fill -25 98 33 25 99 47 stone");
            server.runCommand("setblock -14 100 40 water");
            server.runCommand("setblock 14 100 40 lava");
            context.waitTicks(160);
            for (int x : new int[]{-14, 14}) {
                server.runCommand("tp @a " + x + " 103 35 0 60");
                world.getConnection().waitForClientboundPackets();
                world.getConnection().waitForChunksRender();
                context.waitTicks(3);
                context.takeScreenshot("fluids-diagonal-" + x);
                server.runCommand("tp @a " + (x + 0.03) + " 103 35.03 0 60");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(3);
                context.takeScreenshot("fluids-diagonal-moved-" + x);
            }
            context.runOnClient(client -> {
                for (int x : new int[]{-16, -1, 0, 15, 16}) {
                    var pos = new net.minecraft.core.BlockPos(x, 110, 0);
                    int local = x & 15;
                    int localY = pos.getY() & 15;
                    if (naturality.client.fluid.FluidGeometry.connectedHeight(TestFixtures.level(client), pos, localY + 0.001F) != localY
                        || naturality.client.fluid.FluidGeometry.connectedHeight(TestFixtures.level(client), pos, localY + 0.999F) != localY + 1)
                        throw new AssertionError("Exposed vertical liquid edges remain inset");
                    for (boolean axis : new boolean[]{false, true}) {
                        int base = axis ? local : 0;
                        if (naturality.client.fluid.FluidGeometry.connectedCoordinate(TestFixtures.level(client), pos, base + 0.001F, axis) != base
                            || naturality.client.fluid.FluidGeometry.connectedCoordinate(TestFixtures.level(client), pos, base + 0.999F, axis) != base + 1)
                            throw new AssertionError("Exposed liquid faces do not meet at the block boundary");
                    }
                }
                var solid = new net.minecraft.core.BlockPos(0, 99, 0);
                if (naturality.client.fluid.FluidGeometry.connectedHeight(TestFixtures.level(client), solid, 3.001F) != 3.001F)
                    throw new AssertionError("Liquid bottom inset against solid geometry was removed");
                if (naturality.client.fluid.FluidGeometry.connectedCoordinate(TestFixtures.level(client), solid, 0.001F, true) != 0.001F)
                    throw new AssertionError("Liquid inset against solid geometry was removed");
            });
            for (int x : new int[]{-14, 14}) {
                server.runCommand("tp @a " + (x + 1.4) + " 104 12.6 45 5");
                world.getConnection().waitForClientboundPackets();
                world.getConnection().waitForChunksRender();
                context.waitTicks(3);
                context.takeScreenshot("fluids-connected-fall-corner-" + x);
            }
            // Localized light exposes constant face lighting hidden by daylight.
            server.runCommand("time set 18000");
            server.runCommand("setblock -18 101 40 sea_lantern");
            server.runCommand("setblock -18 101 5 sea_lantern");
            for (int z : new int[]{5, 40}) {
                server.runCommand("tp @a -14 104 " + (z - 5) + " 0 40");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(30);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("fluids-pixel-light-" + z);
            }
            server.runCommand("time set 6000");
            // Beyond float's sub-block precision, straddling a negative section boundary.
            int far = -29999008;
            server.runCommand("tp @a " + (far + 1) + " 108 1 0 55");
            server.runOnServer(s -> {
                for (int x = (far - 12) >> 4; x <= (far + 12) >> 4; x++)
                    for (int z = -1; z <= 1; z++) s.overworld().getChunk(x, z);
            });
            server.runCommand("fill " + (far - 12) + " 99 -4 " + (far + 12) + " 100 20 stone");
            server.runCommand("fill " + (far - 11) + " 100 -3 " + (far - 1) + " 100 19 water");
            server.runCommand("fill " + (far + 1) + " 100 -3 " + (far + 11) + " 100 19 lava");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("fluids-far-negative-sections");
        } finally {
            context.runOnClient(client -> {
                naturality.config.NaturalityConfig.get().liquids = previousLiquids;
                naturality.config.NaturalityConfig.get().save();
                client.options.improvedTransparency().set(saved[0]);
                if (client.gui.hud.isHidden() != saved[1]) client.gui.hud.toggle();
            });
        }
    }

    static void checkSimulation() {
        for (boolean lava : new boolean[]{false, true}) for (boolean flow : new boolean[]{false, true}) {
            var simulation = new FluidSimulation(lava, flow, 1234);
            var reference = new FluidSimulation(lava, flow, 1234);
            var seen = new java.util.HashSet<Long>();
            double across = 0, along = 0;
            for (int tick = 0; tick < 512; tick++) {
                simulation.tick();
                reference.tick();
                if (tick < 160) continue;
                long hash = 1;
                float min = 1, max = 0;
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    float h = simulation.heat(x, y);
                    across += Math.abs(h - simulation.heat(x + 1, y));
                    along += Math.abs(h - simulation.heat(x, y + 1));
                    if (!Float.isFinite(h) || h < 0 || h > 1) throw new AssertionError("Unstable fluid");
                    if (h != reference.heat(x, y)) throw new AssertionError("Seeded simulation is not reproducible");
                    if (h != simulation.heat(x - 64, y + 64)) throw new AssertionError("Simulation boundary does not wrap");
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                    hash = hash * 31 + Float.floatToIntBits(h);
                }
                if (max - min < 0.05F) throw new AssertionError("Flat fluid field");
                if (!seen.add(hash)) throw new AssertionError("Fluid frame repeated");
            }
            if (flow && along >= across * 0.8)
                throw new AssertionError("Flowing " + (lava ? "lava" : "water")
                    + " lacks longitudinal streaks: " + along / across);
        }
    }

    static void checkConfig() {
        var gson = new com.google.gson.Gson();
        var defaults = gson.fromJson("{}", naturality.config.NaturalityConfig.class).liquids;
        if (!defaults.water || !defaults.lava || !defaults.flowingLava) throw new AssertionError("Missing liquid defaults");
        for (int bits = 0; bits < 8; bits++) {
            defaults.water = (bits & 1) != 0;
            defaults.lava = (bits & 2) != 0;
            defaults.flowingLava = (bits & 4) != 0;
            var restored = gson.fromJson(gson.toJson(defaults), naturality.config.NaturalityConfig.Liquids.class);
            if (restored.includes(0) != defaults.water || restored.includes(1) != defaults.water
                    || restored.includes(2) != defaults.lava || restored.includes(3) != (defaults.lava && defaults.flowingLava))
                throw new AssertionError("Liquid toggle independence/roundtrip failed");
        }
    }

    private static boolean findAndPress(net.minecraft.client.gui.components.events.GuiEventListener node, String prefix) {
        if (node instanceof net.minecraft.client.gui.components.AbstractButton button
                && button.getMessage().getString().startsWith(prefix)) {
            button.onPress(new net.minecraft.client.input.InputWithModifiers() {
                @Override public int input() { return 0; }
                @Override public int modifiers() { return 0; }
            });
            return true;
        }
        if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler parent)
            for (var child : parent.children()) if (findAndPress(child, prefix)) return true;
        return false;
    }

    private static void press(net.minecraft.client.gui.screens.Screen screen, String prefix) {
        if (!findAndPress(screen, prefix)) throw new AssertionError("Missing control: " + prefix);
    }

    private static void resetControls(net.minecraft.client.gui.components.events.GuiEventListener node) {
        if (node instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().equals("Reset")) button.onPress(TestFixtures.CONFIRM);
        else if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler parent)
            for (var child : parent.children()) resetControls(child);
    }
}
