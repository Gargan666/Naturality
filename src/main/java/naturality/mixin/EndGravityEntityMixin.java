package naturality.mixin;

import naturality.weather.EndGravity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
public abstract class EndGravityEntityMixin {
    @Unique private boolean naturality$wasInverted;
    @Shadow public java.util.Optional<BlockPos> mainSupportingBlockPos;
    @Unique private Entity naturality$self() { return (Entity)(Object)this; }
    @WrapMethod(method="refreshDimensions")
    private void naturality$keepCeilingFeet(Operation<Void> original) {
        var e=naturality$self();
        boolean anchor=e instanceof net.minecraft.world.entity.player.Player && EndGravity.inverted(e);
        double top=e.getBoundingBox().maxY;
        original.call();
        if(anchor)e.setPos(e.getX(),top-e.getBbHeight(),e.getZ());
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
        var e=naturality$self(); if(EndGravity.affected(e))cir.setReturnValue(e.getY()+EndGravity.eyeHeight(e,e.getEyeHeight(),false));
    }
    @ModifyExpressionValue(method="getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
    private float naturality$partialEye(float height) { return EndGravity.eyeHeight(naturality$self(),height,true); }
    @ModifyVariable(method="turn",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private double naturality$mouseYaw(double delta) { return EndGravity.inverted(naturality$self())?-delta:delta; }
    @ModifyVariable(method="turn",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private double naturality$mousePitch(double delta) { return EndGravity.inverted(naturality$self())?-delta:delta; }
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
