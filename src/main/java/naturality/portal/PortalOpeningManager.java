package naturality.portal;

import java.util.*;
import java.util.function.BiPredicate;
import naturality.NaturalitySounds;
import naturality.config.NaturalityServerConfig;
import naturality.mixin.PortalShapeAccessor;
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

/** Server-owned, short-lived opening transactions. Never persists a locked portal in a save. */
public final class PortalOpeningManager {
    private static final Map<ServerLevel, List<Opening>> ACTIVE = new IdentityHashMap<>();
    public static BiPredicate<Level, BlockPos> clientOpening = (_, _) -> false;

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(PortalCrossingPayload.TYPE, PortalCrossingPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register(PortalCrossing::syncToObserver);
        PayloadTypeRegistry.clientboundPlay().register(PortalOpeningPayload.TYPE, PortalOpeningPayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(PortalOpeningManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> ACTIVE.clear());
        ServerLifecycleEvents.SERVER_STARTING.register(_ -> NaturalityServerConfig.get());
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
                if (!naturality.util.LoadedChunks.has(level, p)) return false;
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
}
