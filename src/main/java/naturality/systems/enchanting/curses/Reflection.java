package naturality.systems.enchanting.curses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** An attack scope includes post-hit effects, so arrows and melee reflect their actual debuffs. */
@org.jspecify.annotations.NullMarked
public final class Reflection {
    private Reflection() {}
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath("naturality", "reflection"));
    private static final ThreadLocal<@Nullable Scope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    private static final class Scope {
        int depth;
        final List<Hit> hits = new ArrayList<>();
    }

    public static final class Hit {
        private final LivingEntity victim;
        private final LivingEntity attacker;
        private final int level;
        private final float before;
        private float damage;
        private final Map<Holder<MobEffect>, MobEffectInstance> effects = new HashMap<>();

        private Hit(LivingEntity victim, LivingEntity attacker, int level) {
            this.victim = victim;
            this.attacker = attacker;
            this.level = level;
            before = victim.getHealth() + victim.getAbsorptionAmount();
            for (var effect : victim.getActiveEffects()) effects.put(effect.getEffect(), new MobEffectInstance(effect));
        }
    }

    public static void begin() {
        Scope scope = CURRENT.get();
        if (scope == null) { scope = new Scope(); CURRENT.set(scope); }
        scope.depth++;
    }

    public static void end() {
        Scope scope = CURRENT.get();
        if (scope == null || --scope.depth > 0) return;
        CURRENT.remove();
        if (REFLECTING.get()) return;
        REFLECTING.set(true);
        try {
            for (Hit hit : scope.hits) apply(hit);
        } finally {
            REFLECTING.remove();
        }
    }

    public static @Nullable Hit before(LivingEntity victim, DamageSource source) {
        // Holding a cursed weapon does not turn Thorns or other attributed damage into a weapon attack.
        if (!source.is(DamageTypeTags.IS_PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypes.MOB_ATTACK) && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return null;
        int level = CurseLevels.active(source.getWeaponItem(), "reflection");
        return REFLECTING.get() || level == 0 || !(source.getEntity() instanceof LivingEntity attacker)
            || attacker == victim ? null : new Hit(victim, attacker, level);
    }

    public static void after(@Nullable Hit hit, boolean accepted) {
        Scope scope = CURRENT.get();
        if (hit == null || !accepted || scope == null) return;
        hit.damage = Math.max(0, hit.before - hit.victim.getHealth() - hit.victim.getAbsorptionAmount());
        if (hit.damage > 0) scope.hits.add(hit);
    }

    private static void apply(Hit hit) {
        if (!(hit.attacker.level() instanceof ServerLevel world)) return;
        float fraction = hit.level * 0.1F;
        var source = new DamageSource(world.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
        hit.attacker.hurtServer(world, source, hit.damage * fraction);
        for (var effect : hit.victim.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) continue;
            var previous = hit.effects.get(effect.getEffect());
            if (previous != null && effect.getAmplifier() == previous.getAmplifier()
                    && effect.getDuration() == previous.getDuration()) continue;
            hit.attacker.addEffect(effect.withScaledDuration(fraction), hit.victim);
        }
    }
}
