package naturality.test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import naturality.worldgen.EndTerrainDensity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.*;

public final class EndTerrainGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }

    /** Measure actual solid footprints, rather than assuming the island placement grid limits their size. */
    private static String checkIslandGroups(int[] top, int size, int step) {
        boolean[] seen = new boolean[top.length];
        var groups = new java.util.ArrayList<double[]>();
        int largest = 0, broad = 0, small = 0, connected = 0, irregular = 0;
        for (int start = 0; start < top.length; start++) {
            if (seen[start] || top[start] < 0) continue;
            var queue = new java.util.ArrayDeque<Integer>();
            queue.add(start); seen[start] = true;
            int count = 0, minX = size, maxX = 0, minZ = size, maxZ = 0;
            double sumX = 0, sumZ = 0, sumHeight = 0;
            while (!queue.isEmpty()) {
                int at = queue.removeFirst(), x = at % size, z = at / size;
                count++; sumX += x; sumZ += z; sumHeight += top[at];
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
                    int next = nx + nz * size;
                    if (!seen[next] && top[next] >= 0) { seen[next] = true; queue.add(next); }
                }
            }
            largest = Math.max(largest, count);
            check((maxX - minX) * step < 2048 && (maxZ - minZ) * step < 2048,
                "Connected End land mass grew into a continent");
            if (count > 55) {
                broad++;
                if (count / (double)((maxX-minX+1)*(maxZ-minZ+1)) < .6) irregular++;
            }
            if (Math.max(maxX-minX,maxZ-minZ)*step >= 320) connected++;
            if (count >= 3 && count < 25) small++;
            if (count >= 8 && minX > 0 && minZ > 0 && maxX < size - 1 && maxZ < size - 1)
                groups.add(new double[]{sumX / count * step, sumZ / count * step, sumHeight / count});
        }
        check(largest > 350 && broad > 20 && small > 20 && connected > 20 && irregular > broad * .65,
            "Missing interconnected, irregular shelves and separate rocks: " + largest + "/" + broad + "/" + small + "/" + connected + "/" + irregular);
        double neighboring = 0, distant = 0;
        int pairs = 0;
        for (double[] a : groups) {
            double nearest = Double.POSITIVE_INFINITY, nearestHeight = 0;
            double farHeight = 0; int farCount = 0;
            for (double[] b : groups) {
                if (a == b) continue;
                double distance = Math.hypot(a[0] - b[0], a[1] - b[1]);
                if (distance < nearest) { nearest = distance; nearestHeight = Math.abs(a[2] - b[2]); }
                if (distance > 1800) { farHeight += Math.abs(a[2] - b[2]); farCount++; }
            }
            if (farCount > 0) { neighboring += nearestHeight; distant += farHeight / farCount; pairs++; }
        }
        check(pairs > 50 && neighboring < distant * .85,
            "Nearby island elevations do not share a broad height pattern");
        return "largest footprint=" + largest * step * step + " blocks, broad=" + broad + ", small=" + small
            + ", connected=" + connected + ", irregular=" + irregular
            + ", average height difference nearby=" + neighboring / pairs + ", distant=" + distant / pairs;
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            double[][][] viewpoints = new double[1][][];
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var generator = (NoiseBasedChunkGenerator)end.getChunkSource().getGenerator();
                var random = end.getChunkSource().randomState();
                var input = generator.generatorSettings().value().noiseRouter().finalDensity();
                var custom = new EndTerrainDensity(input);
                var sampling = random.samplersWithContext(SamplerContext.builder().build());
                var changed = sampling.get(custom);
                var vanilla = sampling.get(input);
                for (int x=-768;x<=768;x+=256) for (int z=-768;z<=768;z+=256) {
                    if (Math.hypot(x,z)>1024) continue;
                    for (int y=0;y<256;y+=4)
                        check(Float.floatToIntBits(changed.sampleValue(x,y,z)) == Float.floatToIntBits(vanilla.sampleValue(x,y,z)),
                            "Protected central terrain changed");
                }
                check(EndTerrainDensity.outerBlend(1024,0)==0 && EndTerrainDensity.outerBlend(1280,0)==1,
                    "Central protection bounds changed");
                                var terrain = (EndTerrainDensity.Sampler)random.getSampler(custom);
                var sameSeed = net.minecraft.world.level.levelgen.RandomState.create(
                    end.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE),
                    random.seed(),generator.generatorSettings().value());
                var otherSeed = net.minecraft.world.level.levelgen.RandomState.create(
                    end.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE),
                    random.seed()+1,generator.generatorSettings().value());
                var repeat = (EndTerrainDensity.Sampler)sameSeed.getSampler(custom);
                var different = (EndTerrainDensity.Sampler)otherSeed.getSampler(custom);
                check(java.util.Arrays.equals(terrain.weights(2700,3500),repeat.weights(2700,3500)),"Style map is not seed reproducible");
                check(!java.util.Arrays.equals(terrain.weights(2700,3500),different.weights(2700,3500)),"World seeds share the same style map");
                for(int x=1536;x<7000;x+=31) {
                    double[] first=terrain.weights(x,3000), next=terrain.weights(x+1,3000);
                    for(int style=0;style<3;style++) check(Math.abs(first[style]-next[style])<0.08,"Terrain style boundary is abrupt");
                    check(Math.abs(terrain.heightShift(x,3000)-terrain.heightShift(x+1,3000))<0.3,"Height field jumps across blocks");
                }
                int size=256, step=16, minimum=1536;
                int[] top = new int[size*size];

                double[][] candidates = new double[5][];
                var image = new BufferedImage(size*3,size,BufferedImage.TYPE_INT_RGB);
                int[] coverage = new int[3];
                int low=256, high=0, occupied=0, lowestBlock=256, thick=0, multipleShelves=0;
                int[] regionArea=new int[2], regionLand=new int[2];
                int[] routeArea=new int[2], routeLand=new int[2];
                double[] bestScore={-1e9,-1e9,-1e9,-1e9,-1e9};
                var volume = new DensityVolume(size,64,size,minimum,0,minimum,step,4,step);
                try (var buffer = changed.sampleVolume(volume)) {
                    for (int z=0;z<size;z++) for (int x=0;x<size;x++) {
                        int wx=minimum+x*step, wz=minimum+z*step;
                        double[] weights=terrain.weights(wx,wz);
                        double[] subtypes=terrain.subtypes(wx,wz);
                        int region=subtypes[1]>.8 ? 1 : subtypes[1]<.2 ? 0 : -1;
                        if(region>=0)regionArea[region]++;
                        int routes=subtypes[1]<.2 ? (weights[1]>.85 && subtypes[0]>.85 ? 1 : weights[0]+weights[2]>.9 ? 0 : -1) : -1;
                        if(routes>=0)routeArea[routes]++;
                        int type=weights[0]>weights[1] ? 0 : 1;
                        if (weights[2]>weights[type]) type=2;
                        coverage[type]++;

                        int height=-1;
                        for (int y=63;y>=0;y--) if (buffer.get(volume.indexUnchecked(x,y,z))>0) {height=y*4;break;}
                        top[x+z*size]=height;
                        if (height>=0) {
                            occupied++; low=Math.min(low,height); high=Math.max(high,height);
                            if(region>=0)regionLand[region]++;
                            if(routes>=0)routeLand[routes]++;
                            int bottom=256, solids=0, runs=0; boolean previous=false;
                            for(int y=0;y<64;y++) {
                                boolean solid=buffer.get(volume.indexUnchecked(x,y,z))>0;
                                if(solid){bottom=Math.min(bottom,y*4);solids++;if(!previous)runs++;}
                                previous=solid;
                            }
                            lowestBlock=Math.min(lowestBlock,bottom);
                            if(solids*4>=40)thick++;
                            if(runs>1)multipleShelves++;
                            double score=type==1 ? height-64-terrain.heightShift(wx,wz) : height;
                            if(weights[type]>.85 && subtypes[1]<.4 && score>bestScore[type]){bestScore[type]=score;candidates[type]=new double[]{wx,wz,height};}
                            if(weights[1]>.85 && subtypes[0]>.8 && subtypes[1]<.4 && score<20 && height>bestScore[3]){bestScore[3]=height;candidates[3]=new double[]{wx,wz,height};}
                            if(subtypes[1]>.9 && height>bestScore[4]){bestScore[4]=height;candidates[4]=new double[]{wx,wz,height};}
                        }
                        int brightness=height<0 ? 0 : Math.min(255,35+height);
                        image.setRGB(x,z,height<0 ? 0x10091e : (brightness<<16)|(brightness<<8)|(brightness/2));
                        image.setRGB(x+size,z,((int)(weights[0]*255)<<16)|((int)(weights[1]*255)<<8)|(int)(weights[2]*255));
                        image.setRGB(x+size*2,z,((int)(subtypes[0]*255)<<16)|((int)(subtypes[1]*255)<<8)|35);
                        // Sample-volume and point paths must agree at sparse points.
                        if (x%16==0 && z%16==0) for (int y=0;y<64;y+=8)
                            check(Math.abs(changed.sampleValue(wx,y*4,wz)-buffer.get(volume.indexUnchecked(x,y,z)))<1e-6,
                                "Terrain changes with sampling volume/chunk boundaries");
                    }
                }
                try {
                    Files.createDirectories(Path.of("screenshots"));
                    ImageIO.write(image,"png",Path.of("screenshots/end-terrain-height-and-style.png").toFile());
                } catch (java.io.IOException e) {throw new AssertionError(e);}
                System.out.println("Terrain survey: land="+occupied+" thick="+thick+" overlap="+multipleShelves+" height="+low+".."+high+" floor="+lowestBlock);
                for (int type=0;type<3;type++) check(coverage[type]>500 && candidates[type]!=null,"Terrain style missing: "+type);
                check(occupied>1000 && occupied<size*size*0.5,"Terrain lost islands/void distribution: "+occupied);
                check(lowestBlock>=24,"Islands approach the world floor: "+lowestBlock);
                check(candidates[3]!=null && candidates[4]!=null,"Branching or sparse subtype missing");
                check(regionLand[1]/(double)regionArea[1]<regionLand[0]/(double)regionArea[0]*.8,"Sparse archipelago is not substantially more separated");
                check(thick>occupied*.5,"Island bodies remain too thin: "+thick+"/"+occupied);
                check(multipleShelves>30,"No overlapping ledges with air between them: "+multipleShelves);
                check(high-low>70,"Outer End remains at a uniform height: "+low+".."+high);
                check(routeArea[1]>250 && routeLand[1]/(double)routeArea[1]<routeLand[0]/(double)routeArea[0]*.85,"Branching paths retain too much solid infill: "+java.util.Arrays.toString(routeLand)+"/"+java.util.Arrays.toString(routeArea));
                String groups = checkIslandGroups(top, size, step);
                // Sample a second seed in the negative-coordinate quadrant, including entire undersides.
                var alternate = otherSeed.samplersWithContext(SamplerContext.builder().build()).get(custom);
                var negative = new DensityVolume(96,64,96,-6144,0,-6144,32,4,32);
                int alternateLand = 0;
                try (var buffer = alternate.sampleVolume(negative)) {
                    for (int z=0;z<96;z++) for (int x=0;x<96;x++) {
                        boolean land = false;
                        for (int y=0;y<64;y++) if (buffer.get(negative.indexUnchecked(x,y,z))>0) {
                            check(y*4>=24 && y*4<=240,"Alternate-seed island touches a world boundary");
                            land = true;
                        }
                        if (land) alternateLand++;
                    }
                }
                check(alternateLand>700 && alternateLand<96*96*.5,"Alternate seed loses archipelago distribution");
                try {
                    Path destination=Path.of("screenshots/end-terrain-height-and-style.png");
                    Files.createDirectories(destination.getParent());
                    ImageIO.write(image,"png",destination.toFile());
                    var points=new StringBuilder();
                    for(int i=0;i<5;i++) points.append(i).append(':').append(java.util.Arrays.toString(candidates[i])).append('\n');
                    Files.writeString(Path.of("screenshots/end-terrain-candidates.txt"),points+"height range="+low+".."+high+" lowest block="+lowestBlock+" occupied="+occupied+" thick="+thick+" overlapping="+multipleShelves+" regional area="+java.util.Arrays.toString(regionArea)+" land="+java.util.Arrays.toString(regionLand)+" routes area="+java.util.Arrays.toString(routeArea)+" land="+java.util.Arrays.toString(routeLand)+"\n"+groups);
                } catch (java.io.IOException e) {throw new AssertionError(e);}
                // The same density drives actual generation and structure height queries.
                viewpoints[0]=java.util.Arrays.stream(candidates).map(double[]::clone).toArray(double[][]::new);
                // Center the route overview on its island, rather than on one high point along a branch.
                try {
                    var getColumn=EndTerrainDensity.Sampler.class.getDeclaredMethod("column",int.class,int.class);
                    getColumn.setAccessible(true);
                    Object column=getColumn.invoke(terrain,(int)candidates[3][0],(int)candidates[3][1]);
                    var getIsland=column.getClass().getDeclaredMethod("island");getIsland.setAccessible(true);
                    Object island=getIsland.invoke(column);
                    var getX=island.getClass().getDeclaredMethod("x");getX.setAccessible(true);
                    var getZ=island.getClass().getDeclaredMethod("z");getZ.setAccessible(true);
                    viewpoints[0][3][0]=(double)getX.invoke(island);
                    viewpoints[0][3][1]=(double)getZ.invoke(island);
                } catch (ReflectiveOperationException e) {throw new AssertionError(e);}
                for (int style=0;style<3;style++)
                    EndTerrainRouteChecks.check(terrain,(int)candidates[style][0],(int)candidates[style][1]);
                for (double[] candidate:candidates) {
                    int x=(int)candidate[0], z=(int)candidate[1];
                    var chunk=end.getChunk(x>>4,z>>4);
                    var column=generator.getBaseColumn(x,z,end,random);
                    for (int y=8;y<244;y++) {
                        var actual=chunk.getBlockState(new BlockPos(x,y,z));
                        var predicted=column.getBlock(y);
                        if(!actual.isAir() && !actual.is(net.minecraft.world.level.block.Blocks.END_STONE)
                            && !actual.is(naturality.NaturalityBlocks.SMOOTH_ENDSTONE)) continue;
                        check(actual.isAir()==predicted.isAir(),"Generated blocks and base column disagree at "+x+","+y+","+z);
                    }
                }
            });
            int[] oldDistance=new int[1];
            boolean[] oldGui=new boolean[1];
            boolean oldFog=naturality.config.NaturalityConfig.get().fog.enabled;
            naturality.config.NaturalityConfig.get().fog.enabled=false;
            context.runOnClient(client -> {
                oldDistance[0]=client.options.renderDistance().get(); oldGui[0]=client.gui.hud.isHidden();
                if(!oldGui[0])client.gui.hud.toggle(); client.options.renderDistance().set(24); client.options.broadcastOptions();
            });
            try {
                server.runCommand("gamemode spectator @a");
                for(int style=0;style<5;style++) {
                    double[] point=viewpoints[0][style];
                    double offset=style==3 ? 40 : 160, rise=style==3 ? 180 : 95;
                    int pitch=style==3 ? 70 : 25;
                    server.runCommand("execute in minecraft:the_end run tp @a "+(point[0]+offset)+" "+(point[2]+rise)+" "+(point[1]+offset)+" 135 "+pitch);
                    context.waitTicks(40);
                    final int targetX=(int)point[0]>>4, targetZ=(int)point[1]>>4;
                    int[] probes={0};
                    context.waitFor(client -> {
                        if(probes[0]++%100==0 && client.level!=null) System.out.println("Terrain camera: "+client.level.dimension()+" at "+client.player.position()+" target "+targetX+","+targetZ+" cache "+(client.level.getChunkSource().getChunk(targetX,targetZ,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null));
                        if(client.level==null || client.level.dimension()!=Level.END) return false;
                        for(int dz=-5;dz<=5;dz++) for(int dx=-5;dx<=5;dx++)
                            if(client.level.getChunkSource().getChunk(targetX+dx,targetZ+dz,
                                net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)==null) return false;
                        return true;
                    }, 1200);
                    context.waitTicks(100);
                    context.waitTicks(10);
                    context.takeScreenshot("end-terrain-style-"+style);
                }
                double[] point=viewpoints[0][0];
                server.runCommand("execute in minecraft:the_end run tp @a "+(point[0]+200)+" "+(point[2]-70)+" "+(point[1]+200)+" 135 -5");
                context.waitTicks(180);
                context.takeScreenshot("end-terrain-undersides");
            } finally {
                naturality.config.NaturalityConfig.get().fog.enabled=oldFog;
                context.runOnClient(client -> {
                    if(client.gui.hud.isHidden()!=oldGui[0])client.gui.hud.toggle(); client.options.renderDistance().set(oldDistance[0]);client.options.broadcastOptions();
                });
            }
        }
    }
}
