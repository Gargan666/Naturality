package naturality.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.portal.PortalOpeningManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BaseFireBlock.class)
public abstract class PortalIgnitionMixin {
    @WrapOperation(method = "onPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/PortalShape;createPortalBlocks(Lnet/minecraft/world/level/LevelAccessor;)V"))
    private void naturality$delayIgnition(PortalShape shape, LevelAccessor level, Operation<Void> original) {
        if (level instanceof ServerLevel server && PortalOpeningManager.begin(server, shape)) return;
        original.call(shape, level);
    }
}
