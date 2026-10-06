package naturality.test;

import java.lang.management.ManagementFactory;
import naturality.NaturalityParticles;
import naturality.client.particle.WeatherClusterParticle;
import naturality.client.weather.WeatherParticleContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;

/** Repeatable CPU/allocation probe, not a GPU or whole-game FPS benchmark. */
final class ParticleWeatherPerformance {
    private static volatile float sink;
    private static final class Output extends QuadParticleRenderState {
        float value;
        @Override public void add(SingleQuadParticle.Layer layer,float x,float y,float z,
                float rx,float ry,float rz,float rw,float size,float u0,float u1,float v0,float v1,int color,int light) {
            value+=rx+ry+rz+rw+x+y+z+size+u0+u1+v0+v1+color+light;
        }
    }
    static void measure(Minecraft client,boolean snow) {
        var cards=new WeatherClusterParticle[128];
        var level=java.util.Objects.requireNonNull(client.level);
        for(int i=0;i<cards.length;i++) {
            cards[i]=(WeatherClusterParticle)client.particleEngine.createParticle(
                snow?NaturalityParticles.SNOW_CLUSTER:NaturalityParticles.RAIN_CLUSTER,
                (i%8)-4,118,(i/8)-8,0,0,0);
            if(cards[i]==null)throw new AssertionError("Benchmark needs weather cards");
            cards[i].setLifetime(100_000);
        }
        var output=new Output();
        var bean=ManagementFactory.getThreadMXBean();
        var allocations=bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported()?b:null;
        if(allocations!=null && !allocations.isThreadAllocatedMemoryEnabled())allocations.setThreadAllocatedMemoryEnabled(true);
        try {
            for(boolean tick:new boolean[]{true,false}) {
                for(int round=0;round<9;round++) {
                    long bytes=allocations==null?0:allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
                    long start=System.nanoTime();
                    for(int pass=0;pass<256;pass++) {
                        WeatherParticleContext.begin(level);
                        try {
                            for(int i=0;i<cards.length;i++) {
                                var card=cards[i];
                                if(tick) {card.setPos((i%8)-4,118,(i/8)-8);card.tick();}
                                else card.extract(output,client.gameRenderer.mainCamera(),.5F);
                            }
                        } finally {WeatherParticleContext.end();}
                    }
                    long elapsed=System.nanoTime()-start;
                    if(round>=6)System.out.printf("Weather CPU probe %s %s: %.1f ns/card, %.1f bytes/card%n",
                        snow?"snow":"rain",tick?"tick":"extract",elapsed/(256.0*cards.length),
                        allocations==null?-1:(allocations.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytes)/(256.0*cards.length));
                }
            }
            sink=output.value;
        } finally {for(var card:cards)card.remove();}
    }
}
