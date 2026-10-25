package naturality.client.blocks.fire;

import java.util.function.Predicate;
import naturality.blocks.fire.FireGeometry;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.AABB;
import java.util.WeakHashMap;
import naturality.config.gameplay.GameplaySettings;
import naturality.api.worlds.LoadedChunks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;

/** Physical surface dimensions and UV domain stay independent of texture resolution. */
@org.jspecify.annotations.NullMarked
public final class SurfaceFireModel extends WrapperBlockStateModel {
    private SurfaceFireModel(BlockStateModel wrapped) { super(wrapped); }
    public static void initialize() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
            ModelModifier.WRAP_LAST_PHASE, (model, bake) -> bake.state().getBlock() instanceof BaseFireBlock
                ? new SurfaceFireModel(model) : model));
    }
    @Override public @org.jspecify.annotations.Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) { return null; }
    @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos,
            BlockState state, RandomSource random, Predicate<@org.jspecify.annotations.Nullable Direction> cullTest) {
        // Native GLSL decodes RGB as procedural seeds. Iris replaces that shader
        // and treats RGB as ordinary tint, so keep its existing fire sprite white.
        boolean packTerrain = naturality.client.api.rendering.ShaderPacks.terrainActive();
        int color = packTerrain ? -1 : ProceduralFire.seedColor(pos);
        if (!naturality.config.gameplay.GameplaySettings.clientFireWrapping()) {
            // Keep vanilla geometry with the selected shader's color convention.
            emitter.pushTransform(quad -> {
                for (int i = 0; i < 4; i++) quad.color(i, color);
                return true;
            });
            try { wrapped.emitQuads(emitter, level, pos, state, random, cullTest); }
            finally { emitter.popTransform(); }
            return;
        }
        var floors = FireGeometry.floors(level, pos, state);
        if (floors.size() == 1 && vanillaFloor(floors.getFirst())) {
            float shift = floors.getFirst().y();
            emitter.pushTransform(quad -> {
                quad.translate(0, shift, 0);
                for (int i = 0; i < 4; i++) quad.color(i, color);
                return true;
            });
            try { wrapped.emitQuads(emitter, level, pos, state, random, cullTest); }
            finally { emitter.popTransform(); }
            return;
        }
        var material = wrapped.particleMaterial(level, pos, state);
        for (var p : floors) {
            // Adjacent fires otherwise emit coincident, differently textured faces.
            float lo = 1F / 1024, hi = 1 - lo;
            Vec3 a = new Vec3(p.mapX(lo, lo), p.y(), p.mapZ(lo, lo));
            Vec3 b = new Vec3(p.mapX(hi, lo), p.y(), p.mapZ(hi, lo));
            Vec3 c = new Vec3(p.mapX(hi, hi), p.y(), p.mapZ(hi, hi));
            Vec3 d = new Vec3(p.mapX(lo, hi), p.y(), p.mapZ(lo, hi));
            double height = FireGeometry.floorHeight(p);
            sheet(emitter, material, color, a, b, height);
            sheet(emitter, material, color, b, c, height);
            sheet(emitter, material, color, c, d, height);
            sheet(emitter, material, color, d, a, height);
            sheet(emitter, material, color, a, c, height);
            sheet(emitter, material, color, b, d, height);
        }
        for (var side : FireGeometry.sides(level, pos, state))
            sheet(emitter, material, packTerrain ? -1 : ConnectedFire.color(level, pos, state, side),
                side.bottomLeft(), side.bottomRight(), side.height());
        if (state.hasProperty(FireBlock.UP) && state.getValue(FireBlock.UP)) {
            for (var b : FireGeometry.supportBoxes(level, pos.above())) {
                double y = b.minY + 1 - FireGeometry.INSET;
                quad(emitter, material, color, new Vec3(b.minX, y, b.minZ), new Vec3(b.minX, y, b.maxZ),
                    new Vec3(b.maxX, y, b.maxZ), new Vec3(b.maxX, y, b.minZ));
            }
        }
    }
    private static boolean vanillaFloor(naturality.blocks.fire.FireGeometry.FireSurface.Patch p) {
        return Math.abs(p.x()) < 1e-5 && Math.abs(p.z()) < 1e-5
            && Math.abs(p.ux() - 1) < 1e-5 && Math.abs(p.uz()) < 1e-5
            && Math.abs(p.vx()) < 1e-5 && Math.abs(p.vz() - 1) < 1e-5;
    }
    private static void sheet(QuadEmitter e, Material.Baked material, int color, Vec3 a, Vec3 b, double height) {
        quad(e, material, color, a.add(0, height, 0), a, b, b.add(0, height, 0));
    }
    private static void quad(QuadEmitter e, Material.Baked material, int color, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        Vec3[] points = {a, b, c, d};
        for (boolean reverse : new boolean[]{false, true}) {
            for (int i = 0; i < 4; i++) {
                int index = reverse ? 3 - i : i;
                Vec3 p = points[index];
                e.pos(i, (float) p.x, (float) p.y, (float) p.z);
                e.uv(i, index < 2 ? 0 : 1, index == 0 || index == 3 ? 0 : 1);
                e.color(i, color);
            }
            e.materialBake(material, MutableQuadView.BAKE_NORMALIZED);
            e.cullFace(null).shadeDirectionOverride(Direction.UP).emissive(true).ambientOcclusion(TriState.FALSE).emit();
        }
    }

    /** Bounded, one-neighbour surface matching. No component flood fills or extra quads. */
    public static final class ConnectedFire {
        private ConnectedFire() { }

        public static int color(BlockGetter level, BlockPos pos, BlockState state, FireGeometry.Sheet sheet) {
            if (!naturality.config.live.NaturalityConfig.get().effects.connectedFire) return ProceduralFire.seedColor(pos);
            var tangent = sheet.bottomRight().subtract(sheet.bottomLeft());
            // Rotated boards keep their single fitted sprite; do not join merely adjacent voxels.
            boolean alongX = Math.abs(tangent.z) < 1e-5;
            if (!alongX && Math.abs(tangent.x) > 1e-5) return ProceduralFire.seedColor(pos);
            if (Math.abs(tangent.length() - 1) > 1e-5 || Math.abs(sheet.height() - 1) > 1e-5)
                return ProceduralFire.seedColor(pos);
            Direction right = alongX ? Direction.EAST : Direction.SOUTH;
            Direction[] neighbours = {right.getOpposite(), right, Direction.UP, Direction.DOWN};
            int mask = 0;
            for (int i = 0; i < neighbours.length; i++) {
                Direction direction = neighbours[i];
                BlockPos otherPos = pos.relative(direction);
                var otherState = level.getBlockState(otherPos);
                if (otherState.getBlock() != state.getBlock()) continue;
                for (var other : FireGeometry.sides(level, otherPos, otherState)) {
                    if (other.normal().distanceToSqr(sheet.normal()) > 1e-8) continue;
                    // Matching local endpoints imply the translated rectangles share precisely one edge.
                    if (other.bottomLeft().distanceToSqr(sheet.bottomLeft()) < 1e-8
                            && other.bottomRight().distanceToSqr(sheet.bottomRight()) < 1e-8
                            && Math.abs(other.height() - sheet.height()) < 1e-5) {
                        mask |= 1 << i;
                        break;
                    }
                }
            }
            if (mask == 0) return ProceduralFire.seedColor(pos);
            int column = (alongX ? pos.getX() : pos.getZ()) & 31;
            int row = pos.getY() & 31;
            int plane = (alongX ? pos.getZ() : pos.getX()) & 31;
            return 0xFF000000 | mask << 20 | plane << 10 | row << 5 | column;
        }
    }

    public static final class EntityFireRenderer {
        public static final RenderType LAYER = ProceduralFire.EntityFireLayer.LAYER;

        private EntityFireRenderer() { }
        public static AABB envelope(AABB box) {
            double above = Math.clamp(box.getYsize() * 0.12, 0.15, 0.35);
            return new AABB(box.minX - 0.025, box.minY, box.minZ - 0.025,
                box.maxX + 0.025, box.maxY + above, box.maxZ + 0.025);
        }
        public static void emit(FlameFeatureRenderer.Submit submit, VertexConsumer buffer, TextureAtlasSprite sprite) {
            var state = submit.entityRenderState();
            var data = (EntityFireState) state;
            AABB actual = data.naturality$fireBox();
            if (actual == null) actual = new AABB(-state.boundingBoxWidth / 2, 0, -state.boundingBoxWidth / 2,
                state.boundingBoxWidth / 2, state.boundingBoxHeight, state.boundingBoxWidth / 2);
            AABB b = envelope(actual);
            double[][] corners = {{b.minX,b.minZ}, {b.maxX,b.minZ}, {b.maxX,b.maxZ}, {b.minX,b.maxZ}};
            for (int side = 0; side < 4; side++) {
                var a = corners[side]; var c = corners[(side + 1) % 4];
                int seed = 0xFF000000 | ((data.naturality$fireSeed() + side * 7919) & 0x0FFFFF);
                // The GUI pose scales world units into pixels. Preserve the original
                // sheet lattice in the otherwise unused full-bright light attribute.
                int width = (int)Math.clamp(Math.rint(Math.hypot(c[0]-a[0], c[1]-a[1])*16), 1, 128);
                int height = (int)Math.clamp(Math.rint(b.getYsize()*16), 1, 128);
                for (int v = 0; v < 4; v++) {
                    var p = v < 2 ? a : c;
                    boolean top = v == 0 || v == 3;
                    buffer.addVertex(submit.pose(), (float)p[0], (float)(top ? b.maxY : b.minY), (float)p[1])
                        .setUv(v < 2 ? sprite.getU0() : sprite.getU1(), top ? sprite.getV0() : sprite.getV1())
                        .setColor(seed).setLight(width | (height << 16));
                }
            }
        }
    }

    /** Keeps the fire appearance tied to the fire that ignited an entity until it is extinguished. */
    public static final class SoulFireTracker {
        private static final WeakHashMap<Entity, Boolean> SOUL_BURNING = new WeakHashMap<>();

        private SoulFireTracker() { }

        public static boolean isSoulBurning(Entity entity) {
            // Lava always replaces the remembered source, regardless of nearby fire.
            if (entity.isInLava()) {
                SOUL_BURNING.remove(entity);
                return false;
            }
            int fireContact = fireContact(entity);
            if (fireContact == 1) SOUL_BURNING.remove(entity);
            else if (fireContact == 2) SOUL_BURNING.put(entity, true);
            else if (!entity.isOnFire()) SOUL_BURNING.remove(entity);
            return SOUL_BURNING.containsKey(entity);
        }

        /** 0 = no fire, 1 = regular fire, 2 = soul fire. Regular fire wins if both touch. */
        private static int fireContact(Entity entity) {
            var box = entity.getBoundingBox().inflate(1.0E-4);
            // Fitted flames can extend into the cell below or beside their owner.
            var search = box.inflate(1);
            if (!mayContainFire(entity.level(), search)) return 0;
            boolean wrapping = GameplaySettings.fireWrapping(entity.level());
            boolean soulFire = false;
            for (var pos : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
                    BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
                if (!LoadedChunks.has(entity.level(), pos)) continue;
                var state = entity.level().getBlockState(pos);
                if (!(state.getBlock() instanceof BaseFireBlock)) continue;
                var shape = wrapping ? FireGeometry.shape(entity.level(), pos, state)
                    : state.getShape(entity.level(), pos);
                boolean touching = false;
                for (var contact : shape.toAabbs()) {
                    if (contact.move(pos.getX(), pos.getY(), pos.getZ()).intersects(box)) {
                        touching = true;
                        break;
                    }
                }
                if (!touching) continue;
                if (state.is(Blocks.SOUL_FIRE)) soulFire = true;
                else return 1;
            }
            return soulFire ? 2 : 0;
        }

        private static boolean mayContainFire(net.minecraft.world.level.Level level, AABB search) {
            int firstY = Math.max(net.minecraft.util.Mth.floor(search.minY) >> 4, level.getMinSectionY());
            int lastY = Math.min(net.minecraft.util.Mth.floor(search.maxY) >> 4, level.getMaxSectionY());
            for (int x = net.minecraft.util.Mth.floor(search.minX) >> 4; x <= net.minecraft.util.Mth.floor(search.maxX) >> 4; x++) {
                for (int z = net.minecraft.util.Mth.floor(search.minZ) >> 4; z <= net.minecraft.util.Mth.floor(search.maxZ) >> 4; z++) {
                    // Like LoadedChunks.has, this only reads an already loaded chunk.
                    // Unknown views retain the original per-block availability checks.
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) return true;
                    for (int y = firstY; y <= lastY; y++) {
                        var section = chunk.getSection(level.getSectionIndexFromSectionY(y));
                        if (!section.hasOnlyAir() && section.maybeHas(state -> state.getBlock() instanceof BaseFireBlock)) return true;
                    }
                }
            }
            // Inspect live palettes every time: contact before ignition and same-tick
            // block changes must still update the remembered source immediately.
            return false;
        }
    }

    public interface EntityFireState {
        @org.jspecify.annotations.Nullable AABB naturality$fireBox();
        int naturality$fireSeed();
        boolean naturality$soulFire();
        void naturality$fireData(AABB box, int seed, boolean soulFire);
    }

    public interface SoulFirePlayerState {
        boolean naturality$soulFireOverlay();
        void naturality$soulFireOverlay(boolean value);
    }
}
