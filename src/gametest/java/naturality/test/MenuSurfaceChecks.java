package naturality.test;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import naturality.Naturality;
import naturality.client.ui.menus.previews.MenuSurface.MenuFog;
import net.minecraft.client.renderer.RenderPipelines;

/** Read back the production surface shader at a cliff, support shell and changing orbit. */
final class MenuSurfaceChecks {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Naturality.id("pipeline/menu_surface_test"))
                .withVertexShader("core/screenquad").withFragmentShader(Naturality.id("core/menu_surface_test"))
                .withBindGroupLayout(MenuFog.LAYOUT)
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MenuChecks", UniformType.UNIFORM_BUFFER)
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER).build())
                .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build());

    static void verify() {
        var device = RenderSystem.getDevice();
        var target = new TextureTarget("Menu surface oracle", 32, 1, GpuFormat.RGBA8_UNORM, null);
        float[][] positions = {{.5F,64.5F,.5F}, {1.5F,101,.5F}, {.5F,39,.5F}, {.5F,40,.5F},
                {80.5F,65,.5F}, {80.5F,120,.5F}, {1,70,.5F}, {1,39,.5F},
                {95.5F,65,0}, {97.5F,65,0}, {99,65,0}, {110,65,0}, {99,63,0}, {99,64,0}, {95,39,0}, {110,62,0},
                {.5F,48,.5F}, {.5F,64,.5F}, {1.5F,80,.5F}, {1.5F,75,.5F},
                {.5F,65,.5F}, {1,48,.5F}, {.5F,40.5F,.5F}, {.5F,38,.5F},
                {0,63,110}, {0,63,-110}, {110,63,0}, {-110,63,0},
                {110,63,1}, {110,63,-1}, {110,63,2}, {-110,63,2}};
        var inputs = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder());
        for (float[] p : positions) inputs.putFloat(p[0]).putFloat(p[1]).putFloat(p[2]).putFloat(0);
        for (int i = 0; i < 32; i++) inputs.putFloat(i == 6 || i == 7 || i == 21 ? 1 : 0).putFloat(i == 6 || i == 7 || i == 21 ? 0 : i == 20 ? -.5F : 1).putFloat(i == 20 ? 1 : 0).putFloat(0);
        inputs.flip();
        var floorLow=ByteBuffer.allocateDirect(57600).order(ByteOrder.nativeOrder());
        var floorHigh=ByteBuffer.allocateDirect(57600).order(ByteOrder.nativeOrder());
        for(int i=0;i<28800;i++){floorLow.putShort((short)128);floorHigh.putShort((short)128);}
        floorHigh.putShort(121*2,(short)164); // Adjacent high column: floor 100.
        floorLow.flip();floorHigh.flip();
        try (var low = device.createBuffer(() -> "Menu test low floors",128,floorLow);
             var high = device.createBuffer(() -> "Menu test high floors",128,floorHigh);
             var checks = device.createBuffer(() -> "Menu test inputs",128,inputs);
             var readback = device.createBuffer(() -> "Menu test results",9,128)) {
            var encoder = device.createCommandEncoder();
            for (float visibility : new float[]{1,0}) for (float[] eye : new float[][]{{0,180},{180,0},{0,-180},{-180,0}}) {
                var data = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
                for(float f:new float[]{0,0,63.36F,96, eye[0],100,eye[1],naturality.client.ui.menus.previews.MenuSurface.SURFACE_DEPTH, -120,-120,-64,240, 0,1,1,visibility})data.putFloat(f);
                data.flip();
                try(var region = device.createBuffer(() -> "Menu test region",128,data)) {
                    try(var pass = encoder.createRenderPass(() -> "Menu surface test",TestFixtures.require(target.getColorTextureView(), "target.getColorTextureView()"),Optional.empty())) {
                        pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("MenuRegion",region);
                        pass.setUniform("MenuFloorsLow",low); pass.setUniform("MenuFloorsHigh",high);
                        pass.setUniform("MenuChecks",checks); pass.draw(3,1,0,0);
                    }
                    encoder.copyTextureToBuffer(TestFixtures.require(target.getColorTexture(), "target.getColorTexture()"),readback,0,() -> {},0);
                    try(var fence=encoder.createFence()){encoder.submit();fence.awaitCompletion(-1);}
                    try(var mapped=readback.map(true,false)) {
                        double fog=1-Math.clamp((Math.hypot(80.5,.5)-63.36)/32.64,0,1);
                        double[] expected={1,1,0,0,fog,fog,1,0};
                        for(int i=0;i<8;i++) {
                            int value=mapped.data().get(i*4)&255;
                            // The production fog include quantizes the edge into its fixed pixel pattern.
                            int tolerance = visibility > 0 && (i == 4 || i == 5) ? 8 : 1;
                            if(Math.abs(value-Math.round(expected[i]*visibility*255))>tolerance)
                                throw new AssertionError("Menu surface sample "+i+", orbit visibility "+visibility+": "+value);
                        }
                        for(int i=0;i<32;i++) {
                            int extension=mapped.data().get(i*4+1)&255;
                            // Check both hemispheres and the narrow transition while orbiting
                            // through all four camera quadrants.
                            float nearSide = (positions[i][0]*eye[0]+positions[i][2]*eye[1])/180;
                            float side = Math.clamp(nearSide/2,0,1);
                            side = side*side*(3-2*side);
                            int expectedExtension=visibility==0?0:Math.round(Math.min(768, Math.max(0, (float)Math.hypot(positions[i][0],positions[i][2])-98)*64)*side/1024*255);
                            if(Math.abs(extension-expectedExtension)>1)
                                throw new AssertionError("Fog cover extension changed visible terrain or failed to extend: "+i+" -> "+extension);
                            int drop=mapped.data().get(i*4+3)&255;
                            int expectedDrop=visibility>0 && (i==12 || i==15 || i>=24)?Math.round(side*255):0;
                            if(Math.abs(drop-expectedDrop)>1) throw new AssertionError("Fog wall must extend only buried outer support: "+i+" -> "+drop);
                            int surfaceVisible=mapped.data().get(i*4+2)&255;
                            int expectedVisible=visibility==0 || i==2 || i==3 || i==7 || i==14 || i==19 || i==20 || i==23?0:255;
                            if(surfaceVisible!=expectedVisible)
                                throw new AssertionError("Surface/fog cover visibility at "+i+": "+surfaceVisible);
                        }
                    }
                }
            }
        } finally {target.destroyBuffers();}
        System.out.println("Menu surface GPU checks passed: exact cliffs, no buried support, smooth horizontal fog, invisible underside, camera-facing fog cover");
    }
    private MenuSurfaceChecks() { }
}
