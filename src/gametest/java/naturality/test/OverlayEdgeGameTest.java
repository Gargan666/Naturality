package naturality.test;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.*;
import naturality.Naturality;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Vector4f;

public final class OverlayEdgeGameTest implements FabricClientGameTest {
    private static RenderPipeline pipeline(boolean fixed, boolean distant) {
        var builder=RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/overlay_edge_"+fixed+"_"+distant))
            .withVertexShader("core/screenquad").withFragmentShader(Naturality.id("core/overlay_edge_test"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("Sampler0",UniformType.COMBINED_IMAGE_SAMPLER).build())
            .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT);
        if(fixed)builder.withShaderDefine("FIX_EDGE");
        if(distant)builder.withShaderDefine("DISTANT");
        return RenderPipelines.register(builder.build());
    }
    private static final RenderPipeline[] PIPELINES={pipeline(false,false),pipeline(true,false),pipeline(false,true),pipeline(true,true)};

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(_ -> checkGpu());
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");server.runCommand("time set midnight");
            server.runCommand("fill -3 100 -3 3 104 3 grass_block");
            server.runCommand("fill -1 101 -1 1 103 1 air");
            server.runCommand("setblock 0 101 0 torch");
            server.runCommand("tp @a 0 101 0 45 -30");
            world.getConnection().waitForClientboundPackets();context.waitTicks(15);
            world.getConnection().waitForChunksRender();context.takeScreenshot("grass-overlay-edge-no-snow");
            server.runCommand("fill -3 105 -3 3 105 3 snow");
            world.getConnection().waitForClientboundPackets();context.waitTicks(10);
            world.getConnection().waitForChunksRender();context.takeScreenshot("grass-overlay-edge-snow");
        }
    }

    private static void checkGpu() {
        var source=new TextureTarget("Overlay alpha fixture",2,1,GpuFormat.RGBA8_UNORM,null);
        try(var image=new NativeImage(2,1,false)) {
            image.setPixel(0,0,0xff408020);image.setPixel(1,0,0x00ffffff);
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToTexture(TestFixtures.require(source.getColorTexture(), "source.getColorTexture()"),image);
            int[] original=render(source,0),fixed=render(source,1);
            int pixel=128;
            int before=original[pixel]&255,after=fixed[pixel]&255;
            int expected=Math.round(64*0.4F);
            if(before<=expected+5 || Math.abs(after-expected)>1)
                throw new AssertionError("Transparent RGB fringe not reproduced/fixed: "+before+" -> "+after+", expected "+expected);
            if(!Arrays.equals(render(source,2),render(source,3)))
                throw new AssertionError("Distant filtering changed");
            image.setPixel(0,0,0x66408020);image.setPixel(1,0,0x99ffffff);
            encoder.writeToTexture(TestFixtures.require(source.getColorTexture(), "source.getColorTexture()"),image);
            if(!Arrays.equals(render(source,0),render(source,1)))
                throw new AssertionError("Authored partial alpha changed");
            System.out.println("Overlay GPU regression: bright-edge red "+before+" -> "+after+"; distant and partial-alpha outputs unchanged");
        } finally { source.destroyBuffers(); }
    }
    private static int[] render(TextureTarget source,int variant) {
        int width=257,height=3;
        var target=new TextureTarget("Overlay edge oracle",width,height,GpuFormat.RGBA8_UNORM,null);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        try(var buffer=RenderSystem.getDevice().createBuffer(() -> "Overlay edge readback",9,width*height*4L)) {
            try(var pass=encoder.createRenderPass(() -> "Overlay edge test",TestFixtures.require(target.getColorTextureView(), "target.getColorTextureView()"),
                    Optional.of(new Vector4f(.02F,.02F,.02F,1)))) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINES[variant]));
                pass.setUniform("Sampler0",source.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(3,1,0,0);
            }
            encoder.copyTextureToBuffer(TestFixtures.require(target.getColorTexture(), "target.getColorTexture()"),buffer,0,() -> {},0);
            try(var fence=encoder.createFence()){encoder.submit();fence.awaitCompletion(-1);}
            int[] pixels=new int[width*height];
            try(var mapped=buffer.map(true,false)){mapped.data().asIntBuffer().get(pixels);}
            return pixels;
        } finally {target.destroyBuffers();}
    }
}
