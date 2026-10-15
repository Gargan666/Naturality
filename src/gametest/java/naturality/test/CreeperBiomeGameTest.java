package naturality.test;

import naturality.client.entity.CreeperTintState;
import naturality.entity.CreeperBiome;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Creeper;

public final class CreeperBiomeGameTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 105 -6 0 15");
            server.runCommand("fill -8 100 -8 24 100 8 stone");
            server.runCommand("fillbiome -8 96 -8 7 112 7 minecraft:swamp");
            server.runCommand("fillbiome 8 96 -8 23 112 7 minecraft:desert");
            server.runCommand("summon creeper 0 101 0 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            context.waitTicks(20);
            check(context);
            server.runCommand("tp @e[type=creeper] 12 101 0");
            context.waitTicks(10);
            check(context);
            // /data merge saves and reloads the complete entity NBT at its new position.
            server.runCommand("data merge entity @e[type=creeper,limit=1] {PersistenceRequired:1b,powered:1b}");
            context.waitTicks(5);
            check(context);
            context.takeScreenshot("creeper-spawn-biome-tint");
            context.runOnClient(client -> client.reloadResourcePacks());
            context.waitTicks(40);
            check(context);
        }
    }

    private static void check(ClientGameTestContext context) {
        context.runOnClient(client -> {
            int count = 0;
            for (var entity : TestFixtures.level(client).entitiesForRendering()) if (entity instanceof Creeper creeper) {
                count++;
                if (!"minecraft:swamp".equals(((CreeperBiome)creeper).naturality$spawnBiome()))
                    throw new AssertionError("Spawn biome must survive movement and sync to client");
                var renderer = (net.minecraft.client.renderer.entity.CreeperRenderer)client.getEntityRenderDispatcher().getRenderer(creeper);
                var state = (CreeperRenderState)renderer.createRenderState(creeper, 1);
                int expected = 0xFF000000 | TestFixtures.require(TestFixtures.level(client).registryAccess().lookupOrThrow(Registries.BIOME)
                    .getValue(Identifier.withDefaultNamespace("swamp")), "swamp biome").getFoliageColor();
                if (((CreeperTintState)state).naturality$foliageTint() != expected)
                    throw new AssertionError("Creeper must retain swamp foliage tint in desert");
                if (!renderer.getTextureLocation(state).equals(Identifier.fromNamespaceAndPath("naturality", "textures/entity/creeper.png")))
                    throw new AssertionError("Custom creeper texture");
            }
            if (count != 1) throw new AssertionError("Missing creeper fixture: " + count);
        });
    }
}
