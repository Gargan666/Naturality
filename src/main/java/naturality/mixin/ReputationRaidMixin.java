package naturality.mixin;

import java.util.Set;
import java.util.UUID;
import naturality.villager.Reputation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raid.class)
public abstract class ReputationRaidMixin {
    @Shadow @Final private Set<UUID> heroesOfTheVillage;
    @Unique private boolean naturality$wasVictory;

    @Inject(method = "tick", at = @At("HEAD"))
    private void naturality$beforeRaid(ServerLevel level, CallbackInfo ci) {
        naturality$wasVictory = ((Raid)(Object)this).isVictory();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void naturality$raidVictory(ServerLevel level, CallbackInfo ci) {
        if (!naturality$wasVictory && ((Raid)(Object)this).isVictory())
            for (var hero : heroesOfTheVillage) Reputation.credit(level, hero, 20);
    }
}
