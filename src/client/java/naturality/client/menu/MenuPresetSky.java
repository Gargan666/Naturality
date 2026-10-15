package naturality.client.menu;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Native/mod sky and clouds supplied with a fixed first-day state, without a world. */
public final class MenuPresetSky {
    private static SkyRenderer sky;
    private static ProjectionMatrixBuffer projection;
    private static MappableRingBuffer fog;
    public static int frames;
    public static void render() {
        var client = Minecraft.getInstance();
        var screen = client.gui.screen();
        if (!client.isGameLoadFinished() || MenuPreview.stopped()) return;
        if (client.gameRenderer.gameRenderState().shouldRenderLevel) { closeSky(); return; }
        if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen
                || screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
                || screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)) return;
        var target = client.gameRenderer.mainRenderTarget();
        var colorTexture = target.getColorTexture();
        var depthTexture = target.getDepthTexture();
        var colorView = target.getColorTextureView();
        if (colorTexture == null || depthTexture == null || colorView == null) return;
        if (sky == null) sky = new SkyRenderer(client.getTextureManager(), client.getAtlasManager(), target);
        if (projection == null) projection = new ProjectionMatrixBuffer("Preset menu sky projection");
        if (fog == null) fog = new MappableRingBuffer(() -> "Preset menu sky fog", 130, FogRenderer.FOG_UBO_SIZE);
        fog.rotate();
        var color = new Vector4f(.67F, .79F, .97F, 1);
        try (var mapped = fog.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(mapped.data()).putVec4(color).putFloat(1000000).putFloat(1000001)
                    .putFloat(1000000).putFloat(1000001).putFloat(4096).putFloat(4096);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colorTexture, color, depthTexture, 0);
        var state = new SkyRenderState();
        state.skybox = Skybox.OVERWORLD; state.skyColor = new Vector3f(.48F, .67F, 1);
        state.sunriseAndSunsetColor = new Vector4f(0, 0, 0, 0);
        state.sunAngle = 0; state.moonAngle = (float)Math.PI; state.starAngle = (float)Math.PI;
        state.rainBrightness = 1;
        var window = client.getWindow();
        var matrix = new Matrix4f().setPerspective((float)Math.toRadians(client.options.fov().get()),
                window.getWidth() / (float)Math.max(1, window.getHeight()), 4096, .05F,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix(); RenderSystem.backupProjectionMatrix();
        try {
            view.identity().rotateX((float)Math.toRadians(34)).rotateY((float)Math.toRadians(215));
            RenderSystem.setProjectionMatrix(projection.getBuffer(matrix), ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderFog(fog.currentBuffer().slice()); MenuFog.prepare();
            sky.render(fog.currentBuffer().slice(), state);
            var clouds = client.levelRenderer.cloudRenderer();
            var status = client.options.getCloudStatus();
            if (status != net.minecraft.client.CloudStatus.OFF) {
                clouds.prepare(0xffffffff, status, 192.33F, Math.max(36, client.options.cloudRange().get() * 4), new Vec3(0, 200, 0), 0, 0);
                try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Preset native menu clouds",
                        colorView, Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass); clouds.render(status, pass);
                }
            }
            frames++;
        } finally { RenderSystem.restoreProjectionMatrix(); view.popMatrix(); }
    }
    private static void closeSky() { if (sky != null) { sky.close(); sky = null; } }
    public static void close() {
        closeSky();
        if (projection != null) { projection.close(); projection = null; }
        if (fog != null) { fog.close(); fog = null; }
    }
    private MenuPresetSky() { }
}
