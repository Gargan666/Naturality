package naturality.blocks.fire.mixins;

import it.unimi.dsi.fastutil.longs.LongSet;
import naturality.blocks.fire.FireGeometry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.Blocks;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Function;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FireMixins {
    private FireMixins() {}

    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FireContactMixin {
        @Inject(method = "checkInsideBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;Lit/unimi/dsi/fastutil/longs/LongSet;I)I", at = @At("RETURN"))
        private void naturality$neighborFire(Vec3 from, Vec3 to, InsideBlockEffectApplier.StepBasedCollector effects,
                LongSet visited, int maxSteps, CallbackInfoReturnable<Integer> cir) {
            Entity entity = (Entity) (Object) this;
            if (!naturality.config.gameplay.GameplaySettings.fireWrapping(entity.level())) return;
            // Broaden discovery for flames outside their owning cell, retaining the exact shape test.
            var bounds = entity.getBoundingBox().move(to.subtract(entity.position())).inflate(1);
            BlockGetter.forEachBlockIntersectedBetween(from, to, bounds, (pos, step) -> {
                if (!entity.isAlive() || step >= maxSteps) return false;
                if (!visited.contains(pos.asLong()) && naturality.api.worlds.LoadedChunks.has(entity.level(), pos)) {
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

    @Mixin(BlockGetter.class)
    @org.jspecify.annotations.NullMarked
    public interface FirePickingMixin {
        @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "clip")
        private BlockHitResult naturality$pickContext(net.minecraft.world.level.ClipContext context,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation<BlockHitResult> original) {
            if (!naturality.config.gameplay.GameplaySettings.fireWrapping((BlockGetter) this)) return original.call(context);
            var previous = FireGeometry.PICK_CONTEXT.get();
            FireGeometry.PICK_CONTEXT.set(context);
            try { return original.call(context); }
            finally {
                if (previous == null) FireGeometry.PICK_CONTEXT.remove();
                else FireGeometry.PICK_CONTEXT.set(previous);
            }
        }
        @Inject(method = "clipWithInteractionOverride", at = @At("RETURN"), cancellable = true)
        private void naturality$neighborFire(Vec3 from, Vec3 to, BlockPos pos, VoxelShape originalShape,
                BlockState state, CallbackInfoReturnable<BlockHitResult> cir) {
            BlockGetter level = (BlockGetter) this;
            if (!naturality.config.gameplay.GameplaySettings.fireWrapping(level)) return;
            var context = FireGeometry.PICK_CONTEXT.get();
            if (context == null) return;
            if (!naturality.api.geometry.RaycastShapePresence.mayContain(level,naturality.api.geometry.RaycastShapePresence.FIRE,
                    pos.getX()-1,pos.getY()-1,pos.getZ()-1,pos.getX()+1,pos.getY()+1,pos.getZ()+1)) return;
            BlockHitResult closest = cir.getReturnValue();
            double distance = closest == null ? Double.POSITIVE_INFINITY : closest.getLocation().distanceToSqr(from);
            for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (candidate.equals(pos)) continue;
                var fire = level.getBlockState(candidate);
                if (!(fire.getBlock() instanceof BaseFireBlock)) continue;
                // Collision/visibility rays must still pass through non-solid fire.
                if (context.getBlockShape(fire, level, candidate).isEmpty()) continue;
                var candidateShape = FireGeometry.shape(level, candidate, fire);
                if (!naturality.api.geometry.RaycastShapePresence.mayIntersect(candidateShape, from, to, candidate)) continue;
                var hit = candidateShape.clip(from, to, candidate.immutable());
                // Only return intersections in the cell currently traversed. Returning
                // a farther neighbor hit here could otherwise skip a wall on the ray.
                if (hit != null && new net.minecraft.world.phys.AABB(pos).inflate(1e-6).contains(hit.getLocation())
                        && hit.getLocation().distanceToSqr(from) < distance) {
                    closest = hit; distance = hit.getLocation().distanceToSqr(from);
                }
            }
            cir.setReturnValue(closest);
        }
    }

    /** Attachment is independent of flammability; burning/spreading still uses vanilla odds. */
    @Mixin(FireBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FirePlacementMixin {
        private static boolean naturality$support(BlockGetter level, BlockPos pos) {
            return !FireGeometry.supportBoxes(level, pos).isEmpty();
        }
        private static boolean naturality$attached(BlockGetter level, BlockPos pos) {
            for (Direction direction : Direction.values())
                if (naturality$support(level, pos.relative(direction))) return true;
            return false;
        }
        @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
        private void naturality$survive(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            if (naturality.api.geometry.ShapeRecursionGuard.active()) return;
            if (level.getBlockState(pos.below()).is(Blocks.SNOW)
                    || level.getBlockState(pos.below()).is(Blocks.SNOW_BLOCK)) {
                cir.setReturnValue(false);
                return;
            }
            if (naturality.config.gameplay.GameplaySettings.fireWrapping(level) && naturality$attached(level, pos)) cir.setReturnValue(true);
        }
        @Inject(method = "getStateForPlacement(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true)
        private void naturality$placement(BlockGetter level, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
            if (naturality.api.geometry.ShapeRecursionGuard.active()) return;
            if (!naturality.config.gameplay.GameplaySettings.fireWrapping(level)) return;
            BlockState result = ((FireBlock) (Object) this).defaultBlockState();
            if (naturality$support(level, pos.below())) {
                cir.setReturnValue(result);
            } else if (naturality$attached(level, pos)) {
                for (var entry : FireBlock.PROPERTY_BY_DIRECTION.entrySet())
                    result = result.setValue(entry.getValue(), naturality$support(level, pos.relative(entry.getKey())));
                cir.setReturnValue(result);
            }
        }
        @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
        private boolean naturality$partialSupport(BlockState state, BlockGetter level, BlockPos below, Direction face, Operation<Boolean> original) {
            if (naturality.api.geometry.ShapeRecursionGuard.active()) return original.call(state, level, below, face);
            return original.call(state, level, below, face) || (naturality.config.gameplay.GameplaySettings.fireWrapping(level) && naturality$attached(level, below.above()));
        }
    }

    @Mixin(FireBlock.class)
    @org.jspecify.annotations.NullMarked
    public interface FireShapeAccessor {
        @Accessor("shapes") Function<BlockState, VoxelShape> naturality$vanillaShapes();
    }

    @Mixin(BlockBehaviour.BlockStateBase.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FireShapeMixin {
        @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
        private void naturality$outline(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
            var state = (BlockState) (Object) this;
            if (naturality.config.gameplay.GameplaySettings.fireWrapping(level) && state.getBlock() instanceof BaseFireBlock) cir.setReturnValue(FireGeometry.shape(level, pos, state));
        }
        @Inject(method = "getEntityInsideCollisionShape", at = @At("HEAD"), cancellable = true)
        private void naturality$contact(BlockGetter level, BlockPos pos, Entity entity, CallbackInfoReturnable<VoxelShape> cir) {
            var state = (BlockState) (Object) this;
            if (naturality.config.gameplay.GameplaySettings.fireWrapping(level) && state.getBlock() instanceof BaseFireBlock) cir.setReturnValue(FireGeometry.shape(level, pos, state));
        }
    }
}
