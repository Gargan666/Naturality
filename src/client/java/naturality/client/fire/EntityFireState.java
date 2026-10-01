package naturality.client.fire;

import net.minecraft.world.phys.AABB;

public interface EntityFireState {
    @org.jspecify.annotations.Nullable AABB naturality$fireBox();
    int naturality$fireSeed();
    boolean naturality$soulFire();
    void naturality$fireData(AABB box, int seed, boolean soulFire);
}
