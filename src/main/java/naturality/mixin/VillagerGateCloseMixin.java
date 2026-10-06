package naturality.mixin;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import naturality.villager.VillagerGates;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Node;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InteractWithDoor.class)
public abstract class VillagerGateCloseMixin {
    @Inject(method = "closeDoorsThatIHaveOpenedOrPassedThrough", at = @At("HEAD"), cancellable = true)
    private static void naturality$closeGates(ServerLevel level, LivingEntity body, Node from, Node to,
            Set<GlobalPos> doors, Optional<List<LivingEntity>> neighbors, CallbackInfo ci) {
        if (!(body instanceof Villager)) return;
        var gates = new java.util.HashSet<GlobalPos>();
        doors.removeIf(global -> {
            if (global.dimension() == level.dimension()
                    && level.getBlockState(global.pos()).getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
                gates.add(global); return true;
            }
            return false;
        });
        if (gates.isEmpty()) return;
        VillagerGates.close(level, body, from, to, gates, neighbors);
        // Vanilla processes only actual DoorBlocks; preserve gates still in use.
        InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, body, from, to, doors, neighbors);
        doors.addAll(gates);
        ci.cancel();
    }
}
