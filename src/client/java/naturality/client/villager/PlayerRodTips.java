package naturality.client.villager;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Rod tips measured from this frame's animated player item submissions. */
public final class PlayerRodTips {
    public static final Map<AvatarRenderState, Matrix4f> roots = new IdentityHashMap<>();
    public static final Map<Integer, Vec3> tips = new HashMap<>();
    private PlayerRodTips() { }
    public static void clear() { roots.clear(); tips.clear(); }
}
