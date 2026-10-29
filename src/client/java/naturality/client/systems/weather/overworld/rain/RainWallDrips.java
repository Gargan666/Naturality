package naturality.client.systems.weather.overworld.rain;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import naturality.api.worlds.LoadedChunks;
import naturality.Naturality;
import naturality.bootstrap.ContentRegistration.NaturalityParticles;
import naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Rain contact sampling and collision-shape-following wall rivulets. */
@org.jspecify.annotations.NullMarked
public final class RainWallDrips {
    private static final double INSET = 0.003;
    private static final TagKey<Block> SLIDE_SURFACES = TagKey.create(Registries.BLOCK, Naturality.id("rain_slide_surfaces"));
    private static final List<WallDrip> ACTIVE = new ArrayList<>();
    private RainWallDrips() {}

    public static void initialize() {
        ParticleProviderRegistry.getInstance().register(NaturalityParticles.DRIP_WALL, sprites ->
            (_, level, x, y, z, nx, _, nz, _) -> {
                var textures = sprites.getSprites();
                var particle = new WallDrip(level, x, y, z,
                    nx != 0 ? (nx > 0 ? Direction.EAST : Direction.WEST)
                        : (nz > 0 ? Direction.SOUTH : Direction.NORTH), textures.getFirst(), textures.get(1));
                ACTIVE.add(particle);
                return particle;
            });
    }

    public static void impact(ClientLevel level, Vec3 from, Vec3 to) {
        var weather = naturality.client.systems.weather.overworld.precipitation.ParticleWeather.weatherState(level);
        // Preserve the former one-in-ten contact chance at rain strength 20.
        float chance = weather == null ? 0 : Math.clamp(weather.rain() / 200F, 0, 1);
        if (chance <= 0 || level.getRandom().nextFloat() >= chance || !LoadedChunks.has(level, BlockPos.containing(from))
                || !LoadedChunks.has(level, BlockPos.containing(to))) return;
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection().getAxis() == Direction.Axis.Y) return;
        if (!level.getBlockState(hit.getBlockPos()).is(SLIDE_SURFACES)) return;
        var normal = hit.getDirection();
        var p = hit.getLocation().add(normal.getStepX() * INSET, 0, normal.getStepZ() * INSET);
        level.addParticle(NaturalityParticles.DRIP_WALL, p.x, p.y, p.z,
            normal.getStepX(), 0, normal.getStepZ());
    }

    public static void extract(ParticlesRenderState output, Frustum frustum, Camera camera, float partial) {
        var level = Minecraft.getInstance().level;
        ACTIVE.removeIf(p -> !p.isAlive() || p.stale(level));
        var state = new WallRenderState();
        for (var particle : ACTIVE) {
            if (frustum.isVisible(particle.getBoundingBox().inflate(1))) particle.extractWall(state, camera, partial);
        }
        if (!state.isEmpty()) output.add(state);
    }

    private record Face(double left, double bottom, double right, double top) {}

    private static final class WallDrip extends SingleQuadParticle {
        private static final Optional<ParticleLimit> LIMIT = Optional.of(new ParticleLimit(512));
        private static final int ACCELERATION_TICKS = 20;
        private final Direction normal;
        private final TextureAtlasSprite hangingSprite;
        private final double maxSpeed;
        private final float dripSize;
        private final double visibleTopOffset;
        private final double visibleBottomOffset;
        private final List<Face> faces = new ArrayList<>();
        private double edge = Double.NaN;
        private int hangingAge;
        private long lastTick;

        private WallDrip(ClientLevel level, double x, double y, double z, Direction normal,
                TextureAtlasSprite sprite, TextureAtlasSprite hangingSprite) {
            super(level, x, y, z, sprite);
            this.normal = normal;
            this.hangingSprite = hangingSprite;
            maxSpeed = 0.018 + random.nextDouble() * 0.022;
            // Choose the eventual drip size now and give both textures identical texel sizes.
            dripSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F) * 2;
            double pixel = 2 * dripSize / hangingSprite.contents().width();
            quadSize = (float) (pixel * sprite.contents().width() / 2);
            int first = sprite.contents().height(), last = -1;
            for (int row = 0; row < sprite.contents().height(); row++) {
                for (int column = 0; column < sprite.contents().width(); column++) {
                    if (!sprite.contents().isTransparent(0, column, row)) {
                        first = Math.min(first, row); last = Math.max(last, row);
                    }
                }
            }
            visibleTopOffset = quadSize - first * pixel;
            visibleBottomOffset = quadSize - (last + 1) * pixel;
            lifetime = 600;
            hasPhysics = false;
            alpha = 0;
            lastTick = level.getGameTime();
            updateFaces();
        }

        private double horizontal() { return x * normal.getStepZ() - z * normal.getStepX(); }

        private boolean stale(@org.jspecify.annotations.Nullable ClientLevel currentLevel) {
            return level != currentLevel || level.getGameTime() - lastTick > 10;
        }

        /** Cache actual coplanar collision faces; extraction clips geometry and UVs to these rectangles. */
        private void updateFaces() {
            faces.clear();
            double plane = normal.getAxis() == Direction.Axis.X
                ? x - normal.getStepX() * INSET : z - normal.getStepZ() * INSET;
            var bounds = new AABB(x - quadSize - 0.1, y - quadSize - 1, z - quadSize - 0.1,
                x + quadSize + 0.1, y + quadSize + 1, z + quadSize + 0.1);
            for (var pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
                if (!LoadedChunks.has(level, pos)) continue;
                for (var local : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                    var box = local.move(pos);
                    double facePlane = switch (normal) {
                        case EAST -> box.maxX; case WEST -> box.minX;
                        case SOUTH -> box.maxZ; default -> box.minZ;
                    };
                    if (Math.abs(facePlane - plane) > 0.0001) continue;
                    double a = box.minX * normal.getStepZ() - box.minZ * normal.getStepX();
                    double b = box.maxX * normal.getStepZ() - box.maxZ * normal.getStepX();
                    faces.add(new Face(Math.min(a, b), box.minY, Math.max(a, b), box.maxY));
                }
            }
            // Join adjacent faces vertically so ordinary block seams do not produce drips.
            double h = horizontal(), bottom = y, top = y;
            boolean supported = false;
            for (int pass = 0; pass <= faces.size(); pass++) {
                boolean changed = false;
                for (var face : faces) {
                    if (h < face.left || h > face.right || face.top < bottom - 0.0001 || face.bottom > top + 0.0001) continue;
                    supported = true;
                    double nextBottom = Math.min(bottom, face.bottom), nextTop = Math.max(top, face.top);
                    changed |= nextBottom != bottom || nextTop != top;
                    bottom = nextBottom; top = nextTop;
                }
                if (!changed) break;
            }
            if (Double.isNaN(edge) && supported && bottom > y - quadSize - 0.1) edge = bottom;
        }

        @Override public void tick() {
            xo = x; yo = y; zo = z;
            lastTick = level.getGameTime();
            if (++age >= lifetime || !LoadedChunks.has(level, BlockPos.containing(x, y, z))) { remove(); return; }
            updateFaces();
            double speed = maxSpeed * Math.min(1, (age - 1) / (double) ACCELERATION_TICKS);
            var from = new Vec3(x, y + visibleBottomOffset, z);
            var to = from.add(0, -speed, 0);
            if (!LoadedChunks.has(level, BlockPos.containing(to))) { remove(); return; }
            var floor = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (floor.getType() == HitResult.Type.BLOCK || !level.getFluidState(BlockPos.containing(to)).isEmpty()) {
                remove(); return;
            }
            if (Double.isNaN(edge) || y + visibleTopOffset > edge) setPos(x, y - speed, z);
            else if (++hangingAge >= 40) {
                var drip = Minecraft.getInstance().particleEngine.createParticle(
                    ParticleTypes.FALLING_WATER, x, edge - INSET - dripSize, z, 0, 0, 0);
                if (drip instanceof SingleQuadParticle quad) drip.scale(dripSize / quad.getQuadSize(0));
                remove(); return;
            }
            int tint = WeatherParticleContext.waterTint(level, BlockPos.containing(x, y, z));
            setColor(((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F);
            alpha = Math.min(1, age / 6F) * Math.min(1, (lifetime - age) / 10F);
        }

        private void extractWall(WallRenderState state, Camera camera, float partial) {
            double centerY = Mth.lerp(partial, yo, y), h = horizontal();
            int color = ARGB.colorFromFloat(alpha, rCol, gCol, bCol);
            int light = getLightCoords(partial);
            for (var face : faces) {
                double left = Math.max(h - quadSize, face.left), right = Math.min(h + quadSize, face.right);
                double bottom = Math.max(centerY - quadSize, face.bottom), top = Math.min(centerY + quadSize, face.top);
                if (left >= right || bottom >= top) continue;
                float u0 = sprite.getU((float) ((left - h + quadSize) / (2 * quadSize)));
                float u1 = sprite.getU((float) ((right - h + quadSize) / (2 * quadSize)));
                float v0 = sprite.getV((float) ((centerY + quadSize - top) / (2 * quadSize)));
                float v1 = sprite.getV((float) ((centerY + quadSize - bottom) / (2 * quadSize)));
                var eye = camera.position();
                state.rectangle(new Vec3(x + (left - h) * normal.getStepZ(), bottom, z - (left - h) * normal.getStepX()).subtract(eye),
                    new Vec3(x + (right - h) * normal.getStepZ(), bottom, z - (right - h) * normal.getStepX()).subtract(eye),
                    top - bottom, u0, u1, v0, v1, color, light);
            }
            if (Double.isNaN(edge)) return;
            float growth = (float) Math.clamp((edge - centerY - visibleBottomOffset)
                / (visibleTopOffset - visibleBottomOffset), 0, 1);
            if (growth <= 0) return;
            // Anchor the sprite's top to the underside while it grows downwards in place.
            float size = dripSize * growth;
            var eye = camera.position();
            var rotation = camera.rotation();
            var dripLayer = naturality.client.api.rendering.ShaderPacks.active() ? Layer.TRANSLUCENT
                : naturality.client.fluids.water.WaterShaders.WaterParticleTint.TRANSPARENT;
            state.add(dripLayer, (float) (x - eye.x), (float) (edge - INSET - size - eye.y),
                (float) (z - eye.z), rotation.x, rotation.y, rotation.z, rotation.w, size,
                hangingSprite.getU0(), hangingSprite.getU1(), hangingSprite.getV0(), hangingSprite.getV1(), color, light);
        }

        // The additional particle state supports rectangular UV clipping, unlike vanilla square cards.
        @Override public void extract(QuadParticleRenderState output, Camera camera, float partial) {}
        @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
        @Override public Optional<ParticleLimit> getParticleLimit() { return LIMIT; }
    }

    private static final class WallRenderState extends QuadParticleRenderState {
        private record Quad(Vec3 left, Vec3 right, double height, float u0, float u1, float v0, float v1, int color, int light) {}
        private final List<Quad> quads = new ArrayList<>();
        void rectangle(Vec3 left, Vec3 right, double height, float u0, float u1, float v0, float v1, int color, int light) {
            quads.add(new Quad(left, right, height, u0, u1, v0, v1, color, light));
        }
        @Override public boolean isEmpty() { return quads.isEmpty() && super.isEmpty(); }
        @Override public Set<SingleQuadParticle.Layer> layers() {
            var layers = new java.util.HashSet<>(super.layers());
            if (!quads.isEmpty()) layers.add(SingleQuadParticle.Layer.TRANSLUCENT);
            return layers;
        }
        @Override public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
            if (!isEmpty()) collector.submitQuadParticleGroup(this);
        }
        @Override public void clear() { quads.clear(); super.clear(); }
        @Override public void buildLayer(SingleQuadParticle.Layer layer, VertexConsumer buffer) {
            super.buildLayer(layer, buffer);
            if (layer != SingleQuadParticle.Layer.TRANSLUCENT) return;
            for (var q : quads) {
                vertex(buffer, q.left, 0, q.u0, q.v1, q.color, q.light);
                vertex(buffer, q.right, 0, q.u1, q.v1, q.color, q.light);
                vertex(buffer, q.right, q.height, q.u1, q.v0, q.color, q.light);
                vertex(buffer, q.left, q.height, q.u0, q.v0, q.color, q.light);
            }
        }
        private static void vertex(VertexConsumer buffer, Vec3 p, double height, float u, float v, int color, int light) {
            buffer.addVertex((float) p.x, (float) (p.y + height), (float) p.z).setUv(u, v).setColor(color).setLight(light);
        }
    }
}
