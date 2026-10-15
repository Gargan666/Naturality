package naturality.client.mixin;

import naturality.client.menu.MenuWorld;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleWorldMixin extends Screen {
    @Unique private @org.jspecify.annotations.Nullable MouseButtonEvent naturality$press;
    @Unique private boolean naturality$moved, naturality$replaying, naturality$doubleClick;
    protected TitleWorldMixin(Component title) { super(title); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void naturality$hold(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> ci) {
        if (event.button() != 1 || naturality$replaying) return;
        naturality$press = event;
        naturality$moved = false;
        naturality$doubleClick = doubleClick;
        MenuWorld.camera().hold();
        ci.setReturnValue(true);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        var press = naturality$press;
        if (event.button() != 1 || press == null) return super.mouseDragged(event, dx, dy);
        if (!naturality$moved && Math.hypot(event.x() - press.x(), event.y() - press.y()) >= 4) {
            naturality$moved = true;
            dx = event.x() - press.x();
            dy = event.y() - press.y();
        }
        if (naturality$moved) MenuWorld.camera().drag(dx, dy, width, height);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        var press = naturality$press;
        if (event.button() != 1 || press == null) return super.mouseReleased(event);
        boolean click = !naturality$moved
                && Math.hypot(event.x() - press.x(), event.y() - press.y()) < 4;
        naturality$press = null;
        MenuWorld.camera().release();
        if (click) {
            naturality$replaying = true;
            try { this.mouseClicked(event, naturality$doubleClick); }
            finally { naturality$replaying = false; }
            super.mouseReleased(event);
        }
        return true;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void naturality$removed(CallbackInfo ci) {
        naturality$press = null;
        MenuWorld.camera().suspend();
    }
}
