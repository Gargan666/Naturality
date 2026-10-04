package naturality.snow;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;

/** Vanilla model solids whose upper faces differ from their selection outline. */
public final class SnowSupportModels {
    private SnowSupportModels() { }
    public static List<AABB> boxes(BlockGetter level,BlockPos pos) {
        var state=level.getBlockState(pos);
        if(state.getBlock() instanceof EndRodBlock || state.getBlock() instanceof LightningRodBlock) {
            boolean end=state.getBlock() instanceof EndRodBlock;
            var facing=state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            var parts=end ? List.of(box(6,0,6,10,1,10),box(7,1,7,9,16,9))
                : List.of(box(6,12,6,10,16,10),box(7,0,7,9,12,9));
            return parts.stream().map(b->orient(b,facing)).toList();
        }
        if(state.getBlock() instanceof LanternBlock) {
            int dy=state.getValue(LanternBlock.HANGING)?1:0;
            // The crossed handle has no horizontal face to hold a snow cap.
            return List.of(box(5,dy,5,11,7+dy,11),box(6,7+dy,6,10,9+dy,10));
        }
        if(state.getBlock() instanceof HopperBlock) {
            // Interior floor and four rim pieces; the spout is entirely below them.
            return List.of(box(0,10,0,16,11,16),box(0,11,0,2,16,16),
                box(14,11,0,16,16,16),box(2,11,0,14,16,2),box(2,11,14,14,16,16));
        }
        if(state.getBlock() instanceof FenceBlock || state.getBlock() instanceof FenceGateBlock)
            return naturality.fire.FireGeometry.supportBoxes(level,pos);
        return naturality.weather.WindShapes.raw(() -> state.getShape(level,pos).toAabbs());
    }
    private static AABB box(double x,double y,double z,double xx,double yy,double zz) {
        return new AABB(x/16,y/16,z/16,xx/16,yy/16,zz/16);
    }
    private static AABB orient(AABB b,Direction facing) {
        return switch(facing) {
            case DOWN -> new AABB(b.minX,1-b.maxY,1-b.maxZ,b.maxX,1-b.minY,1-b.minZ);
            case NORTH -> new AABB(b.minX,b.minZ,1-b.maxY,b.maxX,b.maxZ,1-b.minY);
            case SOUTH -> new AABB(1-b.maxX,b.minZ,b.minY,1-b.minX,b.maxZ,b.maxY);
            case EAST -> new AABB(b.minY,b.minZ,b.minX,b.maxY,b.maxZ,b.maxX);
            case WEST -> new AABB(1-b.maxY,b.minZ,1-b.maxX,1-b.minY,b.maxZ,1-b.minX);
            default -> b;
        };
    }
}
