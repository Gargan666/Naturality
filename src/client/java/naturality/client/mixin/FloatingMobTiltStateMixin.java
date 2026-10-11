package naturality.client.mixin;
import naturality.client.entity.FloatingMobTiltState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(LivingEntityRenderState.class)
public abstract class FloatingMobTiltStateMixin implements FloatingMobTiltState {
    @Unique private Vec2 naturality$floatLean=Vec2.ZERO;
    public Vec2 naturality$floatLean() { return naturality$floatLean; }
    public void naturality$floatLean(Vec2 lean) { naturality$floatLean=lean; }
}
