package naturality.entity;

import java.util.IdentityHashMap;
import java.util.Map;
import naturality.config.Feature;
import naturality.sky.SkyEvents;
import naturality.sky.SkyEventType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/** Each new maddened mob registers its ordinary counterpart here. */
public final class MaddenedVariants {
    private static final Map<EntityType<?>, EntityType<?>> VARIANTS = new IdentityHashMap<>();
    public static void register(EntityType<? extends Mob> original, EntityType<? extends Mob> variant) {
        if (original == variant || original.getCategory() != MobCategory.MONSTER || variant.getCategory() != MobCategory.MONSTER)
            throw new IllegalArgumentException("Maddened counterparts must be distinct monster types");
        var previous = VARIANTS.putIfAbsent(original, variant);
        if (previous != null && previous != variant) throw new IllegalArgumentException("Maddened counterpart already registered");
    }
    public static EntityType<?> forSpawn(ServerLevel level, EntityType<?> original) {
        if (!Feature.CONTENT.enabled() || !Feature.SKY_EVENT_CYCLES.enabled()
                || original.getCategory() != MobCategory.MONSTER
                || SkyEvents.strength(level, SkyEventType.BLOOD_MOON) <= 0) return original;
        return VARIANTS.getOrDefault(original, original);
    }
    private MaddenedVariants() {}
}
