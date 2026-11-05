package naturality.client.blocks.fire;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import naturality.client.particles.smoke.SmokeParticle.FireSmoke;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.NullMarked;

/** Local biome-foliage discoloration, using the shared loaded-fire index and bounded mesh refreshes. */
@NullMarked
public final class FireFoliageTint {
    private static final double RADIUS = 2;
    private static final int GROWTH_TICKS = 60;
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Source> sources =
        new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private record Source(double strength, double instant, int born) { }
    private static Long2DoubleOpenHashMap targets = new Long2DoubleOpenHashMap();
    private static final Long2DoubleOpenHashMap current = new Long2DoubleOpenHashMap();
    // Published maps are immutable after publication; chunk builders only read them.
    private static volatile Long2DoubleOpenHashMap visible = new Long2DoubleOpenHashMap();
    private static final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private static int revision = -1, ticks;

    private FireFoliageTint() { }

    public static boolean isScorched(net.minecraft.world.level.block.state.BlockState state) {
        // Check the preset before touching the content class: strict client mode must not register blocks.
        return !naturality.config.startup.FeatureConfig.clientOnly()
            && naturality.config.startup.FeatureConfig.Feature.CONTENT.enabled()
            && state.is(naturality.blocks.charred.CharredBlocks.GRASS);
    }

    public static void clear() {
        targets.clear(); current.clear(); dirty.clear(); sources.clear();
        visible = new Long2DoubleOpenHashMap();
        revision = -1; ticks = 0;
    }

    public static void refresh(int fireRevision, boolean loaded) {
        if (revision == fireRevision) return;
        revision = fireRevision;
        var strengths = new Long2DoubleOpenHashMap();
        FireSmoke.forEachTintSource((key, count) -> {
            if (count >= 16) strengths.put(key, Math.min(1, (count - 15) / 17.0));
        });
        sources.keySet().removeIf(key -> !strengths.containsKey(key));
        for (var entry : strengths.long2DoubleEntrySet()) {
            long key = entry.getLongKey();
            double strength = entry.getDoubleValue();
            Source old = sources.get(key);
            double instant = old == null ? 0 : Math.min(strength, old.instant);
            if (loaded) instant = Math.min(strength, instant + Math.max(0, strength - (old == null ? 0 : old.strength)));
            sources.put(key, new Source(strength, instant, old == null ? ticks : old.born));
        }
        var next = field(false);
        if (loaded) {
            // Only the newly loaded contribution snaps in. An ongoing placement fade
            // nearby retains its progress when an unrelated chunk arrives.
            for (var entry : next.long2DoubleEntrySet()) {
                long key = entry.getLongKey();
                double added = Math.max(0, entry.getDoubleValue() - targets.get(key));
                if (added > 0) current.put(key, Math.min(1, current.get(key) + added));
            }
        }
        targets = next;
        if (loaded) publish();
    }

    private static Long2DoubleOpenHashMap field(boolean animated) {
        var field = new Long2DoubleOpenHashMap();
        for (var entry : sources.long2ObjectEntrySet()) {
            var cell = BlockPos.of(entry.getLongKey());
            Source source = entry.getValue();
            double front = 1.25 + Math.min(GROWTH_TICKS, ticks - source.born) * 1.25 / GROWTH_TICKS;
            // Integer block positions are sampled directly: no coarse-cell interpolation halo.
            // Only these 27 offsets lie strictly inside the two-block radius.
            for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
                double distance = Math.sqrt(x * x + y * y + z * z);
                double weight = Math.clamp(RADIUS - distance, 0, 1);
                double arrival = animated ? Math.clamp((front - distance) / .5, 0, 1) : 1;
                arrival = arrival * arrival * (3 - 2 * arrival);
                weight = (source.instant + (source.strength - source.instant) * arrival) * weight * weight * (3 - 2 * weight);
                if (weight == 0) continue;
                long sample = BlockPos.asLong(cell.getX() + x, cell.getY() + y, cell.getZ() + z);
                field.put(sample, Math.max(field.get(sample), weight));
            }
        }
        return field;
    }

    public static void tick(ClientLevel level) {
        if (++ticks % 4 == 0) {
            boolean growing = sources.values().stream().anyMatch(source -> source.instant < source.strength
                && ticks - source.born < GROWTH_TICKS);
            // Settled sources reuse the cached field; only expanding fronts need rasterization.
            var desired = growing ? field(true) : targets;
            for (long key : desired.keySet()) if (!current.containsKey(key)) current.put(key, 0);
            var iterator = current.long2DoubleEntrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                double target = desired.get(entry.getLongKey()), value = entry.getDoubleValue();
                // Smooth approach over seconds; recovery is slower than onset.
                value += (target - value) * (target > value ? .28 : .033);
                if (Math.abs(target - value) < 1.0 / 512) value = target;
                if (value == 0) iterator.remove();
                else entry.setValue(value);
            }
            publish();
        }
        // Coalesce repeated changes and drain fairly. Never request or generate chunks.
        for (int budget = 0; budget < 8 && !dirty.isEmpty(); budget++) {
            var section = BlockPos.of(dirty.removeFirstLong());
            if (level.hasChunk(section.getX(), section.getZ()))
                level.setSectionRangeDirty(section.getX(), section.getY(), section.getZ(),
                    section.getX(), section.getY(), section.getZ());
        }
    }

    private static void publish() {
        var next = new Long2DoubleOpenHashMap();
        for (var entry : current.long2DoubleEntrySet()) {
            double value = Math.round(entry.getDoubleValue() * 64) / 64.0;
            if (value != 0) next.put(entry.getLongKey(), value);
        }
        var previous = visible;
        for (var entry : next.long2DoubleEntrySet())
            if (previous.get(entry.getLongKey()) != entry.getDoubleValue()) invalidate(entry.getLongKey());
        for (long key : previous.keySet()) if (!next.containsKey(key)) invalidate(key);
        visible = next;
    }

    private static void invalidate(long key) {
        var point = BlockPos.of(key);
        // Include adjacent mesh sections for tint interpolation at block faces.
        for (int y = ((point.getY() - 1) >> 4); y <= ((point.getY() + 1) >> 4); y++)
            for (int z = ((point.getZ() - 1) >> 4); z <= ((point.getZ() + 1) >> 4); z++)
                for (int x = ((point.getX() - 1) >> 4); x <= ((point.getX() + 1) >> 4); x++)
                    dirty.add(BlockPos.asLong(x, y, z));
    }

    public static double intensity(BlockPos pos) {
        return visible.get(pos.asLong());
    }

    public static int tint(int color, BlockPos pos) {
        double amount = intensity(pos);
        if (amount == 0) return color;
        int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
        double gray = .2126 * red + .7152 * green + .0722 * blue;
        double desaturation = Math.min(1, amount * 2.2), brightness = 1 - .45 * amount;
        red = (int) Math.round((red + (gray - red) * desaturation) * brightness);
        green = (int) Math.round((green + (gray - green) * desaturation) * brightness);
        blue = (int) Math.round((blue + (gray - blue) * desaturation) * brightness);
        return (color & 0xff000000) | red << 16 | green << 8 | blue;
    }
}
