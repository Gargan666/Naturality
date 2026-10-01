package naturality.client.fog;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;

public final class EndFogTransparency {
    private EndFogTransparency() {}

    public static boolean isActive(ClientLevel level, Camera camera) {
        if (!naturality.config.NaturalityConfig.get().fog.enabled) return false;
        if ((!level.dimension().equals(Level.END) && !level.dimension().equals(Level.OVERWORLD))
                || camera.getFluidInCamera() != FogType.NONE) return false;
        return !(camera.entity() instanceof LivingEntity entity)
            || (!entity.hasEffect(MobEffects.BLINDNESS) && !entity.hasEffect(MobEffects.DARKNESS));
    }
}
