package naturality.test;

import naturality.client.horror.AmbientHorror;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.phys.Vec3;

public final class AmbientHorrorGameTest implements FabricClientGameTest {
    private static void set(String name, Object value) {
        try { var field = AmbientHorror.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object get(String name) {
        try { var field = AmbientHorror.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public void runTest(ClientGameTestContext context) {
        if (AmbientHorror.opacity(30, -1, 0) != 1 || AmbientHorror.opacity(30, 30, 0) != 0
                || AmbientHorror.opacity(30, 15, 0) != .5F) throw new AssertionError("Smooth eye envelope");
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -12 100 -12 12 106 32 stone");
            server.runCommand("fill -11 101 -11 11 105 31 air");
            server.runCommand("fill -40 100 -40 40 100 -16 stone");
            server.runCommand("tp @a 0 101 -8 0 0");
            context.waitTicks(30);
            context.runOnClient(client -> {
                var dispatcher = net.fabricmc.fabric.api.client.command.v2.ClientCommands.getActiveDispatcher();
                if (dispatcher == null || dispatcher.getRoot().getChild("naturalityhorror") == null)
                    throw new AssertionError("Horror command registered");
                try {
                    var source = (net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource)
                        client.getConnection().getSuggestionsProvider();
                    if (dispatcher.execute("naturalityhorror eyes", source) != 1 || get("eyes") == null)
                        throw new AssertionError("Eyes command creates effect");
                    if (!AmbientHorror.usesNaturalEyeRules() || ((Vec3)get("eyes")).distanceTo(client.gameRenderer.mainCamera().position()) < 18)
                        throw new AssertionError("Dark room command must prefer natural distant placement");
                    if (dispatcher.execute("naturalityhorror clear", source) != 1 || get("eyes") != null)
                        throw new AssertionError("Clear command removes effect");
                    if (dispatcher.execute("naturalityhorror walker", source) != 1 || get("walker") == null)
                        throw new AssertionError("Walker command creates effect");
                    assertDistantSolidSpawn(client);
                    dispatcher.execute("naturalityhorror clear", source);
                    if (dispatcher.execute("naturalityhorror sprinter", source) != 1 || !(boolean)get("sprinter"))
                        throw new AssertionError("Sprinter command creates the fast variant");
                    assertDistantSolidSpawn(client);
                    dispatcher.execute("naturalityhorror clear", source);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
            });
            Vec3 feet = new Vec3(0, 101, 0);
            context.runOnClient(client -> {
                set("walker", feet); set("walkerLife", 500); set("heading", 0D);
                set("retreating", false);
            });
            context.waitTicks(12);
            context.runOnClient(client -> {
                if (!feet.equals(get("walker"))) throw new AssertionError("Visible hidden bounds must freeze walker");
                if ((int)get("walkerLife") != 500) throw new AssertionError("Watching pauses walker lifetime");
            });
            server.runCommand("tp @a 0 101 -8 180 0");
            context.waitTicks(22);
            context.runOnClient(client -> {
                if (feet.equals(get("walker"))) throw new AssertionError("Walker must resume outside camera frustum");
                if (((Vec3)get("walker")).distanceTo(client.player.position()) >= feet.distanceTo(client.player.position()))
                    throw new AssertionError("Walker approaches player");
            });
            context.waitTicks(85);
            context.runOnClient(client -> {
                if (!(boolean)get("retreating")) throw new AssertionError("Walker turns away after approaching");
            });
            server.runCommand("tp @a 0 101 -8 0 0");
            context.waitTicks(3);
            final Vec3[] stopped = new Vec3[1];
            final int[] remaining = new int[1];
            context.runOnClient(client -> { stopped[0] = (Vec3)get("walker"); remaining[0] = (int)get("walkerLife"); });
            context.waitTicks(12);
            context.runOnClient(client -> {
                if (!stopped[0].equals(get("walker")) || remaining[0] != (int)get("walkerLife"))
                    throw new AssertionError("Watching pauses retreat and its timeout");
            });
            server.runCommand("tp @a 0 101 -8 180 0");
            context.waitTicks(220);
            context.runOnClient(client -> {
                if (get("walker") != null) throw new AssertionError("Walker disappears after retreating");
                set("walker", feet); set("walkerLife", 500); set("retreating", false); set("sprinter", true);
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                Vec3 position = (Vec3)get("walker");
                if (position == null || feet.distanceTo(position) < 2.5 || (boolean)get("retreating"))
                    throw new AssertionError("Sprinter moves much faster without retreating");
            });
            server.runCommand("tp @a 0 101 -8 0 0");
            context.waitTicks(2);
            context.runOnClient(client -> { stopped[0] = (Vec3)get("walker"); remaining[0] = (int)get("walkerLife"); });
            context.waitTicks(12);
            context.runOnClient(client -> {
                if (!stopped[0].equals(get("walker")) || remaining[0] != (int)get("walkerLife"))
                    throw new AssertionError("Sprinter pauses while in view");
            });
            server.runCommand("tp @a 0 101 -8 180 0");
            context.waitTicks(25);
            context.runOnClient(client -> {
                if (get("walker") != null || (boolean)get("retreating"))
                    throw new AssertionError("Sprinter disappears on reaching player without retreat phase");
                set("walker", null);
                set("eyes", new Vec3(3, 102.62, 16)); set("eyeLife", 30); set("fade", -1); set("stare", 0);
            });
            server.runCommand("tp @a 0 101 -8 0 0");
            context.waitTicks(8);
            context.takeScreenshot("ambient-horror-white-eyes");
            for (var variant : naturality.client.horror.EyeVariant.values()) {
                context.runOnClient(client -> set("eyeVariant", variant));
                context.waitTicks(2);
                context.takeScreenshot("ambient-horror-variant-" + variant.name().toLowerCase(java.util.Locale.ROOT));
            }
            server.runCommand("tp @a 0 101 -8 30 0");
            context.waitTicks(2);
            context.takeScreenshot("ambient-horror-eyes-facing-position");
            server.runCommand("tp @a 3 101 3 0 0");
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (get("eyes") == null || (int)get("fade") <= 0) throw new AssertionError("Approach starts gradual fade");
            });
            context.waitTicks(30);
            context.runOnClient(client -> {
                if (get("eyes") != null) throw new AssertionError("Eyes finish fading");
                set("eyes", new Vec3(3, 102.62, 30)); set("eyeLife", 30); set("fade", -1); set("stare", 0);
            });
            context.waitTicks(43);
            context.runOnClient(client -> {
                if ((int)get("fade") < 0) throw new AssertionError("Sustained direct gaze starts fade");
            });
            context.waitTicks(30);
            context.runOnClient(client -> {
                if (get("eyes") != null) throw new AssertionError("Direct gaze fade completes");
                set("eyes", new Vec3(3, 102.62, 30)); set("eyeLife", 30); set("fade", -1); set("stare", 0);
            });
            server.runCommand("setblock 3 102 29 glowstone");
            context.waitTicks(12);
            context.runOnClient(client -> {
                if (get("eyes") == null || (int)get("fade") <= 0) throw new AssertionError("Illumination fades eyes away");
            });
            server.runCommand("setblock 3 102 29 air");
            server.runCommand("fill -12 100 32 12 106 62 stone");
            server.runCommand("fill -11 101 32 11 105 61 air");
            server.runCommand("tp @a 3 101 3 90 0");
            context.waitTicks(12);
            context.runOnClient(client -> {
                set("eyes", new Vec3(3, 102.62, 30)); set("eyeLife", 30); set("fade", -1); set("stare", 0);
                set("testEyes", false);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                if ((int)get("fade") >= 0) throw new AssertionError("Dark background retains eyes");
            });
            server.runCommand("setblock 3 102 61 glowstone");
            context.waitTicks(12);
            context.runOnClient(client -> {
                if (client.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, net.minecraft.core.BlockPos.containing((Vec3)get("eyes"))) != 0)
                    throw new AssertionError("Backdrop fixture must leave the eye position dark");
                if ((int)get("fade") <= 0) throw new AssertionError("Lit backdrop fades eyes even when their own position is dark");
            });
            server.runCommand("tp @a 3 101 3 0 0");
            server.runCommand("fill -11 105 -11 11 105 61 glowstone");
            context.waitTicks(15);
            context.runOnClient(client -> {
                if (!AmbientHorror.test(true) || AmbientHorror.usesNaturalEyeRules())
                    throw new AssertionError("Illuminated room retains explicit test fallback when natural placement is impossible");
                AmbientHorror.clearEffects();
            });
        }
    }
    private static void assertDistantSolidSpawn(net.minecraft.client.Minecraft client) {
        Vec3 feet = (Vec3)get("walker");
        double distance = feet.subtract(client.player.position()).horizontalDistance();
        if (distance < 24 || distance > 36.01) throw new AssertionError("Walker must start 24-36 horizontal blocks away: " + distance);
        var support = net.minecraft.core.BlockPos.containing(feet.add(0, -.05, 0));
        if (!client.level.getBlockState(support).isFaceSturdy(client.level, support, net.minecraft.core.Direction.UP))
            throw new AssertionError("Walker must start on solid terrain");
    }
}
