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
    private static volatile Map<Identifier,TextureAtlasSprite> sprites=Map.of();
    public static void atlasLoaded(Map<Identifier,TextureAtlasSprite> atlas) { sprites=Map.copyOf(atlas); }
    public SnowOverlayModel(BlockStateModel wrapped) { super(wrapped); }
    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter l,BlockPos p,BlockState s,RandomSource r) {
        return !naturality.config.GameplaySettings.clientSnowWrapping()
            || !naturality.config.NaturalityConfig.get().effects.snowOverlays || snowOwner(l,p)==null
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
    private record Side(float[][] p,float[][] uv,Direction face,@org.jspecify.annotations.Nullable TextureAtlasSprite sprite) { }
    @Override public void emitQuads(QuadEmitter e,BlockAndTintGetter level,BlockPos pos,BlockState state,
            RandomSource random,Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        if (!naturality.config.GameplaySettings.clientSnowWrapping() || !naturality.config.NaturalityConfig.get().effects.snowOverlays) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
        var overlay=sprites.get(Identifier.fromNamespaceAndPath("naturality","block/snow_overlay"));
        boolean foliage=SnowGeometry.isFoliage(state);
        var tops=new ArrayList<naturality.fire.FireSurface.Patch>();
        boolean coveredByContinuation=level.getBlockState(pos.above()).is(state.getBlock());
        boolean lowerDoublePlant=state.getBlock() instanceof DoublePlantBlock
            && state.getValue(DoublePlantBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
        if(overlay!=null && !(state.getBlock() instanceof GrassBlock)
                && !naturality.snow.SnowSupportOnly.contains(state)
                && !coveredByContinuation && !lowerDoublePlant) {
            var owner=snowOwner(level,pos);
            if(owner!=null) {
                int offset=owner.getY()-pos.getY();
                for(var p:SnowGeometry.surfaces(level,owner))tops.add(new naturality.fire.FireSurface.Patch(
                    p.x(),p.y()+offset,p.z(),p.ux(),p.uz(),p.vx(),p.vz()));
            }
        }
        if(overlay == null || tops.isEmpty()) { wrapped.emitQuads(e,level,pos,state,random,cull);return; }
        boolean leaves=state.getBlock() instanceof LeavesBlock || foliage;
        var sides=new ArrayList<Side>();
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
                for(int i=0;i<4;i++){int v=order[i];p[i]=new float[]{q.x(v),q.y(v),q.z(v)};uv[i]=new float[]{q.u(v),q.v(v)};}
                TextureAtlasSprite source=null;
                if(leaves) {
                    float u=(q.u(a)+q.u(c))/2,v=(q.v(a)+q.v(c))/2;
                    for(var sprite:sprites.values()) if(u>=sprite.getU0() && u<sprite.getU1() && v>=sprite.getV0() && v<sprite.getV1()) {source=sprite;break;}
                }
                var side=new Side(p,uv,q.lightFace(),source);
                if(foliage)sides.add(side);
                else {
                    // A stair side can span both a snowy tread and a covered
                    // section. Clip against each actual coated top interval.
                    var edges=new TreeSet<Float>();edges.add(0F);edges.add(1F);
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
                    for(int j=0;j<cuts.length-1;j++) {
                        float f=(cuts[j]+cuts[j+1])/2;
                        float x=mix(p[0][0],p[3][0],f),z=mix(p[0][2],p[3][2],f);
                        if(tops.stream().noneMatch(patch->Math.abs(patch.y()-topY)<1e-4 && patch.contains(x,z)))continue;
                        float[][] clipped=new float[4][3],mapped=new float[4][2];
                        for(int i=0;i<4;i++) {
                            int left=i==0||i==3?0:1,right=i==0||i==3?3:2;
                            float at=i<2?cuts[j]:cuts[j+1];
                            for(int k=0;k<3;k++)clipped[i][k]=mix(p[left][k],p[right][k],at);
                            for(int k=0;k<2;k++)mapped[i][k]=mix(uv[left][k],uv[right][k],at);
                        }
                        sides.add(new Side(clipped,mapped,q.lightFace(),source));
                    }
                }
                break;
            }
            return true;
        });
        try { wrapped.emitQuads(e,level,pos,state,random,cull); } finally { e.popTransform(); }
        for(var side:sides) emit(e,side,overlay,leaves);
    }
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private static void emit(QuadEmitter e,Side s,TextureAtlasSprite overlay,boolean leaves) {
        var p=s.p;float width=(float)Math.hypot(p[3][0]-p[0][0],p[3][2]-p[0][2]);
        float height=p[0][1]-p[1][1];if(width<1e-5 || height<1e-5)return;
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
        for(float offset:topOffsets)if(!leaves && Float.isFinite(offset))for(int i=0;i<=density;i++) {
            float y=offset+i/(float)density;if(y<=height)ys.add(y);
        }
        Float[] rows=ys.toArray(Float[]::new);
        for(int y=0;y<rows.length-1;y++)for(int x=0;x<sourceX.length-1;) {
            float t=rows[y],b=rows[y+1],topOffset=topOffsets[x];
            if(t<topOffset || t>=topOffset+1){x++;continue;}
            if(!visible(s,overlay,leaves,(sourceX[x]+sourceX[x+1])/2,(t+b)/2,width,height,topOffset)){x++;continue;}
            int start=x++;
            while(!leaves && x<sourceX.length-1 && topOffsets[x]==topOffset && Math.floor(sourceX[x])==Math.floor(sourceX[start])
                    && visible(s,overlay,leaves,(sourceX[x]+sourceX[x+1])/2,(t+b)/2,width,height,topOffset))x++;
            float l=sourceX[start],r=sourceX[x];
            float[] along={l,l,r,r},down={t,b,b,t};
            for(int i=0;i<4;i++) {
                float f=along[i]/width;
                // The decal sits slightly in front of the support to avoid z-fighting.
                // Overlap only its top edge with the snow skirt so oblique views
                // cannot reveal a rasterization crack between the two planes.
                float joinOverlap=!leaves && down[i]==0 ? 1F/512 : 0;
                e.pos(i,mix(p[0][0],p[3][0],f)+s.face.getStepX()/1024F,p[0][1]-down[i]+joinOverlap,
                    mix(p[0][2],p[3][2],f)+s.face.getStepZ()/1024F).color(i,-1);
                // One sampled overlay color per foliage texel: no independent
                // world-space texture grid or partial-pixel color boundaries.
                if(leaves)e.uv(i,(l+r)/(2*width),((t+b)/2-topOffset)/height);
                else e.uv(i,along[i]-(float)Math.floor(l),down[i]-topOffset);
            }
            e.materialBake(new Material.Baked(overlay,false),MutableQuadView.BAKE_NORMALIZED);
            e.tintIndex(-1).cullFace(null).shadeDirectionOverride(s.face).emit();
        }
    }
    private static Float[] cuts(float length,int density,Side side,boolean horizontal,boolean masked) {
        var edges=new TreeSet<Float>();edges.add(0F);edges.add(length);
        if(!masked)for(int i=1;i<length*density;i++)edges.add(i/(float)density);
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
        if(transparent(overlay,leaves?x/width:x-(float)Math.floor(x),leaves?(y-topOffset)/height:y-topOffset))return false;
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












