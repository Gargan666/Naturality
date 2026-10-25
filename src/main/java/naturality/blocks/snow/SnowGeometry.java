package naturality.blocks.snow;




import java.util.List;
import naturality.blocks.fire.FireGeometry.FireSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.tags.FluidTags;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;


import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import naturality.Naturality;
import naturality.api.geometry.SurfaceSupportModels;
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
    public static final int MAX_DEPTH = naturality.api.geometry.SurfaceLayers.MAX_DEPTH;
    public static boolean usesVanillaGeometry(BlockGetter level, BlockPos pos) {
        return naturality.api.geometry.SurfaceLayers.usesVanillaGeometry(level, pos, Blocks.SNOW);
    }
    public static List<FireSurface.Patch> surfaces(BlockGetter level, BlockPos pos) {
        return naturality.api.geometry.SurfaceLayers.surfaces(level, pos, Blocks.SNOW);
    }
    public static int sliceLayers(List<FireSurface.Patch> patches, FireSurface.Patch patch, int layers) {
        return naturality.api.geometry.SurfaceLayers.sliceLayers(patches, patch, layers);
    }
    public static VoxelShape shape(BlockGetter level, BlockPos pos, int layers) {
        return naturality.api.geometry.SurfaceLayers.shape(level, pos, layers, Blocks.SNOW);
    }
    public static VoxelShape collisionShape(BlockGetter level, BlockPos pos, int layers) {
        return naturality.api.geometry.SurfaceLayers.collisionShape(level, pos, layers, Blocks.SNOW);
    }
    /** Add fitted layer geometry inside this collision cell, preserving the preceding provider's result. */
    public static VoxelShape fitCollision(VoxelShape result, BlockGetter level, BlockPos pos, BlockState state) {
        if (naturality.blocks.ash.Ash.isLayer(state))
            return naturality.api.geometry.SurfaceLayers.collisionShape(level,pos,state.getValue(SnowLayerBlock.LAYERS),state.getBlock());
        if (state.is(Blocks.SNOW) && GameplaySettings.snowWrapping(level))
            return collisionShape(level,pos,state.getValue(SnowLayerBlock.LAYERS));
        if (state.isAir()) return result;
        // Every displaced addition is clipped to the unit cell. A full cube already
        // contains it; the canonical cube also avoids the full-shape cache lookup.
        if(result==net.minecraft.world.phys.shapes.Shapes.block() || Block.isShapeFullBlock(result))return result;
        if(!naturality.api.geometry.SurfaceLayers.mayHaveDisplacedLayers(level,pos))return result;
        for (int above=1;above<=MAX_DEPTH;above++) {
            var owner=pos.above(above); var snow=level.getBlockState(owner);
            if (!naturality.blocks.ash.Ash.isLayer(snow) && !(snow.is(Blocks.SNOW) && GameplaySettings.snowWrapping(level))) {
                if(above>=2 && !exposesGround(level,owner))break;
                continue;
            }
            if (snow.getValue(SnowLayerBlock.LAYERS)==1) continue;
            if(naturality.api.geometry.SurfaceLayers.usesVanillaGeometry(level,owner,snow.getBlock()))continue;
            var displaced=naturality.api.geometry.SurfaceLayers.collisionShape(level,owner,snow.getValue(SnowLayerBlock.LAYERS),snow.getBlock()).move(0,above,0);
            result=net.minecraft.world.phys.shapes.Shapes.or(result,net.minecraft.world.phys.shapes.Shapes.join(
                displaced,net.minecraft.world.phys.shapes.Shapes.block(),net.minecraft.world.phys.shapes.BooleanOp.AND));
        }
        return result;
    }
    public static boolean emptyOwnerCell(BlockGetter level, BlockPos pos, int layers) {
        return naturality.api.geometry.SurfaceLayers.emptyOwnerCell(level, pos, layers, Blocks.SNOW);
    }
    public static final class SnowGeometryCache {
        private SnowGeometryCache() {}
        public static void begin() { naturality.api.geometry.SurfaceLayers.Cache.begin(); }
        public static void end() { naturality.api.geometry.SurfaceLayers.Cache.end(); }
    }
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

    /** Whether some of the ground remains exposed through the support's footprint. */
    public static boolean exposesGround(BlockGetter level,BlockPos pos) {
        var state=level.getBlockState(pos);
        if(state.isAir())return false;
        if(state.getFluidState().is(FluidTags.WATER))return false;
        if(state.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)return false;
        if(isFoliage(state) || SnowSupportOnly.contains(state))return true;
        return SurfaceSupportModels.exposesGround(state);
    }

    /** Snow may be saved above the block whose surface an entity is walking on. */
    public static boolean coversFoot(BlockGetter level, BlockPos supportPos, AABB feet) {
        var state = naturality.api.geometry.SurfaceLayers.footstepState(level,supportPos,feet,true);
        return state != null && state.is(Blocks.SNOW);
    }
    /** Footstep material follows the actual body and fitted snow volume, not its saved cell. */
    public static BlockState walkingParticleState(net.minecraft.world.entity.player.Player player, BlockState original) {
        var level = player.level();
        var body = naturality.systems.rotation.PlayerRotation.body(player);
        var bounds = body.bounds().inflate(1e-6);
        boolean fitted = GameplaySettings.snowWrapping(level);
        var from = BlockPos.containing(bounds.minX, bounds.minY - 1, bounds.minZ);
        var to = BlockPos.containing(bounds.maxX, bounds.maxY + MAX_DEPTH, bounds.maxZ);
        for (var pos : BlockPos.betweenClosed(from, to)) {
            if (!naturality.api.worlds.LoadedChunks.has(level, pos)) continue;
            var state = level.getBlockState(pos);
            if (!state.is(Blocks.SNOW) && !naturality.blocks.ash.Ash.isLayer(state)) continue;
            // Use the full layer hitbox: a single layer has no solid collision volume,
            // but the player's feet still clip into its visible snow.
            var volume = fitted || naturality.blocks.ash.Ash.isLayer(state) ? naturality.api.geometry.SurfaceLayers.shape(level,pos,state.getValue(SnowLayerBlock.LAYERS),state.getBlock())
                : state.getShape(level, pos);
            for (var box : volume.toAabbs()) {
                if (body.separation(box.move(pos)) <= 1e-6) return state;
            }
        }
        return original.is(Blocks.SNOW) || naturality.blocks.ash.Ash.isLayer(original) ? Blocks.AIR.defaultBlockState() : original;
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

    /** Server-owned foot traffic, counted by distance and landings rather than standing time. */
    public static final class SnowCompaction {
        private record Travel(ServerLevel level, Vec3 position, boolean grounded, double distance) {}
        private record Pressure(int layers, int hits, long time) {}
        private static final Map<MinecraftServer, Map<ServerPlayer, Travel>> PLAYERS = Collections.synchronizedMap(new WeakHashMap<>());
        private static final Map<ServerLevel, Map<BlockPos, Pressure>> PRESSURE = Collections.synchronizedMap(new WeakHashMap<>());
        private SnowCompaction() {}
        public static void initialize() {
            ServerLifecycleEvents.SERVER_STOPPED.register(SnowCompaction::clearServer);
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (!naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().snowCompaction) { clearServer(server); return; }
                for (var player : server.getPlayerList().getPlayers()) tick(player);
            });
        }
        private static void clearServer(MinecraftServer server) {
            PLAYERS.remove(server);
            for (var level : server.getAllLevels()) PRESSURE.remove(level);
        }
        private static void tick(ServerPlayer player) {
            var level = player.level();
            var players = PLAYERS.computeIfAbsent(level.getServer(), _ -> new WeakHashMap<>());
            var position = player.position();
            boolean grounded = player.onGround() && !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger();
            var old = players.get(player);
            double distance = 0;
            int hits = 0;
            if (old != null && old.level == level && position.distanceToSqr(old.position) < 16) {
                if (grounded && !old.grounded) hits = 2;
                else if (grounded) {
                    distance = old.distance + Math.hypot(position.x-old.position.x, position.z-old.position.z);
                    if (distance >= .8) { hits=1; distance %= .8; }
                }
            }
            players.put(player,new Travel(level,position,grounded,distance));
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
            if (!naturality.config.gameplay.GameplaySettings.NaturalityServerConfig.get().snowCompaction) {
                var server = level.getServer();
                if (server != null) clearServer(server);
                else PRESSURE.remove(level);
                return;
            }
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
                        // ScalableLux owns its own source data and deliberately
                        // removes the vanilla table. Still notify its light engine.
                        var sources = chunk.getSkyLightSources();
                        if (sources != null) sources.update(chunk,x,pos.getY(),z);
                        var lighting=level.getChunkSource().getLightEngine();
                        lighting.checkBlock(pos.immutable());
                        lighting.checkBlock(pos.below());
                    }
                }
            });
        }
        public static BlockState state(BlockGetter level, BlockPos pos, BlockState state) {
            return (naturality.blocks.ash.Ash.isLayer(state) || state.is(Blocks.SNOW) && naturality.config.gameplay.GameplaySettings.snowWrapping(level))
                && !naturality.api.geometry.SurfaceLayers.usesVanillaGeometry(level,pos,state.getBlock()) ? Blocks.AIR.defaultBlockState() : state;
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
