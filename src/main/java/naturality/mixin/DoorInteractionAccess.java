package naturality.mixin;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(InteractWithDoor.class)
public interface DoorInteractionAccess {
    @Invoker("areOtherMobsComingThroughDoor")
    static boolean naturality$othersComing(LivingEntity body, BlockPos pos, Optional<List<LivingEntity>> neighbors) {
        throw new AssertionError();
    }
}
