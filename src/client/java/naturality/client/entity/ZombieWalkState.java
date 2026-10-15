package naturality.client.entity;
import net.minecraft.world.phys.Vec2;
@org.jspecify.annotations.NullMarked
public interface ZombieWalkState {
    Vec2 naturality$walkLean();
    void naturality$walkLean(Vec2 pose);
}
