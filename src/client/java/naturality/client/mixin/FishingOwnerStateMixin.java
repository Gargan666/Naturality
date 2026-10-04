package naturality.client.mixin;

import naturality.client.villager.FishingOwnerState;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FishingHookRenderState.class)
public abstract class FishingOwnerStateMixin implements FishingOwnerState {
    @Unique private int naturality$ownerId = -1;
    @Unique private boolean naturality$glint;
    public boolean naturality$glint() { return naturality$glint; }
    public void naturality$setGlint(boolean value) { naturality$glint = value; }
    public int naturality$ownerId() { return naturality$ownerId; }
    public void naturality$setOwnerId(int id) { naturality$ownerId = id; }
}
