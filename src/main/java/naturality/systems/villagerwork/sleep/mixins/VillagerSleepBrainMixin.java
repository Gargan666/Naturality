package naturality.systems.villagerwork.sleep.mixins;

import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import naturality.systems.reputation.Reputation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Sleep recovery pauses scheduled jobs, never perception or door/panic core tasks. */
// Lithium overwrites task launch at priority 1000. Mixin permits injection into
// that merged method only from a higher-priority mixin; leave its launch hooks intact.
@Mixin(value = Brain.class, priority = 1100)
@org.jspecify.annotations.NullMarked
public abstract class VillagerSleepBrainMixin<E extends LivingEntity> {
    @SuppressWarnings("null") // Final fields are initialized by Brain, not the mixin constructor.
    @Shadow @Final private Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority;
    @SuppressWarnings("null") // Final fields are initialized by Brain, not the mixin constructor.
    @Shadow @Final private Set<Activity> activeActivities;

    // Exactly one supported task source must exist: native activity membership or
    // Lithium's cached task list. Individual optional sites must not hide a missing guard.
    @Group(name = "naturality$sleepTaskSource", min = 1, max = 1)
    @WrapOperation(method = "startEachNonRunningBehavior", require = 0,
        at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean naturality$sleepActivities(Set<?> activities, Object activity, Operation<Boolean> original,
            ServerLevel level, LivingEntity body) {
        if (body instanceof Villager villager && Reputation.state(villager).sleepCoreOnly)
            return (activity == Activity.CORE || activity == Activity.PANIC) && original.call(activities, activity);
        return original.call(activities, activity);
    }

    @Group(name = "naturality$sleepTaskSource", min = 1, max = 1)
    @WrapOperation(method = "startEachNonRunningBehavior", require = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;getPossibleTasks()Ljava/util/ArrayList;", remap = false))
    private ArrayList<BehaviorControl<? super E>> naturality$sleepLithiumTasks(Brain<E> brain,
            Operation<ArrayList<BehaviorControl<? super E>>> original, ServerLevel level, E body) {
        if (!(body instanceof Villager villager) || !Reputation.state(villager).sleepCoreOnly)
            return original.call(brain);
        // Rebuild only the recovery-sleep view, in the same priority/activity/task
        // order. Filtering the flat cached list by task identity would mishandle a
        // behavior shared by CORE and another activity. Never mutate Lithium's cache.
        var tasks = new ArrayList<BehaviorControl<? super E>>();
        for (var byActivity : availableBehaviorsByPriority.values()) {
            for (var entry : byActivity.entrySet()) {
                Activity activity = entry.getKey();
                if ((activity == Activity.CORE || activity == Activity.PANIC) && activeActivities.contains(activity))
                    tasks.addAll(entry.getValue());
            }
        }
        return tasks;
    }
}
