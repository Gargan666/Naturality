package naturality.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class SkyEventSettings {
    public static final Codec<SkyEventSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("override", false).forGetter(settings -> settings.override),
        Codec.INT.optionalFieldOf("strength", 10).forGetter(settings -> settings.strength)
    ).apply(instance, SkyEventSettings::new));
    public volatile boolean override = false;
    public volatile int strength = 10;
    public SkyEventSettings() {}
    private SkyEventSettings(boolean override, int strength) {
        this.override = override;
        this.strength = strength;
        validate();
    }
    public void validate() { strength = Math.clamp(strength, 0, 20); }
}
