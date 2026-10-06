package naturality.client.mixin;

import naturality.client.particle.WeatherClusterParticle;
import naturality.client.particle.WindParticleControl;
import naturality.client.particle.PortalMoteParticle;
import naturality.client.particle.PortalGlowParticle;
import naturality.client.weather.WeatherParticleContext;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public abstract class ParticleWindMixin implements WindParticleControl.Access {
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
        if (cameraEntity == null || !naturality.client.weather.WeatherSoundEnvironment.hasOpenSkyAccess(level,
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
