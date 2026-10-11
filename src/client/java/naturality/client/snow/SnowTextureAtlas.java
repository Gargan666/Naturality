package naturality.client.snow;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.*;
import java.util.function.Supplier;
import naturality.client.mixin.SnowAtlasAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/** Lazy, shared snowy sprite variants. Meshing only requests pixels; GPU work stays on the render thread. */
public final class SnowTextureAtlas {
    public static final Identifier RESERVE=Identifier.fromNamespaceAndPath("naturality","block/snow_composite_space");
    private static int requestedSize=2048;
    private static @Nullable Store current;
    private SnowTextureAtlas() { }

    public static synchronized SpriteContents reserve(int maximum) {
        int size=Math.min(requestedSize,Integer.highestOneBit(maximum/2));
        return new SpriteContents(RESERVE,new FrameSize(size,size),new NativeImage(size,size,true));
    }
    public static synchronized void loaded(TextureAtlas atlas,Map<Identifier,TextureAtlasSprite> sprites) {
        if(current!=null)current.close();
        var reserve=sprites.get(RESERVE);
        current=reserve==null?null:new Store(atlas,reserve,sprites);
    }
    public static synchronized void retire(TextureAtlas atlas) {
        if(current!=null && current.atlas==atlas) { current.close();current=null; }
    }
    public static synchronized TextureAtlasSprite sprite(Object key,TextureAtlasSprite source,TextureAtlasSprite overlay,Supplier<NativeImage> pixels) {
        var store=current;
        if(store==null)return source;
        // A cancelled mesh task can still reference the previous resource epoch.
        // Never read its native images after the atlas has disposed of them.
        if(store.sources.get(source.contents().name())!=source || store.sources.get(overlay.contents().name())!=overlay)return source;
        var cached=store.variants.get(key);
        if(cached!=null)return cached.sprite;
        var dimensions=(SnowAtlasAccess)store.atlas;
        int mip=dimensions.naturality$mipLevel(),padding=1<<mip;
        int width=source.contents().width(),height=source.contents().height();
        int tileW=Mth.roundToward(width+padding*2,padding),tileH=Mth.roundToward(height+padding*2,padding);
        int size=store.reserve.contents().width();
        if(store.x+tileW>size) { store.x=0;store.y+=store.rowHeight;store.rowHeight=0; }
        if(store.y+tileH>size || tileW>size) {
            if(!store.growing && size<store.atlas.maxSupportedTextureSize()/2) {
                store.growing=true;requestedSize=size*2;
                Minecraft.getInstance().execute(()->Minecraft.getInstance().reloadResourcePacks());
            }
            return source;
        }
        int x=Math.round(store.reserve.getU0()*dimensions.naturality$width())+store.x;
        int y=Math.round(store.reserve.getV0()*dimensions.naturality$height())+store.y;
        store.x+=tileW;store.rowHeight=Math.max(store.rowHeight,tileH);
        var access=(SnowSpritePixels)source.contents();
        var id=Identifier.fromNamespaceAndPath("naturality","snow_composite/"+store.variants.size());
        var contents=new SpriteContents(id,new FrameSize(width,height),pixels.get(),access.naturality$animation(),
            List.of(),access.naturality$textureMetadata());
        contents.increaseMipLevel(mip);
        var sprite=new CompositeSprite(source.atlasLocation(),contents,dimensions.naturality$width(),dimensions.naturality$height(),x,y,padding);
        var variant=new Variant(sprite,tileW,tileH,padding);
        store.variants.put(key,variant);store.pending.add(variant);
        if(!store.flushQueued) {
            store.flushQueued=true;
            Minecraft.getInstance().execute(()->flush(store));
        }
        return sprite;
    }
    public static boolean isComposite(TextureAtlasSprite sprite) { return sprite instanceof CompositeSprite; }
    public static synchronized boolean isCompositeUv(float u,float v) {
        return current!=null && u>=current.reserve.getU0() && u<=current.reserve.getU1()
            && v>=current.reserve.getV0() && v<=current.reserve.getV1();
    }
    public static synchronized @Nullable TextureAtlasSprite findComposite(float u,float v) {
        if(current==null)return null;
        for(var variant:current.variants.values()) {
            var sprite=variant.sprite;
            if(u>=sprite.getU0()-1e-7 && u<=sprite.getU1()+1e-7 && v>=sprite.getV0()-1e-7 && v<=sprite.getV1()+1e-7)return sprite;
        }
        return null;
    }
    private static synchronized void flush(Store store) {
        if(current!=store)return;
        store.flushQueued=false;
        for(var variant:store.pending)upload(store,variant);
        store.pending.clear();
        draw(store);
    }
    public static synchronized void tick(TextureAtlas atlas) {
        var store=current;
        if(store==null || store.atlas!=atlas)return;
        store.ticks++;
        for(var variant:store.variants.values())if(variant.animation!=null)variant.animation.tick();
        flush(store);
    }
    private static void upload(Store store,Variant variant) {
        var sprite=variant.sprite;
        var dimensions=(SnowAtlasAccess)store.atlas;
        int mip=dimensions.naturality$mipLevel();
        if(sprite.isAnimated()) {
            int stride=Mth.roundToward(SpriteContents.UBO_SIZE,RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment());
            var data=MemoryUtil.memAlloc(stride*(mip+1));
            try {
                sprite.uploadSpriteUbo(data,0,mip,dimensions.naturality$width(),dimensions.naturality$height(),stride);
                variant.buffer=RenderSystem.getDevice().createBuffer(()->"Snow sprite animation",128,data);
                variant.animation=sprite.createAnimationState(variant.buffer.slice(),stride);
                if(variant.animation!=null) {
                    var metadata=((SnowSpritePixels)sprite.contents()).naturality$animation().orElseThrow();
                    int period=metadata.frames().map(frames->frames.stream().mapToInt(frame->frame.time().orElse(metadata.defaultFrameTime())).sum())
                        .orElse(((SnowSpritePixels)sprite.contents()).naturality$image().getWidth()/sprite.contents().width()
                            *(((SnowSpritePixels)sprite.contents()).naturality$image().getHeight()/sprite.contents().height())*metadata.defaultFrameTime());
                    for(long tick=0;tick<store.ticks%Math.max(1,period);tick++)variant.animation.tick();
                }
            } finally { MemoryUtil.memFree(data); }
            return;
        }
        var original=((SnowSpritePixels)sprite.contents()).naturality$image();
        int frame=((SnowSpritePixels)sprite.contents()).naturality$animation()
            .flatMap(metadata->metadata.frames()).filter(frames->!frames.isEmpty()).map(frames->frames.getFirst().index()).orElse(0);
        int framesAcross=original.getWidth()/sprite.contents().width();
        int frameX=frame%framesAcross*sprite.contents().width(),frameY=frame/framesAcross*sprite.contents().height();
        var padded=new NativeImage(variant.width,variant.height,false);
        for(int y=0;y<variant.height;y++)for(int x=0;x<variant.width;x++)
            padded.setPixel(x,y,original.getPixel(frameX+Math.clamp(x-variant.padding,0,sprite.contents().width()-1),
                frameY+Math.clamp(y-variant.padding,0,sprite.contents().height()-1)));
        try(var contents=new SpriteContents(sprite.contents().name(),new FrameSize(variant.width,variant.height),padded)) {
            contents.increaseMipLevel(mip);
            var images=((SnowSpritePixels)contents).naturality$mips();
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            for(int level=0;level<=mip;level++)encoder.writeToTexture(store.atlas.getTexture(),images[level],level,0,sprite.getX()>>level,sprite.getY()>>level);
        }
    }
    private static void draw(Store store) {
        boolean dirty=store.variants.values().stream().anyMatch(v->v.animation!=null&&v.animation.needsToDraw());
        if(!dirty)return;
        var dimensions=(SnowAtlasAccess)store.atlas;
        for(int level=0;level<=dimensions.naturality$mipLevel();level++) {
            try(var view=RenderSystem.getDevice().createTextureView(store.atlas.getTexture(),level,1);
                var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"Snow sprite animation",view,Optional.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                for(var variant:store.variants.values())if(variant.animation!=null&&variant.animation.needsToDraw())
                    variant.animation.drawToAtlas(pass,variant.animation.getDrawUbo(level));
            }
        }
    }
    private static final class CompositeSprite extends TextureAtlasSprite {
        CompositeSprite(Identifier atlas,SpriteContents contents,int width,int height,int x,int y,int padding) {
            super(atlas,contents,width,height,x,y,padding);
        }
    }
    private static final class Variant {
        final CompositeSprite sprite;
        final int width,height,padding;
        @Nullable SpriteContents.AnimationState animation;
        @Nullable GpuBuffer buffer;
        Variant(CompositeSprite sprite,int width,int height,int padding) { this.sprite=sprite;this.width=width;this.height=height;this.padding=padding; }
        void close() { if(animation!=null)animation.close();if(buffer!=null)buffer.close();sprite.close(); }
    }
    private static final class Store {
        final TextureAtlas atlas;
        final TextureAtlasSprite reserve;
        final Map<Identifier,TextureAtlasSprite> sources;
        final Map<Object,Variant> variants=new HashMap<>();
        final List<Variant> pending=new ArrayList<>();
        int x,y,rowHeight;
        long ticks;
        boolean growing,flushQueued;
        Store(TextureAtlas atlas,TextureAtlasSprite reserve,Map<Identifier,TextureAtlasSprite> sources) {
            this.atlas=atlas;this.reserve=reserve;this.sources=Map.copyOf(sources);
        }
        void close() { for(var variant:variants.values())variant.close(); }
    }
}
