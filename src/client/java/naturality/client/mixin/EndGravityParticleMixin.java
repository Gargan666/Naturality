package naturality.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import naturality.weather.EndWeatherSystem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Particle.class)
public abstract class EndGravityParticleMixin {
    @Shadow protected ClientLevel level;
    @ModifyExpressionValue(method="tick",at=@At(value="FIELD",target="Lnet/minecraft/client/particle/Particle;gravity:F"))
    private float naturality$gravity(float gravity) { return gravity*EndWeatherSystem.state(level).strength(); }
}