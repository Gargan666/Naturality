package naturality.mixin;

import java.io.InputStream;
import naturality.config.FeatureResources;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.fabricmc.fabric.impl.resource.pack.ModNioPackResources", remap = false)
public abstract class FeatureResourcesMixin {
    private boolean naturality$ownPack() {
        return ((ModPackResources)this).getFabricModMetadata().getId().equals("naturality");
    }
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void naturality$resource(PackType type, Identifier id, CallbackInfoReturnable<IoSupplier<InputStream>> ci) {
        if (naturality$ownPack() && !FeatureResources.allowed(type == PackType.SERVER_DATA, id.getNamespace(), id.getPath()))
            ci.setReturnValue(null);
    }
    @ModifyVariable(method = "listResources", at = @At("HEAD"), argsOnly = true)
    private PackResources.ResourceOutput naturality$list(PackResources.ResourceOutput output, PackType type,
            String namespace, String path, PackResources.ResourceOutput ignored) {
        if (!naturality$ownPack()) return output;
        return (id, supplier) -> {
            if (FeatureResources.allowed(type == PackType.SERVER_DATA, id.getNamespace(), id.getPath())) output.accept(id, supplier);
        };
    }
}
