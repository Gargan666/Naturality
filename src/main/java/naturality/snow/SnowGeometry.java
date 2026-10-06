package naturality.snow;

import java.util.ArrayList;
import java.util.List;
import naturality.fire.FireSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;

/** One saved snow layer coats the upper envelope of its support and exposed ground. */
public final class SnowGeometry {
    public static final ThreadLocal<net.minecraft.world.level.ClipContext> PICK = new ThreadLocal<>();
    public static final int MAX_DEPTH = 32;
    private static final float WATER_SURFACE = 7F / 8F;
    private static final List<FireSurface.Patch> FLAT = List.of(new FireSurface.Patch(0,0,0,1,0,0,1));
    private static final VoxelShape[] LAYERS = java.util.stream.IntStream.rangeClosed(0,8)
        .mapToObj(i -> i == 0 ? Shapes.empty() : Shapes.box(0,0,0,1,i/8.0,1)).toArray(VoxelShape[]::new);
    // Keys are immutable shape objects, not block states or world positions.
    private static final ThreadLocal<java.util.IdentityHashMap<VoxelShape,Boolean>> FOOTPRINTS =
        ThreadLocal.withInitial(java.util.IdentityHashMap::new);
    private static final ThreadLocal<java.util.Map<List<FireSurface.Patch>,VoxelShape[]>> VOLUMES =
        ThreadLocal.withInitial(java.util.HashMap::new);
    public static boolean isFoliage(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.CactusBlock;
    }
    private SnowGeometry() { }

    /** Different materials still form one continuous post or wall. */
    public static boolean coveredByContinuation(BlockGetter level, BlockPos pos) {
        var block=level.getBlockState(pos).getBlock();
        var above=level.getBlockState(pos.above()).getBlock();
        return block==above
            || block instanceof net.minecraft.world.level.block.FenceBlock && above instanceof net.minecraft.world.level.block.FenceBlock
            || block instanceof net.minecraft.world.level.block.WallBlock && above instanceof net.minecraft.world.level.block.WallBlock
            || block instanceof net.minecraft.world.level.block.FenceGateBlock && above instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    public static int maxLayers(BlockGetter level, BlockPos pos) {
        for (var patch : surfaces(level,pos)) if (patch.y() < -1e-5) return 7;
        return 8;
    }

    /** Ordinary snow needs no fitted copies, offsets, or world-dependent mesh. */
    public static boolean usesVanillaGeometry(BlockGetter level, BlockPos pos) {
        var cache = SnowGeometryCache.entry(level,pos);
        if (cache != null && cache.vanilla != null) return cache.vanilla;
        boolean result = computeVanillaGeometry(level,pos);
        if (cache != null) cache.vanilla = result;
        return result;
    }
    private static boolean computeVanillaGeometry(BlockGetter level, BlockPos pos) {
        for(int depth=1;depth<=MAX_DEPTH;depth++) {
            var below=pos.below(depth);var support=level.getBlockState(below);
            if(NoSnowBlocks.contains(support))return false;
            if(support.getFluidState().is(FluidTags.WATER))return false;
            if(support.is(Blocks.SNOW)) {
                if(support.getValue(SnowLayerBlock.LAYERS)!=8)return false;
                continue;
            }
            if(support.isAir() || SnowSupportOnly.contains(support)
                    || support.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock
                    || support.is(net.minecraft.tags.BlockTags.LEAVES) || isFoliage(support))return false;
            return net.minecraft.world.level.block.Block.isShapeFullBlock(support.getShape(level,below));
        }
        return false;
    }

    public static List<FireSurface.Patch> surfaces(BlockGetter level, BlockPos pos) {
        var cache = SnowGeometryCache.entry(level,pos);
        if (cache != null && cache.surfaces != null) return cache.surfaces;
        var result = usesVanillaGeometry(level,pos) ? FLAT : surfaces(level,pos,0);
        if (cache != null) cache.surfaces = result;
        return result;
    }
    private static List<FireSurface.Patch> surfaces(BlockGetter level, BlockPos pos, int stacked) {
        var tops = new ArrayList<FireSurface.Patch>();

        for (int depth = 1; depth <= MAX_DEPTH - stacked; depth++) {
            var supportPos = pos.below(depth);
            var support = level.getBlockState(supportPos);
            boolean waterlogged = support.getFluidState().is(FluidTags.WATER);
            if (NoSnowBlocks.contains(support)) break;
            if (support.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                    || support.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) break;
            if (support.is(Blocks.SNOW)) {
                // A full fitted layer carries its offsets into the next block;
                // resetting to y=0 would leave a half-block gap over bottom slabs.
                if (depth == 1 && support.getValue(SnowLayerBlock.LAYERS) == 8) {
                    if (stacked + 1 < MAX_DEPTH) {
                        var previous = surfaces(level,supportPos,stacked+1);
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
            if (coveredByContinuation(level,supportPos)) {
                if (waterlogged) break;
                continue;
            }
            // Cactus has a solid, inset upper face. Coat that face while also
            // continuing to the exposed ground beneath the column. Other
            // foliage is walk-through and only receives ground snow.
            if (SnowSupportOnly.contains(support)) {
                if (waterlogged) break;
                continue;
            }
            if (isFoliage(support) && !(support.getBlock() instanceof net.minecraft.world.level.block.CactusBlock
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
            if (!exposesGround(level,supportPos)) break;
        }
        // Each partial support gets its own coating; a complete footprint ends
        // the column. Never subtract an upper post from a lower ground sheet.
        tops.removeIf(p -> p.ux()<=1F/16+1e-5 || p.vz()<=1F/16+1e-5);
        return List.copyOf(tops);
    }
    /** Whether some of the ground remains exposed through the support's footprint. */
    public static boolean exposesGround(BlockGetter level,BlockPos pos) {
        var state=level.getBlockState(pos);
        if(state.isAir())return false;
        if(state.getFluidState().is(FluidTags.WATER))return false;
        if(state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)return false;
        if(isFoliage(state) || SnowSupportOnly.contains(state))return true;
        var outline = state.getShape(level,pos);
        if (outline.isEmpty()) return false;
        var cache = FOOTPRINTS.get();
        var cached = cache.get(outline);
        if (cached != null) return cached;
        VoxelShape footprint=Shapes.empty();
        for(var box:outline.toAabbs())
            footprint=Shapes.or(footprint,Shapes.box(box.minX,0,box.minZ,box.maxX,1,box.maxZ));
        boolean result = !footprint.isEmpty() && Shapes.joinIsNotEmpty(Shapes.block(),footprint,
            net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        if (cache.size() >= 2048) cache.clear();
        cache.put(outline,result);
        return result;
    }

    /** Raised small patches gain one physical layer for every two inventory layers. */
    public static int sliceLayers(List<FireSurface.Patch> patches, FireSurface.Patch patch, int layers) {
        if (patch.ux()*patch.vz()<.999F)
            for (var p : patches) if (p.y() < patch.y()-1e-5) return (layers+1)/2;
        return layers;
    }

    public static VoxelShape shape(BlockGetter level, BlockPos pos, int layers) {
        return shape(level,pos,layers,false);
    }
    public static VoxelShape collisionShape(BlockGetter level, BlockPos pos, int layers) {
        return shape(level,pos,layers,true);
    }
    /** The saved snow cell can be entirely empty when its visible layers are displaced below it. */
    public static boolean emptyOwnerCell(BlockGetter level, BlockPos pos, int layers) {
        return shape(level,pos,layers).toAabbs().stream()
            .noneMatch(box -> box.maxY > 1e-5 && box.minY < 1 - 1e-5);
    }
    /** Snow may be saved above the block whose surface an entity is walking on. */
    public static boolean coversFoot(BlockGetter level, BlockPos supportPos, AABB feet) {
        for (int depth = 0; depth <= MAX_DEPTH; depth++) {
            BlockPos owner = supportPos.above(depth);
            var state = level.getBlockState(owner);
            if (!state.is(Blocks.SNOW)) continue;
            for (var box : shape(level, owner, state.getValue(SnowLayerBlock.LAYERS)).toAabbs()) {
                double surfaceY = owner.getY() + box.maxY - 1.0 / 8.0;
                if (Math.abs(feet.minY - surfaceY) > 0.08) continue;
                if (feet.maxX > owner.getX() + box.minX && feet.minX < owner.getX() + box.maxX
                        && feet.maxZ > owner.getZ() + box.minZ && feet.minZ < owner.getZ() + box.maxZ)
                    return true;
            }
        }
        return false;
    }
    private static VoxelShape shape(BlockGetter level, BlockPos pos, int layers, boolean collision) {
        if (layers <= 0 || collision && layers == 1) return Shapes.empty();
        var cache = SnowGeometryCache.entry(level,pos);
        int index = layers + (collision ? 9 : 0);
        var variants = cache == null ? null : cache.shapes;
        var cached = variants == null ? null : variants[index];
        if (cached != null) return cached;
        VoxelShape result = computeShape(level,pos,layers,collision);
        if (cache != null) {
            if (variants == null) cache.shapes = variants = new VoxelShape[18];
            variants[index] = result;
        }
        return result;
    }
    private static VoxelShape computeShape(BlockGetter level, BlockPos pos, int layers, boolean collision) {
        // Vanilla SnowLayerBlock uses SHAPES[layers] for outline/support and
        // SHAPES[layers - 1] for collision.
        if (!ShapeRecursionGuard.enterSnow()) return vanillaShape(layers, collision);
        try {
        if (level.getBlockState(pos.below()).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)
            return vanillaShape(layers, collision);
        if (usesVanillaGeometry(level,pos)) return vanillaShape(layers,collision);
        VoxelShape shape = Shapes.empty();
        var patches = surfaces(level,pos);
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

    public static boolean coveredGrass(BlockGetter level, BlockPos grass) {
        var above = level.getBlockState(grass.above());
        if (above.is(Blocks.SNOW) || above.is(Blocks.SNOW_BLOCK)) return true;
        for (int depth=2; depth<=MAX_DEPTH; depth++) {
            var owner = grass.above(depth);
            var state = level.getBlockState(owner);
            if (state.is(Blocks.SNOW)) {
                final int offset = depth;
                return surfaces(level,owner).stream().anyMatch(p -> Math.abs(p.y()+offset-1)<1e-5);
            }
            if (!exposesGround(level,owner)) break;
        }
        return false;
    }
    public static void refreshGrass(Level level, BlockPos changed) {
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            var pos = changed.below(depth);
            var state = level.getBlockState(pos);
            if (state.hasProperty(SnowyBlock.SNOWY)) {
                boolean snowy = coveredGrass(level, pos);
                if (snowy != state.getValue(SnowyBlock.SNOWY))
                    level.setBlock(pos, state.setValue(SnowyBlock.SNOWY, snowy), 3);
            }
            if (depth >= 2 && !exposesGround(level,pos)) break;
        }
    }
}










