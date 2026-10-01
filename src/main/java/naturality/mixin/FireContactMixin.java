package naturality.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import naturality.fire.FireGeometry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class FireContactMixin {
    @Inject(method = "checkInsideBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;Lit/unimi/dsi/fastutil/longs/LongSet;I)I", at = @At("RETURN"))
    private void naturality$neighborFire(Vec3 from, Vec3 to, InsideBlockEffectApplier.StepBasedCollector effects,
            LongSet visited, int maxSteps, CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity) (Object) this;
        if (!naturality.config.GameplaySettings.fireWrapping(entity.level())) return;
        // Broaden discovery for flames outside their owning cell, retaining the exact shape test.
        var bounds = entity.getBoundingBox().move(to.subtract(entity.position())).inflate(1);
        BlockGetter.forEachBlockIntersectedBetween(from, to, bounds, (pos, step) -> {
            if (!entity.isAlive() || step >= maxSteps) return false;
            if (!visited.contains(pos.asLong()) && naturality.util.LoadedChunks.has(entity.level(), pos)) {
                var state = entity.level().getBlockState(pos);
                if (state.getBlock() instanceof BaseFireBlock && entity.collidedWithShapeMovingFrom(from, to,
                        FireGeometry.shape(entity.level(), pos, state).move(new Vec3(pos)).toAabbs())) {
                    visited.add(pos.asLong());
                    effects.advanceStep(Math.max(step, cir.getReturnValue() - 1));
                    state.entityInside(entity.level(), pos, entity, effects, true);
                }
            }
            return true;
        });
    }
}
