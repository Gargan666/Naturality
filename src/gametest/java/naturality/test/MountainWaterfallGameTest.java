package naturality.test;

import java.util.LinkedHashMap;
import naturality.worldgen.MountainWaterfallFeature;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.FluidTags;

public final class MountainWaterfallGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            for(int y=120;y<180;y+=10)server.runCommand("fill -16 "+y+" -16 31 "+(y+9)+" 31 air");
            server.runCommand("fill -16 119 -16 31 119 31 stone");
            for(int y=120;y<160;y+=10)server.runCommand("fill -16 "+y+" -16 16 "+(y+9)+" 31 stone");
            server.runCommand("fill -16 160 -16 16 160 31 stone");
            for(int y=112;y<176;y+=8)server.runCommand("fillbiome -16 "+y+" -16 31 "+(y+7)+" 31 stony_peaks");
            var center=new BlockPos(8,159,8);
            var before=new LinkedHashMap<BlockPos,BlockState>();
            server.runOnServer(instance -> {
                var level=instance.overworld();
                long started=System.nanoTime();
                var plan=TestFixtures.require(MountainWaterfallFeature.plan(level,center,7,3,Direction.EAST,10), "plan");
                System.out.println("Mountain waterfall plan: "+(System.nanoTime()-started)/1_000_000.0+" ms, blocks="+(plan==null?0:plan.size()));
                check(plan!=null,"Controlled cliff produces lake and waterfall");
                check(plan.entrySet().stream().filter(e -> e.getKey().getY()==159 && e.getValue().getFluidState().isSource()).count()>90,"Large source-water lake");
                check(plan.entrySet().stream().anyMatch(e -> e.getKey().getY()==120 && e.getValue().getFluidState().is(FluidTags.WATER)),"Water reaches ground before any ticks");
                check(plan.entrySet().stream().anyMatch(e -> e.getKey().getY()==120 && !e.getValue().getFluidState().isEmpty() && !e.getValue().getFluidState().isSource() && !e.getValue().getFluidState().getValue(net.minecraft.world.level.material.FlowingFluid.FALLING)),"Landing spread precomputed");
                check(plan.equals(MountainWaterfallFeature.plan(level,center,7,3,Direction.EAST,10)),"Identical terrain produces identical plan");
                level.setBlock(center.below(),Blocks.CHEST.defaultBlockState(),2);
                check(MountainWaterfallFeature.plan(level,center,7,3,Direction.EAST,10)==null,"Protected block rejects entire plan");
                check(level.getBlockState(center).is(Blocks.STONE),"Rejected plan leaves terrain unchanged");
                level.setBlock(center.below(),Blocks.STONE.defaultBlockState(),2);
                long seed=0;
                while(true) {
                    var random=RandomSource.create(seed);
                    if(random.nextInt(4)==0 && random.nextInt(2)==0)break;
                    seed++;
                }
                check(new MountainWaterfallFeature().place(level,level.getChunkSource().getGenerator(),RandomSource.create(seed),center),"Registered feature places the entire waterfall on a valid mountain cliff");
                for(int x=-16;x<=31;x++)for(int z=-16;z<=31;z++)for(int y=119;y<=160;y++) {
                    var pos=new BlockPos(x,y,z);
                    if(level.getFluidState(pos).is(FluidTags.WATER))before.put(pos,level.getBlockState(pos));
                }
                // Native ticks validate the prepared falling columns and terminal water amounts.
                for(int i=0;i<20;i++)for(var pos:before.keySet()) {
                    var state=level.getBlockState(pos);var fluid=state.getFluidState();
                    if(fluid.is(FluidTags.WATER))((net.minecraft.world.level.material.FlowingFluid)fluid.getType()).tick(level,pos,state,fluid);
                }
                long changed=before.entrySet().stream().filter(e -> !level.getBlockState(e.getKey()).equals(e.getValue())).count();
                check(changed==0,"Generated flow already matches native settled water; changed="+changed);
                int water=0;
                for(int x=-16;x<=31;x++)for(int z=-16;z<=31;z++)for(int y=119;y<=160;y++)
                    if(level.getFluidState(new BlockPos(x,y,z)).is(FluidTags.WATER))water++;
                check(water==before.size(),"Native water does not extend beyond the precomputed flow: "+before.size()+" -> "+water);
                // Flat uplands and warm lowlands must not produce arbitrary lakes.
                check(!new MountainWaterfallFeature().place(level,level.getChunkSource().getGenerator(),RandomSource.create(3),new BlockPos(-16,160,0)),"Flat upland rejected");
            });
            // A wider fall lands on a shelf, then continues down a second step.
            for(int y=120;y<180;y+=10)server.runCommand("fill -16 "+y+" -16 31 "+(y+9)+" 31 air");
            for(int y=120;y<160;y+=10)server.runCommand("fill -16 "+y+" -16 16 "+(y+9)+" 31 stone");
            server.runCommand("fill -16 160 -16 16 160 31 stone");
            server.runCommand("fill 17 120 -16 21 128 31 stone");
            server.runOnServer(instance -> {
                var level=instance.overworld();
                var plan=TestFixtures.require(MountainWaterfallFeature.plan(level,center,7,5,Direction.EAST,10), "plan");
                check(plan!=null,"Wide fall on stepped terrain produces a complete plan");
                for(var entry:plan.entrySet())level.setBlock(entry.getKey(),entry.getValue(),2);
                var prepared=new LinkedHashMap<BlockPos,BlockState>();
                for(var entry:plan.entrySet())if(entry.getValue().getFluidState().is(FluidTags.WATER))prepared.put(entry.getKey(),entry.getValue());
                check(prepared.keySet().stream().anyMatch(p -> p.getX()>=22 && p.getY()==120),"Secondary step and landing filled before ticking");
                for(int i=0;i<30;i++)for(var pos:prepared.keySet()) {
                    var state=level.getBlockState(pos);var fluid=state.getFluidState();
                    if(fluid.is(FluidTags.WATER))((net.minecraft.world.level.material.FlowingFluid)fluid.getType()).tick(level,pos,state,fluid);
                }
                long changed=prepared.entrySet().stream().filter(e -> !level.getBlockState(e.getKey()).equals(e.getValue())).count();
                check(changed==0,"Wide stepped flow stays settled under native fluid ticks: "+changed);
                int water=0;
                for(int x=-16;x<=31;x++)for(int z=-16;z<=31;z++)for(int y=119;y<=160;y++)
                    if(level.getFluidState(new BlockPos(x,y,z)).is(FluidTags.WATER))water++;
                check(water==prepared.size(),"Stepped flow does not need extra spread on load");
            });
            for(int y=120;y<180;y+=10)server.runCommand("fill -16 "+y+" -16 31 "+(y+9)+" 31 air");
            for(int y=120;y<160;y+=10)server.runCommand("fill -16 "+y+" -16 16 "+(y+9)+" 31 stone");
            server.runCommand("fill -16 160 -16 16 160 31 stone");
            for(int y=112;y<176;y+=8)server.runCommand("fillbiome -16 "+y+" -16 31 "+(y+7)+" 31 snowy_slopes");
            server.runOnServer(instance -> {
                var level=instance.overworld();
                var plan=TestFixtures.require(MountainWaterfallFeature.plan(level,center,7,3,Direction.EAST,10), "plan");
                check(plan!=null,"Frozen mountain retains an open underwater spillway");
                check(plan.values().stream().anyMatch(s -> s.is(Blocks.ICE)),"Frozen lake has a native ice lid");
                var prepared=new LinkedHashMap<BlockPos,BlockState>();
                for(var entry:plan.entrySet()) {
                    level.setBlock(entry.getKey(),entry.getValue(),2);
                    if(entry.getValue().getFluidState().is(FluidTags.WATER))prepared.put(entry.getKey(),entry.getValue());
                }
                check(prepared.keySet().stream().anyMatch(p -> p.getY()==120),"Frozen lake's fall reaches the ground before ticks");
                for(int x=-16;x<=16;x+=16)for(int z=-16;z<=16;z+=16)
                    new net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature().place(level,level.getChunkSource().getGenerator(),RandomSource.create(1),new BlockPos(x,100,z));
                for(int i=0;i<30;i++)for(var pos:prepared.keySet()) {
                    var state=level.getBlockState(pos);var fluid=state.getFluidState();
                    if(fluid.is(FluidTags.WATER))((net.minecraft.world.level.material.FlowingFluid)fluid.getType()).tick(level,pos,state,fluid);
                }
                check(prepared.entrySet().stream().allMatch(e -> level.getBlockState(e.getKey()).equals(e.getValue())),"Snow/freeze pass cannot shut off or delay the prepared waterfall");
            });
            for(int y=120;y<180;y+=10)server.runCommand("fill -16 "+y+" -16 31 "+(y+9)+" 31 air");
            for(int y=120;y<160;y+=10)server.runCommand("fill -16 "+y+" -16 16 "+(y+9)+" 31 stone");
            server.runCommand("fill -16 160 -16 16 160 31 stone");
            for(int y=0;y<130;y+=10)server.runCommand("fill 17 "+y+" -16 31 "+(y+9)+" 31 air");
            server.runOnServer(instance -> {
                var level=instance.overworld();
                // Detection excludes cliffs that cannot be fully filled within the bounded plan.
                check(MountainWaterfallFeature.plan(level,center,7,5,Direction.EAST,10)==null,"Overly deep downstream path rejects the whole feature");
                check(level.getBlockState(center).is(Blocks.STONE),"Rejected deep-path plan does not carve a partial lake");
            });
        }
    }
}
