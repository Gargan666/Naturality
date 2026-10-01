package naturality;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class NaturalitySounds {
    public static final SoundEvent WIND_WEAK = Registry.register(BuiltInRegistries.SOUND_EVENT,
        Naturality.id("wind_weak"), SoundEvent.createVariableRangeEvent(Naturality.id("wind_weak")));
    public static final SoundEvent WIND_STRONG = Registry.register(BuiltInRegistries.SOUND_EVENT,
        Naturality.id("wind_strong"), SoundEvent.createVariableRangeEvent(Naturality.id("wind_strong")));
    public static final SoundEvent PORTAL_OPEN = Registry.register(
        BuiltInRegistries.SOUND_EVENT, Naturality.id("portal_open"),
        SoundEvent.createVariableRangeEvent(Naturality.id("portal_open")));

    public static final SoundEvent WATER_SPLASH_LOUD = Registry.register(
        BuiltInRegistries.SOUND_EVENT, Naturality.id("watersplash_loud"),
        SoundEvent.createVariableRangeEvent(Naturality.id("watersplash_loud")));

    private NaturalitySounds() {}

    public static void initialize() {
        // Load and register the sound before the server can open a portal.
    }
}

