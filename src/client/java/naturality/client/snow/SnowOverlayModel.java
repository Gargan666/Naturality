package naturality.client.snow;

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

/** Snow drips clipped to the actual side quads underneath coated top edges. */
public final class SnowOverlayModel extends WrapperBlockStateModel {
    private static final Identifier OVERLAY = Identifier.fromNamespaceAndPath("naturality","block/snow_overlay");
    private static volatile Map<Identifier,TextureAtlasSprite> sprites=Map.of();
    private static volatile SnowSpriteLookup spriteLookup=new SnowSpriteLookup(Map.of());
    public static void atlasLoaded(Map<Identifier,TextureAtlasSprite> atlas) {
        sprites=Map.copyOf(atlas); spriteLookup=new SnowSpriteLookup(sprites);
    }
    public SnowOverlayModel(BlockStateModel wrapped) { super(wrapped); }
    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter l,BlockPos p,BlockState s,RandomSource r) {
        return !naturality.config.GameplaySettings.clientSnowWrapping()
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
        var sides=new ArrayList<Side>();
        var edgeOcclusion=new EdgeOcclusion(level,pos,snowBoxes);
        e.pushTransform(q -> {
            if(q.cullFace()!=null && cull.test(q.cullFace()))return true;
            if(q.lightFace().getAxis()==Direction.Axis.Y)return true;
            // Arrange a rectangular side as top A, bottom A, bottom B, top B.
            for(int a=0;a<4;a++) {
                int b=(a+3)%4,d=(a+1)%4,c=(a+2)%4;
                if(Math.abs(q.y(a)-q.y(b))>1e-5 || q.y(a)<=q.y(d)+1e-5
                        || Math.abs(q.y(d)-q.y(c))>1e-5)continue;
                float topY=q.y(a);

                float[][] p=new float[4][3],uv=new float[4][2];int[] order={a,d,c,b};
                for(int i=0;i<4;i++){int v=order[i];p[i][0]=q.x(v);p[i][1]=q.y(v);p[i][2]=q.z(v);uv[i][0]=q.u(v);uv[i][1]=q.v(v);}
                TextureAtlasSprite source=null;
                if(leaves) {
                    float u=(q.u(a)+q.u(c))/2,v=(q.v(a)+q.v(c))/2;
                    source=spriteLookup.find(u,v);
                }
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
                break;
            }
            return true;
        });
        try { wrapped.emitQuads(e,level,pos,state,random,cull); } finally { e.popTransform(); }
        boolean movingLeaves=state.is(net.minecraft.tags.BlockTags.LEAVES)
            && naturality.client.weather.WindRendering.windExposed(level,pos);
        for(var side:sides) emit(e,side,overlay,leaves,movingLeaves,pos,state);
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
    private static void emit(QuadEmitter e,Side s,TextureAtlasSprite overlay,boolean leaves,
            boolean movingLeaves,BlockPos pos,BlockState state) {
        var p=s.p;float width=(float)Math.hypot(p[3][0]-p[0][0],p[3][2]-p[0][2]);
        float height=p[0][1]-p[1][1];if(width<1e-5 || height<1e-5)return;
        var modelOffset=state.getOffset(pos);
        var material=new Material.Baked(overlay,false);
        int density=Math.max(overlay.contents().width(),overlay.contents().height());
        var sourceSprite = s.sprite;
        if(leaves && sourceSprite!=null)density=Math.max(density,Math.max(sourceSprite.contents().width(),sourceSprite.contents().height()));
        density=Math.min(256,density);
        // Masked foliage uses only source texel edges. Crossed
        // plant planes are diagonal: a world-space pixel grid does not match
        // their texture pixels and leaks snow into the transparent cutout.
        var sourceX=cuts(width,density,s,true,leaves);
        var sourceY=cuts(height,density,s,false,leaves);
        float[] topOffsets=new float[sourceX.length-1];
        if(leaves) {
            Arrays.fill(topOffsets,Float.POSITIVE_INFINITY);
            for(int x=0;x<topOffsets.length;x++)for(int y=0;y<sourceY.length-1;y++) {
                if(sourceVisible(s,(sourceX[x]+sourceX[x+1])/2,(sourceY[y]+sourceY[y+1])/2,width,height)) {
                    topOffsets[x]=sourceY[y];break;
                }
            }
        }
        var ys=new TreeSet<Float>();
        for(float y:sourceY)ys.add(y);
        // Non-foliage offsets are all zero; insert this identical row grid once.
        if(!leaves)for(int i=0;i<=density;i++) {
            float y=i/(float)density;if(y<=height)ys.add(y);
        }
        Float[] rows=ys.toArray(Float[]::new);
        for(int y=0;y<rows.length-1;y++)for(int x=0;x<sourceX.length-1;) {
            float t=rows[y],b=rows[y+1],topOffset=topOffsets[x];
            if(t<topOffset || t>=topOffset+1){x++;continue;}
            if(!visible(s,overlay,leaves,(sourceX[x]+sourceX[x+1])/2,(t+b)/2,width,height,topOffset)){x++;continue;}
            int start=x++;
            while(!leaves && x<sourceX.length-1 && topOffsets[x]==topOffset && Math.floor(s.textureOffset+sourceX[x])==Math.floor(s.textureOffset+sourceX[start])
                    && visible(s,overlay,leaves,(sourceX[x]+sourceX[x+1])/2,(t+b)/2,width,height,topOffset))x++;
            float l=sourceX[start],r=sourceX[x];
            for(int i=0;i<4;i++) {
                float along=i<2?l:r,down=i==0||i==3?t:b;
                float f=along/width;
                // The decal sits slightly in front of the support to avoid z-fighting.
                // Overlap only its top edge with the snow skirt so oblique views
                // cannot reveal a rasterization crack between the two planes.
                float joinOverlap=!leaves && down==0 ? 1F/512 : 0;
                float vertexX=mix(p[0][0],p[3][0],f),vertexY=p[0][1]-down+joinOverlap,
                    vertexZ=mix(p[0][2],p[3][2],f);
                int alpha=255;
                if(movingLeaves) {
                    int leafTag=naturality.client.weather.WindRendering.leafTag(
                        vertexX+(float)modelOffset.x,vertexY+(float)modelOffset.y,vertexZ+(float)modelOffset.z);
                    if(leafTag>=160 && leafTag<=191) {
                        int faceAxis=s.face.getAxis()==Direction.Axis.Z ? 1 : 0;
                        alpha=224+faceAxis*4+((leafTag-160)&3);
                    }
                }
                e.pos(i,vertexX+s.face.getStepX()/1024F,vertexY,vertexZ+s.face.getStepZ()/1024F)
                    .color(i,(alpha<<24)|0xFFFFFF);
                // One sampled overlay color per foliage texel: no independent
                // world-space texture grid or partial-pixel color boundaries.
                if(leaves)e.uv(i,(s.textureOffset+(l+r)/2)/s.textureWidth,((t+b)/2-topOffset)/height);
                else e.uv(i,s.textureOffset+along-(float)Math.floor(s.textureOffset+l),down-topOffset);
            }
            e.materialBake(material,MutableQuadView.BAKE_NORMALIZED);
            e.tintIndex(-1).cullFace(null).shadeDirectionOverride(s.face).emit();
        }
    }
    private static Float[] cuts(float length,int density,Side side,boolean horizontal,boolean masked) {
        var edges=new TreeSet<Float>();edges.add(0F);edges.add(length);
        if(!masked) {
            float offset=horizontal?side.textureOffset:0;
            for(int i=(int)Math.floor(offset*density)+1;i<(offset+length)*density;i++)edges.add(i/(float)density-offset);
        }
        if(masked && side.sprite!=null) {
            var sprite=side.sprite;
            for(int axis=0;axis<2;axis++) {
                float low=axis==0?sprite.getU0():sprite.getV0();
                float span=axis==0?sprite.getU1()-low:sprite.getV1()-low;
                int pixels=axis==0?sprite.contents().width():sprite.contents().height();
                float from=(side.uv[0][axis]-low)/span;
                float to=(side.uv[horizontal?3:1][axis]-low)/span;
                if(Math.abs(to-from)<1e-6)continue;
                for(int i=0;i<=pixels;i++) {
                    float f=(i/(float)pixels-from)/(to-from);
                    if(f>1e-6 && f<1-1e-6)edges.add(f*length);
                }
            }
        }
        return edges.toArray(Float[]::new);
    }
    private static boolean visible(Side side,TextureAtlasSprite overlay,boolean leaves,float x,float y,float width,float height,float topOffset) {
        float u=side.textureOffset+x;
        if(transparent(overlay,leaves?u/side.textureWidth:u-(float)Math.floor(u),leaves?(y-topOffset)/height:y-topOffset))return false;
        return !leaves || sourceVisible(side,x,y,width,height);
    }
    private static boolean sourceVisible(Side side,float x,float y,float width,float height) {
        if(side.sprite==null)return false;
        float a=x/width,b=y/height;
        var uv=side.uv;
        float u=mix(mix(uv[0][0],uv[3][0],a),mix(uv[1][0],uv[2][0],a),b);
        float v=mix(mix(uv[0][1],uv[3][1],a),mix(uv[1][1],uv[2][1],a),b);
        var s=side.sprite;
        if (s == null) return false;
        return !transparent(s,(u-s.getU0())/(s.getU1()-s.getU0()),(v-s.getV0())/(s.getV1()-s.getV0()));
    }
    private static boolean transparent(TextureAtlasSprite s,float u,float v) {
        var c=s.contents();return c.isTransparent(0,Math.clamp((int)(u*c.width()),0,c.width()-1),Math.clamp((int)(v*c.height()),0,c.height()-1));
    }
}












