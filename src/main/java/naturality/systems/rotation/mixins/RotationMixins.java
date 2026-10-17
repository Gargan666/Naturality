package naturality.systems.rotation.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import java.util.List;
import naturality.systems.rotation.PlayerRotation;
import naturality.systems.rotation.PlayerRotation.Angles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inert hook container. All hooks are always installed; zero rotation takes the vanilla path. */
public final class RotationMixins {
    private RotationMixins() { }

    @Mixin(Player.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationStateMixin implements PlayerRotation.Access {
        @Unique private static final EntityDataAccessor<Vector3fc> naturality$BODY_ROTATION =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.VECTOR3);
        @Unique private @Nullable Angles naturality$bodyRotation;
        @Unique private @Nullable Angles naturality$previousFrameRotation;
        @Unique private @Nullable Angles naturality$frameRotation;

        @Inject(method = "tick", at = @At("HEAD"))
        private void naturality$rotationFrame(CallbackInfo ci) {
            var player = (Player)(Object)this;
            if (!player.level().isClientSide()) return;
            if (naturality.systems.swimming.WaterSurface.riptiding(player)) {
                naturality$previousFrameRotation = naturality$frameRotation = Angles.ZERO;
                return;
            }
            var next = PlayerRotation.angles(player);
            var current = naturality$frameRotation;
            naturality$previousFrameRotation = current == null ? next : current;
            naturality$frameRotation = current == null || current.radiansTo(next) < Math.toRadians(0.05)
                ? next : current.towards(next, 0.5);
        }
        public Angles naturality$renderRotation(float partial) {
            var player = (Player)(Object)this;
            // Vanilla crawling already rotates the model; do not blend the old swim
            // transform on top of it after the server hands the pose back to vanilla.
            if (player.getPose() == Pose.SWIMMING && !PlayerRotation.active(player)) return Angles.ZERO;
            var previous = naturality$previousFrameRotation;
            var current = naturality$frameRotation;
            return previous == null || current == null ? PlayerRotation.angles((Player)(Object)this) : previous.towards(current, partial);
        }

        @Inject(method = "defineSynchedData", at = @At("TAIL"))
        private void naturality$define(SynchedEntityData.Builder builder, CallbackInfo ci) {
            builder.define(naturality$BODY_ROTATION, new Vector3f());
        }
        public @Nullable Angles naturality$rotation() { return naturality$bodyRotation; }
        public void naturality$rotation(Angles angles) {
            ((Player)(Object)this).getEntityData().set(naturality$BODY_ROTATION, new Vector3f(angles.x(), angles.yawCompensation(), angles.z()));
        }
        public void naturality$rotationUpdated() {
            var player = (Player)(Object)this;
            var value = player.getEntityData().get(naturality$BODY_ROTATION);
            var next = new Angles(value.x(), value.z(), value.y());
            if (next.equals(naturality$bodyRotation)) return;
            naturality$bodyRotation = next;
            player.setPos(player.position());
        }
        @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
        private void naturality$save(ValueOutput output, CallbackInfo ci) {
            var rotation = PlayerRotation.angles((Player)(Object)this);
            output.putFloat("naturality:body_rotation_x", rotation.x());
            output.putFloat("naturality:body_rotation_z", rotation.z());
            output.putFloat("naturality:body_rotation_y_compensation", rotation.yawCompensation());
        }
        @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
        private void naturality$load(ValueInput input, CallbackInfo ci) {
            float x = input.getFloatOr("naturality:body_rotation_x", 0);
            float z = input.getFloatOr("naturality:body_rotation_z", 0);
            float y = input.getFloatOr("naturality:body_rotation_y_compensation", 0);
            naturality$rotation(Float.isFinite(x) && Float.isFinite(z) && Float.isFinite(y) ? new Angles(x, z, y) : Angles.ZERO);
        }
        @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
        private void naturality$poseFits(Pose pose, CallbackInfoReturnable<Boolean> cir) {
            var player = (Player)(Object)this;
            if (PlayerRotation.terrainActive(player)) cir.setReturnValue(PlayerRotation.fits(player,
                PlayerRotation.body(player, player.position(), player.getDimensions(pose), PlayerRotation.angles(player))));
        }
        @Inject(method = "canFallAtLeast", at = @At("HEAD"), cancellable = true)
        private void naturality$edgeSupport(double x, double z, double height, CallbackInfoReturnable<Boolean> cir) {
            var player = (Player)(Object)this;
            if (!PlayerRotation.terrainActive(player)) return;
            var displacement = new Vec3(x, 0, z);
            // Only probe translated clearance here; actual motion is resolved by the swept body solver.
            var body = PlayerRotation.body(player).move(displacement);
            double down = naturality.systems.gravity.EndGravity.inverted(player) ? height : -height;
            cir.setReturnValue(PlayerRotation.fits(player, body.move(new Vec3(0, down, 0))));
        }
    }

    @Mixin(CollisionGetter.class)
    @org.jspecify.annotations.NullMarked
    public interface PlayerRotationClearanceMixin {
        @Inject(method = "noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Z)Z", at = @At("HEAD"), cancellable = true)
        default void naturality$clearance(@Nullable Entity entity, AABB area, boolean fluids, CallbackInfoReturnable<Boolean> cir) {
            if (fluids || !(entity instanceof Player player) || !PlayerRotation.terrainActive(player)) return;
            var envelope = player.getBoundingBox();
            if (Math.abs(area.getXsize() - envelope.getXsize()) > 1.0e-6
                    || Math.abs(area.getYsize() - envelope.getYsize()) > 1.0e-6
                    || Math.abs(area.getZsize() - envelope.getZsize()) > 1.0e-6) return;
            cir.setReturnValue(PlayerRotation.fits(player, PlayerRotation.body(player).move(area.getCenter().subtract(envelope.getCenter()))));
        }
    }

    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationEntityMixin {
        @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
        private void naturality$wholeBodySuffocation(CallbackInfoReturnable<Boolean> cir) {
            if ((Object)this instanceof Player player && PlayerRotation.terrainActive(player))
                cir.setReturnValue(PlayerRotation.fullyInsideSolids(player));
        }
        @Inject(method = "getFluidInteractionBox", at = @At("HEAD"), cancellable = true)
        private void naturality$swimmingFluidBox(CallbackInfoReturnable<AABB> cir) {
            if ((Object)this instanceof Player player && naturality.systems.swimming.PlayerSwimming.compactTerrain(player))
                cir.setReturnValue(PlayerRotation.terrain(PlayerRotation.body(player)).bounds().deflate(0.001));
        }
        @Inject(method = "isEyeInFluid", at = @At("HEAD"), cancellable = true)
        private void naturality$swimmingEyes(net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> type,
                CallbackInfoReturnable<Boolean> cir) {
            if (!((Object)this instanceof Player player) || !naturality.systems.swimming.PlayerSwimming.compactTerrain(player)) return;
            // The body scan now covers the movement cube. Breathing still samples the actual eye,
            // which may lie outside that cube or inside a block while the torso remains submerged.
            var eye = player.getEyePosition();
            var pos = BlockPos.containing(eye);
            if (!naturality.api.worlds.LoadedChunks.has(player.level(), pos)) { cir.setReturnValue(false); return; }
            var fluid = player.level().getFluidState(pos);
            boolean submerged = fluid.is(type) && eye.y <= pos.getY() + fluid.getHeightForCamera(player.level(), pos);
            if (!submerged && type.equals(net.minecraft.tags.FluidTags.WATER)
                    && player.isInWater() && naturality.systems.swimming.WaterSurface.solidAt(player, pos, eye)) {
                // Solid material is not breathable air. The collider's water contact remains authoritative here.
                submerged = true;
            }
            cir.setReturnValue(submerged);
        }
        @Inject(method = "collidedWithShapeMovingFrom", at = @At("HEAD"), cancellable = true)
        private void naturality$shapeContact(Vec3 from, Vec3 to, List<AABB> obstacles, CallbackInfoReturnable<Boolean> cir) {
            if (!((Object)this instanceof Player player) || !PlayerRotation.active(player)) return;
            var body = PlayerRotation.body(player).move(from.subtract(player.position()));
            var delta = to.subtract(from);
            for (var obstacle : obstacles) {
                if (body.separation(obstacle) < -PlayerRotation.EPSILON || body.sweep(obstacle, delta).fraction() < 1) {
                    cir.setReturnValue(true);
                    return;
                }
            }
            cir.setReturnValue(false);
        }
        // Vanilla's full-block shortcut assumes every cell in the enclosing AABB is occupied.
        @WrapOperation(method = "lambda$checkInsideBlocks$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getEntityInsideCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
        private VoxelShape naturality$insideShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity,
                Operation<VoxelShape> original, @Local(argsOnly = true, ordinal = 0) Vec3 from, @Local(argsOnly = true, ordinal = 1) Vec3 to) {
            var shape = original.call(state, level, pos, entity);
            if (PlayerRotation.active(entity) && !entity.collidedWithShapeMovingFrom(from, to, shape.move(pos.getX(), pos.getY(), pos.getZ()).toAabbs()))
                return Shapes.empty();
            return shape;
        }
        @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V", at = @At("TAIL"))
        private void naturality$sync(EntityDataAccessor<?> accessor, CallbackInfo ci) {
            if ((Object)this instanceof PlayerRotation.Access access) access.naturality$rotationUpdated();
        }
        @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
        private void naturality$bounds(Vec3 position, CallbackInfoReturnable<AABB> cir) {
            if ((Object)this instanceof Player player && PlayerRotation.active(player)) cir.setReturnValue(
                PlayerRotation.body(player, position, player.getDimensions(player.getPose()), PlayerRotation.angles(player)).bounds());
        }
        @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
        private void naturality$collide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
            if ((Object)this instanceof Player player && PlayerRotation.terrainActive(player)) cir.setReturnValue(PlayerRotation.collide(player, movement));
        }
        @Inject(method = "getEyeY", at = @At("RETURN"), cancellable = true)
        private void naturality$eyeY(CallbackInfoReturnable<Double> cir) {
            if ((Object)this instanceof Player player && PlayerRotation.active(player))
                cir.setReturnValue(player.getY() + PlayerRotation.eyeOffset(player, player.getEyeHeight()).y);
        }
        @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
        private void naturality$eye(CallbackInfoReturnable<Vec3> cir) {
            if ((Object)this instanceof Player player && PlayerRotation.active(player))
                cir.setReturnValue(player.position().add(PlayerRotation.eyeOffset(player, player.getEyeHeight())));
        }
        @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
        private void naturality$interpolatedEye(float partial, CallbackInfoReturnable<Vec3> cir) {
            if ((Object)this instanceof Player player) {
                var rotation = PlayerRotation.interpolatedAngles(player, partial);
                if (rotation.active()) cir.setReturnValue(player.getPosition(partial).add(PlayerRotation.eyeOffset(player, player.getEyeHeight(), rotation)));
            }
        }
        @Inject(method = "checkSupportingBlock", at = @At("HEAD"), cancellable = true)
        private void naturality$support(boolean onGround, @Nullable Vec3 movement, CallbackInfo ci) {
            if (!((Object)this instanceof Player player) || !PlayerRotation.terrainActive(player)) return;
            player.mainSupportingBlockPos = onGround ? PlayerRotation.support(player, 1.0e-4).map(PlayerRotation.Contact::block) : Optional.empty();
            ci.cancel();
        }
        @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
        private void naturality$surface(float offset, CallbackInfoReturnable<BlockPos> cir) {
            if (!((Object)this instanceof Player player) || !PlayerRotation.terrainActive(player)) return;
            var contact = PlayerRotation.support(player, Math.max(1.0e-4, offset));
            cir.setReturnValue(contact.map(PlayerRotation.Contact::block).orElseGet(() -> {
                var bounds = player.getBoundingBox();
                double y = naturality.systems.gravity.EndGravity.inverted(player) ? bounds.maxY + offset : bounds.minY - offset;
                return BlockPos.containing(player.getX(), y, player.getZ());
            }));
        }
    }

    @Mixin(ServerGamePacketListenerImpl.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationServerMovementMixin {
        @Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
        private void naturality$validate(LevelReader level, Entity entity, AABB oldBounds, double x, double y, double z,
                CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof Player player && PlayerRotation.terrainActive(player)) cir.setReturnValue(
                PlayerRotation.introducesCollision(player, oldBounds, new Vec3(x, y, z)));
        }
    }

    @Mixin(EntityFluidInteraction.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationFluidMixin {
        @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBlockX()I"))
        private int naturality$eyeColumnX(Entity entity, Operation<Integer> original) {
            return PlayerRotation.active(entity) ? net.minecraft.util.Mth.floor(entity.getEyePosition().x) : original.call(entity);
        }
        @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBlockZ()I"))
        private int naturality$eyeColumnZ(Entity entity, Operation<Integer> original) {
            return PlayerRotation.active(entity) ? net.minecraft.util.Mth.floor(entity.getEyePosition().z) : original.call(entity);
        }
        @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;isEmpty()Z"))
        private boolean naturality$fluidContact(FluidState fluid, Operation<Boolean> original,
                @Local(argsOnly = true) Entity entity, @Local BlockPos.MutableBlockPos pos) {
            if (original.call(fluid)) return true;
            if (!(entity instanceof Player player) || !PlayerRotation.active(player)) return false;
            if (naturality.systems.swimming.PlayerSwimming.compactTerrain(player)) return false;
            var bounds = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1,
                pos.getY() + fluid.getHeight(player.level(), pos), pos.getZ() + 1);
            return PlayerRotation.body(player).separation(bounds) >= -PlayerRotation.EPSILON;
        }
    }

    @Mixin(ProjectileUtil.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PlayerRotationTargetingMixin {
        @WrapOperation(method = {
            "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            "getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;FLnet/minecraft/world/level/ClipContext$Block;ZZ)Ljava/util/Collection;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
        private static Optional<Vec3> naturality$exactHit(AABB bounds, Vec3 from, Vec3 to, Operation<Optional<Vec3>> original,
                @Local(name = "entity") Entity entity) {
            if (entity instanceof Player player && PlayerRotation.active(player)) {
                double margin = Math.max(0, (bounds.getXsize() - player.getBoundingBox().getXsize()) / 2);
                return PlayerRotation.body(player).clip(from, to, margin);
            }
            return original.call(bounds, from, to);
        }
        @WrapOperation(method = {
            "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            "getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;FLnet/minecraft/world/level/ClipContext$Block;ZZ)Ljava/util/Collection;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
        private static boolean naturality$inside(AABB bounds, Vec3 point, Operation<Boolean> original,
                @Local(name = "entity") Entity entity) {
            if (entity instanceof Player player && PlayerRotation.active(player)) {
                double margin = Math.max(0, (bounds.getXsize() - player.getBoundingBox().getXsize()) / 2);
                return PlayerRotation.body(player).clip(point, point, margin).isPresent();
            }
            return original.call(bounds, point);
        }
    }
}
