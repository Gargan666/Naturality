package naturality.api.geometry;

import naturality.blocks.snow.SnowGeometry;

import naturality.api.geometry.SurfaceSupportModels.SnowSupportModels;
import naturality.blocks.fire.FireGeometry.FireSurface;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Shared surface fitting, volume generation and mesh-scoped caches for layered coatings. */
@org.jspecify.annotations.NullMarked
public final class SurfaceLayers {
    public static final int MAX_DEPTH = 32;
    private SurfaceLayers() {}

    public static @Nullable BlockState footstepState(BlockGetter level,
            BlockPos supportPos, net.minecraft.world.phys.AABB feet, boolean snowEnabled) {
        for (int depth = 0; depth <= MAX_DEPTH; depth++) {
            BlockPos owner = supportPos.above(depth);
            if (level instanceof net.minecraft.world.level.Level world && !naturality.api.worlds.LoadedChunks.has(world,owner)) continue;
            var state = level.getBlockState(owner);
            if (!naturality.blocks.ash.Ash.isLayer(state) && !(snowEnabled && state.is(Blocks.SNOW))) continue;
            for (var box : shape(level,owner,state.getValue(SnowLayerBlock.LAYERS),state.getBlock()).toAabbs()) {
                double surfaceY = owner.getY() + box.maxY - 1.0 / 8.0;
                if (Math.abs(feet.minY - surfaceY) > 0.08) continue;
                if (feet.maxX > owner.getX()+box.minX && feet.minX < owner.getX()+box.maxX
                        && feet.maxZ > owner.getZ()+box.minZ && feet.minZ < owner.getZ()+box.maxZ) return state;
            }
        }
        return null;
    }
    private static final float WATER_SURFACE = 7F / 8F;
    private static final List<FireSurface.Patch> FLAT = List.of(new FireSurface.Patch(0,0,0,1,0,0,1));
    private static final VoxelShape[] LAYERS = java.util.stream.IntStream.rangeClosed(0,8)
        .mapToObj(i -> i == 0 ? Shapes.empty() : Shapes.box(0,0,0,1,i/8.0,1)).toArray(VoxelShape[]::new);
    private static final ThreadLocal<java.util.Map<List<FireSurface.Patch>,VoxelShape[]>> VOLUMES =
        ThreadLocal.withInitial(java.util.HashMap::new);

    /** Ordinary snow needs no fitted copies, offsets, or world-dependent mesh. */
    public static boolean usesVanillaGeometry(BlockGetter level, BlockPos pos, Block material) {
        // Registry shape initialization runs before data-pack tags are bound and has no world support.
        if (level == net.minecraft.world.level.EmptyBlockGetter.INSTANCE) return true;
        var cache = level.getBlockState(pos).is(material) ? Cache.entry(level,pos) : null;
        if (cache != null && cache.vanilla != null) return cache.vanilla;
        boolean result = computeVanillaGeometry(level,pos,material);
        if (cache != null) cache.vanilla = result;
        return result;
    }
    private static boolean computeVanillaGeometry(BlockGetter level, BlockPos pos, Block material) {
        for(int depth=1;depth<=MAX_DEPTH;depth++) {
            var below=pos.below(depth);var support=level.getBlockState(below);
            if(material == Blocks.SNOW && SnowGeometry.NoSnowBlocks.contains(support))return false;
            if(support.getFluidState().is(FluidTags.WATER))return false;
            if(support.is(material)) {
                if(support.getValue(SnowLayerBlock.LAYERS)!=8)return false;
                continue;
            }
            if(support.isAir() || SnowGeometry.SnowSupportOnly.contains(support)
                    || support.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
                    || support.is(net.minecraft.tags.BlockTags.LEAVES) || SnowGeometry.isFoliage(support))return false;
            return SurfaceSupportModels.fullTop(support);
        }
        return false;
    }

    public static List<FireSurface.Patch> surfaces(BlockGetter level, BlockPos pos, Block material) {
        var cache = level.getBlockState(pos).is(material) ? Cache.entry(level,pos) : null;
        if (cache != null && cache.surfaces != null) return cache.surfaces;
        var result = usesVanillaGeometry(level,pos,material) ? FLAT : surfaces(level,pos,0,material);
        if (cache != null) cache.surfaces = result;
        return result;
    }
    private static List<FireSurface.Patch> surfaces(BlockGetter level, BlockPos pos, int stacked, Block material) {
        var tops = new ArrayList<FireSurface.Patch>();

        for (int depth = 1; depth <= MAX_DEPTH - stacked; depth++) {
            var supportPos = pos.below(depth);
            var support = level.getBlockState(supportPos);
            boolean waterlogged = support.getFluidState().is(FluidTags.WATER);
            if (material == Blocks.SNOW && SnowGeometry.NoSnowBlocks.contains(support)) break;
            if (support.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                    || support.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) break;
            if (support.is(material)) {
                // A full fitted layer carries its offsets into the next block;
                // resetting to y=0 would leave a half-block gap over bottom slabs.
                if (depth == 1 && support.getValue(SnowLayerBlock.LAYERS) == 8) {
                    if (stacked + 1 < MAX_DEPTH) {
                        var previous = surfaces(level,supportPos,stacked+1,material);
                        return previous.stream().map(p -> new FireSurface.Patch(p.x(),
                            p.y()+sliceLayers(previous,p,8)/8F-1,p.z(),p.ux(),p.uz(),p.vx(),p.vz())).toList();
                    }
                    else tops.add(new FireSurface.Patch(0,0,0,1,0,0,1));
                }
                break;
            }
            if (support.isAir()) break;
            // A vertical continuation hides this segment's upper surfaces.
            // Keep scanning through it so snow still reaches exposed ground.
            if (SnowGeometry.coveredByContinuation(level,supportPos)) {
                if (waterlogged) break;
                continue;
            }
            // Cactus has a solid, inset upper face. Coat that face while also
            // continuing to the exposed ground beneath the column. Other
            // foliage is walk-through and only receives ground snow.
            if (SnowGeometry.SnowSupportOnly.contains(support)) {
                if (waterlogged) break;
                continue;
            }
            if (SnowGeometry.isFoliage(support) && !(support.getBlock() instanceof net.minecraft.world.level.block.CactusBlock
                    && !level.getBlockState(supportPos.above()).is(support.getBlock()))) {
                if (waterlogged) break;
                continue;
            }
            var boxes = SnowSupportModels.boxes(level,supportPos);
            var cellTops = new ArrayList<FireSurface.Patch>();
            for (var b : boxes) {
                if (b.maxX <= b.minX || b.maxZ <= b.minZ
                        || (waterlogged && b.maxY <= WATER_SURFACE + 1e-5)) continue;
                cellTops.add(new FireSurface.Patch((float)b.minX, (float)b.maxY - depth, (float)b.minZ,
                    (float)(b.maxX-b.minX), 0, 0, (float)(b.maxZ-b.minZ)));
            }
            tops.addAll(cellTops.size() <= 1 ? cellTops : FireSurface.exposed(cellTops));
            if (waterlogged) break;
            if (!SnowGeometry.exposesGround(level,supportPos)) break;
        }
        // Each partial support gets its own coating; a complete footprint ends
        // the column. Never subtract an upper post from a lower ground sheet.
        tops.removeIf(p -> p.ux()<=1F/16+1e-5 || p.vz()<=1F/16+1e-5);
        return List.copyOf(tops);
    }

    /** Raised small patches gain one physical layer for every two inventory layers. */
    public static int sliceLayers(List<FireSurface.Patch> patches, FireSurface.Patch patch, int layers) {
        if (patch.ux()*patch.vz()<.999F)
            for (var p : patches) if (p.y() < patch.y()-1e-5) return (layers+1)/2;
        return layers;
    }

    public static VoxelShape shape(BlockGetter level, BlockPos pos, int layers, Block material) {
        return shape(level,pos,layers,false,material);
    }
    public static VoxelShape collisionShape(BlockGetter level, BlockPos pos, int layers, Block material) {
        return shape(level,pos,layers,true,material);
    }
    /** The saved snow cell can be entirely empty when its visible layers are displaced below it. */
    public static boolean emptyOwnerCell(BlockGetter level, BlockPos pos, int layers, Block material) {
        return shape(level,pos,layers,material).toAabbs().stream()
            .noneMatch(box -> box.maxY > 1e-5 && box.minY < 1 - 1e-5);
    }

    private static VoxelShape shape(BlockGetter level, BlockPos pos, int layers, boolean collision, Block material) {
        if (layers <= 0 || collision && layers == 1) return Shapes.empty();
        var cache = level.getBlockState(pos).is(material) ? Cache.entry(level,pos) : null;
        int index = layers + (collision ? 9 : 0);
        var variants = cache == null ? null : cache.shapes;
        var cached = variants == null ? null : variants[index];
        if (cached != null) return cached;
        VoxelShape result = computeShape(level,pos,layers,collision,material);
        if (cache != null) {
            if (variants == null) cache.shapes = variants = new VoxelShape[18];
            variants[index] = result;
        }
        return result;
    }
    private static VoxelShape computeShape(BlockGetter level, BlockPos pos, int layers, boolean collision, Block material) {
        // Vanilla SnowLayerBlock uses SHAPES[layers] for outline/support and
        // SHAPES[layers - 1] for collision.
        if (!ShapeRecursionGuard.enterSnow()) return vanillaShape(layers, collision);
        try {
        if (level.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)
            return vanillaShape(layers, collision);
        if (usesVanillaGeometry(level,pos,material)) return vanillaShape(layers,collision);
        VoxelShape shape = Shapes.empty();
        var patches = surfaces(level,pos,material);
        if(patches.isEmpty())return shape;
        // All world-dependent inputs have now been resolved into immutable patches.
        // Equal layouts share volumes even across different positions and worlds.
        var volumes=VOLUMES.get();
        var variants=volumes.get(patches);
        int variant=layers+(collision?9:0);
        if(variants!=null && variants[variant]!=null)return variants[variant];
        for (var p : patches) if (p.y() < -1e-5) { layers=Math.min(layers,7); break; }
        for (var p : patches) {
            int count=sliceLayers(patches,p,layers)-(collision?1:0);
            if(count<=0)continue;
            shape = Shapes.or(shape, Shapes.box(p.x(), p.y(), p.z(),
                p.x()+p.ux(), p.y()+count/8.0, p.z()+p.vz()));
        }
        shape=shape.optimize();
        if(variants==null) {
            if(volumes.size()>=512)volumes.clear();
            variants=new VoxelShape[18];volumes.put(patches,variants);
        }
        variants[variant]=shape;
        return shape;
        } finally {
            ShapeRecursionGuard.exitSnow();
        }
    }
    private static VoxelShape vanillaShape(int layers, boolean collision) {
        int height = layers - (collision ? 1 : 0);
        return LAYERS[Math.max(0,height)];
    }


    /** Bounded, thread-confined memoization, valid only during one mesh build/emission. */
    public static final class Cache {
        private static final ThreadLocal<@Nullable Scope> CURRENT = new ThreadLocal<>();
        private Cache() {}
        private static final class Scope {
            int depth = 1;
            @Nullable BlockGetter view;
            final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
        }
        static final class Entry {
            @Nullable Boolean vanilla;
            @Nullable List<FireSurface.Patch> surfaces;
            @Nullable VoxelShape @Nullable [] shapes;
        }
        public static void begin() {
            Scope scope = CURRENT.get();
            if (scope == null) CURRENT.set(new Scope()); else scope.depth++;
        }
        public static void end() {
            Scope scope = CURRENT.get();
            if (scope != null && --scope.depth == 0) CURRENT.remove();
        }
        static @Nullable Entry entry(BlockGetter view, BlockPos pos) {
            Scope scope = CURRENT.get();
            if (scope == null || ShapeRecursionGuard.fireActive()) return null;
            if (scope.view == null) scope.view = view;
            if (scope.view != view) return null;
            long key = pos.asLong();
            Entry entry = scope.entries.get(key);
            if (entry == null) {
                if (scope.entries.size() >= 8192) scope.entries.clear();
                entry = new Entry(); scope.entries.put(key, entry);
            }
            return entry;
        }
    }


}
