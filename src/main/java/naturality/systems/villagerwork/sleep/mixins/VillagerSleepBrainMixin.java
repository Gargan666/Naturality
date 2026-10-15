package naturality.systems.villagerwork.sleep.mixins;

import java.util.Set;
import naturality.systems.reputation.Reputation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Sleep recovery pauses scheduled jobs, never perception or door/panic core tasks. */
@Mixin(Brain.class)
@org.jspecify.annotations.NullMarked
public abstract class VillagerSleepBrainMixin {
    @WrapOperation(method = "startEachNonRunningBehavior", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean naturality$sleepActivities(Set<?> activities, Object activity, Operation<Boolean> original,
            ServerLevel level, LivingEntity body) {
        if (body instanceof Villager villager && Reputation.state(villager).sleepCoreOnly)
            return (activity == Activity.CORE || activity == Activity.PANIC) && original.call(activities, activity);
        return original.call(activities, activity);
    }
}
