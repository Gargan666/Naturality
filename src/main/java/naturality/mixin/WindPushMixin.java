package naturality.mixin;

import naturality.weather.WindPush;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class WindPushMixin {
    @Inject(method="baseTick",at=@At("HEAD"))
    private void naturality$pushFromFoliage(CallbackInfo ci) {
        if((Object)this instanceof Player player)WindPush.resolve(player);
    }
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void naturality$preventWalkingThroughFoliage(CallbackInfo ci) {
        if((Object)this instanceof Player player && WindPush.resolve(player))ci.cancel();
    }
}
