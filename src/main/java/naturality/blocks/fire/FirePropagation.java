package naturality.blocks.fire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import naturality.Naturality;
import naturality.api.worlds.LoadedChunks;
import naturality.config.startup.FeatureConfig.Feature;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Server-owned ignition ancestry, bounded grass spread and supported fuel afterburn. */
@org.jspecify.annotations.NullMarked
public final class FirePropagation {
    private static final int GRASS_ATTEMPTS = 12;
    private static final int WORLD_ATTEMPTS_PER_TICK = 256;
    private static final int WORLD_IGNITIONS_PER_TICK = 32;
    private static final Direction[] NEIGHBORS = Direction.values();
    private static final TagKey<Block> LONG_FUEL = TagKey.create(Registries.BLOCK, Naturality.id("fire_residual_long"));
    private static final TagKey<Block> SHORT_FUEL = TagKey.create(Registries.BLOCK, Naturality.id("fire_residual_short"));
    private static final ThreadLocal<Context> TICK = new ThreadLocal<>();
    private static final ThreadLocal<Birth> BIRTH = new ThreadLocal<>();

    private FirePropagation() { }

    /** Shared physical classification for fuel ordering and its client preview. */
    public static boolean solidFuel(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return !state.is(BlockTags.LEAVES) && state.isCollisionShapeFullBlock(level, pos);
    }

    public static boolean covering(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof BaseFireBlock || !state.getFluidState().isEmpty()
                || solidFuel(level, pos, state)) return false;
        return state.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.VineBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.GrowingPlantBlock
            || ((naturality.blocks.fire.mixins.FireMixins.FireFuelAccessor) Blocks.FIRE).naturality$burnOdds(state) > 0;
    }

    /** Ascend an existing covering, never search or load adjacent chunks. */
    private static @Nullable BlockPos coveringTop(ServerLevel level, BlockPos bottom) {
        for (int dy = 0; dy <= 32; dy++) {
            BlockPos pos = bottom.above(dy);
            if (level.isOutsideBuildHeight(pos) || !LoadedChunks.has(level, pos)) return null;
            var state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.FIRE)) return pos;
            if (!covering(level, pos, state)) return null;
        }
        return null;
    }

    private static boolean igniteCovering(ServerLevel level, BlockPos bottom, Context source) {
        BlockPos top = coveringTop(level, bottom);
        FireData data = data(level);
        if (top == null || !level.getWorldBorder().isWithinBounds(top) || !level.getBlockState(top).isAir() || nearRain(level, top)
                || !level.canSpreadFireAround(top) || data.ignitionsThisTick >= WORLD_IGNITIONS_PER_TICK) return false;
        var state = BaseFireBlock.getState(level, top);
        if (!state.canSurvive(level, top)) return false;
        boolean placed = withBirth(level, top, source.fire.lineage, 0, () -> level.setBlockAndUpdate(top, state));
        if (placed) data.ignitionsThisTick++;
        return placed;
    }

    public static void initialize() {
        if (!Feature.FIRE_PROPAGATION.enabled()) return;
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(AshPayload.TYPE, AshPayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(level -> data(level).advance(level));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, _) -> data(level).loaded(chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> data(level).activeChunks.remove(chunk.getPos().pack()));
    }

    public static FireData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FireData.TYPE);
    }

    /** Only explicit spread writes inherit ancestry; unrelated neighbor callbacks cannot inherit it. */
    public static boolean spread(ServerLevel level, BlockPos pos, BooleanSupplier operation) {
        Context context = TICK.get();
        if (context == null || context.level != level) return operation.getAsBoolean();
        return withBirth(level, pos, context.fire.lineage, 0, operation);
    }

    private static boolean withBirth(ServerLevel level, BlockPos pos, Lineage lineage, int ticks, BooleanSupplier operation) {
        Birth previous = BIRTH.get();
        BIRTH.set(new Birth(level, pos.immutable(), lineage, ticks));
        try { return operation.getAsBoolean(); }
        finally {
            if (previous == null) BIRTH.remove();
            else BIRTH.set(previous);
        }
    }

    public static void changed(LevelChunk chunk, BlockPos pos, @Nullable BlockState oldState) {
        if (oldState == null || !(chunk.getLevel() instanceof ServerLevel level)) return;
        BlockState current = chunk.getBlockState(pos);
        if (oldState.is(Blocks.FIRE) == current.is(Blocks.FIRE)) return;
        FireData data = data(level);
        if (!current.is(Blocks.FIRE)) {
            data.remove(pos);
            return;
        }
        Birth birth = BIRTH.get();
        if (birth != null && birth.level == level && birth.pos.equals(pos)) data.add(pos, birth.lineage, birth.ticks);
        else data.add(pos, data.newLineage(pos), 0);
    }

    public static void tick(ServerLevel level, BlockPos pos, RandomSource random, Runnable original) {
        FireData data = data(level);
        Fire fire = data.find(pos);
        if (fire == null) fire = data.add(pos, data.newLineage(pos), 0);
        Context previous = TICK.get();
        TICK.set(new Context(level, fire, pos.immutable()));
        try {
            if (fire.lineage.burnedBlocks() >= 16 && level.getGameTime() >= fire.lineage.nextAsh) {
                fire.lineage.nextAsh = level.getGameTime() + 40;
                sendAsh(level, pos, fire.lineage.burnedBlocks(), false);
            }
            if (level.canSpreadFireAround(pos)) {
                // A held afterburn requires a floor even when ordinary wrapping permits wall attachment.
                if (fire.deadline >= 0 && !supported(level, pos)) {
                    level.removeBlock(pos, false);
                    return;
                }
                BlockPos below = pos.below();
                if (LoadedChunks.has(level, below) && covering(level, below, level.getBlockState(below))
                        && !nearRain(level, pos)) {
                    // Consume the covering first; neither native spread nor grass conversion may skip it.
                    beginConsumption(level, below, () -> level.setBlockAndUpdate(below, BaseFireBlock.getState(level, below)));
                    level.scheduleTick(pos, Blocks.FIRE, 20);
                    return;
                }
                burnGrassBelow(level, pos, fire);
                if (grassSpread(level, pos, fire, random)) {
                    // Grass advances as a moving front. Children already own the ancestry before
                    // removing this source; do not let its stale vanilla tick place it back again.
                    if (data.find(pos) == fire && level.getBlockState(pos).is(Blocks.FIRE))
                        level.removeBlock(pos, false);
                    return;
                }
            }
            original.run();
        } finally {
            if (previous == null) TICK.remove();
            else TICK.set(previous);
        }
    }

    /** Called only for vanilla's dry natural-expiry removals, never rain or invalid placement. */
    public static boolean retain(ServerLevel level, BlockPos pos) {
        FireData data = data(level);
        Fire fire = data.find(pos);
        if (fire == null || fire.extraTicks <= 0 || !supported(level, pos)) return false;
        Bucket bucket = data.buckets.get(ChunkPos.pack(pos));
        if (bucket == null) return false;
        if (fire.deadline < 0) {
            fire.deadline = bucket.clock + fire.extraTicks;
            data.setDirty();
        }
        return bucket.clock < fire.deadline;
    }

    /** Observe the successful consumption write, including vanilla's removal-to-air branch. */
    public static boolean consume(ServerLevel level, BlockPos pos, BooleanSupplier operation) {
        Context context = TICK.get();
        if (context == null || context.level != level) return operation.getAsBoolean();
        BlockState fuel = level.getBlockState(pos);
        if (fuel.isAir() || fuel.getBlock() instanceof BaseFireBlock || fuel.getBlock() instanceof TntBlock)
            return operation.getAsBoolean();
        int ticks = fuelTicks(level, pos, fuel);
        boolean changed = withBirth(level, pos, context.fire.lineage, ticks, operation);
        if (!changed || level.getBlockState(pos).equals(fuel)) return changed;
        countBurn(level, pos, context.fire.lineage, fuel);
        if (level.getBlockState(pos).isAir() && supported(level, pos) && !nearRain(level, pos)) {
            withBirth(level, pos, context.fire.lineage, ticks,
                    () -> level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos)));
        }
        if (covering(level, pos, fuel) && context.pos.equals(pos.above())
                && level.getBlockState(pos).is(Blocks.FIRE) && data(level).find(context.pos) == context.fire)
            level.removeBlock(context.pos, false);
        return changed;
    }

    /** Redirect covered fuel before applying the successful vanilla burn immediately. */
    public static boolean beginConsumption(ServerLevel level, BlockPos pos, BooleanSupplier operation) {
        Context context = TICK.get();
        BlockState fuel = level.getBlockState(pos);
        if (context == null || context.level != level || fuel.isAir()
                || fuel.getBlock() instanceof BaseFireBlock || fuel.getBlock() instanceof TntBlock)
            return consume(level, pos, operation);
        BlockPos above = pos.above();
        if (LoadedChunks.has(level, above) && covering(level, above, level.getBlockState(above))) {
            igniteCovering(level, above, context);
            return false;
        }
        if (Feature.CONTENT.enabled()) {
            var replacement = naturality.blocks.charred.CharredBlocks.replacement(fuel);
            if (replacement != null) {
                boolean changed = consume(level, pos, () -> level.setBlockAndUpdate(pos, replacement));
                if (changed) {
                    // Native scheduled ticks belong to the old block type and cannot release
                    // a copied powered button/plate after its replacement.
                    if (replacement.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
                            && replacement.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                        if (replacement.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock)
                            level.scheduleTick(pos, replacement.getBlock(), 30);
                        else if (replacement.getBlock() instanceof net.minecraft.world.level.block.PressurePlateBlock)
                            level.scheduleTick(pos, replacement.getBlock(), 20);
                    }
                    igniteAroundRemains(level, pos, context, fuelTicks(level, pos, fuel));
                }
                return changed;
            }
        }
        return consume(level, pos, operation);
    }

    /** A bounded surface front leaves the original trunk intact and carries its ancestry. */
    private static void igniteAroundRemains(ServerLevel level, BlockPos pos, Context context, int ticks) {
        var data = data(level);
        for (Direction direction : NEIGHBORS) {
            BlockPos target = pos.relative(direction);
            if (!LoadedChunks.has(level, target) || level.isOutsideBuildHeight(target)
                    || !level.getWorldBorder().isWithinBounds(target) || nearRain(level, target)
                    || !level.canSpreadFireAround(target)) continue;
            var current = level.getBlockState(target);
            if (current.is(Blocks.FIRE)) {
                Fire existing = data.find(target);
                if (existing != null && existing.lineage == context.fire.lineage) {
                    existing.extraTicks = Math.max(existing.extraTicks, ticks);
                    data.setDirty();
                }
                continue;
            }
            if (!current.isAir() || data.ignitionsThisTick >= WORLD_IGNITIONS_PER_TICK) continue;
            var fire = BaseFireBlock.getState(level, target);
            if (fire.canSurvive(level, target) && withBirth(level, target, context.fire.lineage, ticks,
                    () -> level.setBlockAndUpdate(target, fire))) data.ignitionsThisTick++;
        }
    }

    public static int fuelTicks(ServerLevel level, BlockPos pos, BlockState fuel) {
        if (fuel.is(Blocks.GRASS_BLOCK)) return 0;
        if (fuel.is(LONG_FUEL)) return 60 * 20;
        if (fuel.is(SHORT_FUEL)) return 10 * 20;
        return fuel.getCollisionShape(level, pos).isEmpty() ? 5 * 20 : 30 * 20;
    }

    /** Smooth slowdown throughout the radius, exactly zero at and beyond 100 horizontal blocks. */
    public static double distanceFactor(BlockPos origin, BlockPos target) {
        double dx = (double) target.getX() - origin.getX();
        double dz = (double) target.getZ() - origin.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz) / 100.0;
        if (distance >= 1) return 0;
        return 1 - distance * distance * (3 - 2 * distance);
    }

    private static boolean supported(ServerLevel level, BlockPos pos) {
        return LoadedChunks.has(level, pos.below()) && FireGeometry.hasSupport(level, pos.below());
    }

    private static boolean nearRain(ServerLevel level, BlockPos pos) {
        if (!level.isRaining()) return false;
        if (level.isRainingAt(pos)) return true;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            if (!LoadedChunks.has(level, neighbor) || level.isRainingAt(neighbor)) return true;
        }
        return false;
    }

    private static void countBurn(ServerLevel level, BlockPos pos, Lineage lineage, BlockState fuel) {
        if (fuel.is(Blocks.GRASS_BLOCK)) lineage.grass++;
        else if (fuel.is(BlockTags.LOGS)) lineage.wood++;
        else if (fuel.is(BlockTags.LEAVES) || fuel.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock)
            lineage.vegetation++;
        else lineage.other++;
        data(level).setDirty();
        sendAsh(level, pos, lineage.burnedBlocks(), true);
    }

    /** Visual traffic has its own budget; exhausting it never changes burn accounting or spread. */
    private static void sendAsh(ServerLevel level, BlockPos pos, long burned, boolean burst) {
        FireData data = data(level);
        long now = level.getGameTime();
        if (data.ashTick != now) { data.ashTick = now; data.ashBursts = data.ashAmbient = 0; }
        if (burst ? data.ashBursts >= 16 : data.ashAmbient >= 8) return;
        if (burst) data.ashBursts++; else data.ashAmbient++;
        var payload = new AshPayload(level.dimension().identifier(), pos.immutable(), burned, burst);
        for (var player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(level, pos)) {
            if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 80 * 80
                    && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, AshPayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }

    public record AshPayload(net.minecraft.resources.Identifier dimension, BlockPos pos, long burned, boolean burst)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<AshPayload> TYPE = new Type<>(Naturality.id("fire_ash"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, AshPayload> CODEC =
            net.minecraft.network.codec.StreamCodec.composite(
                net.minecraft.resources.Identifier.STREAM_CODEC, AshPayload::dimension,
                BlockPos.STREAM_CODEC, AshPayload::pos,
                net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, AshPayload::burned,
                net.minecraft.network.codec.ByteBufCodecs.BOOL, AshPayload::burst, AshPayload::new);
        @Override public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() { return TYPE; }
    }

    private static void burnGrassBelow(ServerLevel level, BlockPos pos, Fire fire) {
        BlockPos ground = pos.below();
        if (!LoadedChunks.has(level, ground)) return;
        BlockState fuel = level.getBlockState(ground);
        if (!fuel.is(Blocks.GRASS_BLOCK) || distanceFactor(fire.lineage.origin, ground) == 0 || nearRain(level, pos)) return;
        if (level.setBlockAndUpdate(ground, Feature.CONTENT.enabled()
                ? naturality.blocks.charred.CharredBlocks.GRASS.withPropertiesOf(fuel) : Blocks.DIRT.defaultBlockState())) {
            countBurn(level, ground, fire.lineage, fuel);
        }
    }

    private static boolean grassSpread(ServerLevel level, BlockPos pos, Fire fire, RandomSource random) {
        FireData data = data(level);
        if (fire.attempts == 0 || data.attemptsThisTick >= WORLD_ATTEMPTS_PER_TICK
                || data.ignitionsThisTick >= WORLD_IGNITIONS_PER_TICK) return false;
        fire.attempts--;
        data.attemptsThisTick++;
        data.setDirty();
        double chance = -1;
        boolean spread = false;
        // One bounded pulse per scheduled fire tick; four fronts are independent.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (data.ignitionsThisTick >= WORLD_IGNITIONS_PER_TICK) break;
            BlockPos side = pos.relative(direction);
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos target = side.offset(0, dy, 0);
                if (!LoadedChunks.has(level, target) || !LoadedChunks.has(level, target.below())
                        || !level.getWorldBorder().isWithinBounds(target) || level.isOutsideBuildHeight(target)
                        || !level.getBlockState(target.below()).is(Blocks.GRASS_BLOCK)
                        || !level.canSpreadFireAround(target)) continue;
                var covering = level.getBlockState(target);
                boolean covered = covering(level, target, covering);
                if (!covering.isAir() && !covered) continue;
                // A step cannot pass diagonally through an intervening solid block.
                BlockPos clearance = dy > 0 ? pos.above() : side;
                if (dy != 0 && !level.getBlockState(clearance).isAir()
                        && !covering(level, clearance, level.getBlockState(clearance))) continue;
                double falloff = distanceFactor(fire.lineage.origin, target);
                if (falloff == 0 || nearRain(level, target)) continue;
                if (chance < 0) {
                    if (nearRain(level, pos) || !level.getBlockState(pos).canSurvive(level, pos)) return false;
                    // Most fires lie behind the advancing edge: no grass candidate means no group search.
                    chance = Math.min(0.85, 0.12 + (nearbyFireCount(level, pos, data) - 1) * 0.055);
                }
                if (random.nextDouble() >= chance * falloff) continue;
                if (covered) {
                    if (igniteCovering(level, target, new Context(level, fire, pos))) spread = true;
                    break;
                }
                boolean placed = withBirth(level, target, fire.lineage, 0,
                        () -> level.setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState()));
                Fire child = data.find(target);
                if (placed && child != null) {
                    data.ignitionsThisTick++;
                    burnGrassBelow(level, target, child);
                    spread = true;
                }
                break;
            }
        }
        return spread;
    }

    /** Local face-connected concentration, capped at 16 fires and a five-block-wide neighborhood. */
    private static int nearbyFireCount(ServerLevel level, BlockPos origin, FireData data) {
        // 5x5x5 cells fit in two words. Packed BlockPos hash sets collided heavily in flat fires.
        // This read-only search stays on the owning server thread and cannot reenter through block writes.
        int[] pending = data.neighborhoodQueue;
        BlockPos.MutableBlockPos neighbor = data.neighborhoodPosition;
        pending[0] = 62;
        long low = 1L << 62, high = 0;
        int count = 0, end = 1;
        while (count < end && count < 16) {
            int index = pending[count++];
            int x = index % 5, y = index / 5 % 5, z = index / 25;
            for (Direction direction : NEIGHBORS) {
                int nx = x + direction.getStepX(), ny = y + direction.getStepY(), nz = z + direction.getStepZ();
                if (nx < 0 || nx >= 5 || ny < 0 || ny >= 5 || nz < 0 || nz >= 5) continue;
                int next = nx + ny * 5 + nz * 25;
                long bit = 1L << (next & 63);
                if (next < 64) {
                    if ((low & bit) != 0) continue;
                    low |= bit;
                } else {
                    if ((high & bit) != 0) continue;
                    high |= bit;
                }
                neighbor.set(origin.getX() + nx - 2, origin.getY() + ny - 2, origin.getZ() + nz - 2);
                var chunk = level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
                if (chunk != null && chunk.getBlockState(neighbor).is(Blocks.FIRE)) pending[end++] = next;
            }
        }
        return count;
    }

    private record Context(ServerLevel level, Fire fire, BlockPos pos) { }
    private record Birth(ServerLevel level, BlockPos pos, Lineage lineage, int ticks) { }

    public static final class Lineage {
        public final long id;
        public final BlockPos origin;
        private long wood, vegetation, grass, other;
        private int references;
        private long nextAsh;
        private Lineage(long id, BlockPos origin) { this.id = id; this.origin = origin.immutable(); }
        public long burnedBlocks() { return wood + vegetation + grass + other; }
    }

    public static final class Fire {
        public final Lineage lineage;
        private int extraTicks;
        private long deadline = -1;
        private int attempts = GRASS_ATTEMPTS;
        private Fire(Lineage lineage, int extraTicks) { this.lineage = lineage; this.extraTicks = extraTicks; }
        public int extraTicks() { return extraTicks; }
        public int attemptsRemaining() { return attempts; }
    }

    private static final class Bucket {
        private long clock;
        private final Map<Long, Fire> fires = new HashMap<>();
    }

    /** Per-dimension storage. Chunk clocks advance only while block ticking; unloaded fire never catches up. */
    public static final class FireData extends SavedData {
        public static final SavedDataType<FireData> TYPE = new SavedDataType<>(Naturality.id("fire_propagation"),
                FireData::new, Snapshot.CODEC.xmap(FireData::new, FireData::snapshot), DataFixTypes.LEVEL);
        private long nextId = 1;
        private final Map<Long, Lineage> lineages = new HashMap<>();
        private final Map<Long, Bucket> buckets = new HashMap<>();
        private final Set<Long> activeChunks = new HashSet<>();
        private int attemptsThisTick;
        private int ignitionsThisTick;
        private long ashTick = Long.MIN_VALUE;
        private int ashBursts, ashAmbient;
        private final int[] neighborhoodQueue = new int[125];
        private final BlockPos.MutableBlockPos neighborhoodPosition = new BlockPos.MutableBlockPos();

        private FireData() { }
        private FireData(Snapshot snapshot) {
            nextId = snapshot.nextId;
            for (LineageRow row : snapshot.lineages) {
                Lineage lineage = new Lineage(row.id, BlockPos.of(row.origin));
                lineage.wood = row.wood; lineage.vegetation = row.vegetation;
                lineage.grass = row.grass; lineage.other = row.other;
                lineages.put(row.id, lineage);
                nextId = Math.max(nextId, row.id + 1);
            }
            for (ChunkRow row : snapshot.chunks) {
                Bucket bucket = new Bucket();
                bucket.clock = row.clock;
                for (FireRow entry : row.fires) {
                    Lineage lineage = lineages.get(entry.lineage);
                    if (lineage == null || ChunkPos.pack(BlockPos.of(entry.pos)) != row.pos) continue;
                    Fire fire = new Fire(lineage, Math.max(0, entry.extraTicks));
                    fire.deadline = entry.deadline;
                    fire.attempts = Math.clamp(entry.attempts, 0, GRASS_ATTEMPTS);
                    if (bucket.fires.putIfAbsent(entry.pos, fire) == null) lineage.references++;
                }
                if (!bucket.fires.isEmpty()) buckets.put(row.pos, bucket);
            }
            lineages.values().removeIf(lineage -> lineage.references == 0);
        }

        public @Nullable Fire find(BlockPos pos) {
            Bucket bucket = buckets.get(ChunkPos.pack(pos));
            return bucket == null ? null : bucket.fires.get(pos.asLong());
        }

        private Lineage newLineage(BlockPos pos) { return new Lineage(nextId++, pos); }

        private Fire add(BlockPos pos, Lineage lineage, int ticks) {
            remove(pos);
            long key = ChunkPos.pack(pos);
            Bucket bucket = buckets.computeIfAbsent(key, _ -> new Bucket());
            Fire fire = new Fire(lineage, ticks);
            bucket.fires.put(pos.asLong(), fire);
            lineage.references++;
            lineages.put(lineage.id, lineage);
            activeChunks.add(key);
            setDirty();
            return fire;
        }

        private void remove(BlockPos pos) {
            long key = ChunkPos.pack(pos);
            Bucket bucket = buckets.get(key);
            if (bucket == null) return;
            Fire fire = bucket.fires.remove(pos.asLong());
            if (fire == null) return;
            if (--fire.lineage.references == 0) lineages.remove(fire.lineage.id);
            if (bucket.fires.isEmpty()) { buckets.remove(key); activeChunks.remove(key); }
            setDirty();
        }

        private void loaded(LevelChunk chunk) {
            long key = chunk.getPos().pack();
            Bucket bucket = buckets.get(key);
            if (bucket == null) return;
            // Only inspect the supplied chunk: its FULL future need not have completed yet.
            for (long packed : List.copyOf(bucket.fires.keySet())) {
                BlockPos pos = BlockPos.of(packed);
                if (!chunk.getBlockState(pos).is(Blocks.FIRE)) remove(pos);
            }
            if (buckets.containsKey(key)) activeChunks.add(key);
        }

        private void advance(ServerLevel level) {
            attemptsThisTick = 0;
            ignitionsThisTick = 0;
            for (long key : activeChunks) {
                Bucket bucket = buckets.get(key);
                if (bucket != null && level.getChunkSource().isPositionTicking(key)) {
                    bucket.clock++;
                    setDirty();
                }
            }
        }

        private Snapshot snapshot() {
            List<LineageRow> roots = new ArrayList<>();
            for (Lineage lineage : lineages.values()) roots.add(new LineageRow(lineage.id, lineage.origin.asLong(),
                    lineage.wood, lineage.vegetation, lineage.grass, lineage.other));
            List<ChunkRow> chunks = new ArrayList<>();
            buckets.forEach((pos, bucket) -> {
                List<FireRow> fires = new ArrayList<>();
                bucket.fires.forEach((location, fire) -> fires.add(new FireRow(location, fire.lineage.id,
                        fire.extraTicks, fire.deadline, fire.attempts)));
                chunks.add(new ChunkRow(pos, bucket.clock, List.copyOf(fires)));
            });
            return new Snapshot(1, nextId, List.copyOf(roots), List.copyOf(chunks));
        }
    }

    private record FireRow(long pos, long lineage, int extraTicks, long deadline, int attempts) {
        private static final Codec<FireRow> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(FireRow::pos), Codec.LONG.fieldOf("lineage").forGetter(FireRow::lineage),
                Codec.INT.fieldOf("extra_ticks").forGetter(FireRow::extraTicks), Codec.LONG.fieldOf("deadline").forGetter(FireRow::deadline),
                Codec.INT.fieldOf("attempts").forGetter(FireRow::attempts)).apply(i, FireRow::new));
    }
    private record ChunkRow(long pos, long clock, List<FireRow> fires) {
        private static final Codec<ChunkRow> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(ChunkRow::pos), Codec.LONG.fieldOf("clock").forGetter(ChunkRow::clock),
                FireRow.CODEC.listOf().fieldOf("fires").forGetter(ChunkRow::fires)).apply(i, ChunkRow::new));
    }
    private record LineageRow(long id, long origin, long wood, long vegetation, long grass, long other) {
        private static final Codec<LineageRow> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(LineageRow::id), Codec.LONG.fieldOf("origin").forGetter(LineageRow::origin),
                Codec.LONG.fieldOf("wood").forGetter(LineageRow::wood), Codec.LONG.fieldOf("vegetation").forGetter(LineageRow::vegetation),
                Codec.LONG.fieldOf("grass").forGetter(LineageRow::grass), Codec.LONG.fieldOf("other").forGetter(LineageRow::other)
                ).apply(i, LineageRow::new));
    }
    private record Snapshot(int version, long nextId, List<LineageRow> lineages, List<ChunkRow> chunks) {
        private static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1).fieldOf("version").forGetter(Snapshot::version), Codec.LONG.fieldOf("next_id").forGetter(Snapshot::nextId),
                LineageRow.CODEC.listOf().fieldOf("lineages").forGetter(Snapshot::lineages),
                ChunkRow.CODEC.listOf().fieldOf("chunks").forGetter(Snapshot::chunks)).apply(i, Snapshot::new));
    }
}
