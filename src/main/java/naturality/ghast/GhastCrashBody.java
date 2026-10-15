package naturality.ghast;

import net.minecraft.world.phys.Vec3;

@org.jspecify.annotations.NullMarked
public interface GhastCrashBody {
    int naturality$impactTicks();
    void naturality$impactTicks(int ticks);
    GhastCrash.@org.jspecify.annotations.Nullable Flight naturality$flight();
    void naturality$flight(GhastCrash.@org.jspecify.annotations.Nullable Flight flight);
    Vec3 naturality$crashRotation();
    void naturality$crashRotation(Vec3 rotation);
    Vec3 naturality$crashImpulse();
    void naturality$crashImpulse(Vec3 impulse);
}

