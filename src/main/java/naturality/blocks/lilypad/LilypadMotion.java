package naturality.blocks.lilypad;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import naturality.api.worlds.LoadedChunks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Shared contact ownership and time-based movement for regular and four-part pads. */
@org.jspecify.annotations.NullMarked
public final class LilypadMotion {
    public static final int EVENT = 17341;
    private static final double DIP = 0.125;
    private static final Map<Level, Map<BlockPos, Motion>> WORLDS = new WeakHashMap<>();
    public static java.util.function.@Nullable BiConsumer<Level, BlockPos> onTransition;
    private record Motion(boolean pressed, long start, double from, long lastContact) {
        double offset(double time) {
            double t = Math.clamp((time - start) / (pressed ? 5 : 20), 0, 1);
            if (pressed) return from + (-DIP - from) * (t * t * (3 - 2 * t));
            if (t >= 1) return 0;
            if (t <= 0) return from;
            double elastic = Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
            return from * (1 - elastic);
        }
    }
    private LilypadMotion() {}
    public static void initialize() { ServerTickEvents.END_SERVER_TICK.register(server -> { for (var level : server.getAllLevels()) tick(level); }); }
    private static synchronized Map<BlockPos, Motion> motions(Level level) {
        return WORLDS.computeIfAbsent(level, _ -> new ConcurrentHashMap<>());
    }
    public static BlockPos anchor(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof GiantLilypadBlock) {
            int part = state.getValue(GiantLilypadBlock.PART);
            return pos.offset(-(part & 1), 0, -(part >> 1));
        }
        return pos.immutable();
    }
    public static double offset(Level level, BlockPos pos, BlockState state, double partial) {
        var motion = motions(level).get(anchor(pos, state));
        return motion == null ? 0 : motion.offset(level.getGameTime() + partial);
    }
    public static void step(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server && state.getBlock() instanceof LilyPadBlock
                && entity.onGround() && !entity.isSpectator()) {
            BlockPos anchor = anchor(pos, state);
            var current = motions(level).get(anchor);
            if (current == null || !current.pressed) transition(server, anchor, true);
        }
    }
    private static void transition(ServerLevel level, BlockPos pos, boolean pressed) {
        receive(level, pos, pressed);
        level.blockEvent(pos, level.getBlockState(pos).getBlock(), EVENT, pressed ? 1 : 0);
    }
    public static void receive(Level level, BlockPos pos, boolean pressed) {
        var map = motions(level);
        var old = map.get(pos);
        if (old != null && old.pressed == pressed) {
            if (pressed) map.put(pos.immutable(), new Motion(true, old.start, old.from, level.getGameTime()));
            return;
        }
        double from = old == null ? 0 : old.offset(level.getGameTime());
        map.put(pos.immutable(), new Motion(pressed, level.getGameTime(), from, level.getGameTime()));
        var listener = onTransition;
        if (level.isClientSide() && listener != null) listener.accept(level, pos);
    }
    /** Predict loaded entity contacts locally, also repairing missed events after chunk tracking changes. */
    public static void clientContacts(Level level, Iterable<? extends Entity> entities) {
        var occupied = new java.util.HashSet<BlockPos>();
        for (var entity : entities) {
            if (entity == null || entity.isSpectator() || entity.getDeltaMovement().y > .08) continue;
            var box = entity.getBoundingBox();
            if (box.getXsize() > 16 || box.getZsize() > 16) continue;
            int y = (int)Math.floor(box.minY);
            for (int x = (int)Math.floor(box.minX); x <= (int)Math.floor(box.maxX-1e-6); x++)
                for (int z = (int)Math.floor(box.minZ); z <= (int)Math.floor(box.maxZ-1e-6); z++) {
                    var pos = new BlockPos(x,y,z);
                    if (!LoadedChunks.has(level,pos)) continue;
                    var state = level.getBlockState(pos);
                    if (state.getBlock() instanceof LilyPadBlock
                            && nearSurface(entity, y+0.5/16))
                        occupied.add(anchor(pos,state));
                }
        }
        var map = motions(level);
        for (var pos : occupied) receive(level,pos,true);
        for (var entry : map.entrySet()) {
            var pos = entry.getKey(); var motion = entry.getValue();
            if (!LoadedChunks.has(level,pos) || !(level.getBlockState(pos).getBlock() instanceof LilyPadBlock)
                    || !motion.pressed && level.getGameTime()-motion.start >= 20) map.remove(pos);
            else if (motion.pressed && !occupied.contains(pos) && level.getGameTime()-motion.lastContact >= 3) receive(level,pos,false);
        }
    }
    // Contact always follows the fixed collision surface, independently of visual animation.
    private static boolean nearSurface(Entity entity, double top) {
        double feet = entity.getBoundingBox().minY;
        return entity.getDeltaMovement().y <= .08 && feet >= top-.025 && feet <= top+.08;
    }

    private static void tick(ServerLevel level) {
        var map = motions(level);
        for (var entry : map.entrySet()) {
            BlockPos pos = entry.getKey();
            Motion motion = entry.getValue();
            if (!LoadedChunks.has(level, pos) || !(level.getBlockState(pos).getBlock() instanceof LilyPadBlock)) {
                map.remove(pos); continue;
            }
            if (!motion.pressed) {
                if (level.getGameTime() - motion.start >= 20) map.remove(pos);
                continue;
            }
            int width = level.getBlockState(pos).getBlock() instanceof GiantLilypadBlock ? 2 : 1;
            double top = pos.getY() + 0.5 / 16;
            var area = new AABB(pos.getX(), top - 0.04, pos.getZ(), pos.getX() + width, top + 0.08, pos.getZ() + width);
            boolean occupied = !level.getEntities((Entity)null, area, e -> !e.isSpectator() && nearSurface(e,top)).isEmpty();
            if (occupied) receive(level,pos,true);
            else if (level.getGameTime()-motion.lastContact >= 3) transition(level, pos, false);
        }
    }
}
