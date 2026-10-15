package naturality.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;

@org.jspecify.annotations.NullMarked
public interface ElasticHandsState {
    void naturality$easeMeasuredItem(PoseStack pose, InteractionHand hand);
}
