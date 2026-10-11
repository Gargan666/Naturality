package naturality.client.mixin;

import naturality.client.entity.EmbeddedArrowState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class EmbeddedArrowStateMixin implements EmbeddedArrowState {
    @Unique private int naturality$arrowCount;
    @Unique private int naturality$arrowSeed;
    @Unique private java.util.List<naturality.client.entity.MobArrowImpacts.Impact> naturality$impacts = java.util.List.of();
    @Unique private final org.joml.Matrix4f naturality$origin = new org.joml.Matrix4f();
    public java.util.List<naturality.client.entity.MobArrowImpacts.Impact> naturality$impacts() { return naturality$impacts; }
    public void naturality$setImpacts(java.util.List<naturality.client.entity.MobArrowImpacts.Impact> hits) { naturality$impacts = hits; }
    public org.joml.Matrix4f naturality$origin() { return naturality$origin; }
    public int naturality$arrowCount() { return naturality$arrowCount; }
    public void naturality$setArrowCount(int count) { naturality$arrowCount = count; }
    public int naturality$arrowSeed() { return naturality$arrowSeed; }
    public void naturality$setArrowSeed(int seed) { naturality$arrowSeed = seed; }
}
