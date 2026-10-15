package naturality.test;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import naturality.Naturality;
import naturality.client.api.rendering.AtlasLocations;
import naturality.client.rendering.distantdetail.DistantTextures;
import naturality.client.blocks.snow.SnowTextureAtlas.SnowSpritePixels;
import naturality.client.blocks.snow.SnowTextureAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/** GPU readback checks the actual paired atlas upload and shared fade shader. */
final class TextureLodChecks {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Naturality.id("pipeline/texture_lod_test"))
            .withVertexShader("core/screenquad").withFragmentShader(Naturality.id("core/texture_lod_test"))
            .withBindGroupLayout(BindGroupLayout.builder()
                .withUniform("Sampler0",UniformType.COMBINED_IMAGE_SAMPLER)
                .withUniform("LodTest",UniformType.UNIFORM_BUFFER).build())
            .withDepthStencilState(Optional.empty()).withColorTargetState(ColorTargetState.DEFAULT).build());
    private TextureLodChecks() {}
    static void verify(Minecraft client) {
        for(String name:new String[]{"stone","grass_block_side_overlay","oak_leaves"})verify(client,name);
    }
    private static void verify(Minecraft client,String name) {
        var source=client.getAtlasManager().get(new net.minecraft.client.resources.model.sprite.SpriteId(
            AtlasLocations.BLOCKS,Identifier.withDefaultNamespace("block/"+name)));
        var paired=DistantTextures.variant(source,false,true).sprite();
        if(paired==source)throw new AssertionError("Atlas did not allocate pair for "+name);
        var atlas=(TextureAtlas)client.getTextureManager().getTexture(AtlasLocations.BLOCKS);
        SnowTextureAtlas.tick(atlas); // Flush both GPU uploads before sampling.
        int width=source.contents().width(),height=source.contents().height(),samples=257;
        var image=((SnowSpritePixels)source.contents()).naturality$image();
        int average=DistantTextures.average(image,width,height);
        var data=ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
        SnowTextureAtlas.writeLodBounds(data);
        data.putFloat(paired.getU0()).putFloat(paired.getV0()).putFloat(paired.getU1()).putFloat(paired.getV1());
        data.putFloat(width).putFloat(height).putFloat(0).putFloat(0).flip();
        var target=new TextureTarget("Texture fade oracle",samples,width*height,GpuFormat.RGBA8_UNORM,null);
        var device=RenderSystem.getDevice();var encoder=device.createCommandEncoder();
        try(var uniform=device.createBuffer(() -> "Texture fade inputs",128,data);
            var readback=device.createBuffer(() -> "Texture fade readback",9,samples*width*height*4L)) {
            try(var pass=encoder.createRenderPass(() -> "Texture fade test",TestFixtures.require(target.getColorTextureView(), "target.getColorTextureView()"),Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
                pass.setUniform("Sampler0",atlas.getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setUniform("LodTest",uniform);pass.draw(3,1,0,0);
            }
            encoder.copyTextureToBuffer(TestFixtures.require(target.getColorTexture(), "target.getColorTexture()"),readback,0,() -> {},0);
            try(var fence=encoder.createFence()){encoder.submit();fence.awaitCompletion(-1);}
            try(var mapped=readback.map(true,false)) {
                for(int row=0;row<width*height;row++)for(int step=0;step<samples;step++) {
                    int original=image.getPixel(row%width,row/width);
                    double distance=128+step*.5,t=Math.clamp((distance-160)/64,0,1),blend=t*t*(3-2*t);
                    for(int channel=0;channel<4;channel++) {
                        int shift=channel==3?24:16-8*channel;
                        int a=(original>>>shift)&255,b=channel==3?a:(average>>>shift)&255;
                        int expected=(int)Math.round(a+(b-a)*blend);
                        int actual=mapped.data().get((row*samples+step)*4+channel)&255;
                        if(Math.abs(actual-expected)>1)throw new AssertionError(name+" fade at "+distance
                            +" blocks, texel "+row+", channel "+channel+": "+actual+" != "+expected);
                    }
                }
            }
            System.out.println("Texture LOD GPU fade passed: "+name+", "+samples*width*height+" pixels; alpha unchanged");
        } finally {target.destroyBuffers();}
    }
}
