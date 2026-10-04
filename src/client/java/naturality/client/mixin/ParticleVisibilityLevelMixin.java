package naturality.client.mixin;

import naturality.client.particle.ParticleVisibility;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ParticleVisibilityLevelMixin {
    @Inject(method="sendBlockUpdated",at=@At("HEAD"))
    private void naturality$blockChanged(BlockPos pos,BlockState old,BlockState state,int flags,CallbackInfo ci) {
        ParticleVisibility.invalidate((ClientLevel)(Object)this,pos);
    }
    @Inject(method="setBlocksDirty",at=@At("HEAD"))
    private void naturality$blockDirty(BlockPos pos,BlockState old,BlockState state,CallbackInfo ci) {
        ParticleVisibility.invalidate((ClientLevel)(Object)this,pos);
    }
    @Inject(method="setSectionDirtyWithNeighbors",at=@At("HEAD"))
    private void naturality$sectionChanged(int x,int y,int z,CallbackInfo ci) {
        ParticleVisibility.invalidate((ClientLevel)(Object)this,null);
    }
}
