package naturality.entity;

import naturality.mixin.CreeperFuseAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

/** Vanilla creeper goals, cats, targeting, ignition and explosions with a shorter fuse. */
public final class MaddenedCreeper extends Creeper implements MaddenedMob {
    public static final int FUSE_TICKS = 16;
    private int leapCooldown;

    public MaddenedCreeper(EntityType<? extends Creeper> type, Level level) {
        super(type, level);
        ((CreeperFuseAccess)(Object)this).naturality$setFuse(FUSE_TICKS);
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ((CreeperFuseAccess)(Object)this).naturality$setFuse(Math.clamp(input.getShortOr("Fuse", (short)FUSE_TICKS), 3, FUSE_TICKS));
    }

    @Override public void tick() {
        if (!level().isClientSide()) {
            if (leapCooldown > 0) leapCooldown--;
            var target = getTarget();
            if (!isNoAi() && isAlive() && onGround() && leapCooldown == 0 && getSwellDir() > 0
                    && target != null && target.isAlive() && distanceToSqr(target) < 49
                    && getSensing().hasLineOfSight(target)) {
                var direction = target.position().subtract(position()).multiply(1, 0, 1);
                if (direction.lengthSqr() > .01) {
                    var horizontal = direction.normalize().scale(.55);
                    setDeltaMovement(horizontal.x, .42, horizontal.z);
                    needsSync = true;
                    leapCooldown = 30;
                }
            }
        }
        super.tick();
    }
}
