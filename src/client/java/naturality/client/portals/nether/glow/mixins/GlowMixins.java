package naturality.client.portals.nether.glow.mixins;

import naturality.client.portals.nether.glow.PortalGlowController;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class GlowMixins {
    private GlowMixins() {}

    @Mixin(NetherPortalBlock.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class NetherPortalBlockMixin {
        @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
        private void naturality$waitForReveal(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
            if (naturality.client.portals.nether.opening.PortalOpeningClient.isOpening(pos)) ci.cancel();
        }
        @WrapOperation(method = "animateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$replacePortalMote(Level level, ParticleOptions type,
                double x, double y, double z, double vx, double vy, double vz, Operation<Void> original,
                @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) BlockState state) {
            if (naturality.config.live.NaturalityConfig.get().portalChanges.portalParticleChanges && type == ParticleTypes.PORTAL && level instanceof ClientLevel clientLevel) {
                PortalGlowController.spawnMote(clientLevel, pos, state, x, y, z, vx, vy, vz);
            } else {
                original.call(level, type, x, y, z, vx, vy, vz);
            }
        }

        @Inject(method = "animateTick", at = @At("TAIL"))
        private void naturality$discoverPortal(BlockState state, Level level, BlockPos pos,
                                               RandomSource random, CallbackInfo ci) {
            if (level instanceof ClientLevel clientLevel) {
                PortalGlowController.observe(clientLevel, pos, state);
            }
        }
    }

    @Mixin(SpriteContents.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class OpaquePortalSpriteMixin {
        @Inject(method="<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",at=@At("HEAD"))
        private static void naturality$opaque(Identifier name,FrameSize size,NativeImage image,java.util.Optional<?> animation,
                java.util.List<?> metadata,java.util.Optional<?> texture,CallbackInfo ci){
            if(!name.equals(Identifier.withDefaultNamespace("block/nether_portal"))) return;
            for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
                image.setPixel(x,y,image.getPixel(x,y)|0xFF000000);
        }
    }

    /** Full-bright portal faces without changing block light propagation or portal mechanics. */
    @Mixin(BlockModelLighter.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalEmissiveMixin {
        @Inject(method = "prepareQuadFlat", at = @At("HEAD"), cancellable = true)
        private void naturality$emissiveFlat(BlockAndTintGetter level, BlockState state, BlockPos pos,
                int light, BakedQuad quad, QuadInstance output, CallbackInfo ci) {
            naturality$fullBright(state, output, ci);
        }

        @Inject(method = "prepareQuadAmbientOcclusion", at = @At("HEAD"), cancellable = true)
        private void naturality$emissiveAmbient(BlockAndTintGetter level, BlockState state, BlockPos pos,
                BakedQuad quad, QuadInstance output, CallbackInfo ci) {
            naturality$fullBright(state, output, ci);
        }

        private static void naturality$fullBright(BlockState state, QuadInstance output, CallbackInfo ci) {
            if (naturality.config.live.NaturalityConfig.get().portalChanges.portalBlockChanges && state.is(Blocks.NETHER_PORTAL)) {
                output.setLightCoords(0xF000F0);
                output.setColor(0xFFFFFFFF);
                ci.cancel();
            }
        }
    }

    /** Carry emission into the terrain material, not just CPU quad lighting. */
    @Mixin(BakedQuad.MaterialInfo.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalMaterialEmissiveMixin {
        @Inject(method = "of", at = @At("RETURN"), cancellable = true)
        private static void naturality$emissivePortalMaterial(CallbackInfoReturnable<BakedQuad.MaterialInfo> ci) {
            BakedQuad.MaterialInfo material = ci.getReturnValue();
            if (naturality.config.live.NaturalityConfig.get().portalChanges.portalBlockChanges && material.sprite().contents().name().equals(Identifier.withDefaultNamespace("block/nether_portal"))) {
                ci.setReturnValue(new BakedQuad.MaterialInfo(material.sprite(), material.layer(),
                    material.itemRenderType(), material.itemGlintRenderType(), material.itemGlintSpecialRenderType(),
                    material.tintIndex(), Direction.UP, 15));
            }
        }
    }

    @Mixin(BlockBehaviour.BlockStateBase.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PortalStateEmissiveMixin {
        @Inject(method = "emissiveRendering", at = @At("HEAD"), cancellable = true)
        private void naturality$portalIsEmissive(CallbackInfoReturnable<Boolean> ci) {
            if (naturality.config.live.NaturalityConfig.get().portalChanges.portalBlockChanges && ((BlockBehaviour.BlockStateBase) (Object) this).getBlock() instanceof NetherPortalBlock) {
                ci.setReturnValue(true);
            }
        }
    }
}
