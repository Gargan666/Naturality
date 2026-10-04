package naturality.mixin;

import java.util.Optional;
import naturality.villager.VillagerWorkVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerWorkVisualsMixin implements VillagerWorkVisuals {
    @Unique private static final EntityDataAccessor<Optional<BlockPos>> NATURALITY_CAST_TARGET =
        SynchedEntityData.defineId(Villager.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    @Unique private static final EntityDataAccessor<Boolean> NATURALITY_DISPLAYING_TRADE =
        SynchedEntityData.defineId(Villager.class, EntityDataSerializers.BOOLEAN);
    @Unique private @Nullable Vec3 naturality$renderedRodTip;
    @Unique private int naturality$renderedRodTipTick;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void naturality$defineVisuals(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(NATURALITY_CAST_TARGET, Optional.empty());
        builder.define(NATURALITY_DISPLAYING_TRADE, false);
    }

    @Override public @Nullable BlockPos naturality$castTarget() {
        return ((Villager)(Object)this).getEntityData().get(NATURALITY_CAST_TARGET).orElse(null);
    }
    @Override public @Nullable Vec3 naturality$renderedRodTip() {
        return ((Villager)(Object)this).tickCount - naturality$renderedRodTipTick <= 2 ? naturality$renderedRodTip : null;
    }
    @Override public void naturality$setRenderedRodTip(@Nullable Vec3 tip) {
        naturality$renderedRodTip = tip;
        naturality$renderedRodTipTick = ((Villager)(Object)this).tickCount;
    }
    @Override public void naturality$setCastTarget(@Nullable BlockPos pos) {
        ((Villager)(Object)this).getEntityData().set(NATURALITY_CAST_TARGET, Optional.ofNullable(pos));
    }
    @Override public boolean naturality$isDisplayingTrade() {
        return ((Villager)(Object)this).getEntityData().get(NATURALITY_DISPLAYING_TRADE);
    }
    @Override public void naturality$setDisplayingTrade(boolean value) {
        ((Villager)(Object)this).getEntityData().set(NATURALITY_DISPLAYING_TRADE, value);
    }
}
