package naturality.entity;

/** Shared sound behavior for current and future maddened variants. */
public interface MaddenedMob {
    static boolean isExplosionSound(net.minecraft.resources.Identifier sound) {
        return sound.getPath().endsWith(".explode");
    }
    default float maddenedSoundPitch(float original) { return original * .5F; }
}
