package naturality.client.particle;

/** Marks waterfall particles whose spawning location is a flowing-to-resting edge. */
public interface BorderSplashParticle {
    void naturality$enlargeAtRestingEdge(float fallSize);
    int naturality$lifetime();
    void naturality$warmStart(int elapsedTicks);
}
