package naturality.client.rendering.lighting.dynamic;

import naturality.client.rendering.lighting.dynamic.FractionalLightField.LightSourcePlacement;
import java.util.*;
import naturality.config.live.NaturalityConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Client-only virtual emitters with an occluded field and a vanilla-light underlay. */
@org.jspecify.annotations.NullMarked
public final class DynamicLighting {
    private record State(@org.jspecify.annotations.Nullable Object engine, it.unimi.dsi.fastutil.longs.Long2IntMap emitters,
            it.unimi.dsi.fastutil.longs.Long2IntMap field, boolean precise, int sourceCount,
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<net.minecraft.world.level.chunk.DataLayer> sections) {}
    private record Source(long id, Vec3 position, int light, double distance) {}
    private record Placement(Vec3 desired, Vec3 owner, @org.jspecify.annotations.Nullable Vec3 resolved) {
        boolean affected(BlockPos pos) {
            // Keep the full vertical columns: sky-dependent foliage shapes can
            // change when a distant roof is edited. The horizontal margin covers
            // endpoint escape, eight-block shelter probes and lateral skylight.
            // Include the whole owner-to-emitter ray, including failed placements.
            int margin = 40;
            return pos.getX() >= Math.min(desired.x, owner.x) - margin
                && pos.getX() <= Math.max(desired.x, owner.x) + margin
                && pos.getZ() >= Math.min(desired.z, owner.z) - margin
                && pos.getZ() <= Math.max(desired.z, owner.z) + margin;
        }
    }
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Placement> placements = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private record Drop(long id, Vec3 position, ItemStack item, Set<Integer> existingItems, int expires) {}
    private static volatile State state = emptyState();
    private static @org.jspecify.annotations.Nullable ClientLevel level;
    private static int ticks;
    private static boolean dirty = true;
    private static boolean lastSubBlock;
    private static List<Source> previousSources = List.of();
    private static final List<Drop> pendingDrops = new ArrayList<>();
    private static long nextDropId = Long.MIN_VALUE;

    private static State emptyState() { return new State(null, it.unimi.dsi.fastutil.longs.Long2IntMaps.EMPTY_MAP, it.unimi.dsi.fastutil.longs.Long2IntMaps.EMPTY_MAP, false, 0, it.unimi.dsi.fastutil.longs.Long2ObjectMaps.emptyMap()); }

    private DynamicLighting() {}

    public static int emission(Object engine, long position) {
        State current = state;
        return current.engine == engine ? current.emitters.get(position) : 0;
    }

    public static int sourceCount() { return state.sourceCount; }
    public static void invalidate() { dirty = true; }
    /** Only geometry within a source's maximum propagation radius can alter its field. */
    public static void invalidate(BlockPos position) {
        placements.values().removeIf(placement -> placement.affected(position));
        for (Source source : previousSources) {
            if (Math.abs(source.position.x - position.getX()) <= 17
                    && Math.abs(source.position.y - position.getY()) <= 17
                    && Math.abs(source.position.z - position.getZ()) <= 17) { dirty = true; return; }
        }
    }

    public static int smoothLight(Object engine, long position) {
        State current = state;
        return current.engine == engine && current.precise ? current.field.get(position) : 0;
    }

    public static int blockLight(Object engine, long position) {
        State current = state;
        return current.engine == engine ? current.field.get(position) / 16 : 0;
    }

    /** Replacement engines expose section arrays directly to mesh builders. Never edit their storage. */
    public static net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer data(Object engine,
            net.minecraft.core.SectionPos position, net.minecraft.world.level.chunk.@org.jspecify.annotations.Nullable DataLayer vanilla) {
        State current = state;
        if (engine != current.engine) return vanilla;
        var extra = current.sections.get(position.asLong());
        if (extra == null) return vanilla;
        return vanilla == null ? extra.copy() : naturality.client.api.rendering.LightLayers.mergeLightData(vanilla, extra);
    }

    /** Called before the local inventory prediction removes a thrown item. */
    public static void beginDrop(LivingEntity owner, ItemStack item) {
        var config = NaturalityConfig.get().dynamicLighting;
        var level = DynamicLighting.level;
        if (level == null || owner.level() != level || !config.enabled || !config.heldItems
                || !config.droppedItems || itemLight(item) == 0) return;
        Vec3 position = LightSourcePlacement.resolve(level, handPosition(owner, true), owner.getEyePosition());
        if (position == null) return;
        var existing = new HashSet<Integer>();
        for (Entity entity : level.entitiesForRendering()) if (entity instanceof ItemEntity) existing.add(entity.getId());
        // Allow one second for the spawn/metadata packets. Only an actual drop
        // gets this bridge; unequipping a light still extinguishes it immediately.
        pendingDrops.add(new Drop(nextDropId++, position, item.copy(), existing, ticks + 20));
        dirty = true;
    }

    public static void tick(Minecraft client) {
        if (client.level != level) {
            state = emptyState();
            level = client.level;
            ticks = 0;
            dirty = true;
            previousSources = List.of();
            placements.clear();
            pendingDrops.clear();
        }
        var level = DynamicLighting.level;
        if (level == null || client.isPaused()) return;
        var config = NaturalityConfig.get().dynamicLighting;
        if (!config.enabled) {
            if (!state.emitters.isEmpty() || !state.field.isEmpty()) publish(it.unimi.dsi.fastutil.longs.Long2IntMaps.EMPTY_MAP, it.unimi.dsi.fastutil.longs.Long2IntMaps.EMPTY_MAP, false, 0);
            previousSources = List.of();
            placements.clear();
            pendingDrops.clear();
            ticks = 0;
            return;
        }
        boolean scheduled = ticks++ % Math.clamp(config.updateTicks, 1, 20) == 0;
        // Bound cache lifetime and retain the existing one-second geometry refresh.
        if (dirty || ticks % 20 == 1 || placements.size() > 1024) placements.clear();
        var camera = client.gameRenderer.mainCamera().position();
        var sources = new ArrayList<Source>();
        double range = Math.clamp(config.sourceRange, 16, 128);
        if (!config.heldItems || !config.droppedItems) pendingDrops.clear();
        pendingDrops.removeIf(drop -> ticks >= drop.expires);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.isSpectator() || entity.position().distanceToSqr(camera) > range * range) continue;
            if (config.heldItems && entity instanceof LivingEntity living) {
                int mainLight = itemLight(living.getMainHandItem()), offLight = itemLight(living.getOffhandItem());
                if (mainLight > 0) add(sources, (long)entity.getId() * 4, handPosition(living, true), living.getEyePosition(), mainLight, camera, config);
                if (offLight > 0) add(sources, (long)entity.getId() * 4 + 1, handPosition(living, false), living.getEyePosition(), offLight, camera, config);
            }
            int bodyLight = config.emissiveMobs
                ? config.entityLightLevels.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), 0) : 0;
            if (entity instanceof net.minecraft.world.entity.animal.squid.GlowSquid squid && squid.getDarkTicksRemaining() > 0) bodyLight = 0;
            if (config.emissiveMobs && entity instanceof net.minecraft.world.entity.monster.Creeper creeper && creeper.isPowered())
                bodyLight = config.entityLightLevels.getOrDefault("minecraft:creeper", 15);
            if (config.burningEntities && entity.isOnFire()) bodyLight = Math.max(bodyLight, 15);
            if (config.droppedItems && entity instanceof ItemEntity item) {
                bodyLight = Math.max(bodyLight, itemLight(item.getItem()));
                for (var iterator = pendingDrops.iterator(); iterator.hasNext();) {
                    Drop drop = iterator.next();
                    if (!drop.existingItems.contains(item.getId()) && drop.position.distanceToSqr(item.position()) < 16
                            && ItemStack.isSameItemSameComponents(drop.item, item.getItem())) {
                        iterator.remove();
                        break;
                    }
                }
            }
            if (bodyLight > 0) {
                Vec3 body = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
                add(sources, (long)entity.getId() * 4 + 2, body, body, bodyLight, camera, config);
            }
        }
        for (Drop drop : pendingDrops) if (drop.position.distanceToSqr(camera) <= range * range)
            add(sources, drop.id, drop.position, drop.position, itemLight(drop.item), camera, config);
        sources.sort(Comparator.comparingDouble(Source::distance));
        if (sources.size() > Math.clamp(config.maxSources, 8, 128))
            sources.subList(Math.clamp(config.maxSources, 8, 128), sources.size()).clear();
        // Select by distance as before, then compare by identity: walking around
        // stationary lights must not invalidate the field when their order swaps.
        sources.sort(Comparator.comparingLong(Source::id));
        boolean sameSources = sources.size() == previousSources.size();
        for (int i = 0; sameSources && i < sources.size(); i++)
            sameSources = sources.get(i).id == previousSources.get(i).id
                && sources.get(i).light == previousSources.get(i).light;
        if (!scheduled && sameSources && !dirty && lastSubBlock == config.subBlockPrecision) return;
        // Distance is only a sorting key; camera movement must not rebuild a fixed field.
        boolean unchanged = sources.size() == previousSources.size();
        for (int i = 0; unchanged && i < sources.size(); i++) {
            var a = sources.get(i); var b = previousSources.get(i);
            unchanged = a.position.equals(b.position) && a.light == b.light;
        }
        if (unchanged && !dirty && lastSubBlock == config.subBlockPrecision && ticks % 20 != 1) {
            previousSources = List.copyOf(sources);
            // Server lighting packets can overwrite the integral underlay.
            for (long key : state.emitters.keySet()) level.getLightEngine().checkBlock(BlockPos.of(key));
            return;
        }
        previousSources = List.copyOf(sources);
        lastSubBlock = config.subBlockPrecision;
        dirty = false;
        var next = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        var field = new FractionalLightField(level);
        for (Source source : sources) {
            if (config.subBlockPrecision) field.add(source.position, source.light);
            else field.addBlock(BlockPos.containing(source.position), source.light);
        }
        for (var entry : field.seeds().long2IntEntrySet())
            if (entry.getIntValue() >= 16) next.put(entry.getLongKey(), entry.getIntValue() / 16);
        publish(next, field.propagate(), config.subBlockPrecision, sources.size());
    }

    private static void add(List<Source> sources, long id, Vec3 position, Vec3 owner, int light,
            Vec3 camera, NaturalityConfig.DynamicLighting config) {
        var level = DynamicLighting.level;
        if (level == null) return;
        light = Math.clamp((int)Math.round(light * Math.clamp(config.brightnessPercent, 25, 200) / 100.0), 0, 15);
        if (light == 0) return;
        var cached = placements.get(id);
        if (cached == null || !cached.desired.equals(position) || !cached.owner.equals(owner)) {
            cached = new Placement(position, owner, LightSourcePlacement.resolve(level, position, owner));
            placements.put(id, cached);
        }
        Vec3 placed = cached.resolved;
        if (placed != null) sources.add(new Source(id, placed, light, placed.distanceToSqr(camera)));
    }

    public static Vec3 handPosition(LivingEntity entity, boolean mainHand) {
        boolean right = (entity.getMainArm() == HumanoidArm.RIGHT) == mainHand;
        double yaw = Math.toRadians(entity.getYRot());
        double side = right ? -0.35 : 0.35;
        return entity.getEyePosition().add(Math.cos(yaw) * side, -0.4, Math.sin(yaw) * side)
            .add(entity.getViewVector(1.0F).scale(0.3));
    }

    public static int itemLight(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.LAVA_BUCKET)) return 15;
        if (stack.is(Items.GLOW_BERRIES)) return 14;
        if (stack.getItem() instanceof BlockItem block) return block.getBlock().defaultBlockState().getLightEmission();
        if (stack.is(Items.BLAZE_ROD) || stack.is(Items.BLAZE_POWDER)) return 10;
        if (stack.is(Items.GLOWSTONE_DUST) || stack.is(Items.GLOW_INK_SAC)) return 8;
        return 0;
    }

    private static void publish(it.unimi.dsi.fastutil.longs.Long2IntMap next, it.unimi.dsi.fastutil.longs.Long2IntMap smooth, boolean precise, int count) {
        var level = DynamicLighting.level;
        if (level == null) return;
        var engine = level.getLightEngine();
        var previous = state.emitters;
        var oldSmooth = state.field;
        var reader = engine.getLayerListener(LightLayer.BLOCK);
        // Native block light already propagates our emitters into its storage.
        // Replacement engines need snapshots of the very same occluded field.
        boolean replacement = !(reader instanceof net.minecraft.world.level.lighting.BlockLightEngine);
        var layers = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.level.chunk.DataLayer>();
        // Fractional changes can leave integral light unchanged; explicitly rebuild
        // affected sections, including neighbors whose AO samples cross a border.
        var sections = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (var entry : smooth.long2IntEntrySet()) {
            long position = entry.getLongKey();
            if (oldSmooth.get(position) != entry.getIntValue()) addChangedSections(sections, position);
            int light = entry.getIntValue() / 16;
            if (replacement && light > 0) {
                int x = BlockPos.getX(position), y = BlockPos.getY(position), z = BlockPos.getZ(position);
                layers.computeIfAbsent(net.minecraft.core.SectionPos.asLong(x >> 4, y >> 4, z >> 4),
                    _ -> new net.minecraft.world.level.chunk.DataLayer()).set(x & 15, y & 15, z & 15, light);
            }
        }
        for (long key : oldSmooth.keySet()) if (!smooth.containsKey(key)) addChangedSections(sections, key);
        state = new State(reader, it.unimi.dsi.fastutil.longs.Long2IntMaps.unmodifiable(next), smooth, precise, count,
            it.unimi.dsi.fastutil.longs.Long2ObjectMaps.unmodifiable(layers));
        for (long section : sections) {
            int x = net.minecraft.core.SectionPos.x(section), y = net.minecraft.core.SectionPos.y(section), z = net.minecraft.core.SectionPos.z(section);
            level.setSectionRangeDirty(x, y, z, x, y, z);
        }
        // Publish first: removals must be seen when decrease propagation executes.
        for (long old : previous.keySet()) if (!next.containsKey(old)) engine.checkBlock(BlockPos.of(old));
        // Recheck current sources too, restoring them after server light packets.
        for (long current : next.keySet()) engine.checkBlock(BlockPos.of(current));
    }

    private static void addChangedSections(it.unimi.dsi.fastutil.longs.LongSet sections, long block) {
        int x = BlockPos.getX(block), y = BlockPos.getY(block), z = BlockPos.getZ(block);
        // AO crosses block boundaries, not an entire 16-block section in every direction.
        for (int sx = (x - 1) >> 4; sx <= (x + 1) >> 4; sx++)
            for (int sy = (y - 1) >> 4; sy <= (y + 1) >> 4; sy++)
                for (int sz = (z - 1) >> 4; sz <= (z + 1) >> 4; sz++)
                    sections.add(net.minecraft.core.SectionPos.asLong(sx, sy, sz));
    }

    /** Bell supports have stretched UVs; their lighting cells follow texture texels. */
    public static final class BellLightingModel extends WrapperBlockStateModel {
        // Reserved opaque-quad marker, outside foliage, snow and underwater tags.
        public static final int TEXEL_LIGHTING_TAG = 242;

        private BellLightingModel(BlockStateModel wrapped) { super(wrapped); }
        private record GeometryKey(Object wrappedKey) {}

        @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level,
                BlockPos pos, BlockState state, RandomSource random) {
            var key = wrapped.createGeometryKey(level, pos, state, random);
            // Cached tagged quads must not alias the original model's unmarked quads.
            return key == null ? null : new GeometryKey(key);
        }

        public static void initialize() {
            ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
                ModelModifier.WRAP_LAST_PHASE, (model, bake) -> bake.state().is(Blocks.BELL)
                    ? new BellLightingModel(model) : model));
        }

        @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos,
                BlockState state, RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
            emitter.pushTransform(quad -> {
                for (int i = 0; i < 4; i++) {
                    int color = quad.color(i);
                    if ((color >>> 24) == 255)
                        quad.color(i, (color & 0xFFFFFF) | (TEXEL_LIGHTING_TAG << 24));
                }
                return true;
            });
            try { wrapped.emitQuads(emitter, level, pos, state, random, cull); }
            finally { emitter.popTransform(); }
        }
    }
}
