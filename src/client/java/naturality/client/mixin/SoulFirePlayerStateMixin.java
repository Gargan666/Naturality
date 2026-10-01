package naturality.client.mixin;

import naturality.client.fire.SoulFirePlayerState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerRenderState.class)
public abstract class SoulFirePlayerStateMixin implements SoulFirePlayerState {
    @Unique private boolean naturality$soulFireOverlay;

    @Override public boolean naturality$soulFireOverlay() { return naturality$soulFireOverlay; }
    @Override public void naturality$soulFireOverlay(boolean value) { naturality$soulFireOverlay = value; }
}
