package naturality.client.particle;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.particle.Particle;

/** Associates particle-type tag decisions with live client particles. */
public final class WindParticleControl {
    public interface Access {
        void naturality$applyWind();
    }
    public interface LeafAccess {
        boolean naturality$isLandedLeaf();
    }
    private static final Map<Particle,Boolean> IMMUNE = Collections.synchronizedMap(new WeakHashMap<>());
    private WindParticleControl() {}
    public static void mark(@org.jspecify.annotations.Nullable Particle particle, boolean immune) { if (particle != null && immune) IMMUNE.put(particle, true); }
    public static boolean immune(Particle particle) { return IMMUNE.containsKey(particle); }
}
