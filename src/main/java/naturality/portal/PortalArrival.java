package naturality.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Establishes a reversible crossing on the exit-facing surface of the destination portal. */
public final class PortalArrival {
    public static TeleportTransition prepare(TeleportTransition transition, PortalCrossing source, Entity entity) {
        BlockPos target = BlockPos.containing(transition.position());
        BlockPos anchor = null;
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-2,-2,-2), target.offset(2,2,2))) {
            if (!transition.newLevel().getBlockState(pos).is(Blocks.NETHER_PORTAL)) continue;
            double distance = pos.distToCenterSqr(transition.position());
            if (distance < best) { best=distance; anchor=pos.immutable(); }
        }
        if (anchor == null) return transition;
        Direction.Axis axis=transition.newLevel().getBlockState(anchor).getValue(NetherPortalBlock.AXIS);
        // Vanilla rotates travel by +90 degrees when changing portal axes.
        int side=source.axis==axis ? -source.side : source.axis==Direction.Axis.X ? source.side : -source.side;
        double center=(axis==Direction.Axis.X?anchor.getZ():anchor.getX())+0.5;
        var bounds=entity.getBoundingBox();
        double half=(axis==Direction.Axis.X?bounds.getZsize():bounds.getXsize())/2;
        // The exit-facing side is visible; start with the entire body behind it.
        // The far-face travel threshold leaves a slab-width gap before return travel.
        double normal=center+side*(0.125-half-PortalCrossing.HIDDEN_CLEARANCE);
        Vec3 original=transition.position();
        Vec3 position=axis==Direction.Axis.X?new Vec3(original.x,original.y,normal):new Vec3(normal,original.y,original.z);
        BlockPos entry=anchor;
        return new TeleportTransition(transition.newLevel(),position,transition.deltaMovement(),transition.yRot(),transition.xRot(),
            transition.missingRespawnBlock(),transition.asPassenger(),transition.relatives(),
            transition.postTeleportTransition().then(arriving -> arrive(arriving,entry,axis,side)));
    }

    private static void arrive(Entity entity,BlockPos anchor,Direction.Axis axis,int side) {
        entity.setPortalCooldown(0);
        entity.portalProcess=null;
        PortalCrossing crossing=new PortalCrossing(entity,anchor,axis,side);
        PortalCrossing.set(entity,crossing);
        entity.setAsInsidePortal((NetherPortalBlock)Blocks.NETHER_PORTAL,anchor);
        crossing.sync(entity,true);
    }
    private PortalArrival() { }
}
