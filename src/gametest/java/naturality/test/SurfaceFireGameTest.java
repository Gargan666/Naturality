package naturality.test;

import java.util.List;
import naturality.fire.FireSurface;
import naturality.client.fire.SurfaceFireModel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;

public final class SurfaceFireGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        checkSimulation();
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runCommand("fill -10 99 -4 12 99 8 stone");
            server.runCommand("tp @a 1 103 -8 0 18");
            String[] supports = {"oak_slab[type=bottom]", "oak_slab[type=top]", "oak_fence",
                "oak_fence_gate[facing=north]", "oak_fence_gate[facing=north,open=true]",
                "oak_sign[rotation=2]", "oak_stairs[facing=east]", "oak_trapdoor[half=bottom]"};
            for (int i = 0; i < supports.length; i++) {
                int x = -6 + (i % 4) * 4, z = (i / 4) * 4;
                server.runCommand("setblock " + x + " 100 " + z + " " + supports[i]);
            }
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                var level = client.level;
                var fire = Blocks.FIRE.defaultBlockState();
                var model = client.getModelManager().getBlockStateModelSet().get(fire);
                check(model instanceof SurfaceFireModel, "Fire model wrapper registered");
                check(client.getModelManager().getBlockStateModelSet().get(Blocks.SOUL_FIRE.defaultBlockState())
                    instanceof SurfaceFireModel, "Soul fire uses the same surface fitting");
                check(!FireSurface.isFloorFire(fire.setValue(FireBlock.NORTH, true)), "Wall fire stays attached");
                var slab = FireSurface.below(level, new BlockPos(-6, 101, 0));
                check(slab.size() == 1, "Bottom slab has one surface");
                near(slab.getFirst().y(), -0.5F, "Bottom slab height");
                near(area(slab), 1, "Bottom slab footprint");
                var top = FireSurface.below(level, new BlockPos(-2, 101, 0));
                near(top.getFirst().y(), 0, "Top slab height");
                var fence = FireSurface.below(level, new BlockPos(2, 101, 0));
                near(area(fence), 1F / 16, "Fence post footprint uses visible 4x4 top");
                near(fence.getFirst().y(), 0, "Fence avoids 1.5-block collision height");
                near((float) naturality.fire.FireGeometry.floorHeight(slab.getFirst()),
                    1.4F,"Full-width slab fire keeps vanilla model height");
                near((float) naturality.fire.FireGeometry.floorHeight(top.getFirst()),
                    1.4F,"Top slab fire keeps vanilla model height");
                near((float) naturality.fire.FireGeometry.floorHeight(
                    new FireSurface.Patch(0,0,0,1,0,0,1)),1.4F,
                    "Full-block fire keeps vanilla model height");
                near((float) naturality.fire.FireGeometry.floorHeight(fence.getFirst()),
                    naturality.fire.FireGeometry.HEIGHT / 4,"Fence fire is scaled to its 4-pixel top");
                for(var flame : List.of(fire, Blocks.SOUL_FIRE.defaultBlockState())) {
                    float[] heightRange={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
                    client.getModelManager().getBlockStateModelSet().get(flame).emitQuads(
                        Renderer.get().quadEmitter(q -> {
                            for(int v=0;v<4;v++) {
                                heightRange[0]=Math.min(heightRange[0],q.y(v));
                                heightRange[1]=Math.max(heightRange[1],q.y(v));
                            }
                        }),level,new BlockPos(2,101,0),flame,RandomSource.create(0),f->false);
                    near(heightRange[1]-heightRange[0],naturality.fire.FireGeometry.HEIGHT / 4,
                        "Normal and soul fence flames render at the fitted height");
                }
                var gate = FireSurface.below(level, new BlockPos(6, 101, 0));
                near(area(gate), 1F / 8, "Closed gate covers thin full-width top");
                var open = FireSurface.below(level, new BlockPos(-6, 101, 4));
                check(open.stream().noneMatch(p -> p.contains(0.5F, 0.5F)), "Open gate keeps middle gap");
                near(open.stream().map(p -> p.z()).min(Float::compare).orElseThrow(), 1F / 16,
                    "North-facing open gate follows blades toward north");
                var sign = FireSurface.below(level, new BlockPos(-2, 101, 4));
                check(sign.size() == 1, "Sign board has one top");
                near(area(sign), 1F / 12, "Sign uses actual board thickness");
                near(sign.getFirst().y(), 1F / 12, "Sign reaches above voxel boundary");
                check(Math.abs(sign.getFirst().ux()) > 0.01 && Math.abs(sign.getFirst().uz()) > 0.01,
                    "Sign follows diagonal rotation");
                var stairs = FireSurface.below(level, new BlockPos(2, 101, 4));
                near(area(stairs), 1, "Stair surfaces do not overlap");
                check(stairs.stream().anyMatch(p -> p.y() == -0.5F) && stairs.stream().anyMatch(p -> p.y() == 0),
                    "Stair treads retain separate heights");
                float[] minY = {Float.POSITIVE_INFINITY}, maxY = {Float.NEGATIVE_INFINITY};
                var emitter = Renderer.get().quadEmitter(q -> {
                    for (int v = 0; v < 4; v++) {
                        minY[0] = Math.min(minY[0], q.y(v));
                        maxY[0] = Math.max(maxY[0], q.y(v));
                    }
                });
                model.emitQuads(emitter, level, new BlockPos(-6, 101, 0), fire, RandomSource.create(0), f -> false);
                check(minY[0] >= -0.55F && minY[0] <= -0.42F, "Vanilla fire planes move down to bottom slab");
                check(maxY[0] > .7F && maxY[0] < 1.0F,"Full-width fire uses the tilted vanilla height");
                var changing = new BlockPos(10, 100, 0);
                level.setBlock(changing, Blocks.OAK_WALL_SIGN.defaultBlockState(), 2);
                var wallSign = FireSurface.below(level, changing.above());
                near(area(wallSign), 1F / 12, "Wall sign uses board thickness rather than selection box");
                near(wallSign.getFirst().y(), 37F / 48 - 1, "Wall sign uses board height");
                level.setBlock(changing, Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceBlock.EAST, true), 2);
                var connected = FireSurface.below(level, changing.above());
                near(area(connected), 7F / 64, "Connected fence follows thin rail and post");
                check(connected.stream().anyMatch(p -> p.y() == -1F / 16), "Rail top is below post top");
                level.setBlock(changing, Blocks.OAK_FENCE_GATE.defaultBlockState(), 2);
                near(area(FireSurface.below(level, changing.above())), 1F / 8, "Replacing support refreshes footprint");
                level.setBlock(changing, Blocks.AIR.defaultBlockState(), 2);
                check(FireSurface.below(level, changing.above()).isEmpty(), "Missing support falls back to vanilla");
                level.setBlock(changing, Blocks.FERN.defaultBlockState(), 2);
                check(FireSurface.foliageDepth(level, changing.above())==1,
                    "Walk-through foliage is skipped as a fire support");
                var merged=FireSurface.below(level, changing.above());
                near(merged.getFirst().y(),-1,"Fire begins on ground beneath fern");
                var fernTop=naturality.fire.FireSurface.exposed(naturality.fire.FireGeometry.supportBoxes(level,changing)
                    .stream().map(b->new FireSurface.Patch((float)b.minX,(float)b.maxY-1,(float)b.minZ,
                        (float)(b.maxX-b.minX),0,0,(float)(b.maxZ-b.minZ))).toList());
                near(area(merged),area(fernTop),"Merged fern fire keeps its original footprint");
                for(var flame:List.of(fire,Blocks.SOUL_FIRE.defaultBlockState())) {
                    float[] range={Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
                    client.getModelManager().getBlockStateModelSet().get(flame).emitQuads(
                        Renderer.get().quadEmitter(q->{for(int v=0;v<4;v++) {
                            range[0]=Math.min(range[0],q.y(v));range[1]=Math.max(range[1],q.y(v));
                        }}),level,changing.above(),flame,RandomSource.create(0),d->false);
                    near(range[0],-1,"Normal and soul fire merge into fern at ground level");
                    near(range[1]-range[0],(float)naturality.fire.FireGeometry.floorHeight(merged.getFirst()),
                        "Merged flames keep their original height");
                }
                level.setBlock(changing,Blocks.CACTUS.defaultBlockState(),2);
                check(FireSurface.foliageDepth(level,changing.above())==0,"Solid cactus remains a fitted support");
                level.setBlock(changing,Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER),2);
                level.setBlock(changing.above(),Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
                check(FireSurface.foliageDepth(level,changing.above(2))==2,
                    "Fire crosses both halves of tall grass");
                near(FireSurface.below(level,changing.above(2)).getFirst().y(),-2,
                    "Tall-grass fire begins on the ground");
                level.setBlock(changing.above(),Blocks.AIR.defaultBlockState(),2);
                level.setBlock(changing,Blocks.AIR.defaultBlockState(),2);
                for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    level.setBlock(changing.relative(direction), Blocks.OAK_FENCE.defaultBlockState(), 2);
                    var sideState = fire.setValue(FireBlock.PROPERTY_BY_DIRECTION.get(direction), true);
                    var sheets = naturality.fire.FireGeometry.sides(level, changing, sideState);
                    check(sheets.size() == 1, "Isolated fence exposes one side");
                    near((float) sheets.getFirst().bottomLeft().distanceTo(sheets.getFirst().bottomRight()), 0.25F,
                        "Side flame is exactly four texture pixels wide");
                    var box = naturality.fire.FireGeometry.shape(level, changing, sideState).bounds();
                    if (direction.getAxis() == net.minecraft.core.Direction.Axis.X)
                        check(direction.getStepX() > 0 ? box.minX > 1.3 : box.maxX < -0.3, "Side fire moves onto inset fence face");
                    else check(direction.getStepZ() > 0 ? box.minZ > 1.3 : box.maxZ < -0.3, "Side fire moves onto inset fence face");
                    level.setBlock(changing.relative(direction), Blocks.AIR.defaultBlockState(), 2);
                }
                var outline = fire.getShape(level, new BlockPos(-6, 101, 0));
                for (int rotation = 0; rotation < 16; rotation++) {
                    for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        var supportPos = changing.relative(direction);
                        level.setBlock(supportPos, Blocks.OAK_SIGN.defaultBlockState().setValue(
                            net.minecraft.world.level.block.StandingSignBlock.ROTATION, rotation), 2);
                        var side = fire.setValue(FireBlock.PROPERTY_BY_DIRECTION.get(direction), true);
                        var sheets = naturality.fire.FireGeometry.sides(level, changing, side);
                        check(sheets.size() == 1, "Every sign rotation emits one sheet per attached face");
                        near((float) sheets.getFirst().bottomLeft().distanceTo(sheets.getFirst().bottomRight()),
                            1, "Sign sprite spans the whole board");
                        check(naturality.fire.FireGeometry.shape(level, changing, side).toAabbs().size() == 1,
                            "Rotated sign fire has exactly one hitbox");
                        check(naturality.fire.FireGeometry.supportBoxes(level, supportPos).size() == 1,
                            "Sign support queries do not voxelize the board");
                        level.setBlock(supportPos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                near((float) outline.min(net.minecraft.core.Direction.Axis.Y), -0.5F, "Selection box follows lowered slab flame");
                for (var supportPos : List.of(new BlockPos(-6, 101, 0), new BlockPos(-2, 101, 0),
                        new BlockPos(2, 101, 0), new BlockPos(-2, 101, 4))) {
                    for (var state : List.of(fire, Blocks.SOUL_FIRE.defaultBlockState())) {
                        for (var box : state.getShape(level, supportPos).toAabbs())
                            near((float) (box.maxY - box.minY), 1F / 16,
                                "Normal and soul floor fire keep thin hitboxes on every support");
                    }
                }
                // Client-only scene keeps nonflammable supports testable without changing survival rules.
                for (int i = 0; i < supports.length; i++) {
                    level.setBlock(new BlockPos(-6 + (i % 4) * 4, 101, (i / 4) * 4), fire, 2);
                }
            });
            world.getConnection().waitForChunksRender();
            context.waitTicks(5);
            context.takeScreenshot("fire-support-surfaces");
            server.runCommand("tp @a -9 102 -3 -55 10");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("fire-support-surfaces-oblique");
            // Reproduce the reported stacked fence-post side placement, plus unique normal/soul flames.
            context.runOnClient(client -> {
                for (var p : BlockPos.betweenClosed(-8, 100, -1, 10, 103, 6))
                    client.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            });
            server.runCommand("tp @a 1 102 -6 0 8");
            server.runCommand("fill -8 100 -1 10 103 6 air");
            for (int x : new int[]{-4, 0, 4}) {
                server.runCommand("setblock " + x + " 100 1 oak_fence");
                server.runCommand("setblock " + x + " 101 1 oak_fence");
                server.runCommand("setblock " + x + " 101 0 fire[south=true]");
            }
            for (int x = -4; x <= 4; x += 2) {
                server.runCommand("setblock " + x + " 100 4 soul_sand");
                server.runCommand("setblock " + x + " 101 4 soul_fire");
            }
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            long[] ticks = new long[1];
            context.runOnClient(client -> ticks[0] = naturality.client.fire.ProceduralFire.ticks());
            context.takeScreenshot("fire-side-posts-and-soul");
            context.waitTicks(15);
            context.runOnClient(client -> check(naturality.client.fire.ProceduralFire.ticks() > ticks[0], "Heat simulation animates"));
            context.takeScreenshot("fire-side-posts-and-soul-later");
            server.runCommand("tp @a 0.5 101 -1.5 0 0");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("fire-post-side-close");
            // Spectators do not activate the vanilla player-radius fire tick gate.
            server.runCommand("gamerule minecraft:fire_spread_radius_around_player -1");
            server.runOnServer(s -> {
                var level = s.overworld();
                var probe = new BlockPos(10, 105, 6);
                for (var support : List.of(Blocks.ANVIL, Blocks.STONE_SLAB, Blocks.GLASS, Blocks.IRON_BARS)) {
                    level.setBlock(probe.below(), support.defaultBlockState(), 3);
                    check(net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, probe,
                        net.minecraft.core.Direction.UP), "Flint and steel accepts " + support);
                    var placed = net.minecraft.world.level.block.BaseFireBlock.getState(level, probe);
                    check(FireSurface.isFloorFire(placed), "Irregular support chooses floor fire");
                    level.setBlock(probe, placed, 3);
                    check(level.getBlockState(probe).is(Blocks.FIRE), "Fire survives placement");
                    placed.tick(level, probe, RandomSource.create(123));
                    check(level.getBlockState(probe).is(Blocks.FIRE), "Partial support does not extinguish on first tick");
                    var old = placed.setValue(FireBlock.AGE, 4);
                    level.setBlock(probe, old, 3);
                    old.tick(level, probe, RandomSource.create(123));
                    check(level.getBlockState(probe).isAir(), "Nonflammable support still burns out");
                    level.setBlock(probe.below(), Blocks.AIR.defaultBlockState(), 3);
                }
                check(!net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, probe,
                    net.minecraft.core.Direction.UP), "Unsupported air cannot hold fire");
                var pos = new BlockPos(0, 101, 0);
                var state = level.getBlockState(pos);
                check(state.is(Blocks.FIRE), "Real side fire survives on fence");
                var shape = state.getShape(level, pos);
                check(shape.bounds().minZ > 1.3, "Server shares displaced side shape");
                var from = new net.minecraft.world.phys.Vec3(2, 101.5, 1.34);
                var to = new net.minecraft.world.phys.Vec3(-2, 101.5, 1.34);
                var hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
                check(hit.getBlockPos().equals(pos), "Ray outside fire voxel picks displaced fire, not support");
                var passThrough = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
                check(passThrough.getType() == net.minecraft.world.phys.HitResult.Type.MISS, "Collision rays still pass through fire");
                var pig = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                pig.setNoAi(true); pig.setNoGravity(true);
                pig.setPos(0.5, 101.1, 1.1);
                pig.setRemainingFireTicks(0);
                float health = pig.getHealth();
                pig.applyEffectsFromBlocks(pig.position(), pig.position());
                check(pig.getHealth() < health && pig.isOnFire(), "Inset flame burns entities outside its owning voxel");
                var safe = net.minecraft.world.entity.EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                safe.setNoAi(true); safe.setNoGravity(true);
                safe.setPos(0.5, 101.1, 0.5);
                float safeHealth = safe.getHealth();
                safe.applyEffectsFromBlocks(safe.position(), safe.position());
                check(safe.getHealth() == safeHealth && !safe.isOnFire(), "Original empty fire voxel no longer burns");
                level.setBlock(new BlockPos(1, 101, 1), Blocks.STONE.defaultBlockState(), 2);
                var blocked = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
                check(blocked.getBlockPos().equals(new BlockPos(1, 101, 1)), "Foreground blocks occlude displaced fire targeting");
                level.setBlock(new BlockPos(1, 101, 1), Blocks.AIR.defaultBlockState(), 2);
            });
            server.runCommand("gamerule minecraft:fire_spread_radius_around_player 0");
            for (int i = 0; i < 3; i++) {
                int x = (i - 1) * 3;
                server.runCommand("setblock " + x + " 100 1 stone");
                server.runCommand("setblock " + x + " 101 1 oak_sign[rotation=" + (i * 2) + "]");
                server.runCommand("setblock " + x + " 101 0 fire[south=true]");
            }
            server.runCommand("tp @a 0.5 102 -4 0 5");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(5);
            context.takeScreenshot("fire-single-sign-sheets");
            server.runCommand("fill -4 100 0 4 105 1 air");
            server.runCommand("fill -4 100 1 4 104 1 oak_planks");
            server.runCommand("fill -2 101 0 1 103 0 fire[south=true]");
            server.runCommand("setblock 1 102 0 air");
            server.runCommand("setblock 3 104 0 fire[south=true]");
            server.runCommand("tp @a 0 102 -6 0 0");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                var pos = new BlockPos(-1, 102, 0);
                var state = client.level.getBlockState(pos);
                var sheet = naturality.fire.FireGeometry.sides(client.level, pos, state).getFirst();
                int color = naturality.client.fire.ConnectedFire.color(client.level, pos, state, sheet);
                check(((color >>> 20) & 15) == 15, "Interior wall fire connects on four edges");
                var isolated = new BlockPos(3, 104, 0);
                var isolatedState = client.level.getBlockState(isolated);
                var single = naturality.fire.FireGeometry.sides(client.level, isolated, isolatedState).getFirst();
                check(((naturality.client.fire.ConnectedFire.color(client.level, isolated, isolatedState, single)
                    >>> 20) & 15) == 0, "Detached fire retains its vanilla sprite");
                client.level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                color = naturality.client.fire.ConnectedFire.color(client.level, pos, state, sheet);
                check(((color >>> 20) & 4) == 0, "Removing a neighbour opens the flame edge");
            });
            context.waitTicks(5);
            context.takeScreenshot("fire-connected-wall");
            context.waitTicks(10);
            context.takeScreenshot("fire-connected-wall-later");
            context.runOnClient(client -> client.options.improvedTransparency().set(true));
            context.waitTicks(10);
            context.takeScreenshot("fire-connected-wall-oit");
            context.runOnClient(client -> client.options.improvedTransparency().set(false));
            var reload = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
            context.runOnClient(client -> reload.set(client.reloadResourcePacks()));
            context.waitFor(client -> reload.get().isDone());
            reload.get().join();
            context.waitFor(client -> client.gui.overlay() == null);
            context.waitTicks(5);
            context.takeScreenshot("fire-reloaded");
            server.runCommand("fill -6 100 -6 6 106 0 air");
            server.runCommand("fill -6 100 -6 6 100 0 stone");
            server.runCommand("summon zombie -2 101 -2 {NoAI:1b,Silent:1b,Invulnerable:1b,Fire:1200s}");
            server.runCommand("summon pig 0 101 -2 {NoAI:1b,Silent:1b,Invulnerable:1b,Fire:1200s}");
            server.runCommand("summon slime 3 101 -2 {Size:2,NoAI:1b,Silent:1b,Invulnerable:1b,Fire:1200s}");
            server.runCommand("tp @a 0.5 102 -7 0 4");
            world.getConnection().waitForClientboundPackets();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            context.runOnClient(client -> {
                int burning = 0;
                for (var entity : client.level.entitiesForRendering()) {
                    if (!entity.displayFireAnimation()) continue;
                    var renderState = client.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 0.5F);
                    var data = (naturality.client.fire.EntityFireState) renderState;
                    check(data.naturality$fireBox() != null, "Burning entity extracts its actual hitbox");
                    var bounds = naturality.client.fire.EntityFireRenderer.envelope(data.naturality$fireBox());
                    check(bounds.maxY > data.naturality$fireBox().maxY, "Flames extend above entity hitbox");
                    near((float) bounds.minY, (float) data.naturality$fireBox().minY, "Flame root follows feet");
                    burning++;
                }
                check(burning >= 3, "Short, tall and wide burning entities rendered");
            });
            context.takeScreenshot("fire-entity-hitboxes");
            server.runCommand("tp @a -5 103 -5 -50 15");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("fire-entity-hitboxes-oblique");
            server.runCommand("gamemode survival @a");
            server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player -> player.setRemainingFireTicks(200)));
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(5);
            context.takeScreenshot("fire-player-overlay");
            context.runOnClient(client -> client.options.improvedTransparency().set(true));
            context.waitTicks(5);
            context.takeScreenshot("fire-player-overlay-oit");
            context.runOnClient(client -> client.options.improvedTransparency().set(false));
            server.runCommand("kill @e[type=!player]");
            server.runCommand("weather minecraft:overworld rain 100");
            server.runCommand("fill -6 101 -3 6 101 1 lava");
            server.runCommand("summon ravager 0 101 -2 {NoAI:1b,Invulnerable:1b}");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0.5 103 -7 0 8");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(25);
            context.runOnClient(client -> {
                check(client.level.getRainLevel(0) > 0, "Rain is active during fire visual test");
                int burningRavagers = 0;
                for (var entity : client.level.entitiesForRendering()) {
                    if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().equals("ravager")) {
                        check(entity.isInLava(), "Ravager remains in lava during rain");
                        var renderState = client.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 0.5F);
                        check(renderState.displayFireAnimation, "Ravager in rainy lava submits fire geometry");
                        burningRavagers++;
                    }
                }
                check(burningRavagers == 1, "Ravager in lava is visible to the client");
            });
            context.takeScreenshot("fire-ravager-lava");
            server.runCommand("effect give @a minecraft:resistance 30 4 true");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 101 -2 0 10");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(10);
            context.runOnClient(client -> check(client.player.isInLava(), "Player in rainy lava has an active fire source"));
            context.takeScreenshot("fire-player-lava-overlay");
        }
    }

    private static void checkSimulation() {
        var a = new naturality.client.fire.FireSimulation(123);
        var b = new naturality.client.fire.FireSimulation(456);
        for (int tick = 0; tick < 80; tick++) { a.tick(); b.tick(); }
        float difference = 0, bottom = 0, top = 0;
        for (int x = 0; x < 256; x++) {
            difference += Math.abs(a.sample(x, 8) - b.sample(x, 8));
            bottom += a.sample(x, 0); top += a.sample(x, 31);
        }
        check(difference > 1, "Independent fuel creates different flames");
        check(bottom > top, "Fire rises and cools");
        var seeds = new java.util.HashSet<Integer>();
        for (int x = -20; x <= 20; x++) seeds.add(naturality.client.fire.ProceduralFire.seedColor(new BlockPos(x, 100, 0)));
        check(seeds.size() == 41, "Neighboring fires have distinct stable seeds");
    }

    private static float area(List<FireSurface.Patch> patches) {
        float area = 0;
        for (var p : patches) area += Math.abs(p.ux() * p.vz() - p.uz() * p.vx());
        return area;
    }
    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.0001, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
