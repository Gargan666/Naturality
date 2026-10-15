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
        try(var world=TestWorlds.disposable(context).create()) {
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
            for(int x:new int[]{8,9,10,11,12,40}) {
                for(int y:new int[]{104,107,110})server.runCommand("setblock "+x+" "+y+" 8 spruce_leaves[persistent=true]");
            }
            server.runCommand("setblock 9 101 8 fern");
            server.runCommand("setblock 10 106 8 stone");
            server.runCommand("setblock 11 112 8 stone");
            server.runCommand("setblock 12 101 8 water");
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
                for(int y:new int[]{101,105,108,111})check(level.getBlockState(new BlockPos(8,y,8)).is(Blocks.SNOW),"Snow covers ground and every separated leaf tier: "+y);
                check(level.getBlockState(new BlockPos(8,100,8)).getValue(SnowyBlock.SNOWY),"Canopy ground marked snowy");
                check(level.getBlockState(new BlockPos(9,101,8)).is(Blocks.FERN) && level.getBlockState(new BlockPos(9,102,8)).is(Blocks.SNOW),"Sheltered vegetation preserved with fitted snow");
                check(level.getBlockState(new BlockPos(10,105,8)).isAir() && level.getBlockState(new BlockPos(10,101,8)).isAir(),"Solid roof below canopy blocks deeper snow");
                check(level.getBlockState(new BlockPos(11,108,8)).isAir(),"Solid roof above tree blocks canopy pass");
                check(level.getBlockState(new BlockPos(12,101,8)).is(Blocks.WATER),"Canopy water preserved");
                check(level.getBlockState(new BlockPos(40,105,8)).isAir() && level.getBlockState(new BlockPos(40,101,8)).isAir(),"Warm canopy and ground snow-free");
                level.setBlock(new BlockPos(8,105,8),Blocks.AIR.defaultBlockState(),2);
                level.setBlock(new BlockPos(8,101,8),Blocks.AIR.defaultBlockState(),2);
                NaturalityServerConfig.get().snowWrapping=false;
                for(int x:new int[]{0,1,2,3})level.setBlock(new BlockPos(x,x==1?103:102,0),Blocks.AIR.defaultBlockState(),2);
                new SnowAndFreezeFeature().place(level,level.getChunkSource().getGenerator(),RandomSource.create(1),new BlockPos(0,100,0));
                check(level.getBlockState(new BlockPos(0,102,0)).isAir(),"Foliage snow requires wrapping enabled");
                check(level.getBlockState(new BlockPos(8,105,8)).isAir() && level.getBlockState(new BlockPos(8,101,8)).isAir(),"Disabled wrapping leaves canopy interiors unchanged");
            });
        } finally { NaturalityServerConfig.get().snowWrapping=wrapping; }
    }
}
