package naturality.client.particle;

import net.minecraft.client.particle.Particle;

/** Associates particle-type tag decisions with live client particles. */
public final class WindParticleControl {
    public interface Access {
        void naturality$applyWind();
        boolean naturality$windImmune();
        void naturality$windImmune(boolean value);
    }
    public interface LeafAccess {
        boolean naturality$isLandedLeaf();
    }
    private WindParticleControl() {}
    public static void mark(@org.jspecify.annotations.Nullable Particle particle, boolean immune) { if (particle != null && immune) ((Access)particle).naturality$windImmune(true); }
    public static boolean immune(Particle particle) { return ((Access)particle).naturality$windImmune(); }
}
