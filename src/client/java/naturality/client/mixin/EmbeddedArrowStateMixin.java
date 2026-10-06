package naturality.client.mixin;

import naturality.client.entity.EmbeddedArrowState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class EmbeddedArrowStateMixin implements EmbeddedArrowState {
    @Unique private int naturality$arrowCount;
    @Unique private int naturality$arrowSeed;
    public int naturality$arrowCount() { return naturality$arrowCount; }
    public void naturality$setArrowCount(int count) { naturality$arrowCount = count; }
    public int naturality$arrowSeed() { return naturality$arrowSeed; }
    public void naturality$setArrowSeed(int seed) { naturality$arrowSeed = seed; }
}
