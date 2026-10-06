package naturality.villager;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ReputationState {
    public UUID lineage = UUID.randomUUID();
    public ItemStack rod = ItemStack.EMPTY;
    public long wakeUntil = -1;
    public long lastSleep = -1, recoveryUntil = -1;
    public boolean seekingBed;
    public net.minecraft.core.BlockPos sleepBed;
    public int bedSearchTick = -1;
    public int nodStart = -100;
    public boolean fleeing;
    public UUID fearedPlayer;
    public net.minecraft.world.phys.Vec3 escapeTarget;
    public int escapeTick = -1;
    public UUID reputationTarget;
    public void copyFrom(ReputationState other) { lineage = other.lineage; rod = other.rod.copy(); wakeUntil = other.wakeUntil; lastSleep = other.lastSleep; recoveryUntil = other.recoveryUntil; }
    public void save(ValueOutput output) {
        output.store("NaturalityVillagerLineage", UUIDUtil.CODEC, lineage);
        output.putLong("NaturalityWokenUntil", wakeUntil);
        output.putLong("NaturalityLastSleep", lastSleep);
        output.putLong("NaturalityRecoverySleepUntil", recoveryUntil);
        if (!rod.isEmpty()) output.store("NaturalityFishingRod", ItemStack.CODEC, rod);
    }
    public void load(ValueInput input) {
        lineage = input.read("NaturalityVillagerLineage", UUIDUtil.CODEC).orElse(lineage);
        wakeUntil = input.getLongOr("NaturalityWokenUntil", -1);
        lastSleep = input.getLongOr("NaturalityLastSleep", -1);
        recoveryUntil = input.getLongOr("NaturalityRecoverySleepUntil", -1);
        rod = input.read("NaturalityFishingRod", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
