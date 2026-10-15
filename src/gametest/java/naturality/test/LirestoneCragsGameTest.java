package naturality.test;

import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import naturality.worldgen.end.terrain.EndTerrainDensity;
import naturality.worldgen.end.lirestonecrags.LirestoneCrags;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.ChorusPlantFeature;

public final class LirestoneCragsGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static boolean crags(BiomeResolver resolver,int x,int z) {
        return resolver.getNoiseBiome(QuartPos.fromBlock(x),0,QuartPos.fromBlock(z)).is(LirestoneCrags.BIOME);
    }

    @Override public void runTest(ClientGameTestContext context) {
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();BlockPos[] view={null};
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runOnServer(s -> {
                var end=TestFixtures.require(s.getLevel(Level.END), "end");
                var generator=(NoiseBasedChunkGenerator)end.getChunkSource().getGenerator();
                var random=end.getChunkSource().randomState();
                var source=generator.getBiomeSource();
                var biome=end.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(LirestoneCrags.BIOME);
                check(source.possibleBiomes().contains(biome),"End biome source does not contain Lirestone Crags");
                check(biome.is(net.minecraft.tags.BiomeTags.IS_END),"Crags lacks the End biome tag");
                var resolver=source.createCachingResolver(random);
                var alternate=source.createCachingResolver(RandomState.create(
                    end.registryAccess().lookupOrThrow(Registries.NOISE),end.getSeed()+1,generator.generatorSettings().value()));
                var repeated=source.createCachingResolver(RandomState.create(
                    end.registryAccess().lookupOrThrow(Registries.NOISE),end.getSeed(),generator.generatorSettings().value()));
                var density=random.samplersWithContext(SamplerContext.builder().build()).get(
                    new EndTerrainDensity(generator.generatorSettings().value().noiseRouter().finalDensity()));
                int coverage=0,different=0,samples=0;
                BlockPos interior=null;
                for(int x=-8192;x<=8192;x+=128)for(int z=-8192;z<=8192;z+=128) {
                    boolean chosen=crags(resolver,x,z);
                    check(chosen==crags(repeated,x,z),"Crags biome is not seed reproducible");
                    if(chosen)coverage++;
                    if(chosen!=crags(alternate,x,z))different++;
                    samples++;
                    if(Math.hypot(x,z)<=1024)check(!chosen,"Crags replaced the central island biome");
                    if(!chosen || interior!=null || Math.hypot(x,z)<1536)continue;
                    boolean broad=true;
                    for(int dx=-16;dx<=16 && broad;dx+=16)for(int dz=-16;dz<=16;dz+=16)
                        if(!crags(resolver,x+dx,z+dz)){broad=false;break;}
                    if(!broad)continue;
                    for(int y=224;y>=32;y-=4)if(density.sampleValue(x,y,z)>0){interior=new BlockPos(x,y,z);break;}
                }
                System.out.println("Lirestone Crags map: "+coverage+"/"+samples+", changed seed="+different);
                check(coverage>samples*.03 && coverage<samples*.7 && different>samples*.03 && interior!=null,
                    "Crags is missing, universal, seed-independent or too small");
                interior=TestFixtures.require(interior, "interior");
                var located=source.findBiomeHorizontal(interior.getX(),interior.getY(),interior.getZ(),512,4,
                    holder->holder.is(LirestoneCrags.BIOME),RandomSource.create(1),true,random);
                check(located!=null,"Biome locator cannot find Lirestone Crags");

                long materialSeed=random.getOrCreateRandomFactory(naturality.Naturality.id("lirestone_patches"))
                    .at(0,0,0).nextLong();
                int fringe=0,changedEdge=0;
                for(int cz=(interior.getZ()>>4)-4;cz<=(interior.getZ()>>4)+4;cz++)
                    for(int cx=(interior.getX()>>4)-4;cx<=(interior.getX()>>4)+4;cx++) {
                        var core=LirestoneCrags.columns(end.getBiomeManager(),cx*16,cz*16);
                        var edge=LirestoneCrags.conversionColumns(end.getBiomeManager(),cx*16,cz*16,materialSeed);
                        var repeat=LirestoneCrags.conversionColumns(end.getBiomeManager(),cx*16,cz*16,materialSeed);
                        var alternateEdge=LirestoneCrags.conversionColumns(end.getBiomeManager(),cx*16,cz*16,materialSeed+1);
                        for(int i=0;i<256;i++) {
                            check(!core[i] || edge[i],"Fringe removed the fully converted biome interior");
                            check(edge[i]==repeat[i],"Fringe depends on generation order");
                            if(edge[i] && !core[i])fringe++;
                            if(edge[i]!=alternateEdge[i])changedEdge++;
                        }
                    }
                check(fringe>100 && changedEdge>50,"Conversion edge lacks a seed-dependent natural fringe");
                System.out.println("Crags border fringe="+fringe+", changed seed="+changedEdge);

                int minX=(interior.getX()>>4)-2,minZ=(interior.getZ()>>4)-2;
                for(int cz=minZ;cz<minZ+5;cz++)for(int cx=minX;cx<minX+5;cx++)end.getChunk(cx,cz);
                int lirestone=0,smooth=0,columns=0;
                var pos=new BlockPos.MutableBlockPos();
                for(int cz=minZ;cz<minZ+5;cz++)for(int cx=minX;cx<minX+5;cx++) {
                    var chunk=end.getChunk(cx,cz);
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                        boolean land=false;
                        for(int y=0;y<256;y++) {
                            pos.set(cx*16+x,y,cz*16+z);
                            if(!end.getBiome(pos).is(LirestoneCrags.BIOME))continue;
                            var state=chunk.getBlockState(pos);
                            check(!state.is(Blocks.END_STONE) && !state.is(NaturalityBlocks.SMOOTH_ENDSTONE),
                                "Crags contains unconverted End Stone at "+pos);
                            check(!state.is(Blocks.CHORUS_PLANT) && !state.is(Blocks.CHORUS_FLOWER)
                                && !state.is(NaturalityBlocks.CHORUS_SPROUT),"Chorus vegetation generated in Crags at "+pos);
                            if(state.is(NaturalityBlocks.LIRESTONE)){lirestone++;land=true;}
                            if(state.is(NaturalityBlocks.SMOOTH_LIRESTONE)){smooth++;land=true;}
                        }
                        if(land)columns++;
                    }
                }
                check(lirestone>100 && smooth>5000 && columns>100,"Crags lacks substantial Lirestone terrain");
                // Native feature placement must also refuse an otherwise valid Lirestone foundation.
                BlockPos root=interior.atY(230);
                end.setBlock(root.below(),NaturalityBlocks.LIRESTONE.defaultBlockState(),2);
                end.setBlock(root,Blocks.AIR.defaultBlockState(),2);
                check(!new ChorusPlantFeature().place(end,generator,RandomSource.create(1),root),
                    "Native chorus feature ignores the Crags exclusion");
                System.out.println("Lirestone Crags terrain: "+lirestone+" cap blocks, "+smooth+" smooth blocks, "+columns+" land columns at "+interior);
                view[0]=interior;
            });
            int[] distance={0};boolean[] hidden={false};
            boolean fog=naturality.config.live.NaturalityConfig.get().fog.enabled;
            naturality.config.live.NaturalityConfig.get().fog.enabled=false;
            context.runOnClient(client -> {
                distance[0]=client.options.renderDistance().get();hidden[0]=client.gui.hud.isHidden();
                if(!hidden[0])client.gui.hud.toggle();client.options.renderDistance().set(16);client.options.broadcastOptions();
            });
            try {
                var point=view[0];server.runCommand("gamemode spectator @a");
                server.runCommand("execute in minecraft:the_end run tp @a "+(point.getX()+100)+" "+(point.getY()+55)
                    +" "+(point.getZ()+100)+" 135 25");
                context.waitTicks(40);
                context.waitFor(client -> client.level!=null && client.level.dimension()==Level.END
                    && TestFixtures.level(client).getChunkSource().getChunk(point.getX()>>4,point.getZ()>>4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null,600);
                context.waitTicks(140);context.takeScreenshot("lirestone-crags-biome");
                context.runOnClient(_ -> check(net.minecraft.client.resources.language.I18n.get("biome.naturality.lirestone_crags")
                    .equals("Lirestone Crags"),"Biome display name is missing"));
            } finally {
                naturality.config.live.NaturalityConfig.get().fog.enabled=fog;
                context.runOnClient(client -> {
                    if(client.gui.hud.isHidden()!=hidden[0])client.gui.hud.toggle();
                    client.options.renderDistance().set(distance[0]);client.options.broadcastOptions();
                });
            }
        }
    }
}
