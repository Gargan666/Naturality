package naturality.starfall;

import naturality.Naturality;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class StarfallEntities {
    public static final EntityType<FallingStar> STAR = register("falling_star");
    public static final EntityType<FallingStar> FIZZLE = register("fizzle");
    private static EntityType<FallingStar> register(String name) {
        var id=Naturality.id(name);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE,id,EntityType.Builder.<FallingStar>of(FallingStar::new,MobCategory.MISC)
            .sized(.65F,.65F).clientTrackingRange(16).updateInterval(1).noSave()
            .build(ResourceKey.create(Registries.ENTITY_TYPE,id)));
    }
    public static void initialize() {}
    private StarfallEntities() {}
}