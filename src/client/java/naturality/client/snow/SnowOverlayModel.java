package naturality.client.snow;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import java.util.function.Predicate;
import naturality.snow.SnowGeometry;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.*;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Snow painted into cached sprite variants on the original model faces. */
public final class SnowOverlayModel extends WrapperBlockStateModel {
    private static final Identifier OVERLAY = Identifier.fromNamespaceAndPath("naturality","block/snow_overlay");
    private static volatile Map<Identifier,TextureAtlasSprite> sprites=Map.of();
    private static volatile SnowSpriteLookup spriteLookup=new SnowSpriteLookup(Map.of());
    public static void atlasLoaded(Map<Identifier,TextureAtlasSprite> atlas) {
        sprites=Map.copyOf(atlas); spriteLookup=new SnowSpriteLookup(sprites);
    }
    public SnowOverlayModel(BlockStateModel wrapped) { super(wrapped); }
    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter l,BlockPos p,BlockState s,RandomSource r) {
        return l instanceof SnowRenderRegion region && !region.naturality$mayHaveSnow()
            || !naturality.config.GameplaySettings.clientSnowWrapping()
            || !naturality.config.NaturalityConfig.get().effects.snowOverlays
            || naturality.snow.NoSnowSideOverlay.contains(s) || naturality.snow.SnowSupportOnly.contains(s) || snowOwner(l,p)==null
            ? wrapped.createGeometryKey(l,p,s,r) : null;
    }
    private static @org.jspecify.annotations.Nullable BlockPos snowOwner(BlockAndTintGetter level,BlockPos pos) {
        for(int offset=1;offset<=SnowGeometry.MAX_DEPTH;offset++) {
            var candidate=pos.above(offset);
            if(level.getBlockState(candidate).is(Blocks.SNOW))return candidate;
            if(!SnowGeometry.exposesGround(level,candidate))break;
        }
        return null;
    }
    private record Side(float[][] p,float[][] uv,Direction face,@org.jspecify.annotations.Nullable TextureAtlasSprite sprite,
            float textureOffset,float textureWidth) {
        Side(float[][] p,float[][] uv,Direction face,@org.jspecify.annotations.Nullable TextureAtlasSprite sprite) {
            this(p,uv,face,sprite,0,(float)Math.hypot(p[3][0]-p[0][0],p[3][2]-p[0][2]));
        }
    }
    @Override public void emitQuads(QuadEmitter e,BlockAndTintGetter level,BlockPos pos,BlockState state,
            RandomSource random,Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        if (level instanceof SnowRenderRegion region && !region.naturality$mayHaveSnow()) {
            wrapped.emitQuads(e,level,pos,state,random,cull);
            return;
        }
        naturality.snow.SnowGeometryCache.begin();
        try { emitCached(e,level,pos,state,random,cull); }
        finally { naturality.snow.SnowGeometryCache.end(); }
    }
    private void emitCached(QuadEmitter e,BlockAndTintGetter level,BlockPos pos,BlockState state,
            RandomSource random,Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        if (!naturality.config.GameplaySettings.clientSnowWrapping() || !naturality.config.NaturalityConfig.get().effects.snowOverlays) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
        var overlay=sprites.get(OVERLAY);
        if (overlay == null || naturality.snow.NoSnowSideOverlay.contains(state)
                || naturality.snow.SnowSupportOnly.contains(state)) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
        boolean lowerDoublePlant=state.getBlock() instanceof DoublePlantBlock
            && state.getValue(DoublePlantBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
        if(state.getBlock() instanceof GrassBlock || SnowGeometry.coveredByContinuation(level,pos) || lowerDoublePlant) {
            wrapped.emitQuads(e,level,pos,state,random,cull); return;
        }
        var owner=snowOwner(level,pos);
        if(owner==null) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
        boolean foliage=SnowGeometry.isFoliage(state);
        var tops=new ArrayList<naturality.fire.FireSurface.Patch>();
        var snowBoxes=new ArrayList<net.minecraft.world.phys.AABB>();
        int offset=owner.getY()-pos.getY();
        var snowState=level.getBlockState(owner);
        for(var box:SnowGeometry.shape(level,owner,snowState.getValue(SnowLayerBlock.LAYERS)).toAabbs())
            snowBoxes.add(box.move(0,offset,0));
        for(var p:SnowGeometry.surfaces(level,owner))tops.add(new naturality.fire.FireSurface.Patch(
            p.x(),p.y()+offset,p.z(),p.ux(),p.uz(),p.vx(),p.vz()));
        if(tops.isEmpty()) { wrapped.emitQuads(e,level,pos,state,random,cull);return; }
        boolean leaves=state.getBlock() instanceof LeavesBlock || foliage;
        var edgeOcclusion=new EdgeOcclusion(level,pos,snowBoxes);
        e.pushTransform(q -> {
            if(q.cullFace()!=null && cull.test(q.cullFace()))return true;
            if(q.lightFace().getAxis()==Direction.Axis.Y)return true;
            var sides=new ArrayList<Side>();
            // Arrange a rectangular side as top A, bottom A, bottom B, top B.
            for(int a=0;a<4;a++) {
                int b=(a+3)%4,d=(a+1)%4,c=(a+2)%4;
                if(Math.abs(q.y(a)-q.y(b))>1e-5 || q.y(a)<=q.y(d)+1e-5
                        || Math.abs(q.y(d)-q.y(c))>1e-5)continue;
                float topY=q.y(a);

                float[][] p=new float[4][3],uv=new float[4][2];int[] order={a,d,c,b};
                for(int i=0;i<4;i++){int v=order[i];p[i][0]=q.x(v);p[i][1]=q.y(v);p[i][2]=q.z(v);uv[i][0]=q.u(v);uv[i][1]=q.v(v);}
                float centerU=(q.u(a)+q.u(c))/2,centerV=(q.v(a)+q.v(c))/2;
                TextureAtlasSprite source=spriteLookup.find(centerU,centerV);
                var side=new Side(p,uv,q.lightFace(),source);
                if(foliage)sides.add(side);
                else {
                    // A stair side can span both a snowy tread and a covered
                    // section. Clip against each actual coated top interval.
                    var edges=new TreeSet<Float>();edges.add(0F);edges.add(1F);
                    // Visibility can change along a side beneath a partial overhang.
                    int columns=Math.max(1,(int)Math.ceil(Math.hypot(p[3][0]-p[0][0],p[3][2]-p[0][2])*16));
                    for(int column=1;column<columns;column++)edges.add((float)column/columns);
                    for(var patch:tops)if(Math.abs(patch.y()-topY)<1e-4) {
                        float dx=p[3][0]-p[0][0],dz=p[3][2]-p[0][2];
                        if(Math.abs(dx)>1e-5)for(float bound:new float[]{patch.x(),patch.x()+patch.ux()}) {
                            float f=(bound-p[0][0])/dx;if(f>0 && f<1)edges.add(f);
                        }
                        if(Math.abs(dz)>1e-5)for(float bound:new float[]{patch.z(),patch.z()+patch.vz()}) {
                            float f=(bound-p[0][2])/dz;if(f>0 && f<1)edges.add(f);
                        }
                    }
                    Float[] cuts=edges.toArray(Float[]::new);
                    int runStart=-1;
                    for(int j=0;j<cuts.length-1;j++) {
                        float f=(cuts[j]+cuts[j+1])/2;
                        float x=mix(p[0][0],p[3][0],f),z=mix(p[0][2],p[3][2],f);
                        boolean coated=false;
                        for(var patch:tops) if(Math.abs(patch.y()-topY)<1e-4 && patch.contains(x,z)) { coated=true;break; }
                        double snowTop=topY;
                        if(coated)for(var box:snowBoxes)if(Math.abs(box.minY-topY)<1e-4
                            && x>=box.minX-1e-5 && x<=box.maxX+1e-5 && z>=box.minZ-1e-5 && z<=box.maxZ+1e-5)
                            snowTop=Math.max(snowTop,box.maxY);
                        boolean exposed=coated && edgeOcclusion.exposed(q.lightFace(),x,topY,z,snowTop);
                        if(exposed && runStart<0)runStart=j;
                        // Keep visibility sampling at every original boundary, but emit
                        // one continuous interval instead of sixteen separate meshes.
                        if(exposed && j<cuts.length-2)continue;
                        if(runStart<0)continue;
                        int end=exposed?j+1:j;
                        float[][] clipped=new float[4][3],mapped=new float[4][2];
                        for(int i=0;i<4;i++) {
                            int left=i==0||i==3?0:1,right=i==0||i==3?3:2;
                            float at=i<2?cuts[runStart]:cuts[end];
                            for(int k=0;k<3;k++)clipped[i][k]=mix(p[left][k],p[right][k],at);
                            for(int k=0;k<2;k++)mapped[i][k]=mix(uv[left][k],uv[right][k],at);
                        }
                        sides.add(new Side(clipped,mapped,q.lightFace(),source,
                            cuts[runStart]*side.textureWidth,side.textureWidth));
                        runStart=-1;
                    }
                }
                if(source!=null && !sides.isEmpty()) {
                    var tintSource=q.tintIndex()<0?null:net.minecraft.client.Minecraft.getInstance().getBlockColors().getTintSource(state,q.tintIndex());
                    int tint=tintSource==null?0xFFFFFF:tintSource.colorInWorld(state,level,pos)&0xFFFFFF;
                    var key=compositeKey(source,sides,tint,leaves);
                    var composite=SnowTextureAtlas.sprite(key,source,overlay,()->raster(source,overlay,sides,tint,leaves));
                    if(composite!=source) {
                        for(int vertex=0;vertex<4;vertex++)q.uv(vertex,
                            (q.u(vertex)-source.getU0())/(source.getU1()-source.getU0()),
                            (q.v(vertex)-source.getV0())/(source.getV1()-source.getV0()));
                        q.materialBake(new Material.Baked(composite,false),MutableQuadView.BAKE_NORMALIZED);
                        q.tintIndex(-1);
                    }
                }
                break;
            }
            return true;
        });
        try { wrapped.emitQuads(e,level,pos,state,random,cull); } finally { e.popTransform(); }
    }
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    /** Reuse the same translated blockers for all texel strips in a column. Mesh-local only. */
    private static final class EdgeOcclusion {
        private record Column(int x,int z,int bottom,int top) { }
        private final BlockAndTintGetter level;
        private final BlockPos pos;
        private final net.minecraft.world.phys.shapes.VoxelShape snow;
        private final Map<Column,net.minecraft.world.phys.shapes.VoxelShape> columns=new HashMap<>();
        private final Map<BlockPos,net.minecraft.world.phys.shapes.VoxelShape> cells=new HashMap<>();
        EdgeOcclusion(BlockAndTintGetter level,BlockPos pos,List<net.minecraft.world.phys.AABB> boxes) {
            this.level=level;this.pos=pos.immutable();
            var shape=net.minecraft.world.phys.shapes.Shapes.empty();
            for(var box:boxes)shape=net.minecraft.world.phys.shapes.Shapes.or(shape,
                net.minecraft.world.phys.shapes.Shapes.create(box));
            snow=shape;
        }
        private net.minecraft.world.phys.shapes.VoxelShape column(Column column) {
            var blockers=snow;
            for(int y=column.bottom;y<=column.top;y++) {
                var cell=pos.offset(column.x,y,column.z);
                var shape=cells.computeIfAbsent(cell,p -> {
                    var state=level.getBlockState(p);
                    var local=state.is(Blocks.SNOW)?SnowGeometry.shape(level,p,state.getValue(SnowLayerBlock.LAYERS))
                        :state.getOcclusionShape();
                    return local.move(p.getX()-pos.getX(),p.getY()-pos.getY(),p.getZ()-pos.getZ());
                });
                blockers=net.minecraft.world.phys.shapes.Shapes.or(blockers,shape);
            }
            return blockers;
        }
        boolean exposed(Direction face,double x,double bottom,double z,double top) {
            if(top<=bottom+1e-5)return false;
            double epsilon=1e-4;
            x+=face.getStepX()*epsilon;z+=face.getStepZ()*epsilon;
            var probe=net.minecraft.world.phys.shapes.Shapes.box(x-epsilon/4,bottom+epsilon,z-epsilon/4,
                x+epsilon/4,top-epsilon,z+epsilon/4);
            var key=new Column((int)Math.floor(x),(int)Math.floor(z),(int)Math.floor(bottom),(int)Math.floor(top));
            var blockers=columns.computeIfAbsent(key,this::column);
            return net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(probe,blockers,
                net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        }
    }
    private record CompositeKey(Identifier source,List<Integer> mask,int tint,boolean foliage) { }
    private static CompositeKey compositeKey(TextureAtlasSprite source,List<Side> sides,int tint,boolean foliage) {
        var mask=new ArrayList<Integer>();
        for(var side:sides) {
            for(var uv:side.uv) {
                mask.add(Math.round((uv[0]-source.getU0())/(source.getU1()-source.getU0())*65536));
                mask.add(Math.round((uv[1]-source.getV0())/(source.getV1()-source.getV0())*65536));
            }
            mask.add(Math.round(side.textureOffset*65536));mask.add(Math.round(side.textureWidth*65536));
            mask.add(Math.round((float)Math.hypot(side.p[3][0]-side.p[0][0],side.p[3][2]-side.p[0][2])*65536));
            mask.add(Math.round((side.p[0][1]-side.p[1][1])*65536));
        }
        return new CompositeKey(source.contents().name(),List.copyOf(mask),tint,foliage);
    }
    private static NativeImage raster(TextureAtlasSprite source,TextureAtlasSprite overlay,List<Side> sides,int tint,boolean foliage) {
        var original=((SnowSpritePixels)source.contents()).naturality$image();
        var snow=((SnowSpritePixels)overlay.contents()).naturality$image();
        int width=source.contents().width(),height=source.contents().height();
        var result=new NativeImage(original.getWidth(),original.getHeight(),false);
        for(int y=0;y<original.getHeight();y++)for(int x=0;x<original.getWidth();x++) {
            int color=original.getPixel(x,y);
            result.setPixel(x,y,(color&0xFF000000)
                |(((color>>>16)&255)*((tint>>>16)&255)/255)<<16
                |(((color>>>8)&255)*((tint>>>8)&255)/255)<<8
                |(color&255)*(tint&255)/255);
        }
        for(var side:sides) {
            double u0=(side.uv[0][0]-source.getU0())/(source.getU1()-source.getU0());
            double v0=(side.uv[0][1]-source.getV0())/(source.getV1()-source.getV0());
            double ua=(side.uv[3][0]-side.uv[0][0])/(source.getU1()-source.getU0());
            double va=(side.uv[3][1]-side.uv[0][1])/(source.getV1()-source.getV0());
            double ub=(side.uv[1][0]-side.uv[0][0])/(source.getU1()-source.getU0());
            double vb=(side.uv[1][1]-side.uv[0][1])/(source.getV1()-source.getV0());
            double determinant=ua*vb-va*ub;
            if(Math.abs(determinant)<1e-12)continue;
            int columns=Math.max(1,(int)Math.round(Math.max(Math.abs(ua)*width,Math.abs(va)*height)));
            double rows=Math.max(1,Math.max(Math.abs(ub)*width,Math.abs(vb)*height));
            double worldWidth=Math.hypot(side.p[3][0]-side.p[0][0],side.p[3][2]-side.p[0][2]);
            double worldHeight=side.p[0][1]-side.p[1][1];
            for(int frameY=0;frameY<original.getHeight();frameY+=height)for(int frameX=0;frameX<original.getWidth();frameX+=width) {
                double[] starts=new double[columns];Arrays.fill(starts,Double.POSITIVE_INFINITY);
                if(foliage)for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    if((original.getPixel(frameX+x,frameY+y)>>>24)==0)continue;
                    double u=(x+.5)/width-u0,v=(y+.5)/height-v0;
                    double a=(u*vb-v*ub)/determinant,b=(ua*v-va*u)/determinant;
                    if(a<0||a>1||b<0||b>1)continue;
                    int column=Math.clamp((int)(a*columns),0,columns-1);
                    starts[column]=Math.min(starts[column],Math.max(0,b-.5/rows));
                }
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    int base=original.getPixel(frameX+x,frameY+y);
                    if(foliage && (base>>>24)==0)continue;
                    double u=(x+.5)/width-u0,v=(y+.5)/height-v0;
                    double a=(u*vb-v*ub)/determinant,b=(ua*v-va*u)/determinant;
                    if(a<0||a>1||b<0||b>1)continue;
                    double snowU=side.textureOffset+a*worldWidth;
                    double snowV=foliage?b-starts[Math.clamp((int)(a*columns),0,columns-1)]:b*worldHeight;
                    snowU=foliage?snowU/side.textureWidth:snowU-Math.floor(snowU);
                    if(!Double.isFinite(snowV)||snowV<0||snowV>=1)continue;
                    int color=snow.getPixel(Math.clamp((int)(snowU*overlay.contents().width()),0,overlay.contents().width()-1),
                        Math.clamp((int)(snowV*overlay.contents().height()),0,overlay.contents().height()-1));
                    int alpha=color>>>24;
                    if(alpha==0)continue;
                    int below=result.getPixel(frameX+x,frameY+y),belowAlpha=below>>>24;
                    int outputAlpha=foliage?base>>>24:alpha+(belowAlpha*(255-alpha)+127)/255;
                    int blended=outputAlpha<<24;
                    for(int channel=0;channel<3;channel++) {
                        int shift=channel*8;
                        int over=(color>>>shift)&255,under=(below>>>shift)&255;
                        int value=foliage?(over*alpha+under*(255-alpha)+127)/255
                            :(over*alpha*255+under*belowAlpha*(255-alpha)+outputAlpha*255/2)/(outputAlpha*255);
                        blended|=Math.clamp(value,0,255)<<shift;
                    }
                    result.setPixel(frameX+x,frameY+y,blended);
                }
            }
        }
        return result;
    }
}

