package naturality.systems.fishing;

import naturality.Naturality;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import naturality.systems.villagerwork.professions.ProfessionWork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import naturality.api.worlds.LoadedChunks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VillagerBobbers {
    public static final EntityType<VillagerBobber> TYPE;
    public static final EntityType<PlayerFishingReturn> PLAYER_RETURN;
    static {
        var id = Naturality.id("villager_bobber");
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
            EntityType.Builder.<VillagerBobber>of(VillagerBobber::new, MobCategory.MISC)
                .sized(0.25F, 0.25F).clientTrackingRange(10).updateInterval(1).noSave()
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
        var playerId = Naturality.id("player_fishing_return");
        PLAYER_RETURN = Registry.register(BuiltInRegistries.ENTITY_TYPE, playerId,
            EntityType.Builder.<PlayerFishingReturn>of(PlayerFishingReturn::new, MobCategory.MISC)
                .sized(0.25F, 0.25F).clientTrackingRange(10).updateInterval(1).noSave()
                .build(ResourceKey.create(Registries.ENTITY_TYPE, playerId)));
    }
    private VillagerBobbers() { }
    public static void initialize() { }

    /** Server-controlled hook with vanilla-sized float and an independent entity renderer. */
    public static final class VillagerBobber extends Entity {
        public static final int FLYING = 0, BOBBING = 1, BITING = 2, RETRIEVING = 3;
        private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<ItemStack> CATCH = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.ITEM_STACK);
        private BlockPos target = BlockPos.ZERO;
        private Vec3 start = Vec3.ZERO;
        private Vec3 retrievalStart = Vec3.ZERO;
        private int phaseTicks;

        public VillagerBobber(EntityType<VillagerBobber> type, Level level) { super(type, level); }
        public VillagerBobber(ServerLevel level, Villager owner, BlockPos target) {
            this(VillagerBobbers.TYPE, level);
            this.target = target.immutable();
            this.start = owner.getEyePosition().add(owner.getLookAngle().scale(0.35)).add(0, -0.4, 0);
            setPos(start.x, start.y, start.z);
            // Choose a launch direction and pitch, never solve a trajectory to a destination.
            Vec3 waterDirection = Vec3.atCenterOf(target).subtract(start);
            double yaw = waterFacingYaw(level, owner, target, waterDirection);
            double pitch = Math.toRadians(15 + owner.getRandom().nextDouble() * 35);
            double speed = .45 + owner.getRandom().nextDouble() * .2;
            setDeltaMovement(Math.cos(yaw) * Math.cos(pitch) * speed,
                Math.sin(pitch) * speed, Math.sin(yaw) * Math.cos(pitch) * speed);
            getEntityData().set(OWNER, owner.getId());
        }

        private double waterFacingYaw(ServerLevel level, Villager owner, BlockPos water, Vec3 direction) {
            double heading = Math.atan2(direction.z, direction.x);
            // Randomize within a forward cone and prefer directions crossing water.
            // This checks the direction only: flight still determines the landing point.
            for (int attempt = 0; attempt < 16; attempt++) {
                double yaw = heading + (owner.getRandom().nextDouble() - .5) * Math.toRadians(70);
                int wetSamples = 0;
                for (int distance = 2; distance <= 6; distance++) {
                    var sample = BlockPos.containing(start.x + Math.cos(yaw) * distance,
                        water.getY(), start.z + Math.sin(yaw) * distance);
                    if (naturality.api.worlds.LoadedChunks.has(level, sample) && ProfessionWork.restingWater(level, sample)) wetSamples++;
                }
                if (wetSamples >= 2) return yaw;
            }
            return heading;
        }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
            builder.define(OWNER, 0);
            builder.define(PHASE, FLYING);
            builder.define(CATCH, ItemStack.EMPTY);
        }
        @Override protected void readAdditionalSaveData(ValueInput input) { }
        @Override protected void addAdditionalSaveData(ValueOutput output) { }
        @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096.0; }
        @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }

        public @Nullable Villager owner() {
            Entity entity = level().getEntity(getEntityData().get(OWNER));
            return entity instanceof Villager villager ? villager : null;
        }
        public int phase() { return getEntityData().get(PHASE); }
        public ItemStack catchItem() { return getEntityData().get(CATCH); }
        public void bite(ItemStack item) {
            getEntityData().set(CATCH, item.copyWithCount(1));
            getEntityData().set(PHASE, BITING);
            phaseTicks = 0;
            setDeltaMovement(getDeltaMovement().x, -0.35, getDeltaMovement().z);
        }
        public void retrieve() {
            getEntityData().set(PHASE, RETRIEVING);
            retrievalStart = position();
            phaseTicks = 0;
        }

        @Override public void tick() {
            super.tick();
            if (level().isClientSide()) return;
            Villager owner = owner();
            if (owner == null || !owner.isAlive()) { discard(); return; }
            int phase = phase();
            phaseTicks++;
            if (phase == FLYING) {
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.92).add(0, -0.03, 0));
                var water = blockPosition();
                if (level().getFluidState(water).is(net.minecraft.tags.FluidTags.WATER)) {
                    target = water.immutable();
                    setDeltaMovement(Vec3.ZERO);
                    getEntityData().set(PHASE, BOBBING);
                    phaseTicks = 0;
                } else if (phaseTicks >= 80 || horizontalCollision || onGround()) {
                    discard();
                }
            } else if (phase == BOBBING || phase == BITING) {
                double surface = target.getY() + .87;
                Vec3 velocity = getDeltaMovement();
                double force = getY() + velocity.y - surface;
                if (Math.abs(force) < .01) force += Math.copySign(.1, force == 0 ? 1 : force);
                double vertical = velocity.y - force * random.nextFloat() * .2;
                setDeltaMovement(0, vertical * .9, 0);
                setPos(getX(), getY() + vertical, getZ());
            } else if (phase == RETRIEVING) {
                float t = Mth.clamp(phaseTicks / 5.0F, 0, 1);
                Vec3 destination = owner.getEyePosition().add(0, -0.4, 0);
                Vec3 next = retrievalStart.lerp(destination, t).add(0, Math.sin(t * Math.PI) * 0.4, 0);
                setPos(next.x, next.y, next.z);
                if (t >= 1) {
                    discard();
                }
            }
        }
    }

    /** Brief visual continuation of a player hook after vanilla calculates its catch. */
    public static final class PlayerFishingReturn extends Entity {
        private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PlayerFishingReturn.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<ItemStack> CATCH = SynchedEntityData.defineId(PlayerFishingReturn.class, EntityDataSerializers.ITEM_STACK);
        private static final EntityDataAccessor<Boolean> GLINT = SynchedEntityData.defineId(PlayerFishingReturn.class, EntityDataSerializers.BOOLEAN);
        private Vec3 start = Vec3.ZERO;
        private int travelTicks;

        public PlayerFishingReturn(EntityType<PlayerFishingReturn> type, Level level) { super(type, level); }

        public PlayerFishingReturn(ServerLevel level, Player owner, Vec3 hookPosition, ItemStack catchItem) {
            this(VillagerBobbers.PLAYER_RETURN, level);
            setPos(hookPosition.x, hookPosition.y, hookPosition.z);
            start = hookPosition;
            getEntityData().set(OWNER, owner.getId());
            getEntityData().set(CATCH, catchItem.copy());
            var rod = owner.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)
                ? owner.getMainHandItem() : owner.getOffhandItem();
            getEntityData().set(GLINT, rod.hasFoil());
        }

        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
            builder.define(OWNER, 0);
            builder.define(CATCH, ItemStack.EMPTY);
            builder.define(GLINT, false);
        }
        @Override protected void readAdditionalSaveData(ValueInput input) { }
        @Override protected void addAdditionalSaveData(ValueOutput output) { }
        @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096; }
        @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }

        public @Nullable Player owner() {
            Entity entity = level().getEntity(getEntityData().get(OWNER));
            return entity instanceof Player player ? player : null;
        }
        public ItemStack catchItem() { return getEntityData().get(CATCH); }
        public boolean rodGlint() { return getEntityData().get(GLINT); }

        @Override public void tick() {
            super.tick();
            if (level().isClientSide()) return;
            Player owner = owner();
            if (owner == null || !owner.isAlive()) {
                if (!catchItem().isEmpty())
                    level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), catchItem().copy()));
                discard();
                return;
            }
            float t = Mth.clamp(++travelTicks / 5.0F, 0, 1);
            Vec3 end = owner.getEyePosition().add(0, -0.4, 0);
            Vec3 next = start.lerp(end, t).add(0, Math.sin(t * Math.PI) * .25, 0);
            setPos(next.x, next.y, next.z);
            if (t >= 1) {
                ItemStack item = catchItem().copy();
                owner.getInventory().add(item);
                if (!item.isEmpty()) level().addFreshEntity(new ItemEntity(level(), owner.getX(), owner.getY() + .5, owner.getZ(), item));
                discard();
            }
        }
    }

    /** Shared, bounded, server-thread-only shoreline surveys. Never loads chunks. */
    public static final class FishingSpots {
        public record Spot(BlockPos water, BlockPos stand) { }
        private static final int RADIUS = 16, WIDTH = 33, CELLS = WIDTH * WIDTH * 8;
        private static final int CELLS_PER_TICK = 512, MAX_SURVEYS = 64;
        private static final Map<ServerLevel, Cache> LEVELS = Collections.synchronizedMap(new WeakHashMap<>());

        private FishingSpots() { }

        public static void initialize() {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                for (ServerLevel level : server.getAllLevels()) tick(level);
            });
            ServerChunkEvents.CHUNK_LOAD.register((level, chunk, _) -> chunkChanged(level, chunk.getPos()));
            ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> chunkChanged(level, chunk.getPos()));
            ServerLifecycleEvents.SERVER_STOPPED.register(FishingSpots::clearServer);
        }

        private static void clearServer(net.minecraft.server.MinecraftServer server) {
            for (var level : server.getAllLevels()) LEVELS.remove(level);
        }

        /** Nearby job sites share even unfinished/empty surveys; paths remain per villager. */
        public static List<Spot> nearby(ServerLevel level, BlockPos origin) {
            Cache cache = LEVELS.computeIfAbsent(level, _ -> new Cache());
            long now = level.getGameTime();
            Survey found = null;
            for (Survey survey : cache.surveys) {
                if (Math.abs(origin.getX() - survey.origin.getX()) <= 4
                        && Math.abs(origin.getZ() - survey.origin.getZ()) <= 4
                        && origin.getY() == survey.origin.getY()) { found = survey; break; }
            }
            if (found == null) {
                if (cache.surveys.size() >= MAX_SURVEYS) {
                    Survey oldest = Collections.min(cache.surveys, Comparator.comparingLong(s -> s.lastUsed));
                    cache.remove(oldest);
                }
                found = new Survey(origin.immutable());
                cache.surveys.add(found);
                for (int x = (origin.getX() - 18) >> 4; x <= (origin.getX() + 18) >> 4; x++)
                    for (int z = (origin.getZ() - 18) >> 4; z <= (origin.getZ() + 18) >> 4; z++)
                        cache.byChunk.computeIfAbsent(ChunkPos.pack(x, z), _ -> new ArrayList<>()).add(found);
            }
            found.lastUsed = now;
            found.requested = found.cursor != CELLS;
            return found.result;
        }

        public static boolean usable(ServerLevel level, Spot spot) {
            return standable(level, spot.stand) && ProfessionWork.restingWater(level, spot.water);
        }

        private static boolean standable(ServerLevel level, BlockPos stand) {
            if (!LoadedChunks.has(level, stand) || !level.getBlockState(stand).isAir()
                    || !level.getBlockState(stand.above()).isAir()) return false;
            BlockPos below = stand.below();
            return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
        }

        public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
            Cache cache = LEVELS.get(level);
            if (cache == null) return;
            List<Survey> surveys = cache.byChunk.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
            if (surveys == null) return;
            for (Survey survey : surveys) {
                if (Math.abs(pos.getX() - survey.origin.getX()) <= 18
                        && Math.abs(pos.getZ() - survey.origin.getZ()) <= 18
                        && pos.getY() >= survey.origin.getY() - 5 && pos.getY() <= survey.origin.getY() + 6
                        && (before.is(Blocks.WATER) || after.is(Blocks.WATER) || survey.nearWater(pos)))
                    survey.invalidate();
            }
        }

        private static void chunkChanged(ServerLevel level, ChunkPos pos) {
            Cache cache = LEVELS.get(level);
            if (cache == null) return;
            List<Survey> surveys = cache.byChunk.get(pos.pack());
            if (surveys != null) for (Survey survey : surveys) survey.invalidate();
        }

        private static void tick(ServerLevel level) {
            Cache cache = LEVELS.get(level);
            if (cache == null) return;
            // Round-robin prevents a group in one village from multiplying the budget.
            int remaining = CELLS_PER_TICK;
            for (int visited = 0; visited < cache.surveys.size() && remaining > 0; visited++) {
                cache.next = (cache.next + 1) % cache.surveys.size();
                Survey survey = cache.surveys.get(cache.next);
                if (level.getGameTime() - survey.lastUsed > 12000 || !survey.requested || survey.cursor == CELLS) continue;
                remaining -= survey.advance(level, remaining);
            }
        }

        private static final class Cache {
            final List<Survey> surveys = new ArrayList<>();
            final Map<Long, List<Survey>> byChunk = new HashMap<>();
            int next;
            void remove(Survey survey) {
                surveys.remove(survey);
                byChunk.values().removeIf(list -> { list.remove(survey); return list.isEmpty(); });
            }
        }

        private static final class Survey {
            final BlockPos origin;
            final Set<BlockPos> water = new HashSet<>(), sources = new HashSet<>();
            List<Spot> result = List.of();
            int cursor;
            long lastUsed;
            boolean requested;
            Survey(BlockPos origin) { this.origin = origin; }
            void invalidate() { cursor = 0; water.clear(); sources.clear(); result = List.of(); }

            boolean nearWater(BlockPos changed) {
                var probe = new BlockPos.MutableBlockPos();
                for (int y = -3; y <= 3; y++) for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
                    probe.set(changed.getX() + x, changed.getY() + y, changed.getZ() + z);
                    if (sources.contains(probe)) return true;
                }
                return false;
            }

            int advance(ServerLevel level, int budget) {
                int end = Math.min(CELLS, cursor + budget), used = end - cursor;
                var pos = new BlockPos.MutableBlockPos();
                while (cursor < end) {
                    int i = cursor++;
                    pos.set(origin.getX() + i % WIDTH - RADIUS, origin.getY() + i / (WIDTH * WIDTH) - 4,
                        origin.getZ() + i / WIDTH % WIDTH - RADIUS);
                    if (LoadedChunks.has(level, pos) && level.getBlockState(pos).is(Blocks.WATER)) {
                        sources.add(pos.immutable());
                        if (ProfessionWork.restingWater(level, pos)) water.add(pos.immutable());
                    }
                }
                if (cursor == CELLS) { result = finish(level); water.clear(); requested = false; }
                return used;
            }

            private List<Spot> finish(ServerLevel level) {
                List<Spot> candidates = new ArrayList<>();
                Set<BlockPos> unseen = new HashSet<>(water);
                var probe = new BlockPos.MutableBlockPos();
                while (!unseen.isEmpty()) {
                    Set<BlockPos> pool = new HashSet<>();
                    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                    BlockPos seed = unseen.iterator().next();
                    unseen.remove(seed);
                    queue.add(seed);
                    double sumX = 0, sumZ = 0;
                    while (!queue.isEmpty()) {
                        BlockPos current = queue.removeFirst();
                        pool.add(current); sumX += current.getX(); sumZ += current.getZ();
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos next = current.relative(dir);
                            if (unseen.remove(next)) queue.addLast(next);
                        }
                    }
                    double centerX = sumX / pool.size(), centerZ = sumZ / pool.size();
                    Map<BlockPos, Double> scores = new HashMap<>();
                    Set<BlockPos> stands = new HashSet<>();
                    for (BlockPos p : pool) {
                        int interior = 0;
                        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                            probe.set(p.getX() + dx, p.getY(), p.getZ() + dz);
                            if (water.contains(probe)) interior++;
                        }
                        double dx = p.getX() - centerX, dz = p.getZ() - centerZ;
                        scores.put(p, interior * 4.0 - (dx * dx + dz * dz) * .15);
                        for (Direction dir : Direction.Plane.HORIZONTAL) for (int dy = 0; dy <= 1; dy++) {
                            BlockPos stand = p.relative(dir).above(dy);
                            // Deduplicate before querying the world, and skip known water.
                            if (!water.contains(stand)) stands.add(stand);
                        }
                    }
                    for (BlockPos stand : stands) {
                        if (!standable(level, stand)) continue;
                        BlockPos cast = null;
                        double best = -Double.MAX_VALUE;
                        // Horizontal components have one Y. Only inspect the cast's ten-block radius.
                        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
                            int dy = stand.getY() - seed.getY(), distance = dx * dx + dy * dy + dz * dz;
                            if (distance > 100) continue;
                            probe.set(stand.getX() + dx, seed.getY(), stand.getZ() + dz);
                            Double base = scores.get(probe);
                            if (base != null && base - distance * .08 > best) {
                                best = base - distance * .08;
                                cast = probe.immutable();
                            }
                        }
                        if (cast != null) candidates.add(new Spot(cast, stand));
                    }
                }
                return List.copyOf(candidates);
            }
        }
    }
}
