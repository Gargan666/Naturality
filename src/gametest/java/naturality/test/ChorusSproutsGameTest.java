package naturality.test;

import naturality.NaturalityBlocks;
import naturality.chorus.ChorusSprouts;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ChorusPlantFeature;

public final class ChorusSproutsGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}

    @Override public void runTest(ClientGameTestContext context) {
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();
            BlockPos[] view={null};
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runOnServer(s -> {
                var end=TestFixtures.require(s.getLevel(Level.END), "end");
                var generator=end.getChunkSource().getGenerator();
                var feature=new ChorusPlantFeature();
                int total=0,largest=0,smallest=5,stepped=0;
                for(int seed=0;seed<32;seed++) {
                    BlockPos root=new BlockPos(2000+seed%8*12,230,2000+seed/8*12);
                    var substrate=seed%2==0?Blocks.END_STONE:NaturalityBlocks.LIRESTONE;
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
                        int height=seed%3==0?Integer.signum(x):0;
                        for(int y=-4;y<=3;y++)end.setBlock(root.offset(x,y,z),
                            (y<height?substrate:Blocks.AIR).defaultBlockState(),2);
                    }
                    check(feature.place(end,generator,RandomSource.create(seed),root),"Native chorus worldgen failed");
                    int count=0;
                    for(BlockPos pos:BlockPos.betweenClosed(root.offset(-2,-2,-2),root.offset(2,2,2))) {
                        var state=end.getBlockState(pos);
                        if(!state.is(NaturalityBlocks.CHORUS_SPROUT))continue;
                        count++;check(state.canSurvive(end,pos),"Sprout has no valid ground");
                        check(!pos.equals(root),"Sprout replaced the plant base");
                        if(pos.getY()!=root.getY())stepped++;
                    }
                    check(count>=1 && count<=4,"Plant received an invalid sprout count: "+count);
                    total+=count;largest=Math.max(largest,count);smallest=Math.min(smallest,count);
                    if(view[0]==null && count==4)view[0]=root;
                    ChorusSprouts.scatter(end,root,RandomSource.create(seed+100));
                    int repeated=0;
                    for(BlockPos pos:BlockPos.betweenClosed(root.offset(-2,-2,-2),root.offset(2,2,2)))
                        if(end.getBlockState(pos).is(NaturalityBlocks.CHORUS_SPROUT))repeated++;
                    check(repeated<=4,"Repeat scatter exceeded four nearby sprouts");
                }
                check(total>40 && largest==4 && smallest<4 && stepped>0,"Groups do not vary or follow nearby terrain");

                for(int kind=0;kind<3;kind++) {
                    BlockPos root=new BlockPos(2120+kind*12,230,2000);
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-3;y<=3;y++) {
                        var block=y<0?(kind==0?NaturalityBlocks.SMOOTH_ENDSTONE:Blocks.END_STONE):Blocks.AIR;
                        if(kind==1 && (x!=0 || z!=0))block=Blocks.AIR;
                        if(kind==2 && (x!=0 || z!=0) && y>=0)block=Blocks.GOLD_BLOCK;
                        end.setBlock(root.offset(x,y,z),block.defaultBlockState(),2);
                    }
                    check(feature.place(end,generator,RandomSource.create(1),root),"Chorus fixture failed");
                    for(BlockPos pos:BlockPos.betweenClosed(root.offset(-2,-2,-2),root.offset(2,2,2)))
                        check(!end.getBlockState(pos).is(NaturalityBlocks.CHORUS_SPROUT),"Sprout overwrote an obstacle or appeared on unsupported ground");
                    if(kind==2)check(end.getBlockState(root.offset(2,0,0)).is(Blocks.GOLD_BLOCK),"Scatter damaged an occupied block");
                }

                int natural=0,roots=0;
                for(int cz=310;cz<322;cz++)for(int cx=230;cx<242;cx++)end.getChunk(cx,cz);
                for(int cz=311;cz<321;cz++)for(int cx=231;cx<241;cx++) {
                    var chunk=end.getChunk(cx,cz);
                    var probe=new BlockPos.MutableBlockPos();
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++) {
                        probe.set(cx*16+x,y,cz*16+z);var state=chunk.getBlockState(probe);
                        if(state.is(Blocks.CHORUS_PLANT) && end.getBlockState(probe.below()).is(net.minecraft.tags.BlockTags.SUPPORTS_CHORUS_PLANT))roots++;
                        if(!state.is(NaturalityBlocks.CHORUS_SPROUT))continue;
                        natural++;check(state.canSurvive(end,probe),"Natural sprout cannot survive");
                        boolean nearby=false;
                        for(int dx=-2;dx<=2 && !nearby;dx++)for(int dz=-2;dz<=2 && !nearby;dz++)for(int dy=-2;dy<=2;dy++) {
                            BlockPos base=probe.offset(dx,dy,dz);
                            if(end.getBlockState(base).is(Blocks.CHORUS_PLANT)
                                && end.getBlockState(base.below()).is(net.minecraft.tags.BlockTags.SUPPORTS_CHORUS_PLANT)) {nearby=true;break;}
                        }
                        check(nearby,"Natural sprout is too far from a plant base");
                    }
                }
                System.out.println("Chorus sprouts: native fixtures="+total+", stepped="+stepped+", natural sprouts="+natural+", plant bases="+roots);
                check(natural>0 && roots>0,"Initial End chunks lack plant-base sprouts");
            });

            int[] distance={0};boolean[] hidden={false};
            boolean fog=naturality.config.NaturalityConfig.get().fog.enabled;
            naturality.config.NaturalityConfig.get().fog.enabled=false;
            context.runOnClient(client -> {
                distance[0]=client.options.renderDistance().get();hidden[0]=client.gui.hud.isHidden();
                if(!hidden[0])client.gui.hud.toggle();client.options.renderDistance().set(10);client.options.broadcastOptions();
            });
            try {
                BlockPos point=view[0];
                server.runCommand("gamemode spectator @a");
                server.runCommand("execute in minecraft:the_end run tp @a "+(point.getX()+7)+" "+(point.getY()+4)+" "+(point.getZ()+7)+" 135 20");
                context.waitFor(client -> client.level!=null && client.level.dimension()==Level.END
                    && TestFixtures.level(client).getChunkSource().getChunk(point.getX()>>4,point.getZ()>>4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null,600);
                context.waitTicks(100);context.takeScreenshot("chorus-plant-base-sprouts");
            } finally {
                naturality.config.NaturalityConfig.get().fog.enabled=fog;
                context.runOnClient(client -> {
                    if(client.gui.hud.isHidden()!=hidden[0])client.gui.hud.toggle();
                    client.options.renderDistance().set(distance[0]);client.options.broadcastOptions();
                });
            }
        }
    }
}
