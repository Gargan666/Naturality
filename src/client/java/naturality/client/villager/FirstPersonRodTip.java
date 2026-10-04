package naturality.client.villager;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Current-frame hand-model measurement, filled before world submission. */
public final class FirstPersonRodTip {
    public static boolean measuring;
    public static Vector3f viewTip;
    public static Vec3 worldTip;
    public static float pixelSize = 1.0F / 32;
    private FirstPersonRodTip() { }
}
