package naturality.client.mixin;

import naturality.client.particle.WeatherClusterParticle;
import naturality.client.particle.WindParticleControl;
import naturality.client.particle.PortalMoteParticle;
import naturality.client.particle.PortalGlowParticle;
import naturality.weather.WeatherSystem;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
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
    @Unique private double naturality$windX;
    @Unique private double naturality$windZ;

    @Override public void naturality$applyWind() {
        Particle self = (Particle)(Object)this;
        if (!naturality.config.NaturalityConfig.get().effects.particleWind || !self.isAlive()
                || self instanceof WindParticleControl.LeafAccess leaf && leaf.naturality$isLandedLeaf()
                || self instanceof WeatherClusterParticle || self instanceof PortalMoteParticle
                || self instanceof PortalGlowParticle || WindParticleControl.immune(self)) {
            naturality$windX = naturality$windZ = 0;
            return;
        }
        var state = WeatherSystem.state(level);
        if (state == null) {
            naturality$windX = naturality$windZ = 0;
            return;
        }
        var pos = BlockPos.containing(x, y, z);
        var fluid = level.getFluidState(pos);
        if (fluid.is(FluidTags.WATER) && y < pos.getY() + fluid.getHeight(level, pos)) {
            naturality$windX = naturality$windZ = 0;
            return;
        }
        var cameraEntity = Minecraft.getInstance().getCameraEntity();
        if (cameraEntity == null || !naturality.client.weather.WeatherSoundEnvironment.hasOpenSkyAccess(level,
                new Vec3(x, y, z), cameraEntity)) {
            naturality$windX = naturality$windZ = 0;
            return;
        }
        // Keep wind separate from velocities that custom tick implementations overwrite.
        // The native tick has already moved the particle; this adds collision-aware drift.
        naturality$windX = naturality$windX * .96 + state.windX() * .007;
        naturality$windZ = naturality$windZ * .96 + state.windZ() * .007;
        self.move(naturality$windX, 0, naturality$windZ);
    }
}
