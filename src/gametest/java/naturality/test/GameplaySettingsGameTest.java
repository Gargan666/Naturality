package naturality.test;

import naturality.config.GameplaySettings;
import naturality.config.NaturalityServerConfig;
import naturality.portal.PortalCrossing;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;

public final class GameplaySettingsGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        var config = NaturalityServerConfig.get();
        boolean wrapping = config.fireWrapping, vanilla = config.vanillaPortalEntry;
        boolean snowWrapping = config.snowWrapping, compaction = config.snowCompaction, thaw = config.weatherThaw, depth = config.weatherSnowAccumulation;
        var pos = new BlockPos(0, 101, 0);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 104 -5");
            server.runCommand("setblock 0 100 0 stone_slab[type=bottom]");
            server.runOnServer(s -> {
                config.snowWrapping = config.snowCompaction = config.weatherThaw = config.weatherSnowAccumulation = false;
                config.fireWrapping = false;
                config.vanillaPortalEntry = true;
            });
            context.waitFor(client -> !GameplaySettings.clientFireWrapping() && !GameplaySettings.clientPhysicalPortalEntry()
                && !GameplaySettings.clientSnowWrapping() && !GameplaySettings.clientSnowCompaction()
                && !GameplaySettings.clientWeatherThaw() && !GameplaySettings.clientWeatherSnowAccumulation());
            world.getConnection().waitForClientboundPackets();
            server.runOnServer(s -> {
                var level = s.overworld();
                var snow = Blocks.SNOW.defaultBlockState();
                check(!snow.canSurvive(level, pos), "Disabled snow wrapping rejects a bottom slab");
                check(snow.getShape(level, pos).min(Direction.Axis.Y) == 0, "Disabled snow outline stays in its saved cell");
                var layers = snow.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 3);
                check(layers.getCollisionShape(level, pos).min(Direction.Axis.Y) == 0, "Disabled snow collision stays in its saved cell");
                var ordinary = pos.offset(3, 0, 0);
                level.setBlock(ordinary.below(), Blocks.STONE.defaultBlockState(), 18);
                level.setBlock(ordinary, layers, 18);
                naturality.snow.SnowCompaction.press(level, ordinary, 100);
                check(level.getBlockState(ordinary).equals(layers), "Disabled compaction does not remove layers");
                check(!naturality.weather.WeatherThaw.thawAt(level, ordinary), "Disabled thaw never modifies snow");
                check(naturality.weather.WeatherSnow.accumulationLimit(level, 8) == 8, "Disabled weather depth honors vanilla gamerule");
                var fire = Blocks.FIRE.defaultBlockState();
                check(!fire.canSurvive(level, pos), "Disabled wrapping must reject non-sturdy, nonflammable support");
                check(fire.getShape(level, pos).min(Direction.Axis.Y) >= 0, "Vanilla fire outline must stay in its own block");
                var portal = (NetherPortalBlock) Blocks.NETHER_PORTAL;
                var player = world.getConnection().getServerPlayer();
                var processor = new PortalProcessor(portal, pos);
                int delay = portal.getPortalTransitionTime(level, player);
                check(delay > 0, "Survival must have a vanilla waiting period");
                for (int tick = 0; tick < delay; tick++) {
                    processor.setAsInsidePortalThisTick(true);
                    check(!processor.processPortalTeleportation(level, player, true), "Vanilla entry must wait for its timer");
                }
                processor.setAsInsidePortalThisTick(true);
                check(processor.processPortalTeleportation(level, player, true), "Vanilla entry must travel after its timer");
                var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
                check(new PortalProcessor(portal, pos).processPortalTeleportation(level, pig, true), "Vanilla mobs must retain zero-delay travel");
                PortalCrossing.enter(pig, pos, Direction.Axis.X);
                check(PortalCrossing.get(pig) == null, "Vanilla mode must not create physical crossings");
            });
            context.runOnClient(client -> {
                var snow = Blocks.SNOW.defaultBlockState();
                check(snow.getShape(client.level, pos).min(Direction.Axis.Y) == 0, "Client snow outline follows server OFF");
                var snowModel = client.getModelManager().getBlockStateModelSet().get(snow);
                float[] snowBottom = {Float.POSITIVE_INFINITY};
                var snowEmitter = Renderer.get().quadEmitter(q -> {
                    for (int i=0;i<4;i++) snowBottom[0] = Math.min(snowBottom[0], q.y(i));
                });
                snowModel.emitQuads(snowEmitter,client.level,pos,snow,net.minecraft.util.RandomSource.create(0),face -> false);
                check(snowBottom[0] == 0, "Disabled snow wrapping emits vanilla model");
                var fire = Blocks.FIRE.defaultBlockState();
                var model = client.getModelManager().getBlockStateModelSet().get(fire);
                float[] bottom = {Float.POSITIVE_INFINITY};
                var emitter = Renderer.get().quadEmitter(q -> {
                    for (int i = 0; i < 4; i++) bottom[0] = Math.min(bottom[0], q.y(i));
                });
                model.emitQuads(emitter, client.level, pos, fire, net.minecraft.util.RandomSource.create(0), face -> false);
                var parts = new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
                model.collectParts(net.minecraft.util.RandomSource.create(0), parts);
                float vanillaBottom = Float.POSITIVE_INFINITY;
                for (var part : parts) for (var quad : part.getQuads(null))
                    for (int i = 0; i < 4; i++) vanillaBottom = Math.min(vanillaBottom, quad.position(i).y());
                check(Float.isFinite(bottom[0]) && Math.abs(bottom[0] - vanillaBottom) < 0.0001,
                    "Disabled wrapping must emit the original model, including its rotated flame sheets");
            });
            server.runOnServer(s -> {
                config.snowWrapping = config.snowCompaction = config.weatherThaw = config.weatherSnowAccumulation = true;
                config.fireWrapping = true;
                config.vanillaPortalEntry = false;
            });
            context.waitFor(client -> GameplaySettings.clientFireWrapping() && GameplaySettings.clientPhysicalPortalEntry() && GameplaySettings.clientSnowWrapping());
            server.runOnServer(s -> {
                var level = s.overworld();
                var fire = Blocks.FIRE.defaultBlockState();
                var snow = Blocks.SNOW.defaultBlockState();
                check(snow.canSurvive(level, pos), "Re-enabled wrapping supports slab snow");
                check(snow.getShape(level,pos).min(Direction.Axis.Y) == -.5, "Re-enabled snow outline fits slab");
                var ordinary=pos.offset(3,0,0);
                naturality.snow.SnowCompaction.press(level,ordinary,8);
                check(level.getBlockState(ordinary).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==2, "Re-enabled compaction removes a layer");
                check(fire.canSurvive(level, pos), "Wrapping must restore partial support placement");
                check(fire.getShape(level, pos).min(Direction.Axis.Y) == -0.5, "Wrapping must fit the slab outline");
                var anchor = pos.offset(5, 0, 0);
                level.setBlock(anchor, Blocks.NETHER_PORTAL.defaultBlockState(), 2);
                var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
                pig.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.375);
                pig.xo = pig.getX(); pig.zo = pig.getZ();
                PortalCrossing.enter(pig, anchor, Direction.Axis.X);
                check(PortalCrossing.get(pig) != null, "Physical crossing must return when vanilla entry is off");
                config.vanillaPortalEntry = true;
                PortalCrossing.tick(pig);
                check(((naturality.portal.PortalCrossingAccess) pig).naturality$getCrossing() == null,
                    "Switching to vanilla must clear an in-progress physical crossing");
            });
            context.waitFor(client -> !GameplaySettings.clientPhysicalPortalEntry());
        } finally {
            config.snowWrapping = snowWrapping; config.snowCompaction = compaction; config.weatherThaw = thaw; config.weatherSnowAccumulation = depth;
            config.fireWrapping = wrapping;
            config.vanillaPortalEntry = vanilla;
        }
        var old = new com.google.gson.Gson().fromJson("{\"portalIntroAnimation\":false}", NaturalityServerConfig.class);
        check(old.snowWrapping && old.snowCompaction && old.weatherThaw && old.weatherSnowAccumulation && old.fireWrapping && !old.vanillaPortalEntry && !old.portalIntroAnimation,
            "Existing server files must retain current behavior and their intro preference");
    }
}
