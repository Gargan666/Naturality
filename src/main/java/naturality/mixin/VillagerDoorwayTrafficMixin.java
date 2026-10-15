package naturality.mixin;

import naturality.villager.DoorwayTraffic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MoveControl.class)
public abstract class VillagerDoorwayTrafficMixin {
    @SuppressWarnings("null") @Shadow @Final protected Mob mob;
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void naturality$doorQueue(CallbackInfo ci) {
        if (mob instanceof Villager body && mob.level() instanceof ServerLevel level && DoorwayTraffic.yield(level, body)) ci.cancel();
    }
}
