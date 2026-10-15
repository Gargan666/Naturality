package naturality.client.mixin;
import naturality.client.entity.FishSwim;
import naturality.client.entity.FishSwimState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(LivingEntityRenderState.class)
public abstract class FishSwimStateMixin implements FishSwimState {
    @Unique private FishSwim.@org.jspecify.annotations.Nullable Pose naturality$swim;
    public FishSwim.@org.jspecify.annotations.Nullable Pose naturality$swim() { return naturality$swim; }
    public void naturality$swim(FishSwim.@org.jspecify.annotations.Nullable Pose pose) { naturality$swim=pose; }
}
