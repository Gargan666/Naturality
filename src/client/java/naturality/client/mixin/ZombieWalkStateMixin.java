package naturality.client.mixin;
import naturality.client.entity.ZombieWalkState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(LivingEntityRenderState.class)
public abstract class ZombieWalkStateMixin implements ZombieWalkState {
    @Unique private Vec2 naturality$walkLean=Vec2.ZERO;
    public Vec2 naturality$walkLean() { return naturality$walkLean; }
    public void naturality$walkLean(Vec2 pose) { naturality$walkLean=pose; }
}
