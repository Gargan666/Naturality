package naturality.client.mixin;

import naturality.client.entity.CreeperTintState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CreeperRenderState.class)
public abstract class CreeperTintStateMixin implements CreeperTintState {
    @Unique private int naturality$color = -1;
    public int naturality$foliageTint() { return naturality$color; }
    public void naturality$foliageTint(int color) { naturality$color = color; }
}
