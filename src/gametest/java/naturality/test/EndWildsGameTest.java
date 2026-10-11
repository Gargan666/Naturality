package naturality.test;

import naturality.NaturalityBlocks;
import naturality.worldgen.EndWilds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ChorusPlantFeature;

public final class EndWildsGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();BlockPos[] view={null};
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runOnServer(s -> {
                var end=s.getLevel(Level.END);
                var generator=(NoiseBasedChunkGenerator)end.getChunkSource().getGenerator();
                var random=end.getChunkSource().randomState();
                var source=generator.getBiomeSource();
                var biome=end.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(EndWilds.BIOME);
                check(source.possibleBiomes().contains(biome) && biome.is(net.minecraft.tags.BiomeTags.IS_END),"Wilds is not an End biome");
                // A Lirestone conversion fringe can overlap the Wilds surface mask.
                // Both regular and smooth deposits must win over Heath in that overlap.
                int collisions=0;
                var heathMask=new naturality.worldgen.LyrenHeathPatches(1);
                var crags=end.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getOrThrow(naturality.worldgen.LirestoneCrags.BIOME);
                var fixture=new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(0,0),
                    net.minecraft.world.level.chunk.UpgradeData.EMPTY,end,
                    net.minecraft.world.level.chunk.PalettedContainerFactory.create(end.registryAccess()),null);
                for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=80;y<=100;y++)
                    fixture.setBlockState(new BlockPos(x,y,z),(x%2==0?Blocks.END_STONE:NaturalityBlocks.SMOOTH_ENDSTONE).defaultBlockState());
                naturality.worldgen.EndStoneLayers.apply(fixture,1,null,(x,y,z)->crags,
                    new net.minecraft.world.level.biome.BiomeManager((x,y,z)->biome,0L));
                for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                    if(heathMask.contains(x,z))collisions++;
                    var cap=fixture.getBlockState(new BlockPos(x,100,z));
                    check(cap.is(x%2==0?NaturalityBlocks.LIRESTONE:NaturalityBlocks.SMOOTH_LIRESTONE),
                        "Heath replaced a regular or smooth Lirestone deposit");
                }
                check(collisions>0,"Protection fixture did not exercise overlapping Heath patches");
                var resolver=source.createCachingResolver(random);
                int wildSamples=0,longRuns=0;
                for(int z=-8192;z<=8192;z+=64) {
                    int run=0;
                    for(int x=-8192;x<=8192;x+=64) {
                        boolean wild=resolver.getNoiseBiome(QuartPos.fromBlock(x),0,QuartPos.fromBlock(z)).is(EndWilds.BIOME);
                        if(!wild){run=0;continue;}
                        wildSamples++;run++;
                        if(run==5)longRuns++;
                        for(int dz=-64;dz<=64;dz+=32)for(int dx=-64;dx<=64;dx+=32)
                            check(!resolver.getNoiseBiome(QuartPos.fromBlock(x+dx),0,QuartPos.fromBlock(z+dz))
                                .is(naturality.worldgen.LirestoneCrags.BIOME),"End Wilds is too close to Lirestone Crags");
                    }
                }
                check(wildSamples>1000 && longRuns>100,"Wilds does not form broad regions");
                System.out.println("End Wilds regional map: "+wildSamples+" cells, "+longRuns+" runs at least 256 blocks wide");
                BlockPos region=null;
                search: for(int x=-8192;x<=8192;x+=64)for(int z=-8192;z<=8192;z+=64) {
                    boolean chosen=resolver.getNoiseBiome(QuartPos.fromBlock(x),0,QuartPos.fromBlock(z)).is(EndWilds.BIOME);
                    if(Math.hypot(x,z)<=1024)check(!chosen,"Wilds replaced the central island");
                    if(!chosen || Math.hypot(x,z)<1536)continue;
                    boolean interior=true;
                    for(int dx=-16;dx<=16;dx+=16)for(int dz=-16;dz<=16;dz+=16)
                        if(!resolver.getNoiseBiome(QuartPos.fromBlock(x+dx),0,QuartPos.fromBlock(z+dz)).is(EndWilds.BIOME))interior=false;
                    if(!interior)continue;
                    var column=generator.getBaseColumn(x,z,end,random);
                    for(int y=224;y>=48;y--)if(!column.getBlock(y).isAir()) {region=new BlockPos(x,y,z);break search;}
                }
                check(region!=null,"No populated End Wilds region found");
                check(source.findBiomeHorizontal(region.getX(),region.getY(),region.getZ(),256,4,
                    holder->holder.is(EndWilds.BIOME),RandomSource.create(1),true,random)!=null,"Wilds cannot be located");
                int cx=region.getX()>>4,cz=region.getZ()>>4;
                for(int z=cz-2;z<=cz+2;z++)for(int x=cx-2;x<=cx+2;x++)end.getChunk(x,z);
                int heath=0,stone=0,sprouts=0,growths=0,bareSurface=0,bareChunks=0,boulderChunks=0,endBoulderTops=0,lireBoulderTops=0;
                for(int z=cz-2;z<=cz+2;z++)for(int x=cx-2;x<=cx+2;x++) {
                    var chunk=end.getChunk(x,z);
                    boolean bare=false,boulder=false;
                    for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++)for(int y=1;y<255;y++) {
                        var p=new BlockPos(x*16+dx,y,z*16+dz);
                        var state=chunk.getBlockState(p);
                        if(state.is(NaturalityBlocks.LYREN_SPROUT) || state.is(NaturalityBlocks.LYREN_GROWTH)) {
                            check(end.getBiome(p).is(EndWilds.BIOME),"Lyren plant escaped End Wilds");
                            check(chunk.getBlockState(p.below()).is(NaturalityBlocks.LYREN_HEATH)
                                && state.canSurvive(end,p),"Invalid Lyren plant ground");
                            if(state.is(NaturalityBlocks.LYREN_SPROUT))sprouts++;else growths++;
                        }
                        if(!end.getBiome(p).is(EndWilds.BIOME))continue;
                        check(!state.is(Blocks.CHORUS_PLANT) && !state.is(Blocks.CHORUS_FLOWER)
                            && !state.is(NaturalityBlocks.CHORUS_SPROUT),"Chorus generated in End Wilds");
                        boolean rock=naturality.worldgen.LirestoneBoulderFeature.isEndStone(state);
                        if(rock && chunk.getBlockState(p.above()).isAir()) {
                            bareSurface++;bare=true;
                            if(generator.getBaseColumn(p.getX(),p.getZ(),end,random).getBlock(y).isAir()) {
                                boulder=true;
                                if(state.is(NaturalityBlocks.LIRESTONE))lireBoulderTops++;else endBoulderTops++;
                            }
                        }
                        if(rock)stone++;
                        if(state.is(NaturalityBlocks.LYREN_HEATH)) {
                            heath++;
                            var above=chunk.getBlockState(p.above());
                            check(above.isAir() || above.is(NaturalityBlocks.LYREN_SPROUT) || above.is(NaturalityBlocks.LYREN_GROWTH),"Heath is buried below the surface");
                            check(!chunk.getBlockState(p.below()).is(NaturalityBlocks.LYREN_HEATH),"Heath layer is too deep");
                        }
                    }
                    if(bare)bareChunks++;if(boulder)boulderChunks++;
                }
                System.out.println("Wilds natural boulder chunks="+boulderChunks+"/"+bareChunks+", End Stone tops="+endBoulderTops+", Lirestone tops="+lireBoulderTops);
                check(endBoulderTops>lireBoulderTops,"Lirestone boulders are not rarer than End Stone");
                check(boulderChunks>bareChunks/2,"Boulders are not more common than absent in bare Wilds chunks");
                check(heath>100 && stone>1000,"Wilds lacks grass caps or stone interiors: "+heath+","+stone);
                check(bareSurface>300 && heath/(double)(heath+bareSurface)>.15
                    && heath/(double)(heath+bareSurface)<.7,"Heath does not form separated surface patches");
                System.out.println("Heath patches: heath="+heath+", bare surface="+bareSurface);
                check(sprouts>30 && growths>30 && sprouts/(double)(sprouts+growths)>.25 && sprouts/(double)(sprouts+growths)<.75,"Missing mixed vegetation: "+sprouts+","+growths);
                System.out.println("End Wilds vegetation: sprouts="+sprouts+", growths="+growths);
                var root=region.atY(230);
                end.setBlock(root.below(),NaturalityBlocks.LYREN_HEATH.defaultBlockState(),2);
                end.setBlock(root,Blocks.AIR.defaultBlockState(),2);
                check(!new ChorusPlantFeature().place(end,generator,RandomSource.create(1),root),"Native chorus feature ignores Wilds exclusion");
                // A flat bare gap exercises complete mixed groups independently of terrain rarity.
                var floor=region.atY(230);
                for(int dz=-24;dz<=24;dz++)for(int dx=-24;dx<=24;dx++)for(int y=228;y<=244;y++)
                    end.setBlock(floor.offset(dx,y-230,dz),(y<=230?Blocks.END_STONE:Blocks.AIR).defaultBlockState(),2);
                var boulders=new naturality.worldgen.WildsBouldersFeature();
                boolean placed=false;
                for(int seed=0;seed<32 && !placed;seed++)placed=boulders.place(end,generator,
                    RandomSource.create(seed*0x9e3779b97f4a7c15L),floor.above());
                check(placed,"No large boulder group could occupy a valid bare gap");
                int endRock=0,lireRock=0,tall=0;
                for(BlockPos p:BlockPos.betweenClosed(floor.offset(-24,1,-24),floor.offset(24,14,24))) {
                    var state=end.getBlockState(p);
                    if(state.is(Blocks.END_STONE))endRock++;
                    if(state.is(NaturalityBlocks.LIRESTONE))lireRock++;
                    if(naturality.worldgen.LirestoneBoulderFeature.isEndStone(state) && p.getY()>=233)tall++;
                }
                check(endRock+lireRock>10 && endRock+lireRock>150 && tall>10,
                    "Boulder group lacks a larger dominant rock");
                var remaining=new java.util.HashSet<BlockPos>();
                for(BlockPos p:BlockPos.betweenClosed(floor.offset(-24,1,-24),floor.offset(24,14,24)))
                    if(naturality.worldgen.LirestoneBoulderFeature.isEndStone(end.getBlockState(p)))remaining.add(p.immutable());
                while(!remaining.isEmpty()) {
                    var queue=new java.util.ArrayDeque<BlockPos>();
                    var first=remaining.iterator().next();remaining.remove(first);queue.add(first);
                    int top=0,topBlocks=0;
                    while(!queue.isEmpty()) {
                        var p=queue.remove();
                        if(p.getY()>top){top=p.getY();topBlocks=0;}
                        if(p.getY()==top)topBlocks++;
                        for(var direction:net.minecraft.core.Direction.values()) {
                            var neighbor=p.relative(direction);
                            if(remaining.remove(neighbor))queue.add(neighbor);
                        }
                    }
                    check(topBlocks>=4,"Boulder has a tiny top nub");
                }
                end.setBlock(floor,NaturalityBlocks.LYREN_HEATH.defaultBlockState(),2);
                check(!boulders.place(end,generator,RandomSource.create(1),floor.above()),"Boulder group overwrites Heath");
                System.out.println("Wilds boulder group: End Stone="+endRock+", Lirestone="+lireRock+", tall="+tall);
                System.out.println("End Wilds: heath="+heath+", stone="+stone+", region="+region);view[0]=region;
            });
            boolean fog=naturality.config.NaturalityConfig.get().fog.enabled;
            naturality.config.NaturalityConfig.get().fog.enabled=false;
            try {
                var p=view[0];server.runCommand("gamemode spectator @a");
                server.runCommand("execute in minecraft:the_end run tp @a "+(p.getX()+60)+" "+(p.getY()+45)+" "+(p.getZ()+60)+" 135 25");
                context.waitTicks(200);context.takeScreenshot("end-wilds-heath");
            } finally {naturality.config.NaturalityConfig.get().fog.enabled=fog;}
        }
    }
}
