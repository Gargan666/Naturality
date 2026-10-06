package naturality.client.mixin;

import java.util.BitSet;
import naturality.client.particle.BlockFragmentPixels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Snap block-break texture crops to texels and choose a few missing texels. */
@Mixin(TerrainParticle.class)
public abstract class TerrainParticlePixelMixin extends SingleQuadParticle implements BlockFragmentPixels {
    @Unique private boolean naturality$sand;
    @Unique private float naturality$rotationSpeed;
    @Unique private int naturality$cropStartX;
    @Unique private int naturality$cropStartY;
    @Unique private int naturality$cropWidth;
    @Unique private int naturality$cropHeight;
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
            setSpriteFromAge(naturality.client.particle.SandParticleSprites.sprites);
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
            setSpriteFromAge(naturality.client.particle.SandParticleSprites.sprites);
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
        return naturality$removedPixels.get(y * naturality$cropWidth + x);
    }
}
