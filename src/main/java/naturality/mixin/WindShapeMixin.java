package naturality.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.weather.WindShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class WindShapeMixin {
    @WrapMethod(method={"getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"})
    private VoxelShape naturality$wind(BlockGetter level,BlockPos pos,CollisionContext context,Operation<VoxelShape> original) {
        var shape=WindShapes.raw(() -> original.call(level,pos,context));
        return WindShapes.move(level,pos,(BlockState)(Object)this,shape);
    }
    @WrapMethod(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    private VoxelShape naturality$cachedWind(BlockGetter level,BlockPos pos,Operation<VoxelShape> original) {
        var shape=WindShapes.raw(() -> original.call(level,pos));
        return WindShapes.move(level,pos,(BlockState)(Object)this,shape);
    }
}
