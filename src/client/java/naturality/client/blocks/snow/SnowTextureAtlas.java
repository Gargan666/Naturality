package naturality.client.blocks.snow;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.*;
import java.util.function.Supplier;
import naturality.client.blocks.snow.mixins.SnowMixins.SnowAtlasAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import java.util.Optional;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lazy, shared snowy sprite variants. Meshing only requests pixels; GPU work stays on the render thread. */
@org.jspecify.annotations.NullMarked
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
        var store = current;
        if(store!=null && store.atlas==atlas) { store.close();current=null; }
    }
    public static synchronized TextureAtlasSprite sprite(Object key,TextureAtlasSprite source,TextureAtlasSprite overlay,Supplier<NativeImage> pixels) {
        return sprite(key,source,overlay,pixels,null,false);
    }
    /** Paired original/average tiles share one atlas and a constant horizontal offset. */
    public static synchronized TextureAtlasSprite lodSprite(Object key,TextureAtlasSprite source,
            Supplier<NativeImage> pixels,Supplier<NativeImage> average,boolean copySourceMips) {
        return sprite(key,source,source,pixels,average,copySourceMips);
    }
    public static synchronized void writeLodBounds(java.nio.ByteBuffer data) {
        var store=current;
        if(store==null) { data.putFloat(-1).putFloat(-1).putFloat(-1).putFloat(-1);return; }
        var s=store.reserve;
        data.putFloat(s.getU0()).putFloat((s.getV0()+s.getV1())*.5f)
            .putFloat((s.getU0()+s.getU1())*.5f).putFloat(s.getV1());
    }
    private static TextureAtlasSprite sprite(Object key,TextureAtlasSprite source,TextureAtlasSprite overlay,
            Supplier<NativeImage> pixels,@Nullable Supplier<NativeImage> average,boolean copySourceMips) {
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
        // Snow composites occupy the upper half. LOD originals and averages
        // occupy the lower-left and lower-right quarters respectively.
        boolean paired=average!=null;
        var cursor=paired?store.lodCursor:store.snowCursor;
        int regionWidth=paired?size/2:size;
        if(cursor.x+tileW>regionWidth) { cursor.x=0;cursor.y+=cursor.rowHeight;cursor.rowHeight=0; }
        if(cursor.y+tileH>size/2 || tileW>regionWidth) {
            if(!store.growing && size<store.atlas.maxSupportedTextureSize()/2) {
                store.growing=true;requestedSize=size*2;
                Minecraft.getInstance().execute(()->Minecraft.getInstance().reloadResourcePacks());
            }
            return source;
        }
        int x=Math.round(store.reserve.getU0()*dimensions.naturality$width())+cursor.x;
        int y=Math.round(store.reserve.getV0()*dimensions.naturality$height())+cursor.y+(paired?size/2:0);
        cursor.x+=tileW;cursor.rowHeight=Math.max(cursor.rowHeight,tileH);
        var access=(SnowSpritePixels)source.contents();
        var id=Identifier.fromNamespaceAndPath("naturality","snow_composite/"+store.variants.size());
        var contents=new SpriteContents(id,new FrameSize(width,height),pixels.get(),access.naturality$animation(),
            List.of(),access.naturality$textureMetadata());
        if(!copySourceMips)contents.increaseMipLevel(mip);
        var sprite=new CompositeSprite(source.atlasLocation(),contents,dimensions.naturality$width(),dimensions.naturality$height(),x,y,padding);
        var variant=new Variant(sprite,tileW,tileH,padding);
        if(average!=null) {
            variant.lod=true;
            if(copySourceMips)variant.source=source;
            var averageContents=new SpriteContents(id,new FrameSize(width,height),average.get());
            averageContents.increaseMipLevel(mip);
            var averageSprite=new CompositeSprite(source.atlasLocation(),averageContents,
                dimensions.naturality$width(),dimensions.naturality$height(),x+size/2,y,padding);
            var averageVariant = new Variant(averageSprite,tileW,tileH,padding);
            averageVariant.lod=true;
            variant.average=averageVariant;
        }
        store.variants.put(key,variant);store.pending.add(variant);
        if(!store.flushQueued) {
            store.flushQueued=true;
            Minecraft.getInstance().execute(()->flush(store));
        }
        return sprite;
    }
    public static boolean isComposite(TextureAtlasSprite sprite) { return sprite instanceof CompositeSprite; }
    public static synchronized boolean isCompositeUv(float u,float v) {
        var store = current;
        return store!=null && u>=store.reserve.getU0() && u<=store.reserve.getU1()
            && v>=store.reserve.getV0() && v<=store.reserve.getV1();
    }
    public static synchronized @Nullable TextureAtlasSprite findComposite(float u,float v) {
        var store = current;
        if(store==null)return null;
        for(var variant:store.variants.values()) {
            var sprite=variant.sprite;
            if(u>=sprite.getU0()-1e-7 && u<=sprite.getU1()+1e-7 && v>=sprite.getV0()-1e-7 && v<=sprite.getV1()+1e-7)return sprite;
        }
        return null;
    }
    private static synchronized void flush(Store store) {
        if(current!=store)return;
        store.flushQueued=false;
        for(var variant:store.pending) {
            upload(store,variant);
            if(variant.average!=null)upload(store,variant.average);
        }
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
        if(variant.lod) {
            // Preserve the source's exact mip colors and cutout coverage. Running
            // mip generation on a padded copy changes leaf alpha/color filtering.
            var source=variant.source==null?sprite:variant.source;
            var images=((SnowSpritePixels)source.contents()).naturality$mips();
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            for(int level=0;level<=mip;level++) {
                var original=images[level];
                int pad=variant.padding>>level;
                try(var padded=new NativeImage(variant.width>>level,variant.height>>level,false)) {
                    for(int y=0;y<padded.getHeight();y++)for(int x=0;x<padded.getWidth();x++)
                        padded.setPixel(x,y,original.getPixel(Math.clamp(x-pad,0,original.getWidth()-1),
                            Math.clamp(y-pad,0,original.getHeight()-1)));
                    encoder.writeToTexture(store.atlas.getTexture(),padded,level,0,sprite.getX()>>level,sprite.getY()>>level);
                }
            }
            return;
        }
        if(sprite.isAnimated()) {
            int stride=Mth.roundToward(SpriteContents.UBO_SIZE,RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment());
            var data=MemoryUtil.memAlloc(stride*(mip+1));
            try {
                sprite.uploadSpriteUbo(data,0,mip,dimensions.naturality$width(),dimensions.naturality$height(),stride);
                variant.buffer=RenderSystem.getDevice().createBuffer(()->"Snow sprite animation",128,data);
                var animation=sprite.createAnimationState(variant.buffer.slice(),stride);
                variant.animation=animation;
                if(animation!=null) {
                    var metadata=((SnowSpritePixels)sprite.contents()).naturality$animation().orElseThrow();
                    int period=metadata.frames().map(frames->frames.stream().mapToInt(frame->frame.time().orElse(metadata.defaultFrameTime())).sum())
                        .orElse(((SnowSpritePixels)sprite.contents()).naturality$image().getWidth()/sprite.contents().width()
                            *(((SnowSpritePixels)sprite.contents()).naturality$image().getHeight()/sprite.contents().height())*metadata.defaultFrameTime());
                    for(long tick=0;tick<store.ticks%Math.max(1,period);tick++)animation.tick();
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
                for(var variant:store.variants.values()) {
                    var animation = variant.animation;
                    if(animation!=null&&animation.needsToDraw()) animation.drawToAtlas(pass,animation.getDrawUbo(level));
                }
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
        SpriteContents.@Nullable AnimationState animation;
        @Nullable GpuBuffer buffer;
        @Nullable Variant average;
        @Nullable TextureAtlasSprite source;
        boolean lod;
        Variant(CompositeSprite sprite,int width,int height,int padding) { this.sprite=sprite;this.width=width;this.height=height;this.padding=padding; }
        void close() { if(animation!=null)animation.close();if(buffer!=null)buffer.close();if(average!=null)average.close();sprite.close(); }
    }
    private static final class Cursor { int x,y,rowHeight; }
    private static final class Store {
        final TextureAtlas atlas;
        final TextureAtlasSprite reserve;
        final Map<Identifier,TextureAtlasSprite> sources;
        final Map<Object,Variant> variants=new HashMap<>();
        final List<Variant> pending=new ArrayList<>();
        final Cursor snowCursor=new Cursor(),lodCursor=new Cursor();
        long ticks;
        boolean growing,flushQueued;
        Store(TextureAtlas atlas,TextureAtlasSprite reserve,Map<Identifier,TextureAtlasSprite> sources) {
            this.atlas=atlas;this.reserve=reserve;this.sources=Map.copyOf(sources);
        }
        void close() { for(var variant:variants.values())variant.close(); }
    }

    public interface SnowSpritePixels {
        NativeImage naturality$image();
        NativeImage[] naturality$mips();
        Optional<AnimationMetadataSection> naturality$animation();
        Optional<TextureMetadataSection> naturality$textureMetadata();
    }

    /** Immutable atlas-space buckets, replaced on resource reload. */
    public static final class SnowSpriteLookup {
        private static final int SIZE=64;
        private final List<List<TextureAtlasSprite>> cells=new ArrayList<>(SIZE*SIZE);
        public SnowSpriteLookup(Map<Identifier,TextureAtlasSprite> sprites) {
            for(int i=0;i<SIZE*SIZE;i++)cells.add(new ArrayList<>());
            for(var sprite:sprites.values()) {
                int x0=cell(sprite.getU0()),z0=cell(sprite.getV0());
                int x1=cell(Math.nextDown(sprite.getU1())),z1=cell(Math.nextDown(sprite.getV1()));
                for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++)cells.get(z*SIZE+x).add(sprite);
            }
        }
        private static int cell(float coordinate) { return Math.clamp((int)(coordinate*SIZE),0,SIZE-1); }
        public @Nullable TextureAtlasSprite find(float u,float v) {
            for(var sprite:cells.get(cell(v)*SIZE+cell(u)))
                if(u>=sprite.getU0() && u<sprite.getU1() && v>=sprite.getV0() && v<sprite.getV1())return sprite;
            return null;
        }
    }
}
