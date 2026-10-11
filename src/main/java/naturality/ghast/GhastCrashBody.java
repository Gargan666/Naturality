package naturality.ghast;

import net.minecraft.world.phys.Vec3;

public interface GhastCrashBody {
    GhastCrash.Flight naturality$flight();
    void naturality$flight(GhastCrash.Flight flight);
    Vec3 naturality$crashRotation();
    void naturality$crashRotation(Vec3 rotation);
    Vec3 naturality$crashImpulse();
    void naturality$crashImpulse(Vec3 impulse);
}

