package naturality.snow;

import java.util.List;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Upper surfaces of block-entity meshes which have no JSON model elements. */
final class SnowBuiltinModels {
    private SnowBuiltinModels() { }
    static List<AABB> boxes(BlockState state) {
        var block=state.getBlock();
        if(block instanceof ChestBlock || block instanceof EnderChestBlock) {
            double x=1,z=1,X=15,Z=15;
            if(block instanceof ChestBlock) {
                var type=state.getValue(ChestBlock.TYPE);
                if(type!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                    var joined=ChestBlock.getConnectedDirection(state);
                    switch(joined) {
                        case NORTH -> z=0;
                        case SOUTH -> Z=16;
                        case WEST -> x=0;
                        case EAST -> X=16;
                        default -> { }
                    }
                }
            }
            return List.of(top(x,14,z,X,Z));
        }
        if(block instanceof ShulkerBoxBlock)return List.of(top(0,16,0,16,16));
        if(block instanceof DecoratedPotBlock)return List.of(top(1,16,1,15,15));
        if(block instanceof ConduitBlock)return List.of(top(5,11,5,11,11));
        if(block instanceof AbstractSkullBlock) {
            if(block instanceof WallSkullBlock) {
                var facing=state.getValue(WallSkullBlock.FACING);
                return switch(facing) {
                    case NORTH -> List.of(top(4,12,8,12,16));
                    case SOUTH -> List.of(top(4,12,0,12,8));
                    case EAST -> List.of(top(0,12,4,8,12));
                    case WEST -> List.of(top(8,12,4,16,12));
                    default -> List.of();
                };
            }
            return List.of(top(4,8,4,12,12));
        }
        if(block instanceof BedBlock)return List.of(top(0,9,0,16,16));
        // Invisible blocks and temporary moving-piston cells have no static
        // rendered upper face and therefore do not produce a floating cap.
        return List.of();
    }
    private static AABB top(double x,double y,double z,double X,double Z) {
        return new AABB(x/16,y/16-1e-6,z/16,X/16,y/16,Z/16);
    }
}
