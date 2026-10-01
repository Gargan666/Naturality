package naturality.client.portal;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import naturality.Naturality;
import net.minecraft.client.renderer.RenderPipelines;
public final class PortalOverlay {
    public static final RenderPipeline PIPELINE=RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Naturality.id("pipeline/portal_overlay")).withFragmentShader(Naturality.id("core/portal_overlay")).build());
    public static void initialize(){}

    public static float cameraOpacity(float previous) {
        var client=net.minecraft.client.Minecraft.getInstance();
        var player = client.player;
        if(player==null) return previous;
        var crossing=naturality.portal.PortalCrossing.get(player);
        if(crossing==null || !crossing.valid(player)) return previous;
        var camera=client.gameRenderer.mainCamera();
        var eye=camera.position();
        var near=camera.getNearPlane(camera.getFov());
        double closest=Double.POSITIVE_INFINITY;
        for(var offset:java.util.List.of(near.getTopLeft(),near.getTopRight(),near.getBottomLeft(),near.getBottomRight())) {
            var corner=eye.add(offset);
            closest=Math.min(closest,crossing.signed(corner.x,corner.z));
        }
        // Saturate before even a near-plane corner reaches the portal face.
        if(closest<=1.0/64) return 1;
        double half=player.getBbWidth()/2.0;
        double distance=crossing.signed(eye.x,eye.z);
        double margin=Math.max(0,distance-closest)+1.0/64;
        return Math.max(previous,(float)Math.clamp((half-distance)/Math.max(0.001,half-margin),0,1));
    }
}
