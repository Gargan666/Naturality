package naturality.entities.maddened;

import java.util.IdentityHashMap;
import java.util.Map;
import naturality.config.startup.FeatureConfig.Feature;
import naturality.systems.event.framework.Events;
import naturality.systems.event.framework.Events.EventType;
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
        if (!Feature.CONTENT.enabled() || !Feature.EVENT_CYCLES.enabled()
                || original.getCategory() != MobCategory.MONSTER
                || Events.strength(level, EventType.BLOOD_MOON) <= 0) return original;
        return VARIANTS.getOrDefault(original, original);
    }
    private MaddenedVariants() {}

    /** Shared sound behavior for current and future maddened variants. */
    public interface MaddenedMob {
        static boolean isExplosionSound(net.minecraft.resources.Identifier sound) {
            return sound.getPath().endsWith(".explode");
        }
        default float maddenedSoundPitch(float original) { return original * .5F; }
    }
}
