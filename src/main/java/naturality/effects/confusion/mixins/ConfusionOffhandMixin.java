package naturality.effects.confusion.mixins;
import naturality.effects.confusion.ConfusionOffhand;
import naturality.effects.confusion.ConfusionOffhand.ConfusionOffhandAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Player.class)
@org.jspecify.annotations.NullMarked
public abstract class ConfusionOffhandMixin implements ConfusionOffhandAccess {
    @Unique private final ConfusionOffhand naturality$offhand = new ConfusionOffhand();
    public ConfusionOffhand naturality$confusionOffhand() { return naturality$offhand; }
    @Inject(method="tick",at=@At("TAIL"))
    private void naturality$tick(CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer player) naturality$offhand.tick(player);
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void naturality$save(ValueOutput output,CallbackInfo ci) { naturality$offhand.save(output); }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void naturality$load(ValueInput input,CallbackInfo ci) { naturality$offhand.load(input); }
}
