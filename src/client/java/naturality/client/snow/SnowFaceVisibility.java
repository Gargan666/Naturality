package naturality.client.snow;

import java.util.HashMap;
import java.util.Map;
import naturality.snow.SnowGeometry;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Conservative whole-face tests in mesh coordinates, including displaced snow. */
public final class SnowFaceVisibility {
    private static final double EPSILON = 1e-5;
    private final BlockAndTintGetter level;
    private final BlockPos owner;
    private final VoxelShape snow;
    private final Map<BlockPos, VoxelShape> occluders = new HashMap<>();

    public SnowFaceVisibility(BlockAndTintGetter level, BlockPos owner, VoxelShape snow) {
        this.level = level;
        this.owner = owner.immutable();
        this.snow = snow;
    }

    public boolean visible(AABB box, Direction face) {
        double x=box.minX, y=box.minY, z=box.minZ, X=box.maxX, Y=box.maxY, Z=box.maxZ;
        switch (face) {
            case DOWN -> { Y=y; y-=EPSILON; }
            case UP -> { y=Y; Y+=EPSILON; }
            case NORTH -> { Z=z; z-=EPSILON; }
            case SOUTH -> { z=Z; Z+=EPSILON; }
            case WEST -> { X=x; x-=EPSILON; }
            case EAST -> { x=X; X+=EPSILON; }
        }
        var bounds = new AABB(x,y,z,X,Y,Z);
        VoxelShape remaining = null;
        // Other boxes in this same fitted mesh can hide an entire box face.
        if (!snow.isEmpty() && snow.bounds().intersects(bounds)) {
            remaining = Shapes.join(Shapes.create(bounds),snow,BooleanOp.ONLY_FIRST);
            if (remaining.isEmpty()) return false;
        }
        for (int bx=(int)Math.floor(x);bx<=Math.floor(Math.nextDown(X));bx++)
            for (int by=(int)Math.floor(y);by<=Math.floor(Math.nextDown(Y));by++)
                for (int bz=(int)Math.floor(z);bz<=Math.floor(Math.nextDown(Z));bz++) {
                    var cell = owner.offset(bx,by,bz);
                    if (cell.equals(owner)) continue;
                    var blocker = occluders.computeIfAbsent(cell,this::occluder);
                    if (!blocker.isEmpty() && blocker.bounds().intersects(bounds)) {
                        remaining = Shapes.join(remaining == null ? Shapes.create(bounds) : remaining,
                            blocker,BooleanOp.ONLY_FIRST);
                        if (remaining.isEmpty()) return false;
                    }
                    // Adjacent fitted snow can be saved beside our owner while its
                    // geometry sits in this lower physical cell (e.g. bottom slabs).
                    var adjacentOwner=owner.offset(bx,0,bz);
                    if (by!=0 && !adjacentOwner.equals(owner)) {
                        var adjacent=occluders.computeIfAbsent(adjacentOwner,this::occluder);
                        if (!adjacent.isEmpty() && adjacent.bounds().intersects(bounds)) {
                            remaining=Shapes.join(remaining == null ? Shapes.create(bounds) : remaining,
                                adjacent,BooleanOp.ONLY_FIRST);
                            if (remaining.isEmpty()) return false;
                        }
                    }
                }
        return true;
    }

    private VoxelShape occluder(BlockPos cell) {
        var state = level.getBlockState(cell);
        VoxelShape shape;
        if (state.is(Blocks.SNOW)) {
            // Neighboring leaf snow can move away from an otherwise shared edge.
            for (int depth=1;depth<=7;depth++) {
                var support = level.getBlockState(cell.below(depth));
                if (support.is(BlockTags.LEAVES)) return Shapes.empty();
                if (!support.is(Blocks.SNOW)) break;
            }
            shape = SnowGeometry.shape(level,cell,state.getValue(SnowLayerBlock.LAYERS));
        } else {
            // Cutout/translucent blocks must never act as solid face occluders.
            if (!state.canOcclude()) return Shapes.empty();
            shape = state.getOcclusionShape();
        }
        return shape.move(cell.getX()-owner.getX(),cell.getY()-owner.getY(),cell.getZ()-owner.getZ());
    }
}
