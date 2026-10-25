package naturality.client.api.rendering.layers;
import naturality.client.blocks.snow.SnowTextureAtlas;

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
public final class SurfaceLayerModel extends WrapperBlockStateModel {
    public static boolean isLayer(BlockState state) {
        return state.is(Blocks.SNOW) || naturality.blocks.ash.Ash.isLayer(state);
    }
    private static boolean wrapping(BlockState state) {
        return naturality.blocks.ash.Ash.isLayer(state)
            || naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()
                && naturality.config.gameplay.GameplaySettings.clientSnowWrapping();
    }
    private static boolean plainGeometry(BlockAndTintGetter level, BlockPos pos) {
        var material = level.getBlockState(pos).getBlock();
        return naturality.api.geometry.SurfaceLayers.usesVanillaGeometry(level,pos,material)
            || naturality.blocks.ash.Ash.isLayer(level.getBlockState(pos))
                && naturality.api.geometry.SurfaceLayers.surfaces(level,pos,material).isEmpty();
    }
    private static VoxelShape layerShape(BlockAndTintGetter level, BlockPos pos, int layers) {
        return naturality.api.geometry.SurfaceLayers.shape(level,pos,layers,level.getBlockState(pos).getBlock());
    }
    private static List<naturality.blocks.fire.FireGeometry.FireSurface.Patch> layerSurfaces(BlockAndTintGetter level, BlockPos pos) {
        return naturality.api.geometry.SurfaceLayers.surfaces(level,pos,level.getBlockState(pos).getBlock());
    }
    private SurfaceLayerModel(BlockStateModel wrapped) { super(wrapped); }
    public static void initialize() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
            ModelModifier.WRAP_LAST_PHASE, (model, bake) -> {
                BlockState state = bake.state();
                BlockStateModel coated = isLayer(state) ? new SurfaceLayerModel(model)
                    : state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
                        ? model : new SnowOverlayModel(model);
                if (state.getBlock() instanceof SnowLayerBlock
                        || state.getBlock() instanceof naturality.api.blocks.FallingLayerBlock)
                    coated = new LayerLightingModel(coated);
                return naturality.blocks.ash.Ash.isLayer(state)
                    || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(AshTopRotationModel.ASH_BLOCK)
                    ? new AshTopRotationModel(coated) : coated;
            }));
    }

    /** Partial layer tops must not inherit full-cube corner occlusion from adjacent terrain. */
    private static final class LayerLightingModel extends WrapperBlockStateModel {
        private LayerLightingModel(BlockStateModel wrapped) { super(wrapped); }

        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
            emitter.pushTransform(quad -> {
                // Enhanced lighting interpolates inset faces at their actual height.
                // Disabling AO also disables smooth lightmaps, causing flat-lit tops.
                quad.ambientOcclusion(net.fabricmc.fabric.api.util.TriState.DEFAULT)
                    .shadeMode((quad.tag() & 0xFFFFFF00)==0x534E0000
                        ? net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode.VANILLA
                        : net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode.ENHANCED);
                return true;
            });
            try { wrapped.emitQuads(emitter,level,pos,state,random,cull); }
            finally { emitter.popTransform(); }
        }
    }

    /** Rotate only the ash sprite's upward faces, including the patches emitted by fitted layers. */
    private static final class AshTopRotationModel extends WrapperBlockStateModel {
        private static final Identifier ASH_BLOCK = Identifier.fromNamespaceAndPath("naturality","ash_block");
        private static final Identifier ASH_SPRITE = Identifier.fromNamespaceAndPath("naturality","block/ash");
        private record Key(Object geometry, int turns) {}

        private AshTopRotationModel(BlockStateModel wrapped) { super(wrapped); }

        private static int turns(BlockState state, BlockPos pos) {
            return (int)(state.getSeed(pos) >>> 16) & 3;
        }

        @Override
        public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos,
                BlockState state, RandomSource random) {
            Object geometry = wrapped.createGeometryKey(level,pos,state,random);
            return geometry == null ? null : new Key(geometry,turns(state,pos));
        }

        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
            int turns = turns(state,pos);
            if (turns == 0) { wrapped.emitQuads(emitter,level,pos,state,random,cull); return; }
            emitter.pushTransform(quad -> {
                if (quad.lightFace() != Direction.UP) return true;
                TextureAtlasSprite sprite = SnowOverlayModel.spriteLookup.find(
                    (quad.u(0)+quad.u(2))*.5F,(quad.v(0)+quad.v(2))*.5F);
                if (sprite == null || !sprite.contents().name().equals(ASH_SPRITE)) return true;
                float width = sprite.getU1()-sprite.getU0(), height = sprite.getV1()-sprite.getV0();
                for (int vertex = 0; vertex < 4; vertex++) {
                    float u = (quad.u(vertex)-sprite.getU0())/width;
                    float v = (quad.v(vertex)-sprite.getV0())/height;
                    for (int turn = 0; turn < turns; turn++) {
                        float previousU = u;
                        u = 1-v;
                        v = previousU;
                    }
                    quad.uv(vertex,sprite.getU0()+u*width,sprite.getV0()+v*height);
                }
                return true;
            });
            try { wrapped.emitQuads(emitter,level,pos,state,random,cull); }
            finally { emitter.popTransform(); }
        }
    }
    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return !wrapping(state) || !level.getBlockState(pos).is(state.getBlock()) || plainGeometry(level,pos)
            ? wrapped.createGeometryKey(level,pos,state,random) : null;
    }
    @Override public void emitQuads(QuadEmitter e, BlockAndTintGetter level, BlockPos pos, BlockState state,
            RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        if (!wrapping(state) || !level.getBlockState(pos).is(state.getBlock()) || plainGeometry(level,pos)) {
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
            if(!below.is(state.getBlock()))break;
        }
        // Use the supporting leaf's wind shelter result, not skylight at the
        // snow block. Those can differ under a canopy and make the cap drift
        // away from leaves which are correctly sheltered from wind.
        boolean exposedLeaves=leafDepth>0
            && naturality.client.systems.weather.wind.WindRendering.windExposed(level,pos.below(leafDepth));
        var shape = layerShape(level,pos,state.getValue(SnowLayerBlock.LAYERS));
        var visibility = new SnowFaceVisibility(level,pos,shape);
        for (var p : shape.toAabbs()) {
            if (!CoatingMeshes.visible(level,pos,p.minY)) continue;
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
        private static final Identifier ASH_OVERLAY = Identifier.fromNamespaceAndPath("naturality","block/ash_overlay");
        private static volatile Map<Identifier,TextureAtlasSprite> sprites=Map.of();
        private static volatile SnowSpriteLookup spriteLookup=new SnowSpriteLookup(Map.of());
        public static void atlasLoaded(Map<Identifier,TextureAtlasSprite> atlas) {
            sprites=Map.copyOf(atlas); spriteLookup=new SnowSpriteLookup(sprites);
        }
        public SnowOverlayModel(BlockStateModel wrapped) { super(wrapped); }
        @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter l,BlockPos p,BlockState s,RandomSource r) {
            return naturality.client.rendering.distantdetail.RenderDetail.reducedDetails(p)
                || l instanceof SnowRenderRegion region && !region.naturality$mayHaveSnow()
                || !overlayAllowed(l,p,s)
                ? wrapped.createGeometryKey(l,p,s,r) : null;
        }
        private static boolean overlayAllowed(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            var owner = snowOwner(level,pos);
            if (owner == null) return false;
            if (naturality.blocks.ash.Ash.isLayer(level.getBlockState(owner))) return !state.is(naturality.blocks.ash.Ash.NO_OVERLAYS);
            return naturality.config.gameplay.GameplaySettings.clientSnowWrapping()
                && naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()
                && naturality.config.live.NaturalityConfig.get().effects.snowOverlays
                && !SnowGeometry.NoSnowSideOverlay.contains(state) && !SnowGeometry.SnowSupportOnly.contains(state);
        }
        private static @org.jspecify.annotations.Nullable BlockPos snowOwner(BlockAndTintGetter level,BlockPos pos) {
            for(int offset=1;offset<=SnowGeometry.MAX_DEPTH;offset++) {
                var candidate=pos.above(offset);
                if(isLayer(level.getBlockState(candidate)) && wrapping(level.getBlockState(candidate)))return candidate;
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
            var owner=snowOwner(level,pos);
            if(owner==null || !overlayAllowed(level,pos,state)) { wrapped.emitQuads(e,level,pos,state,random,cull);return; }
            boolean ash=naturality.blocks.ash.Ash.isLayer(level.getBlockState(owner));
            var overlay=sprites.get(ash ? ASH_OVERLAY : OVERLAY);
            if (overlay == null) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
            boolean lowerDoublePlant=state.getBlock() instanceof DoublePlantBlock
                && state.getValue(DoublePlantBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
            if(!ash && state.getBlock() instanceof GrassBlock || SnowGeometry.coveredByContinuation(level,pos) || lowerDoublePlant) {
                wrapped.emitQuads(e,level,pos,state,random,cull); return;
            }
            boolean foliage=SnowGeometry.isFoliage(state);
            var tops=new ArrayList<naturality.blocks.fire.FireGeometry.FireSurface.Patch>();
            var snowBoxes=new ArrayList<net.minecraft.world.phys.AABB>();
            int offset=owner.getY()-pos.getY();
            var snowState=level.getBlockState(owner);
            for(var box:layerShape(level,owner,snowState.getValue(SnowLayerBlock.LAYERS)).toAabbs())
                snowBoxes.add(box.move(0,offset,0));
            for(var p:layerSurfaces(level,owner))tops.add(new naturality.blocks.fire.FireGeometry.FireSurface.Patch(
                p.x(),p.y()+offset,p.z(),p.ux(),p.uz(),p.vx(),p.vz()));
            if(tops.isEmpty()) { wrapped.emitQuads(e,level,pos,state,random,cull);return; }
            boolean leaves=state.getBlock() instanceof LeavesBlock || foliage;
            var edgeOcclusion=new EdgeOcclusion(level,pos,owner,snowBoxes);
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
                        var key=compositeKey(source,overlay,sides,tint,leaves);
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
            private final BlockPos owner;
            private final boolean internalBarFaces;
            private final net.minecraft.world.phys.shapes.VoxelShape snow;
            private final Map<Column,net.minecraft.world.phys.shapes.VoxelShape> columns=new HashMap<>();
            private final Map<BlockPos,net.minecraft.world.phys.shapes.VoxelShape> cells=new HashMap<>();
            EdgeOcclusion(BlockAndTintGetter level,BlockPos pos,BlockPos owner,List<net.minecraft.world.phys.AABB> boxes) {
                this.level=level;this.pos=pos.immutable();
                this.owner=owner.immutable();
                internalBarFaces=level.getBlockState(pos).is(Blocks.IRON_BARS);
                var shape=net.minecraft.world.phys.shapes.Shapes.empty();
                for(var box:boxes)shape=net.minecraft.world.phys.shapes.Shapes.or(shape,
                    net.minecraft.world.phys.shapes.Shapes.create(box));
                snow=shape;
            }
            private net.minecraft.world.phys.shapes.VoxelShape column(Column column) {
                // Iron-bar faces lie inside their collision-shaped coating. That
                // coating must not hide its own painted overlay on the centre
                // planes; neighboring blocks still participate in occlusion.
                var blockers=internalBarFaces
                    ? net.minecraft.world.phys.shapes.Shapes.empty() : snow;
                for(int y=column.bottom;y<=column.top;y++) {
                    var cell=pos.offset(column.x,y,column.z);
                    var shape=cells.computeIfAbsent(cell,p -> {
                        if(internalBarFaces && p.equals(owner)) return net.minecraft.world.phys.shapes.Shapes.empty();
                        var state=level.getBlockState(p);
                        var local=isLayer(state)?layerShape(level,p,state.getValue(SnowLayerBlock.LAYERS))
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
        private record CompositeKey(Identifier source,Identifier overlay,List<Integer> mask,int tint,boolean foliage) { }
        private static CompositeKey compositeKey(TextureAtlasSprite source,TextureAtlasSprite overlay,List<Side> sides,int tint,boolean foliage) {
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
            return new CompositeKey(source.contents().name(),overlay.contents().name(),List.copyOf(mask),tint,foliage);
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

    /** Optional renderer sections expose only their already-cloned state and copied light. */
    public interface CoatingSectionAccess {
        CoatingSection naturality$coatingSection();
    }

    public record CoatingSection(net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable PalettedContainerRO<BlockState> blocks,
            net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer blockLight,
            net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer skyLight,
            Map<Integer, net.minecraft.world.level.block.entity.BlockEntity> entities) {
        public CoatingSection(net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable PalettedContainerRO<BlockState> blocks,
                net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer blockLight,
                net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer skyLight,
                Map<Integer, net.minecraft.world.level.block.entity.BlockEntity> entities) {
            // Sodium omits all-air palettes and the SKY layer in dimensions without sky.
            // Preserve its AIR/zero defaults while owning copies of published light.
            this.blocks = blocks;
            this.blockLight = blockLight == null ? null : blockLight.copy();
            this.skyLight = skyLight == null ? null : skyLight.copy();
            this.entities = Map.copyOf(entities);
        }
        public BlockState block(int x, int y, int z) {
            return blocks == null ? Blocks.AIR.defaultBlockState() : blocks.get(x & 15, y & 15, z & 15);
        }
        public int light(net.minecraft.world.level.LightLayer layer, BlockPos pos) {
            var data = layer == net.minecraft.world.level.LightLayer.SKY ? skyLight : blockLight;
            return data == null ? 0 : data.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
        public net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity entity(int x, int y, int z) {
            return entities.get((y & 15) << 8 | (z & 15) << 4 | (x & 15));
        }
    }

    /** Travels with one queued build, then its worker-owned slice; never retained globally. */
    public record CoatingSnapshot(SectionPos origin, Map<Long, CoatingSection> sections) {
        public static final CoatingSnapshot EMPTY = new CoatingSnapshot(SectionPos.of(0,0,0),Map.of());
        public CoatingSnapshot { sections = Map.copyOf(sections); }
        public @org.jspecify.annotations.Nullable CoatingSection section(int x, int y, int z) {
            return sections.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        }
        public BlockState block(int x, int y, int z) {
            var section = section(x, y, z);
            return section == null ? Blocks.AIR.defaultBlockState() : section.block(x, y, z);
        }
        public int light(net.minecraft.world.level.LightLayer layer, BlockPos pos) {
            var section = section(pos.getX(), pos.getY(), pos.getZ());
            return section == null ? 0 : section.light(layer, pos);
        }
        public net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity entity(int x, int y, int z) {
            var section = section(x, y, z);
            return section == null ? null : section.entity(x, y, z);
        }

        /** LevelSlice.prepare runs before job submission, on the client thread. */
        public static CoatingSnapshot capture(ClientLevel level, SectionPos origin, Object sodiumCache) {
            if (!net.minecraft.client.Minecraft.getInstance().isSameThread())
                throw new IllegalStateException("Coating snapshots must be captured before meshing workers start");
            if (!SnowRegionPresence.mayHaveSnow(level, origin.asLong())) return EMPTY;
            var sections = new HashMap<Long, CoatingSection>();
            // Sodium's native read volume is only the section plus two blocks.
            // Capture full neighboring sections for the 32-block support/owner
            // search and its AO margin, reusing Sodium's invalidated clone cache.
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int x = origin.x() + dx, z = origin.z() + dz;
                if (level.getChunkSource().getChunk(x, z, false) == null) continue;
                for (int y = Math.max(origin.y() - 3, level.getMinSectionY());
                        y <= Math.min(origin.y() + 3, level.getMaxSectionY() - 1); y++) {
                    var section = SodiumSections.acquire(sodiumCache, x, y, z);
                    sections.put(SectionPos.asLong(x, y, z), section.naturality$coatingSection());
                }
            }
            return new CoatingSnapshot(origin,sections);
        }
    }

    public interface CoatingSnapshotAccess {
        CoatingSnapshot naturality$coatingSnapshot();
        void naturality$coatingSnapshot(CoatingSnapshot snapshot);
    }

    /** Sodium's array cache inherits its view and uncached computation from another class. */
    public interface CoatingLightAccess {
        boolean naturality$coatingLightView();
        int naturality$computeLight(int x,int y,int z);
    }

    /** The terrain renderer keeps the saved layer's state, offset and material during replay. */
    public interface CoatingRenderer {
        void naturality$renderCoating(BlockStateModel model,BlockState state,BlockPos owner,BlockPos relative);
    }

    public interface CoatingRenderView {
        BlockAndTintGetter naturality$coatingView();
    }

    /**
     * Sodium's compact coordinates cover only [-8,24), and visibility belongs to the mesh section.
     * Emit a displaced volume once from its physical section, using the original layer model and
     * renderer state. Appending it to a support model would inherit that model's offset and lighting.
     */
    public static final class CoatingMeshes {
        private static final ThreadLocal<@org.jspecify.annotations.Nullable Integer> DESTINATION = new ThreadLocal<>();
        private CoatingMeshes() {}

        private static int physicalSection(BlockPos owner,double localMinY) {
            return SectionPos.blockToSectionCoord((int)Math.floor(owner.getY()+localMinY+1e-5));
        }

        private static boolean visible(BlockAndTintGetter view,BlockPos owner,double localMinY) {
            if (!(view instanceof CoatingSnapshotAccess)) return true;
            var destination=DESTINATION.get();
            int section=destination==null ? SectionPos.blockToSectionCoord(owner.getY()) : destination;
            return physicalSection(owner,localMinY)==section;
        }

        /** Runs before the prepared renderer releases its buffers; all reads use the worker snapshot. */
        public static void append(CoatingRenderer renderer,BlockAndTintGetter view) {
            if (!(view instanceof CoatingSnapshotAccess access)) return;
            var snapshot=access.naturality$coatingSnapshot();
            if (snapshot.sections().isEmpty()) return;
            var origin=snapshot.origin();
            var models=net.minecraft.client.Minecraft.getInstance().getModelManager().getBlockStateModelSet();
            var previous=DESTINATION.get();
            DESTINATION.set(origin.y());
            try {
                // Coatings only fall downward. Two sections above contain every owner capable of
                // reaching this section; palette checks avoid scanning coating-free columns.
                for (int sy=origin.y()+1;sy<=origin.y()+2;sy++) {
                    var section=snapshot.sections().get(SectionPos.asLong(origin.x(),sy,origin.z()));
                    if (section==null) continue;
                    var blocks=section.blocks();
                    if (blocks==null || !blocks.maybeHas(SurfaceLayerModel::isLayer)) continue;
                    for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                        var state=section.block(x,y,z);
                        if (!isLayer(state) || !wrapping(state)) continue;
                        var owner=new BlockPos(origin.minBlockX()+x,sy*16+y,origin.minBlockZ()+z);
                        if (plainGeometry(view,owner)) continue;
                        boolean reaches=false;
                        for (var box:layerShape(view,owner,state.getValue(SnowLayerBlock.LAYERS)).toAabbs()) {
                            if (physicalSection(owner,box.minY)==origin.y()) { reaches=true;break; }
                        }
                        if (reaches) renderer.naturality$renderCoating(models.get(state),state,owner,owner.subtract(origin.origin()));
                    }
                }
            } finally {
                if (previous==null) DESTINATION.remove();else DESTINATION.set(previous);
            }
        }

        /** Iris normally sets this at the task-loop callsite, which an appended owner does not visit. */
        public static void shaderMetadata(Object renderer,BlockState state,BlockPos relative) {
            var metadata=ShaderMetadata.ACCESS.get(renderer.getClass());
            if (metadata.isEmpty()) return;
            try {
                var bridge=metadata.get();
                var ids=(it.unimi.dsi.fastutil.objects.@org.jspecify.annotations.Nullable Object2IntMap<?>)bridge.ids.invokeExact();
                // The installed Iris renderer also exists while shader packs are disabled.
                if (ids==null) return;
                bridge.begin.invokeExact(renderer,ids.getOrDefault(state,-1),(byte)0,(byte)state.getLightEmission(),
                    relative.getX(),relative.getY(),relative.getZ());
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Cannot prepare Iris coating metadata",failure);
            }
        }

        /** No Iris class is resolved unless its injected public renderer entry point exists. */
        private record ShaderMetadata(java.lang.invoke.MethodHandle ids,java.lang.invoke.MethodHandle begin) {
            private static final ClassValue<Optional<ShaderMetadata>> ACCESS=new ClassValue<>() {
                @Override protected Optional<ShaderMetadata> computeValue(@org.jspecify.annotations.Nullable Class<?> type) {
                    java.util.Objects.requireNonNull(type);
                    java.lang.reflect.Method begin;
                    try { begin=type.getMethod("beginBlock",int.class,byte.class,byte.class,int.class,int.class,int.class); }
                    catch (NoSuchMethodException absent) { return Optional.empty(); }
                    try {
                        var settingsType=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings",false,type.getClassLoader());
                        var settings=java.util.Objects.requireNonNull(settingsType.getField("INSTANCE").get(null));
                        var lookup=java.lang.invoke.MethodHandles.publicLookup();
                        return Optional.of(new ShaderMetadata(
                            lookup.unreflect(settingsType.getMethod("getBlockStateIds")).bindTo(settings)
                                .asType(java.lang.invoke.MethodType.methodType(it.unimi.dsi.fastutil.objects.Object2IntMap.class)),
                            lookup.unreflect(begin).asType(java.lang.invoke.MethodType.methodType(void.class,
                                Object.class,int.class,byte.class,byte.class,int.class,int.class,int.class))));
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException("Unsupported Iris terrain metadata entry point",failure);
                    }
                }
            };
        }
    }

    /** Resolve once; this optional cache API is used only while capturing a build. */
    private static final class SodiumSections {
        private static final ClassValue<java.lang.invoke.MethodHandle> ACQUIRE = new ClassValue<>() {
            @Override protected java.lang.invoke.MethodHandle computeValue(@org.jspecify.annotations.Nullable Class<?> type) {
                java.util.Objects.requireNonNull(type);
                try {
                    return java.lang.invoke.MethodHandles.publicLookup()
                        .unreflect(type.getMethod("acquire", int.class, int.class, int.class))
                        .asType(java.lang.invoke.MethodType.methodType(CoatingSectionAccess.class,
                            Object.class, int.class, int.class, int.class));
                } catch (NoSuchMethodException | IllegalAccessException failure) {
                    throw new IllegalStateException("Unsupported Sodium section snapshot cache", failure);
                }
            }
        };
        private static CoatingSectionAccess acquire(Object cache, int x, int y, int z) {
            try {
                return (CoatingSectionAccess) ACQUIRE.get(cache.getClass()).invokeExact(cache, x, y, z);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Cannot capture Sodium coating section", failure);
            }
        }
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
                    if (!data.hasOnlyAir() && data.maybeHas(SurfaceLayerModel::isLayer)) return true;
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

    /** Coating meshes depend on owners/supports outside vanilla's one-block rebuild neighborhood. */
    public static final class Updates {
        private Updates() {}

        @FunctionalInterface
        public interface DirtySection {
            void mark(int x, int y, int z);
        }

        public static void changed(ClientLevel level, BlockPos pos, BlockState before, BlockState after, DirtySection dirty) {
            if (before == after) return;
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            int depth = naturality.api.geometry.SurfaceLayers.MAX_DEPTH;
            boolean ownerChanged = isLayer(before) || isLayer(after);
            if (ownerChanged) {
                // Consult the old state too: after removal no palette/owner lookup can find its old overlays.
                lowest = pos.getY() - depth - 1;
                highest = pos.getY() + 1;
            } else {
                // A support or side occluder can change fitted layers above it. Probe section palettes first;
                // ordinary edits in coating-free terrain must not scan 32 blocks in each neighboring column.
                int minSectionY = Math.max(level.getMinSectionY(),SectionPos.blockToSectionCoord(pos.getY()-1));
                int maxSectionY = Math.min(level.getMaxSectionY()-1,SectionPos.blockToSectionCoord(pos.getY()+depth+1));
                for (int z = (pos.getZ()-1)>>4; z <= (pos.getZ()+1)>>4; z++) {
                    for (int x = (pos.getX()-1)>>4; x <= (pos.getX()+1)>>4; x++) {
                        var chunk = level.getChunkSource().getChunkNow(x,z);
                        if (chunk == null) continue;
                        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
                            var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                            if (!section.maybeHas(SurfaceLayerModel::isLayer)) continue;
                            int minY = Math.max(pos.getY()-1,sy*16);
                            int maxY = Math.min(pos.getY()+depth+1,sy*16+15);
                            for (int cz = Math.max(pos.getZ()-1,z*16); cz <= Math.min(pos.getZ()+1,z*16+15); cz++) {
                                for (int cx = Math.max(pos.getX()-1,x*16); cx <= Math.min(pos.getX()+1,x*16+15); cx++) {
                                    for (int y = minY; y <= maxY; y++) {
                                        if (!isLayer(section.getBlockState(cx&15,y&15,cz&15))) continue;
                                        lowest = Math.min(lowest,y-depth-1);
                                        highest = Math.max(highest,y+1);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (lowest == Integer.MAX_VALUE) return;
            int minY = Math.max(level.getMinSectionY(),lowest>>4);
            int maxY = Math.min(level.getMaxSectionY()-1,highest>>4);
            int margin = ownerChanged ? 1 : 2;
            for (int z = (pos.getZ()-margin)>>4; z <= (pos.getZ()+margin)>>4; z++) {
                for (int x = (pos.getX()-margin)>>4; x <= (pos.getX()+margin)>>4; x++) {
                    for (int y = minY; y <= maxY; y++) dirty.mark(x,y,z);
                }
            }
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
            if (isLayer(state)) {
                // Neighboring leaf snow can move away from an otherwise shared edge.
                for (int depth=1;depth<=7;depth++) {
                    var support = level.getBlockState(cell.below(depth));
                    if (support.is(BlockTags.LEAVES)) return Shapes.empty();
                    if (!support.is(state.getBlock())) break;
                }
                shape = layerShape(level,cell,state.getValue(SnowLayerBlock.LAYERS));
            } else {
                // Cutout/translucent blocks must never act as solid face occluders.
                if (!state.canOcclude()) return Shapes.empty();
                shape = state.getOcclusionShape();
            }
            return shape.move(cell.getX()-owner.getX(),cell.getY()-owner.getY(),cell.getZ()-owner.getZ());
        }
    }
}
