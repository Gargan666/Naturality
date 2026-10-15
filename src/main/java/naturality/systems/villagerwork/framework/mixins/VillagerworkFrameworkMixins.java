package naturality.systems.villagerwork.framework.mixins;

import naturality.systems.villagerwork.framework.VillagerWork.DoorwayTraffic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import naturality.systems.villagerwork.framework.VillagerWork.VillagerWorkVisuals;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.item.ItemStack;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class VillagerworkFrameworkMixins {
    private VillagerworkFrameworkMixins() {}

    @Mixin(MoveControl.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerDoorwayTrafficMixin {
        @SuppressWarnings("null") @Shadow @Final protected Mob mob;
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void naturality$doorQueue(CallbackInfo ci) {
            if (mob instanceof Villager body && mob.level() instanceof ServerLevel level && DoorwayTraffic.yield(level, body)) ci.cancel();
        }
    }

    /** A workstation is an interaction target, never a footstep in its owner's path. */
    @Mixin(WalkNodeEvaluator.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerStationPathMixin {
        @Inject(method = "getPathTypeOfMob", at = @At("HEAD"), cancellable = true)
        private void naturality$avoidOwnStation(PathfindingContext context, int x, int y, int z, Mob mob,
                CallbackInfoReturnable<PathType> cir) {
            if (!(mob instanceof Villager villager)) return;
            var job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (job.isPresent() && job.get().dimension().equals(villager.level().dimension())) {
                var pos = job.get().pos();
                if (pos.getX() == x && pos.getY() + 1 == y && pos.getZ() == z)
                    cir.setReturnValue(PathType.BLOCKED);
            }
        }
    }

    @Mixin(ShowTradesToPlayer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerTradeDisplayMixin {
        @Inject(method = "displayAsHeldItem", at = @At("TAIL"))
        private static void naturality$display(Villager body, ItemStack stack, CallbackInfo ci) {
            ((VillagerWorkVisuals)body).naturality$setDisplayingTrade(true);
        }
        @Inject(method = "clearHeldItem", at = @At("TAIL"))
        private static void naturality$clear(Villager body, CallbackInfo ci) {
            ((VillagerWorkVisuals)body).naturality$setDisplayingTrade(false);
        }
    }

    @Mixin(VillagerGoalPackages.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerWorkMixin {
        @Inject(method = "getCorePackage", at = @At("RETURN"), cancellable = true)
        private static void naturality$gateCore(Holder<VillagerProfession> profession, float speed,
                CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
            if (!naturality.config.startup.FeatureConfig.Feature.VILLAGER_GATES.enabled()) return;
            cir.setReturnValue(naturality.systems.villagerwork.framework.VillagerWork.core(cir.getReturnValue()));
        }
        @Inject(method = "getWorkPackage", at = @At("HEAD"), cancellable = true)
        private static void naturality$work(Holder<VillagerProfession> profession, float speed,
                CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
            var work = naturality.systems.villagerwork.framework.VillagerWork.work(profession);
            if (work != null) cir.setReturnValue(work);
        }
    }

    @Mixin(Villager.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class VillagerWorkVisualsMixin implements VillagerWorkVisuals {
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
}
