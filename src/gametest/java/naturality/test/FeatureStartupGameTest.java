package naturality.test;

import naturality.Naturality;
import naturality.config.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;

/** Run in a fresh JVM with each fixture emitted by ConfigPresetGameTest. */
public final class FeatureStartupGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        boolean content = Feature.CONTENT.enabled();
        boolean visuals = Feature.VISUAL_OVERHAUL.enabled();
        context.runOnClient(client -> {
            check(BuiltInRegistries.BLOCK.containsKey(Naturality.id("lyren_heath")) == content, "Content block registration");
            check(BuiltInRegistries.ITEM.containsKey(Naturality.id("lyren_sprout")) == content, "Content item registration");
            check(BuiltInRegistries.MOB_EFFECT.containsKey(Naturality.id("distortion")) == content, "Content effect registration");
            check(BuiltInRegistries.ENTITY_TYPE.containsKey(Naturality.id("falling_star")) == content, "Content entity registration");
            check(Blocks.CHORUS_FLOWER.getStateDefinition().getPossibleStates().size()
                == (Feature.CHORUS_FLOWERS.enabled() ? 72 : 6), "Vanilla chorus state table must be restored");
            if (!content) {
                try {
                    var type = new com.google.gson.reflect.TypeToken<java.util.Map<String, String>>() {}.getType();
                    java.util.Map<String, String> vanilla = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(
                        java.nio.file.Path.of(System.getProperty("naturality.test.vanillaSnapshot"))), type);
                    var actual = VanillaRegistrySnapshot.capture();
                    for (var entry : vanilla.entrySet()) check(entry.getValue().equals(actual.get(entry.getKey())),
                        "Vanilla network ID changed: " + entry + " versus " + actual.get(entry.getKey()));
                    check(vanilla.equals(actual), "Clientside Only introduced extra vanilla states or tracked entity data");
                    System.out.println("Matched " + vanilla.size() + " vanilla wire-table entries.");
                } catch (java.io.IOException error) { throw new AssertionError(error); }
            }
            try (var reader = client.getResourceManager().getResource(Identifier.withDefaultNamespace("shaders/core/terrain.fsh"))
                    .orElseThrow().openAsReader()) {
                String shader = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                check(shader.contains("naturality:") == visuals, "Overhaul shader override must follow visual settings");
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        });
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -8 100 -8 8 100 8 stone");
            server.runCommand("tp @a 0 102 -5 0 0");
            server.runCommand("setblock 2 100 0 end_stone");
            server.runCommand("setblock 2 101 0 chorus_flower");
            server.runCommand("setblock -2 101 0 water");
            server.runCommand("summon villager 0 101 0 {NoAI:1b}");
            server.runCommand("summon fireball 2 103 0 {Motion:[0.0d,0.0d,0.0d]}");
            server.runCommand("summon ghast -3 105 3 {NoAI:1b}");
            if (content) {
                server.runCommand("setblock 3 101 0 naturality:lyren_heath");
                server.runCommand("setblock 3 102 0 naturality:lyren_sprout");
                server.runCommand("give @a naturality:echo_coral");
                if (Feature.CONFUSION.enabled()) server.runCommand("effect give @a naturality:confusion 5");
            }
            context.waitTicks(30);
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                check((client.player instanceof naturality.weather.DistortionState) == Feature.DISTORTION.enabled(),
                    "Tracked entity data must follow Distortion setting");
                check((client.player instanceof naturality.ghast.GhastCrashBody) == Feature.GHAST_PHYSICS.enabled(),
                    "Ghast physics must not add tracked data with Clientside Only or No Visual Overhaul");
                var ball = TestFixtures.level(client).entitiesForRendering().iterator();
                boolean found = false;
                while (ball.hasNext()) {
                    var entity = ball.next();
                    if (entity.getType() != EntityTypes.FIREBALL) continue;
                    check((client.getEntityRenderDispatcher().getRenderer(entity) instanceof naturality.client.entity.FireballModelRenderer<?>)
                        == Feature.FIREBALL_MODELS.enabled(), "Fireball renderer must return to vanilla");
                    found = true;
                }
                check(found, "Fireball fixture must reach the client");
                var ghast = TestFixtures.level(client).entitiesForRendering().iterator();
                while (ghast.hasNext()) {
                    var entity = ghast.next();
                    if (!(entity instanceof net.minecraft.world.entity.monster.Ghast mob)) continue;
                    check((client.getEntityRenderDispatcher().getRenderer(mob) instanceof naturality.client.entity.SquashingGhastRenderer)
                        == (Feature.GHAST_SQUASH.enabled() || Feature.GHAST_PHYSICS.enabled()), "Ghast renderer must follow optional visuals and required death physics");
                }
                for (var type : new net.minecraft.core.particles.SimpleParticleType[] {
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER}) {
                    var particle = client.particleEngine.createParticle(type, 0, 105, 4, 0, 0, 0);
                    check((particle instanceof naturality.client.particle.FlatExplosionParticle) == Feature.EXPLOSION_PARTICLES.enabled(),
                        "Explosion provider and sprite definitions must follow the standalone visual option");
                }
                if (!content) {
                    check(!GameplaySettings.clientPhysicalPortalEntry() && !GameplaySettings.clientFireWrapping()
                        && !GameplaySettings.clientSnowWrapping(), "Clientside preset must use vanilla gameplay");
                    check(TestFixtures.level(client).getBlockState(new net.minecraft.core.BlockPos(2,101,0)).is(Blocks.CHORUS_FLOWER),
                        "Vanilla state IDs must survive chunk/packet decoding");
                }
            });
            server.runOnServer(s -> {
                if (!content) {
                    check(s.getResourceManager().getResource(Naturality.id("recipe/smooth_endstone.json")).isEmpty(),
                        "Disabled content recipe leaked from the mod pack");
                    check(s.getCommands().getDispatcher().getRoot().getChild("reputation") == null,
                        "Disabled reputation command must be absent");
                }
            });
            context.takeScreenshot("preset-startup-" + (content ? visuals ? "default" : "content-only" : "clientside"));
            if (!Feature.GHAST_PHYSICS.enabled()) {
                server.runCommand("kill @e[type=minecraft:ghast]");
                context.waitTicks(5);
                // Render the ordinary death with custom squash still on: no GhastCrashBody cast is allowed.
                context.takeScreenshot("preset-vanilla-ghast-death");
                context.waitTicks(25);
                server.runOnServer(s -> {
                    for (var entity : s.overworld().getAllEntities())
                        check(entity.getType() != EntityTypes.GHAST, "Disabled ghast physics must retain vanilla death lifetime");
                });
            }
        }
    }
}
