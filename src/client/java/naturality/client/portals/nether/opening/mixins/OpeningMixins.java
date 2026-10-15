package naturality.client.portals.nether.opening.mixins;

import naturality.client.portals.nether.opening.PortalOpeningClient;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.SoundBuffer;
import naturality.Naturality;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class OpeningMixins {
    private OpeningMixins() {}

    @Mixin(ParticleEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalOpeningRenderMixin {
        @Inject(method = "extract", at = @At("HEAD"))
        private void naturality$prepareGlowMasks(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
            naturality.client.portals.nether.travel.PortalCrossingClient.beginGlowFrame(camera, partialTick);
        }
        @Inject(method = "extract", at = @At("TAIL"))
        private void naturality$renderOpening(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
            PortalOpeningClient.extract(state, frustum, camera, partialTick);
            naturality.client.fluids.water.WaterIntersection.extract(state, frustum, camera, partialTick);
            naturality.client.fluids.lava.LavaIntersection.extract(state, frustum, camera, partialTick);
            naturality.client.portals.end.EndPortalBorderState.EndEyeGlow.extract(state, frustum, camera, partialTick);
            naturality.client.portals.nether.travel.PortalCrossingClient.extract(state, frustum, camera, partialTick);
            naturality.client.effects.ambienthorror.AmbientHorror.extract(state, frustum, camera, partialTick);
        }
    }

    @Mixin(SoundEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalOpeningSoundMixin {
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

    @Mixin(SectionCompiler.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalOpeningTerrainMixin {
        // Gate before Fabric redirects tessellation to Indigo's alternate renderer.
        @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
        private RenderShape naturality$animatedModel(BlockState state, Operation<RenderShape> original,
                @Local(name = "pos") BlockPos pos) {
            return PortalOpeningClient.hidesTerrain(pos, state) ? RenderShape.INVISIBLE : original.call(state);
        }
    }
}
