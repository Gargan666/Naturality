package naturality.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import naturality.Naturality;
import naturality.NaturalityBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Small, partly buried rocks. Validate the whole footprint before placing any blocks. */
public record LirestoneBoulderFeature() implements Feature {
    public static final MapCodec<LirestoneBoulderFeature> CODEC = MapCodec.unit(LirestoneBoulderFeature::new);
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(
        Registries.PLACED_FEATURE, Naturality.id("lirestone_boulder"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("lirestone_boulder"), CODEC);
        BiomeModifications.addFeature(BiomeSelectors.foundInTheEnd(), GenerationStep.Decoration.LOCAL_MODIFICATIONS, PLACED);
    }

    @Override public MapCodec<LirestoneBoulderFeature> codec() { return CODEC; }

    public static boolean isEndStone(BlockState state) {
        return state.is(Blocks.END_STONE) || state.is(NaturalityBlocks.SMOOTH_ENDSTONE) || state.is(NaturalityBlocks.LIRESTONE);
    }

    @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        // Material patches can reach the main island; additional geometry remains in the outer End.
        if (level.getLevel().dimension() != Level.END || Math.hypot(origin.getX(), origin.getZ()) < 1280
                || !isEndStone(level.getBlockState(origin.below()))) return false;
        if (!nearRim(level,origin) && random.nextInt(5)!=0) return false;
        int rx = 2 + random.nextInt(2), rz = 2 + random.nextInt(2), height = 2 + random.nextInt(3);
        int leanX = random.nextBoolean() ? 1 : -1, leanZ = random.nextBoolean() ? 1 : -1;
        BlockPos base = origin.below();
        var blocks = new ArrayList<BlockPos>();
        for (int x = -rx-1; x <= rx+1; x++) for (int z = -rz-1; z <= rz+1; z++) {
            boolean first = true;
            for (int y = -2; y <= height; y++) {
                double vertical = y / (y < 0 ? 2.4 : (double)height);
                double main = x*x / (double)(rx*rx) + z*z / (double)(rz*rz) + vertical*vertical;
                double side = (x-leanX)*(x-leanX) / (rx*rx*.7)
                    + (z-leanZ)*(z-leanZ) / (rz*rz*.7) + (y+.5)*(y+.5) / (height*height*.65);
                if (Math.min(main, side) > 1) continue;
                BlockPos pos = base.offset(x, y, z);
                if (level.isOutsideBuildHeight(pos)) return false;
                var existing = level.getBlockState(pos);
                if (!existing.isAir() && !isEndStone(existing)) return false;
                // Every occupied column must sit on rock; cliffs, void and structures reject the attempt.
                if (first && !isEndStone(level.getBlockState(pos.below()))) return false;
                first = false;
                blocks.add(pos);
            }
        }
        for (BlockPos pos : blocks) level.setBlock(pos, NaturalityBlocks.LIRESTONE.defaultBlockState(), 2);
        return !blocks.isEmpty();
    }

    public static boolean nearRim(net.minecraft.world.level.LevelReader level, BlockPos origin) {
        BlockPos shoulder=origin.below(4);
        return level.getBlockState(shoulder.offset(8,0,0)).isAir()
            || level.getBlockState(shoulder.offset(-8,0,0)).isAir()
            || level.getBlockState(shoulder.offset(0,0,8)).isAir()
            || level.getBlockState(shoulder.offset(0,0,-8)).isAir();
    }
}
