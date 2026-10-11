package naturality.worldgen;

import naturality.Naturality;
import net.fabricmc.fabric.api.biome.v1.TheEndBiomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class EndWilds {
    public static final ResourceKey<Biome> BIOME=ResourceKey.create(Registries.BIOME,Naturality.id("end_wilds"));
    private EndWilds() {}

    public static void initialize() {
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.FEATURE_TYPE,
            Naturality.id("wilds_boulders"),WildsBouldersFeature.CODEC);
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.FEATURE_TYPE,
            Naturality.id("lyren_plants"),LyrenPlantsFeature.CODEC);
        if (!naturality.config.Feature.END_WILDS.enabled()) return;
        TheEndBiomes.addHighlandsBiome(BIOME,1.5);
        TheEndBiomes.addSmallIslandsBiome(BIOME,1.5);
        TheEndBiomes.addMidlandsBiome(BIOME,BIOME,1);
        TheEndBiomes.addBarrensBiome(BIOME,BIOME,1);
    }
}
