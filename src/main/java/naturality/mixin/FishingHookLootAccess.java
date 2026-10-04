package naturality.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FishingHook.class)
public interface FishingHookLootAccess {
    @Invoker("calculateOpenWater") boolean naturality$calculateOpenWater(BlockPos pos);
    @Accessor("openWater") void naturality$setOpenWater(boolean open);
}
