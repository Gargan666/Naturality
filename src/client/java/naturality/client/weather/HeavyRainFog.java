package naturality.client.weather;

import naturality.weather.WeatherState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;

public final class HeavyRainFog {
    private HeavyRainFog() {}

    public static void apply(FogData fog, WeatherState weather, float exposure, int chunks) {
        float strength = weather.heavyRainFog();
        if (strength <= 0) return;
        // Beyond this sphere, render-distance fog already hides all geometry.
        // Capping effectively infinite vanilla ranges keeps interpolation useful
        // while making the disappearing override visually continuous at zero.
        float clearRange = Math.max(100, chunks * 16F * 2);
        float start = Math.min(fog.environmentalStart, clearRange);
        float end = Math.min(fog.environmentalEnd, clearRange);
        fog.environmentalStart = Mth.lerp(strength, start, 12);
        fog.environmentalEnd = Mth.lerp(strength, end, weather.heavyRainFogEnd(exposure));
    }
}
