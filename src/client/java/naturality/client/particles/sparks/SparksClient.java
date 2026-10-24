package naturality.client.particles.sparks;

import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Place minecart sparks using observed travel, including both vanilla minecart movement implementations. */
@NullMarked
public final class SparksClient {
    private static final double FAST_CART_SPEED = 7.0 / 20.0;

    private SparksClient() {}

    public static void minecart(AbstractMinecart cart, @Nullable Vec3 previousPosition) {
        if (!(cart.level() instanceof ClientLevel level) || previousPosition == null || cart.isRemoved()) return;
        var movement = cart.position().subtract(previousPosition);
        double speed = movement.horizontalDistance();
        if (speed < FAST_CART_SPEED
                || !BaseRailBlock.isRail(level.getBlockState(cart.getCurrentBlockPosOrRailBelow()))) return;
        double forwardX = movement.x / speed, forwardZ = movement.z / speed;
        var random = level.getRandom();
        int count = Math.min(6, 3 + (int) ((speed - FAST_CART_SPEED) * 4));
        for (int i = 0; i < count; i++) {
            double along = random.nextDouble() * 0.4 - 0.2;
            double across = random.nextDouble() * 0.5 - 0.25;
            double backward = 0.12 + random.nextDouble() * 0.14;
            double spread = (random.nextDouble() - 0.5) * 0.12;
            level.addParticle(NaturalityParticles.SWORD_COUNTER_SPARK,
                cart.getX() + forwardX * along - forwardZ * across,
                cart.getBoundingBox().minY + 0.08,
                cart.getZ() + forwardZ * along + forwardX * across,
                -forwardX * backward - forwardZ * spread, 0.02 + random.nextDouble() * 0.06,
                -forwardZ * backward + forwardX * spread);
        }
    }
}
