package naturality.client.mixin;

import naturality.client.portal.end.*;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EndPortalRenderState.class)
public class EndPortalBorderStateMixin implements EndPortalBorderAccess {
    @Unique private @org.jspecify.annotations.Nullable EndPortalBorderState naturality$border;
    public @org.jspecify.annotations.Nullable EndPortalBorderState naturality$getBorder() { return naturality$border; }
    public void naturality$setBorder(@org.jspecify.annotations.Nullable EndPortalBorderState state) { naturality$border = state; }
}
