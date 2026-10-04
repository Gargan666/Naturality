package naturality.client.villager;

import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.entity.npc.villager.Villager;

public interface VillagerVisualState {
    boolean naturality$toolPose();
    void naturality$setToolPose(boolean value);
    boolean naturality$fishingRodPose();
    void naturality$setFishingRodPose(boolean value);
    Matrix4f naturality$basePoseInverse();
    Vector3f naturality$rodTip();
    boolean naturality$rodTipValid();
    void naturality$setRodTipValid(boolean value);
    @Nullable Villager naturality$owner();
    void naturality$setOwner(@Nullable Villager owner);
}
