package naturality.client.mixin;

import naturality.client.insanity.InsanityState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class InsanityStateMixin implements InsanityState {
    @Unique private final Data naturality$data = new Data();
    public Data naturality$insanity() { return naturality$data; }
}
