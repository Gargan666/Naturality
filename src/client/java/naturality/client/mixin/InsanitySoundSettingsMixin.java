package naturality.client.mixin;

import naturality.client.insanity.InsanitySoundSettings;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSoundInstance.class)
public interface InsanitySoundSettingsMixin extends InsanitySoundSettings {
    @Accessor("volume") float naturality$volume();
    @Accessor("pitch") float naturality$pitch();
}
