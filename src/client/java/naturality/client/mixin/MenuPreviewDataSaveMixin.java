package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import net.minecraft.world.level.storage.SavedDataStorage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SavedDataStorage.class)
public abstract class MenuPreviewDataSaveMixin {
    @SuppressWarnings("null") @Shadow @Final private Path dataFolder;
    @Inject(method = "scheduleSave", at = @At("HEAD"), cancellable = true)
    private void naturality$discardData(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (MenuPreview.temporaryPath(dataFolder)) cir.setReturnValue(CompletableFuture.completedFuture(null));
    }
}
