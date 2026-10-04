package naturality.client.villager;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

/** Leads use the same persistent physics and camera-facing geometry as fishing. */
public final class PhysicsLeadRendering {
    private static final Map<EntityRenderState.LeashState, Object> KEYS = new WeakHashMap<>();

    public static void identify(EntityRenderState.LeashState state, Object key) { KEYS.put(state, key); }

    public static void submit(PoseStack pose, SubmitNodeCollector collector,
            EntityRenderState.LeashState state) {
        var start = state.start;
        var end = state.end;
        var offset = state.offset;
        Object key = KEYS.getOrDefault(state, state);
        int blockStart = state.startBlockLight, blockEnd = state.endBlockLight;
        int skyStart = state.startSkyLight, skyEnd = state.endSkyLight;
        FishingLineRendering.submitRope(pose, collector, key, start, () -> end, offset, .075F, index -> {
            float t = index / 24.0F;
            float light = Math.max(Mth.lerp(t, blockStart, blockEnd), Mth.lerp(t, skyStart, skyEnd));
            float shade = (.25F + .75F * light / 15) * (index % 2 == 0 ? .7F : 1);
            return 0xff000000 | ((int)(128 * shade) << 16) | ((int)(102 * shade) << 8) | (int)(77 * shade);
        });
    }
    private PhysicsLeadRendering() { }
}