package naturality.items.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Registry-backed item components can outlive the world that supplied their holders. */
@org.jspecify.annotations.NullMarked
public final class ItemDamageSources {
    private ItemDamageSources() {}

    public static DamageSource inWorld(DamageSource source, LivingEntity attacker) {
        var key = source.typeHolder().unwrapKey();
        if (key.isEmpty()) return source;
        var current = attacker.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
            .getOrThrow(key.get());
        if (current == source.typeHolder()) return source;
        // Resolve the same type in the attacking world, preserving attribution and
        // positional sources instead of substituting generic/player damage.
        var position = source.sourcePositionRaw();
        return position == null
            ? new DamageSource(current, source.getDirectEntity(), source.getEntity())
            : new DamageSource(current, position);
    }
}
