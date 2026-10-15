package naturality.portals.nether.travel.mixins;

import naturality.portals.nether.travel.PortalCrossing;
import naturality.portals.nether.travel.PortalCrossing.PortalCrossingAccess;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class TravelMixins {
    private TravelMixins() {}

    @Mixin(Entity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EntityPortalCrossingMixin implements PortalCrossingAccess {
        @Unique private @org.jspecify.annotations.Nullable PortalCrossing naturality$crossing;
        public @org.jspecify.annotations.Nullable PortalCrossing naturality$getCrossing(){return naturality$crossing;}
        public void naturality$setCrossing(@org.jspecify.annotations.Nullable PortalCrossing crossing){naturality$crossing=crossing;}
        @Inject(method="baseTick",at=@At("HEAD"))
        private void naturality$crossingTick(CallbackInfo ci){PortalCrossing.tick((Entity)(Object)this);}
        @Inject(method="push(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true)
        private void naturality$hiddenPush(Entity other,CallbackInfo ci) {
            Entity self=(Entity)(Object)this;
            if(PortalCrossing.separated(self,other) || PortalCrossing.separated(other,self)) ci.cancel();
        }
    }

    @Mixin(BlockCollisions.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalCollisionMixin {
        @SuppressWarnings("null") @Shadow @Final private CollisionContext context;
        @SuppressWarnings("null") @Shadow @Final private BlockPos.MutableBlockPos pos;
        @ModifyExpressionValue(method="computeNext",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
        private VoxelShape naturality$visibleCollision(VoxelShape shape) {
            if(!(context instanceof EntityCollisionContext ec)) return shape;
            var entity=ec.getEntity();
            if (entity == null) return shape;
            var crossing=PortalCrossing.get(entity);
            return crossing==null || !crossing.valid(entity)?shape:
                crossing.visibleCollision(shape.move(pos)).move(-pos.getX(),-pos.getY(),-pos.getZ());
        }
    }

    @Mixin(PortalProcessor.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalCrossingTeleportMixin {
        @Inject(method="getPortalDestination",at=@At("RETURN"),cancellable=true)
        private void naturality$arrival(ServerLevel level,Entity entity,
                CallbackInfoReturnable<net.minecraft.world.level.portal.TeleportTransition> ci) {
            var crossing=PortalCrossing.get(entity);
            if(portal==Blocks.NETHER_PORTAL && crossing!=null && ci.getReturnValue()!=null)
                ci.setReturnValue(naturality.portals.nether.travel.PortalCrossing.PortalArrival.prepare(ci.getReturnValue(),crossing,entity));
        }
        @SuppressWarnings("null") @Shadow @Final private Portal portal;
        @Shadow private int portalTime;
        @Shadow private boolean insidePortalThisTick;
        @Inject(method="processPortalTeleportation",at=@At("HEAD"),cancellable=true)
        private void naturality$crossing(ServerLevel level,Entity entity,boolean allowed,CallbackInfoReturnable<Boolean> ci){
            if(portal!=Blocks.NETHER_PORTAL || !naturality.config.gameplay.GameplaySettings.physicalPortalEntry(level)) return;
            PortalCrossing c=PortalCrossing.get(entity);
            insidePortalThisTick=false;
            // Voxel contact can create a processor before the hitbox reaches the slab.
            // Never fall back to vanilla's zero-delay travel for mobs/creative players.
            if(c==null) {
                portalTime=0;
                ci.setReturnValue(false);
                return;
            }
            portalTime++;
            ci.setReturnValue(allowed && c.valid(entity) && c.fullyCrossed(entity.getBoundingBox()));
        }
    }

    @Mixin(EntityGetter.class)
    @org.jspecify.annotations.NullMarked
    public interface PortalEntityCollisionMixin {
        @ModifyExpressionValue(method="getEntityCollisions",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
        private VoxelShape naturality$visibleEntity(VoxelShape shape,
            @Local(argsOnly=true) Entity source, @Local(ordinal=1) Entity other) {
            var crossing=PortalCrossing.get(other);
            if(crossing!=null && crossing.valid(other)) shape=crossing.visibleCollision(shape);
            crossing=source==null?null:PortalCrossing.get(source);
            return crossing!=null && crossing.valid(source)?crossing.visibleCollision(shape):shape;
        }
    }
}
