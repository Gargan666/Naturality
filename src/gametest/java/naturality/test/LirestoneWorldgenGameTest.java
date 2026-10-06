package naturality.test;

import naturality.NaturalityBlocks;
import naturality.worldgen.EndStoneLayers;
import naturality.worldgen.EndTerrainDensity;
import naturality.worldgen.LirestoneBoulderFeature;
import naturality.worldgen.LirestonePatches;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

public final class LirestoneWorldgenGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            BlockPos[] views = new BlockPos[4];
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var generator = (NoiseBasedChunkGenerator)end.getChunkSource().getGenerator();
                var random = end.getChunkSource().randomState();
                var sampler = random.samplersWithContext(SamplerContext.builder().build()).get(
                    new EndTerrainDensity(generator.generatorSettings().value().noiseRouter().finalDensity()));
                var terrain=(EndTerrainDensity.Sampler)random.getSampler(
                    new EndTerrainDensity(generator.generatorSettings().value().noiseRouter().finalDensity()));

                // Deposits remain coherent across chunk boundaries and vary with the world seed.
                var first = new LirestonePatches(1);
                var repeat = new LirestonePatches(1);
                var second = new LirestonePatches(2);
                int changedSeed = 0, neighboringChanges = 0, filled = 0, samples = 0, buried = 0, emptyRegions = 0;
                BlockPos rich = null, empty = null;
                for (int x=-8192;x<8192;x+=64) for (int z=-8192;z<8192;z+=64) {
                    double abundance=first.regionalAbundance(x,z);
                    if(abundance==0) {
                        emptyRegions++;
                        if(empty==null && first.regionalAbundance(x-256,z-256)==0
                            && first.regionalAbundance(x+256,z+256)==0
                            && first.regionalAbundance(x-256,z+256)==0
                            && first.regionalAbundance(x+256,z-256)==0) empty=new BlockPos(x,112,z);
                    }
                    if(rich==null && abundance>.99 && first.contains(x,112,z))rich=new BlockPos(x,112,z);
                    check(Math.abs(abundance-first.regionalAbundance(x+1,z))<.02,"Regional boundary jumps between blocks");
                    for (int y=32;y<224;y+=16) {
                    boolean patch=first.contains(x,y,z);
                    check(patch==repeat.contains(x,y,z),"Deposits are not seed reproducible");
                    check(abundance!=0 || !patch,"Patch-free region contains deposits");
                    if(patch)filled++;
                    if(first.contains(x,y,z,32,abundance))buried++;
                    if(patch!=second.contains(x,y,z))changedSeed++;
                    if(patch!=first.contains(x+1,y,z))neighboringChanges++;
                    samples++;
                    }
                }
                System.out.println("Lirestone regions: surface="+filled+"/"+samples+", buried="+buried+", empty columns="+emptyRegions);
                check(filled>samples*.005 && filled<samples*.12,"Regional deposits replace too little/too much stone: "+filled+"/"+samples);
                check(changedSeed>samples*.01 && neighboringChanges<samples*.04,"Deposits are speckles or ignore the seed");
                check(emptyRegions>256*256*.15 && rich!=null && empty!=null,"Missing rich or large patch-free regions");
                check(buried<filled*.2,"Deep deposits remain too common");
                for(int dx=-256;dx<=256;dx+=32)for(int dz=-256;dz<=256;dz+=32)for(int y=32;y<224;y+=16)
                    check(!first.contains(empty.getX()+dx,y,empty.getZ()+dz),"Empty region is only a small hole");

                // Exercise the complete material pass on both source materials, air and an unrelated block.
                var factory=PalettedContainerFactory.create(end.registryAccess());
                int changedCap=0, changedInterior=0;
                for (int offset=-2;offset<2;offset++) {
                    int cx=(rich.getX()>>4)+offset, cz=rich.getZ()>>4;
                    var chunk=new ProtoChunk(new ChunkPos(cx,cz),UpgradeData.EMPTY,end,factory,null);
                    var pos=new BlockPos.MutableBlockPos();
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=48;y<=112;y++) {
                        pos.set(cx*16+x,y,cz*16+z);
                        chunk.setBlockState(pos,(y>=111?Blocks.END_STONE:NaturalityBlocks.SMOOTH_ENDSTONE).defaultBlockState());
                    }
                    BlockPos protectedBlock=new BlockPos(cx*16,100,cz*16);
                    chunk.setBlockState(protectedBlock,Blocks.GOLD_BLOCK.defaultBlockState());
                    EndStoneLayers.apply(chunk,1);
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=48;y<=112;y++) {
                        pos.set(cx*16+x,y,cz*16+z);
                        if(chunk.getBlockState(pos).is(NaturalityBlocks.LIRESTONE)) {
                            if(y>=111)changedCap++;else changedInterior++;
                        }
                    }
                    check(chunk.getBlockState(protectedBlock).is(Blocks.GOLD_BLOCK),"Deposit replaced an unrelated block");
                    check(chunk.getBlockState(protectedBlock.atY(120)).isAir(),"Deposit filled empty space");
                }
                check(changedCap>20 && changedInterior>20,"Deposits did not replace both End Stone layers");

                int stone=0, deposits=0, surfacePatches=0, landColumns=0, boulderColumns=0, boulderChunks=0, side=0, sidePatches=0;
                int rimArea=0,rimPatches=0,interiorArea=0,interiorPatches=0;
                var probe=new BlockPos.MutableBlockPos();
                var actualPatches=new LirestonePatches(random.getOrCreateRandomFactory(naturality.Naturality.id("lirestone_patches"))
                    .at(0,0,0).nextLong());
                BlockPos survey=null;
                search: for(int x=1536;x<8192;x+=128)for(int z=1536;z<8192;z+=128) {
                    if(actualPatches.regionalAbundance(x,z)<.99)continue;
                    for(int y=80;y<224;y+=16)if(sampler.sampleValue(x,y,z)>0) {survey=new BlockPos(x,y,z);break search;}
                }
                check(survey!=null,"Missing a deposit-rich island region");
                int startX=(survey.getX()>>4)-8,startZ=(survey.getZ()>>4)-8;
                // A fresh 256-chunk region includes cliff deposits, plateaus and empty gaps.
                for(int cz=startZ;cz<startZ+16;cz++)for(int cx=startX;cx<startX+16;cx++)end.getChunk(cx,cz);
                for(int cz=startZ;cz<startZ+16;cz++)for(int cx=startX;cx<startX+16;cx++) {
                    var chunk=end.getChunk(cx,cz);
                    boolean boulder=false;
                    for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++) {
                        int x=cx*16+dx,z=cz*16+dz;
                        var materials=terrain.surfaceMaterials(x,z);
                        boolean entire=actualPatches.converts(materials.smallIsland());
                        boolean surface=true;int depth=0;
                        for(int y=255;y>=0;y--) {
                            probe.set(x,y,z);
                            var state=chunk.getBlockState(probe);
                            if(!LirestoneBoulderFeature.isEndStone(state)){depth=0;continue;}
                            depth++;
                            stone++;
                            if(state.is(NaturalityBlocks.LIRESTONE))deposits++;
                            if(!entire && depth>=12 && cx>startX && cx<startX+15 && cz>startZ && cz<startZ+15
                                && (end.getBlockState(probe.west()).isAir() || end.getBlockState(probe.east()).isAir()
                                    || end.getBlockState(probe.north()).isAir() || end.getBlockState(probe.south()).isAir())) {
                                side++;
                                if(state.is(NaturalityBlocks.LIRESTONE))sidePatches++;
                            }
                            if(surface) {
                                surface=false;landColumns++;
                                if(!entire && sampler.sampleValue(x,y,z)>0 && cx>startX && cx<startX+15
                                    && cz>startZ && cz<startZ+15
                                    && LirestoneBoulderFeature.isEndStone(end.getBlockState(probe.west().below(2)))
                                    && LirestoneBoulderFeature.isEndStone(end.getBlockState(probe.east().below(2)))
                                    && LirestoneBoulderFeature.isEndStone(end.getBlockState(probe.north().below(2)))
                                    && LirestoneBoulderFeature.isEndStone(end.getBlockState(probe.south().below(2)))) {
                                    // Classify actual terrain drops independently of the generator's rim calculation.
                                    boolean rim=end.getBlockState(probe.offset(12,-3,0)).isAir()
                                        || end.getBlockState(probe.offset(-12,-3,0)).isAir()
                                        || end.getBlockState(probe.offset(0,-3,12)).isAir()
                                        || end.getBlockState(probe.offset(0,-3,-12)).isAir();
                                    if(rim){rimArea++;if(state.is(NaturalityBlocks.LIRESTONE))rimPatches++;}
                                    else {interiorArea++;if(state.is(NaturalityBlocks.LIRESTONE))interiorPatches++;}
                                }
                                if(state.is(NaturalityBlocks.LIRESTONE)) {
                                    if(sampler.sampleValue(x,y,z)<=0) {
                                        boulderColumns++;boulder=true;
                                        if(views[1]==null)views[1]=probe.immutable();
                                    } else if(!entire) {
                                        surfacePatches++;
                                        if(views[0]==null)views[0]=probe.immutable();
                                    }
                                }
                            }
                        }
                    }
                    if(boulder)boulderChunks++;
                }
                System.out.println("Lirestone survey: deposits="+deposits+"/"+stone+", surface patches="+surfacePatches
                    +", cliff patches="+sidePatches+"/"+side+", boulder columns="+boulderColumns+"/"+landColumns+", boulder chunks="+boulderChunks);
                System.out.println("Lirestone rims="+rimPatches+"/"+rimArea+", interiors="+interiorPatches+"/"+interiorArea);
                check(rimArea>200 && interiorArea>200 && rimPatches/(double)rimArea>interiorPatches/(double)interiorArea*2,
                    "Clumps do not prefer the edges of flat ledges");
                check(deposits>stone*.001 && deposits<stone*.08 && surfacePatches>100,"Generated End lacks mixed surface/interior patches");
                check(side>1000 && sidePatches/(double)side < surfacePatches/(double)landColumns*.25
                    && sidePatches<side*.02,"Cliff faces still have too many Lirestone patches");
                check(boulderChunks>0 && boulderChunks<30 && boulderColumns>10 && boulderColumns<landColumns*.06,
                    "Boulders are missing or too frequent");

                // Locate an actual populated island chunk inside an entirely patch-free region.
                search: for(int x=1536;x<8192;x+=256)for(int z=1536;z<8192;z+=256) {
                    if(actualPatches.regionalAbundance(x,z)!=0 || actualPatches.regionalAbundance(x+15,z+15)!=0)continue;
                    var chunk=end.getChunk(x>>4,z>>4);int land=0,lirestone=0;
                    for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)for(int y=0;y<256;y++) {
                        probe.set(x+dx,y,z+dz);var state=chunk.getBlockState(probe);
                        if(LirestoneBoulderFeature.isEndStone(state))land++;
                        if(state.is(NaturalityBlocks.LIRESTONE))lirestone++;
                    }
                    if(land>1000 && lirestone==0) {
                        var column=generator.getBaseColumn(x+8,z+8,end,random);
                        for(int y=255;y>=0;y--)if(!column.getBlock(y).isAir()) {
                            views[2]=new BlockPos(x+8,y,z+8);break search;
                        }
                    }
                }
                check(views[2]!=null,"No actual island found without Lirestone patches");
                views[3]=LirestoneIslandChecks.run(end,terrain,actualPatches);

                var feature=new LirestoneBoulderFeature();
                check(!feature.place(end,generator,RandomSource.create(1),new BlockPos(0,65,0)),"Boulders modify the central island");
                check(!feature.place(end,generator,RandomSource.create(1),new BlockPos(2000,245,2000)),"Boulder floats in empty space");
                // Place over a chunk border on a test foundation, then reject an obstructed attempt atomically.
                BlockPos floor=new BlockPos(2000,230,2000);
                for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=-5;y<=6;y++)
                    end.setBlock(floor.offset(x,y,z),(y<=0?Blocks.END_STONE:Blocks.AIR).defaultBlockState(),2);
                check(feature.place(end,generator,RandomSource.create(42),floor.above()),"Boulder cannot span a chunk boundary");
                check(end.getBlockState(floor.above()).is(NaturalityBlocks.LIRESTONE),"Boulder has no exposed rock");
                for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=-5;y<=6;y++)
                    end.setBlock(floor.offset(x,y,z),(y<=0?Blocks.END_STONE:Blocks.AIR).defaultBlockState(),2);
                end.setBlock(floor.above(),Blocks.GOLD_BLOCK.defaultBlockState(),2);
                check(!feature.place(end,generator,RandomSource.create(42),floor.above()),"Boulder overwrites another feature");
                for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=-5;y<=6;y++)
                    check(!end.getBlockState(floor.offset(x,y,z)).is(NaturalityBlocks.LIRESTONE),"Failed boulder left partial blocks");
            });

            int[] oldDistance=new int[1];boolean[] hidden=new boolean[1];
            boolean oldFog=naturality.config.NaturalityConfig.get().fog.enabled;
            naturality.config.NaturalityConfig.get().fog.enabled=false;
            context.runOnClient(client -> {
                oldDistance[0]=client.options.renderDistance().get();hidden[0]=client.gui.hud.isHidden();
                if(!hidden[0])client.gui.hud.toggle();
                client.options.renderDistance().set(12);client.options.broadcastOptions();
            });
            try {
                server.runCommand("gamemode spectator @a");
                for(int view=0;view<4;view++) {
                    BlockPos point=views[view];
                    int offset=view==1?14:view==3?40:60,rise=view==1?10:view==3?130:32;
                    int pitch=view==3?70:25;
                    server.runCommand("execute in minecraft:the_end run tp @a "+(point.getX()+offset)+" "+(point.getY()+rise)
                        +" "+(point.getZ()+offset)+" 135 "+pitch);
                    context.waitTicks(40);
                    context.waitFor(client -> client.level!=null && client.level.dimension()==Level.END
                        && client.level.getChunkSource().getChunk(point.getX()>>4,point.getZ()>>4,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null,600);
                    context.waitTicks(100);
                    context.takeScreenshot(view==0?"lirestone-patches":view==1?"lirestone-boulder":view==2?"lirestone-free-region":"lirestone-entire-island");
                }
            } finally {
                naturality.config.NaturalityConfig.get().fog.enabled=oldFog;
                context.runOnClient(client -> {
                    if(client.gui.hud.isHidden()!=hidden[0])client.gui.hud.toggle();
                    client.options.renderDistance().set(oldDistance[0]);client.options.broadcastOptions();
                });
            }
        }
    }
}
