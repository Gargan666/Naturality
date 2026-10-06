package naturality.mixin;

import naturality.weather.EndGravity;
import naturality.weather.EndGravityTeleport;
import naturality.NaturalityEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Enderman.class)
public abstract class EndGravityEndermanMixin {
    @Unique private boolean naturality$wasInverted;
    @Unique private boolean naturality$wasTargetingUnderside;
    @Unique private Vec3 naturality$brace;
    @Unique private int naturality$retry;
    @Inject(method="aiStep",at=@At("HEAD"))
    private void naturality$seekFooting(CallbackInfo ci) {
        var mob=(Enderman)(Object)this;if(!(mob.level() instanceof ServerLevel level))return;
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
        if(naturality$brace==null)return;
        var mob=(Enderman)(Object)this;
        var support=BlockPos.containing(naturality$brace.x,naturality$brace.y+mob.getBbHeight()+1.0e-5,naturality$brace.z);
        var box=mob.getBoundingBox().move(naturality$brace.subtract(mob.position()));
        // Only hold successful, still-valid footing during the first half of
        // the turn. Never undo a different teleport or anchor to a removed block.
        if(mob.position().distanceToSqr(naturality$brace)>1
                || !mob.level().getBlockState(support).isFaceSturdy(mob.level(),support,Direction.DOWN)
                || !mob.level().noCollision(mob,box)) {
            naturality$brace=null;return;
        }
        mob.setPos(naturality$brace);mob.setDeltaMovement(Vec3.ZERO);
        mob.resetFallDistance();mob.setOnGround(true);mob.getNavigation().stop();
    }
    @Inject(method="teleport(DDD)Z",at=@At("HEAD"),cancellable=true)
    private void naturality$ceilingTeleport(double x,double y,double z,CallbackInfoReturnable<Boolean> cir) {
        var mob=(Enderman)(Object)this;
        if(EndGravity.affected(mob) && mob.level() instanceof ServerLevel level
                && (EndGravity.inverted(mob) || mob.hasEffect(NaturalityEffects.DISTORTION))) {
            boolean escaped=EndGravityTeleport.toUnderside(mob,x,z);
            if(escaped)naturality$brace=EndGravity.inverted(mob)?null:mob.position();
            cir.setReturnValue(escaped);
        }
    }
}
