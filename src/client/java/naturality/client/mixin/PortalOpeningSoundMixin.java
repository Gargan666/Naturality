package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.SoundBuffer;
import naturality.Naturality;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public abstract class PortalOpeningSoundMixin {
    @WrapOperation(method = "play", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getCompleteBuffer(Lnet/minecraft/resources/Identifier;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<SoundBuffer> naturality$positionalOpening(SoundBufferLibrary library,
            Identifier path, Operation<CompletableFuture<SoundBuffer>> original,
            @Local(argsOnly = true) SoundInstance instance) {
        // A separate cache key keeps vanilla playback and other sound events untouched.
        if (instance.getIdentifier().equals(Naturality.id("portal_open"))
            || instance.getIdentifier().equals(Naturality.id("watersplash_loud"))) {
            path = Naturality.id("positional_sound/" + path.getNamespace() + "/" + path.getPath());
        }
        return original.call(library, path);
    }
}

