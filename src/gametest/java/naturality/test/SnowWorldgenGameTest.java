package naturality.test;

import naturality.config.NaturalityServerConfig;
import naturality.snow.SnowGeometry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;

public final class SnowWorldgenGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        boolean wrapping=NaturalityServerConfig.get().snowWrapping;
        NaturalityServerConfig.get().snowWrapping=true;
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("fill 0 100 0 47 100 15 grass_block");
            server.runCommand("fillbiome -16 96 -16 31 112 15 snowy_plains");
            server.runCommand("fillbiome 32 96 -16 63 112 15 plains");
            server.runCommand("setblock 0 101 0 short_grass");
            server.runCommand("setblock 1 101 0 tall_grass[half=lower]");
            server.runCommand("setblock 1 102 0 tall_grass[half=upper]");
            server.runCommand("setblock 2 101 0 fern");
            server.runCommand("setblock 3 101 0 dandelion");
            server.runCommand("setblock 4 101 0 oak_leaves[persistent=true]");
            server.runCommand("setblock 5 101 0 short_grass");
            server.runCommand("setblock 5 105 0 stone");
            server.runCommand("setblock 40 101 0 short_grass");
            server.runOnServer(instance -> {
                var level=instance.overworld();
                new SnowAndFreezeFeature().place(level,level.getChunkSource().getGenerator(),RandomSource.create(1),new BlockPos(0,100,0));
                new SnowAndFreezeFeature().place(level,level.getChunkSource().getGenerator(),RandomSource.create(1),new BlockPos(32,100,0));
                for(int x:new int[]{0,1,2,3}) {
                    var plant=new BlockPos(x,101,0);
                    check(SnowGeometry.isFoliage(level.getBlockState(plant)),"Worldgen preserves foliage");
                    var owner=new BlockPos(x,x==1?103:102,0);
                    check(level.getBlockState(owner).is(Blocks.SNOW),"Worldgen places fitted snow above each foliage column: "+x
                        +" height="+level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,0)
                        +" biome="+level.getBiome(owner).unwrapKey()+" shouldSnow="+level.getBiome(owner).value().shouldSnow(level,owner)
                        +" support="+level.getBlockState(owner).isAir()+" surfaces="+SnowGeometry.surfaces(level,owner));
                    check(level.getBlockState(owner).canSurvive(level,owner),"Generated snow has valid fitted support");
                    check(SnowGeometry.surfaces(level,owner).stream().anyMatch(p -> p.y()<0),"Generated snow reaches exposed ground around foliage");
                    check(level.getBlockState(plant.below()).getValue(SnowyBlock.SNOWY),"Ground under coated foliage is snowy");
                }
                check(level.getBlockState(new BlockPos(1,102,0)).is(Blocks.TALL_GRASS),"Tall foliage upper half survives");
                check(level.getBlockState(new BlockPos(4,102,0)).is(Blocks.SNOW),"Normal canopy snow still generates");
                check(level.getBlockState(new BlockPos(5,102,0)).isAir(),"Foliage under roofs receives no worldgen snow");
                check(level.getBlockState(new BlockPos(40,101,0)).is(Blocks.SHORT_GRASS) && level.getBlockState(new BlockPos(40,102,0)).isAir(),"Warm biome foliage remains snow-free");
                NaturalityServerConfig.get().snowWrapping=false;
                for(int x:new int[]{0,1,2,3})level.setBlock(new BlockPos(x,x==1?103:102,0),Blocks.AIR.defaultBlockState(),2);
                new SnowAndFreezeFeature().place(level,level.getChunkSource().getGenerator(),RandomSource.create(1),new BlockPos(0,100,0));
                check(level.getBlockState(new BlockPos(0,102,0)).isAir(),"Foliage snow requires wrapping enabled");
            });
        } finally { NaturalityServerConfig.get().snowWrapping=wrapping; }
    }
}
