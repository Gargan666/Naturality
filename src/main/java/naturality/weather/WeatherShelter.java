package naturality.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WeatherShelter {
    private static final int[] DISTANCES = {2, 4, 8};
    private record SurfacePlayers(long tick, boolean present) {}
    private static final java.util.WeakHashMap<net.minecraft.server.level.ServerLevel, SurfacePlayers> players = new java.util.WeakHashMap<>();
    private WeatherShelter() {}

    public static boolean hasSurfacePlayer(net.minecraft.server.level.ServerLevel level) {
        var cached = players.get(level);
        if (cached != null && cached.tick == level.getGameTime()) return cached.present;
        boolean present = level.players().stream().anyMatch(p -> !underground(level, p.getEyePosition(), p));
        players.put(level, new SurfacePlayers(level.getGameTime(), present));
        return present;
    }

    public static boolean underground(Level level, Vec3 eye, Entity entity) {
        BlockPos pos = BlockPos.containing(eye);
        if (!naturality.util.LoadedChunks.has(level, pos)) return true;
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - eye.y <= 8
                || level.canSeeSky(pos)) return false;
        // A nearby, unobstructed opening still counts as being at the surface.
        for (int distance : DISTANCES) for (int direction = 0; direction < 8; direction++) {
            double angle = direction * Math.PI / 4;
            Vec3 sample = eye.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            BlockPos target = BlockPos.containing(sample);
            if (naturality.util.LoadedChunks.has(level, target) && level.canSeeSky(target)
                    && level.clip(new ClipContext(eye, sample, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) return false;
        }
        return true;
    }
}
