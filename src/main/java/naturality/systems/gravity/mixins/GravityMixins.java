package naturality.systems.gravity.mixins;

import naturality.systems.gravity.EndGravity;
import naturality.systems.gravity.EndGravity.EndGravityTeleport;
import naturality.bootstrap.ContentRegistration.NaturalityEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Shadow;
import naturality.systems.gravity.EndGravity.CeilingPathNavigation;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.entity.MoverType;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.world.entity.Pose;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class GravityMixins {
    private GravityMixins() {}

    @Mixin(Enderman.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityEndermanMixin {
        @Unique private boolean naturality$wasInverted;
        @Unique private boolean naturality$wasTargetingUnderside;
        @Unique private @org.jspecify.annotations.Nullable Vec3 naturality$brace;
        @Unique private int naturality$retry;
        @Inject(method="aiStep",at=@At("HEAD"))
        private void naturality$seekFooting(CallbackInfo ci) {
            var mob=(Enderman)(Object)this;if(!(mob.level() instanceof ServerLevel))return;
            if(!EndGravity.affected(mob)) {
                naturality$wasInverted=false;naturality$wasTargetingUnderside=false;naturality$brace=null;
                return;
            }
            boolean inverted=EndGravity.inverted(mob);
            boolean target=mob.hasEffect(NaturalityEffects.DISTORTION);
            if(target && (!naturality$wasTargetingUnderside || !mob.onGround() && --naturality$retry<=0)) {
                boolean escaped=EndGravityTeleport.seekUnderside(mob);
                naturality$brace=escaped && !inverted?mob.position():null;
                naturality$retry=escaped?10:2;
            }
            if(!inverted && (naturality$wasInverted || !target && naturality$brace!=null)) {
                naturality$brace=null;
                var pos=mob.blockPosition();
                mob.teleport(mob.getX(),mob.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    pos.getX(),pos.getZ()),mob.getZ());
            }
            if(!target || inverted)naturality$brace=null;
            naturality$wasInverted=inverted;
            naturality$wasTargetingUnderside=target;
        }
        @Inject(method="aiStep",at=@At("TAIL"))
        private void naturality$braceDuringTurn(CallbackInfo ci) {
            var brace = naturality$brace;
            if(brace==null)return;
            var mob=(Enderman)(Object)this;
            var support=BlockPos.containing(brace.x,brace.y+mob.getBbHeight()+1.0e-5,brace.z);
            var box=mob.getBoundingBox().move(brace.subtract(mob.position()));
            // Only hold successful, still-valid footing during the first half of
            // the turn. Never undo a different teleport or anchor to a removed block.
            if(mob.position().distanceToSqr(brace)>1
                    || !mob.level().getBlockState(support).isFaceSturdy(mob.level(),support,Direction.DOWN)
                    || !mob.level().noCollision(mob,box)) {
                naturality$brace=null;return;
            }
            mob.setPos(brace);mob.setDeltaMovement(Vec3.ZERO);
            mob.resetFallDistance();mob.setOnGround(true);mob.getNavigation().stop();
        }
        @Inject(method="teleport(DDD)Z",at=@At("HEAD"),cancellable=true)
        private void naturality$ceilingTeleport(double x,double y,double z,CallbackInfoReturnable<Boolean> cir) {
            var mob=(Enderman)(Object)this;
            if(EndGravity.affected(mob) && mob.level() instanceof ServerLevel
                    && (EndGravity.inverted(mob) || mob.hasEffect(NaturalityEffects.DISTORTION))) {
                boolean escaped=EndGravityTeleport.toUnderside(mob,x,z);
                if(escaped)naturality$brace=EndGravity.inverted(mob)?null:mob.position();
                cir.setReturnValue(escaped);
            }
        }
    }

    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityEntityMixin {
        @Unique private boolean naturality$wasInverted;
        @SuppressWarnings("null") @Shadow public java.util.Optional<BlockPos> mainSupportingBlockPos;
        @Unique private Entity naturality$self() { return (Entity)(Object)this; }
        @WrapMethod(method="refreshDimensions")
        private void naturality$keepCeilingFeet(Operation<Void> original) {
            var e=naturality$self();
            boolean anchor=e instanceof net.minecraft.world.entity.player.Player && EndGravity.inverted(e)
                && !naturality.systems.rotation.PlayerRotation.active(e);
            double top=e.getBoundingBox().maxY;
            original.call();
            if(anchor) {
                double shift=top-e.getBoundingBox().maxY;
                e.setPos(e.getX(),e.getY()+shift,e.getZ());
                // Pose-origin adjustments are not motion: keep interpolation in the new frame.
                e.yo+=shift;e.yOld+=shift;
                if(e instanceof net.minecraft.server.level.ServerPlayer player && player.connection!=null)
                    ((naturality.systems.gravity.EndGravity.GravityMovementAccess)player.connection).naturality$poseShift(shift);
            }
        }
        @Inject(method="getGravity",at=@At("RETURN"),cancellable=true)
        private void naturality$gravity(CallbackInfoReturnable<Double> cir) {
            cir.setReturnValue(cir.getReturnValue()*EndGravity.factor(naturality$self()));
        }
        @Inject(method="baseTick",at=@At("HEAD"))
        private void naturality$flipContact(CallbackInfo ci) {
            var e=naturality$self(); boolean inverted=EndGravity.inverted(e);
            if(inverted!=naturality$wasInverted) { e.setOnGround(false); e.resetFallDistance(); naturality$wasInverted=inverted; }
        }
        @WrapOperation(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setOnGroundWithMovement(ZZLnet/minecraft/world/phys/Vec3;)V"))
        private void naturality$ceilingGround(Entity e,boolean ground,boolean horizontal,Vec3 movement,Operation<Void> original,
                @Local(argsOnly=true) Vec3 delta) {
            if(EndGravity.inverted(e)) ground=e.verticalCollision && delta.y>0;
            e.verticalCollisionBelow=ground;
            original.call(e,ground,horizontal,movement);
        }
        @ModifyVariable(method="checkSupportingBlock",at=@At("STORE"),name="testArea")
        private AABB naturality$ceilingSupport(AABB area) {
            if(!EndGravity.inverted(naturality$self()))return area;
            var box=naturality$self().getBoundingBox();
            // Keep vanilla's previous-position fallback when crossing a block edge.
            return new AABB(box.minX,box.maxY,box.minZ,box.maxX,box.maxY+1.0e-6,box.maxZ);
        }
        @ModifyVariable(method="collide",at=@At("STORE"),name="onGroundAfterCollision")
        private boolean naturality$ceilingStepContact(boolean grounded,@Local(argsOnly=true) Vec3 movement,
                @Local(name="movementStep") Vec3 resolved) {
            return EndGravity.inverted(naturality$self())?movement.y>0 && movement.y!=resolved.y:grounded;
        }
        @WrapOperation(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;expandTowards(DDD)Lnet/minecraft/world/phys/AABB;"))
        private AABB naturality$ceilingStepSweep(AABB box,double x,double y,double z,Operation<AABB> original) {
            return original.call(box,x,EndGravity.inverted(naturality$self())?-y:y,z);
        }
        @WrapOperation(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;collectCandidateStepUpHeights(Lnet/minecraft/world/phys/AABB;Ljava/util/List;FF)[F"))
        private float[] naturality$ceilingStepHeights(AABB box,java.util.List<net.minecraft.world.phys.shapes.VoxelShape> colliders,
                float maxStep,float skip,Operation<float[]> original) {
            if(!EndGravity.inverted(naturality$self()))return original.call(box,colliders,maxStep,skip);
            var heights=new it.unimi.dsi.fastutil.floats.FloatArraySet(4);
            for(var collider:colliders)for(double y:collider.getCoords(net.minecraft.core.Direction.Axis.Y)) {
                float height=(float)(box.maxY-y);
                if(height>=0 && height<=maxStep && -height!=skip)heights.add(height);
            }
            float[] candidates=heights.toFloatArray();
            it.unimi.dsi.fastutil.floats.FloatArrays.unstableSort(candidates);
            for(int i=0;i<candidates.length;i++)candidates[i]=-candidates[i];
            return candidates;
        }
        @Inject(method="getOnPos(F)Lnet/minecraft/core/BlockPos;",at=@At("HEAD"),cancellable=true)
        private void naturality$ceilingBlock(float offset,CallbackInfoReturnable<BlockPos> cir) {
            var e=naturality$self(); if(EndGravity.inverted(e))cir.setReturnValue(
                mainSupportingBlockPos.orElseGet(() -> BlockPos.containing(e.getX(),e.getBoundingBox().maxY+offset,e.getZ())));
        }
        @WrapOperation(method="spawnSprintParticle",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$ceilingFootstep(net.minecraft.world.level.Level level,net.minecraft.core.particles.ParticleOptions options,
                double x,double y,double z,double vx,double vy,double vz,Operation<Void> original) {
            var e=naturality$self();
            if(EndGravity.inverted(e)) {
                // Mirror the burst about the body center so it leaves the ceiling-facing feet.
                var box=e.getBoundingBox();
                y=box.minY+box.maxY-y;
                vy=-vy;
            }
            original.call(level,options,x,y,z,vx,vy,vz);
        }
        @WrapOperation(method={"move","doCheckFallDamage"},at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"))
        private void naturality$upwardFall(Entity e,double y,boolean ground,BlockState block,BlockPos pos,Operation<Void> original) {
            original.call(e,EndGravity.inverted(e)?-y:y,ground,block,pos);
        }
        @Inject(method="getEyeY",at=@At("RETURN"),cancellable=true)
        private void naturality$eye(CallbackInfoReturnable<Double> cir) {
            var e=naturality$self(); if(EndGravity.affected(e) && !naturality.systems.rotation.PlayerRotation.active(e))
                cir.setReturnValue(e.getY()+EndGravity.eyeHeight(e,e.getEyeHeight(),false));
        }
        @ModifyExpressionValue(method="getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
        private float naturality$partialEye(float height) { return EndGravity.eyeHeight(naturality$self(),height,true); }
        @ModifyVariable(method="turn",at=@At("HEAD"),argsOnly=true,ordinal=0)
        private double naturality$mouseYaw(double delta) {
            return EndGravity.inverted(naturality$self()) && !naturality.systems.rotation.PlayerRotation.active(naturality$self())?-delta:delta;
        }
        @ModifyVariable(method="turn",at=@At("HEAD"),argsOnly=true,ordinal=1)
        private double naturality$mousePitch(double delta) {
            return EndGravity.inverted(naturality$self()) && !naturality.systems.rotation.PlayerRotation.active(naturality$self())?-delta:delta;
        }
        @ModifyVariable(method="move",at=@At("HEAD"),argsOnly=true)
        private Vec3 naturality$softCeiling(Vec3 movement) {
            var e=naturality$self();
            if(!e.isLocalInstanceAuthoritative())return movement;
            double y=EndGravity.softenUpwardMotion(e,movement.y);
            if(y==movement.y)return movement;
            var v=e.getDeltaMovement();e.setDeltaMovement(v.x,Math.min(v.y,y),v.z);
            return new Vec3(movement.x,y,movement.z);
        }
    }

    @Mixin(LandRandomPos.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityLandTargetMixin {
        @Inject(method="movePosUpOutOfSolid",at=@At("HEAD"),cancellable=true)
        private static void naturality$keepCeilingTarget(PathfinderMob mob,BlockPos pos,CallbackInfoReturnable<BlockPos> cir) {
            if(mob.getNavigation() instanceof CeilingPathNavigation navigation)
                cir.setReturnValue(navigation.isStableDestination(pos)?pos:null);
        }
    }

    @Mixin(LivingEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityLivingMixin {
        // Run vanilla aerodynamics in the gravity-local frame, then return the
        // result to world space. Lift, pitch and dive conversion all share a frame.
        @WrapMethod(method="updateFallFlyingMovement")
        private Vec3 naturality$invertedGlide(Vec3 movement,Operation<Vec3> original) {
            if(!EndGravity.inverted((LivingEntity)(Object)this))return original.call(movement);
            var local=original.call(new Vec3(movement.x,-movement.y,movement.z));
            return new Vec3(local.x,-local.y,local.z);
        }
        @ModifyExpressionValue(method="updateFallFlyingMovement",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
        private Vec3 naturality$glideLook(Vec3 look) {
            return EndGravity.inverted((LivingEntity)(Object)this)?new Vec3(look.x,-look.y,look.z):look;
        }
        @ModifyExpressionValue(method="updateFallFlyingMovement",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
        private float naturality$glidePitch(float pitch) {
            return EndGravity.inverted((LivingEntity)(Object)this)?-pitch:pitch;
        }
        @ModifyExpressionValue(method="updateFallFlyingMovement",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getEffectiveGravity()D"))
        private double naturality$glideGravity(double gravity) {
            return EndGravity.inverted((LivingEntity)(Object)this)?-gravity:gravity;
        }
        @ModifyExpressionValue(method="checkFallDamage",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getY()D"))
        private double naturality$landingAtFeet(double y) {
            var entity=(LivingEntity)(Object)this;
            return EndGravity.inverted(entity)?entity.getBoundingBox().maxY-.01:y;
        }
        @Shadow protected abstract float getJumpPower();
        @Inject(method="jumpFromGround",at=@At("HEAD"),cancellable=true)
        private void naturality$ceilingJump(CallbackInfo ci) {
            var e=(LivingEntity)(Object)this;if(!EndGravity.inverted(e))return;
            var v=e.getDeltaMovement();
            e.setDeltaMovement(v.x,Math.min(-getJumpPower(),v.y),v.z);
            if(e.isSprinting()) {
                double angle=Math.toRadians(e.getYRot());
                e.addDeltaMovement(new Vec3(-Math.sin(angle)*.2,0,Math.cos(angle)*.2));
            }
            e.needsSync=true;
            ci.cancel();
        }
        @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
        private Vec3 naturality$controls(Vec3 input) {
            var e=(LivingEntity)(Object)this;
            return e instanceof Player && EndGravity.inverted(e)?new Vec3(-input.x,input.y,input.z):input;
        }
        @Inject(method="getEffectiveGravity",at=@At("RETURN"),cancellable=true)
        private void naturality$slowFalling(CallbackInfoReturnable<Double> cir) {
            var e=(LivingEntity)(Object)this;
            if(EndGravity.inverted(e) && e.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                    && e.getDeltaMovement().y>=0) cir.setReturnValue(Math.max(e.getGravity(),-.01));
        }
    }

    @Mixin(Mob.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityMobNavigationMixin {
        @SuppressWarnings("null") @Shadow protected PathNavigation navigation;
        @Unique private @org.jspecify.annotations.Nullable GroundPathNavigation naturality$uprightNavigation;

        @Inject(method="serverAiStep",at=@At("HEAD"))
        private void naturality$changeWalkingSurface(CallbackInfo ci) {
            var mob=(Mob)(Object)this;
            var upright = naturality$uprightNavigation;
            if(EndGravity.inverted(mob)) {
                if(navigation instanceof GroundPathNavigation ground && !(ground instanceof CeilingPathNavigation)) {
                    naturality$uprightNavigation=ground;
                    ground.stop();
                    navigation=new CeilingPathNavigation(mob,mob.level(),ground);
                    naturality$clearOldSteering(mob);
                }
            } else if(upright!=null) {
                if(navigation instanceof CeilingPathNavigation) {
                    navigation.stop();
                    navigation=upright;
                    naturality$clearOldSteering(mob);
                }
                naturality$uprightNavigation=null;
            }
        }
        @Unique private void naturality$clearOldSteering(Mob mob) {
            mob.getMoveControl().setWait();
            mob.setJumping(false);
            mob.getBrain().eraseMemory(MemoryModuleType.PATH);
        }
    }

    @Mixin(MoveControl.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityMoveControlMixin {
        @SuppressWarnings("null") @Shadow @Final protected Mob mob;
        @ModifyVariable(method="tick",at=@At("STORE"),name="yd")
        private double naturality$jumpAwayFromCeiling(double delta) {
            return mob.getNavigation() instanceof CeilingPathNavigation?-delta:delta;
        }
    }

    @Mixin(Player.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityPlayerMixin implements naturality.systems.gravity.EndGravity.GravityPoseAccess {
        @org.spongepowered.asm.mixin.gen.Invoker("updatePlayerPose")
        public abstract void naturality$updateGravityPose();
        @WrapMethod(method="maybeBackOffFromEdge")
        private Vec3 naturality$ceilingEdge(Vec3 delta,
                MoverType type,
                Operation<Vec3> original) {
            if(!EndGravity.inverted((Player)(Object)this))return original.call(delta,type);
            var clipped=original.call(new Vec3(delta.x,-delta.y,delta.z),type);
            return new Vec3(clipped.x,-clipped.y,clipped.z);
        }
        @ModifyArg(method="canFallAtLeast",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"),index=1)
        private AABB naturality$ceilingEdgeSupport(AABB area) {
            var p=(Player)(Object)this;
            if(!EndGravity.inverted(p))return area;
            double sum=p.getBoundingBox().minY+p.getBoundingBox().maxY;
            return new AABB(area.minX,sum-area.maxY,area.minZ,area.maxX,sum-area.minY,area.maxZ);
        }
        @Inject(method="canPlayerFitWithinBlocksAndEntitiesWhen",at=@At("HEAD"),cancellable=true)
        private void naturality$ceilingPoseFits(Pose pose, CallbackInfoReturnable<Boolean> cir) {
            var player=(Player)(Object)this;
            if(!EndGravity.inverted(player) || naturality.systems.rotation.PlayerRotation.active(player))return;
            var dimensions=player.getDimensions(pose);
            // The feet are on the upper face of the box. Grow toward the head,
            // below the ceiling, rather than into the supporting block.
            var box=dimensions.makeBoundingBox(player.getX(),
                player.getBoundingBox().maxY-dimensions.height(),player.getZ());
            cir.setReturnValue(player.level().noCollision(player,box.deflate(1.0e-7)));
        }
    }

    @Mixin(RandomPos.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityRandomPosMixin {
        @Inject(method="generateRandomPosTowardDirection",at=@At("RETURN"),cancellable=true)
        private static void naturality$ceilingWanderTarget(PathfinderMob mob,double range,RandomSource random,
                BlockPos direction,CallbackInfoReturnable<BlockPos> cir) {
            if(!(mob.getNavigation() instanceof CeilingPathNavigation))return;
            var surface=CeilingPathNavigation.surfaceAt(mob,cir.getReturnValue(),Math.min(8,Math.abs(direction.getY())+1));
            if(surface!=null)cir.setReturnValue(BlockPos.containing(surface.position(mob).add(0,1e-5,0)));
        }
    }

    @Mixin(ServerGamePacketListenerImpl.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EndGravityServerMovementMixin implements naturality.systems.gravity.EndGravity.GravityMovementAccess {
        @Shadow private double firstGoodY;
        @Shadow private double lastGoodY;
        public void naturality$poseShift(double shift) {firstGoodY+=shift;lastGoodY+=shift;}
        @Inject(method={"handlePlayerInput","handlePlayerCommand"},at=@At("TAIL"))
        private void naturality$poseBeforeMovement(CallbackInfo ci) {
            if(EndGravity.inverted(player))((naturality.systems.gravity.EndGravity.GravityPoseAccess)player).naturality$updateGravityPose();
        }
        @SuppressWarnings("null") @Shadow public ServerPlayer player;
        @Shadow private boolean clientIsFloating;
        @Inject(method="tick",at=@At("HEAD"))
        private void naturality$allowedGravityFlight(CallbackInfo ci) {
            if(EndGravity.affected(player) && EndGravity.inversion(player,false)>0)clientIsFloating=false;
        }
        @ModifyVariable(method="handlePlayerPositionChange",at=@At("STORE"),name="movedUpwards")
        private boolean naturality$movingAwayFromSupport(boolean upwards,@Local(name="yDist") double dy) {
            // Both jump recognition and fall-counter reset must use gravity-local up.
            return EndGravity.inverted(player)?dy<0:upwards;
        }
    }
}
