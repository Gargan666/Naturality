package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.particle.DeathSmoke;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class DeathSmokeSpawnMixin {
    @WrapMethod(method="makePoofParticles")
    private void naturality$smoke(Operation<Void> original) {
        var entity = (LivingEntity)(Object)this;
        if (!entity.level().isClientSide() || !(entity instanceof Mob)) { original.call(); return; }
        var previous = DeathSmoke.spawning;
        int index = DeathSmoke.index;
        DeathSmoke.spawning = entity;
        DeathSmoke.index = 0;
        try { original.call(); }
        finally { DeathSmoke.spawning = previous; DeathSmoke.index = index; }
    }
}
