package naturality.items.heavyboots;

import naturality.bootstrap.ContentRegistration.NaturalityItems;
import naturality.config.startup.FeatureConfig.Feature;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Iron footwear that trades water locomotion for slow, low-gravity walking. */
@org.jspecify.annotations.NullMarked
public final class HeavyBoots {
    private HeavyBoots() { }
    public static final double GRAVITY_SCALE = 0.25;
    public static final double HORIZONTAL_SPEED_SCALE = 0.75;
    private static final ThreadLocal<@Nullable Entity> LUNGE_USER = new ThreadLocal<>();

    public static boolean worn(LivingEntity entity) {
        // Do not initialize the content registry when content is disabled.
        var boots = entity.getItemBySlot(EquipmentSlot.FEET);
        return Feature.CONTENT.enabled() && !boots.isBroken() && boots.is(NaturalityItems.HEAVY_BOOTS);
    }

    public static boolean active(LivingEntity entity) {
        return walkingEligible(entity) && entity.isInWater();
    }

    /** Also usable during water entry, before vanilla updates its cached water flag. */
    public static boolean walkingEligible(LivingEntity entity) {
        return entity instanceof Player player && worn(player) && player.isAlive()
            && !player.isPassenger() && !player.isSleeping() && !player.isSpectator()
            && !player.getAbilities().flying && !player.isFallFlying()
            && !naturality.systems.swimming.WaterSurface.riptiding(player);
    }

    /** Only Lunge's synchronous condition checks see this player as dry. */
    public static void withLungeUser(Entity user, Runnable action) {
        var previous = LUNGE_USER.get();
        LUNGE_USER.set(user);
        try {
            action.run();
        } finally {
            if (previous == null) LUNGE_USER.remove();
            else LUNGE_USER.set(previous);
        }
    }

    public static boolean lungeTreatsAsDry(Entity entity) {
        return LUNGE_USER.get() == entity && entity instanceof LivingEntity living && active(living);
    }

    public static Vec3 walkingInput(Vec3 input) {
        var normalized = input.lengthSqr() > 1 ? input.normalize() : input;
        return new Vec3(normalized.x * HORIZONTAL_SPEED_SCALE, normalized.y,
            normalized.z * HORIZONTAL_SPEED_SCALE);
    }
}
