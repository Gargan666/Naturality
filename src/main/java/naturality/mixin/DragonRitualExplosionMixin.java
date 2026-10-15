package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.config.Feature;
import naturality.particle.EndExplosionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scope the End palette to pillar rebuilding and ritual crystal detonation. */
@Mixin(targets = {"net.minecraft.world.level.dimension.end.DragonRespawnStage$3",
    "net.minecraft.world.level.dimension.end.DragonRespawnStage$4"})
public abstract class DragonRitualExplosionMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"))
    private void naturality$ritualPalette(ServerLevel level, @Nullable Entity source,
            double x, double y, double z, float strength, Level.ExplosionInteraction interaction, Operation<Void> original) {
        if (Feature.EXPLOSION_PARTICLES.enabled()) {
            EndExplosionContext.run(() -> original.call(level, source, x, y, z, strength, interaction));
        } else {
            original.call(level, source, x, y, z, strength, interaction);
        }
    }
}
