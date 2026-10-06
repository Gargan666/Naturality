package naturality.client.snow;

import java.util.function.Predicate;
import naturality.snow.SnowGeometry;
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
        return !naturality.config.GameplaySettings.clientSnowWrapping() || SnowGeometry.usesVanillaGeometry(level,pos)
            ? wrapped.createGeometryKey(level,pos,state,random) : null;
    }
    @Override public void emitQuads(QuadEmitter e, BlockAndTintGetter level, BlockPos pos, BlockState state,
            RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        if (!naturality.config.GameplaySettings.clientSnowWrapping() || SnowGeometry.usesVanillaGeometry(level,pos)) {
            wrapped.emitQuads(e,level,pos,state,random,cull); return;
        }
        naturality.snow.SnowGeometryCache.begin();
        try { emitCached(e,level,pos,state,random,cull); }
        finally { naturality.snow.SnowGeometryCache.end(); }
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
            && naturality.client.weather.WindRendering.windExposed(level,pos.below(leafDepth));
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
            int alpha=leafDepth==0 || !exposedLeaves ? 192 : naturality.client.weather.WindRendering.leafTag(px,py+leafDepth,pz)-32;
            e.pos(i,px,py,pz).color(i,(alpha<<24)|0xFFFFFF);
            float u=normal.getAxis()==Direction.Axis.X ? pz : px;
            float v=normal.getAxis()==Direction.Axis.Y ? pz : py;
            // Crop in block units; never stretch the vanilla snow grain to a narrow patch.
            e.uv(i, Math.clamp(u,0,1), normal.getAxis()==Direction.Axis.Y ? Math.clamp(v,0,1) : 1-Math.clamp(v-(float)Math.floor(y),0,1));
        }
        e.materialBake(material,MutableQuadView.BAKE_NORMALIZED);
        e.tag(0x534E0000 | ((lightShift+64)&255));
        e.cullFace(null).shadeDirectionOverride(normal).emit();
    }
}






