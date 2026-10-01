package naturality.portal;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.AABB;

/** Entry side stays fixed throughout a crossing, including after the centre crosses the plane. */
public final class PortalCrossing {
    public final BlockPos anchor, min, max;
    public final Direction.Axis axis;
    public final int side;
    public final long start;
    public final double plane;

    public PortalCrossing(Entity entity, BlockPos anchor, Direction.Axis axis, int side) {
        this.anchor = anchor.immutable(); this.axis = axis; this.side = side;
        start = entity.level().getGameTime();
        plane = (axis == Direction.Axis.X ? anchor.getZ() : anchor.getX()) + 0.5 + side * 0.125;
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(anchor);
        int x0=anchor.getX(), x1=x0, y0=anchor.getY(), y1=y0, z0=anchor.getZ(), z1=z0;
        while (!queue.isEmpty() && found.size() < 441) {
            BlockPos p = queue.removeFirst();
            if (found.contains(p)) continue;
            var state = entity.level().getBlockState(p);
            if (!state.is(Blocks.NETHER_PORTAL) || state.getValue(NetherPortalBlock.AXIS) != axis) continue;
            found.add(p);
            x0=Math.min(x0,p.getX()); x1=Math.max(x1,p.getX()); y0=Math.min(y0,p.getY()); y1=Math.max(y1,p.getY());
            z0=Math.min(z0,p.getZ()); z1=Math.max(z1,p.getZ());
            queue.add(p.above()); queue.add(p.below());
            queue.add(p.relative(axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH));
            queue.add(p.relative(axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH));
        }
        min=new BlockPos(x0,y0,z0); max=new BlockPos(x1,y1,z1);
    }
    public static @org.jspecify.annotations.Nullable PortalCrossing get(Entity entity) { if (!naturality.config.GameplaySettings.physicalPortalEntry(entity.level())) return null; return ((PortalCrossingAccess)entity).naturality$getCrossing(); }
    public static void syncToObserver(Entity entity, ServerPlayer player) {
        PortalCrossing crossing=get(entity);
        if(crossing!=null && ServerPlayNetworking.canSend(player,PortalCrossingPayload.TYPE))
            ServerPlayNetworking.send(player,new PortalCrossingPayload(entity.level().dimension().identifier().toString(),
                entity.getId(),crossing.anchor,crossing.axis,crossing.side,true));
    }
    public static void set(Entity entity, @org.jspecify.annotations.Nullable PortalCrossing crossing) { ((PortalCrossingAccess)entity).naturality$setCrossing(crossing); }
    public double signed(double x, double z) { return ((axis == Direction.Axis.X ? z : x) - plane) * side; }
    public float progress(AABB box) {
        double width = axis == Direction.Axis.X ? box.getZsize() : box.getXsize();
        return (float)Math.clamp(0.5 - signed(box.getCenter().x, box.getCenter().z) / Math.max(width, 0.001), 0, 1);
    }
    public boolean valid(Entity entity) {
        var state=entity.level().getBlockState(anchor);
        if (!state.is(Blocks.NETHER_PORTAL) || state.getValue(NetherPortalBlock.AXIS)!=axis) return false;
        AABB b=entity.getBoundingBox();
        double half=(axis==Direction.Axis.X?b.getZsize():b.getXsize())/2;
        if (signed(b.getCenter().x,b.getCenter().z)>half+0.15) return false;
        return b.maxY>min.getY() && b.minY<max.getY()+1 && (axis==Direction.Axis.X
            ? b.maxX>min.getX() && b.minX<max.getX()+1 : b.maxZ>min.getZ() && b.minZ<max.getZ()+1);
    }
    public static void enter(Entity entity, BlockPos pos, Direction.Axis axis) {
        if (!naturality.config.GameplaySettings.physicalPortalEntry(entity.level())) return;
        if (get(entity)!=null || entity.isOnPortalCooldown() || !entity.canUsePortal(false)) return;
        double center=(axis==Direction.Axis.X?pos.getZ():pos.getX())+0.5;
        // Block contact covers the entire voxel; entry requires touching the portal slab.
        AABB bounds=entity.getBoundingBox();
        double near=axis==Direction.Axis.X?bounds.minZ:bounds.minX;
        double far=axis==Direction.Axis.X?bounds.maxZ:bounds.maxX;
        if(near>center+0.125 || far<center-0.125) return;
        double previous=axis==Direction.Axis.X?entity.zo:entity.xo;
        double coordinate=axis==Direction.Axis.X?entity.getZ():entity.getX();
        double velocity=axis==Direction.Axis.X?entity.getDeltaMovement().z:entity.getDeltaMovement().x;
        double difference=previous-center;
        if(Math.abs(difference)<0.001) difference=coordinate-center;
        int side=Math.abs(difference)>0.001?(difference>0?1:-1):(velocity>0?-1:1);
        PortalCrossing crossing=new PortalCrossing(entity,pos,axis,side);
        set(entity,crossing); crossing.sync(entity,true);
    }
    public static void tick(Entity entity) {
        PortalCrossing c=((PortalCrossingAccess)entity).naturality$getCrossing();
        if(c==null) return;
        if(!naturality.config.GameplaySettings.physicalPortalEntry(entity.level()) || entity.isRemoved() || entity.isOnPortalCooldown() || !c.valid(entity)) {
            c.sync(entity,false); set(entity,null);
            if(entity.portalProcess!=null && entity.portalProcess.isSamePortal((NetherPortalBlock)Blocks.NETHER_PORTAL)) entity.portalProcess=null;
            return;
        }
        if(!entity.level().isClientSide()) {
            entity.setAsInsidePortal((NetherPortalBlock)Blocks.NETHER_PORTAL,c.anchor);
            if((entity.level().getGameTime()-c.start)%10==0) c.sync(entity,true);
        }
    }
    void sync(Entity entity, boolean active) {
        if(entity.level().isClientSide()) return;
        var payload=new PortalCrossingPayload(entity.level().dimension().identifier().toString(),entity.getId(),anchor,axis,side,active);
        Set<ServerPlayer> players=new HashSet<>(PlayerLookup.tracking(entity));
        if(entity instanceof ServerPlayer player) players.add(player);
        for(var player:players) if(ServerPlayNetworking.canSend(player,PortalCrossingPayload.TYPE)) ServerPlayNetworking.send(player,payload);
    }
}
