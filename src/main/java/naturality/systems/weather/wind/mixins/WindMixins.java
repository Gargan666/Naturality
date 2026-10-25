package naturality.systems.weather.wind.mixins;

import java.util.Optional;
import naturality.systems.weather.wind.WindPush.WindShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.VineBlock;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import naturality.systems.weather.wind.WindPush;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class WindMixins {
    private WindMixins() {}

    @Mixin(LivingEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WindClimbingMixin {
        @SuppressWarnings("null") @Shadow private Optional<BlockPos> lastClimbablePos;
        @Inject(method="onClimbable",at=@At("RETURN"),cancellable=true)
        private void naturality$movingVines(CallbackInfoReturnable<Boolean> cir) {
            var entity=(LivingEntity)(Object)this;
            if(entity.isSpectator() || entity.isFallFlying() || !WindShapes.active(entity.level()))return;
            boolean originalVine=entity.getInBlockState().getBlock() instanceof VineBlock;
            if(cir.getReturnValueZ() && !originalVine)return;
            if(originalVine && WindShapes.offset(entity.level(),entity.blockPosition(),entity.getInBlockState()).equals(net.minecraft.world.phys.Vec3.ZERO))return;
            var box=entity.getBoundingBox();
            for(var pos:BlockPos.betweenClosed((int)Math.floor(box.minX)-1,(int)Math.floor(box.minY),(int)Math.floor(box.minZ)-1,
                    (int)Math.floor(box.maxX)+1,(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ)+1)) {
                var state=entity.level().getBlockState(pos);
                if(!(state.getBlock() instanceof VineBlock) || WindShapes.offset(entity.level(),pos,state).equals(net.minecraft.world.phys.Vec3.ZERO))continue;
                for(var part:state.getShape(entity.level(),pos).toAabbs())if(part.move(pos).inflate(.001).intersects(box)) {
                    lastClimbablePos=Optional.of(pos.immutable());cir.setReturnValue(true);return;
                }
            }
            if(originalVine) {lastClimbablePos=Optional.empty();cir.setReturnValue(false);}
        }
    }

    @Mixin(BlockCollisions.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WindCollisionScanMixin {
        @SuppressWarnings("null") @Shadow @Final private CollisionGetter collisionGetter;
        @Unique private boolean naturality$windActive;
        @Inject(method="computeNext", at=@At("HEAD"))
        private void naturality$sampleWind(CallbackInfoReturnable<Object> cir) {
            // One synchronous iterator step cannot advance weather. Resample on
            // each step so iterators retained across ticks still see live state.
            naturality$windActive = WindShapes.active(collisionGetter);
        }
        // Wind offsets remain under one block. Visit the iterator's existing outer
        // ring, including diagonal cells, rather than relying on static shape flags.
        @ModifyExpressionValue(method="computeNext",at=@At(value="INVOKE",target="Lnet/minecraft/core/Cursor3D;getNextType()I"))
        private int naturality$includeWindNeighbors(int type) { return naturality$windActive?0:type; }
    }

    @Mixin(BlockGetter.class)
    @org.jspecify.annotations.NullMarked
    public interface WindPickingMixin {

        @WrapMethod(method="clip")
        private BlockHitResult naturality$context(ClipContext context,Operation<BlockHitResult> original) {
            if(!WindShapes.active((BlockGetter)this))return original.call(context);
            var previous=WindShapes.PICK.get();WindShapes.PICK.set(context);
            try {return original.call(context);} finally {if(previous==null)WindShapes.PICK.remove();else WindShapes.PICK.set(previous);}
        }
        @WrapMethod(method="clipWithInteractionOverride")
        private @org.jspecify.annotations.Nullable BlockHitResult naturality$neighbors(Vec3 from,Vec3 to,BlockPos pos,VoxelShape shape,BlockState state,
                Operation<BlockHitResult> original) {
            var best=original.call(from,to,pos,shape,state);
            var context=WindShapes.PICK.get();if(context==null)return best;
            var cell=new AABB(pos).inflate(1e-6);
            if(best!=null && WindShapes.eligible(state) && !cell.contains(best.getLocation()))best=null;
            double distance=best==null?Double.POSITIVE_INFINITY:from.distanceToSqr(best.getLocation());
            BlockGetter level=(BlockGetter)this;
            if(!naturality.api.geometry.RaycastShapePresence.mayContain(level,naturality.api.geometry.RaycastShapePresence.WIND,
                    pos.getX()-1,pos.getY(),pos.getZ()-1,pos.getX()+1,pos.getY(),pos.getZ()+1))return best;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                if(dx==0 && dz==0)continue;
                var owner=pos.offset(dx,0,dz);var candidate=level.getBlockState(owner);
                if(!WindShapes.eligible(candidate))continue;
                var candidateShape=context.getBlockShape(candidate,level,owner);
                if(!naturality.api.geometry.RaycastShapePresence.mayIntersect(candidateShape,from,to,owner))continue;
                var hit=candidateShape.clip(from,to,owner);
                if(hit!=null && cell.contains(hit.getLocation()) && from.distanceToSqr(hit.getLocation())<distance) {
                    best=hit;distance=from.distanceToSqr(hit.getLocation());
                }
            }
            return best;
        }
    }

    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WindPushMixin {
        @Inject(method="baseTick",at=@At("HEAD"))
        private void naturality$pushFromFoliage(CallbackInfo ci) {
            if((Object)this instanceof Player player)WindPush.resolve(player);
        }
        @Inject(method="move",at=@At("HEAD"),cancellable=true)
        private void naturality$preventWalkingThroughFoliage(CallbackInfo ci) {
            if((Object)this instanceof Player player && WindPush.resolve(player))ci.cancel();
        }
    }

    @Mixin(BlockBehaviour.BlockStateBase.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class WindShapeMixin {
        @WrapMethod(method="getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
        private VoxelShape naturality$windOutline(BlockGetter level,BlockPos pos,CollisionContext context,Operation<VoxelShape> original) {
            var args=WindShapes.beginShapeCall(level,pos,context);
            VoxelShape shape;
            try {shape=original.call(args);} finally {WindShapes.endShapeCall(args);}
            return WindShapes.move(level,pos,(BlockState)(Object)this,shape);
        }
        @WrapMethod(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
        private VoxelShape naturality$windCollision(BlockGetter level,BlockPos pos,CollisionContext context,Operation<VoxelShape> original) {
            var args=WindShapes.beginShapeCall(level,pos,context);
            VoxelShape shape;
            try {shape=original.call(args);} finally {WindShapes.endShapeCall(args);}
            return WindShapes.move(level,pos,(BlockState)(Object)this,shape);
        }
        @WrapMethod(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
        private VoxelShape naturality$cachedWind(BlockGetter level,BlockPos pos,Operation<VoxelShape> original) {
            var args=WindShapes.beginShapeCall(level,pos);
            VoxelShape shape;
            try {shape=original.call(args);} finally {WindShapes.endShapeCall(args);}
            return WindShapes.move(level,pos,(BlockState)(Object)this,shape);
        }
    }
}
