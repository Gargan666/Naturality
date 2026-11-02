package naturality.client.particles.forestspores;

/** Appearance shared by crimson and warped ambient spores. */
public final class ForestSpores {
    private ForestSpores() {}

    public interface Access {
        void naturality$fadeForestSpore();
    }

    public static float opacity(int age, int lifetime, float partial) {
        float time = age + partial;
        float duration = Math.min(8, lifetime * .25F);
        if (duration <= 0) return 0;
        float fade = Math.clamp(Math.min(time, lifetime - time) / duration, 0, 1);
        return fade * fade * (3 - 2 * fade);
    }
}
