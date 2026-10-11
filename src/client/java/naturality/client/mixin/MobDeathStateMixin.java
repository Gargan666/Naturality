package naturality.client.mixin;

import naturality.client.entity.MobDeathState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class MobDeathStateMixin implements MobDeathState {
    @Unique private int naturality$deathPixels;
    public int naturality$deathPixels() { return naturality$deathPixels; }
    public void naturality$deathPixels(int progress) { naturality$deathPixels = progress; }
}
