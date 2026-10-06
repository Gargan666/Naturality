package naturality.client.villager;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

import net.minecraft.world.phys.Vec3;

/** Client-side Verlet rope: pinned ends, gravity, inertia and length constraints. */
public final class RopeChain {
    static final int SEGMENTS = 24;
    private static final Map<Object, RopeChain> CHAINS = new HashMap<>();
    private static long lastPrune;
    final RopeSimulation simulation = new RopeSimulation();
    private boolean initialized;
    private long lastTime, lastSeen;
    private double remainder, length;
    private Object level;

    public static Vec3[] sample(Object key, Vec3 start, Vec3 end) {
        return sampleChain(key, start, end).simulation.snapshot();
    }

    static RopeChain sampleChain(Object key, Vec3 start, Vec3 end) {
        long now = System.nanoTime();
        if (now - lastPrune > 1_000_000_000L) {
            CHAINS.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 5_000_000_000L);
            lastPrune = now;
        }
        RopeChain chain = CHAINS.computeIfAbsent(key, _ -> new RopeChain());
        chain.update(start, end, now);
        return chain;
    }

    private void update(Vec3 start, Vec3 end, long now) {
        var client = Minecraft.getInstance();
        double desired = start.distanceTo(end) * 1.06 + .12;
        if (!initialized || level != client.level || simulation.distanceSquared(0, start) > 64
                || simulation.distanceSquared(SEGMENTS, end) > 64) {
            initialized = true;
            level = client.level;
            length = desired;
            simulation.reset(start, end);
            lastTime = now;
            remainder = 0;
        }
        double elapsed = Math.min(.1, (now - lastTime) / 1_000_000_000.0);
        lastTime = lastSeen = now;
        if (!client.isPaused()) remainder += elapsed;
        length = Math.max(desired, length * .98 + desired * .02);
        simulation.pin(start, end);
        while (remainder >= .025) {
            remainder -= .025;
            simulation.step(start, end, length);
        }
        simulation.pin(start, end);
    }
    private RopeChain() { }
}
