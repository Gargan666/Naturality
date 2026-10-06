package naturality.client.mixin;
import naturality.client.weather.EndGravityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(LivingEntityRenderState.class)
public abstract class EndGravityRenderStateMixin implements EndGravityRenderState {
    @Unique private float naturality$inversion;
    public float naturality$inversion() { return naturality$inversion; }
    public void naturality$inversion(float value) { naturality$inversion=value; }
}
