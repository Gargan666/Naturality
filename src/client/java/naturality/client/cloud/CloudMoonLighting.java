package naturality.client.cloud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.joml.Vector4f;

/** Uses the same camera attributes as the rendered moon, including custom sky timing. */
public final class CloudMoonLighting {
    private static final org.joml.Vector3f renderedDirection = new org.joml.Vector3f();
    private static boolean captured;
    public static void beginSky() { captured = false; }
    public static void capture(com.mojang.blaze3d.vertex.PoseStack pose) {
        pose.last().pose().transformDirection(new org.joml.Vector3f(0, 1, 0), renderedDirection);
        com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy().transformDirection(renderedDirection);
        renderedDirection.normalize();
        captured = true;
    }
    public static float phaseStrength(int phase) {
        return 0.1F + 0.9F * (float)((1 + Math.cos(phase * Math.PI / 4)) * 0.5);
    }

    public static org.joml.Vector3f direction(float angle) {
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.rotateDegrees(com.mojang.math.Axis.YP, -90.0F);
        pose.rotate(com.mojang.math.Axis.XP, angle);
        return pose.last().pose().transformDirection(new org.joml.Vector3f(0, 1, 0));
    }

    public static Vector4f sample(float partialTicks) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) return new Vector4f();
        var probe = client.gameRenderer.mainCamera().attributeProbe();
        float moon = probe.getValue(EnvironmentAttributes.MOON_ANGLE, partialTicks) * (float)(Math.PI / 180);
        float sun = probe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * (float)(Math.PI / 180);
        int phase = probe.getValue(EnvironmentAttributes.MOON_PHASE, partialTicks).index();
        float night = Math.clamp(-(float)Math.cos(sun) * 3, 0, 1);
        night = night * night * (3 - 2 * night);
        float strength = night * phaseStrength(phase) * Math.clamp((float)Math.cos(moon) * 4, 0, 1)
            * (1 - level.getRainLevel(partialTicks) * 0.8F);
        var viewDirection = captured ? new org.joml.Vector3f(renderedDirection)
            : com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy().transformDirection(direction(moon));
        return new Vector4f(viewDirection, strength);
    }

    private CloudMoonLighting() { }
}
