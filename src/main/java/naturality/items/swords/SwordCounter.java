package naturality.items.swords;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import naturality.Naturality;
import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import naturality.effects.stunned.Stunned;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import naturality.bootstrap.ContentRegistration.NaturalitySounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Server-owned sword counter windows and a shared cooldown across both hands and all swords. */
@NullMarked
public final class SwordCounter {
    public static final int WINDOW_TICKS = 6;
    private static final int COOLDOWN_TICKS = 40;
    private static final int DURABILITY_PER_ATTEMPT = 5;
    private static final Identifier COOLDOWN_GROUP = Naturality.id("sword_counter");
    private static final float REFLECTED_FRACTION = 0.1F;
    private static final double KNOCKBACK = 0.6;
    private static final Map<MinecraftServer, CounterState> SERVERS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final class CounterState {
        private final Map<ServerPlayer, Attempt> attempts = new HashMap<>();
        private final Map<LivingEntity, ServerLevel> airborneStuns = new HashMap<>();
    }

    private static CounterState state(ServerLevel level) {
        return SERVERS.computeIfAbsent(level.getServer(), _ -> new CounterState());
    }

    private SwordCounter() {}

    public interface ProjectileState {
        void naturality$resetParryCollision(Player player, boolean changedOwner);
    }

    /** Temporary collision grace is separate from ownership and ends once the projectile leaves the defender. */
    public static final class ProjectileGrace {
        private @Nullable Player defender;

        public void begin(@Nullable Player player) { defender = player; }

        public void tick(Projectile projectile) {
            var player = defender;
            if (player != null && (player.isRemoved() || player.level() != projectile.level()
                    || !player.getBoundingBox().inflate(0.5).intersects(projectile.getBoundingBox()))) defender = null;
        }

        public boolean blocks(Entity target) {
            var player = defender;
            return player != null && player.isPassengerOfSameVehicle(target);
        }
    }

    public interface HomingProjectile {
        void naturality$retarget(LivingEntity target, Vec3 movement);
    }

    public static void initialize() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(SwordCounter::allowDamage);
        ServerTickEvents.END_SERVER_TICK.register(SwordCounter::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SwordCounter::clearServer);
    }

    private static void clearServer(MinecraftServer server) { SERVERS.remove(server); }

    private static void tick(MinecraftServer server) {
        var state = SERVERS.get(server);
        if (state == null) return;
        state.attempts.entrySet().removeIf(entry -> {
            ServerPlayer player = entry.getKey();
            Attempt attempt = entry.getValue();
            return player.isRemoved() || player.tickCount >= attempt.endsAt || !attempt.holdsSword(player);
        });
        state.airborneStuns.entrySet().removeIf(entry -> {
            LivingEntity entity = entry.getKey();
            if (!entity.isAlive() || entity.isRemoved() || entity.level() != entry.getValue()
                    || !entity.hasEffect(Stunned.EFFECT)) return true;
            // Refresh while airborne; the last refresh leaves a full second after landing.
            entity.addEffect(new MobEffectInstance(Stunned.EFFECT, 20, 0, false, false, true));
            return entity.onGround();
        });
    }

    public static InteractionResult use(Player player, InteractionHand hand) {
        ItemStack sword = player.getItemInHand(hand);
        if (!canAttempt(player, hand)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            state(serverPlayer.level()).attempts.put(serverPlayer, new Attempt(serverPlayer, hand, sword));
        }
        player.startUsingItem(hand);
        // Predict immediately on the client; ServerItemCooldowns synchronizes the authoritative timer.
        player.getCooldowns().addCooldown(COOLDOWN_GROUP, COOLDOWN_TICKS);
        if (player instanceof ServerPlayer serverPlayer) {
            // Use Lunge's standard durability path: enchantment modifiers and creative exemptions apply.
            sword.hurtAndBreak(DURABILITY_PER_ATTEMPT, serverPlayer, hand);
            if (!isSword(sword)) {
                state(serverPlayer.level()).attempts.remove(serverPlayer);
                player.stopUsingItem();
            }
        }
        return InteractionResult.CONSUME;
    }

    public static int windowTicks(ItemStack sword) {
        return WINDOW_TICKS + 4 * enchantmentLevel(sword, "steady");
    }

    private static int enchantmentLevel(ItemStack sword, String name) {
        if (!isSword(sword)) return 0;
        for (var entry : sword.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            if (Enchanting.name(entry.getKey()).equals("naturality:" + name)) {
                return Math.max(0, Math.min(entry.getIntValue(), entry.getKey().value().getMaxLevel()));
            }
        }
        return 0;
    }

    public static Identifier cooldownGroup(ItemStack stack, Identifier original) {
        // Broken swords still show the same timer, and swapping sword materials cannot bypass it.
        return stack.is(ItemTags.SWORDS) ? COOLDOWN_GROUP : original;
    }

    public static boolean isSword(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) && !stack.isBroken();
    }

    public static boolean canAttempt(Player player, InteractionHand hand) {
        ItemStack sword = player.getItemInHand(hand);
        return isSword(sword) && !Stunned.active(player) && !player.isSpectator() && player.isAlive()
            && !player.isUsingItem() && !player.getCooldowns().isOnCooldown(sword);
    }

    public static boolean isCounterUse(LivingEntity entity) {
        return entity instanceof Player && entity.isUsingItem() && isSword(entity.getUseItem())
            && entity.getUseItemRemainingTicks() > 0;
    }

    private static boolean allowDamage(LivingEntity victim, DamageSource source, float damage) {
        if (!(victim instanceof ServerPlayer player) || !(damage > 0) || !Float.isFinite(damage)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == player
                || attacker.level() != player.level() || !attacker.isAlive()
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_SHIELD)) return true;

        // Projectiles are intercepted before impact, so they survive without applying effects to their shooter.
        if (source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE)) return true;
        // Environmental damage and retaliation (including our own Thorns source) cannot be countered.
        if (!source.is(DamageTypeTags.IS_PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)
                && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) return true;

        var origin = source.getSourcePosition();
        if (origin == null) return true;
        Attempt attempt = matchingAttempt(player, origin.subtract(player.position()));
        if (attempt == null) return true;
        var facing = player.getViewVector(1.0F);

        // Consume the window before retaliation can trigger any further damage callbacks.
        state(player.level()).attempts.remove(player);
        player.stopUsingItem();

        DamageSource reflected = player.damageSources().thorns(player);
        float reflectedDamage = damage * REFLECTED_FRACTION;
        attacker.hurtServer(player.level(), reflected, reflectedDamage);
        Stunned.apply(attacker, player);
        if (enchantmentLevel(attempt.sword, "shockwave") > 0) {
            var direction = attacker.position().subtract(player.position());
            if (direction.lengthSqr() < 1.0E-8) direction = facing;
            attacker.setDeltaMovement(direction.normalize().scale(0.75));
            if (direction.y > 0) attacker.setOnGround(false);
            state(player.level()).airborneStuns.put(attacker, player.level());
        } else {
            attacker.knockback(KNOCKBACK, player.getX() - attacker.getX(), player.getZ() - attacker.getZ(),
                reflected, reflectedDamage);
        }
        attacker.syncVelocity = true;
        successFeedback(player);
        return false;
    }

    public static boolean returnProjectile(Projectile projectile, EntityHitResult hit) {
        if (!(hit.getEntity() instanceof ServerPlayer player) || !projectile.isAlive()
                || !(projectile.getOwner() instanceof LivingEntity attacker) || attacker == player
                || !attacker.isAlive() || attacker.level() != player.level()) return false;
        Vec3 incoming = hit.getLocation().subtract(player.position());
        if (incoming.lengthSqr() < 1.0E-8) incoming = projectile.getDeltaMovement().scale(-1);
        if (matchingAttempt(player, incoming) == null) return false;

        state(player.level()).attempts.remove(player);
        player.stopUsingItem();
        double speed = Math.max(0.1, projectile.getDeltaMovement().length());
        Vec3 direction = attacker.getBoundingBox().getCenter().subtract(hit.getLocation());
        if (direction.lengthSqr() < 1.0E-8) direction = player.getViewVector(1.0F);
        // Arrows (including tridents) keep shooter attribution, pickup rules and Loyalty ownership.
        boolean changedOwner = !(projectile instanceof AbstractArrow);
        if (changedOwner) projectile.setOwner(player);
        ((ProjectileState)projectile).naturality$resetParryCollision(player, changedOwner);
        projectile.setPos(hit.getLocation());
        projectile.shoot(direction.x, direction.y, direction.z, (float)speed, 0);
        projectile.syncVelocity = true;
        if (projectile instanceof HomingProjectile homing)
            homing.naturality$retarget(attacker, projectile.getDeltaMovement());
        successFeedback(player);
        return true;
    }

    private static @Nullable Attempt matchingAttempt(ServerPlayer player, Vec3 incoming) {
        var state = SERVERS.get(player.level().getServer());
        Attempt attempt = state == null ? null : state.attempts.get(player);
        if (attempt == null || player.tickCount >= attempt.endsAt || !attempt.holdsSword(player)) return null;
        var facing = player.getViewVector(1.0F);
        // A horizontal half-plane keeps looking up/down from changing the front-facing arc.
        return incoming.x * facing.x + incoming.z * facing.z > 0.0
            || incoming.horizontalDistanceSqr() < 1.0E-8 && incoming.y > 0 ? attempt : null;
    }

    private static void successFeedback(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            NaturalitySounds.PARRY, SoundSource.PLAYERS, 1.0F, 1.0F);
        var impact = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.65)).add(0, -0.25, 0);
        player.level().sendParticles(NaturalityParticles.SWORD_COUNTER_SPARK,
            impact.x, impact.y, impact.z, 24, 0.025, 0.025, 0.025, 0.22);
    }

    private static final class Attempt {
        private final ServerLevel level;
        private final InteractionHand hand;
        private final ItemStack sword;
        private final int endsAt;

        private Attempt(ServerPlayer player, InteractionHand hand, ItemStack sword) {
            level = player.level();
            this.hand = hand;
            this.sword = sword;
            endsAt = player.tickCount + windowTicks(sword);
        }

        private boolean holdsSword(ServerPlayer player) {
            return player.isAlive() && !player.isSpectator() && player.level() == level
                && player.getItemInHand(hand) == sword && isCounterUse(player)
                && player.getUseItem() == sword;
        }
    }
}
