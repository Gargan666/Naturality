package naturality.blocks.snow;

import naturality.blocks.snow.SnowBlockModels.SnowSupportModels;
import naturality.api.geometry.ShapeRecursionGuard;
import java.util.ArrayList;
import java.util.List;
import naturality.blocks.fire.FireGeometry.FireSurface;
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
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import naturality.Naturality;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import naturality.config.gameplay.GameplaySettings;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** One saved snow layer coats the upper envelope of its support and exposed ground. */
@org.jspecify.annotations.NullMarked
public final class SnowGeometry {
    public static final ThreadLocal<net.minecraft.world.level.ClipContext> PICK = new ThreadLocal<>();
    public static final int MAX_DEPTH = 32;
    private static final float WATER_SURFACE = 7F / 8F;
    private static final List<FireSurface.Patch> FLAT = List.of(new FireSurface.Patch(0,0,0,1,0,0,1));
    private static final VoxelShape[] LAYERS = java.util.stream.IntStream.rangeClosed(0,8)
        .mapToObj(i -> i == 0 ? Shapes.empty() : Shapes.box(0,0,0,1,i/8.0,1)).toArray(VoxelShape[]::new);
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
            return SnowBlockModels.fullTop(support);
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
        return SnowBlockModels.exposesGround(state);
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

    /** Bounded, thread-confined memoization, valid only during one mesh build/emission. */
    public static final class SnowGeometryCache {
        private static final ThreadLocal<@Nullable Scope> CURRENT = new ThreadLocal<>();
        private SnowGeometryCache() {}
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

    /** Server-owned foot traffic, counted by distance and landings rather than standing time. */
    public static final class SnowCompaction {
        private record Travel(ServerLevel level, Vec3 position, boolean grounded, double distance) {}
        private record Pressure(int layers, int hits, long time) {}
        private static final Map<ServerPlayer, Travel> PLAYERS = new WeakHashMap<>();
        private static final Map<ServerLevel, Map<BlockPos, Pressure>> PRESSURE = new WeakHashMap<>();
        private SnowCompaction() {}
        public static void initialize() {
            ServerLifecycleEvents.SERVER_STOPPED.register(_ -> { PLAYERS.clear(); PRESSURE.clear(); });
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (!naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().snowCompaction) { PLAYERS.clear(); PRESSURE.clear(); return; }
                for (var player : server.getPlayerList().getPlayers()) tick(player);
            });
        }
        private static void tick(ServerPlayer player) {
            var level = player.level();
            var position = player.position();
            boolean grounded = player.onGround() && !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger();
            var old = PLAYERS.get(player);
            double distance = 0;
            int hits = 0;
            if (old != null && old.level == level && position.distanceToSqr(old.position) < 16) {
                if (grounded && !old.grounded) hits = 2;
                else if (grounded) {
                    distance = old.distance + Math.hypot(position.x-old.position.x, position.z-old.position.z);
                    if (distance >= .8) { hits=1; distance %= .8; }
                }
            }
            PLAYERS.put(player,new Travel(level,position,grounded,distance));
            if (hits > 0) {
                var snow = contact(level,player.getBoundingBox());
                if (snow != null) press(level,snow,hits);
            }
        }
        /** Find the snow owner for the actual fitted collision surface beneath the feet. */
        public static @org.jspecify.annotations.Nullable BlockPos contact(ServerLevel level, AABB feet) {
            int base = (int)Math.floor(feet.minY)-1;
            var pos = new BlockPos.MutableBlockPos();
            for (int x=(int)Math.floor(feet.minX); x<=Math.floor(feet.maxX); x++)
                for (int z=(int)Math.floor(feet.minZ); z<=Math.floor(feet.maxZ); z++)
                    for (int offset=0; offset<=SnowGeometry.MAX_DEPTH+1; offset++) {
                        pos.set(x,base+offset,z);
                        if (!naturality.api.worlds.LoadedChunks.has(level, pos)) break;
                        var state = level.getBlockState(pos);
                        if (state.is(Blocks.SNOW)) {
                            int layers=state.getValue(SnowLayerBlock.LAYERS);
                            if (layers>1) for (var box : state.getCollisionShape(level,pos).toAabbs()) {
                                if (Math.abs(pos.getY()+box.maxY-feet.minY)<.08
                                        && x+box.maxX>feet.minX && x+box.minX<feet.maxX
                                        && z+box.maxZ>feet.minZ && z+box.minZ<feet.maxZ) return pos.immutable();
                            }
                            break;
                        }
                        if (offset>=2 && !SnowGeometry.exposesGround(level,pos)) break;
                    }
            return null;
        }
        public static void press(ServerLevel level, BlockPos pos, int hits) {
            if (!naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().snowCompaction) { PLAYERS.clear(); PRESSURE.clear(); return; }
            if (hits<=0 || !naturality.api.worlds.LoadedChunks.has(level, pos)) return;
            var state=level.getBlockState(pos);
            var history=PRESSURE.computeIfAbsent(level, _ -> new HashMap<>());
            if (!state.is(Blocks.SNOW) || state.getValue(SnowLayerBlock.LAYERS)<=1) { history.remove(pos); return; }
            int layers=state.getValue(SnowLayerBlock.LAYERS);
            long time=level.getGameTime();
            var old=history.get(pos);
            int total=hits+(old!=null && old.layers==layers && time-old.time<6000 ? old.hits : 0);
            if (total>=8) {
                history.remove(pos);
                level.setBlockAndUpdate(pos,state.setValue(SnowLayerBlock.LAYERS,layers-1));
            } else {
                if (history.size()>=8192) history.entrySet().removeIf(entry -> time-entry.getValue().time>=6000);
                if (history.size()>=8192) history.remove(history.keySet().iterator().next());
                history.put(pos.immutable(),new Pressure(layers,total,time));
            }
        }
    }

    /** The saved owner cell of fitted snow contains no light-blocking geometry. */
    public static final class SnowLighting {
        private SnowLighting() { }
        public static void initialize() {
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated) -> {
                if (generated) return;
                if (!naturality.config.gameplay.GameplaySettings.snowWrapping(level)) return;
                // Saved light arrays can still contain shadows from the old owner
                // cells. Recheck only fitted snow, not every block in snowy chunks.
                var pos=new BlockPos.MutableBlockPos();
                for (int sectionIndex=0;sectionIndex<chunk.getSectionsCount();sectionIndex++) {
                    var section=chunk.getSection(sectionIndex);
                    if (!section.maybeHas(s -> s.is(Blocks.SNOW))) continue;
                    int baseY=chunk.getSectionYFromSectionIndex(sectionIndex)*16;
                    for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                        var snow=section.getBlockState(x,y,z);
                        if (!snow.is(Blocks.SNOW)) continue;
                        pos.set(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                        if (state(chunk,pos,snow)==snow) continue;
                        // The skylight table indexes chunk-local X/Z, unlike the
                        // light-engine checks below which need world positions.
                        chunk.getSkyLightSources().update(chunk,x,pos.getY(),z);
                        var lighting=level.getChunkSource().getLightEngine();
                        lighting.checkBlock(pos.immutable());
                        lighting.checkBlock(pos.below());
                    }
                }
            });
        }
        public static BlockState state(BlockGetter level, BlockPos pos, BlockState state) {
            return state.is(Blocks.SNOW) && naturality.config.gameplay.GameplaySettings.snowWrapping(level)
                && !SnowGeometry.usesVanillaGeometry(level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }
    }

    /** Blocks that cannot be replaced by snow or support a snow coating. */
    public static final class NoSnowBlocks {
        public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, Naturality.id("nosnow"));

        private NoSnowBlocks() { }

        public static boolean contains(BlockState state) {
            return state.is(BLOCKS);
        }
    }

    /** Blocks that retain snow coverage but do not receive the side drip overlay. */
    public static final class NoSnowSideOverlay {
        public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, Naturality.id("no_snow_side_overlay"));

        private NoSnowSideOverlay() { }

        public static boolean contains(BlockState state) {
            return state.is(BLOCKS);
        }
    }

    /** Blocks snow passes through to their support without coating the block. */
    public static final class SnowSupportOnly {
        public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, Naturality.id("snow_support_only"));

        private SnowSupportOnly() { }

        public static boolean contains(BlockState state) {
            return state.is(BLOCKS) && (!(state.getBlock() instanceof CampfireBlock)
                || state.getValue(CampfireBlock.LIT));
        }
    }

    /** Preserve vegetation while giving fitted snow its own saved cell above it. */
    public static final class SnowWorldgen {
        private SnowWorldgen() {}
        public static void placeOnFoliage(WorldGenLevel level,BlockPos origin) {
            if(!GameplaySettings.snowWrapping(level))return;
            for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++) {
                int x=origin.getX()+dx,z=origin.getZ()+dz;
                var surface=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z),z);
                placeUnderCanopy(level,surface);
                // Solid roofs and trees keep their normal surface snow placement.
                var plant=level.getBlockState(surface);
                if(!SnowGeometry.isFoliage(plant))continue;
                var owner=surface;
                int height=0;
                while(height<SnowGeometry.MAX_DEPTH && level.isInsideBuildHeight(owner.getY())
                        && SnowGeometry.isFoliage(level.getBlockState(owner))) {
                    if(!level.getBlockState(owner).getFluidState().isEmpty())break;
                    owner=owner.above();height++;
                }
                if(height==0 || !level.isInsideBuildHeight(owner.getY()) || !level.getBlockState(owner).isAir())continue;
                if(!level.getBiome(owner).value().shouldSnow(level,owner))continue;
                if(!level.setBlock(owner,Blocks.SNOW.defaultBlockState(),2))continue;
                var ground=surface.below();var support=level.getBlockState(ground);
                if(support.hasProperty(SnowyBlock.SNOWY))level.setBlock(ground,support.setValue(SnowyBlock.SNOWY,true),2);
            }
        }

        /** Leaves admit generated snow; solid roofs stop the scan before sheltered surfaces. */
        private static void placeUnderCanopy(WorldGenLevel level,BlockPos surface) {
            boolean canopy=false;
            var cursor=surface.mutable();
            while(level.isInsideBuildHeight(cursor.getY())) {
                var state=level.getBlockState(cursor);
                if(state.is(BlockTags.LEAVES))canopy=true;
                else if(!state.isAir() && !state.is(Blocks.SNOW) && !state.is(BlockTags.LOGS)
                        && !SnowGeometry.isFoliage(state)) {
                    if(canopy)placeSnow(level,cursor.above());
                    break;
                }
                if(canopy && state.is(BlockTags.LEAVES))placeSnow(level,cursor.above());
                if(canopy && SnowGeometry.isFoliage(state)) {
                    var owner=cursor.above();
                    int depth=1;
                    while(depth<SnowGeometry.MAX_DEPTH && level.isInsideBuildHeight(owner.getY())
                            && SnowGeometry.isFoliage(level.getBlockState(owner))) {
                        owner=owner.above();depth++;
                    }
                    if(state.getFluidState().isEmpty())placeSnow(level,owner);
                }
                cursor.move(0,-1,0);
            }
        }

        private static void placeSnow(WorldGenLevel level,BlockPos owner) {
            if(!level.isInsideBuildHeight(owner.getY()) || !level.getBlockState(owner).isAir()
                    || !level.getBiome(owner).value().shouldSnow(level,owner))return;
            if(!level.setBlock(owner,Blocks.SNOW.defaultBlockState(),2))return;
            // Fitted plant snow can have its ground support several cells below its owner.
            var ground=owner.below();
            int depth=0;
            while(depth<SnowGeometry.MAX_DEPTH && SnowGeometry.isFoliage(level.getBlockState(ground))) {
                ground=ground.below();depth++;
            }
            var support=level.getBlockState(ground);
            if(support.hasProperty(SnowyBlock.SNOWY))level.setBlock(ground,support.setValue(SnowyBlock.SNOWY,true),2);
        }
    }
}
