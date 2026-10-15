package naturality.client.rendering.shadows;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Places generated shadow textures on the submitted support surfaces. */
public final class ShadowRendering {
    static { BlockGridShadow.initialize(); }
    private ShadowRendering() { }
    public static void emit(ShadowFeatureRenderer.Submit submit, VertexConsumer buffer) {
        if (submit.pieces().isEmpty()) return;
        int pixels = BlockGridShadow.pixelRadius(submit.radius());
        float extent = (pixels + BlockGridShadow.BORDER) / 16.0F;
        var first = submit.pieces().getFirst();
        int phase = BlockGridShadow.gridPhase(first.relativeX()) | (BlockGridShadow.gridPhase(first.relativeZ()) << 16);
        float left = -extent;
        float back = -extent;
        float width = extent * 2;
        for (var piece : submit.pieces()) {
            var box = piece.shapeBelow().bounds();
            float x0 = Math.max(piece.relativeX() + (float)box.minX, left);
            float x1 = Math.min(piece.relativeX() + (float)box.maxX, left + width);
            float z0 = Math.max(piece.relativeZ() + (float)box.minZ, back);
            float z1 = Math.min(piece.relativeZ() + (float)box.maxZ, back + width);
            if (x0 >= x1 || z0 >= z1) continue;
            float y = piece.relativeY() + (float)box.minY;
            int color = ARGB.white(piece.alpha());
            vertex(submit.pose(), buffer, color, phase, x0, y, z0, (x0-left)/width, (z0-back)/width);
            vertex(submit.pose(), buffer, color, phase, x0, y, z1, (x0-left)/width, (z1-back)/width);
            vertex(submit.pose(), buffer, color, phase, x1, y, z1, (x1-left)/width, (z1-back)/width);
            vertex(submit.pose(), buffer, color, phase, x1, y, z0, (x1-left)/width, (z0-back)/width);
        }
    }

    private static void vertex(Matrix4fc pose, VertexConsumer buffer, int color, int phase,
                               float x, float y, float z, float u, float v) {
        var position = pose.transformPosition(x, y, z, new Vector3f());
        buffer.addVertex(position.x(), position.y(), position.z(), color, u, v,
            phase, 15728880, 0, 1, 0);
    }

}
