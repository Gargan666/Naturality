package naturality.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import naturality.client.snow.SnowSpritePixels;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A single convex opaque cube must cover the WHOLE padded render box. */
public final class OcclusionCulling {
    private static final Map<Object, Boolean> MODELS = new HashMap<>();
    private static final Map<Entity, BlockPos> OCCLUDERS = new WeakHashMap<>();
    private static final Map<net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection, Object> DIRTY = new WeakHashMap<>();
    private static final java.util.Set<net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection> VISIBLE =
        new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<>();
    private static boolean capturedVisibility;
    private static final RandomSource RANDOM = RandomSource.create(0);
    private static final boolean SODIUM = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
    private static Object world;
    private static long frame;
    private static int budget;
    private OcclusionCulling() {}
    public static void reset() { MODELS.clear(); OCCLUDERS.clear(); DIRTY.clear(); VISIBLE.clear(); capturedVisibility = false; world = null; }
    public static void changed(BlockPos pos) {
        if (SODIUM) return;
        var level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
        var view = Minecraft.getInstance().levelRenderer.viewArea();
        if (view == null) return;
        var section = view.getRenderSectionAt(pos);
        if (section != null) DIRTY.put(section, section.getSectionMesh());
    }
    public static void chunkLoaded(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.level.ChunkPos pos) {
        if (SODIUM) return;
        var block = new BlockPos.MutableBlockPos();
        for (int y = level.getMinY(); y <= level.getMaxY(); y += 16) changed(block.set(pos.x() << 4, y, pos.z() << 4));
    }
    public static void beginFrame() { frame++; budget = 256; capturedVisibility = false; }
    public static boolean hidden(Entity entity, AABB bounds) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (SODIUM || level == null || entity.level() != level || entity.isCurrentlyGlowing()
                || entity.hasCustomName() || entity.isPassenger() || entity.isVehicle()) return false;
        // Renderers with beams, nameplates, long tentacles, ropes or other distant
        // effects deliberately stay on the renderer's own visibility path.
        var type = entity.getType();
        if (!(type == EntityTypes.ITEM || type == EntityTypes.COW || type == EntityTypes.PIG
                || type == EntityTypes.SHEEP || type == EntityTypes.CHICKEN || type == EntityTypes.ZOMBIE
                || type == EntityTypes.SKELETON || type == EntityTypes.CREEPER || type == EntityTypes.VILLAGER)) return false;
        if (entity instanceof net.minecraft.world.entity.Leashable leash && leash.getLeashHolder() != null) return false;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living && living.isDeadOrDying()) return false;
        // The bobber draws a separate line from the villager's rendered rod tip.
        // Keep that pose fresh even when the villager's body is hidden.
        if (entity instanceof naturality.villager.VillagerWorkVisuals work && work.naturality$castTarget() != null) return false;
        if (world != level) { reset(); world = level; }
        Vec3 eye = client.gameRenderer.mainCamera().position();
        if (bounds.getCenter().distanceToSqr(eye) < 24 * 24) return false;
        AABB padded = bounds.inflate(2);
        var previous = OCCLUDERS.get(entity);
        if (previous != null && covers(eye, padded, previous) && opaque(previous)) return true;
        OCCLUDERS.remove(entity);
        // Missing a cull is safe. Bound exploratory work in crowded/open scenes;
        // proven occluders are revalidated every frame, including camera motion.
        if (budget <= 0 || Math.floorMod(entity.getId(), 4) != (frame & 3)) return false;
        Vec3 delta = padded.getCenter().subtract(eye);
        double length = delta.length();
        if (!Double.isFinite(length) || length <= 0) return false;
        Vec3 direction = delta.scale(1 / length);
        var pos = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        for (double distance = .5; distance < Math.min(length - 3, 24) && budget > 0; distance += .5) {
            pos.set(eye.x + direction.x * distance, eye.y + direction.y * distance, eye.z + direction.z * distance);
            if (pos.asLong() == last) continue;
            last = pos.asLong(); budget--;
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
            if (covers(eye, padded, pos) && opaque(pos)) {
                OCCLUDERS.put(entity, pos.immutable()); return true;
            }
        }
        return false;
    }
    private static boolean opaque(BlockPos pos) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        var state = level.getBlockState(pos);
        if (!state.isSolidRender() || !state.getFluidState().isEmpty()) return false;
        var view = client.levelRenderer.viewArea();
        if (view == null) return false;
        var section = view.getRenderSectionAt(pos);
        if (section == null || section.getSectionMesh() == net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED
                || section.getSectionMesh().isEmpty(net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID)
                || section.getVisibility(net.minecraft.util.Util.getMillis(), net.minecraft.util.Util.toMillis(client.options.chunkSectionFadeInTime().get())) < 1)
            return false;
        // A compiled mesh may still be absent from the terrain visibility graph.
        if (!capturedVisibility) {
            VISIBLE.clear(); VISIBLE.addAll(client.levelRenderer.visibleSections()); capturedVisibility = true;
        }
        if (!VISIBLE.contains(section)) return false;
        if (DIRTY.get(section) == section.getSectionMesh()) return false;
        DIRTY.remove(section);
        var model = client.getModelManager().getBlockStateModelSet().get(state);
        var random = RANDOM; random.setSeed(0);
        var key = model.createGeometryKey(level, pos, state, random);
        if (key == null) return false;
        var result = MODELS.get(key);
        if (result != null) return result;
        int[] faces = {0};
        var emitter = Renderer.get().mutableMesh().emitter();
        emitter.pushTransform(quad -> {
            if (!DistanceModel.fullFace(quad) || quad.animated()
                    || quad.chunkLayer() != net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID) return false;
            float u = 0, v = 0;
            for (int i = 0; i < 4; i++) {
                if ((quad.color(i) >>> 24) != 255) return false;
                u += quad.u(i); v += quad.v(i);
            }
            var sprite = DistantTextures.find(u / 4, v / 4);
            if (sprite == null) sprite = naturality.client.snow.SnowTextureAtlas.findComposite(u / 4, v / 4);
            if (sprite == null || sprite.isAnimated()) return false;
            if ((long)sprite.contents().width() * sprite.contents().height() > 4096) return false;
            var pixels = ((SnowSpritePixels)sprite.contents()).naturality$image();
            for (int y = 0; y < sprite.contents().height(); y++) for (int x = 0; x < sprite.contents().width(); x++)
                if ((pixels.getPixel(x, y) >>> 24) != 255) return false;
            faces[0] |= 1 << quad.lightFace().ordinal();
            return false;
        });
        try { model.emitQuads(emitter, level, pos, state, random, _ -> false); }
        finally { emitter.popTransform(); }
        boolean solid = faces[0] == 63;
        if (MODELS.size() < 4096) MODELS.put(key, solid);
        return solid;
    }
    public static boolean covers(Vec3 eye, AABB bounds, BlockPos block) {
        // Shrink the blocker slightly to avoid hiding any boundary pixels.
        double minX = block.getX() + .001, minY = block.getY() + .001, minZ = block.getZ() + .001;
        double maxX = block.getX() + .999, maxY = block.getY() + .999, maxZ = block.getZ() + .999;
        if (eye.x >= minX && eye.x <= maxX && eye.y >= minY && eye.y <= maxY && eye.z >= minZ && eye.z <= maxZ) return false;
        for (int corner = 0; corner < 8; corner++) {
            double dx = ((corner & 1) == 0 ? bounds.minX : bounds.maxX) - eye.x;
            double dy = ((corner & 2) == 0 ? bounds.minY : bounds.maxY) - eye.y;
            double dz = ((corner & 4) == 0 ? bounds.minZ : bounds.maxZ) - eye.z;
            double enter = 0, exit = 1;
            for (int axis = 0; axis < 3; axis++) {
                double origin = axis == 0 ? eye.x : axis == 1 ? eye.y : eye.z;
                double ray = axis == 0 ? dx : axis == 1 ? dy : dz;
                double min = axis == 0 ? minX : axis == 1 ? minY : minZ;
                double max = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
                if (Math.abs(ray) < 1e-12) { if (origin <= min || origin >= max) return false; }
                else {
                    double a = (min - origin) / ray, b = (max - origin) / ray;
                    enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
                }
            }
            if (!(enter < exit && enter > 0 && exit < 1)) return false;
        }
        return true;
    }
}
