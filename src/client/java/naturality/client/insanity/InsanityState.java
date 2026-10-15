package naturality.client.insanity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public interface InsanityState {
    Data naturality$insanity();
    final class Data {
        public float alpha = 1;
        public float distortion;
        public LivingEntityRenderState hallucination;
    }
}
