package naturality.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SandPlacementBurst {
    private SandPlacementBurst() {}

    public static void spawn(ClientLevel level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.SAND) && !state.is(Blocks.RED_SAND)) return;
        for (int ix = 0; ix < 4; ix++) for (int iy = 0; iy < 4; iy++) for (int iz = 0; iz < 4; iz++) {
            double dx = (ix + 0.5) / 4 - 0.5;
            double dy = (iy + 0.5) / 4 - 0.5;
            double dz = (iz + 0.5) / 4 - 0.5;
            // Project each original burst sample beyond a face. Rotate tie-breaking
            // between axes so edges/corners distribute over all six faces.
            double[] offset = outside(dx, dy, dz, (ix + iy + iz) % 3);
            double x = pos.getX() + 0.5 + offset[0];
            double y = pos.getY() + 0.5 + offset[1];
            double z = pos.getZ() + 0.5 + offset[2];
            Minecraft.getInstance().particleEngine.add(new TerrainParticle(level, x, y, z, dx, dy, dz,
                state, BlockPos.containing(x, y, z)));
        }
    }

    public static double[] outside(double x, double y, double z, int firstAxis) {
        double[] offsets = {x, y, z};
        int axis = firstAxis;
        for (int step = 1; step < 3; step++) {
            int next = (firstAxis + step) % 3;
            if (Math.abs(offsets[next]) > Math.abs(offsets[axis])) axis = next;
        }
        // 0.25 clearance exceeds the maximum rotating sand quad radius.
        offsets[axis] = Math.copySign(0.75, offsets[axis]);
        return offsets;
    }
}
