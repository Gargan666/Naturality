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
        if (!naturality.config.GameplaySettings.clientSnowWrapping() || SnowGeometry.usesVanillaGeometry(level,pos)) { wrapped.emitQuads(e,level,pos,state,random,cull); return; }
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
            if (exposedLeaves || visibility.visible(p,Direction.UP)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.UP, new float[][]{{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}});
            if (exposedLeaves || visibility.visible(p,Direction.DOWN)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.DOWN, new float[][]{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}});
            if (exposedLeaves || visibility.visible(p,Direction.NORTH)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.NORTH, new float[][]{{X,Y,z},{X,y,z},{x,y,z},{x,Y,z}});
            if (exposedLeaves || visibility.visible(p,Direction.SOUTH)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.SOUTH, new float[][]{{x,Y,Z},{x,y,Z},{X,y,Z},{X,Y,Z}});
            if (exposedLeaves || visibility.visible(p,Direction.WEST)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.WEST, new float[][]{{x,Y,z},{x,y,z},{x,y,Z},{x,Y,Z}});
            if (exposedLeaves || visibility.visible(p,Direction.EAST)) face(e, material, leafDepth, exposedLeaves, (int)Math.floor(y+1e-5), Direction.EAST, new float[][]{{X,Y,Z},{X,y,Z},{X,y,z},{X,Y,z}});
        }
    }
    private static void face(QuadEmitter e, Material.Baked material, int leafDepth, boolean exposedLeaves, int lightShift, Direction normal, float[][] points) {
        for (int i=0;i<4;i++) {
            var p=points[i];
            int alpha=leafDepth==0 || !exposedLeaves ? 192 : naturality.client.weather.WindRendering.leafTag(p[0],p[1]+leafDepth,p[2])-32;
            e.pos(i,p[0],p[1],p[2]).color(i,(alpha<<24)|0xFFFFFF);
            float u=normal.getAxis()==Direction.Axis.X ? p[2] : p[0];
            float v=normal.getAxis()==Direction.Axis.Y ? p[2] : p[1];
            // Crop in block units; never stretch the vanilla snow grain to a narrow patch.
            e.uv(i, Math.clamp(u,0,1), normal.getAxis()==Direction.Axis.Y ? Math.clamp(v,0,1) : 1-Math.clamp(v-(float)Math.floor(points[1][1]),0,1));
        }
        e.materialBake(material,MutableQuadView.BAKE_NORMALIZED);
        e.tag(0x534E0000 | ((lightShift+64)&255));
        e.cullFace(null).shadeDirectionOverride(normal).emit();
    }
}






