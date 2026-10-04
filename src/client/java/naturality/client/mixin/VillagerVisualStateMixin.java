package naturality.client.mixin;

import naturality.client.villager.VillagerVisualState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VillagerRenderState.class)
public abstract class VillagerVisualStateMixin implements VillagerVisualState {
    @Unique private boolean naturality$toolPose;
    @Unique private boolean naturality$fishingRodPose;
    @Unique private final Matrix4f naturality$basePoseInverse = new Matrix4f();
    @Unique private final Vector3f naturality$rodTip = new Vector3f();
    @Unique private boolean naturality$rodTipValid;
    @Unique private @Nullable Villager naturality$owner;
    @Override public boolean naturality$toolPose() { return naturality$toolPose; }
    @Override public void naturality$setToolPose(boolean value) { naturality$toolPose = value; }
    @Override public boolean naturality$fishingRodPose() { return naturality$fishingRodPose; }
    @Override public void naturality$setFishingRodPose(boolean value) { naturality$fishingRodPose = value; }
    @Override public Matrix4f naturality$basePoseInverse() { return naturality$basePoseInverse; }
    @Override public Vector3f naturality$rodTip() { return naturality$rodTip; }
    @Override public boolean naturality$rodTipValid() { return naturality$rodTipValid; }
    @Override public void naturality$setRodTipValid(boolean value) { naturality$rodTipValid = value; }
    @Override public @Nullable Villager naturality$owner() { return naturality$owner; }
    @Override public void naturality$setOwner(@Nullable Villager owner) { naturality$owner = owner; }
}
