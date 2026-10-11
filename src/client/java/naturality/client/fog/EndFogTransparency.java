package naturality.client.fog;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;

public final class EndFogTransparency {
    private EndFogTransparency() {}

    /** Complete the sky fade before either loaded terrain or boss-fog culling ends. */
    public static void boundDistanceFog(net.minecraft.client.renderer.fog.FogData fog) {
        float boundary = fog.renderDistanceEnd;
        if (Float.isFinite(fog.environmentalEnd) && fog.environmentalEnd > 0)
            boundary = Math.min(boundary, fog.environmentalEnd);
        float margin = Math.min(16.0F, boundary * 0.2F);
        fog.renderDistanceEnd = Math.max(1.0F, boundary - margin);
        fog.renderDistanceStart = Math.min(fog.renderDistanceStart, fog.renderDistanceEnd * 0.75F);
    }

    public static boolean isActive(ClientLevel level, Camera camera) {
        if (!naturality.config.NaturalityConfig.get().fog.enabled) return false;
        if ((!level.dimension().equals(Level.END) && !level.dimension().equals(Level.OVERWORLD))
                || camera.getFluidInCamera() != FogType.NONE) return false;
        return !(camera.entity() instanceof LivingEntity entity)
            || (!entity.hasEffect(MobEffects.BLINDNESS) && !entity.hasEffect(MobEffects.DARKNESS));
    }
}
