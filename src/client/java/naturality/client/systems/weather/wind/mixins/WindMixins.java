package naturality.client.systems.weather.wind.mixins;

import naturality.client.systems.weather.wind.WindRendering.WindParticleControl;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.systems.weather.overworld.precipitation.ParticleWeather.WeatherClusterParticle;
import naturality.client.portals.nether.glow.PortalGlowController.PortalMoteParticle;
import naturality.client.portals.nether.glow.PortalGlowController.PortalGlowParticle;
import naturality.client.systems.weather.framework.WeatherClient.WeatherParticleContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class WindMixins {
    private WindMixins() {}

    @Mixin(ParticleGroup.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ParticleGroupWindMixin {
        @Inject(method = "tickParticle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;tick()V", shift = At.Shift.AFTER))
        private void naturality$applyWind(Particle particle, CallbackInfo ci) {
            ((WindParticleControl.Access)particle).naturality$applyWind();
        }
    }

    @Mixin(Particle.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class ParticleWindMixin implements WindParticleControl.Access {
        @SuppressWarnings("null") @Shadow protected ClientLevel level;
        @Shadow protected double x;
        @Shadow protected double y;
        @Shadow protected double z;
        @Unique private boolean naturality$immune;
        @Override public boolean naturality$windImmune() {return naturality$immune;}
        @Override public void naturality$windImmune(boolean value) {naturality$immune=value;}
        @Unique private double naturality$windX;
        @Unique private double naturality$windZ;

        @Override public void naturality$applyWind() {
            Particle self = (Particle)(Object)this;
            if (!self.isAlive()
                    || self instanceof WindParticleControl.LeafAccess leaf && leaf.naturality$isLandedLeaf()
                    || self instanceof WeatherClusterParticle || self instanceof PortalMoteParticle
                    || self instanceof PortalGlowParticle || naturality$immune) {
                naturality$windX = naturality$windZ = 0;
                return;
            }
            var state = WeatherParticleContext.wind(level);
            if (!state.enabled()) {
                naturality$windX = naturality$windZ = 0;
                return;
            }
            var pos = BlockPos.containing(x, y, z);
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && y < pos.getY() + fluid.getHeight(level, pos)) {
                naturality$windX = naturality$windZ = 0;
                return;
            }
            var cameraEntity = state.camera();
            if (cameraEntity == null || !naturality.client.systems.weather.framework.WeatherClient.WeatherSoundEnvironment.hasOpenSkyAccess(level,
                    new Vec3(x, y, z), cameraEntity)) {
                naturality$windX = naturality$windZ = 0;
                return;
            }
            // Keep wind separate from velocities that custom tick implementations overwrite.
            // The native tick has already moved the particle; this adds collision-aware drift.
            naturality$windX = naturality$windX * .96 + state.x();
            naturality$windZ = naturality$windZ * .96 + state.z();
            self.move(naturality$windX, 0, naturality$windZ);
        }
    }

    /** Keep exposure rays inside Sodium's immutable meshing snapshot. */
    @Pseudo
    @Mixin(targets="net.caffeinemc.mods.sodium.client.world.LevelSlice", remap=false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumWindSnapshotMixin {
        @ModifyArgs(method="prepare", at=@At(value="INVOKE",
            target="Lnet/minecraft/world/level/levelgen/structure/BoundingBox;<init>(IIIIII)V"))
        private static void naturality$exposureBorder(Args args) {
            // Sodium already clones the 3x3x3 section neighborhood, but normally
            // unpacks only two neighboring blocks. Wind rays need eight. This
            // expands the copied volume without querying the live world off-thread.
            for(int i=0;i<3;i++) args.set(i, (int)args.get(i)-6);
            for(int i=3;i<6;i++) args.set(i, (int)args.get(i)+6);
        }
    }
}
