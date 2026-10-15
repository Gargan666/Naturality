package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.menu.MenuPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
public abstract class MenuPreviewServerMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "onServerCrash", at = @At("HEAD"), cancellable = true)
    private void naturality$previewFailure(net.minecraft.CrashReport report,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (MenuPreview.privateServer((IntegratedServer)(Object)this)) {
            naturality.Naturality.LOGGER.error("Disposable menu preview failed: {}", report.getTitle(), report.getException());
            ci.cancel();
        }
    }
    @WrapOperation(method = "halt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void naturality$noLanCleanup(IntegratedServer server, Runnable task, Operation<Void> original) {
        // Preview servers are never published and have only the local player. Native cleanup continues asynchronously; preview save hooks discard persistence.
        if (!MenuPreview.privateServer(server)) original.call(server, task);
    }
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object naturality$boundedWorld(OptionInstance<?> option, Operation<Object> original) {
        if (MenuPreview.owns((IntegratedServer)(Object)this)) {
            var options = Minecraft.getInstance().options;
            if (option == options.renderDistance()) return 8;
            if (option == options.simulationDistance()) return 2;
        }
        return original.call(option);
    }
}
