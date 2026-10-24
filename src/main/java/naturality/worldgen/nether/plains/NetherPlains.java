package naturality.worldgen.nether.plains;

import naturality.Naturality;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/** Sparse forest margins selected by the same climate fields as vanilla Nether biomes. */
public final class NetherPlains {
    public static final ResourceKey<Biome> CRIMSON_PLAINS = ResourceKey.create(Registries.BIOME, Naturality.id("crimson_plains"));
    public static final ResourceKey<Biome> WARPED_PLAINS = ResourceKey.create(Registries.BIOME, Naturality.id("warped_plains"));

    public static void initialize() {
        // Between wastes (temperature 0) and crimson forest (temperature .4).
        NetherBiomes.addNetherBiome(CRIMSON_PLAINS, Climate.parameters(.2F, 0, 0, 0, 0, 0, .1F));
        // Warped forest has an offset penalty of .375. Retain a comparable penalty
        // here so the new margin does not consume the existing warped forest core.
        NetherBiomes.addNetherBiome(WARPED_PLAINS, Climate.parameters(0, .25F, 0, 0, 0, 0, .3125F));
    }

    private NetherPlains() {}
}
