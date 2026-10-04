package naturality.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.weather.WindShapes;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public abstract class WindCollisionScanMixin {
    @Shadow @Final private CollisionGetter collisionGetter;
    // Wind offsets remain under one block. Visit the iterator's existing outer
    // ring, including diagonal cells, rather than relying on static shape flags.
    @ModifyExpressionValue(method="computeNext",at=@At(value="INVOKE",target="Lnet/minecraft/core/Cursor3D;getNextType()I"))
    private int naturality$includeWindNeighbors(int type) { return WindShapes.active(collisionGetter)?0:type; }
}
