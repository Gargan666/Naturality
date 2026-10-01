package naturality.client.fire;

import java.util.function.Predicate;
import naturality.fire.FireGeometry;
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

/** Physical surface dimensions and UV domain stay independent of texture resolution. */
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
        if (!naturality.config.GameplaySettings.clientFireWrapping()) {
            // Keep vanilla geometry while supplying valid seeds to the independent fire-texture shader.
            emitter.pushTransform(quad -> {
                for (int i = 0; i < 4; i++) quad.color(i, ProceduralFire.seedColor(pos));
                return true;
            });
            try { wrapped.emitQuads(emitter, level, pos, state, random, cullTest); }
            finally { emitter.popTransform(); }
            return;
        }
        var floors = FireGeometry.floors(level, pos, state);
        if (floors.size() == 1 && vanillaFloor(floors.getFirst())) {
            float shift = floors.getFirst().y();
            int seed = ProceduralFire.seedColor(pos);
            emitter.pushTransform(quad -> {
                quad.translate(0, shift, 0);
                for (int i = 0; i < 4; i++) quad.color(i, seed);
                return true;
            });
            try { wrapped.emitQuads(emitter, level, pos, state, random, cullTest); }
            finally { emitter.popTransform(); }
            return;
        }
        var material = wrapped.particleMaterial(level, pos, state);
        int color = ProceduralFire.seedColor(pos);
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
            sheet(emitter, material, ConnectedFire.color(level, pos, state, side),
                side.bottomLeft(), side.bottomRight(), side.height());
        if (state.hasProperty(FireBlock.UP) && state.getValue(FireBlock.UP)) {
            for (var b : FireGeometry.supportBoxes(level, pos.above())) {
                double y = b.minY + 1 - FireGeometry.INSET;
                quad(emitter, material, color, new Vec3(b.minX, y, b.minZ), new Vec3(b.minX, y, b.maxZ),
                    new Vec3(b.maxX, y, b.maxZ), new Vec3(b.maxX, y, b.minZ));
            }
        }
    }
    private static boolean vanillaFloor(naturality.fire.FireSurface.Patch p) {
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
}
