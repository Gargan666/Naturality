package naturality.client.weather;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Predicate;

/** Supply wind tags through FRAPI, including renderers which bypass Indigo. */
public final class FoliageWindModel extends WrapperBlockStateModel {
    private FoliageWindModel(BlockStateModel model) { super(model); }
    public static void initialize() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
            // Leaf tags may not yet be bound during the initial model bake.
            ModelModifier.WRAP_LAST_PHASE,(model,bake) -> !(bake.state().getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
                && WindRendering.tag(bake.state())==255
                ? model : new FoliageWindModel(model)));
    }
    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random) {
        // Exposure and plant attachment are position-dependent.
        return null;
    }
    @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter level,BlockPos pos,BlockState state,
            RandomSource random,Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
        // Render views (including Sodium's LevelSlice) can be reused after edits.
        // Cache across vertices of this emission, never across snapshot revisions.
        WindRendering.clearExposureCache();
        if(!state.getFluidState().isEmpty()) { wrapped.emitQuads(emitter,level,pos,state,random,cull); return; }
        var offset=state.getOffset(pos);
        emitter.pushTransform(quad -> {
            for(int i=0;i<4;i++) {
                int color=quad.color(i);
                if((color>>>24)!=255) continue; // Preserve fitted snow/plant tags.
                int tag=WindRendering.vertexTag(level,pos,state,quad.x(i)+(float)offset.x,
                    quad.y(i)+(float)offset.y,quad.z(i)+(float)offset.z);
                quad.color(i,(color&0xFFFFFF)|(tag<<24));
            }
            return true;
        });
        try { wrapped.emitQuads(emitter,level,pos,state,random,cull); }
        finally { emitter.popTransform(); }
    }
}
