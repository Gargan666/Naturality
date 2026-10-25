package naturality.portals.nether.opening;

import naturality.portals.nether.travel.PortalCrossing;
import naturality.portals.nether.travel.PortalCrossing.PortalCrossingPayload;
import java.util.*;
import java.util.function.BiPredicate;
import naturality.bootstrap.ContentRegistration.NaturalitySounds;
import naturality.config.gameplay.GameplaySettings.NaturalityServerConfig;
import naturality.portals.nether.opening.mixins.OpeningMixins.PortalShapeAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.PortalShape;
import java.util.ArrayList;
import java.util.List;
import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-owned, short-lived opening transactions. Never persists a locked portal in a save. */
@org.jspecify.annotations.NullMarked
public final class PortalOpeningManager {
    private static final Map<ServerLevel, List<Opening>> ACTIVE = Collections.synchronizedMap(new IdentityHashMap<>());
    public static BiPredicate<Level, BlockPos> clientOpening = (_, _) -> false;

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(PortalCrossingPayload.TYPE, PortalCrossingPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(PortalCrossing::syncToObserver);
        PayloadTypeRegistry.clientboundPlay().register(PortalOpeningPayload.TYPE, PortalOpeningPayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(PortalOpeningManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(PortalOpeningManager::clearServer);
        ServerLifecycleEvents.SERVER_STARTING.register(_ -> NaturalityServerConfig.get());
    }

    private static void clearServer(net.minecraft.server.MinecraftServer server) {
        for (var level : server.getAllLevels()) ACTIVE.remove(level);
    }

    public static boolean begin(ServerLevel level, PortalShape shape) {
        if (!NaturalityServerConfig.get().portalIntroAnimation) return false;
        PortalShapeAccessor access = (PortalShapeAccessor) shape;
        BlockPos corner = access.naturality$bottomLeft();
        BlockPos other = corner.above(access.naturality$height() - 1)
            .relative(access.naturality$rightDir(), access.naturality$width() - 1);
        BlockPos min = new BlockPos(Math.min(corner.getX(), other.getX()), corner.getY(), Math.min(corner.getZ(), other.getZ()));
        BlockPos max = new BlockPos(Math.max(corner.getX(), other.getX()), other.getY(), Math.max(corner.getZ(), other.getZ()));
        List<Opening> openings = ACTIVE.computeIfAbsent(level, _ -> new ArrayList<>());
        if (openings.stream().anyMatch(o -> o.contains(corner))) return true;
        List<BlockPos> fires = new ArrayList<>();
        BlockPos.betweenClosed(min, max).forEach(pos -> {
            if (level.getBlockState(pos).is(BlockTags.FIRE)) fires.add(pos.immutable());
        });
        if (fires.isEmpty()) return false;
        Opening opening = new Opening(min, max, access.naturality$axis(), List.copyOf(fires), level.getGameTime());
        openings.add(opening);
        opening.notifyNewViewers(level);
        return true;
    }

    public static boolean isOpening(Level level, BlockPos pos) {
        if (level.isClientSide()) return clientOpening.test(level, pos);
        return ACTIVE.getOrDefault(level, List.of()).stream()
            .anyMatch(o -> o.contains(pos) && !PortalOpeningTiming.ready(level.getGameTime() - o.start));
    }

    public static boolean fadingFire(Level level, BlockPos pos) {
        if (level.isClientSide()) return false;
        return ACTIVE.getOrDefault(level, List.of()).stream()
            .anyMatch(o -> !o.placed && o.fires.contains(pos));
    }

    private static void tick(ServerLevel level) {
        List<Opening> openings = ACTIVE.get(level);
        if (openings == null) return;
        Iterator<Opening> iterator = openings.iterator();
        while (iterator.hasNext()) {
            Opening o = iterator.next();
            long age = level.getGameTime() - o.start;
            if (!o.valid(level)) {
                o.sendCancel(level);
                iterator.remove();
                continue;
            }
            o.notifyNewViewers(level);
            if (!o.placed && age >= PortalOpeningTiming.FIRE_TICKS) {
                // Revalidate before replacing air/fire; never overwrite a player's newly placed block.
                var state = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, o.axis);
                o.placed = true;
                BlockPos.betweenClosed(o.min, o.max).forEach(pos -> level.setBlock(pos, state, 18));
            }
            if (!o.playedOpeningSound && age >= PortalOpeningTiming.READY_TICK) {
                o.playedOpeningSound = true;
                // A normal positional sound, once per opening, at the same transition as the visual pulse.
                level.playSound(null, (o.min.getX() + o.max.getX() + 1) / 2.0,
                    (o.min.getY() + o.max.getY() + 1) / 2.0,
                    (o.min.getZ() + o.max.getZ() + 1) / 2.0,
                    NaturalitySounds.PORTAL_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            if (age >= PortalOpeningTiming.END_TICK) iterator.remove();
        }
        if (openings.isEmpty()) ACTIVE.remove(level);
    }

    private static final class Opening {
        final BlockPos min, max;
        final Direction.Axis axis;
        final List<BlockPos> fires;
        final long start;
        final Set<UUID> notified = new HashSet<>();
        boolean placed;
        boolean playedOpeningSound;
        Opening(BlockPos min, BlockPos max, Direction.Axis axis, List<BlockPos> fires, long start) {
            this.min = min; this.max = max; this.axis = axis; this.fires = fires; this.start = start;
        }
        boolean contains(BlockPos p) {
            return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY()
                && p.getY() <= max.getY() && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
        }
        boolean valid(ServerLevel level) {
            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                if (!naturality.api.worlds.LoadedChunks.has(level, p)) return false;
                var state = level.getBlockState(p);
                if (placed ? !state.is(Blocks.NETHER_PORTAL) || state.getValue(NetherPortalBlock.AXIS) != axis
                    : !state.isAir() && !state.is(BlockTags.FIRE)) return false;
            }
            if (!placed && fires.stream().noneMatch(p -> level.getBlockState(p).is(BlockTags.FIRE))) return false;
            PortalShape shape = PortalShape.findAnyShape(level, min, axis);
            PortalShapeAccessor access = (PortalShapeAccessor) shape;
            int width = axis == Direction.Axis.X ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1;
            return shape.isValid() && access.naturality$width() == width && access.naturality$height() == max.getY() - min.getY() + 1;
        }
        PortalOpeningPayload payload(ServerLevel level, int age) {
            return new PortalOpeningPayload(level.dimension().identifier().toString(), min, max, axis, fires, age);
        }
        void notifyNewViewers(ServerLevel level) {
            for (var player : level.players()) {
                if (min.distToCenterSqr(player.position()) < 128 * 128
                    && ServerPlayNetworking.canSend(player, PortalOpeningPayload.TYPE) && notified.add(player.getUUID())) {
                    ServerPlayNetworking.send(player, payload(level, (int) (level.getGameTime() - start)));
                }
            }
        }
        void sendCancel(ServerLevel level) {
            for (var player : level.players()) {
                if (notified.contains(player.getUUID()) && ServerPlayNetworking.canSend(player, PortalOpeningPayload.TYPE))
                    ServerPlayNetworking.send(player, payload(level, -1));
            }
        }
    }

    /** Durations in game ticks; shared by the server lock and client animation. */
    public static final class PortalOpeningTiming {
        public static final int FIRE_TICKS = 10;
        public static final int REVEAL_TICKS = 20;
        public static final int FLASH_TICKS = 40;
        public static final int READY_TICK = FIRE_TICKS + REVEAL_TICKS;
        public static final int END_TICK = READY_TICK + FLASH_TICKS;

        private PortalOpeningTiming() { }
        public static float clamp(float x) { return Math.max(0, Math.min(1, x)); }
        public static float expoIn(float t) {
            t = clamp(t);
            return t == 0 ? 0 : t == 1 ? 1 : (float) Math.pow(2, 10 * (t - 1));
        }
        public static float expoOut(float t) {
            t = clamp(t);
            return t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
        }
        public static float reveal(float age) { return clamp((age - FIRE_TICKS) / REVEAL_TICKS); }
        public static float fireAlpha(float age) { return 1 - clamp(age / FIRE_TICKS); }
        public static boolean ready(float age) { return age >= READY_TICK; }
        public static float pulse(float age) {
            return !ready(age) ? 0 : 1 - expoOut((age - READY_TICK) / FLASH_TICKS);
        }
        public static float pixelAlpha(float progress, float luminance) {
            // Luminance is normalized to the active texture's darkest/lightest texels.
            progress = clamp(progress);
            float brightness = clamp(luminance);
            if (brightness <= 0.5F) {
                float blend = brightness * 2;
                return expoIn(progress) * (1 - blend) + progress * blend;
            }
            float blend = (brightness - 0.5F) * 2;
            return progress * (1 - blend) + expoOut(progress) * blend;
        }
    }

    public record PortalOpeningPayload(String dimension, BlockPos min, BlockPos max,
            Direction.Axis axis, List<BlockPos> fires, int elapsed) implements CustomPacketPayload {
        public static final Type<PortalOpeningPayload> TYPE = new Type<>(Naturality.id("portal_opening"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PortalOpeningPayload> CODEC = new StreamCodec<@org.jspecify.annotations.NonNull RegistryFriendlyByteBuf, @org.jspecify.annotations.NonNull PortalOpeningPayload>() {
            @Override public PortalOpeningPayload decode(RegistryFriendlyByteBuf buf) {
                String dimension = buf.readUtf(256);
                BlockPos min = buf.readBlockPos(), max = buf.readBlockPos();
                Direction.Axis axis = buf.readBoolean() ? Direction.Axis.X : Direction.Axis.Z;
                int elapsed = buf.readInt(), count = buf.readVarInt();
                if (count < 0 || count > 441 || max.getY() - min.getY() < 0 || max.getY() - min.getY() > 20
                    || max.getX() - min.getX() < 0 || max.getX() - min.getX() > 20
                    || max.getZ() - min.getZ() < 0 || max.getZ() - min.getZ() > 20
                    || (axis == Direction.Axis.X ? min.getZ() != max.getZ() : min.getX() != max.getX())) {
                    throw new IllegalArgumentException("Invalid portal opening bounds");
                }
                List<BlockPos> fires = new ArrayList<>();
                for (int i = 0; i < count; i++) fires.add(buf.readBlockPos());
                return new PortalOpeningPayload(dimension, min, max, axis, List.copyOf(fires), elapsed);
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, PortalOpeningPayload p) {
                buf.writeUtf(p.dimension(), 256);
                buf.writeBlockPos(p.min()); buf.writeBlockPos(p.max());
                buf.writeBoolean(p.axis() == Direction.Axis.X);
                buf.writeInt(p.elapsed()); buf.writeVarInt(p.fires().size());
                p.fires().forEach(buf::writeBlockPos);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
