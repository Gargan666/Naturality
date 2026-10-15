package naturality.client.insanity;

import java.util.IdentityHashMap;
import naturality.NaturalityEffects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** Observer-local hallucinations. Proxy entities are never added to the world. */
public final class Insanity {
    private static final EntityType<? extends Mob>[] TYPES = types();
    private static final String[] VOICES = {"ghast", "piglin", "sheep", "zombie", "skeleton", "spider", "enderman"};
    // Extraction snapshots the pose into a fresh render state; eight templates cover any crowd size.
    private static final IdentityHashMap<EntityType<?>, Mob> PROXIES = new IdentityHashMap<>();
    private static ClientLevel level;
    private static boolean active;
    private static long episode;
    private static int nextProxyId = -2_000_000;

    @SuppressWarnings("unchecked")
    private static EntityType<? extends Mob>[] types() {
        return (EntityType<? extends Mob>[])new EntityType<?>[] {
            EntityTypes.COW, EntityTypes.PIG, EntityTypes.SHEEP, EntityTypes.ZOMBIE,
            EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.ENDERMAN, EntityTypes.CREEPER
        };
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(_ -> refresh());
    }
    private static boolean refresh() {
        var client = Minecraft.getInstance();
        boolean now = client.player != null && client.level != null && client.player.hasEffect(NaturalityEffects.INSANITY);
        if (client.level != level || now != active) {
            PROXIES.clear();
            if (now) episode++;
            level = client.level;
            active = now;
        }
        return now;
    }
    public static float distanceWeight(double distance) {
        float t = (float)Math.clamp((distance - 4) / 4, 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static float weight(Mob mob) {
        if (!refresh() || !mob.isAlive() || mob.isInvisible()) return 0;
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : distanceWeight(player.distanceTo(mob));
    }
    private static long seed(Mob mob) {
        return mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits() ^ (episode * 0x9E3779B97F4A7C15L);
    }
    public static EntityType<? extends Mob> replacementType(Mob mob) {
        int index = Math.floorMod(Long.hashCode(seed(mob)), TYPES.length);
        if (TYPES[index] == mob.getType()) index = (index + 1) % TYPES.length;
        return TYPES[index];
    }
    public static void extract(Entity entity, EntityRenderState output, float partial) {
        if (!(entity instanceof Mob mob)) return;
        float weight = weight(mob);
        if (weight <= 0) return;
        var type = replacementType(mob);
        Mob proxy = PROXIES.get(type);
        if (proxy == null) {
            // These are render-only models, so Peaceful must not reject monster appearances.
            proxy = type.create(mob.level(), new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.COMMAND, true));
            if (proxy == null) return;
            // Rendering hooks hash entities by ID even when they never enter a world.
            proxy.setId(nextProxyId--);
            PROXIES.put(type, proxy);
        }
        proxy.setPos(mob.position());
        proxy.xOld = mob.xOld; proxy.yOld = mob.yOld; proxy.zOld = mob.zOld;
        proxy.setYRot(mob.getYRot()); proxy.setXRot(mob.getXRot());
        proxy.yRotO = mob.yRotO; proxy.xRotO = mob.xRotO;
        proxy.yBodyRot = mob.yBodyRot; proxy.yBodyRotO = mob.yBodyRotO;
        proxy.yHeadRot = mob.yHeadRot; proxy.yHeadRotO = mob.yHeadRotO;
        proxy.tickCount = mob.tickCount;
        var state = (LivingEntityRenderState)Minecraft.getInstance().getEntityRenderDispatcher()
            .getRenderer(proxy).createRenderState(proxy, partial);
        if (state == null) return;
        state.lightCoords = output.lightCoords;
        state.ageInTicks = output.ageInTicks;
        state.passengerOffset = output.passengerOffset;
        if (state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid)
            humanoid.isPassenger = mob.isPassenger();
        if (output instanceof LivingEntityRenderState real) {
            state.bodyRot = real.bodyRot; state.yRot = real.yRot; state.xRot = real.xRot;
            state.walkAnimationPos = real.walkAnimationPos; state.walkAnimationSpeed = real.walkAnimationSpeed;
        }
        state.scale *= mob.getBbHeight() / Math.max(.1F, proxy.getBbHeight());
        state.nameTag = null;
        state.shadowPieces.clear();
        state.displayFireAnimation = false;
        var illusion = ((InsanityState)state).naturality$insanity();
        illusion.alpha = weight;
        illusion.distortion = weight;
        var original = ((InsanityState)output).naturality$insanity();
        original.alpha = 1 - weight;
        original.distortion = weight;
        original.hallucination = state;
    }

    public static SoundInstance sound(SoundInstance original) {
        var player = Minecraft.getInstance().player;
        if (player == null) return original;
        if (!refresh() || original.isRelative() || original.isLooping()
                || original.getSource() != SoundSource.HOSTILE && original.getSource() != SoundSource.NEUTRAL) return original;
        String path = original.getIdentifier().getPath();
        if (!path.startsWith("entity.")) return original;
        String kind = path.endsWith(".hurt") ? "hurt" : path.endsWith(".death") ? "death"
            : path.endsWith(".step") ? "step" : "ambient";
        Mob nearest = null;
        double best = 4;
        for (var entity : level.entitiesForRendering()) if (entity instanceof Mob mob) {
            double dx = mob.getX() - original.getX(), dy = mob.getY() - original.getY(), dz = mob.getZ() - original.getZ();
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared < best) { best = squared; nearest = mob; }
        }
        if (nearest == null || distanceWeight(player.distanceTo(nearest)) <= 0) return original;
        String voice = VOICES[Math.floorMod(Long.hashCode(seed(nearest) ^ 0x71C2L), VOICES.length)];
        var id = net.minecraft.resources.Identifier.withDefaultNamespace("entity." + voice + "." + kind);
        SoundEvent replacement = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (replacement == null || replacement.location().equals(original.getIdentifier()))
            replacement = net.minecraft.sounds.SoundEvents.GHAST_AMBIENT;
        // SoundManager.play runs before sample resolution; ordinary getters are unsafe there.
        if (!(original instanceof InsanitySoundSettings settings)) return original;
        // Keep original placement, category, volume, pitch and attenuation.
        return new SimpleSoundInstance(replacement.location(), original.getSource(), settings.naturality$volume(), settings.naturality$pitch(),
            RandomSource.create(seed(nearest)), false, original.getDelay(), original.getAttenuation(),
            original.getX(), original.getY(), original.getZ(), false);
    }
    private Insanity() {}
}
