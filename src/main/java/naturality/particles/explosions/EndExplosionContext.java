package naturality.particles.explosions;

/** Marks synchronous ritual explosions, including those without an entity source. */
public final class EndExplosionContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private EndExplosionContext() {}
    public static boolean active() { return ACTIVE.get(); }
    public static void run(Runnable explosion) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try { explosion.run(); }
        finally {
            if (previous) ACTIVE.set(true);
            else ACTIVE.remove();
        }
    }
}
