package naturality.client.effects.ambienthorror;

import naturality.client.effects.ambienthorror.HorrorEyesLayer.EyeVariant;
import naturality.api.worlds.LoadedChunks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Private impressions, never added to an entity manager or collision/debug renderer. */
public final class AmbientHorror {
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel world;
    private static Vec3 walker, eyes;
    private static double heading, walked;
    private static int walkerLife, eyeLife, stare, fade = -1, rolls, cooldown = 1200;
    private static Frustum view;
    private static boolean watched = true;
    private static boolean testEyes;
    private static boolean retreating;
    private static boolean sprinter;
    private static EyeVariant eyeVariant = EyeVariant.NORMAL;
    public static final double EYE_FADE_DISTANCE = 16;
    private AmbientHorror() {}

    public static void initialize() {
        HorrorEyesLayer.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(AmbientHorror::tick);
        HorrorCommands.initialize();
    }
    public static AABB bounds(Vec3 feet) {
        return new AABB(feet.x - .3, feet.y, feet.z - .3, feet.x + .3, feet.y + 1.8, feet.z + .3);
    }
    public static float opacity(int age, int fading, float partial) {
        float in = Math.clamp((age + partial) / 20F, 0, 1);
        float out = fading < 0 ? 1 : Math.clamp(1 - (fading + partial) / 30F, 0, 1);
        return in * out * out * (3 - 2 * out);
    }
    private static void reset() {
        walker = eyes = null; view = null; watched = true;
        stare = rolls = 0; fade = -1; cooldown = 1200;
        testEyes = false;
        retreating = false;
        sprinter = false;
    }
    /** Prefer normal placement; allow a visible test fallback only when none can be found. */
    public static boolean test(boolean eyeEffect) {
        Minecraft client = Minecraft.getInstance();
        var listenerEntity = client.player;
        if (client.level == null || listenerEntity == null || !listenerEntity.isAlive()
                || listenerEntity.isSpectator() || listenerEntity.isSleeping()) return false;
        if (world != client.level) { world = client.level; reset(); }
        var camera = client.gameRenderer.mainCamera();
        Vec3 forward = new Vec3(camera.forwardVector());
        if (eyeEffect) {
            Vec3 natural = findEyes(camera, true);
            if (natural != null) { startEyes(natural, false); return true; }
            for (int distance = 24; distance >= 8; distance--) {
                Vec3 candidate = camera.position().add(forward.scale(distance));
                if (LoadedChunks.has(world, BlockPos.containing(candidate))
                        && world.getBlockState(BlockPos.containing(candidate)).isAir()
                        && clear(camera.position(), candidate)) {
                    startEyes(candidate, true); return true;
                }
            }
        } else {
            return spawnWalker(listenerEntity.position(), 64, false);
        }
        return false;
    }
    public static boolean testSprinter() {
        if (!test(false)) return false;
        sprinter = true;
        return true;
    }
    public static void clearEffects() {
        // Clearing impressions does not invalidate the current camera's visibility data.
        Frustum currentView = view;
        reset();
        view = currentView;
    }
    public static boolean usesNaturalEyeRules() { return !testEyes; }
    private static void startEyes(Vec3 position, boolean fallback) {
        eyes = position; eyeLife = stare = 0; fade = -1; testEyes = fallback;
        int roll = RANDOM.nextInt(10);
        eyeVariant = roll < 5 ? EyeVariant.NORMAL : roll < 7 ? EyeVariant.UNEVEN : roll < 9 ? EyeVariant.CROOKED : EyeVariant.SMALL_WIDE;
    }
    private static boolean eyePosition(Vec3 listener, Vec3 candidate) {
        return dark(candidate) && world.noCollision(new AABB(candidate.x - .5, candidate.y - .15, candidate.z - .5,
            candidate.x + .5, candidate.y + .15, candidate.z + .5)) && clear(listener, candidate)
            && blackBackdrop(listener, candidate);
    }
    private static Vec3 findEyes(Camera camera, boolean exhaustive) {
        Vec3 listener = camera.position(), forward = new Vec3(camera.forwardVector());
        Vec3 right = new Vec3(new org.joml.Vector3f(1, 0, 0).rotate(camera.rotation()));
        Vec3 up = new Vec3(new org.joml.Vector3f(0, 1, 0).rotate(camera.rotation()));
        for (int i = 0; i < 16; i++) {
            double offset = Math.toRadians((RANDOM.nextInt(10) == 0 ? 8 : 28) + RANDOM.nextDouble() * 18);
            Vec3 direction = forward.add(right.scale(Math.tan(offset) * (RANDOM.nextBoolean() ? 1 : -1)))
                .add(up.scale((RANDOM.nextDouble() - .5) * .16)).normalize();
            Vec3 candidate = listener.add(direction.scale(18 + RANDOM.nextDouble() * 18));
            if (eyePosition(listener, candidate)) return candidate;
        }
        if (exhaustive) for (int angle : new int[]{36, -36, 28, -28, 44, -44, 16, -16, 8, -8})
            for (int vertical : new int[]{0, -8, 8}) for (int distance : new int[]{18, 24, 30, 36}) {
                Vec3 direction = forward.add(right.scale(Math.tan(Math.toRadians(angle))))
                    .add(up.scale(Math.tan(Math.toRadians(vertical)))).normalize();
                Vec3 candidate = listener.add(direction.scale(distance));
                if (eyePosition(listener, candidate)) return candidate;
            }
        return null;
    }
    private static boolean spawnWalker(Vec3 player, int attempts, boolean sprinting) {
        for (int i = 0; i < attempts; i++) {
            double angle = (attempts > 12 ? i / (double)attempts : RANDOM.nextDouble()) * Math.PI * 2;
            double radius = attempts > 12 ? 24 + (i % 4) * 4 : 24 + RANDOM.nextDouble() * 12;
            Vec3 candidate = ground(player.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 3);
            if (candidate != null && view != null && !view.isVisible(bounds(candidate))
                    && candidate.subtract(player).horizontalDistance() >= 24 && solidSpawnGround(candidate)) {
                walker = candidate; walkerLife = 1800; walked = 0; retreating = false;
                sprinter = sprinting; watched = true; return true;
            }
        }
        return false;
    }
    private static boolean solidSpawnGround(Vec3 feet) {
        // Support the whole footprint, rather than letting a thin edge or plant count as terrain.
        for (double x : new double[]{-.29, .29}) for (double z : new double[]{-.29, .29}) {
            BlockPos pos = BlockPos.containing(feet.add(x, -.05, z));
            if (!LoadedChunks.has(world, pos)) return false;
            var state = world.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || !state.isFaceSturdy(world, pos, net.minecraft.core.Direction.UP)) return false;
            var shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty() || Math.abs(pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) - feet.y) > .01) return false;
        }
        return true;
    }
    private static void walk(Vec3 player) {
        // Watching suspends movement, phase transitions and the active-time safety timeout.
        if (watched || view == null) return;
        if (--walkerLife <= 0) { walker = null; return; }
        // Four ordinary checked strides prevent the faster variant crossing walls or entering view.
        for (int stride = 0; stride < (sprinter ? 4 : 1) && walker != null; stride++) walkStride(player);
    }
    private static void walkStride(Vec3 player) {
        var listenerEntity = Minecraft.getInstance().player; if (listenerEntity == null) { walker = null; return; }
        double distance = walker.distanceTo(player);
        if (distance > 40 || (retreating && distance >= 16)
                || (sprinter && bounds(walker).intersects(listenerEntity.getBoundingBox()))) { walker = null; return; }
        if (!sprinter && !retreating && distance <= 3) retreating = true;
        Vec3 direction = retreating ? walker.subtract(player) : player.subtract(walker);
        heading = Math.atan2(direction.z, direction.x);
        for (double offset : new double[]{0, .4, -.4, .8, -.8, 1.2, -1.2}) {
            Vec3 next = ground(walker.add(Math.cos(heading + offset) * .085, 0, Math.sin(heading + offset) * .085), 1);
            if (next == null || view.isVisible(bounds(next))) continue;
            // Reject sidesteps that reverse the current approach/retreat progress.
            if (retreating ? next.distanceTo(player) <= distance : next.distanceTo(player) >= distance) continue;
            walked += next.distanceTo(walker); walker = next;
            if (sprinter && bounds(walker).intersects(listenerEntity.getBoundingBox())) {
                walker = null; return;
            }
            if (walked >= .8) {
                walked = 0;
                var sound = world.getBlockState(BlockPos.containing(walker.add(0, -.1, 0))).getSoundType();
                world.playLocalSound(walker.x, walker.y, walker.z, sound.getStepSound(), SoundSource.AMBIENT,
                    .55F, .9F + RANDOM.nextFloat() * .15F, false);
            }
            break;
        }
    }
    private static void tick(Minecraft client) {
        var listenerEntity = client.player;
        if (world != client.level) { world = client.level; reset(); }
        if (world == null || listenerEntity == null || client.isPaused()) return;
        if (!naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled() || naturality.client.ui.menus.previews.MenuPreview.active()
                || !listenerEntity.isAlive() || listenerEntity.isSpectator() || listenerEntity.isSleeping()) { reset(); return; }
        Vec3 listener = client.gameRenderer.mainCamera().position();
        if (walker != null) {
            walk(listenerEntity.position());
        }
        if (eyes != null) {
            eyeLife++;
            double distance = listener.distanceTo(eyes);
            double dot = client.gameRenderer.mainCamera().forwardVector().dot(eyes.subtract(listener).normalize().toVector3f());
            stare = dot > .995 && clear(listener, eyes) ? stare + 1 : 0;
            if (fade < 0 && (stare >= 35 || distance <= EYE_FADE_DISTANCE || eyeLife >= 400
                    || (!testEyes && !dark(eyes)) || !blackBackdrop(listener, eyes))) fade = 0;
            if (fade >= 0 && ++fade >= 30) { eyes = null; cooldown = 1200; }
        }
        if (cooldown > 0) { cooldown--; return; }
        if (++rolls < 100) return;
        rolls = 0;
        if (walker == null && RANDOM.nextInt(1000) == 0) {
            spawnWalker(listenerEntity.position(), 12, RANDOM.nextInt(5) == 0);
        }
        if (eyes == null && RANDOM.nextInt(140) == 0) {
            Vec3 candidate = findEyes(client.gameRenderer.mainCamera(), false);
            if (candidate != null) startEyes(candidate, false);
        }
    }
    private static boolean dark(Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        return LoadedChunks.has(world, pos) && world.getBrightness(LightLayer.BLOCK, pos) == 0
                && naturality.client.rendering.lighting.dynamic.DynamicLighting.smoothLight(world.getLightEngine(), pos.asLong()) == 0
                && world.getBrightness(LightLayer.SKY, pos) == 0 && world.getFluidState(pos).isEmpty();
    }
    /** Trace the background along the listener's sight lines, not just light at the dots. */
    private static boolean blackBackdrop(Vec3 listener, Vec3 position) {
        var listenerEntity = Minecraft.getInstance().player; if (listenerEntity == null) return false;
        if (naturality.client.rendering.lighting.hardcore.HardcoreDarkness.ambientMultiplier(world) > 0) return false;
        if (listenerEntity.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) return false;
        Vec3 direction = position.subtract(listener).normalize();
        Vec3 reference = Math.abs(direction.y) > .999 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = direction.cross(reference).normalize(), up = right.cross(direction).normalize();
        for (double horizontal : new double[]{-.45, 0, .45}) for (double vertical : new double[]{-.12, 0, .12}) {
            Vec3 sample = position.add(right.scale(horizontal)).add(up.scale(vertical));
            Vec3 ray = sample.subtract(listener).normalize(), end = sample.add(ray.scale(64));
            // Unknown or unloaded background is never assumed pitch black.
            for (int step = 0; step <= 64; step += 2) {
                Vec3 point = sample.add(ray.scale(step));
                if (!LoadedChunks.has(world, BlockPos.containing(point))) return false;
                // Stop checking loadedness at the first opaque background surface.
                if (!world.getBlockState(BlockPos.containing(point)).getCollisionShape(world, BlockPos.containing(point)).isEmpty()) break;
            }
            var hit = world.clip(new ClipContext(sample, end, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY,
                listenerEntity));
            if (hit.getType() == HitResult.Type.MISS) return false;
            Vec3 surface = hit.getLocation().subtract(ray.scale(.05));
            if (!LoadedChunks.has(world, hit.getBlockPos()) || !dark(surface)
                    || !world.getBlockState(hit.getBlockPos()).canOcclude()
                    || world.getBlockState(hit.getBlockPos()).getLightEmission() > 0) return false;
        }
        return true;
    }
    private static boolean clear(Vec3 from, Vec3 to) {
        var listenerEntity = Minecraft.getInstance().player; if (listenerEntity == null) return false;
        for (int i = 0, n = (int)Math.ceil(from.distanceTo(to)); i <= n; i++)
            if (!LoadedChunks.has(world, BlockPos.containing(from.lerp(to, i / (double)Math.max(1, n))))) return false;
        return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                listenerEntity)).getType() == HitResult.Type.MISS;
    }
    private static Vec3 ground(Vec3 p, int range) {
        for (int dy = range; dy >= -range; dy--) {
            BlockPos floor = BlockPos.containing(p).offset(0, dy - 1, 0);
            if (!LoadedChunks.has(world, floor) || !LoadedChunks.has(world, floor.above(2))) continue;
            var shape = world.getBlockState(floor).getCollisionShape(world, floor);
            if (shape.isEmpty()) continue;
            Vec3 feet = new Vec3(p.x, floor.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), p.z);
            if (Math.abs(feet.y - p.y) <= range + .1 && world.getFluidState(BlockPos.containing(feet)).isEmpty()
                    && world.noBlockCollision(null, bounds(feet))) return feet;
        }
        return null;
    }
    public static void extract(ParticlesRenderState output, Frustum frustum, Camera camera, float partial) {
        // Terrain/particle culling uses an expanded frustum around the camera cube.
        // Use the actual projection so an offscreen walker can approach closely.
        view = new Frustum(new org.joml.Matrix4f(), camera.getViewRotationProjectionMatrix(new org.joml.Matrix4f()));
        view.prepare(camera.position().x, camera.position().y, camera.position().z);
        watched = walker == null || view.isVisible(bounds(walker));
        if (eyes != null && world == Minecraft.getInstance().level && clear(camera.position(), eyes))
            HorrorEyesLayer.extract(output, camera, eyes, opacity(eyeLife, fade, partial), eyeVariant);
    }

    /** Private effects can be tested on multiplayer servers without operator permission. */
    public static final class HorrorCommands {
        public static void initialize() {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(
                literal("naturalityhorror")
                    .then(literal("walker").executes(context -> trigger(context.getSource(), false)))
                    .then(literal("sprinter").executes(context -> {
                        if (!AmbientHorror.testSprinter()) {
                            context.getSource().sendError(Component.literal("No offscreen solid terrain 24-36 blocks away."));
                            return 0;
                        }
                        context.getSource().sendFeedback(Component.literal("spawned walker"));
                        return 1;
                    }))
                    .then(literal("eyes").executes(context -> trigger(context.getSource(), true)))
                    .then(literal("clear").executes(context -> {
                        AmbientHorror.clearEffects();
                        context.getSource().sendFeedback(Component.literal("Cleared ambient horror effects."));
                        return 1;
                    }))));
        }
        private static int trigger(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, boolean eyes) {
            if (!AmbientHorror.test(eyes)) {
                source.sendError(Component.literal(eyes
                    ? "No clear space 8-24 blocks ahead. Face an open area while awake and outside spectator mode."
                    : "No offscreen solid terrain 24-36 blocks away."));
                return 0;
            }
            source.sendFeedback(Component.literal(eyes ? "spawned eyes" : "spawned walker"));
            return 1;
        }
        private HorrorCommands() {}
    }
}
