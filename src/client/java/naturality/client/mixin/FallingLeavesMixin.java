package naturality.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FallingLeavesParticle;
import net.minecraft.client.particle.FallingParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import naturality.client.fluid.WaterRipples;
import naturality.client.fluid.WaterSurface;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla leaf drift and sprites, but give the quad a world-space pose. */
@Mixin(FallingParticle.class)
public abstract class FallingLeavesMixin extends SingleQuadParticle implements naturality.client.particle.WindParticleControl.LeafAccess {
    @Unique private boolean naturality$landed;
    @Unique private int naturality$groundTicks;
    @Unique private int naturality$flightTicks;
    @Unique private float naturality$yaw;
    @Unique private float naturality$phase;
    @Unique private float naturality$spin;
    @Unique private boolean naturality$onWater;
    @Unique private double naturality$waterY;
    @Unique private double naturality$driftX;
    @Unique private double naturality$driftZ;
    @Unique private double naturality$waterOriginX;
    @Unique private double naturality$waterOriginZ;

    protected FallingLeavesMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    @Override public boolean naturality$isLandedLeaf() {
        return naturality$landed && (Object) this instanceof FallingLeavesParticle;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void naturality$initialize(CallbackInfo ci) {
        naturality$yaw = random.nextFloat() * (float) (Math.PI * 2);
        naturality$phase = random.nextFloat() * (float) (Math.PI * 2);
        naturality$spin = (0.025F + random.nextFloat() * 0.05F) * (random.nextBoolean() ? 1 : -1);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void naturality$rest(CallbackInfo ci) {
        if (!naturality.config.NaturalityConfig.get().effects.leaves) { if (naturality$landed) remove(); return; }
        if (!((Object) this instanceof FallingLeavesParticle)) return;
        if (!naturality$landed) {
            naturality$flightTicks++;
            return;
        }
        xo = x;
        yo = y;
        zo = z;
        if (naturality$onWater) {
            if (!naturality.config.NaturalityConfig.get().effects.floatingLeaves) { remove(); ci.cancel(); return; }
            naturality$driftX = (naturality$driftX + (random.nextDouble() - 0.5) * 0.0002) * 0.985;
            naturality$driftZ = (naturality$driftZ + (random.nextDouble() - 0.5) * 0.0002) * 0.985;
            double dx = Math.clamp(x + naturality$driftX - naturality$waterOriginX, -0.045, 0.045);
            double dz = Math.clamp(z + naturality$driftZ - naturality$waterOriginZ, -0.045, 0.045);
            double nx = naturality$waterOriginX + dx;
            double nz = naturality$waterOriginZ + dz;
            var surface = WaterSurface.at(level, BlockPos.containing(nx, naturality$waterY, nz));
            if (surface != null && surface.contains(nx - surface.pos().getX(), nz - surface.pos().getZ())) {
                naturality$waterY = surface.height(nx - surface.pos().getX(), nz - surface.pos().getZ());
                setPos(nx, naturality$waterY + 1.0 / 512, nz);
            }
        }
        // Two seconds at full opacity, followed by a half-second fade.
        naturality$groundTicks++;
        alpha = 1 - Math.clamp((naturality$groundTicks - naturality.config.NaturalityConfig.get().effects.leafRestTicks) / 10.0F, 0, 1);
        if (naturality$groundTicks >= naturality.config.NaturalityConfig.get().effects.leafRestTicks + 10) remove();
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void naturality$landOnWater(CallbackInfo ci) {
        if (!naturality.config.NaturalityConfig.get().effects.floatingLeaves || !naturality.config.NaturalityConfig.get().effects.leaves || !((Object) this instanceof FallingLeavesParticle)
            || naturality$landed || removed || yd >= 0 || yo <= y) return;
        // Check the swept segment, so a fast leaf cannot pass through a shallow water surface.
        int steps = Math.min(32, Math.max(1, (int) Math.ceil((yo - y) * 16)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double px = xo + (x - xo) * t;
            double py = yo + (y - yo) * t;
            double pz = zo + (z - zo) * t;
            var surface = WaterSurface.at(level, BlockPos.containing(px, py, pz));
            if (surface == null || !surface.contains(px - surface.pos().getX(), pz - surface.pos().getZ())) continue;
            double surfaceY = surface.height(px - surface.pos().getX(), pz - surface.pos().getZ());
            if (yo < surfaceY || y > surfaceY) continue;
            naturality$landed = true;
            naturality$onWater = true;
            naturality$waterY = surfaceY;
            naturality$waterOriginX = px;
            naturality$waterOriginZ = pz;
            naturality$yaw += naturality$flightTicks * naturality$spin;
            xd = yd = zd = 0;
            setPos(px, surfaceY + 1.0 / 512, pz);
            xo = x;
            yo = y;
            zo = z;
            WaterRipples.leafImpact(level, px, surfaceY, pz);
            return;
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/particle/FallingParticle;remove()V", ordinal = 1))
    private void naturality$land(FallingParticle particle) {
        if (!naturality.config.NaturalityConfig.get().effects.leaves) { particle.remove(); return; }
        if ((Object) this instanceof FallingLeavesParticle && onGround) {
            naturality$landed = true;
            naturality$yaw += naturality$flightTicks * naturality$spin;
            xd = yd = zd = 0;
            // Collision places y at the support surface, including slabs/stairs.
            setPos(x, y + 1.0 / 512, z);
            xo = x;
            yo = y;
            zo = z;
        } else if (!naturality.config.NaturalityConfig.get().effects.leaves || !((Object) this instanceof FallingLeavesParticle)) {
            particle.remove();
        }
        // For airborne leaves, ignore vanilla's removal when an X/Z velocity
        // is blocked. Collision already clips that axis; gravity keeps sliding
        // the leaf down the wall until a downward collision sets onGround.
    }

    @Inject(method = "getLayer", at = @At("HEAD"), cancellable = true)
    private void naturality$translucent(CallbackInfoReturnable<Layer> cir) {
        if (naturality.config.NaturalityConfig.get().effects.leaves && (Object) this instanceof FallingLeavesParticle) cir.setReturnValue(Layer.TRANSLUCENT);
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        if (!((Object) this instanceof FallingLeavesParticle)) {
            super.extract(state, camera, partialTick);
            return;
        }
        float time = Math.max(0, naturality$flightTicks - 1 + partialTick);
        Quaternionf rotation = new Quaternionf();
        if (naturality$landed) {
            rotation.rotationY(naturality$yaw).rotateX(-(float) Math.PI / 2);
        } else {
            rotation.rotationY(naturality$yaw + time * naturality$spin)
                .rotateX(naturality$phase + time * naturality$spin * 0.7F)
                .rotateZ(0.45F * (float) Math.sin(naturality$phase + time * 0.09F));
        }
        extractRotatedQuad(state, camera, rotation, partialTick);
        if (!naturality$landed) {
            // Reverse the normal in local space so the same leaf has a visible
            // back face while tumbling. Grounded leaves need only the top face.
            rotation.rotateY((float) Math.PI);
            extractRotatedQuad(state, camera, rotation, partialTick);
        }
    }
}
