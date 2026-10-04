package naturality.test;

import naturality.client.lighting.DynamicLighting;
import naturality.client.lighting.FractionalLightField;
import naturality.client.lighting.LightSourcePlacement;
import naturality.client.config.NaturalityCategoryScreen;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;

public final class DynamicLightingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = NaturalityConfig.get().dynamicLighting;
        var config = new NaturalityConfig.DynamicLighting();
        NaturalityConfig.get().dynamicLighting = config;
        try {
            config.updateTicks = 20;
            context.runOnClient(client -> client.gui.setScreen(new NaturalityCategoryScreen(client.gui.screen(),
                NaturalityCategoryScreen.Category.DYNAMIC_LIGHTING)));
            context.waitTicks(2);
            context.runOnClient(client -> {
                if (resetAll(client.gui.screen()) != 10 + NaturalityConfig.DynamicLighting.defaultEntityLights().size()) throw new AssertionError("Dynamic lighting must expose every resettable control");
                if (config.updateTicks != 2) throw new AssertionError("Update interval reset failed");
            });
            context.takeScreenshot("dynamic-lighting-settings");
            context.runOnClient(client -> client.gui.screen().onClose());
            config.droppedItems = false;
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer();
                server.runCommand("gamemode creative @a");
                server.runCommand("time set midnight");
                server.runCommand("fill -8 100 -8 40 100 12 smooth_quartz");
                server.runCommand("tp @a 0.5 101 0.5 0 25");
                world.getConnection().waitForClientboundPackets();
                world.getConnection().waitForChunksRender();
                BlockPos near = new BlockPos(0, 102, 3);
                waitLight(context, near, false);
                server.runCommand("item replace entity @a weapon.mainhand with minecraft:torch");
                waitLight(context, near, true);
                server.runOnServer(s -> {
                    if (s.overworld().getBrightness(LightLayer.BLOCK, near) != 0)
                        throw new AssertionError("Dynamic lighting must never enter the server light engine");
                });
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("dynamic-lighting-held-torch");
                BlockPos side = new BlockPos(3, 102, 0);
                int before = smooth(context, side);
                server.runCommand("tp @a 0.75 101 0.5 0 25");
                context.waitFor(client -> DynamicLighting.smoothLight(client.level.getLightEngine()
                    .getLayerListener(LightLayer.BLOCK), side.asLong()) > before);
                context.runOnClient(client -> {
                    int precise = DynamicLighting.smoothLight(client.level.getLightEngine().getLayerListener(LightLayer.BLOCK), side.asLong());
                    int rendered = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level, side);
                    if (net.minecraft.util.LightCoordsUtil.smoothBlock(rendered) < precise)
                        throw new AssertionError("Fractional light must reach rendered lightmap coordinates");
                });
                server.runCommand("tp @a 1.33 101 0.5 0 25");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(5);
                int boundaryBefore = smooth(context, side);
                server.runCommand("tp @a 1.37 101 0.5 0 25");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(5);
                int boundaryAfter = smooth(context, side);
                if (Math.abs(boundaryAfter - boundaryBefore) > 2)
                    throw new AssertionError("Crossing a block boundary snapped the fractional light field");
                config.subBlockPrecision = false;
                context.waitTicks(5);
                if (smooth(context, side) != 0) throw new AssertionError("Block-snapped mode retained fractional lighting");
                config.subBlockPrecision = true;
                server.runCommand("tp @a 0.5 101 0.5 0 25");
                context.waitFor(client -> DynamicLighting.smoothLight(client.level.getLightEngine()
                    .getLayerListener(LightLayer.BLOCK), side.asLong()) > 0);
                // A solid wall must stop the propagated light, not just dim it.
                server.runCommand("fill 2 90 -20 2 120 20 stone");
                world.getConnection().waitForClientboundPackets();
                waitLight(context, new BlockPos(3, 102, 0), false);
                context.waitTicks(3);
                if (smooth(context, side) != 0) throw new AssertionError("Fractional light leaked through a solid wall");
                server.runCommand("fill 2 90 -20 2 120 20 air");
                waitLight(context, new BlockPos(3, 102, 0), true);
                server.runCommand("item replace entity @a weapon.offhand with minecraft:torch");
                server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
                context.waitTicks(6);
                waitLight(context, near, true);
                server.runCommand("tp @a 32.5 101 0.5 0 25");
                waitLight(context, near, false);
                BlockPos moved = new BlockPos(32, 102, 3);
                waitLight(context, moved, true);
                config.enabled = false;
                waitLight(context, moved, false);
                config.enabled = true;
                waitLight(context, moved, true);
                server.runCommand("item replace entity @a weapon.offhand with minecraft:air");
                waitLight(context, moved, false);
                server.runCommand("tp @a 0.5 101 -5.5 0 15");
                server.runCommand("summon minecraft:blaze 0.5 101 0.5 {NoAI:1b,Silent:1b}");
                waitLight(context, near, true);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("dynamic-lighting-blaze");
                server.runCommand("kill @e[type=minecraft:blaze]");
                waitLight(context, near, false);
                server.runCommand("summon minecraft:glow_squid 0.5 101 0.5 {NoAI:1b,Invulnerable:1b}");
                waitLight(context, near, true);
                config.emissiveMobs = false;
                waitLight(context, near, false);
                config.emissiveMobs = true;
                waitLight(context, near, true);
                server.runCommand("kill @e[type=minecraft:glow_squid]");
                waitLight(context, near, false);
                config.droppedItems = true;
                server.runCommand("summon minecraft:item 0.5 101 0.5 {Item:{id:\"minecraft:torch\",count:1}}");
                waitLight(context, near, true);
                server.runCommand("kill @e[type=minecraft:item]");
                waitLight(context, near, false);
                context.runOnClient(client -> checkPlacement(client.level));

                server.runCommand("fill 4 101 -2 4 105 2 oak_leaves[persistent=true]");
                for (boolean mainHand : new boolean[] {true, false}) {
                    server.runCommand("tp @a 3.7 101 0.5 " + (mainHand ? 180 : 0) + " 25");
                    server.runCommand("item replace entity @a weapon." + (mainHand ? "mainhand" : "offhand") + " with minecraft:torch");
                    world.getConnection().waitForClientboundPackets();
                    context.runOnClient(client -> {
                        Vec3 raw = DynamicLighting.handPosition(client.player, mainHand);
                        if (!client.level.getBlockState(BlockPos.containing(raw)).is(Blocks.OAK_LEAVES))
                            throw new AssertionError("Wall fixture must put the raw hand emitter inside leaves");
                        var blocked = new FractionalLightField(client.level);
                        blocked.add(raw, 14);
                        if (!blocked.seeds().isEmpty()) throw new AssertionError("Leaf fixture no longer reproduces the original loss of all seeds");
                        DynamicLighting.invalidate();
                        DynamicLighting.tick(client);
                        if (DynamicLighting.smoothLight(client.level.getLightEngine().getLayerListener(LightLayer.BLOCK),
                                new BlockPos(3, 102, 0).asLong()) < 160)
                            throw new AssertionError("Held torch vanished against leaves, mainHand=" + mainHand);
                    });
                    context.waitTicks(6);
                    world.getConnection().waitForChunksRender();
                    context.takeScreenshot("dynamic-lighting-leaf-wall-" + (mainHand ? "main" : "off"));
                    server.runCommand("item replace entity @a weapon." + (mainHand ? "mainhand" : "offhand") + " with minecraft:air");
                }
                server.runCommand("fill 4 101 -2 4 105 2 air");

                // Air-only sections and space above the build ceiling still
                // contain entities. Check both lighting modes at each height.
                server.runCommand("item replace entity @a weapon.mainhand with minecraft:torch");
                for (int height : new int[] {240, 319, 340, 512}) {
                    server.runCommand("tp @a 0.5 " + height + " 0.5 0 25");
                    world.getConnection().waitForClientboundPackets();
                    context.runOnClient(client -> {
                        client.player.getAbilities().flying = true;
                        for (boolean precise : new boolean[] {true, false}) {
                            config.subBlockPrecision = precise;
                            DynamicLighting.invalidate();
                            DynamicLighting.tick(client);
                            BlockPos sample = client.player.blockPosition().above();
                            if (client.level.getBrightness(LightLayer.BLOCK, sample) < 10)
                                throw new AssertionError("Dynamic light vanished at Y=" + height + ", precise=" + precise);
                            int packed = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level, sample);
                            if (net.minecraft.util.LightCoordsUtil.smoothBlock(packed) < 160)
                                throw new AssertionError("High-altitude light did not reach rendered coordinates");
                        }
                    });
                }
                config.subBlockPrecision = true;
                server.runCommand("tp @a 0.5 101 0.5 0 25");
                world.getConnection().waitForClientboundPackets();
                waitLight(context, near, true);
                config.updateTicks = 20;
                context.runOnClient(client -> {
                    client.gameMode.dropItem(client.player, true);
                    if (!client.player.getMainHandItem().isEmpty()) throw new AssertionError("Drop did not remove held torch");
                    // No server spawn can be handled during this client callback.
                    for (int tick = 0; tick < 3; tick++) {
                        DynamicLighting.tick(client);
                        if (DynamicLighting.blockLight(client.level.getLightEngine().getLayerListener(LightLayer.BLOCK), near.asLong()) == 0)
                            throw new AssertionError("Held light went dark before the dropped entity arrived");
                    }
                });
                context.waitFor(client -> {
                    for (var entity : client.level.entitiesForRendering())
                        if (entity instanceof ItemEntity item && item.getItem().is(Items.TORCH)) return true;
                    return false;
                });
                context.runOnClient(client -> {
                    DynamicLighting.tick(client);
                    if (DynamicLighting.sourceCount() != 1)
                        throw new AssertionError("Dropped item did not replace the temporary handoff light immediately");
                    if (DynamicLighting.blockLight(client.level.getLightEngine().getLayerListener(LightLayer.BLOCK), near.asLong()) == 0)
                        throw new AssertionError("Handoff waited for the configured update interval");
                });
                context.waitTicks(22);
                waitLight(context, near, true);
                server.runCommand("kill @e[type=minecraft:item]");
                waitLight(context, near, false);
                context.runOnClient(client -> {
                    DynamicLighting.beginDrop(client.player, new ItemStack(Items.TORCH));
                    DynamicLighting.tick(client);
                    if (DynamicLighting.sourceCount() != 1) throw new AssertionError("Missing handoff bridge");
                    for (int tick = 0; tick < 22; tick++) DynamicLighting.tick(client);
                    if (DynamicLighting.sourceCount() != 0) throw new AssertionError("Rejected drop left a permanent ghost light");
                });
                config.enabled = false;
                context.waitTicks(4);
                context.runOnClient(client -> {
                    if (DynamicLighting.sourceCount() != 0) throw new AssertionError("Disabled lighting retained emitters");
                });
            }
        } finally {
            NaturalityConfig.get().dynamicLighting = original;
            original.sanitize();
            NaturalityConfig.get().save();
        }
    }

    private static void checkPlacement(net.minecraft.client.multiplayer.ClientLevel level) {
        BlockPos pos = new BlockPos(4, 102, 4);
        Vec3 owner = new Vec3(3.7, 102.7, 4.5);
        Vec3 desired = new Vec3(4.1, 102.7, 4.5);
        for (var block : new net.minecraft.world.level.block.Block[] {Blocks.STONE, Blocks.OAK_LEAVES, Blocks.GLASS}) {
            level.setBlock(pos, block.defaultBlockState(), 3);
            Vec3 placed = LightSourcePlacement.resolve(level, desired, owner);
            if (placed == null || placed.x >= 4 || placed.x < 3.9)
                throw new AssertionError("Emitter must stay just outside the owner's side of " + block);
            var field = new FractionalLightField(level);
            field.add(placed, 14);
            if (field.seeds().isEmpty()) throw new AssertionError("Wall-adjacent emitter lost all seeds for " + block);
            Vec3 body = new Vec3(4.1, 102.7, 4.5);
            Vec3 escaped = LightSourcePlacement.resolve(level, body, body);
            if (escaped == null || escaped.x >= 4) throw new AssertionError("Embedded entity light was not pushed out");
        }
        level.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState(), 3);
        Vec3 aboveSlab = new Vec3(4.5, 102.7, 4.5);
        if (!aboveSlab.equals(LightSourcePlacement.resolve(level, aboveSlab, aboveSlab)))
            throw new AssertionError("Empty part of a slab cell must remain usable");
        Vec3 insideSlab = new Vec3(4.5, 102.45, 4.5);
        Vec3 escaped = LightSourcePlacement.resolve(level, insideSlab, insideSlab);
        if (escaped == null || escaped.y <= 102.5 || escaped.y >= 102.6)
            throw new AssertionError("Embedded light must move just above the slab surface");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void waitLight(ClientGameTestContext context, BlockPos position, boolean lit) {
        context.waitFor(client -> client.level != null && (client.level.getBrightness(LightLayer.BLOCK, position) > 0) == lit);
    }

    private static int smooth(ClientGameTestContext context, BlockPos position) {
        var result = new java.util.concurrent.atomic.AtomicInteger();
        context.runOnClient(client -> result.set(DynamicLighting.smoothLight(
            client.level.getLightEngine().getLayerListener(LightLayer.BLOCK), position.asLong())));
        return result.get();
    }

    private static int resetAll(net.minecraft.client.gui.components.events.GuiEventListener element) {
        if (element instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals("Reset")) {
            button.onPress(null);
            return 1;
        }
        int count = 0;
        if (element instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
            for (var child : container.children()) count += resetAll(child);
        return count;
    }
}
