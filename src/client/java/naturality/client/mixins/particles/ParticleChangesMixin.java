package naturality.client.mixins.particles;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.client.particles.deathsmoke.DeathSmoke;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.injection.At;
import naturality.client.particles.sand.SandPlacementBurst.SandParticleSprites;
import net.minecraft.client.particle.FallingDustParticle;
import net.minecraft.client.particle.SpriteSet;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.level.Level;
import naturality.client.rendering.breaking.BreakingEffects.BlockFragmentPixels;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Shadow;
import java.util.BitSet;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class ParticleChangesMixin {
    private ParticleChangesMixin() {}

    @Mixin(LivingEntity.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class DeathSmokeSpawnMixin {
        @WrapMethod(method="makePoofParticles")
        private void naturality$smoke(Operation<Void> original) {
            var entity = (LivingEntity)(Object)this;
            if (!entity.level().isClientSide() || !(entity instanceof Mob)) { original.call(); return; }
            var previous = DeathSmoke.spawning;
            int index = DeathSmoke.index;
            DeathSmoke.spawning = entity;
            DeathSmoke.index = 0;
            try {
                original.call();
                if (naturality.api.motion.GhastMotion.supported(entity)) {
                    var random = entity.getRandom();
                    for (int i=0;i<10;i++) entity.level().addParticle(net.minecraft.core.particles.ParticleTypes.POOF,
                        entity.getRandomX(1),entity.getRandomY(),entity.getRandomZ(1),
                        random.nextGaussian()*.02,random.nextGaussian()*.02,random.nextGaussian()*.02);
                }
            }
            finally { DeathSmoke.spawning = previous; DeathSmoke.index = index; }
        }
    }

    /** Preserve the server's explosion strength when creating its client visual. */
    @Mixin(ClientPacketListener.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ExplosionParticleSizeMixin {
        @WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$explosionScale(ClientLevel level, ParticleOptions particle,
                double x, double y, double z, double vx, double vy, double vz, Operation<Void> original,
                @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ClientboundExplodePacket packet) {
            if (naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()
                    && (particle.getType() == ParticleTypes.EXPLOSION || particle.getType() == ParticleTypes.EXPLOSION_EMITTER
                        || particle.getType() == naturality.bootstrap.ContentRegistration.NaturalityParticles.END_EXPLOSION)) {
                double baseScale = packet.radius() / 4.0;
                vx = baseScale < 1 ? Math.min(1, baseScale * 2) : baseScale;
                var impact = naturality.client.entities.fireballs.FireballModelRenderer.FireballImpactClient.consume(level, x, y, z);
                if (impact != null) {
                    x = impact.hitX(); y = impact.hitY(); z = impact.hitZ();
                    vy = impact.face().get3DDataValue() + 1;
                }
            }
            original.call(level, particle, x, y, z, vx, vy, vz);
        }
    }

    @Mixin(FallingDustParticle.Provider.class)
    @org.jspecify.annotations.NullMarked
    public static class FallingDustSpritesMixin {
        @Inject(method = "<init>", at = @At("RETURN"))
        private void naturality$captureSprites(SpriteSet sprites, CallbackInfo ci) {
            SandParticleSprites.sprites = sprites;
        }
    }

    @Mixin(SingleQuadParticle.class)
    @org.jspecify.annotations.NullMarked
    public interface ParticleSpriteAccess {
        @Accessor("sprite") TextureAtlasSprite naturality$sprite();
    }

    @Mixin(AreaEffectCloud.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class PotionCloudSmokeMixin {
        @WrapOperation(method = "clientTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
        private void naturality$smoke(Level level, ParticleOptions options, double x, double y, double z,
                                      double vx, double vy, double vz, Operation<Void> original) {
            // Use the cloud color even during vanilla's white waiting-phase flashes.
            var source = ((AreaEffectCloud) (Object) this).getParticle();
            // Thin both lingering potion and dragon clouds without changing their gameplay.
            if ((source.getType() == ParticleTypes.ENTITY_EFFECT || source.getType() == ParticleTypes.DRAGON_BREATH)
                    && level.getRandom().nextFloat() < 0.25F) return;
            if (source.getType() == ParticleTypes.ENTITY_EFFECT && source instanceof ColorParticleOption color) {
                original.call(level, NaturalityParticles.POTION_CLOUD, x, y, z,
                    (double) color.getRed(), (double) color.getGreen(), (double) color.getBlue());
            } else if (source.getType() == ParticleTypes.DRAGON_BREATH) {
                // Explicitly mark dragon cloud smoke as stationary; flying breath uses billboards.
                var random = level.getRandom();
                original.call(level, NaturalityParticles.POTION_CLOUD, x, y, z,
                    (double) (0.7176471F + random.nextFloat() * (0.8745098F - 0.7176471F)),
                    0.0, (double) (0.8235294F + random.nextFloat() * (0.9764706F - 0.8235294F)));
            } else {
                original.call(level, options, x, y, z, vx, vy, vz);
            }
        }
    }

    /** Emit texel-sized pieces so randomly omitted source pixels remain transparent. */
    @Mixin(SingleQuadParticle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class TerrainParticleExtractMixin extends Particle {
        @Shadow protected float rCol;
        @Shadow protected float gCol;
        @Shadow protected float bCol;
        @Shadow protected float alpha;
        @Shadow protected float roll;
        @Shadow protected float oRoll;
        @SuppressWarnings("null") @Shadow protected TextureAtlasSprite sprite;
        @Shadow public abstract SingleQuadParticle.FacingCameraMode getFacingCameraMode();
        @Shadow protected abstract SingleQuadParticle.Layer getLayer();
        @Shadow public abstract float getQuadSize(float partialTick);

        @SuppressWarnings("null") // Mixin injects the shadow sprite from SingleQuadParticle.
        protected TerrainParticleExtractMixin(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
        private void naturality$emitPixelFragments(QuadParticleRenderState state, Camera camera, float partialTick, CallbackInfo ci) {
            if (!((Object) this instanceof TerrainParticle) || !((Object) this instanceof BlockFragmentPixels fragment)) return;

            if (fragment.naturality$cropWidth() == 0) return;

            var rotation = new Quaternionf();
            getFacingCameraMode().setRotation(rotation, camera, partialTick);
            if (roll != 0) rotation.rotateZ(Mth.lerp(partialTick, oRoll, roll));

            Vec3 eye = camera.position();
            float centerX = (float) (Mth.lerp(partialTick, xo, x) - eye.x);
            float centerY = (float) (Mth.lerp(partialTick, yo, y) - eye.y);
            float centerZ = (float) (Mth.lerp(partialTick, zo, z) - eye.z);
            float size = getQuadSize(partialTick);
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            int columns = fragment.naturality$cropWidth();
            int rows = fragment.naturality$cropHeight();
            float cellWidth = 2 * size / columns;
            float cellHeight = 2 * size / rows;
            float pixelQuadSize = Math.min(cellWidth, cellHeight) * 0.5F;
            int color = ARGB.colorFromFloat(alpha, rCol, gCol, bCol);
            int light = getLightCoords(partialTick);
            int cropStartX = fragment.naturality$cropStartX();
            int cropStartY = fragment.naturality$cropStartY();
            var layer = getLayer();
            var offset = new Vector3f();

            for (int row = 0; row < rows; row++) {
                int sourceY = cropStartY + rows - 1 - row;
                float v0 = sprite.getV(sourceY / (float) height);
                float v1 = sprite.getV((sourceY + 1.0F) / height);
                for (int column = 0; column < columns; column++) {
                    if (fragment.naturality$isPixelRemoved(column, row)) continue;
                    int sourceX = cropStartX + column;
                    float u0 = sprite.getU((sourceX + 1.0F) / width);
                    float u1 = sprite.getU(sourceX / (float) width);
                    float localX = -size + (column + 0.5F) * cellWidth;
                    float localY = -size + (row + 0.5F) * cellHeight;
                    rotation.transform(offset.set(localX, localY, 0));
                    state.add(layer, centerX + offset.x, centerY + offset.y, centerZ + offset.z,
                        rotation.x, rotation.y, rotation.z, rotation.w, pixelQuadSize,
                        u0, u1, v0, v1, color, light);
                }
            }
            ci.cancel();
        }
    }

    /** Snap block-break texture crops to texels and choose a few missing texels. */
    @Mixin(TerrainParticle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class TerrainParticlePixelMixin extends SingleQuadParticle implements BlockFragmentPixels {
        @Unique private boolean naturality$sand;
        @Unique private float naturality$rotationSpeed;
        @Unique private int naturality$cropStartX;
        @Unique private int naturality$cropStartY;
        @Unique private int naturality$cropWidth;
        @Unique private int naturality$cropHeight;
        @Unique private int naturality$initialHoles;
        @Unique private BitSet naturality$removedPixels = new BitSet();

        protected TerrainParticlePixelMixin(ClientLevel level, double x, double y, double z,
                                           double xd, double yd, double zd, TextureAtlasSprite sprite) {
            super(level, x, y, z, xd, yd, zd, sprite);
        }

        @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
            at = @At("RETURN"))
        private void naturality$alignCropAndChooseHoles(ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.core.BlockPos pos, CallbackInfo ci) {
            naturality$sand = state.is(net.minecraft.world.level.block.Blocks.SAND)
                || state.is(net.minecraft.world.level.block.Blocks.RED_SAND);
            if (naturality$sand) {
                int color = ((net.minecraft.world.level.block.FallingBlock) state.getBlock()).getDustColor(state, level, pos);
                setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
                var sprites = naturality.client.particles.sand.SandPlacementBurst.SandParticleSprites.sprites;
                if (sprites != null) setSpriteFromAge(sprites);
                quadSize *= 1.35F;
                naturality$rotationSpeed = (random.nextFloat() - 0.5F) * 0.1F;
                roll = random.nextFloat() * (float) (Math.PI * 2);
                return;
            }
            int width = sprite.contents().width();
            int height = sprite.contents().height();
            naturality$cropWidth = Math.max(1, Math.round(width / 4.0F));
            naturality$cropHeight = Math.max(1, Math.round(height / 4.0F));
            naturality$cropStartX = random.nextInt(width - naturality$cropWidth + 1);
            naturality$cropStartY = random.nextInt(height - naturality$cropHeight + 1);
            naturality$removedPixels = new BitSet();
            int cells = naturality$cropWidth * naturality$cropHeight;
            int holes = random.nextInt(Math.min(4, cells) + 1);
            naturality$initialHoles = holes;
            while (naturality$removedPixels.cardinality() < holes) {
                naturality$removedPixels.set(random.nextInt(cells));
            }
        }

        @Inject(method = "getU0", at = @At("HEAD"), cancellable = true)
        private void naturality$cropRightU(CallbackInfoReturnable<Float> cir) {
            if (naturality$sand) { cir.setReturnValue(sprite.getU0()); return; }
            cir.setReturnValue(sprite.getU((naturality$cropStartX + naturality$cropWidth) / (float) sprite.contents().width()));
        }

        @Inject(method = "getU1", at = @At("HEAD"), cancellable = true)
        private void naturality$cropLeftU(CallbackInfoReturnable<Float> cir) {
            if (naturality$sand) { cir.setReturnValue(sprite.getU1()); return; }
            cir.setReturnValue(sprite.getU(naturality$cropStartX / (float) sprite.contents().width()));
        }

        @Inject(method = "getV0", at = @At("HEAD"), cancellable = true)
        private void naturality$cropTopV(CallbackInfoReturnable<Float> cir) {
            if (naturality$sand) { cir.setReturnValue(sprite.getV0()); return; }
            cir.setReturnValue(sprite.getV(naturality$cropStartY / (float) sprite.contents().height()));
        }

        @Inject(method = "getV1", at = @At("HEAD"), cancellable = true)
        private void naturality$cropBottomV(CallbackInfoReturnable<Float> cir) {
            if (naturality$sand) { cir.setReturnValue(sprite.getV1()); return; }
            cir.setReturnValue(sprite.getV((naturality$cropStartY + naturality$cropHeight) / (float) sprite.contents().height()));
        }

        @Inject(method = "getLayer", at = @At("HEAD"), cancellable = true)
        private void naturality$sandLayer(CallbackInfoReturnable<SingleQuadParticle.Layer> cir) {
            if (naturality$sand) cir.setReturnValue(SingleQuadParticle.Layer.OPAQUE);
        }

        @Override public void tick() {
            super.tick();
            if (naturality$sand) {
                var sprites = naturality.client.particles.sand.SandPlacementBurst.SandParticleSprites.sprites;
                if (sprites != null) setSpriteFromAge(sprites);
                oRoll = roll;
                roll += (float) Math.PI * naturality$rotationSpeed * 2F;
                if (onGround) oRoll = roll = 0;
            }
        }

        @Override @Unique public int naturality$cropStartX() { return naturality$cropStartX; }
        @Override @Unique public int naturality$cropStartY() { return naturality$cropStartY; }
        @Override @Unique public int naturality$cropWidth() { return naturality$cropWidth; }
        @Override @Unique public int naturality$cropHeight() { return naturality$cropHeight; }
        @Override @Unique public boolean naturality$isPixelRemoved(int x, int y) {
            // Gradually erode the remaining texels after the first fifth of life,
            // reaching an empty fragment at 95% of its lifetime.
            int cells = naturality$cropWidth * naturality$cropHeight;
            float progress = lifetime - age <= 1 ? 1
                : Math.clamp((age / (float) Math.max(1, lifetime) - 0.2F) / 0.75F, 0, 1);
            int holes = naturality$initialHoles
                + (int) Math.floor((cells - naturality$initialHoles) * progress);
            while (naturality$removedPixels.cardinality() < holes) {
                naturality$removedPixels.set(random.nextInt(cells));
            }
            return naturality$removedPixels.get(y * naturality$cropWidth + x);
        }
    }
}
