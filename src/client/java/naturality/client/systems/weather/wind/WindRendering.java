package naturality.client.systems.weather.wind;
import naturality.client.systems.weather.framework.WeatherClient.WeatherSoundEnvironment;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import naturality.bootstrap.ContentRegistration.NaturalitySounds;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import naturality.systems.weather.framework.WeatherSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import java.util.function.Predicate;
import net.minecraft.client.particle.Particle;

@org.jspecify.annotations.NullMarked
public final class WindRendering {
    public static final BindGroupLayout LAYOUT = BindGroupLayout.builder().withUniform("NaturalityWind", UniformType.UNIFORM_BUFFER).build();
    private static @org.jspecify.annotations.Nullable GpuBuffer uniform;
    private static net.minecraft.client.renderer.@org.jspecify.annotations.Nullable MappableRingBuffer uniformRing;
    private static final ThreadLocal<ExposureCache> EXPOSURE_CACHE = ThreadLocal.withInitial(ExposureCache::new);
    private WindRendering() {}
    public static void clearExposureCache() {
        var cache = EXPOSURE_CACHE.get();
        cache.values.clear();
        cache.view = null;
    }
    /** Reserved tint-alpha markers, below the water occupancy range 246..254. */
    public static int tag(BlockState state) {
        if (connected(state)) return 100;
        if (state.is(BlockTags.LEAVES)) return 240;
        if (state.getBlock() instanceof VegetationBlock && !(state.getBlock() instanceof net.minecraft.world.level.block.LilyPadBlock)) return 241;
        return 255;
    }
    private static boolean connected(BlockState state) {
        return state.getBlock() instanceof SugarCaneBlock || state.getBlock() instanceof VineBlock
            || state.is(Blocks.PALE_HANGING_MOSS)
            || state.getBlock() instanceof GrowingPlantBlock;
    }
    /** Connected segments sample one continuous world-space field at their shared edges. */
    public static int vertexTag(net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
            BlockState state, float x, float y, float z) {
        if (tag(state) != 255 && (!state.getFluidState().isEmpty() || !windExposed(level, pos)))
            return brushable(state) ? state.getFluidState().isEmpty() ? 243 : 244 : 255;
        if (connected(state)) {
            if (state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT)) {
                int height = 0;
                while (height < 7) {
                    var below = level.getBlockState(pos.below(height + 1));
                    if (!below.is(Blocks.TWISTING_VINES) && !below.is(Blocks.TWISTING_VINES_PLANT)) break;
                    height++;
                }
                // The lower edge is pinned; both sides of each join encode the same height.
                return 201 + Math.clamp(height + Math.round(y), 0, 7);
            }
            if (hangingFamily(state)!=0) {
                int depth=0;
                int family=hangingFamily(state);
                while(depth<7 && hangingFamily(level.getBlockState(pos.above(depth+1)))==family) depth++;
                var support=pos.above(depth+1);
                boolean leaf=level.getBlockState(support).is(BlockTags.LEAVES) && windExposed(level,support);
                int distance=Math.clamp(depth+Math.round(1-y),0,7);
                // The leaf contribution reaches zero at seven blocks, so long
                // strands can continue through bounded render snapshots seamlessly.
                return leaf ? distance==0 ? 118 : 232+distance : 193+distance;
            }
            if (state.getBlock() instanceof VineBlock) {
                // Pin only the upper edge of an attached strand's first block.
                // Lower joins keep the ordinary shared wind field on both sides.
                if (y >= .999F && !level.getBlockState(pos.above()).is(state.getBlock())) {
                    boolean attached=false, movingLeaf=false;
                    if(state.getValue(VineBlock.UP)) {
                        var above=level.getBlockState(pos.above());
                        if(above.is(BlockTags.LEAVES)) movingLeaf=windExposed(level,pos.above());
                        attached=!above.is(BlockTags.LEAVES) || !movingLeaf;
                    }
                    for (var face : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                        if (!state.getValue(VineBlock.getPropertyForFace(face))) continue;
                        var supportPos=pos.relative(face);
                        var support=level.getBlockState(supportPos);
                        if(support.is(BlockTags.LEAVES)) {
                            boolean moving=windExposed(level,supportPos);
                            movingLeaf |= moving;
                            attached |= !moving;
                        } else attached |= support.isFaceSturdy(level,supportPos,face.getOpposite());
                    }
                    if (attached) return 101;
                    if (movingLeaf) return 232;
                }
                int mask = solidVineSupportMask(level,pos,state);
                // All cells touching a shared vertex contribute the same constraints,
                // keeping vertical joins and wall/corner joins connected.
                for (int dx = x < .001F ? -1 : 0; dx <= (x > .999F ? 1 : 0); dx++)
                    for (int dy = y < .001F ? -1 : 0; dy <= (y > .999F ? 1 : 0); dy++)
                        for (int dz = z < .001F ? -1 : 0; dz <= (z > .999F ? 1 : 0); dz++)
                            if (dx != 0 || dy != 0 || dz != 0)
                                mask |= solidVineSupportMask(level,pos.offset(dx,dy,dz),level.getBlockState(pos.offset(dx,dy,dz)));
                if(mask!=0)return 102+mask;
                // Free strands start at the bottom of the last wall-supported
                // block, or at the top of a leaf/ceiling-attached root block.
                int depth=0;
                var root=pos;
                while(depth<7) {
                    var above=root.above();
                    var aboveState=level.getBlockState(above);
                    if(!(aboveState.getBlock() instanceof VineBlock)
                            || solidVineSupportMask(level,above,aboveState)!=0)break;
                    root=above;depth++;
                }
                var rootState=level.getBlockState(root);
                boolean movingLeaf=false;
                for(var face:net.minecraft.core.Direction.values()) {
                    if(face==net.minecraft.core.Direction.DOWN
                            || !(rootState.getBlock() instanceof VineBlock)
                            || !rootState.getValue(VineBlock.getPropertyForFace(face)))continue;
                    var support=root.relative(face);
                    movingLeaf |= level.getBlockState(support).is(BlockTags.LEAVES) && windExposed(level,support);
                }
                int distance=Math.clamp(depth+Math.round(1-y),0,7);
                return movingLeaf ? distance==0 ? 232 : 232+distance : 193+distance;
            }
            // Mark height above the shared stalk root, including identical
            // markers on both sides of each segment join.
            if (state.getBlock() instanceof SugarCaneBlock) {
                int depth=0;
                while(depth<62 && level.getBlockState(pos.below(depth+1)).is(state.getBlock())) depth++;
                return Math.clamp(depth+Math.round(y),0,63);
            }
            return 100;
        }
        return vertexTag(state,x,y,z);
    }
    /** Head and body states belong to the same hanging strand. */
    public static int hangingFamily(BlockState state) {
        if(state.is(Blocks.PALE_HANGING_MOSS))return 1;
        if(state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT))return 2;
        if(state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT))return 3;
        return 0;
    }
    public static boolean exposed(net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
        // Chunk render views may expose only their thread-safe lighting snapshot.
        return level.getBrightness(LightLayer.SKY, pos) > 0;
    }
    /** Read only immutable dimension identity from a render snapshot or its native light engine. */
    public interface WindWorldAccess {
        net.minecraft.world.level.LevelHeightAccessor naturality$windLevel();
    }
    private static boolean windEverywhere(BlockAndTintGetter view) {
        // Sodium's LevelSlice deliberately rejects getLightEngine(): its light
        // samples are copied arrays. Its optional bridge exposes dimension identity
        // without querying live terrain or bypassing the worker-owned snapshot.
        WindWorldAccess access = view instanceof WindWorldAccess snapshot ? snapshot
            : view.getLightEngine() instanceof WindWorldAccess engine ? engine : null;
        return access != null && access.naturality$windLevel() instanceof net.minecraft.world.level.Level world
            && world.dimension().equals(net.minecraft.world.level.Level.NETHER);
    }
    /** Test exposure at the foliage itself; Nether foliage bypasses all shelter rays. */
    public static boolean windExposed(net.minecraft.client.renderer.block.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos) {
        if (windEverywhere(level)) return true;
        var cache = EXPOSURE_CACHE.get();
        if (cache.view != level) {
            cache.view = level;
            cache.values.clear();
        }
        long key = pos.asLong();
        byte cached = cache.values.get(key);
        if (cached != 0) return cached == 1;
        boolean result = naturality.systems.weather.wind.WindPush.WindShapes.raw(() -> naturality.systems.weather.wind.WindPush.WindShelter.hasOpenSkyPath(level, pos, p -> level.getBrightness(LightLayer.SKY,p)));
        if (cache.values.size() >= 4096) cache.values.clear();
        cache.values.put(key, result ? (byte)1 : (byte)2);
        return result;
    }
    private static final class ExposureCache {
        private @org.jspecify.annotations.Nullable BlockAndTintGetter view;
        private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap values = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
    }
    public static int vineSupportMask(BlockState state) {
        if (!(state.getBlock() instanceof VineBlock)) return 0;
        return (state.getValue(VineBlock.WEST) ? 1 : 0)
            | (state.getValue(VineBlock.EAST) ? 2 : 0)
            | (state.getValue(VineBlock.NORTH) ? 4 : 0)
            | (state.getValue(VineBlock.SOUTH) ? 8 : 0);
    }
    private static int solidVineSupportMask(BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state) {
        int mask=vineSupportMask(state);
        for(var face:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var supportPos=pos.relative(face);
            var support=level.getBlockState(supportPos);
            if(support.is(BlockTags.LEAVES) || !support.isFaceSturdy(level,supportPos,face.getOpposite())) {
                int bit=switch(face) { case WEST -> 1; case EAST -> 2; case NORTH -> 4; case SOUTH -> 8; default -> 0; };
                mask &= ~bit;
            }
        }
        return mask;
    }
    /** Leaf vertices and their snow share one block-center phase, including boundary vertices. */
    public static int leafTag(float x,float y,float z) {
        return 160 + Math.clamp((int)Math.floor(x+.002F),0,1)
            + 2*Math.clamp((int)Math.floor(z+.002F),0,1)
            + 4*Math.clamp((int)Math.floor(y+.002F),0,7);
    }
    /** Coordinates include the state model offset, matching positions seen by the shader. */
    public static int vertexTag(BlockState state,float x,float y,float z) {
        if (state.is(BlockTags.LEAVES)) return leafTag(x,y,z);
        if(tag(state)==241) {
            // Crop geometry spans -1/16..15/16 above farmland. Encode which
            // integer cell each vertex occupies so every corner finds one root.
            if (state.getBlock() instanceof CropBlock) return 120
                + Math.clamp((int)Math.floor(x+.002F),0,1)
                + 2*Math.clamp((int)Math.floor(z+.002F),0,1)
                + 4*Math.clamp((int)Math.floor(y+.002F)+1,0,1);
            int upper=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    ==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? 1 : 0;
            return 64 + Math.clamp((int)Math.floor(x+.002F)+1,0,2)
                + 3*Math.clamp((int)Math.floor(z+.002F)+1,0,2)
                + 9*Math.clamp((int)Math.floor(y+.002F)+upper+1,0,3);
        }
        return vertexTag(state,y);
    }
    public static int vertexTag(BlockState state, float y) {
        // Use the same anchored encoding as model vertices; 201..208 belong to rising vines.
        return tag(state) == 241 ? vertexTag(state, .5F, y, .5F) : tag(state);
    }
    private static boolean framePrepared;
    private static final int MAX_BRUSHES = 32;
    private static final int MAX_CONTACT_ENTITIES = 64;
    private static final int MAX_CONTACT_BLOCK_QUERIES = 4096;
    private static final double ENTITY_CONTACT_RANGE = 48;
    private static final int MAX_BRUSH_NODES = 512;
    private static final int BRUSH_NODES_OFFSET = 48 + MAX_BRUSHES * 48;
    private static final int UNIFORM_BYTES = BRUSH_NODES_OFFSET + MAX_BRUSH_NODES * 16;
    private static final ByteBuffer frameData = ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
    private static final java.util.Map<BlockPos, BrushPose> brushPoses = new java.util.HashMap<>();
    private static net.minecraft.client.multiplayer.@org.jspecify.annotations.Nullable ClientLevel brushLevel;
    private static long brushFrameTime;

    /** A vertically connected strand with shared spring nodes at every block join. */
    private static final class BrushPose {
        private static final float STEP = 1F / 120;
        private static final float CONTACT_FORCE = .35F;
        private static final float MAX_BEND = .55F;
        private final BlockPos bottom;
        private final boolean hanging, underwater, pinBottom, pinTop;
        private final float[] x, y, z, velocityX, velocityZ;
        private float targetX, targetZ, contactHeight, remainder;
        private double contactDistance = Double.POSITIVE_INFINITY;

        private BrushPose(BlockPos bottom, int blocks, boolean hanging, boolean underwater, boolean cutBottom, boolean cutTop) {
            this.bottom = bottom;
            this.hanging = hanging;
            this.underwater = underwater;
            pinBottom = !hanging || cutBottom;
            pinTop = hanging || cutTop;
            x = new float[blocks + 1]; y = new float[blocks + 1]; z = new float[blocks + 1];
            velocityX = new float[blocks + 1]; velocityZ = new float[blocks + 1];
        }

        private boolean pinned(int node) { return node == 0 && pinBottom || node == x.length - 1 && pinTop; }

        private void advance(float seconds) {
            remainder += seconds;
            // Fixed simulation steps make damping and propagation independent of render rate.
            while (remainder + 1E-7F >= STEP) {
                remainder = Math.max(0, remainder - STEP);
                float tension = underwater ? 32 : 70;
                float restoring = underwater ? 7 : 20;
                float drag = underwater ? 5.5F : 3.5F;
                float force = underwater ? 48 : 100;
                for (int i = 0; i < x.length; i++) {
                    if (pinned(i)) continue;
                    int before = Math.max(0, i - 1), after = Math.min(x.length - 1, i + 1);
                    float contact = (float)Math.exp(-Math.abs(i - contactHeight) * (underwater ? .8F : 1.2F));
                    float accelerationX = tension * (x[before] + x[after] - 2 * x[i]) - restoring * x[i] + targetX * force * contact;
                    float accelerationZ = tension * (z[before] + z[after] - 2 * z[i]) - restoring * z[i] + targetZ * force * contact;
                    float damping = (float)Math.exp(-drag * STEP);
                    velocityX[i] = (velocityX[i] + accelerationX * STEP) * damping;
                    velocityZ[i] = (velocityZ[i] + accelerationZ * STEP) * damping;
                }
                for (int i = 0; i < x.length; i++) {
                    x[i] += velocityX[i] * STEP;
                    z[i] += velocityZ[i] * STEP;
                    float length = (float)Math.sqrt(x[i] * x[i] + z[i] * z[i]);
                    if (length > MAX_BEND) { x[i] *= MAX_BEND / length; z[i] *= MAX_BEND / length; }
                }
                // Each segment keeps its rest length; hanging strands lift and
                // buoyant upright strands dip as they bend instead of stretching.
                int root = hanging ? x.length - 1 : 0;
                int direction = hanging ? -1 : 1;
                y[root] = 0;
                for (int i = root + direction; i >= 0 && i < x.length; i += direction) {
                    int previous = i - direction;
                    float dx = x[i] - x[previous], dz = z[i] - z[previous];
                    float distance = (float)Math.sqrt(dx * dx + dz * dz);
                    if (distance > .55F && !pinned(i)) {
                        x[i] = x[previous] + dx * .55F / distance;
                        z[i] = z[previous] + dz * .55F / distance;
                        dx = x[i] - x[previous]; dz = z[i] - z[previous];
                    }
                    y[i] = y[previous] + direction * ((float)Math.sqrt(Math.max(0, 1 - dx * dx - dz * dz)) - 1);
                }
                // At a bounded continuation, end at the untouched join without
                // creating a vertical discontinuity in an exceptionally long strand.
                if (pinBottom && pinTop) {
                    float first = y[0], last = y[x.length - 1];
                    for (int i = 0; i < x.length; i++) {
                        float fraction = (float)i / (x.length - 1);
                        y[i] -= first * (1 - fraction) + last * fraction;
                    }
                }
            }
        }

        private boolean resting() {
            if (targetX != 0 || targetZ != 0) return false;
            for (int i = 0; i < x.length; i++)
                if (Math.abs(x[i]) + Math.abs(z[i]) + Math.abs(velocityX[i]) + Math.abs(velocityZ[i]) > .002F) return false;
            return true;
        }
    }

    private static boolean brushable(BlockState state) {
        return !state.is(BlockTags.LEAVES) && tag(state) != 255;
    }

    /** Head/body variants are one plant; neighboring columns are always independent. */
    private static boolean samePlant(BlockState first, BlockState second) {
        if (first.is(second.getBlock())) return true;
        if (hangingFamily(first) != 0) return hangingFamily(first) == hangingFamily(second);
        return first.is(Blocks.KELP) && second.is(Blocks.KELP_PLANT)
            || first.is(Blocks.KELP_PLANT) && second.is(Blocks.KELP)
            || first.is(Blocks.TWISTING_VINES) && second.is(Blocks.TWISTING_VINES_PLANT)
            || first.is(Blocks.TWISTING_VINES_PLANT) && second.is(Blocks.TWISTING_VINES);
    }

    private static boolean continues(net.minecraft.client.multiplayer.ClientLevel level, BlockPos pos, BlockState state) {
        if (!naturality.api.worlds.LoadedChunks.has(level, pos)) return false;
        var neighbor = level.getBlockState(pos);
        return samePlant(state, neighbor) && neighbor.getCollisionShape(level, pos).isEmpty();
    }

    /** Contact bodies use the same hitbox and nearest-contact force limit. */
    private static int applyBrushContacts(net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.world.entity.Entity entity, int budget) {
        int queries = 0;
        var box = entity.getBoundingBox();
        var visited = new java.util.HashSet<BlockPos>();
        for (var cursor : BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX), net.minecraft.util.Mth.floor(box.minY),
                net.minecraft.util.Mth.floor(box.minZ), net.minecraft.util.Mth.floor(box.maxX),
                net.minecraft.util.Mth.floor(box.maxY), net.minecraft.util.Mth.floor(box.maxZ))) {
            if (queries++ >= budget) break;
            var pos = cursor.immutable();
            if (visited.contains(pos) || !naturality.api.worlds.LoadedChunks.has(level, pos)) continue;
            var state = level.getBlockState(pos);
            if (!brushable(state) || !state.getCollisionShape(level, pos).isEmpty()) continue;
            var shape = naturality.systems.weather.wind.WindPush.WindShapes.raw(() -> state.getShape(level, pos));
            if (shape.isEmpty() || !shape.bounds().move(pos).intersects(box)) continue;
            var bottom = pos;
            var top = pos;
            while (pos.getY() - bottom.getY() < 64 && continues(level, bottom.below(), state)) bottom = bottom.below();
            while (top.getY() - bottom.getY() < 127 && continues(level, top.above(), state)) top = top.above();
            for (int y = bottom.getY(); y <= top.getY(); y++) visited.add(new BlockPos(pos.getX(), y, pos.getZ()));
            boolean hanging = hangingFamily(state) != 0 || state.getBlock() instanceof VineBlock;
            boolean underwater = !state.getFluidState().isEmpty();
            boolean cutBottom = continues(level, bottom.below(), state);
            boolean cutTop = continues(level, top.above(), state);
            int blocks = top.getY() - bottom.getY() + 1;
            var pose = brushPoses.get(bottom);
            if (pose == null && brushPoses.size() >= MAX_BRUSHES) continue;
            if (pose == null || pose.x.length != blocks + 1 || pose.hanging != hanging || pose.underwater != underwater
                    || pose.pinBottom != (!hanging || cutBottom) || pose.pinTop != (hanging || cutTop)) {
                pose = new BrushPose(bottom, blocks, hanging, underwater, cutBottom, cutTop);
                brushPoses.put(bottom, pose);
            }
            double dx = pos.getX() + .5 - box.getCenter().x;
            double dz = pos.getZ() + .5 - box.getCenter().z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > pose.contactDistance) continue;
            pose.contactDistance = length;
            if (length < .001) {
                var forward = entity.getLookAngle();
                dx = forward.x; dz = forward.z; length = Math.sqrt(dx * dx + dz * dz);
                if (length < .001) { dx = 1; dz = 0; length = 1; }
            }
            pose.targetX = (float)(dx / length) * BrushPose.CONTACT_FORCE;
            pose.targetZ = (float)(dz / length) * BrushPose.CONTACT_FORCE;
            pose.contactHeight = (float)Math.clamp(box.getCenter().y - bottom.getY(), .1, blocks - .1);
        }
        return Math.min(queries, budget);
    }

    /** Render-thread contact queries follow only the touched plant's vertical column. */
    private static int writeBrushes(ByteBuffer data, Minecraft client) {
        var level = client.level;
        var player = client.player;
        if (brushLevel != level) { brushPoses.clear(); brushLevel = level; brushFrameTime = 0; }
        if (level == null || player == null || !naturality.config.live.NaturalityConfig.get().effects.foliageWind) {
            brushPoses.clear(); return 0;
        }
        long now = System.nanoTime();
        float seconds = brushFrameTime == 0 ? 0 : Math.clamp((now - brushFrameTime) / 1_000_000_000F, 0F, .1F);
        brushFrameTime = now;
        for (var pose : brushPoses.values()) {
            pose.targetX = 0; pose.targetZ = 0; pose.contactDistance = Double.POSITIVE_INFINITY;
        }
        int budget = MAX_CONTACT_BLOCK_QUERIES;
        if (!player.isSpectator() && !player.isRemoved())
            budget -= applyBrushContacts(level, player, Math.min(512, budget));
        var entities = level.getEntities(player, player.getBoundingBox().inflate(ENTITY_CONTACT_RANGE),
            // Harvest drops spawn inside the plant; they must not create a new brush impulse.
            entity -> !(entity instanceof net.minecraft.world.entity.item.ItemEntity)
                && !entity.isRemoved() && !entity.isSpectator() && entity.getBbWidth() > 0 && entity.getBbHeight() > 0);
        entities.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        int considered = 0;
        for (var entity : entities) {
            if (budget <= 0 || considered++ >= MAX_CONTACT_ENTITIES) break;
            budget -= applyBrushContacts(level, entity, Math.min(512, budget));
        }
        var anchor = player.blockPosition();
        data.putInt(36, anchor.getX()).putInt(40, anchor.getY()).putInt(44, anchor.getZ());
        int count = 0, nodeCount = 0;
        var iterator = brushPoses.values().iterator();
        while (iterator.hasNext()) {
            var pose = iterator.next();
            pose.advance(client.isPaused() ? 0 : seconds);
            if (pose.resting()) { iterator.remove(); continue; }
            if (nodeCount + pose.x.length > MAX_BRUSH_NODES) continue;
            int firstNode = nodeCount;
            for (int i = 0; i < pose.x.length; i++) {
                int offset = BRUSH_NODES_OFFSET + nodeCount++ * 16;
                data.putFloat(offset, pose.x[i]).putFloat(offset + 4, pose.y[i]).putFloat(offset + 8, pose.z[i]);
            }
            data.putFloat(pose.bottom.getX() - anchor.getX()).putFloat(pose.bottom.getY() - anchor.getY())
                .putFloat(pose.bottom.getZ() - anchor.getZ()).putFloat(firstNode);
            data.putFloat(pose.bottom.getX() - anchor.getX() + 1).putFloat(pose.bottom.getY() - anchor.getY() + pose.x.length - 1)
                .putFloat(pose.bottom.getZ() - anchor.getZ() + 1).putFloat(pose.x.length);
            data.putFloat(pose.hanging ? 1 : 0).putFloat(0).putFloat(0).putFloat(0);
            count++;
        }
        return count;
    }
    public static void beginFrame() {
        framePrepared = false;
        // Buffer rotation inserts a GPU fence, which must precede the terrain render pass.
        prepareFrame();
        naturality.client.rendering.culling.FogCulling.OcclusionCulling.beginFrame();
    }
    public static void bind(RenderPass pass) {
        if (!framePrepared || uniform == null) prepareFrame();
        pass.setUniform("NaturalityWind", java.util.Objects.requireNonNull(uniform));
    }
    private static void prepareFrame() {
        var client = Minecraft.getInstance();
        var level = client.level;
        var s = !naturality.config.live.NaturalityConfig.get().effects.foliageWind || level == null ? null : WeatherSystem.state(level);
        float windScale = s == null ? 0 : 1;
        float time = level == null ? 0 : (level.getGameTime() % 24000
            + client.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20F;
        float x = s == null ? 0 : s.windX() * windScale;
        float z = s == null ? 0 : s.windZ() * windScale;
        float strength = s == null ? 0 : s.wind() / 100 * windScale;
        var data = frameData.clear();
        while (data.hasRemaining()) data.putInt(0);
        data.clear();
        data.putFloat(x).putFloat(z).putFloat(time).putFloat(strength);
        float magnitude=(float)Math.sqrt(x*x+z*z);
        float severe=s == null ? 0 : s.severeWindBlend();
        data.putFloat(magnitude>.0001F?x/magnitude:0F).putFloat(magnitude>.0001F?z/magnitude:0F)
            .putFloat(severe).putFloat(magnitude);
        data.position(48);
        int brushes = writeBrushes(data, client);
        data.position(UNIFORM_BYTES);
        data.putInt(32, brushes);
        data.flip();
        var ring=uniformRing;
        if(ring==null) {ring=new net.minecraft.client.renderer.MappableRingBuffer(() -> "Naturality wind",130,UNIFORM_BYTES);uniformRing=ring;}
        else ring.rotate();
        var buffer=ring.currentBuffer();
        try(var mapped=buffer.map(false,true)) {mapped.data().put(data);}
        uniform=buffer;
        framePrepared = true;
    }
    public static void close() {
        if(uniformRing!=null) {uniformRing.close();uniformRing=null;}
        uniform=null;framePrepared=false;
        brushPoses.clear();brushLevel=null;brushFrameTime=0;
    }

    /** Supply wind tags through FRAPI, including renderers which bypass Indigo. */
    public static final class FoliageWindModel extends WrapperBlockStateModel {
        private FoliageWindModel(BlockStateModel model) { super(model); }
        public static void initialize() {
            ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
                // Leaf tags may not yet be bound during the initial model bake.
                ModelModifier.WRAP_LAST_PHASE,(model,bake) -> !(bake.state().getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
                    && WindRendering.tag(bake.state())==255
                    ? model : new FoliageWindModel(model)));
        }
        @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random) {
            // Exposure and plant attachment are position-dependent.
            return null;
        }
        @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter level,BlockPos pos,BlockState state,
                RandomSource random,Predicate<@org.jspecify.annotations.Nullable Direction> cull) {
            // Render views (including Sodium's LevelSlice) can be reused after edits.
            // Cache across vertices of this emission, never across snapshot revisions.
            WindRendering.clearExposureCache();
            var offset=state.getOffset(pos);
            emitter.pushTransform(quad -> {
                for(int i=0;i<4;i++) {
                    int color=quad.color(i);
                    if((color>>>24)!=255) continue; // Preserve fitted snow/plant tags.
                    float x=quad.x(i)+(float)offset.x,y=quad.y(i)+(float)offset.y,z=quad.z(i)+(float)offset.z;
                    int tag=WindRendering.vertexTag(level,pos,state,x,y,z);
                    quad.color(i,(color&0xFFFFFF)|(tag<<24));
                }
                return true;
            });
            try { wrapped.emitQuads(emitter,level,pos,state,random,cull); }
            finally { emitter.popTransform(); }
        }
    }

    /** Associates particle-type tag decisions with live client particles. */
    public static final class WindParticleControl {
        public interface Access {
            void naturality$applyWind();
            boolean naturality$windImmune();
            void naturality$windImmune(boolean value);
        }
        public interface LeafAccess {
            boolean naturality$isLandedLeaf();
        }
        private WindParticleControl() {}
        public static void mark(@org.jspecify.annotations.Nullable Particle particle, boolean immune) {
            if (particle instanceof Access access && immune) access.naturality$windImmune(true);
        }
        public static boolean immune(Particle particle) { return particle instanceof Access access && access.naturality$windImmune(); }
    }

    public static final class WindAudio {
        private static @org.jspecify.annotations.Nullable WindLoop weak, strong;
        private static boolean underwater(Minecraft client) {
            return client.gameRenderer.mainCamera().getFluidInCamera()
                == net.minecraft.world.level.material.FogType.WATER;
        }
        public static void stop(Minecraft client) {
            var weak = WindAudio.weak;
            var strong = WindAudio.strong;
            if (weak != null) client.getSoundManager().stop(weak);
            if (strong != null) client.getSoundManager().stop(strong);
            WindAudio.weak = WindAudio.strong = null;
        }
        public static void update(net.minecraft.client.Minecraft client, naturality.systems.weather.framework.WeatherSystem.@org.jspecify.annotations.Nullable WeatherState state) {
            if (!naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()
                    || !naturality.config.live.NaturalityConfig.get().effects.windSounds || state == null
                    || underwater(client)) { stop(client); return; }
            var weak = WindAudio.weak;
            var strong = WindAudio.strong;
            // Keep silent loops alive so gradual crossfades do not repeatedly restart their clips.
            if (weak == null || !client.getSoundManager().isActive(weak)) {
                weak = new WindLoop(false, client.level != null && client.level.dimension().equals(net.minecraft.world.level.Level.NETHER)); WindAudio.weak = weak; client.getSoundManager().play(weak);
            }
            if (client.level != null && !client.level.dimension().equals(net.minecraft.world.level.Level.NETHER)
                    && (strong == null || !client.getSoundManager().isActive(strong))) {
                strong = new WindLoop(true, false); WindAudio.strong = strong; client.getSoundManager().play(strong);
            }
        }
        private static final class WindLoop extends AbstractTickableSoundInstance {
            private final boolean strong;
            WindLoop(boolean strong, boolean nether) {
                super(nether ? NaturalitySounds.WIND_NETHER : strong ? NaturalitySounds.WIND_STRONG : NaturalitySounds.WIND_WEAK, SoundSource.WEATHER, RandomSource.create());
                this.strong = strong; looping = true; delay = 0; relative = true;
                attenuation = SoundInstance.Attenuation.NONE; volume = 0;
            }
            @Override public boolean canStartSilent() { return true; }
            @Override public void tick() {
                var client = Minecraft.getInstance();
                var s = client.level == null ? null : WeatherSystem.state(client.level);
                if (!naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()
                        || !naturality.config.live.NaturalityConfig.get().effects.windSounds || s == null
                        || underwater(client)) { volume = 0; stop(); return; }
                float exposure = WeatherSoundEnvironment.windExposure();
                if (!client.gameRenderer.mainCamera().getFluidInCamera().equals(net.minecraft.world.level.material.FogType.NONE)) exposure *= .15F;
                float target = (strong ? s.strongWindGain() : s.weakWindGain()) * exposure;
                volume += Math.clamp(target - volume, -.025F, .025F);
            }
        }
    }
}
