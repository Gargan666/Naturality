package naturality.client.systems.weather.wind;
import naturality.client.systems.weather.framework.WeatherClient.WeatherSoundEnvironment;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import naturality.bootstrap.ContentRegistration.NaturalitySounds;

import com.mojang.blaze3d.systems.RenderSystem;
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
        if (state.getBlock() instanceof VegetationBlock && !state.is(Blocks.LILY_PAD)) return 241;
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
        if (tag(state) != 255 && !windExposed(level, pos)) return 255;
        if (connected(state)) {
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
    /** Test exposure at the foliage itself; player location must not control plants indoors. */
    public static boolean windExposed(net.minecraft.client.renderer.block.BlockAndTintGetter level,
            net.minecraft.core.BlockPos pos) {
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
        if (tag(state) != 241) return tag(state);
        if (state.getBlock() instanceof CropBlock) return vertexTag(state,.5F,y,.5F);
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) y += 1;
        return 201 + Math.clamp(Math.round(y * 19), 0, 38);
    }
    private static boolean framePrepared;
    private static final ByteBuffer frameData = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    public static void beginFrame() { framePrepared = false; naturality.client.rendering.culling.FogCulling.OcclusionCulling.beginFrame(); }
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
        if (uniform != null && frameData.getFloat(0) == x && frameData.getFloat(4) == z
                && frameData.getFloat(8) == time && frameData.getFloat(12) == strength) {
            framePrepared = true;
            return; // Paused frames can retain the exact same GPU data too.
        }
        var data = frameData.clear();
        data.putFloat(x).putFloat(z).putFloat(time).putFloat(strength).flip();
        if (uniform != null) uniform.close();
        uniform = RenderSystem.getDevice().createBuffer(() -> "Naturality wind", 128, data);
        framePrepared = true;
    }
    public static void close() { if (uniform != null) { uniform.close(); uniform = null; } }

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
            if(!state.getFluidState().isEmpty()) { wrapped.emitQuads(emitter,level,pos,state,random,cull); return; }
            var offset=state.getOffset(pos);
            emitter.pushTransform(quad -> {
                for(int i=0;i<4;i++) {
                    int color=quad.color(i);
                    if((color>>>24)!=255) continue; // Preserve fitted snow/plant tags.
                    int tag=WindRendering.vertexTag(level,pos,state,quad.x(i)+(float)offset.x,
                        quad.y(i)+(float)offset.y,quad.z(i)+(float)offset.z);
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
        public static void stop(Minecraft client) {
            var weak = WindAudio.weak;
            var strong = WindAudio.strong;
            if (weak != null) client.getSoundManager().stop(weak);
            if (strong != null) client.getSoundManager().stop(strong);
            WindAudio.weak = WindAudio.strong = null;
        }
        public static void update(net.minecraft.client.Minecraft client, naturality.systems.weather.framework.WeatherSystem.@org.jspecify.annotations.Nullable WeatherState state) {
            if (!naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()
                    || !naturality.config.live.NaturalityConfig.get().effects.windSounds || state == null) { stop(client); return; }
            var weak = WindAudio.weak;
            var strong = WindAudio.strong;
            // Keep silent loops alive so gradual crossfades do not repeatedly restart their clips.
            if (weak == null || !client.getSoundManager().isActive(weak)) {
                weak = new WindLoop(false); WindAudio.weak = weak; client.getSoundManager().play(weak);
            }
            if (strong == null || !client.getSoundManager().isActive(strong)) {
                strong = new WindLoop(true); WindAudio.strong = strong; client.getSoundManager().play(strong);
            }
        }
        private static final class WindLoop extends AbstractTickableSoundInstance {
            private final boolean strong;
            WindLoop(boolean strong) {
                super(strong ? NaturalitySounds.WIND_STRONG : NaturalitySounds.WIND_WEAK, SoundSource.WEATHER, RandomSource.create());
                this.strong = strong; looping = true; delay = 0; relative = true;
                attenuation = SoundInstance.Attenuation.NONE; volume = 0;
            }
            @Override public boolean canStartSilent() { return true; }
            @Override public void tick() {
                var client = Minecraft.getInstance();
                var s = client.level == null ? null : WeatherSystem.state(client.level);
                if (!naturality.config.startup.FeatureConfig.Feature.VISUAL_OVERHAUL.enabled()
                        || !naturality.config.live.NaturalityConfig.get().effects.windSounds || s == null) { stop(); return; }
                float exposure = WeatherSoundEnvironment.windExposure();
                if (!client.gameRenderer.mainCamera().getFluidInCamera().equals(net.minecraft.world.level.material.FogType.NONE)) exposure *= .15F;
                float target = (strong ? s.strongWindGain() : s.weakWindGain()) * exposure;
                volume += Math.clamp(target - volume, -.025F, .025F);
            }
        }
    }
}
