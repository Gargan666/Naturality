package naturality.portals.nether.opening.mixins;

import naturality.portals.nether.opening.PortalOpeningManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class OpeningMixins {
    private OpeningMixins() {}

    @Mixin(FireBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FadingFireMixin {
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void naturality$holdFlame(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
            if (PortalOpeningManager.fadingFire(level, pos)) {
                level.scheduleTick(pos, state.getBlock(), 10);
                ci.cancel();
            }
        }
    }

    @Mixin(NetherPortalBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class OpeningPortalTravelMixin {
        @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
        private void naturality$waitForOpening(BlockState state, Level level, BlockPos pos, Entity entity,
                InsideBlockEffectApplier effects, boolean precise, CallbackInfo ci) {
            if (PortalOpeningManager.isOpening(level, pos)) { ci.cancel(); return; }
            naturality.portals.nether.travel.PortalCrossing.enter(entity, pos, state.getValue(NetherPortalBlock.AXIS));
        }
    }

    @Mixin(BaseFireBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalIgnitionMixin {
        @WrapOperation(method = "onPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/PortalShape;createPortalBlocks(Lnet/minecraft/world/level/LevelAccessor;)V"))
        private void naturality$delayIgnition(PortalShape shape, LevelAccessor level, Operation<Void> original) {
            if (level instanceof ServerLevel server && PortalOpeningManager.begin(server, shape)) return;
            original.call(shape, level);
        }
    }

    @Mixin(PortalShape.class)
    @org.jspecify.annotations.NullMarked
    public interface PortalShapeAccessor {
        @Accessor("bottomLeft") BlockPos naturality$bottomLeft();
        @Accessor("width") int naturality$width();
        @Accessor("height") int naturality$height();
        @Accessor("axis") Direction.Axis naturality$axis();
        @Accessor("rightDir") Direction naturality$rightDir();
    }
}
