package naturality.client.blocks.snow;

import java.util.function.Predicate;
import naturality.blocks.snow.SnowGeometry;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import naturality.client.blocks.snow.SnowTextureAtlas.SnowSpriteLookup;
import naturality.client.blocks.snow.SnowTextureAtlas.SnowSpritePixels;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.SectionPos;
import java.util.Map;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.HashMap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@org.jspecify.annotations.NullMarked
public final class SurfaceSnowModel extends WrapperBlockStateModel {
    private SurfaceSnowModel(BlockStateModel wrapped) { super(wrapped); }
    public static void initialize() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
            ModelModifier.WRAP_LAST_PHASE, (model, bake) -> bake.state().is(Blocks.SNOW) ? new SurfaceSnowModel(model)
                : bake.state().isAir() || bake.state().getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
                    || bake.state().getBlock() instanceof net.minecraft.world.level.block.GrassBlock
                    ? model : new SnowOverlayModel(model)));
    }
    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return !naturality.config.gameplay.GameplaySettings.clientSnowWrapping() || SnowGeometry.usesVanillaGeometry(level,pos)
            ? wrapped.createGeometryKey(level,pos,state,random) : null;
    }
    @Override public void emitQuads(QuadEmitter e, BlockAndTintGetter level, BlockPos pos, BlockState state,
            RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        if (!naturality.config.gameplay.GameplaySettings.clientSnowWrapping() || SnowGeometry.usesVanillaGeometry(level,pos)) {
            wrapped.emitQuads(e,level,pos,state,random,cull); return;
        }
        naturality.blocks.snow.SnowGeometry.SnowGeometryCache.begin();
        try { emitCached(e,level,pos,state,random,cull); }
        finally { naturality.blocks.snow.SnowGeometry.SnowGeometryCache.end(); }
    }
    private void emitCached(QuadEmitter e,BlockAndTintGetter level,BlockPos pos,BlockState state,
            RandomSource random,Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        var material = wrapped.particleMaterial(level, pos, state);
        int leafDepth=0;
        for(int d=1;d<=7;d++) {
            var below=level.getBlockState(pos.below(d));
            if(below.is(net.minecraft.tags.BlockTags.LEAVES)){leafDepth=d;break;}
            if(!below.is(Blocks.SNOW))break;
        }
        // Use the supporting leaf's wind shelter result, not skylight at the
        // snow block. Those can differ under a canopy and make the cap drift
        // away from leaves which are correctly sheltered from wind.
        boolean exposedLeaves=leafDepth>0
            && naturality.client.systems.weather.wind.WindRendering.windExposed(level,pos.below(leafDepth));
        var shape = SnowGeometry.shape(level,pos,state.getValue(SnowLayerBlock.LAYERS));
        var visibility = new SnowFaceVisibility(level,pos,shape);
        for (var p : shape.toAabbs()) {
            SnowSectionVisibility.record(pos,p.minY);
            float x=(float)p.minX, X=(float)p.maxX, z=(float)p.minZ, Z=(float)p.maxZ, y=(float)p.minY, Y=(float)p.maxY;
            // Moving leaf caps retain all faces: a static neighbor can reveal a face as they sway.
            if (exposedLeaves || visibility.visible(p,Direction.UP)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.UP, x,y,z,X,Y,Z);
            if (exposedLeaves || visibility.visible(p,Direction.DOWN)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.DOWN, x,y,z,X,Y,Z);
            if (exposedLeaves || visibility.visible(p,Direction.NORTH)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.NORTH, x,y,z,X,Y,Z);
            if (exposedLeaves || visibility.visible(p,Direction.SOUTH)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.SOUTH, x,y,z,X,Y,Z);
            if (exposedLeaves || visibility.visible(p,Direction.WEST)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.WEST, x,y,z,X,Y,Z);
            if (exposedLeaves || visibility.visible(p,Direction.EAST)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.EAST, x,y,z,X,Y,Z);
        }
    }
    private static void face(QuadEmitter e, Material.Baked material, int leafDepth, boolean exposedLeaves, int lightShift, Direction normal, float x, float y, float z, float X, float Y, float Z) {
        for (int i=0;i<4;i++) {
            float px,py,pz;
            switch(normal) {
                case UP -> { px=i<2?x:X;py=Y;pz=i==0||i==3?z:Z; }
                case DOWN -> { px=i<2?x:X;py=y;pz=i==0||i==3?Z:z; }
                case NORTH -> { px=i<2?X:x;py=i==0||i==3?Y:y;pz=z; }
                case SOUTH -> { px=i<2?x:X;py=i==0||i==3?Y:y;pz=Z; }
                case WEST -> { px=x;py=i==0||i==3?Y:y;pz=i<2?z:Z; }
                case EAST -> { px=X;py=i==0||i==3?Y:y;pz=i<2?Z:z; }
                default -> throw new IllegalStateException();
            }
            int alpha=leafDepth==0 || !exposedLeaves ? 192 : naturality.client.systems.weather.wind.WindRendering.leafTag(px,py+leafDepth,pz)-32;
            e.pos(i,px,py,pz).color(i,(alpha<<24)|0xFFFFFF);
            float u=normal.getAxis()==Direction.Axis.X ? pz : px;
            float v=normal.getAxis()==Direction.Axis.Y ? pz : py;
            // Crop in block units; never stretch the vanilla snow grain to a narrow patch.
            e.uv(i, Math.clamp(u,0,1), normal.getAxis()==Direction.Axis.Y ? Math.clamp(v,0,1) : 1-Math.clamp(v-(float)Math.floor(y),0,1));
        }
        e.materialBake(material,MutableQuadView.BAKE_NORMALIZED);
        e.tag(0x534E0000 | ((lightShift+64)&255));
        // Vanilla snow uses the vanilla inset-face lighting stencil. Enhanced
        // shading blends another cell into fitted patches and creates seams
        // where they meet ordinary snow, especially next to local light sources.
        e.cullFace(null).shadeDirectionOverride(normal)
            .shadeMode(net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode.VANILLA).emit();
    }

    /** Snow painted into cached sprite variants on the original model faces. */
    public static final class SnowOverlayModel extends WrapperBlockStateModel {
        private static final Identifier OVERLAY = Identifier.fromNamespaceAndPath("naturality","block/snow_overlay");
        private static volatile Map<Identifier,TextureAtlasSprite> sprites=Map.of();
        private static volatile SnowSpriteLookup spriteLookup=new SnowSpriteLookup(Map.of());
        public static void atlasLoaded(Map<Identifier,TextureAtlasSprite> atlas) {
            sprites=Map.copyOf(atlas); spriteLookup=new SnowSpriteLookup(sprites);
        }
        public SnowOverlayModel(BlockStateModel wrapped) { super(wrapped); }
        @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter l,BlockPos p,BlockState s,RandomSource r) {
            return naturality.client.rendering.distantdetail.RenderDetail.reducedDetails(p)
                || l instanceof SnowRenderRegion region && !region.naturality$mayHaveSnow()
                || !naturality.config.gameplay.GameplaySettings.clientSnowWrapping()
                || !naturality.config.live.NaturalityConfig.get().effects.snowOverlays
                || naturality.blocks.snow.SnowGeometry.NoSnowSideOverlay.contains(s) || naturality.blocks.snow.SnowGeometry.SnowSupportOnly.contains(s) || snowOwner(l,p)==null
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
            if (naturality.client.rendering.distantdetail.RenderDetail.reducedDetails(pos)
                    || level instanceof SnowRenderRegion region && !region.naturality$mayHaveSnow()) {
                wrapped.emitQuads(e,level,pos,state,random,cull);
                return;
            }
            naturality.blocks.snow.SnowGeometry.SnowGeometryCache.begin();
            try { emitCached(e,level,pos,state,random,cull); }
            finally { naturality.blocks.snow.SnowGeometry.SnowGeometryCache.end(); }
        }
        private void emitCached(QuadEmitter e,BlockAndTintGetter level,BlockPos pos,BlockState state,
                RandomSource random,Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
            if (!naturality.config.gameplay.GameplaySettings.clientSnowWrapping() || !naturality.config.live.NaturalityConfig.get().effects.snowOverlays) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
            var overlay=sprites.get(OVERLAY);
            if (overlay == null || naturality.blocks.snow.SnowGeometry.NoSnowSideOverlay.contains(state)
                    || naturality.blocks.snow.SnowGeometry.SnowSupportOnly.contains(state)) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
            boolean lowerDoublePlant=state.getBlock() instanceof DoublePlantBlock
                && state.getValue(DoublePlantBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
            if(state.getBlock() instanceof GrassBlock || SnowGeometry.coveredByContinuation(level,pos) || lowerDoublePlant) {
                wrapped.emitQuads(e,level,pos,state,random,cull); return;
            }
            var owner=snowOwner(level,pos);
            if(owner==null) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
            boolean foliage=SnowGeometry.isFoliage(state);
            var tops=new ArrayList<naturality.blocks.fire.FireGeometry.FireSurface.Patch>();
            var snowBoxes=new ArrayList<net.minecraft.world.phys.AABB>();
            int offset=owner.getY()-pos.getY();
            var snowState=level.getBlockState(owner);
            for(var box:SnowGeometry.shape(level,owner,snowState.getValue(SnowLayerBlock.LAYERS)).toAabbs())
                snowBoxes.add(box.move(0,offset,0));
            for(var p:SnowGeometry.surfaces(level,owner))tops.add(new naturality.blocks.fire.FireGeometry.FireSurface.Patch(
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

    /** Vertical extent of snow meshes that reach below their owning section. */
    public static final class SnowSectionVisibility {
        private static final ConcurrentHashMap<Long, Integer> BELOW = new ConcurrentHashMap<>();
        private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();
        public static final int MAX_DROP = naturality.blocks.snow.SnowGeometry.MAX_DEPTH + 2;

        private record Capture(long section, int originY, int drop) { }
        private SnowSectionVisibility() { }

        public static void begin(SectionPos section) {
            CURRENT.set(new Capture(section.asLong(), section.minBlockY(), 0));
        }

        public static void record(BlockPos owner, double localMinY) {
            var capture = CURRENT.get();
            if (capture == null) return;
            int drop = Math.clamp((int)Math.ceil(capture.originY - owner.getY() - localMinY), 0, MAX_DROP);
            if (drop > capture.drop) CURRENT.set(new Capture(capture.section, capture.originY, drop));
        }

        public static void finish() {
            var capture = CURRENT.get();
            CURRENT.remove();
            if (capture == null) return;
            if (capture.drop == 0) BELOW.remove(capture.section);
            else BELOW.put(capture.section, capture.drop);
        }

        public static int below(long sectionNode) { return BELOW.isEmpty() ? 0 : BELOW.getOrDefault(sectionNode, 0); }
        public static void clear() { BELOW.clear(); CURRENT.remove(); }
    }

    /** Additional immutable section copies used by fitted snow on meshing workers. */
    public interface SnowRenderRegion {
        boolean naturality$mayHaveSnow();
        void naturality$markSnowFree();
        void naturality$setSnowSections(Map<Long, SectionCopy> sections);
    }

    /** Conservative client-thread palette probe; never load chunks or inspect live terrain on workers. */
    public static final class SnowRegionPresence {
        private SnowRegionPresence() {}
        public static boolean mayHaveSnow(ClientLevel level, long section) {
            if (level.isDebug()) return true;
            int x = SectionPos.x(section), y = SectionPos.y(section), z = SectionPos.z(section);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                var chunk = level.getChunkSource().getChunk(x + dx, z + dz, false);
                if (chunk == null) continue;
                for (int sy = Math.max(y - 3, level.getMinSectionY()); sy <= Math.min(y + 3, level.getMaxSectionY() - 1); sy++) {
                    var data = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (!data.hasOnlyAir() && data.maybeHas(state -> state.is(Blocks.SNOW))) return true;
                }
            }
            return false;
        }

        public static boolean hasRootedStalk(ClientLevel level, long section) {
            var chunk = level.getChunkSource().getChunk(SectionPos.x(section), SectionPos.z(section), false);
            int y = SectionPos.y(section);
            return chunk != null && y >= level.getMinSectionY() && y < level.getMaxSectionY()
                && chunk.getSection(chunk.getSectionIndexFromSectionY(y)).maybeHas(state -> state.is(Blocks.SUGAR_CANE));
        }
    }

    /** Conservative whole-face tests in mesh coordinates, including displaced snow. */
    public static final class SnowFaceVisibility {
        private static final double EPSILON = 1e-5;
        private final BlockAndTintGetter level;
        private final BlockPos owner;
        private final VoxelShape snow;
        private final Map<BlockPos, VoxelShape> occluders = new HashMap<>();

        public SnowFaceVisibility(BlockAndTintGetter level, BlockPos owner, VoxelShape snow) {
            this.level = level;
            this.owner = owner.immutable();
            this.snow = snow;
        }

        public boolean visible(AABB box, Direction face) {
            double x=box.minX, y=box.minY, z=box.minZ, X=box.maxX, Y=box.maxY, Z=box.maxZ;
            switch (face) {
                case DOWN -> { Y=y; y-=EPSILON; }
                case UP -> { y=Y; Y+=EPSILON; }
                case NORTH -> { Z=z; z-=EPSILON; }
                case SOUTH -> { z=Z; Z+=EPSILON; }
                case WEST -> { X=x; x-=EPSILON; }
                case EAST -> { x=X; X+=EPSILON; }
            }
            var bounds = new AABB(x,y,z,X,Y,Z);
            VoxelShape remaining = null;
            // Other boxes in this same fitted mesh can hide an entire box face.
            if (!snow.isEmpty() && snow.bounds().intersects(bounds)) {
                remaining = Shapes.join(Shapes.create(bounds),snow,BooleanOp.ONLY_FIRST);
                if (remaining.isEmpty()) return false;
            }
            for (int bx=(int)Math.floor(x);bx<=Math.floor(Math.nextDown(X));bx++)
                for (int by=(int)Math.floor(y);by<=Math.floor(Math.nextDown(Y));by++)
                    for (int bz=(int)Math.floor(z);bz<=Math.floor(Math.nextDown(Z));bz++) {
                        var cell = owner.offset(bx,by,bz);
                        if (cell.equals(owner)) continue;
                        var blocker = occluders.computeIfAbsent(cell,this::occluder);
                        if (!blocker.isEmpty() && blocker.bounds().intersects(bounds)) {
                            remaining = Shapes.join(remaining == null ? Shapes.create(bounds) : remaining,
                                blocker,BooleanOp.ONLY_FIRST);
                            if (remaining.isEmpty()) return false;
                        }
                        // Adjacent fitted snow can be saved beside our owner while its
                        // geometry sits in this lower physical cell (e.g. bottom slabs).
                        var adjacentOwner=owner.offset(bx,0,bz);
                        if (by!=0 && !adjacentOwner.equals(owner)) {
                            var adjacent=occluders.computeIfAbsent(adjacentOwner,this::occluder);
                            if (!adjacent.isEmpty() && adjacent.bounds().intersects(bounds)) {
                                remaining=Shapes.join(remaining == null ? Shapes.create(bounds) : remaining,
                                    adjacent,BooleanOp.ONLY_FIRST);
                                if (remaining.isEmpty()) return false;
                            }
                        }
                    }
            return true;
        }

        private VoxelShape occluder(BlockPos cell) {
            var state = level.getBlockState(cell);
            VoxelShape shape;
            if (state.is(Blocks.SNOW)) {
                // Neighboring leaf snow can move away from an otherwise shared edge.
                for (int depth=1;depth<=7;depth++) {
                    var support = level.getBlockState(cell.below(depth));
                    if (support.is(BlockTags.LEAVES)) return Shapes.empty();
                    if (!support.is(Blocks.SNOW)) break;
                }
                shape = SnowGeometry.shape(level,cell,state.getValue(SnowLayerBlock.LAYERS));
            } else {
                // Cutout/translucent blocks must never act as solid face occluders.
                if (!state.canOcclude()) return Shapes.empty();
                shape = state.getOcclusionShape();
            }
            return shape.move(cell.getX()-owner.getX(),cell.getY()-owner.getY(),cell.getZ()-owner.getZ());
        }
    }
}
