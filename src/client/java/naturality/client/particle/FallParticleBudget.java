package naturality.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleLimit;

/** Water and lava share a population limit, including queued particles and warm starts. */
public final class FallParticleBudget {
    public static final ParticleLimit LIMIT=new ParticleLimit(768);
    public static final java.util.Optional<ParticleLimit> OPTIONAL_LIMIT=java.util.Optional.of(LIMIT);
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private static long tick=Long.MIN_VALUE;
    private static int spawned,droplets;
    private FallParticleBudget() { }
    public static boolean exhausted(ClientLevel level) {
        if(world!=level || tick!=level.getGameTime()) {world=level;tick=level.getGameTime();spawned=droplets=0;}
        return spawned>=64;
    }
    public static void admitted(ClientLevel level) {exhausted(level);spawned++;}
    public static boolean allowDroplet(ClientLevel level) {
        exhausted(level);
        if(droplets>=64)return false;
        droplets++;return true;
    }
    public static void reset() {world=null;tick=Long.MIN_VALUE;spawned=droplets=0;}
}
