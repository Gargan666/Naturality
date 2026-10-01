package naturality.client.weather;

import naturality.NaturalitySounds;
import naturality.weather.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class WeatherClient {
    private static @org.jspecify.annotations.Nullable WindLoop weak, strong;
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private WeatherClient() {}
    public static void initialize() {
        ClientPlayConnectionEvents.INIT.register((_, _) -> WeatherSystem.clearClient());
        ClientPlayConnectionEvents.DISCONNECT.register((_, client) -> { WeatherSystem.clearClient(); stop(client); });
        ClientPlayNetworking.registerGlobalReceiver(WeatherPayload.TYPE, (payload, _) -> WeatherSystem.receive(payload));
        ClientTickEvents.END_CLIENT_TICK.register(WeatherClient::tick);
    }
    private static void stop(Minecraft client) {
        var weak = WeatherClient.weak;
        var strong = WeatherClient.strong;
        if (weak != null) client.getSoundManager().stop(weak);
        if (strong != null) client.getSoundManager().stop(strong);
        WeatherClient.weak = WeatherClient.strong = null;
    }
    private static void tick(Minecraft client) {
        if (world != client.level) { stop(client); world = client.level; WeatherSoundEnvironment.reset(); }
        var world = WeatherClient.world;
        if (world == null || client.isPaused()) return;
        WeatherSoundEnvironment.tick(client);
        var state = WeatherSystem.state(world);
        if (!naturality.config.NaturalityConfig.get().effects.windSounds || state == null) { stop(client); return; }
        var weak = WeatherClient.weak;
        var strong = WeatherClient.strong;
        // Keep silent loops alive so gradual crossfades do not repeatedly restart their clips.
        if (weak == null || !client.getSoundManager().isActive(weak)) {
            weak = new WindLoop(false); WeatherClient.weak = weak; client.getSoundManager().play(weak);
        }
        if (strong == null || !client.getSoundManager().isActive(strong)) {
            strong = new WindLoop(true); WeatherClient.strong = strong; client.getSoundManager().play(strong);
        }
    }
    private static final class WindLoop extends AbstractTickableSoundInstance {
        private final boolean strong;
        WindLoop(boolean strong) {
            super(strong ? NaturalitySounds.WIND_STRONG : NaturalitySounds.WIND_WEAK, SoundSource.WEATHER, RandomSource.create());
            this.strong = strong; looping = true; delay = 0; relative = true;
            attenuation = SoundInstance.Attenuation.NONE; volume = 0;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            var client = Minecraft.getInstance();
            var s = client.level == null ? null : WeatherSystem.state(client.level);
            if (!naturality.config.NaturalityConfig.get().effects.windSounds || s == null) { stop(); return; }
            float exposure = WeatherSoundEnvironment.windExposure();
            if (!client.gameRenderer.mainCamera().getFluidInCamera().equals(net.minecraft.world.level.material.FogType.NONE)) exposure *= .15F;
            float target = (strong ? s.strongWindGain() : s.weakWindGain()) * exposure;
            volume += Math.clamp(target - volume, -.025F, .025F);
        }
    }
}
