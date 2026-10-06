package naturality.test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class EndFootstepParticleChecks {
    private record Burst(ParticleOptions options, double y, double vx, double vy, double vz) {}
    private static final List<Burst> bursts = new ArrayList<>();
    private static boolean recording;

    public static void record(ParticleOptions options, double y, double vx, double vy, double vz) {
        if (recording && options.getType() == ParticleTypes.BLOCK) bursts.add(new Burst(options, y, vx, vy, vz));
    }

    public static void inverted(Minecraft client) {
        checkBurst(client.player, true);
        var cow = EntityTypes.COW.create(client.level, EntitySpawnReason.COMMAND);
        DistortionFixtures.invert(cow);
        cow.setPos(4, 105 - cow.getBbHeight(), 4);
        cow.setOnGround(true);
        checkBurst(cow, true);
        // Only the test resource pack exempts pigs from End gravity.
        var pig = EntityTypes.PIG.create(client.level, EntitySpawnReason.COMMAND);
        pig.setPos(4, 99, 4);
        pig.setOnGround(true);
        checkBurst(pig, false);
    }

    public static void upright(Minecraft client) { checkBurst(client.player, false); }

    private static void checkBurst(Entity entity, boolean inverted) {
        Vec3 oldMovement = entity.getDeltaMovement();
        bursts.clear();
        recording = true;
        try {
            entity.setDeltaMovement(.2, 0, -.3);
            var spawn = Entity.class.getDeclaredMethod("spawnSprintParticle");
            spawn.setAccessible(true);
            spawn.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        } finally {
            recording = false;
            entity.setDeltaMovement(oldMovement);
        }
        if (bursts.size() != 1) throw new AssertionError("Expected one actual footstep particle, got " + bursts.size());
        Burst burst = bursts.getFirst();
        double feetY = inverted ? entity.getBoundingBox().maxY - .1 : entity.getY() + .1;
        if (Math.abs(burst.y - feetY) > 1e-6)
            throw new AssertionError("Footstep particle must spawn at gravity-relative feet: expected " + feetY + ", got " + burst.y);
        if (burst.vy != (inverted ? -1.5 : 1.5))
            throw new AssertionError("Footstep particle must launch away from its supporting surface");
        if (Math.abs(burst.vx + .8) > 1e-6 || Math.abs(burst.vz - 1.2) > 1e-6)
            throw new AssertionError("Footstep particle must preserve horizontal kickback");
        if (!((BlockParticleOption) burst.options).getState().is(Blocks.END_STONE))
            throw new AssertionError("Footstep particle must use the supporting block's texture");
    }
}
