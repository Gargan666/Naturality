package naturality.mixin;
import java.util.Optional;
import naturality.weather.WindShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.VineBlock;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class WindClimbingMixin {
    @Shadow private Optional<BlockPos> lastClimbablePos;
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
