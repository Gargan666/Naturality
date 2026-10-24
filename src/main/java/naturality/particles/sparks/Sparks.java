package naturality.particles.sparks;

import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/** Server-broadcast flint-and-steel bursts, sharing the parry spark particle. */
@NullMarked
public final class Sparks {
    private Sparks() {}

    public static void ignite(Level level, Vec3 hit, Direction face) {
        if (!(level instanceof ServerLevel server)) return;
        // Start just outside the clicked surface so terrain collision does not consume the burst immediately.
        var origin = hit.add(face.getStepX() * 0.04, face.getStepY() * 0.04, face.getStepZ() * 0.04);
        server.sendParticles(NaturalityParticles.SWORD_COUNTER_SPARK,
            origin.x, origin.y, origin.z, 12, 0.005, 0.005, 0.005, 0.15);
    }
}
