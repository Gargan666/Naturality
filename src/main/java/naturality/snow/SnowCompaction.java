package naturality.snow;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-owned foot traffic, counted by distance and landings rather than standing time. */
public final class SnowCompaction {
    private record Travel(ServerLevel level, Vec3 position, boolean grounded, double distance) {}
    private record Pressure(int layers, int hits, long time) {}
    private static final Map<ServerPlayer, Travel> PLAYERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, Pressure>> PRESSURE = new WeakHashMap<>();
    private SnowCompaction() {}
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> { PLAYERS.clear(); PRESSURE.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!naturality.config.NaturalityServerConfig.get().snowCompaction) { PLAYERS.clear(); PRESSURE.clear(); return; }
            for (var player : server.getPlayerList().getPlayers()) tick(player);
        });
    }
    private static void tick(ServerPlayer player) {
        var level = player.level();
        var position = player.position();
        boolean grounded = player.onGround() && !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger();
        var old = PLAYERS.get(player);
        double distance = 0;
        int hits = 0;
        if (old != null && old.level == level && position.distanceToSqr(old.position) < 16) {
            if (grounded && !old.grounded) hits = 2;
            else if (grounded) {
                distance = old.distance + Math.hypot(position.x-old.position.x, position.z-old.position.z);
                if (distance >= .8) { hits=1; distance %= .8; }
            }
        }
        PLAYERS.put(player,new Travel(level,position,grounded,distance));
        if (hits > 0) {
            var snow = contact(level,player.getBoundingBox());
            if (snow != null) press(level,snow,hits);
        }
    }
    /** Find the snow owner for the actual fitted collision surface beneath the feet. */
    public static @org.jspecify.annotations.Nullable BlockPos contact(ServerLevel level, AABB feet) {
        int base = (int)Math.floor(feet.minY)-1;
        for (int x=(int)Math.floor(feet.minX); x<=Math.floor(feet.maxX); x++)
            for (int z=(int)Math.floor(feet.minZ); z<=Math.floor(feet.maxZ); z++)
                for (int offset=0; offset<=SnowGeometry.MAX_DEPTH+1; offset++) {
                    var pos = new BlockPos(x,base+offset,z);
                    if (!naturality.util.LoadedChunks.has(level, pos)) break;
                    var state = level.getBlockState(pos);
                    if (state.is(Blocks.SNOW)) {
                        int layers=state.getValue(SnowLayerBlock.LAYERS);
                        if (layers>1) for (var box : state.getCollisionShape(level,pos).toAabbs()) {
                            var world=box.move(pos);
                            if (Math.abs(world.maxY-feet.minY)<.08 && world.maxX>feet.minX && world.minX<feet.maxX
                                    && world.maxZ>feet.minZ && world.minZ<feet.maxZ) return pos;
                        }
                        break;
                    }
                    if (offset>=2 && !SnowGeometry.exposesGround(level,pos)) break;
                }
        return null;
    }
    public static void press(ServerLevel level, BlockPos pos, int hits) {
        if (!naturality.config.NaturalityServerConfig.get().snowCompaction) { PLAYERS.clear(); PRESSURE.clear(); return; }
        if (hits<=0 || !naturality.util.LoadedChunks.has(level, pos)) return;
        var state=level.getBlockState(pos);
        var history=PRESSURE.computeIfAbsent(level, _ -> new HashMap<>());
        if (!state.is(Blocks.SNOW) || state.getValue(SnowLayerBlock.LAYERS)<=1) { history.remove(pos); return; }
        int layers=state.getValue(SnowLayerBlock.LAYERS);
        long time=level.getGameTime();
        var old=history.get(pos);
        int total=hits+(old!=null && old.layers==layers && time-old.time<6000 ? old.hits : 0);
        if (total>=8) {
            history.remove(pos);
            level.setBlockAndUpdate(pos,state.setValue(SnowLayerBlock.LAYERS,layers-1));
        } else {
            if (history.size()>=8192) history.entrySet().removeIf(entry -> time-entry.getValue().time>=6000);
            if (history.size()>=8192) history.remove(history.keySet().iterator().next());
            history.put(pos.immutable(),new Pressure(layers,total,time));
        }
    }
}
