package naturality.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import naturality.worldgen.EndTerrainDensity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Repeatable generation/profile workload; wall times are reported rather than used as flaky assertions. */
public final class EndChunkPerformanceGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        EndTerrainSamplingChecks.run();
        Path directory = Path.of("profiling");
        var report = new StringBuilder();
        try (var world = context.worldBuilder().create(); var recording = new Recording()) {
            Files.createDirectories(directory);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5)).withStackTrace();
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(5)).withStackTrace();
            recording.enable("jdk.ObjectAllocationSample").withStackTrace();
            recording.start();
            var server = world.getServer();
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runOnServer(s -> {
                var end = s.getLevel(Level.END);
                var generator = (NoiseBasedChunkGenerator)end.getChunkSource().getGenerator();
                var random = end.getChunkSource().randomState();
                var sampler = random.samplersWithContext(SamplerContext.builder().build()).get(
                    new EndTerrainDensity(generator.generatorSettings().value().noiseRouter().finalDensity()));
                report.append("seed=").append(random.seed()).append('\n');
                // Warm the sampling path and hash the full-resolution density of varied fixed chunks.
                int[][] probes = {{2,0},{65,0},{76,0},{80,0},{246,292},{203,236},{169,263},{202,296},{228,266},{-203,-236}};
                try {
                    var densityHash = MessageDigest.getInstance("SHA-256");
                    var solidHash = MessageDigest.getInstance("SHA-256");
                    for (int[] p : probes) {
                        var volume = new DensityVolume(16,256,16,p[0]*16,0,p[1]*16);
                        try (var buffer = sampler.sampleVolume(volume)) {
                            for (int i=0;i<16*256*16;i++) {
                                float value=buffer.get(i); int bits=Float.floatToIntBits(value);
                                densityHash.update((byte)(bits>>>24)); densityHash.update((byte)(bits>>>16));
                                densityHash.update((byte)(bits>>>8)); densityHash.update((byte)bits);
                                solidHash.update((byte)(value>0 ? 1 : 0));
                            }
                        }
                    }
                    report.append("density_sha256=").append(HexFormat.of().formatHex(densityHash.digest())).append('\n');
                    String solidFingerprint=HexFormat.of().formatHex(solidHash.digest());
                    report.append("solid_sha256=").append(solidFingerprint).append('\n');
                    // Geometry baseline for the connected-ledge redesign. Covers 655,360 block
                    // decisions across central, transition, outer and negative-coordinate chunks.
                    if(random.seed()==1 && !solidFingerprint.equals("1d82078f5e6df8307e5e34388eeec385170047f46e12e1e615a139e7a55aacdc"))
                        throw new AssertionError("Terrain changed the connected-ledge geometry baseline");
                } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
                for (int pass=0;pass<4;pass++) {
                    long start=System.nanoTime();
                    for(int dz=0;dz<8;dz++)for(int dx=0;dx<8;dx++) {
                        var volume=new DensityVolume(16,256,16,(200+dx)*16,0,(232+dz)*16);
                        try(var buffer=sampler.sampleVolume(volume)) {buffer.get(0);}
                    }
                    report.append("density_64_chunks_ms[").append(pass).append("]=").append((System.nanoTime()-start)/1e6).append('\n');
                }
                for (int pass=0;pass<3;pass++) {
                    long start=System.nanoTime();
                    for(int dz=0;dz<4;dz++)for(int dx=0;dx<4;dx++) {
                        var volume=new DensityVolume(16,256,16,(68+dx)*16,0,dz*16);
                        try(var buffer=sampler.sampleVolume(volume)) {buffer.get(0);}
                    }
                    report.append("gateway_density_16_chunks_ms[").append(pass).append("]=").append((System.nanoTime()-start)/1e6).append('\n');
                }
                int[][] regions={{-4,-4},{64,-4},{242,288},{199,232},{225,263}};
                for(int region=0;region<regions.length;region++) {
                    long start=System.nanoTime();
                    int[] p=regions[region];
                    for(int dz=0;dz<8;dz++)for(int dx=0;dx<8;dx++)end.getChunk(p[0]+dx,p[1]+dz);
                    report.append("full_64_chunks_ms[").append(region).append("]=").append((System.nanoTime()-start)/1e6).append('\n');
                }
            });
            server.runCommand("gamemode spectator @a");
            server.runCommand("execute in minecraft:the_end run tp @a 4000 180 4700");
            context.waitTicks(160);
            recording.stop();
            Path destination=directory.resolve("end-chunks.jfr");
            recording.dump(destination);
            var inclusive = new LinkedHashMap<String,Integer>();
            var leaves = new LinkedHashMap<String,Integer>();
            var allocations = new LinkedHashMap<String,Long>();
            try(var events=new RecordingFile(destination)) {
                while(events.hasMoreEvents()) {
                    var event=events.readEvent();
                    var stack=event.getStackTrace();
                    if(stack==null)continue;
                    if(event.getEventType().getName().equals("jdk.ObjectAllocationSample")) {
                        allocations.merge(event.getClass("objectClass").getName(),event.getLong("weight"),Long::sum);
                        continue;
                    }
                    if(!event.getEventType().getName().endsWith("Sample"))continue;
                    var unique=new java.util.HashSet<String>();
                    boolean first=true;
                    for(var frame:stack.getFrames()) {
                        var method=frame.getMethod();
                        String name=method.getType().getName()+"."+method.getName();
                        if(first){leaves.merge(name,1,Integer::sum);first=false;}
                        if(unique.add(name))inclusive.merge(name,1,Integer::sum);
                    }
                }
            }
            report.append("\nInclusive CPU samples:\n");
            inclusive.entrySet().stream().sorted(java.util.Map.Entry.<String,Integer>comparingByValue().reversed()).limit(70)
                .forEach(e->report.append(e.getValue()).append(' ').append(e.getKey()).append('\n'));
            report.append("\nLeaf CPU samples:\n");
            leaves.entrySet().stream().sorted(java.util.Map.Entry.<String,Integer>comparingByValue().reversed()).limit(40)
                .forEach(e->report.append(e.getValue()).append(' ').append(e.getKey()).append('\n'));
            report.append("\nSampled allocation bytes:\n");
            allocations.entrySet().stream().sorted(java.util.Map.Entry.<String,Long>comparingByValue().reversed()).limit(25)
                .forEach(e->report.append(e.getValue()).append(' ').append(e.getKey()).append('\n'));
            Files.writeString(directory.resolve("end-chunks.txt"),report);
            System.out.println(report);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
