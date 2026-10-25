package naturality.client.fluids.shared;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import naturality.client.fluids.lava.LavaEffects.LavaSurface;
import naturality.client.fluids.water.WaterShaders.WaterSurface;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.function.DoubleBinaryOperator;
import naturality.client.api.geometry.PlanarGeometry;
import naturality.client.api.geometry.PlanarGeometry.ModelSection;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Join exposed liquid faces while retaining the inset against block geometry. */
@org.jspecify.annotations.NullMarked
public final class FluidGeometry {
    private FluidGeometry() {}

    /**
     * Fabric's default-renderer boundary keeps the registered fluid handler in charge.
     * Only ordinary vanilla fluids use our authoritative native tessellation; shader
     * packs and handler overrides keep Sodium's renderer and its attribute contract.
     */
    public static final class SodiumFluids {
        public static final int TAIL_POSITION_FLAG = 1 << 30;
        public static final int NATIVE_POSITION_FLAG = 1 << 31;
        private static final int NATIVE_LIGHT_FLAG = 0x2000;
        public static final net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering.DefaultRenderer DEFAULT =
            // The API default establishes Fabric's recursion guard before entering tesselate.
            new net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering.DefaultRenderer() {};

        private SodiumFluids() {}

        public static boolean handles(net.minecraft.world.level.material.FluidState state) {
            var fluid = state.getType();
            return (fluid == net.minecraft.world.level.material.Fluids.WATER
                    || fluid == net.minecraft.world.level.material.Fluids.FLOWING_WATER
                    || fluid == net.minecraft.world.level.material.Fluids.LAVA
                    || fluid == net.minecraft.world.level.material.Fluids.FLOWING_LAVA)
                && net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.getOverride(fluid) == null
                && !naturality.client.api.rendering.ShaderPacks.terrainActive();
        }

        public static net.minecraft.client.renderer.block.FluidRenderer.Output output(Object buffers,
                @org.jspecify.annotations.Nullable Object collector) {
            // Resolve the builder as well as the material for each requested layer.
            // In particular, a lava continuation must enter the translucent buffers.
            return layer -> new MarkedConsumer(Bindings.consumer(buffers, collector, layer));
        }

        /** CompactChunkVertex uses only bits 0..29 of each packed position word. */
        public static int packedPosition(int packed, Object[] vertices, int index) {
            int light = ((SodiumFluidVertex) vertices[index]).naturality$fluidLight();
            if ((light & NATIVE_LIGHT_FLAG) == 0) return packed;
            // Mark ownership as well: custom handlers may use alpha as actual opacity.
            packed |= NATIVE_POSITION_FLAG;
            return (light & FallingFluidTail.LIGHT_FLAG) != 0 ? packed | TAIL_POSITION_FLAG : packed;
        }

        /** Sorting can rewrite copied vertices after tessellation has returned. */
        public static int splitLight(int light, Object inside, Object outside) {
            int common = ((SodiumFluidVertex) inside).naturality$fluidLight()
                & ((SodiumFluidVertex) outside).naturality$fluidLight();
            return (common & NATIVE_LIGHT_FLAG) == 0 ? light
                : light | (common & (NATIVE_LIGHT_FLAG | FallingFluidTail.LIGHT_FLAG));
        }

        public static int splitColor(int mixed, int first, int second, float weight, Object inside, Object outside) {
            if ((splitLight(0, inside, outside) & NATIVE_LIGHT_FLAG) == 0) return mixed;
            int a = first >>> 24, b = second >>> 24;
            if (a == 255 || b == 255) return mixed;
            // Alpha stores an angle, so averaging bytes would send 253/0 through
            // the opposite direction. Interpolate the decoded vectors instead.
            // Sodium passes outsideAmount here. Follow its geometric interpolation,
            // not ColorMixer's reversed RGB weighting; leave that RGB result intact.
            double scale = 2 * Math.PI / 254;
            double angleA = (a + .5) * scale - Math.PI, angleB = (b + .5) * scale - Math.PI;
            double x = (a == 254 ? 0 : Math.cos(angleA)) * (1 - weight)
                + (b == 254 ? 0 : Math.cos(angleB)) * weight;
            double z = (a == 254 ? 0 : Math.sin(angleA)) * (1 - weight)
                + (b == 254 ? 0 : Math.sin(angleB)) * weight;
            int code = x * x + z * z < 1e-10 ? 254
                : (int) Math.floor((Math.atan2(z, x) + Math.PI) / scale) % 254;
            return (mixed & 0xFFFFFF) | code << 24;
        }

        /** Metadata travels in unused light bits until the compact encoder packs it. */
        private record MarkedConsumer(com.mojang.blaze3d.vertex.VertexConsumer original)
                implements com.mojang.blaze3d.vertex.VertexConsumer {
            @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                    int overlay, int light, float nx, float ny, float nz) {
                original.addVertex(x, y, z, color, u, v, overlay, light | NATIVE_LIGHT_FLAG, nx, ny, nz);
            }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z) { original.addVertex(x,y,z); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a) { original.setColor(r,g,b,a); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int color) { original.setColor(color); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v) { original.setUv(u,v); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v) { original.setUv1(u,v); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v) { original.setUv2(u | NATIVE_LIGHT_FLAG,v); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv3(float u,float v) { original.setUv3(u,v); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z) { original.setNormal(x,y,z); return this; }
            @Override public com.mojang.blaze3d.vertex.VertexConsumer setLineWidth(float width) { original.setLineWidth(width); return this; }
        }

        /** Optional linkage is initialized only after the Sodium-only hook has selected this path. */
        private static final class Bindings {
            private static final Object[] MATERIALS;
            private static final java.lang.invoke.MethodHandle BUILDER, CONSUMER;
            static {
                try {
                    var lookup = java.lang.invoke.MethodHandles.publicLookup();
                    var material = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material");
                    var materials = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials");
                    var buffers = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers");
                    var builder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder");
                    var collector = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector");
                    var layer = net.minecraft.client.renderer.chunk.ChunkSectionLayer.class;
                    var resolve = materials.getMethod("forChunkLayer", layer);
                    var layers = net.minecraft.client.renderer.chunk.ChunkSectionLayer.values();
                    MATERIALS = new Object[layers.length];
                    for (var value : layers) MATERIALS[value.ordinal()] = Objects.requireNonNull(resolve.invoke(null, value));
                    BUILDER = lookup.unreflect(buffers.getMethod("get", material)).asType(
                        java.lang.invoke.MethodType.methodType(Object.class, Object.class, Object.class));
                    CONSUMER = lookup.unreflect(builder.getMethod("asFallbackVertexConsumer", material, collector)).asType(
                        java.lang.invoke.MethodType.methodType(com.mojang.blaze3d.vertex.VertexConsumer.class,
                            Object.class, Object.class, Object.class));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Unsupported Sodium fluid mesh buffers", failure);
                }
            }

            static com.mojang.blaze3d.vertex.VertexConsumer consumer(Object buffers,
                    @org.jspecify.annotations.Nullable Object collector,
                    net.minecraft.client.renderer.chunk.ChunkSectionLayer layer) {
                try {
                    Object material = MATERIALS[layer.ordinal()];
                    Object builder = BUILDER.invokeExact(buffers, material);
                    return (com.mojang.blaze3d.vertex.VertexConsumer) CONSUMER.invokeExact(builder, material, collector);
                } catch (RuntimeException | Error failure) { throw failure; }
                catch (Throwable failure) { throw new IllegalStateException("Cannot write Sodium fluid geometry", failure); }
            }
        }
    }

    /** Injected into Sodium's vertex without linking optional classes from common rendering code. */
    public interface SodiumFluidVertex {
        int naturality$fluidLight();
    }

    /** One fluid tessellation's cell-centered light field, including its one-block tail. */
    public static final class LightSamples {
        private final BlockAndTintGetter level;
        private final BlockPos pos;
        private final net.minecraft.world.level.material.FluidState fluid;
        // Ordinary body/tail vertices visit x,z=-1..1 and y=-2..1: exactly 36 cells.
        private final int[] samples = new int[36];
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private long valid;
        private @org.jspecify.annotations.Nullable Map<BlockPos, Integer> overflow;

        public LightSamples(BlockAndTintGetter level, BlockPos pos, net.minecraft.world.level.material.FluidState fluid) {
            this.level = level; this.pos = pos.immutable(); this.fluid = fluid;
        }

        private int sample(int x, int y, int z) {
            if (x < -1 || x > 1 || y < -2 || y > 1 || z < -1 || z > 1) {
                // Preserve caching for peer-mod vertices outside the native fluid envelope.
                var cache = overflow;
                if (cache == null) overflow = cache = new HashMap<>();
                return cache.computeIfAbsent(pos.offset(x, y, z), this::read);
            }
            int index = x + 1 + (z + 1) * 3 + (y + 2) * 9;
            long bit = 1L << index;
            if ((valid & bit) == 0) {
                cursor.setWithOffset(pos, x, y, z);
                samples[index] = read(cursor);
                valid |= bit;
            }
            return samples[index];
        }

        private int read(BlockPos sample) {
            return level.getBlockState(sample).isSolidRender() ? -1
                : net.minecraft.util.LightCoordsUtil.getLightCoords(level, sample);
        }

        public int at(float x, float y, float z, int fallback) {
            // Keep the original query order and blending arithmetic, including zero-weight cells.
            double px = x - (pos.getX() & 15) - 0.5;
            double py = y - (pos.getY() & 15) - 0.5;
            double pz = z - (pos.getZ() & 15) - 0.5;
            int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
            double fx = px - bx, fy = py - by, fz = pz - bz;
            double block = 0, sky = 0, total = 0;
            for (int dz = 0; dz < 2; dz++) for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int packed = sample(bx + dx, by + dy, bz + dz);
                if (packed < 0) continue;
                double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
                block += net.minecraft.util.LightCoordsUtil.smoothBlock(packed) * weight;
                sky += net.minecraft.util.LightCoordsUtil.smoothSky(packed) * weight;
                total += weight;
            }
            if (total < 1e-6) return fallback;
            int blockLight = (int) Math.round(block / total);
            // Retain the live emissive setting and the original fallback-before-emission order.
            if (fluid.getType().isSame(net.minecraft.world.level.material.Fluids.LAVA)
                    && naturality.config.live.NaturalityConfig.get().liquids.lava
                    && naturality.config.live.NaturalityConfig.get().liquids.emissiveLava)
                return net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
            return net.minecraft.util.LightCoordsUtil.smoothPack(blockLight, (int) Math.round(sky / total));
        }
    }

    public static float connectedHeight(BlockAndTintGetter level, BlockPos pos, float height) {
        return connectedHeight(level, pos, height, null);
    }

    private static float connectedHeight(BlockAndTintGetter level, BlockPos pos, float height,
            @org.jspecify.annotations.Nullable BoundaryCache cache) {
        int local = pos.getY() & 15;
        float relative = height - local;
        if (Math.abs(relative - 0.001F) < 0.0001F && boundaryOpen(level, pos, Direction.DOWN, cache))
            return local;
        // A full-height corner can be lowered by vanilla's top-face epsilon,
        // leaving a crack against a neighboring column at the integer boundary.
        if (Math.abs(relative - 0.999F) < 0.0001F && boundaryOpen(level, pos, Direction.UP, cache))
            return local + 1;
        return height;
    }

    public static float connectedCoordinate(BlockAndTintGetter level, BlockPos pos, float coordinate, boolean xAxis) {
        return connectedCoordinate(level, pos, coordinate, xAxis, null);
    }

    private static float connectedCoordinate(BlockAndTintGetter level, BlockPos pos, float coordinate, boolean xAxis,
            @org.jspecify.annotations.Nullable BoundaryCache cache) {
        int local = (xAxis ? pos.getX() : pos.getZ()) & 15;
        float relative = coordinate - local;
        Direction side;
        float boundary;
        if (Math.abs(relative - 0.001F) < 0.0001F) {
            side = xAxis ? Direction.WEST : Direction.NORTH;
            boundary = local;
        } else if (Math.abs(relative - 0.999F) < 0.0001F) {
            side = xAxis ? Direction.EAST : Direction.SOUTH;
            boundary = local + 1;
        } else return coordinate;
        return boundaryOpen(level, pos, side, cache) ? boundary : coordinate;
    }

    private static boolean boundaryOpen(BlockAndTintGetter level, BlockPos pos, Direction side,
            @org.jspecify.annotations.Nullable BoundaryCache cache) {
        return cache == null ? openBoundary(level, pos, side) : cache.open(side);
    }

    /** One tessellation's lazy six-face cache; never retained across blocks or mesh rebuilds. */
    public static final class BoundaryCache {
        private final BlockAndTintGetter level;
        private final BlockPos pos;
        private int checked, open;

        public BoundaryCache(BlockAndTintGetter level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        private boolean open(Direction side) {
            int bit = 1 << side.ordinal();
            if ((checked & bit) == 0) {
                if (openBoundary(level, pos, side)) open |= bit;
                checked |= bit;
            }
            return (open & bit) != 0;
        }

        public float height(float height) { return connectedHeight(level, pos, height, this); }
        public float coordinate(float coordinate, boolean xAxis) {
            return connectedCoordinate(level, pos, coordinate, xAxis, this);
        }
    }

    private static boolean openBoundary(BlockAndTintGetter level, BlockPos pos, Direction side) {
        var self = level.getBlockState(pos);
        // Waterlogged blocks share this cell with the fluid. Keep vanilla's
        // inset at their outer faces too, so doors and other partial blocks
        // remain in front of the water instead of sharing its depth plane.
        if (self.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE
                && !self.getCollisionShape(level, pos).getFaceShape(side).isEmpty()) return false;
        var neighborPos = pos.relative(side);
        var neighbor = level.getBlockState(neighborPos);
        // Air is not the only empty visual boundary: barriers are invisible,
        // while plants have no solid contact face. Selection/outline shapes
        // include plant bounding boxes and would falsely leave these cracks.
        // Keep the anti-z-fighting inset when visible block geometry does meet
        // that plane (including transparent glass and partial block supports).
        return neighbor.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE
            || neighbor.getCollisionShape(level, neighborPos).getFaceShape(side.getOpposite()).isEmpty();
    }

    /** Rendered fluid heights shared by moving spray and the pixel columns of impact splashes. */
    public static final class LiquidSurfaceGeometry {
        public record Sample(BlockPos pos,double x,double z,double height,double downhillX,double downhillZ) {}
        private LiquidSurfaceGeometry() {}
        public static @org.jspecify.annotations.Nullable Sample at(ClientLevel level,BlockPos pos,double x,double z,boolean lava) {
            double u=Math.clamp(x-pos.getX(),.01,.99),v=Math.clamp(z-pos.getZ(),.01,.99);
            if(lava) {
                var surface=LavaSurface.at(level,pos);
                if(surface==null || !surface.contains(u,v))return null;
                return sample(surface.pos(),u,v,surface::height);
            }
            var surface=WaterSurface.at(level,pos);
            if(surface==null || !surface.contains(u,v))return null;
            return sample(surface.pos(),u,v,surface::height);
        }
        private static Sample sample(BlockPos pos,double u,double v,DoubleBinaryOperator height) {
            double gx=height.applyAsDouble(Math.min(.99,u+.04),v)-height.applyAsDouble(Math.max(.01,u-.04),v);
            double gz=height.applyAsDouble(u,Math.min(.99,v+.04))-height.applyAsDouble(u,Math.max(.01,v-.04));
            return new Sample(pos,pos.getX()+u,pos.getZ()+v,height.applyAsDouble(u,v),-gx,-gz);
        }
        public static @org.jspecify.annotations.Nullable Sample near(ClientLevel level,double x,double y,double z,boolean lava,boolean edge) {
            int originX=net.minecraft.util.Mth.floor(x),originY=net.minecraft.util.Mth.floor(y),originZ=net.minecraft.util.Mth.floor(z);
            var cursor=new BlockPos.MutableBlockPos();
            Sample best=null;
            double score=Double.POSITIVE_INFINITY;
            int radius=edge?1:0;
            for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                int bx=originX+dx,bz=originZ+dz;
                if(x<bx-.08 || x>bx+1.08 || z<bz-.08 || z>bz+1.08)continue;
                for(int dy=1;dy>=-3;dy--) {
                    var candidate=at(level,cursor.set(bx,originY+dy,bz),x,z,lava);
                    if(candidate==null)continue;
                    double distance=Math.abs(candidate.height-y)+4*Math.hypot(candidate.x-x,candidate.z-z);
                    if(distance<score){score=distance;best=candidate;}
                }
            }
            return best;
        }
    }

    /** Geometry-only water cross sections and a non-overlapping, three-pixel square-distance rim. */
    public static final class WaterIntersectionGeometry {
        private WaterIntersectionGeometry() {}
        public static List<PlanarGeometry.Rect> section(List<Vec3[]> mesh, double height, double dx, double dz,
                                                             double left, double bottom, double right, double top) {
            var section = ModelSection.horizontal(height,dx,dz);
            for (var quad : mesh) section.quad(quad);
            return section.rectangles(left,bottom,right,top);
        }
        public static float alpha(List<PlanarGeometry.Rect> sections, double x, double z) {
            double distance=Double.POSITIVE_INFINITY;
            for (var rect:sections) distance=Math.min(distance,PlanarGeometry.distance(rect,x,z));
            // Move the whole gradient inward one water texel. The inner row belongs
            // only to the union's exposed boundary, never a join between rectangles.
            if (distance<=0) {
                boolean boundary=false;
                for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
                    if (dx==0 && dz==0) continue;
                    boolean occupied=false;
                    for (var rect:sections) if (rect.contains(x+dx/16.0,z+dz/16.0)) {
                        occupied=true;
                        break;
                    }
                    if (!occupied) boundary=true;
                }
                if (!boundary) return 0;
            }
            distance+=1.0/16;
            if (distance<=0 || distance>=3.0/16) return 0;
            return switch ((int)Math.floor(distance*16)) { case 0 -> 0.72F; case 1 -> 0.34F; default -> 0.10F; };
        }
    }

    /** Terrain tint-alpha tags: 254 fully wet; 246..253 encode partial water heights. */
    public static final class SubmergedFace {
        private SubmergedFace() {}

        public static int tag(float minY, float maxY, int fluidY, float height) {
            float surface = fluidY + height;
            if (minY > surface + 0.0001F || maxY < fluidY) return 255;
            if (maxY <= surface + 0.0001F) return 254;
            // Partial vanilla block faces stay within the fluid's block. The shader
            // tests their fractional Y against the exact 1/9-block fluid height.
            // Do not tag oversized/custom faces whose block origin is ambiguous.
            if (minY < fluidY || maxY > fluidY + 1.0001F) return 255;
            return 245 + Math.clamp(Math.round(height * 9.0F), 1, 8);
        }
    }
}
