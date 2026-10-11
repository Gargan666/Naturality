package naturality.client.mixin;

import naturality.client.villager.VillagerVisualState;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerModel.class)
public abstract class VillagerArmSwingMixin {
    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart head;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void naturality$workSwing(VillagerRenderState state, CallbackInfo ci) {
        var owner = ((VillagerVisualState)state).naturality$owner();
        if (owner == null) return;
        float nod = naturality.config.Feature.REPUTATION.enabled()
            ? state.ageInTicks - naturality.villager.Reputation.state(owner).nodStart : 20;
        if (nod >= 0 && nod < 20) head.xRot += Mth.sin(nod * (float)Math.PI / 5) * .3F * (1 - nod / 20);
        var reputation = naturality.villager.Reputation.state(owner);
        float gossipAge = state.ageInTicks - reputation.gossipStart;
        int conversationDuration = naturality.villager.GossipReactions.duration(reputation.gossipReaction);
        if (naturality.config.Feature.REPUTATION.enabled() && gossipAge >= 0 && gossipAge < conversationDuration) {
            float envelope = Math.min(1, gossipAge / 5) * Math.min(1, (conversationDuration - gossipAge) / 10);
            if (reputation.gossipReaction == naturality.villager.GossipReactions.LISTEN_NEGATIVE)
                head.yRot += Mth.sin(gossipAge * (float)Math.PI / 8) * .3F * envelope;
            else {
                float bob = (1 - Mth.cos(gossipAge * (float)Math.PI / 10)) * .5F * envelope;
                float direction = reputation.gossipReaction == naturality.villager.GossipReactions.TELL_POSITIVE
                    || reputation.gossipReaction == naturality.villager.GossipReactions.CONVERSE ? -1 : 1;
                float amplitude = reputation.gossipReaction == naturality.villager.GossipReactions.CONVERSE ? .08F
                    : reputation.gossipReaction >= naturality.villager.GossipReactions.TELL_POSITIVE ? .16F : .25F;
                head.xRot += bob * amplitude * direction;
            }
        }
        float swing = owner.getSwingAnimation(Mth.clamp(state.ageInTicks - owner.tickCount, 0, 1));
        if (swing > 0) {
            float forward = Mth.sin(swing * (float)Math.PI);
            float recovery = Mth.sin((1 - (1 - swing) * (1 - swing)) * (float)Math.PI);
            arms.xRot -= forward * 1.2F - recovery * 0.4F;
        }
    }
}
