package naturality.client.mixin;

import naturality.client.fire.EntityFireState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityFireStateMixin implements EntityFireState {
    @Unique private @org.jspecify.annotations.Nullable AABB naturality$box;
    @Unique private int naturality$seed;
    @Unique private boolean naturality$soulFire;
    public @org.jspecify.annotations.Nullable AABB naturality$fireBox() { return naturality$box; }
    public int naturality$fireSeed() { return naturality$seed; }
    public boolean naturality$soulFire() { return naturality$soulFire; }
    public void naturality$fireData(AABB box, int seed, boolean soulFire) {
        naturality$box = box;
        naturality$seed = seed;
        naturality$soulFire = soulFire;
    }
}
