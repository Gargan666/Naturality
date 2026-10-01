package naturality.client.mixin;

import naturality.client.particle.WeatherClusterParticle;
import naturality.client.particle.WindParticleControl;
import naturality.client.particle.PortalMoteParticle;
import naturality.client.particle.PortalGlowParticle;
import naturality.weather.WeatherSystem;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
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
        var pos = BlockPos.containing(x, y, z);
        boolean sheltered = !naturality.client.weather.WindRendering.exposed(level, pos);
        var fluid = level.getFluidState(pos);
        boolean underwater = fluid.is(FluidTags.WATER)
            && y < pos.getY() + fluid.getHeight(level, pos);
        var state = WeatherSystem.state(level);
        if (!naturality.config.NaturalityConfig.get().effects.particleWind || !self.isAlive() || sheltered || underwater || WindParticleControl.immune(self)
                || self instanceof WindParticleControl.LeafAccess leaf && leaf.naturality$isLandedLeaf()
                || self instanceof WeatherClusterParticle || self instanceof PortalMoteParticle
                || self instanceof PortalGlowParticle || state == null) {
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
