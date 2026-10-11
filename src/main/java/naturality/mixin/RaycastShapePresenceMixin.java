package naturality.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.util.RaycastShapePresence;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockGetter.class)
public interface RaycastShapePresenceMixin {
    @WrapMethod(method="clip")
    private BlockHitResult naturality$shapePresence(ClipContext context,Operation<BlockHitResult> original) {
        var scope=RaycastShapePresence.begin((BlockGetter)this);
        try {return original.call(context);} finally {RaycastShapePresence.end(scope);}
    }
}
