package naturality.client.fluids.shared.mixins;

import naturality.client.fluids.shared.FallingFluidTail.FallParticleBudget;
import naturality.client.fluids.shared.FallingFluidTail.BorderSplashParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import naturality.client.rendering.biometint.BiomeTint;
import naturality.client.fluids.shared.FluidGeometry.SodiumFluids;
import naturality.client.fluids.shared.FluidGeometry.SodiumFluidVertex;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FluidsSharedMixins {
    private FluidsSharedMixins() {}

    /** Sodium supplies a replacement default renderer, bypassing vanilla tesselate entirely. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumFluidRendererMixin {
        @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/fabricmc/fabric/api/client/render/fluid/v1/FluidRendering;render(Lnet/minecraft/client/renderer/block/FluidRenderer;Lnet/fabricmc/fabric/api/client/render/fluid/v1/FluidRenderHandler;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;Lnet/fabricmc/fabric/api/client/render/fluid/v1/FluidRendering$DefaultRenderer;)V"))
        private void naturality$nativeFluidGeometry(FluidRenderer renderer, FluidRenderHandler handler,
                BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState block,
                FluidState fluid, FluidRendering.DefaultRenderer defaultRenderer, Operation<Void> original,
                @Coerce Object slice, BlockState sourceBlock, FluidState sourceFluid, BlockPos sourcePos, BlockPos offset,
                @Coerce @org.jspecify.annotations.Nullable Object collector, @Coerce Object buffers) {
            if (SodiumFluids.handles(fluid)) {
                output = SodiumFluids.output(buffers, collector);
                defaultRenderer = SodiumFluids.DEFAULT;
            }
            original.call(renderer, handler, level, pos, output, block, fluid, defaultRenderer);
        }
    }

    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder$Vertex", remap = false)
    public static abstract class SodiumFluidVertexMixin implements SodiumFluidVertex {
        @Shadow public int light;
        @Override public int naturality$fluidLight() { return light; }
    }

    /** Keep the tail marker out of light coordinates, which Sodium compresses to eight bits each. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumFluidEncodingMixin {
        @ModifyExpressionValue(method = "lambda$getEncoder$0", at = @At(value = "INVOKE", target =
            "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/impl/CompactChunkVertex;packPositionHi(III)I"))
        private static int naturality$tailPositionBit(int packed, long pointer, int material,
                @Coerce Object[] vertices, int section, @Local(index = 7) int index) {
            return SodiumFluids.packedPosition(packed, vertices, index);
        }
    }

    /** SAFE sorting copies and later rewrites split vertices outside the render call. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.InnerPartitionBSPNode", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumFluidSplitMixin {
        @WrapOperation(method = "interpolateAttributes(FLorg/joml/Vector3fc;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;)V",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/api/util/ColorMixer;mix(IIF)I"))
        private static int naturality$interpolateFlow(int first, int second, float weight, Operation<Integer> original,
                float distance, org.joml.Vector3fc plane, @Coerce Object inside, @Coerce Object outside,
                @Coerce Object targetA, @Coerce Object targetB, @Coerce @org.jspecify.annotations.Nullable Object targetC) {
            return SodiumFluids.splitColor(original.call(first, second, weight), first, second, weight, inside, outside);
        }

        @WrapOperation(method = "interpolateAttributes(FLorg/joml/Vector3fc;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;)V",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;writeVertex(Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;FFFIFFFI)V"))
        private static void naturality$preserveSplitTags(@Coerce Object vertex, float x, float y, float z,
                int color, float ao, float u, float v, int light, Operation<Void> original,
                float distance, org.joml.Vector3fc plane, @Coerce Object inside, @Coerce Object outside,
                @Coerce Object targetA, @Coerce Object targetB, @Coerce @org.jspecify.annotations.Nullable Object targetC) {
            original.call(vertex, x, y, z, color, ao, u, v, SodiumFluids.splitLight(light, inside, outside));
        }
    }

    @Mixin(ParticleEngine.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FallParticleBudgetMixin {
        @SuppressWarnings("null") @Shadow protected ClientLevel level;
        @SuppressWarnings("null") @Shadow private it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<net.minecraft.core.particles.ParticleLimit> trackedParticleCounts;

        @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
        private void naturality$enforceSpawnBudget(ParticleOptions options,double x,double y,double z,double vx,double vy,double vz,
                CallbackInfoReturnable<Particle> cir) {
            if(FallParticleBudget.includes(options) && naturality$fallFull()) {
                cir.setReturnValue(null);
            }
        }
        @Inject(method="createParticle",at=@At("RETURN"),cancellable=true)
        private void naturality$returnRejectedParticle(ParticleOptions options,double x,double y,double z,double vx,double vy,double vz,
                CallbackInfoReturnable<Particle> cir) {
            var particle=cir.getReturnValue();
            // Return null when add() rejected the particle because the shared budget is full.
            if(particle!=null && !particle.isAlive())cir.setReturnValue(null);
        }
        @Inject(method="add",at=@At("HEAD"),cancellable=true)
        private void naturality$checkDirectParticle(Particle particle,CallbackInfo ci) {
            boolean fall=particle instanceof BorderSplashParticle;
            if(fall && naturality$fallFull()) {
                particle.remove();ci.cancel();
            } else if(fall) {
                FallParticleBudget.admitted(level);
            }
        }
        private boolean naturality$fallFull() {
            return FallParticleBudget.exhausted(level) || trackedParticleCounts.getInt(FallParticleBudget.LIMIT)>=FallParticleBudget.LIMIT.limit();
        }
        @Inject(method="clearParticles",at=@At("HEAD"))
        private void naturality$resetBudget(CallbackInfo ci){FallParticleBudget.reset();}
    }

    /** Vanilla fluid tint alpha is opaque; carry a corner flow angle in that byte.
     * The paired terrain shader restores alpha before lighting, even with effects OFF.
     */
    @Mixin(FluidRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FluidFlowMixin {
        @SuppressWarnings("null") @Shadow @Final private FluidStateModelSet fluidModels;
        @Unique private static final ThreadLocal<FlowCorners> naturality$corners = new ThreadLocal<>();

        @ModifyArgs(method = "vertex", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(FFFIFFIIFFF)V"))
        private void naturality$connectExposedFaces(Args args) {
            var corners = naturality$corners.get();
            if (corners == null) return;
            var tintSamples = corners.tintSamples;
            if (tintSamples != null) {
                float x = args.get(0), y = args.get(1), z = args.get(2);
                int tint = tintSamples.at(
                    x - (corners.pos.getX() & 15), y - (corners.pos.getY() & 15), z - (corners.pos.getZ() & 15));
                args.set(3, BiomeTint.replaceFluidTint(args.get(3), corners.baseTint, tint));
            }
            // Apply after flow encoding: side vertices must retain their side tag.
            args.set(0, corners.boundaries.coordinate(args.get(0), true));
            args.set(1, corners.boundaries.height(args.get(1)));
            args.set(2, corners.boundaries.coordinate(args.get(2), false));
        }

        @WrapMethod(method = "tesselate")
        private void naturality$flowCorners(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
                BlockState block, FluidState state, Operation<Void> original) {
            var previous = naturality$corners.get();
            try {
                if (state.getType().isSame(Fluids.WATER) || state.getType().isSame(Fluids.LAVA)) {
                    var source = fluidModels.get(state).tintSource();
                    // Fabric's ordinary native entry dispatches a handler and then re-enters
                    // for default geometry. Its matching body and outer tail are one pass.
                    naturality$corners.set(previous != null && previous.matches(level, pos, block, state, source)
                        ? previous : new FlowCorners(level, pos, block, state, source));
                } else naturality$corners.remove();
                original.call(level, pos, output, block, state);
                // Fabric's fluid handler re-enters this method for the same block.
                // Only the outer call emits the visual continuation.
                if(naturality$corners.get()!=null && (previous==null || previous.level!=level || !previous.pos.equals(pos)))
                    naturality.client.fluids.shared.FallingFluidTail.emit(level,pos,block,state,fluidModels.get(state),output,naturality$corners.get()::light);
            } finally {
                if (previous == null) naturality$corners.remove();
                else naturality$corners.set(previous);
            }
        }

        @WrapOperation(method="tesselate",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/block/FluidRenderer;shouldRenderFace(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z"))
        private boolean naturality$openTail(FluidState fluid,BlockState block,Direction side,FluidState neighbor,Operation<Boolean> original) {
            var corners=naturality$corners.get();
            if(side==Direction.DOWN && corners!=null
                && naturality.client.fluids.shared.FallingFluidTail.eligible(corners.level,corners.pos,fluid))return false;
            return original.call(fluid,block,side,neighbor);
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
            if(corners==null)return light;
            x = corners.boundaries.coordinate(x, true);
            y = corners.boundaries.height(y);
            z = corners.boundaries.coordinate(z, false);
            return corners.light(x,y,z,light);
        }

        @Unique private static final class FlowCorners {
            final BlockAndTintGetter level;
            final BlockPos pos;
            final BlockState block;
            final FluidState fluid;
            final net.minecraft.client.color.block.@org.jspecify.annotations.Nullable BlockTintSource source;
            final BiomeTint.@org.jspecify.annotations.Nullable Samples tintSamples;
            final int baseTint;
            final naturality.client.fluids.shared.FluidGeometry.BoundaryCache boundaries;
            final int[] angles = {-1, -1, -1, -1};
            naturality.client.fluids.shared.FluidGeometry.@org.jspecify.annotations.Nullable LightSamples lights;
            FlowCorners(BlockAndTintGetter level, BlockPos pos, BlockState block, FluidState fluid,
                    net.minecraft.client.color.block.@org.jspecify.annotations.Nullable BlockTintSource source) {
                this.level = level; this.pos = pos.immutable(); this.block = block; this.fluid = fluid; this.source = source;
                this.boundaries = new naturality.client.fluids.shared.FluidGeometry.BoundaryCache(level, this.pos);
                this.tintSamples = source instanceof BiomeTint.Source biome
                    ? new BiomeTint.Samples(biome, level, block, this.pos) : null;
                this.baseTint = source == null ? -1 : source.colorInWorld(block, level, pos);
            }
            boolean matches(BlockAndTintGetter level, BlockPos pos, BlockState block, FluidState fluid,
                    net.minecraft.client.color.block.@org.jspecify.annotations.Nullable BlockTintSource source) {
                return this.level == level && this.pos.equals(pos) && this.block == block
                    && this.fluid == fluid && this.source == source;
            }
            int light(float x, float y, float z, int fallback) {
                var samples = lights;
                if (samples == null) lights = samples = new naturality.client.fluids.shared.FluidGeometry.LightSamples(level, pos, fluid);
                return samples.at(x, y, z, fallback);
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
}
