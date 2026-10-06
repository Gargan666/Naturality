package naturality.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import naturality.villager.ProfessionWork;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerGoalPackages.class)
public abstract class VillagerWorkMixin {
    @Inject(method = "getCorePackage", at = @At("RETURN"), cancellable = true)
    private static void naturality$gateCore(Holder<VillagerProfession> profession, float speed,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
        var builder = ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder();
        builder.add(Pair.of(0, naturality.villager.VillagerGates.create()));
        builder.addAll(cir.getReturnValue());
        cir.setReturnValue(builder.build());
    }
    @Inject(method = "getWorkPackage", at = @At("HEAD"), cancellable = true)
    private static void naturality$work(Holder<VillagerProfession> profession, float speed,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
        boolean farmer = profession.is(VillagerProfession.FARMER);
        if (profession.is(VillagerProfession.SHEPHERD) || profession.is(VillagerProfession.TOOLSMITH)
                || profession.is(VillagerProfession.WEAPONSMITH) || profession.is(VillagerProfession.ARMORER)) {
            cir.setReturnValue(ImmutableList.of(Pair.of(5, new naturality.villager.AnimalCareWork(profession.is(VillagerProfession.SHEPHERD))),
                Pair.of(99, UpdateActivityFromSchedule.create())));
            return;
        }
        if (profession.is(VillagerProfession.BUTCHER)) {
            cir.setReturnValue(ImmutableList.of(Pair.of(5, new naturality.villager.ButcherWork()),
                Pair.of(99, UpdateActivityFromSchedule.create())));
            return;
        }
        if (farmer || profession.is(VillagerProfession.FISHERMAN)) {
            cir.setReturnValue(ImmutableList.of(Pair.of(5, new ProfessionWork(farmer)),
                Pair.of(99, UpdateActivityFromSchedule.create())));
        }
    }
}
