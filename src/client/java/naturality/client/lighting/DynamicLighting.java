package naturality.client.lighting;

import java.util.*;
import naturality.config.NaturalityConfig;
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

/** Client-only virtual emitters with an occluded field and a vanilla-light underlay. */
public final class DynamicLighting {
    private record State(@org.jspecify.annotations.Nullable Object engine, Map<Long, Integer> emitters,
            it.unimi.dsi.fastutil.longs.Long2IntMap field, boolean precise, int sourceCount) {}
    private record Source(long id, Vec3 position, int light, double distance) {}
    private record Drop(long id, Vec3 position, ItemStack item, Set<Integer> existingItems, int expires) {}
    private static volatile State state = emptyState();
    private static @org.jspecify.annotations.Nullable ClientLevel level;
    private static int ticks;
    private static boolean dirty = true;
    private static boolean lastSubBlock;
    private static List<Source> previousSources = List.of();
    private static final List<Drop> pendingDrops = new ArrayList<>();
    private static long nextDropId = Long.MIN_VALUE;

    private static State emptyState() { return new State(null, Map.of(), it.unimi.dsi.fastutil.longs.Long2IntMaps.EMPTY_MAP, false, 0); }

    private DynamicLighting() {}

    public static int emission(Object engine, long position) {
        State current = state;
        return current.engine == engine ? current.emitters.getOrDefault(position, 0) : 0;
    }

    public static int sourceCount() { return state.sourceCount; }
    public static void invalidate() { dirty = true; }

    public static int smoothLight(Object engine, long position) {
        State current = state;
        return current.engine == engine && current.precise ? current.field.get(position) : 0;
    }

    public static int blockLight(Object engine, long position) {
        State current = state;
        return current.engine == engine ? current.field.get(position) / 16 : 0;
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
            pendingDrops.clear();
        }
        var level = DynamicLighting.level;
        if (level == null) return;
        var config = NaturalityConfig.get().dynamicLighting;
        if (!config.enabled) {
            if (!state.emitters.isEmpty() || !state.field.isEmpty()) publish(Map.of(), it.unimi.dsi.fastutil.longs.Long2IntMaps.EMPTY_MAP, false, 0);
            previousSources = List.of();
            pendingDrops.clear();
            ticks = 0;
            return;
        }
        boolean scheduled = ticks++ % Math.clamp(config.updateTicks, 1, 20) == 0;
        var camera = client.gameRenderer.mainCamera().position();
        var sources = new ArrayList<Source>();
        double range = Math.clamp(config.sourceRange, 16, 128);
        if (!config.heldItems || !config.droppedItems) pendingDrops.clear();
        pendingDrops.removeIf(drop -> ticks >= drop.expires);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.isSpectator() || entity.position().distanceToSqr(camera) > range * range) continue;
            if (config.heldItems && entity instanceof LivingEntity living) {
                add(sources, (long)entity.getId() * 4, handPosition(living, true), living.getEyePosition(), itemLight(living.getMainHandItem()), camera, config);
                add(sources, (long)entity.getId() * 4 + 1, handPosition(living, false), living.getEyePosition(), itemLight(living.getOffhandItem()), camera, config);
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
            Vec3 body = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            add(sources, (long)entity.getId() * 4 + 2, body, body, bodyLight, camera, config);
        }
        for (Drop drop : pendingDrops) if (drop.position.distanceToSqr(camera) <= range * range)
            add(sources, drop.id, drop.position, drop.position, itemLight(drop.item), camera, config);
        sources.sort(Comparator.comparingDouble(Source::distance));
        if (sources.size() > Math.clamp(config.maxSources, 8, 128))
            sources.subList(Math.clamp(config.maxSources, 8, 128), sources.size()).clear();
        // Membership/brightness changes bypass the movement cadence so the first
        // dropped-item tick replaces the held/bridging light in one publication.
        boolean sameSources = sources.size() == previousSources.size();
        if (sameSources) {
            var identities = new HashMap<Long, Integer>();
            for (Source source : previousSources) identities.put(source.id, source.light);
            for (Source source : sources) if (!Objects.equals(identities.get(source.id), source.light)) {
                sameSources = false;
                break;
            }
        }
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
        var next = new HashMap<Long, Integer>();
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
        Vec3 placed = LightSourcePlacement.resolve(level, position, owner);
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

    private static void publish(Map<Long, Integer> next, it.unimi.dsi.fastutil.longs.Long2IntMap smooth, boolean precise, int count) {
        var level = DynamicLighting.level;
        if (level == null) return;
        var engine = level.getLightEngine();
        var previous = state.emitters;
        var oldSmooth = state.field;
        state = new State(engine.getLayerListener(LightLayer.BLOCK), Map.copyOf(next), smooth, precise, count);
        // Fractional changes can leave integral light unchanged; explicitly rebuild
        // affected sections, including neighbors whose AO samples cross a border.
        var sections = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (var entry : smooth.long2IntEntrySet()) if (oldSmooth.get(entry.getLongKey()) != entry.getIntValue())
            sections.add(net.minecraft.core.SectionPos.blockToSection(entry.getLongKey()));
        for (long key : oldSmooth.keySet()) if (!smooth.containsKey(key)) sections.add(net.minecraft.core.SectionPos.blockToSection(key));
        for (long section : sections) level.setSectionDirtyWithNeighbors(net.minecraft.core.SectionPos.x(section),
            net.minecraft.core.SectionPos.y(section), net.minecraft.core.SectionPos.z(section));
        // Publish first: removals must be seen when decrease propagation executes.
        for (long old : previous.keySet()) if (!next.containsKey(old)) engine.checkBlock(BlockPos.of(old));
        // Recheck current sources too, restoring them after server light packets.
        for (long current : next.keySet()) engine.checkBlock(BlockPos.of(current));
    }
}
