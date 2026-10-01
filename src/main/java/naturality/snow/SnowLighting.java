package naturality.snow;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The saved owner cell of fitted snow contains no light-blocking geometry. */
public final class SnowLighting {
    private SnowLighting() { }
    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated) -> {
            if (generated) return;
            if (!naturality.config.GameplaySettings.snowWrapping(level)) return;
            // Saved light arrays can still contain shadows from the old owner
            // cells. Recheck only fitted snow, not every block in snowy chunks.
            var pos=new BlockPos.MutableBlockPos();
            for (int sectionIndex=0;sectionIndex<chunk.getSectionsCount();sectionIndex++) {
                var section=chunk.getSection(sectionIndex);
                if (!section.maybeHas(s -> s.is(Blocks.SNOW))) continue;
                int baseY=chunk.getSectionYFromSectionIndex(sectionIndex)*16;
                for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                    var snow=section.getBlockState(x,y,z);
                    if (!snow.is(Blocks.SNOW)) continue;
                    pos.set(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                    if (state(chunk,pos,snow)==snow) continue;
                    // The skylight table indexes chunk-local X/Z, unlike the
                    // light-engine checks below which need world positions.
                    chunk.getSkyLightSources().update(chunk,x,pos.getY(),z);
                    var lighting=level.getChunkSource().getLightEngine();
                    lighting.checkBlock(pos.immutable());
                    lighting.checkBlock(pos.below());
                }
            }
        });
    }
    public static BlockState state(BlockGetter level, BlockPos pos, BlockState state) {
        return state.is(Blocks.SNOW) && naturality.config.GameplaySettings.snowWrapping(level)
            && !SnowGeometry.usesVanillaGeometry(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }
}
