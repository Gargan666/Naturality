package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vanilla fluid tint alpha is opaque; carry a corner flow angle in that byte.
 * The paired terrain shader restores alpha before lighting, even with effects OFF.
 */
@Mixin(FluidRenderer.class)
public abstract class FluidFlowMixin {
    @Unique private static final ThreadLocal<FlowCorners> naturality$corners = new ThreadLocal<>();

    @WrapMethod(method = "tesselate")
    private void naturality$flowCorners(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
            BlockState block, FluidState state, Operation<Void> original) {
        var previous = naturality$corners.get();
        try {
            if (state.getType().isSame(Fluids.WATER) || state.getType().isSame(Fluids.LAVA))
                naturality$corners.set(new FlowCorners(level, pos, state));
            else naturality$corners.remove();
            original.call(level, pos, output, block, state);
        } finally {
            if (previous == null) naturality$corners.remove();
            else naturality$corners.set(previous);
        }
    }

    @ModifyVariable(method = "vertex", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int naturality$encodeFlow(int color, VertexConsumer builder, float x, float y, float z,
            int originalColor, float u, float v, int light) {
        var corners = naturality$corners.get();
        if (corners == null || y <= (corners.pos.getY() & 15) + 0.01F
                || Math.abs(x - Math.round(x)) > 0.0001F || Math.abs(z - Math.round(z)) > 0.0001F) return color;
        int cx = Math.round(x) - (corners.pos.getX() & 15);
        int cz = Math.round(z) - (corners.pos.getZ() & 15);
        if (cx < 0 || cx > 1 || cz < 0 || cz > 1) return color;
        int angle = corners.angle(cx, cz);
        return (color & 0xFFFFFF) | (angle << 24);
    }

    @ModifyVariable(method = "vertex", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int naturality$vertexLight(int light, VertexConsumer builder, float x, float y, float z,
            int color, float u, float v, int originalLight) {
        var corners = naturality$corners.get();
        return corners == null ? light : corners.light(x, y, z, light);
    }

    @Unique private static final class FlowCorners {
        final BlockAndTintGetter level;
        final BlockPos pos;
        final FluidState fluid;
        final int[] angles = {-1, -1, -1, -1};
        final java.util.Map<BlockPos, Integer> lights = new java.util.HashMap<>();
        FlowCorners(BlockAndTintGetter level, BlockPos pos, FluidState fluid) {
            this.level = level; this.pos = pos.immutable(); this.fluid = fluid;
        }
        int light(float x, float y, float z, int fallback) {
            // Interpolate a world-anchored, cell-centered field. Inset side faces
            // sample their actual positions; section-local arithmetic keeps the
            // same precision at negative coordinates and the world border.
            double px = x - (pos.getX() & 15) - 0.5;
            double py = y - (pos.getY() & 15) - 0.5;
            double pz = z - (pos.getZ() & 15) - 0.5;
            int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
            double fx = px - bx, fy = py - by, fz = pz - bz;
            double block = 0, sky = 0, total = 0;
            for (int dz = 0; dz < 2; dz++) for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                BlockPos sample = pos.offset(bx + dx, by + dy, bz + dz);
                int packed = lights.computeIfAbsent(sample, p -> level.getBlockState(p).isSolidRender()
                    ? -1 : LightCoordsUtil.getLightCoords(level, p));
                if (packed < 0) continue;
                double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
                block += LightCoordsUtil.smoothBlock(packed) * weight;
                sky += LightCoordsUtil.smoothSky(packed) * weight;
                total += weight;
            }
            if (total < 1e-6) return fallback;
            int blockLight = (int) Math.round(block / total);
            // Lava's own emission must not be diluted by its air neighbors.
            if (fluid.getType().isSame(Fluids.LAVA) && naturality.config.NaturalityConfig.get().liquids.lava && naturality.config.NaturalityConfig.get().liquids.emissiveLava) return LightCoordsUtil.FULL_BRIGHT;
            return LightCoordsUtil.smoothPack(blockLight, (int) Math.round(sky / total));
        }
        int angle(int x, int z) {
            int index = z * 2 + x;
            if (angles[index] >= 0) return angles[index];
            double vx = 0, vz = 0;
            // Every touching quad visits precisely the same four cells, in the
            // same order, including negative positions and section boundaries.
            for (int dz = -1; dz <= 0; dz++) for (int dx = -1; dx <= 0; dx++) {
                BlockPos p = pos.offset(x + dx, 0, z + dz);
                var neighbor = level.getBlockState(p).getFluidState();
                if (!neighbor.getType().isSame(fluid.getType())) continue;
                var flow = neighbor.getFlow(level, p);
                vx += flow.x; vz += flow.z;
            }
            // 254 is zero flow; 255 means an ordinary, unencoded vertex.
            return angles[index] = vx * vx + vz * vz < 1e-10 ? 254
                : (int) Math.floor((Math.atan2(vz, vx) + Math.PI) * 254 / (2 * Math.PI)) % 254;
        }
    }
}
