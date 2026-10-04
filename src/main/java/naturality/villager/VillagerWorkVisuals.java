package naturality.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface VillagerWorkVisuals {
    @Nullable BlockPos naturality$castTarget();
    @Nullable Vec3 naturality$renderedRodTip();
    void naturality$setRenderedRodTip(@Nullable Vec3 tip);
    void naturality$setCastTarget(@Nullable BlockPos pos);
    boolean naturality$isDisplayingTrade();
    void naturality$setDisplayingTrade(boolean value);
}
