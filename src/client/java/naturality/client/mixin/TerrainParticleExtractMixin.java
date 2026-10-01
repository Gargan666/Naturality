package naturality.client.mixin;

import naturality.client.particle.BlockFragmentPixels;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emit texel-sized pieces so randomly omitted source pixels remain transparent. */
@Mixin(SingleQuadParticle.class)
public abstract class TerrainParticleExtractMixin extends Particle {
    @Shadow protected float rCol;
    @Shadow protected float gCol;
    @Shadow protected float bCol;
    @Shadow protected float alpha;
    @Shadow protected float roll;
    @Shadow protected float oRoll;
    @Shadow protected TextureAtlasSprite sprite;
    @Shadow public abstract SingleQuadParticle.FacingCameraMode getFacingCameraMode();
    @Shadow protected abstract SingleQuadParticle.Layer getLayer();
    @Shadow public abstract float getQuadSize(float partialTick);

    protected TerrainParticleExtractMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void naturality$emitPixelFragments(QuadParticleRenderState state, Camera camera, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof TerrainParticle) || !((Object) this instanceof BlockFragmentPixels fragment)) return;

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

        for (int row = 0; row < rows; row++) {
            int sourceY = fragment.naturality$cropStartY() + rows - 1 - row;
            for (int column = 0; column < columns; column++) {
                if (fragment.naturality$isPixelRemoved(column, row)) continue;
                int sourceX = fragment.naturality$cropStartX() + column;
                float u0 = sprite.getU((sourceX + 1.0F) / width);
                float u1 = sprite.getU(sourceX / (float) width);
                float v0 = sprite.getV(sourceY / (float) height);
                float v1 = sprite.getV((sourceY + 1.0F) / height);
                float localX = -size + (column + 0.5F) * cellWidth;
                float localY = -size + (row + 0.5F) * cellHeight;
                Vector3f offset = rotation.transform(new Vector3f(localX, localY, 0));
                state.add(getLayer(), centerX + offset.x, centerY + offset.y, centerZ + offset.z,
                    rotation.x, rotation.y, rotation.z, rotation.w, pixelQuadSize,
                    u0, u1, v0, v1, color, light);
            }
        }
        ci.cancel();
    }
}
