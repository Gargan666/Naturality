package naturality.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.weather.WindShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.*;

@Mixin(BlockGetter.class)
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
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            if(dx==0 && dz==0)continue;
            var owner=pos.offset(dx,0,dz);var candidate=level.getBlockState(owner);
            if(!WindShapes.eligible(candidate))continue;
            var hit=context.getBlockShape(candidate,level,owner).clip(from,to,owner);
            if(hit!=null && cell.contains(hit.getLocation()) && from.distanceToSqr(hit.getLocation())<distance) {
                best=hit;distance=from.distanceToSqr(hit.getLocation());
            }
        }
        return best;
    }
}
